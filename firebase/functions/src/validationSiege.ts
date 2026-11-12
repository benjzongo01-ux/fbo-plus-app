import { FieldValue, Transaction } from "firebase-admin/firestore";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { exigerRole } from "./commandes.js";
import { db, exigerMemePays, journaliser, MAX_INSTANCES, OPTIONS_APPEL, PAYS_DEFAUT } from "./commun.js";
import { onAppel } from "./limites.js";
import { notifier } from "./notifications.js";
import { regulariserLot } from "./repartition.js";

/**
 * Validation au siège Forever : c'est elle qui crédite les CC sur l'ID Forever.
 *
 * Chaque commande terminée (livrée, expédiée ou validée sur l'ID) verse ses produits dans la réserve
 * de l'ID bénéficiaire. Tant que la réserve n'atteint pas l'achat minimum (36 000 FCFA par défaut,
 * réglable par pays), elle attend : la vente n'est pas perdue. Dès le minimum atteint, un lot de
 * validation est créé pour le correspondant au siège, qui valide puis marque le lot « validé ».
 */
export const MINIMUM_VALIDATION_DEFAUT = 36_000;
const STATUTS_TERMINES = ["livree", "expediee", "validee"];
const ROLES_VALIDATION = ["operateur", "correspondant", "admin", "super_admin"];

interface LigneValidation {
  commandeId: string;
  produitId: string;
  ref: string;
  nom: string;
  quantite: number;
  prixUnitaire: number;
  cc: number;
}

const arrondiCc = (n: number) => Math.round(n * 1000) / 1000;
const montantDe = (lignes: LigneValidation[]) => lignes.reduce((s, l) => s + l.prixUnitaire * l.quantite, 0);
const ccDe = (lignes: LigneValidation[]) => arrondiCc(lignes.reduce((s, l) => s + l.cc * l.quantite, 0));

async function minimumDuPays(tx: Transaction, pays: string) {
  const t = (await tx.get(db.doc(`tarifsPays/${pays}`))).data();
  return Number(t?.minimumValidation ?? MINIMUM_VALIDATION_DEFAUT);
}

