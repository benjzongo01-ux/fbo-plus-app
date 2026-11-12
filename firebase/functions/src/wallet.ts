import { FieldValue, Transaction } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { db, exigerAdmin, exigerConnexion, exigerMemePays, exigerSuperAdmin, journaliser, OPTIONS_APPEL, PAYS_DEFAUT } from "./commun.js";
import { onAppel } from "./limites.js";

/**
 * FBO+ Wallet : le portefeuille de chaque distributeur et de chaque livreur.
 *
 * Le serveur est la seule source du solde : aucun client n'écrit dans ces collections.
 * Chaque mouvement est inscrit dans `wallets/{uid}/mouvements`, avec le solde obtenu, et n'est
 * jamais modifié ensuite (sauf son statut, pour un retrait en attente).
 *
 * - Dépôt : demandé par l'utilisateur, crédité quand le paiement mobile est confirmé.
 *   Tant qu'aucun agrégateur n'est branché, le paiement est simulé (émulateurs uniquement).
 * - Retrait : le montant est mis de côté (bloqué) jusqu'à la décision de l'administration du pays ;
 *   validé, il part vers le numéro mobile money ; refusé, il revient dans le solde.
 * - Ajustement : correction manuelle, réservée au super administrateur, toujours motivée et journalisée.
 *
 * Les règles de répartition automatique (parts FBO, livreur, plateforme) viendront ensuite.
 */

/** Moyens de paiement mobile proposés dans chaque pays. */
export const OPERATEURS: Record<string, string[]> = {
  "Burkina Faso": ["Orange Money", "Moov Money"],
  "Côte d'Ivoire": ["Orange Money", "MTN MoMo", "Moov Money", "Wave"],
  "Sénégal": ["Orange Money", "Wave", "Free Money"],
  "Guinée": ["Orange Money", "MTN MoMo"],
};

/** Indicatif et nombre de chiffres d'un numéro mobile, par pays. */
const NUMEROS: Record<string, { indicatif: string; chiffres: number }> = {
  "Burkina Faso": { indicatif: "226", chiffres: 8 },
  "Côte d'Ivoire": { indicatif: "225", chiffres: 10 },
  "Sénégal": { indicatif: "221", chiffres: 9 },
  "Guinée": { indicatif: "224", chiffres: 9 },
};

const ROLES_WALLET = ["fbo", "livreur"];

/** Le paiement simulé n'existe que sur les émulateurs : jamais d'argent fictif en production. */
const paiementSimule = () => process.env.FUNCTIONS_EMULATOR === "true";

function texte(valeur: unknown, longueurMax = 200): string {
  return typeof valeur === "string" ? valeur.trim().slice(0, longueurMax) : "";
}

function montantPositif(valeur: unknown): number {
  if (!Number.isInteger(valeur) || (valeur as number) <= 0 || (valeur as number) > 100_000_000) {
    throw new HttpsError("invalid-argument", "Montant invalide.");
  }
  return valeur as number;
}

function cleIdempotence(valeur: unknown): string {
  const cle = texte(valeur, 64);
  if (!/^[A-Za-z0-9_-]{8,64}$/.test(cle)) throw new HttpsError("invalid-argument", "Clé d'idempotence invalide.");
  return cle;
}

/** Numéro mobile money au format international (+226XXXXXXXX). */
function numeroMobile(valeur: unknown, pays: string): string {
  const regle = NUMEROS[pays];
  let chiffres = texte(valeur, 30).replace(/\D/g, "");
  if (chiffres.startsWith("00")) chiffres = chiffres.slice(2);
  if (chiffres.startsWith(regle.indicatif) && chiffres.length === regle.indicatif.length + regle.chiffres) {
    chiffres = chiffres.slice(regle.indicatif.length);
  }
  if (chiffres.length !== regle.chiffres) {
    throw new HttpsError("invalid-argument", `Numéro mobile invalide : ${regle.chiffres} chiffres attendus.`);
  }
  return `+${regle.indicatif}${chiffres}`;
}

function operateur(valeur: unknown, pays: string): string {
  const choix = texte(valeur, 40);
  if (!OPERATEURS[pays]?.includes(choix)) {
    throw new HttpsError("invalid-argument", `Moyen de paiement non disponible : ${OPERATEURS[pays]?.join(", ")}.`);
  }
  return choix;
}

