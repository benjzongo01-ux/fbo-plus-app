import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions";
import { affecterLivreur, exigerRole } from "./commandes.js";
import { db, exigerMemePays, journaliser, MAX_INSTANCES, OPTIONS_APPEL, PAYS_DEFAUT } from "./commun.js";
import { onAppel } from "./limites.js";

/**
 * Affectation intelligente des livreurs : pour une commande prête, classe les livreurs actifs du pays.
 * - charge : moins il a de missions en cours, mieux c'est ;
 * - même quartier : déjà en route vers ce quartier, il livre les deux colis dans la même tournée ;
 * - distance : sa dernière position GPS par rapport à l'entrepôt où il récupère le colis ;
 * - fiabilité : son taux de livraisons réussies.
 */
interface Position {
  lat: number;
  lng: number;
}

export interface Proposition {
  livreurId: string;
  nom: string;
  score: number;
  raisons: string[];
}

const POSITION_RECENTE_MS = 2 * 60 * 60 * 1000;

function distanceKm(a: Position, b: Position) {
  const rad = (x: number) => (x * Math.PI) / 180;
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.sqrt(h));
}

const normaliser = (s: unknown) =>
  String(s ?? "").normalize("NFD").replace(/[̀-ͯ]/g, "").trim().toLowerCase();

export async function classerLivreurs(commande: FirebaseFirestore.DocumentData): Promise<Proposition[]> {
  const pays = commande.pays || PAYS_DEFAUT;
  const [livreurs, livraisons, entrepot] = await Promise.all([
    db.collection("users").where("role", "==", "livreur").where("pays", "==", pays).get(),
    db.collection("deliveries").where("pays", "==", pays).get(),
    db.doc(`warehouses/${commande.entrepotId}`).get(),
  ]);
  const positionEntrepot = entrepot.data()?.position as Position | undefined;
  const quartier = normaliser(commande.client?.quartier ?? commande.zone?.nom);

  const propositions: Proposition[] = [];
  for (const doc of livreurs.docs) {
    const l = doc.data();
    if (l.statut !== "actif" || l.kyc?.statut !== "valide") continue;
    const siennes = livraisons.docs.map((d) => d.data()).filter((d) => d.livreurId === doc.id);
    const enCours = siennes.filter((d) => ["assignee", "en_livraison"].includes(d.statut));
    const livrees = siennes.filter((d) => d.statut === "livree").length;
    const terminees = livrees + siennes.filter((d) => d.statut === "retournee").length;

    let score = 100;
    const raisons: string[] = [];

    score -= enCours.length * 15;
    raisons.push(enCours.length ? `${enCours.length} mission(s) en cours` : "libre");

    const memeQuartier = quartier && enCours.some((d) => normaliser(d.client?.quartier) === quartier);
    if (memeQuartier) {
      score += 30;
      raisons.push(`livre déjà à ${commande.client?.quartier ?? commande.zone?.nom} : même tournée`);
    }

    const position = l.position as Position | undefined;
    const fraiche = l.positionMaj?.toMillis?.() > Date.now() - POSITION_RECENTE_MS;
    if (position && positionEntrepot && fraiche) {
      const km = distanceKm(position, positionEntrepot);
      score -= Math.min(km, 30) * 2;
      raisons.push(`${km.toFixed(1)} km de l'entrepôt`);
    }

    if (terminees >= 3) {
      const taux = livrees / terminees;
      score += Math.round(taux * 10);
      raisons.push(`${Math.round(taux * 100)} % de réussite`);
    }

    propositions.push({ livreurId: doc.id, nom: String(l.nom ?? ""), score: Math.round(score), raisons });
  }
  return propositions.sort((a, b) => b.score - a.score);
}

export const proposerLivreurs = onAppel("proposerLivreurs", OPTIONS_APPEL, async (req) => {
  exigerRole(req, ["operateur", "admin", "super_admin"]);
  const id = String((req.data as { id?: unknown } | null)?.id ?? "");
  const commande = (await db.doc(`orders/${id}`).get()).data();
  if (!commande) throw new HttpsError("not-found", "Commande introuvable.");
  exigerMemePays(req, commande.pays);
  return { propositions: await classerLivreurs(commande) };
});

/** Assigne le meilleur livreur, à la demande du centre d'appel. */
export const affecterMeilleurLivreur = onAppel("affecterMeilleurLivreur", OPTIONS_APPEL, async (req) => {
  const uid = exigerRole(req, ["operateur", "admin", "super_admin"]);
  const id = String((req.data as { id?: unknown } | null)?.id ?? "");
  const ref = db.doc(`orders/${id}`);
  const commande = (await ref.get()).data();
  if (!commande) throw new HttpsError("not-found", "Commande introuvable.");
  exigerMemePays(req, commande.pays);
  const [meilleur] = await classerLivreurs(commande);
  if (!meilleur) throw new HttpsError("failed-precondition", "Aucun livreur actif dans ce pays.");

  await db.runTransaction(async (tx) => {
    const actuelle = (await tx.get(ref)).data()!;
    await affecterLivreur(tx, ref, actuelle, meilleur.livreurId, uid);
  });
  await journaliser("livreur_assigne", uid, id, { livreurId: meilleur.livreurId, automatique: true });
  return meilleur;
});

/** Pays où l'affectation automatique est activée : chaque commande prête part au meilleur livreur. */
export const affectationAutomatique = onDocumentUpdated(
  { document: "orders/{id}", region: "europe-west1", maxInstances: MAX_INSTANCES },
  async (evenement) => {
    const avant = evenement.data?.before.data();
    const apres = evenement.data?.after.data();
    if (!avant || !apres || avant.statut === apres.statut || apres.statut !== "prete") return;
    if (apres.mode === "validation" || apres.mode === "expedition" || apres.livreurId) return;
    const tarifs = (await db.doc(`tarifsPays/${apres.pays || PAYS_DEFAUT}`).get()).data();
    if (tarifs?.affectationAuto !== true) return;

    const [meilleur] = await classerLivreurs(apres);
    if (!meilleur) return;
    const ref = evenement.data!.after.ref;
    try {
      await db.runTransaction(async (tx) => {
        const actuelle = (await tx.get(ref)).data();
        // Un déclencheur peut se relancer, ou un opérateur avoir déjà assigné : on ne remplace rien.
        if (!actuelle || actuelle.statut !== "prete" || actuelle.livreurId) return;
        await affecterLivreur(tx, ref, actuelle, meilleur.livreurId, "systeme");
      });
      await journaliser("livreur_assigne", "systeme", ref.id, { livreurId: meilleur.livreurId, automatique: true });
    } catch (e) {
      logger.warn("Affectation automatique impossible", e);
    }
  },
);
