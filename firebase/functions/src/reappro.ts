import { FieldValue } from "firebase-admin/firestore";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { exigerRole } from "./commandes.js";
import { db, exigerMemePays, journaliser, MAX_INSTANCES, OPTIONS_APPEL, PAYS_DEFAUT } from "./commun.js";
import { onAppel } from "./limites.js";

/**
 * Réapprovisionnement : dès qu'un produit passe sous son seuil d'alerte dans un entrepôt, une demande
 * est ouverte (une seule à la fois par produit et entrepôt). Le gestionnaire la marque commandée,
 * puis reçue : la quantité reçue entre alors dans le stock et la demande se ferme.
 */
const ROLES_STOCK = ["gestionnaire_stock", "admin", "super_admin"];

export const surveillerStock = onDocumentWritten(
  { document: "stock/{id}", region: "europe-west1", maxInstances: MAX_INSTANCES },
  async (evenement) => {
    const stock = evenement.data?.after.data();
    if (!stock) return;
    const seuil = Number(stock.seuilAlerte ?? 10);
    const disponible = Number(stock.disponible ?? 0);
    // N'agit qu'au passage sous le seuil (ou à la création d'un stock déjà bas), pas à chaque vente.
    const avant = evenement.data?.before.data();
    const etaitAuDessus = !avant || Number(avant.disponible ?? 0) >= Number(avant.seuilAlerte ?? 10);
    if (disponible >= seuil || !etaitAuDessus) return;

    // L'identifiant du stock est « entrepôt_produit » : il sert si les champs manquent.
    const [entrepotDuNom, ...reste] = evenement.params.id.split("_");
    const entrepotId = String(stock.entrepotId ?? entrepotDuNom);
    const produitId = String(stock.produitId ?? reste.join("_"));

    const ref = db.doc(`reappros/${evenement.params.id}`);
    const produit = (await db.doc(`products/${produitId}`).get()).data();
    await db.runTransaction(async (tx) => {
      const demande = (await tx.get(ref)).data();
      if (demande && demande.statut !== "recue") {
        tx.update(ref, { disponible, updatedAt: FieldValue.serverTimestamp() });
        return;
      }
      tx.set(ref, {
        statut: "a_traiter",
        entrepotId,
        produitId,
        nom: produit?.nom ?? produitId,
        pays: stock.pays || PAYS_DEFAUT,
        disponible,
        seuil,
        // De quoi tenir : cinq fois le seuil d'alerte.
        quantiteConseillee: Math.max(seuil * 5 - disponible, seuil),
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    });
  },
);

export const suivreReappro = onAppel("suivreReappro", OPTIONS_APPEL, async (req) => {
  const uid = exigerRole(req, ROLES_STOCK);
  const d = (req.data ?? {}) as { id?: unknown; etape?: unknown; quantite?: unknown };
  const id = typeof d.id === "string" ? d.id : "";
  const ref = db.doc(`reappros/${id}`);

  await db.runTransaction(async (tx) => {
    const demande = (await tx.get(ref)).data();
    if (!demande) throw new HttpsError("not-found", "Demande introuvable.");
    exigerMemePays(req, demande.pays);

    if (d.etape === "commandee") {
      if (demande.statut !== "a_traiter") throw new HttpsError("failed-precondition", "Demande déjà commandée.");
      tx.update(ref, { statut: "commandee", commandeeLe: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
      return;
    }
    if (d.etape !== "recue") throw new HttpsError("invalid-argument", "Étape inconnue.");
    const quantite = Number(d.quantite);
    if (!Number.isInteger(quantite) || quantite < 1 || quantite > 100_000) throw new HttpsError("invalid-argument", "Quantité reçue invalide.");
    if (demande.statut === "recue") throw new HttpsError("failed-precondition", "Demande déjà reçue.");

    const refStock = db.doc(`stock/${id}`);
    await tx.get(refStock);
    tx.set(refStock, { disponible: FieldValue.increment(quantite) }, { merge: true });
    tx.create(db.collection("stockMovements").doc(), {
      type: "entree",
      entrepotId: demande.entrepotId,
      produitId: demande.produitId,
      quantite,
      reappro: id,
      auteurId: uid,
      date: FieldValue.serverTimestamp(),
    });
    tx.update(ref, { statut: "recue", quantiteRecue: quantite, recueLe: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
  });

  await journaliser("reappro_suivi", uid, id, { etape: d.etape });
  return { id };
});