/** Réglages modifiables par le super administrateur dans `parametres/wallet`. */
async function reglages(tx: Transaction) {
  const p = (await tx.get(db.doc("parametres/wallet"))).data() ?? {};
  return {
    depotMin: Number(p.depotMin ?? 500),
    depotMax: Number(p.depotMax ?? 1_000_000),
    retraitMin: Number(p.retraitMin ?? 5000),
    retraitMax: Number(p.retraitMax ?? 500_000),
  };
}

const fcfa = (n: number) => `${n.toLocaleString("fr-FR").replace(/ /g, " ")} FCFA`;

/** Profil de l'utilisateur qui agit sur son propre wallet : distributeur ou livreur, et son pays. */
async function titulaire(tx: Transaction, uid: string, role: unknown) {
  if (!ROLES_WALLET.includes(String(role))) {
    throw new HttpsError("permission-denied", "Le wallet est réservé aux distributeurs et aux livreurs.");
  }
  const profil = (await tx.get(db.doc(`users/${uid}`))).data() ?? {};
  const pays = NUMEROS[String(profil.pays)] ? String(profil.pays) : PAYS_DEFAUT;
  const nom = [profil.prenom, profil.nom].filter(Boolean).join(" ") || String(profil.email ?? "");
  return { pays, nom, role: String(role) };
}

interface EtatWallet {
  solde: number;
  bloque: number;
  existe: boolean;
}

async function lireWallet(tx: Transaction, uid: string): Promise<EtatWallet> {
  const d = (await tx.get(db.doc(`wallets/${uid}`))).data();
  return { solde: Number(d?.solde ?? 0), bloque: Number(d?.bloque ?? 0), existe: !!d };
}

/**
 * Écrit le nouveau solde et le mouvement correspondant (à appeler après toutes les lectures).
 * `delta` modifie le solde disponible, `deltaBloque` le montant mis de côté.
 */
