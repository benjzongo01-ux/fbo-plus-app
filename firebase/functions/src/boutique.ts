import { FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { db, journaliser, OPTIONS_APPEL } from "./commun.js";
import { onAppel } from "./limites.js";
import { notifier } from "./notifications.js";

/**
 * Commande en ligne passée par un client depuis la boutique publique d'un distributeur.
 * Le client n'a pas de compte : la page ouvre une session anonyme (gratuite), ce qui permet
 * de limiter les envois par appareil. La commande arrive chez le distributeur, qui la vérifie
 * puis la transforme en vraie commande : rien n'est réservé dans le stock à ce stade.
 */
const TELEPHONE = /^\+?[0-9 ]{8,16}$/;

function texte(valeur: unknown, longueurMax = 200): string {
  return typeof valeur === "string" ? valeur.trim().slice(0, longueurMax) : "";
}

export const commanderEnBoutique = onAppel("commanderEnBoutique", OPTIONS_APPEL, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "Rechargez la page puis réessayez.");
  const d = (req.data ?? {}) as Record<string, unknown>;
  const fboId = texte(d.boutiqueId, 128);
  const nom = texte(d.clientNom, 120);
  const telephone = texte(d.clientTelephone, 20);
  const telephone2 = texte(d.clientTelephone2, 20);
  const quartier = texte(d.quartier, 120);
  if (!nom) throw new HttpsError("invalid-argument", "Indiquez votre nom et prénom.");
  if (!TELEPHONE.test(telephone)) throw new HttpsError("invalid-argument", "Numéro de téléphone invalide.");
  if (telephone2 && !TELEPHONE.test(telephone2)) throw new HttpsError("invalid-argument", "Deuxième numéro invalide.");
  if (!quartier) throw new HttpsError("invalid-argument", "Indiquez votre quartier.");

  const demandes = Array.isArray(d.lignes) ? (d.lignes as Record<string, unknown>[]) : [];
  if (demandes.length === 0 || demandes.length > 50) throw new HttpsError("invalid-argument", "Choisissez entre 1 et 50 produits.");

  const boutique = (await db.doc(`boutiques/${fboId}`).get()).data();
  const distributeur = (await db.doc(`users/${fboId}`).get()).data();
  if (!boutique || distributeur?.role !== "fbo" || distributeur?.statut !== "actif") {
    throw new HttpsError("not-found", "Cette boutique n'existe pas ou n'est plus active.");
  }

  // Prix officiels du catalogue : ceux envoyés par la page sont ignorés.
  const lignes = [];
  for (const l of demandes) {
    const quantite = Number(l.quantite);
    if (!Number.isInteger(quantite) || quantite < 1 || quantite > 100) throw new HttpsError("invalid-argument", "Quantité invalide.");
    const produitId = texte(l.produitId, 80);
    const produit = (await db.doc(`products/${produitId}`).get()).data();
    if (!produit || produit.actif !== true) throw new HttpsError("failed-precondition", "Un produit n'est plus disponible.");
    lignes.push({ produitId, ref: String(produit.reference ?? produitId), nom: String(produit.nom), prixUnitaire: Number(produit.prix), quantite });
  }
  const total = lignes.reduce((s, l) => s + l.prixUnitaire * l.quantite, 0);

  const ref = db.collection("commandesBoutique").doc();
  await ref.create({
    fboId,
    statut: "nouvelle",
    client: { nom, telephone, telephone2, quartier, adresse: texte(d.adresse) },
    note: texte(d.note, 500),
    lignes,
    total,
    appareil: req.auth.uid,
    createdAt: FieldValue.serverTimestamp(),
  });
  await journaliser("commande_boutique", req.auth.uid, ref.id, { fboId, total });
  await notifier(fboId, "Nouvelle commande en ligne", `${nom} a commandé dans votre boutique (${total} FCFA).`, "", `boutique_${ref.id}`);
  return { id: ref.id, total };
});