/** Crée un lot à valider à partir d'une réserve, et vide la réserve. Écritures seulement. */
function creerLot(tx: Transaction, refReserve: FirebaseFirestore.DocumentReference, reserve: FirebaseFirestore.DocumentData) {
  const lignes = reserve.lignes as LigneValidation[];
  const lot = db.collection("lotsValidation").doc();
  tx.create(lot, {
    statut: "a_valider",
    pays: reserve.pays,
    fboId: reserve.fboId,
    idForever: reserve.idForever,
    nom: reserve.nom,
    telephone: reserve.telephone ?? null,
    lignes,
    commandes: [...new Set(lignes.map((l) => l.commandeId))],
    montant: montantDe(lignes),
    ccTotal: ccDe(lignes),
    createdAt: FieldValue.serverTimestamp(),
  });
  tx.set(refReserve, { lignes: [], montant: 0, ccTotal: 0, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  return lot.id;
}

export const alimenterValidation = onDocumentUpdated(
  { document: "orders/{id}", region: "europe-west1", maxInstances: MAX_INSTANCES },
  async (evenement) => {
    const avant = evenement.data?.before.data();
    const apres = evenement.data?.after.data();
    if (!avant || !apres || avant.statut === apres.statut) return;
    if (!STATUTS_TERMINES.includes(apres.statut) || !apres.beneficiaire?.idForever) return;

    const refCommande = evenement.data!.after.ref;
    const lotCree = await db.runTransaction(async (tx) => {
      const commande = (await tx.get(refCommande)).data();
      // Un déclencheur peut s'exécuter deux fois : la commande n'est versée qu'une fois.
      if (!commande || commande.versValidation) return null;

      const pays = commande.pays || PAYS_DEFAUT;
      const idForever = String(commande.beneficiaire.idForever);
      const refReserve = db.doc(`reservesValidation/${commande.fboId}_${idForever.replace(/\D/g, "")}`);
      const reserve = (await tx.get(refReserve)).data();
      const minimum = await minimumDuPays(tx, pays);
      const profil = (await tx.get(db.doc(`users/${commande.fboId}`))).data();

      const nouvelles: LigneValidation[] = (commande.lignes as LigneValidation[]).map((l) => ({
        commandeId: refCommande.id,
        produitId: l.produitId,
        ref: l.ref,
        nom: l.nom,
        quantite: l.quantite,
        prixUnitaire: l.prixUnitaire,
        cc: l.cc,
      }));
      const lignes = [...((reserve?.lignes as LigneValidation[] | undefined) ?? []), ...nouvelles];
      const beneficiaire = commande.beneficiaire;
      const donnees = {
        pays,
        fboId: commande.fboId,
        idForever,
        nom: [beneficiaire.prenom, beneficiaire.nom].filter(Boolean).join(" ") || String(profil?.nom ?? ""),
        telephone: beneficiaire.type === "moi" ? (profil?.telephone ?? null) : null,
        lignes,
        montant: montantDe(lignes),
        ccTotal: ccDe(lignes),
        minimum,
        updatedAt: FieldValue.serverTimestamp(),
      };

      tx.update(refCommande, { versValidation: true });
      tx.set(refReserve, donnees, { merge: true });
      if (donnees.montant >= minimum) return { id: creerLot(tx, refReserve, donnees), cc: donnees.ccTotal, fboId: commande.fboId };
      return null;
    });

    if (lotCree) {
      await notifier(
        lotCree.fboId,
        "Validation au siège",
        `Achat minimum atteint : vos produits (${lotCree.cc} CC) partent en validation au siège.`,
        "",
        `lot_${lotCree.id}`,
      );
    }
  },
);

/** Le centre d'appel envoie une réserve en validation sans attendre le minimum (fin de mois, par exemple). */
export const envoyerReserveEnValidation = onAppel("envoyerReserveEnValidation", OPTIONS_APPEL, async (req) => {
  const uid = exigerRole(req, ROLES_VALIDATION);
  const id = String((req.data as { id?: unknown } | null)?.id ?? "");
  if (!id) throw new HttpsError("invalid-argument", "Réserve manquante.");
  const ref = db.doc(`reservesValidation/${id}`);

  const lotId = await db.runTransaction(async (tx) => {
    const reserve = (await tx.get(ref)).data();
    if (!reserve) throw new HttpsError("not-found", "Réserve introuvable.");
    exigerMemePays(req, reserve.pays);
    if (!(reserve.lignes as unknown[] | undefined)?.length) {
      throw new HttpsError("failed-precondition", "Cette réserve est vide.");
    }
    return creerLot(tx, ref, reserve);
  });
  await journaliser("reserve_envoyee_validation", uid, lotId, { reserve: id });
  return { id: lotId };
});

/** Le correspondant au siège a validé les produits sur l'ID Forever : il le note avec la référence du reçu. */
export const validerLotSiege = onAppel("validerLotSiege", OPTIONS_APPEL, async (req) => {
  const uid = exigerRole(req, ROLES_VALIDATION);
  const d = (req.data ?? {}) as { id?: unknown; reference?: unknown; montantFacture?: unknown; prixFactures?: unknown; recu?: unknown };
  // Reçu scanné (image envoyée sans être gardée sur le téléphone) : archivé avec le lot.
  const recu = typeof d.recu === "string" && d.recu.startsWith("recus/") ? d.recu.slice(0, 300) : null;
  const id = typeof d.id === "string" ? d.id : "";
  const reference = typeof d.reference === "string" ? d.reference.trim().slice(0, 80) : "";
  // Montant réellement payé au siège (facture) : sert à rendre le surplus ou prélever le déficit.
  const montantFacture = d.montantFacture === undefined || d.montantFacture === null ? null : Number(d.montantFacture);
  if (montantFacture !== null && (!Number.isInteger(montantFacture) || montantFacture <= 0 || montantFacture > 100_000_000)) {
    throw new HttpsError("invalid-argument", "Montant de la facture invalide.");
  }
  const prixFactures: Record<string, number> = {};
  if (d.prixFactures && typeof d.prixFactures === "object") {
    for (const [produitId, prix] of Object.entries(d.prixFactures as Record<string, unknown>).slice(0, 200)) {
      if (/^[\w-]{1,128}$/.test(produitId) && Number.isInteger(prix) && (prix as number) > 0) prixFactures[produitId] = prix as number;
    }
  }
  const ref = db.doc(`lotsValidation/${id}`);

  const lot = await db.runTransaction(async (tx) => {
    const lot = (await tx.get(ref)).data();
    if (!lot) throw new HttpsError("not-found", "Lot introuvable.");
    exigerMemePays(req, lot.pays);
    if (!["a_valider", "au_siege"].includes(lot.statut)) throw new HttpsError("failed-precondition", "Ce lot est déjà validé.");
    tx.update(ref, {
      statut: "valide",
      reference,
      ...(montantFacture !== null ? { montantFacture } : {}),
      ...(recu ? { recu } : {}),
      valideLe: FieldValue.serverTimestamp(),
      validePar: uid,
    });
    for (const commandeId of lot.commandes as string[]) {
      tx.update(db.doc(`orders/${commandeId}`), { valideeAuSiege: true, lotValidation: id });
    }
    return lot;
  });

  await journaliser("lot_valide_siege", uid, id, { reference, cc: lot.ccTotal, montantFacture });
  const regularisation = montantFacture !== null ? await regulariserLot(id, lot, montantFacture, prixFactures, uid) : null;
  await notifier(
    lot.fboId,
    "Validation au siège",
    `Vos produits ont été validés sur l'ID ${lot.idForever} : ${lot.ccTotal} CC.`,
    "",
    `lotvalide_${id}`,
  );
  return { id, regularisation };
});