function inscrire(
  tx: Transaction,
  uid: string,
  wallet: EtatWallet,
  identite: { pays: string; nom: string; role: string } | null,
  m: { delta: number; deltaBloque?: number; type: string; libelle: string; reference: string; statut: string; auteurId: string },
) {
  const solde = wallet.solde + m.delta;
  const bloque = wallet.bloque + (m.deltaBloque ?? 0);
  if (solde < 0 || bloque < 0) throw new HttpsError("failed-precondition", "Solde insuffisant.");
  const ref = db.doc(`wallets/${uid}`);
  tx.set(
    ref,
    {
      uid,
      solde,
      bloque,
      ...(identite ? { pays: identite.pays, nom: identite.nom, role: identite.role } : {}),
      ...(m.delta > 0 ? { totalEntrees: FieldValue.increment(m.delta) } : {}),
      ...(m.delta < 0 && m.type !== "retrait" ? { totalSorties: FieldValue.increment(-m.delta) } : {}),
      ...(wallet.existe ? {} : { creeLe: FieldValue.serverTimestamp() }),
      majLe: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
  const mouvement = ref.collection("mouvements").doc();
  tx.create(mouvement, {
    type: m.type,
    montant: m.delta,
    soldeApres: solde,
    libelle: m.libelle,
    reference: m.reference,
    statut: m.statut,
    auteurId: m.auteurId,
    creeLe: FieldValue.serverTimestamp(),
  });
  wallet.solde = solde;
  wallet.bloque = bloque;
  wallet.existe = true;
  return mouvement.id;
}

/** Démarre un dépôt par mobile money. Le wallet n'est crédité qu'à la confirmation du paiement. */
export const demarrerDepot = onAppel("demarrerDepot", OPTIONS_APPEL, async (req) => {
  const uid = exigerConnexion(req);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const cle = cleIdempotence(d.cleIdempotence);
  const ref = db.doc(`depots/${uid}_${cle}`);

  return db.runTransaction(async (tx) => {
    const deja = await tx.get(ref);
    if (deja.exists) return { id: ref.id, statut: deja.data()!.statut, simule: deja.data()!.fournisseur === "simule" };
    const qui = await titulaire(tx, uid, req.auth?.token.role);
    const r = await reglages(tx);
    const montant = montantPositif(d.montant);
    if (montant < r.depotMin || montant > r.depotMax) {
      throw new HttpsError("invalid-argument", `Dépôt entre ${fcfa(r.depotMin)} et ${fcfa(r.depotMax)}.`);
    }
    if (!paiementSimule()) {
      // Branchement de l'agrégateur de paiement au lancement (CinetPay, PayDunya…).
      throw new HttpsError("unavailable", "Les dépôts ne sont pas encore ouverts.");
    }
    tx.create(ref, {
      uid,
      nom: qui.nom,
      pays: qui.pays,
      montant,
      operateur: operateur(d.operateur, qui.pays),
      numero: numeroMobile(d.numero, qui.pays),
      statut: "en_attente",
      fournisseur: "simule",
      creeLe: FieldValue.serverTimestamp(),
    });
    return { id: ref.id, statut: "en_attente", simule: true };
  });
});

/**
 * Émulateurs uniquement : joue la confirmation (ou le refus) du paiement sur le téléphone du client.
 * Au lancement, c'est la notification de l'agrégateur qui appellera la même logique.
 */
export const confirmerDepotSimule = onAppel("confirmerDepotSimule", OPTIONS_APPEL, async (req) => {
  const uid = exigerConnexion(req);
  if (!paiementSimule()) throw new HttpsError("permission-denied", "Paiement simulé indisponible.");
  const d = (req.data ?? {}) as Record<string, unknown>;
  const ref = db.doc(`depots/${texte(d.id, 128)}`);

  return db.runTransaction(async (tx) => {
    const depot = (await tx.get(ref)).data();
    if (!depot || depot.uid !== uid) throw new HttpsError("not-found", "Dépôt introuvable.");
    if (depot.statut !== "en_attente") return { statut: depot.statut };
    if (d.succes !== true) {
      tx.update(ref, { statut: "echoue", termineLe: FieldValue.serverTimestamp() });
      return { statut: "echoue" };
    }
    const wallet = await lireWallet(tx, uid);
    const qui = await titulaire(tx, uid, req.auth?.token.role);
    inscrire(tx, uid, wallet, qui, {
      delta: Number(depot.montant),
      type: "depot",
      libelle: `Dépôt ${depot.operateur}`,
      reference: ref.id,
      statut: "termine",
      auteurId: uid,
    });
    tx.update(ref, { statut: "confirme", termineLe: FieldValue.serverTimestamp() });
    return { statut: "confirme", solde: wallet.solde };
  });
});

/** Demande de retrait vers un numéro mobile money : le montant est bloqué jusqu'à la décision. */
export const demanderRetrait = onAppel("demanderRetrait", OPTIONS_APPEL, async (req) => {
  const uid = exigerConnexion(req);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const cle = cleIdempotence(d.cleIdempotence);
  const ref = db.doc(`retraits/${uid}_${cle}`);

  return db.runTransaction(async (tx) => {
    const deja = await tx.get(ref);
    if (deja.exists) return { id: ref.id, statut: deja.data()!.statut };
    const qui = await titulaire(tx, uid, req.auth?.token.role);
    const r = await reglages(tx);
    const wallet = await lireWallet(tx, uid);
    const montant = montantPositif(d.montant);
    if (montant < r.retraitMin || montant > r.retraitMax) {
      throw new HttpsError("invalid-argument", `Retrait entre ${fcfa(r.retraitMin)} et ${fcfa(r.retraitMax)}.`);
    }
    if (montant > wallet.solde) {
      throw new HttpsError("failed-precondition", `Solde insuffisant : ${fcfa(wallet.solde)} disponibles.`);
    }
    const choix = operateur(d.operateur, qui.pays);
    const numero = numeroMobile(d.numero, qui.pays);
    const mouvementId = inscrire(tx, uid, wallet, qui, {
      delta: -montant,
      deltaBloque: montant,
      type: "retrait",
      libelle: `Retrait ${choix}`,
      reference: ref.id,
      statut: "en_attente",
      auteurId: uid,
    });
    tx.create(ref, {
      uid,
      nom: qui.nom,
      role: qui.role,
      pays: qui.pays,
      montant,
      operateur: choix,
      numero,
      statut: "en_attente",
      mouvementId,
      creeLe: FieldValue.serverTimestamp(),
    });
    return { id: ref.id, statut: "en_attente" };
  });
});

/** L'administration du pays valide (paiement) ou refuse (remboursement) une demande de retrait. */
export const deciderRetrait = onAppel("deciderRetrait", OPTIONS_APPEL, async (req) => {
  const admin = exigerAdmin(req);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const ref = db.doc(`retraits/${texte(d.id, 128)}`);
  const valider = d.decision === "valider";
  if (!valider && d.decision !== "refuser") throw new HttpsError("invalid-argument", "Décision invalide.");
  const motif = texte(d.motif, 300);
  if (!valider && !motif) throw new HttpsError("invalid-argument", "Indiquez le motif du refus.");

  const resultat = await db.runTransaction(async (tx) => {
    const retrait = (await tx.get(ref)).data();
    if (!retrait) throw new HttpsError("not-found", "Demande de retrait introuvable.");
    exigerMemePays(req, retrait.pays);
    if (retrait.statut !== "en_attente") throw new HttpsError("failed-precondition", "Cette demande a déjà été traitée.");
    const uid = String(retrait.uid);
    const montant = Number(retrait.montant);
    const wallet = await lireWallet(tx, uid);
    const mouvement = db.doc(`wallets/${uid}/mouvements/${retrait.mouvementId}`);

    if (valider) {
      // Versement simulé tant que l'agrégateur n'est pas branché : payé aussitôt.
      tx.set(db.doc(`wallets/${uid}`), { bloque: wallet.bloque - montant, totalSorties: FieldValue.increment(montant), majLe: FieldValue.serverTimestamp() }, { merge: true });
      tx.update(mouvement, { statut: "paye" });
      tx.update(ref, { statut: "paye", decidePar: admin.uid, decideLe: FieldValue.serverTimestamp(), simule: paiementSimule() });
      return "paye";
    }
    tx.update(mouvement, { statut: "refuse" });
    inscrire(tx, uid, wallet, null, {
      delta: montant,
      deltaBloque: -montant,
      type: "remboursement",
      libelle: "Retrait refusé : montant rendu",
      reference: ref.id,
      statut: "termine",
      auteurId: admin.uid,
    });
    tx.update(ref, { statut: "refuse", motif, decidePar: admin.uid, decideLe: FieldValue.serverTimestamp() });
    return "refuse";
  });
  await journaliser(`retrait_${resultat}`, admin.uid, ref.id, motif ? { motif } : {});
  return { statut: resultat };
});

/** Correction manuelle d'un wallet (crédit ou débit), réservée au super administrateur. */
export const ajusterWallet = onAppel("ajusterWallet", OPTIONS_APPEL, async (req) => {
  const sa = exigerSuperAdmin(req);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const uid = texte(d.uid, 128);
  const montant = d.montant;
  if (!Number.isInteger(montant) || montant === 0 || Math.abs(montant as number) > 100_000_000) {
    throw new HttpsError("invalid-argument", "Montant invalide.");
  }
  const motif = texte(d.motif, 300);
  if (motif.length < 3) throw new HttpsError("invalid-argument", "Indiquez le motif de l'ajustement.");

  const solde = await db.runTransaction(async (tx) => {
    const profil = (await tx.get(db.doc(`users/${uid}`))).data();
    if (!profil || !ROLES_WALLET.includes(String(profil.role))) {
      throw new HttpsError("not-found", "Distributeur ou livreur introuvable.");
    }
    const wallet = await lireWallet(tx, uid);
    const pays = NUMEROS[String(profil.pays)] ? String(profil.pays) : PAYS_DEFAUT;
    const nom = [profil.prenom, profil.nom].filter(Boolean).join(" ") || String(profil.email ?? "");
    inscrire(tx, uid, wallet, { pays, nom, role: String(profil.role) }, {
      delta: montant as number,
      type: "ajustement",
      libelle: motif,
      reference: "",
      statut: "termine",
      auteurId: sa.uid,
    });
    return wallet.solde;
  });
  await journaliser("wallet_ajuste", sa.uid, uid, { montant, motif });
  return { solde };
});
