import { createHash } from "node:crypto";
import { FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { db, exigerConnexion, exigerMemePays, journaliser, OPTIONS_APPEL, PAYS_DEFAUT } from "./commun.js";
import { onAppel } from "./limites.js";
import { notifier } from "./notifications.js";
import { preparerRepartition } from "./repartition.js";
import { identiteDe, lireWallet, mouvementSysteme, operateurDuPays } from "./wallet.js";

/**
 * Paiements par les numéros de dépôt de FBO+ (sans agrégateur), vérifiés par l'administration.
 *
 * Le client (à la livraison) ou le distributeur et le livreur (recharge de wallet) paient sur un
 * numéro marchand de FBO+ (Wave, Orange, Moov, MTN… réglés dans `parametres/numerosDepot`), puis
 * déclarent l'opérateur et la référence de la transaction (et une capture, facultative).
 * L'administration du pays vérifie dans le compte marchand :
 * - reçu : paiement de commande confirmé (la répartition automatique se fait), ou wallet crédité ;
 * - refusé : rien n'est crédité ; une livraison déjà remise repasse en « argent à récupérer ».
 *
 * Sécurité : le montant d'une commande vient du serveur ; une référence ne peut être déclarée
 * qu'une fois (verrou `referencesPaiement`, illisible par les clients) ; seule l'administration du
 * pays décide ; chaque décision est journalisée et ne peut pas être prise deux fois.
 */

const ROLES_DECISION = ["admin", "super_admin"];

function texte(valeur: unknown, longueurMax = 200): string {
  return typeof valeur === "string" ? valeur.trim().slice(0, longueurMax) : "";
}

/** Référence de transaction de l'opérateur, normalisée (majuscules, sans espaces). */
function referenceTransaction(valeur: unknown): string {
  const ref = texte(valeur, 60).toUpperCase().replace(/\s+/g, "");
  if (!/^[A-Z0-9.\-_/]{4,40}$/.test(ref)) {
    throw new HttpsError("invalid-argument", "Référence de transaction invalide : recopiez-la depuis le SMS de l'opérateur.");
  }
  return ref;
}

const cleVerrou = (operateur: string, reference: string) =>
  createHash("sha256").update(`${operateur}|${reference}`).digest("hex").slice(0, 40);

function capture(valeur: unknown, uid: string): string | null {
  const chemin = texte(valeur, 300);
  if (!chemin) return null;
  if (!chemin.startsWith(`declarations/${uid}/`)) throw new HttpsError("invalid-argument", "Capture invalide.");
  return chemin;
}

/**
 * Déclare un paiement fait sur un numéro de dépôt de FBO+.
 * - type « commande » : le livreur de la livraison (ou le distributeur de la commande) ;
 * - type « recharge » : un distributeur ou un livreur, pour son propre wallet.
 */
export const declarerPaiement = onAppel("declarerPaiement", OPTIONS_APPEL, async (req) => {
  const uid = exigerConnexion(req);
  const role = String(req.auth?.token.role ?? "");
  const d = (req.data ?? {}) as Record<string, unknown>;
  const type = d.type === "recharge" ? "recharge" : d.type === "commande" ? "commande" : null;
  if (!type) throw new HttpsError("invalid-argument", "Type de paiement invalide.");
  const reference = referenceTransaction(d.reference);
  const chemin = capture(d.capture, uid);

  const resultat = await db.runTransaction(async (tx) => {
    let pays: string;
    let montant: number;
    let fboId: string;
    let commandeId: string | null = null;
    let refCommande: FirebaseFirestore.DocumentReference | null = null;
    let declarationRef: FirebaseFirestore.DocumentReference;

    if (type === "commande") {
      commandeId = texte(d.id, 200);
      refCommande = db.doc(`orders/${commandeId}`);
      const commande = (await tx.get(refCommande)).data();
      if (!commande) throw new HttpsError("not-found", "Commande introuvable.");
      const livraison = (await tx.get(db.doc(`deliveries/${commandeId}`))).data();
      const estLivreur = role === "livreur" && livraison?.livreurId === uid;
      const estDistributeur = role === "fbo" && commande.fboId === uid;
      if (!estLivreur && !estDistributeur) throw new HttpsError("permission-denied", "Cette commande ne vous concerne pas.");
      if (commande.mode === "validation") throw new HttpsError("failed-precondition", "Une validation se paie avec le wallet.");
      if (["annulee", "retournee"].includes(commande.statut)) throw new HttpsError("failed-precondition", "Commande annulée ou retournée.");
      if (["a_verifier", "confirme"].includes(commande.paiement?.statut)) {
        throw new HttpsError("failed-precondition", "Un paiement est déjà déclaré pour cette commande.");
      }
      pays = String(commande.pays || PAYS_DEFAUT);
      // Le montant est celui de la commande, jamais celui saisi.
      montant = Number(livraison?.montantAEncaisser ?? commande.total);
      fboId = String(commande.fboId);
      declarationRef = db.collection("declarations").doc();
    } else {
      if (!["fbo", "livreur"].includes(role)) throw new HttpsError("permission-denied", "Recharge réservée aux distributeurs et aux livreurs.");
      const cle = texte(d.cleIdempotence, 64);
      if (!/^[\w-]{8,64}$/.test(cle)) throw new HttpsError("invalid-argument", "Clé d'idempotence invalide.");
      declarationRef = db.doc(`declarations/${uid}_${cle}`);
      const deja = (await tx.get(declarationRef)).data();
      if (deja) return { id: declarationRef.id, statut: deja.statut, montant: deja.montant, deja: true };
      const identite = await identiteDe(tx, uid);
      pays = identite.pays;
      const p = (await tx.get(db.doc("parametres/wallet"))).data() ?? {};
      const min = Number(p.depotMin ?? 500);
      const max = Number(p.depotMax ?? 1_000_000);
      montant = Number(d.montant);
      if (!Number.isInteger(montant) || montant < min || montant > max) {
        throw new HttpsError("invalid-argument", `Dépôt entre ${min} et ${max} FCFA.`);
      }
      fboId = uid;
    }
    const operateur = operateurDuPays(d.operateur, pays);
    const verrou = db.doc(`referencesPaiement/${cleVerrou(operateur, reference)}`);
    if ((await tx.get(verrou)).exists) {
      throw new HttpsError("already-exists", "Cette référence de transaction a déjà été déclarée.");
    }
    const declarant = await identiteDe(tx, uid);

    // Écritures.
    tx.create(verrou, { operateur, reference, declarationId: declarationRef.id, creeLe: FieldValue.serverTimestamp() });
    tx.create(declarationRef, {
      type,
      uid,
      declarantNom: declarant.nom,
      declarantRole: role,
      fboId,
      commandeId,
      pays,
      montant,
      operateur,
      reference,
      capture: chemin,
      statut: "en_attente",
      creeLe: FieldValue.serverTimestamp(),
    });
    if (refCommande) {
      tx.update(refCommande, {
        paiement: { mode: "mobile_money", statut: "a_verifier", operateur, reference, montant, declarationId: declarationRef.id },
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
    return { id: declarationRef.id, statut: "en_attente", montant, deja: false };
  });

  if (!resultat.deja) await journaliser(`paiement_declare_${type}`, uid, resultat.id, { reference });
  return { id: resultat.id, statut: resultat.statut, montant: resultat.montant };
});

/** L'administration du pays confirme (argent bien reçu) ou refuse un paiement déclaré. */
export const deciderDeclaration = onAppel("deciderDeclaration", OPTIONS_APPEL, async (req) => {
  const uid = exigerConnexion(req);
  if (!ROLES_DECISION.includes(String(req.auth?.token.role))) {
    throw new HttpsError("permission-denied", "Vérification réservée aux administrateurs.");
  }
  const d = (req.data ?? {}) as Record<string, unknown>;
  const ref = db.doc(`declarations/${texte(d.id, 200)}`);
  const accepter = d.accepter === true;
  const motif = texte(d.motif, 300);
  if (!accepter && !motif) throw new HttpsError("invalid-argument", "Indiquez le motif du refus.");

  const decl = await db.runTransaction(async (tx) => {
    const declaration = (await tx.get(ref)).data();
    if (!declaration) throw new HttpsError("not-found", "Déclaration introuvable.");
    exigerMemePays(req, declaration.pays);
    if (declaration.statut !== "en_attente") throw new HttpsError("failed-precondition", "Ce paiement a déjà été traité.");
    const refCommande = declaration.commandeId ? db.doc(`orders/${declaration.commandeId}`) : null;
    const refLivraison = declaration.commandeId ? db.doc(`deliveries/${declaration.commandeId}`) : null;

    // Lectures.
    const livraison = refLivraison ? (await tx.get(refLivraison)).data() : undefined;
    let repartir: (() => void) | null = null;
    let crediter: (() => void) | null = null;
    if (accepter && declaration.type === "commande" && livraison?.statut === "livree" && livraison.encaissement === "mobile_money") {
      repartir = await preparerRepartition(tx, refCommande!, uid);
    }
    if (accepter && declaration.type === "recharge") {
      const wallet = await lireWallet(tx, declaration.uid);
      const identite = await identiteDe(tx, declaration.uid);
      crediter = () =>
        mouvementSysteme(tx, declaration.uid, wallet, identite, {
          montant: Number(declaration.montant),
          type: "depot",
          libelle: `Dépôt ${declaration.operateur} · réf. ${declaration.reference}`,
          reference: ref.id,
          auteurId: uid,
        });
    }

    // Écritures.
    tx.update(ref, {
      statut: accepter ? "confirme" : "refuse",
      ...(motif ? { motif } : {}),
      decidePar: uid,
      decideLe: FieldValue.serverTimestamp(),
    });
    if (refCommande) {
      tx.update(refCommande, {
        "paiement.statut": accepter ? "confirme" : "refuse",
        ...(accepter ? {} : { "paiement.motif": motif }),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
    if (!accepter) {
      // Référence libérée : une déclaration corrigée reste possible.
      tx.delete(db.doc(`referencesPaiement/${cleVerrou(declaration.operateur, declaration.reference)}`));
      // Livraison déjà remise sans argent reçu : l'argent reste à récupérer (comme des espèces).
      if (refLivraison && livraison?.statut === "livree" && livraison.encaissement === "mobile_money") {
        tx.update(refLivraison, { encaissement: "especes", especesRemises: false, montantEncaisse: Number(declaration.montant), updatedAt: FieldValue.serverTimestamp() });
      }
    }
    repartir?.();
    crediter?.();
    return declaration;
  });

  await journaliser(accepter ? "paiement_confirme" : "paiement_refuse", uid, ref.id, motif ? { motif } : {});
  const montant = `${Number(decl.montant).toLocaleString("fr-FR")} FCFA`;
  await notifier(
    String(decl.uid),
    accepter ? "Paiement confirmé" : "Paiement refusé",
    accepter
      ? decl.type === "recharge" ? `Votre dépôt de ${montant} est crédité sur votre wallet.` : `Le paiement de ${montant} est confirmé.`
      : `Le paiement de ${montant} (réf. ${decl.reference}) n'a pas été retrouvé : ${motif}`,
    String(decl.commandeId ?? ""),
    `declaration_${ref.id}`,
  ).catch(() => {});
  return { statut: accepter ? "confirme" : "refuse" };
});

