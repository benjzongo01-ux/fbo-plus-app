import { randomBytes } from "node:crypto";
import { DocumentReference, FieldValue, Transaction } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { HttpsError } from "firebase-functions/v2/https";
import { db, exigerMemePays, journaliser, OPTIONS_APPEL, PAYS_DEFAUT } from "./commun.js";
import { onAppel } from "./limites.js";
import { notifier } from "./notifications.js";
import { identiteDe, lireWallet, mouvementSysteme, numeroMobile, operateurDuPays, paiementSimule } from "./wallet.js";

/**
 * Répartition automatique de l'argent d'une commande livrée, puis régularisation au siège.
 *
 * 1. Le client paie le prix annoncé par le distributeur (et la livraison si elle lui est facturée) :
 *    par mobile money au moment de la livraison (de préférence), ou en espèces au livreur.
 * 2. Dès que l'argent est sur la plateforme (paiement confirmé, ou espèces remises et confirmées
 *    par l'administration), il est réparti en une seule transaction :
 *    - réserve de validation : ce que le siège facturera (prix appris des factures, sinon prix
 *      public moins la remise du grade, ajusté par le coefficient du pays) ;
 *    - livraison : commission du livreur (dans son wallet), le reste à la plateforme ;
 *    - commission FBO+ (réglable par pays, nulle par défaut) ;
 *    - bénéfice du distributeur : tout le reste, dans son wallet. Négatif, il devient une dette.
 * 3. À la validation du lot au siège, la facture réelle est saisie : surplus rendu au distributeur,
 *    déficit prélevé (dette si le solde ne suffit pas). Les prix facturés sont retenus pour la suite.
 *
 * Sécurité : tous les montants viennent du serveur (jamais de l'application), chaque répartition
 * est faite une seule fois (drapeau dans la commande, dans la même transaction), la somme des
 * parts est vérifiée égale à l'encaissement, et tout est journalisé.
 */

const ROLES_CAISSE = ["operateur", "admin", "super_admin"];

interface Ligne {
  produitId: string;
  quantite: number;
  prixUnitaire: number;
}

const cleePrix = (pays: string, remise: number, produitId: string) => `${pays}__${remise}__${produitId}`;

/** Réglages financiers d'un pays (tarifsPays), avec des valeurs prudentes par défaut. */
async function finances(tx: Transaction, pays: string) {
  const t = (await tx.get(db.doc(`tarifsPays/${pays || PAYS_DEFAUT}`))).data() ?? {};
  const coef = Number(t.coefValidation ?? 1);
  return {
    // Taxes et timbres du siège, appris des factures (1 = aucune différence avec le prix remisé).
    coefValidation: coef >= 0.8 && coef <= 1.5 ? coef : 1,
    // Petite marge retenue en plus, rendue à la régularisation (0 % par défaut).
    margeSecuritePct: Math.min(Math.max(Number(t.margeSecuritePct ?? 0), 0), 10),
    commissionPlateformeFixe: Math.max(Math.trunc(Number(t.commissionPlateformeFixe ?? 0)), 0),
    commissionPlateformePct: Math.min(Math.max(Number(t.commissionPlateformePct ?? 0), 0), 50),
  };
}

/** Ce que le siège devrait facturer pour ces produits, à ce grade, dans ce pays. Lectures seules. */
async function estimerValidation(tx: Transaction, pays: string, remise: number, lignes: Ligne[], coef: number) {
  const appris = lignes.length ? await tx.getAll(...lignes.map((l) => db.doc(`prixValidation/${cleePrix(pays, remise, l.produitId)}`))) : [];
  let total = 0;
  lignes.forEach((l, i) => {
    const prixAppris = appris[i]?.data()?.prix;
    const unitaire = Number.isInteger(prixAppris) ? (prixAppris as number) : Math.ceil(l.prixUnitaire * (1 - remise / 100) * coef);
    total += unitaire * l.quantite;
  });
  return total;
}

/**
 * Prépare la répartition d'une commande (toutes les lectures), et rend la fonction qui l'écrit.
 * À appeler dans la transaction, avant toute écriture.
 */
export async function preparerRepartition(tx: Transaction, refCommande: DocumentReference, auteurId: string) {
  const commande = (await tx.get(refCommande)).data();
  if (!commande) throw new HttpsError("not-found", "Commande introuvable.");
  if (commande.repartition) return null; // déjà faite : jamais deux fois
  const livraison = (await tx.get(db.doc(`deliveries/${refCommande.id}`))).data();
  const pays = String(commande.pays || PAYS_DEFAUT);
  const f = await finances(tx, pays);
  const remise = Number(commande.remiseGrade ?? 0);
  const lignes = (commande.lignes ?? []) as Ligne[];

  const encaisse = Number(commande.total);
  const frais = Number(commande.fraisLivraison ?? 0);
  const commissionLivreur = livraison?.livreurId ? Math.min(Number(livraison.commission ?? 0), frais) : 0;
  const estimation = await estimerValidation(tx, pays, remise, lignes, f.coefValidation);
  const reserve = estimation + Math.ceil((estimation * f.margeSecuritePct) / 100);
  const avantCommission = encaisse - reserve - frais;
  const commissionPlateforme = Math.max(
    0,
    Math.min(f.commissionPlateformeFixe + Math.floor((Math.max(avantCommission, 0) * f.commissionPlateformePct) / 100), Math.max(avantCommission, 0)),
  );
  const benefice = avantCommission - commissionPlateforme;
  const partLivraisonPlateforme = frais - commissionLivreur;
  // Garde-fou : chaque franc encaissé a une destination, ni plus ni moins.
  if (reserve + commissionLivreur + partLivraisonPlateforme + commissionPlateforme + benefice !== encaisse) {
    throw new HttpsError("internal", "Répartition incohérente : opération annulée.");
  }

  const fboId = String(commande.fboId);
  const walletFbo = await lireWallet(tx, fboId);
  const idFbo = await identiteDe(tx, fboId);
  const livreurId = livraison?.livreurId ? String(livraison.livreurId) : null;
  const walletLivreur = livreurId ? await lireWallet(tx, livreurId) : null;
  const idLivreur = livreurId ? await identiteDe(tx, livreurId) : null;
  const numero = String(commande.titre || refCommande.id);

  return () => {
    const repartition = {
      encaisse,
      reserveValidation: reserve,
      estimationSiege: estimation,
      fraisLivraison: frais,
      commissionLivreur,
      partLivraisonPlateforme,
      commissionPlateforme,
      benefice,
      livreurId,
      le: FieldValue.serverTimestamp(),
      par: auteurId,
    };
    tx.update(refCommande, { repartition, updatedAt: FieldValue.serverTimestamp() });
    mouvementSysteme(tx, fboId, walletFbo, idFbo, {
      montant: benefice,
      type: benefice >= 0 ? "benefice" : "deficit",
      libelle: benefice >= 0 ? `Bénéfice ${numero}` : `Frais au-delà du prix client, ${numero}`,
      reference: refCommande.id,
      auteurId,
    });
    if (livreurId && walletLivreur && commissionLivreur > 0) {
      mouvementSysteme(tx, livreurId, walletLivreur, idLivreur, {
        montant: commissionLivreur,
        type: "commission",
        libelle: `Livraison ${numero}`,
        reference: refCommande.id,
        auteurId,
      });
    }
    const compte = db.doc(`comptesPlateforme/${pays}`);
    tx.set(
      compte,
      {
        pays,
        encaisse: FieldValue.increment(encaisse),
        reserveValidation: FieldValue.increment(reserve),
        livraison: FieldValue.increment(partLivraisonPlateforme),
        commissions: FieldValue.increment(commissionPlateforme),
        majLe: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );
    tx.create(compte.collection("ecritures").doc(), {
      type: "repartition",
      commandeId: refCommande.id,
      fboId,
      ...repartition,
    });
  };
}

// ───────────────────────── Paiement à la livraison ─────────────────────────

/**
 * Le livreur demande le paiement mobile money du client (QR code ou numéro du client).
 * Le montant est celui de la commande, jamais celui saisi par le livreur.
 */
export const demanderPaiementLivraison = onAppel("demanderPaiementLivraison", OPTIONS_APPEL, async (req) => {
  const uid = req.auth?.uid;
  if (!uid || req.auth?.token.role !== "livreur") throw new HttpsError("permission-denied", "Réservé aux livreurs.");
  const d = (req.data ?? {}) as Record<string, unknown>;
  const id = typeof d.id === "string" ? d.id.slice(0, 200) : "";
  const refPaiement = db.doc(`paiements/${id}`);

  return db.runTransaction(async (tx) => {
    const livraison = (await tx.get(db.doc(`deliveries/${id}`))).data();
    if (!livraison || livraison.livreurId !== uid) throw new HttpsError("permission-denied", "Cette mission ne vous est pas assignée.");
    if (livraison.statut !== "en_livraison") throw new HttpsError("failed-precondition", "La livraison n'est pas en cours.");
    const commande = (await tx.get(db.doc(`orders/${id}`))).data()!;
    const existant = (await tx.get(refPaiement)).data();
    if (existant?.statut === "confirme") return { statut: "confirme", montant: existant.montant, reference: existant.reference };
    if (!paiementSimule()) throw new HttpsError("unavailable", "Le paiement mobile n'est pas encore ouvert : encaissez en espèces.");
    const pays = String(commande.pays || PAYS_DEFAUT);
    const reference = `FBO-${randomBytes(4).toString("hex").toUpperCase()}`;
    const montant = Number(livraison.montantAEncaisser);
    tx.set(refPaiement, {
      commandeId: id,
      livreurId: uid,
      fboId: commande.fboId,
      pays,
      montant,
      operateur: operateurDuPays(d.operateur, pays),
      numero: d.numero ? numeroMobile(d.numero, pays) : null,
      reference,
      statut: "en_attente",
      fournisseur: "simule",
      creeLe: FieldValue.serverTimestamp(),
    });
    return { statut: "en_attente", montant, reference, qr: `https://pay.fboplus.app/p/${reference}` };
  });
});

/** Émulateurs uniquement : joue la confirmation du client (au lancement : notification de l'agrégateur). */
export const confirmerPaiementSimule = onAppel("confirmerPaiementSimule", OPTIONS_APPEL, async (req) => {
  const uid = req.auth?.uid;
  if (!paiementSimule()) throw new HttpsError("permission-denied", "Paiement simulé indisponible.");
  const d = (req.data ?? {}) as Record<string, unknown>;
  const id = typeof d.id === "string" ? d.id.slice(0, 200) : "";
  const ref = db.doc(`paiements/${id}`);
  return db.runTransaction(async (tx) => {
    const p = (await tx.get(ref)).data();
    if (!p || p.livreurId !== uid) throw new HttpsError("not-found", "Paiement introuvable.");
    if (p.statut !== "en_attente") return { statut: p.statut };
    const statut = d.succes === true ? "confirme" : "echoue";
    tx.update(ref, { statut, termineLe: FieldValue.serverTimestamp() });
    if (statut === "confirme") {
      tx.update(db.doc(`orders/${id}`), {
        paiement: { mode: "mobile_money", statut, montant: p.montant, operateur: p.operateur, reference: p.reference },
      });
    }
    return { statut };
  });
});

/** L'administration confirme que le livreur a remis les espèces : la répartition se fait alors. */
export const confirmerRemiseEspeces = onAppel("confirmerRemiseEspeces", OPTIONS_APPEL, async (req) => {
  const uid = req.auth?.uid;
  if (!uid || !ROLES_CAISSE.includes(String(req.auth?.token.role))) {
    throw new HttpsError("permission-denied", "Action réservée au centre d'appel et à l'administration.");
  }
  const d = (req.data ?? {}) as Record<string, unknown>;
  const id = typeof d.id === "string" ? d.id.slice(0, 200) : "";
  const refCommande = db.doc(`orders/${id}`);
  const refLivraison = db.doc(`deliveries/${id}`);

  const resultat = await db.runTransaction(async (tx) => {
    const livraison = (await tx.get(refLivraison)).data();
    if (!livraison) throw new HttpsError("not-found", "Livraison introuvable.");
    const commande = (await tx.get(refCommande)).data()!;
    exigerMemePays(req, commande.pays);
    if (livraison.statut !== "livree" || livraison.encaissement !== "especes") {
      throw new HttpsError("failed-precondition", "Aucune espèce à remettre pour cette livraison.");
    }
    if (livraison.especesRemises === true) return "deja";
    const ecrire = await preparerRepartition(tx, refCommande, uid);
    tx.update(refLivraison, { especesRemises: true, especesRecuesPar: uid, especesRecuesLe: FieldValue.serverTimestamp() });
    ecrire?.();
    return "ok";
  });
  if (resultat === "ok") await journaliser("especes_remises", uid, id);
  return { statut: resultat };
});

// ───────────────────────── Régularisation au siège ─────────────────────────

/**
 * Appelée à la validation d'un lot au siège, avec la facture réelle. Rend le surplus ou prélève
 * le déficit, et retient les prix facturés pour les prochaines commandes.
 */
export async function regulariserLot(
  lotId: string,
  lot: Record<string, unknown>,
  montantFacture: number,
  prixFactures: Record<string, number>,
  auteurId: string,
) {
  const pays = String(lot.pays || PAYS_DEFAUT);
  const fboId = String(lot.fboId);
  const ids = (lot.commandes as string[]) ?? [];

  const bilan = await db.runTransaction(async (tx) => {
    const commandes = ids.length ? await tx.getAll(...ids.map((i) => db.doc(`orders/${i}`))) : [];
    const lignes = (lot.lignes as Array<Ligne & { commandeId: string }>) ?? [];
    let theoriqueTotal = 0;
    let theoriqueReparti = 0;
    let reserveRetenue = 0;
    const aRegulariser: DocumentReference[] = [];
    let remiseLot = 0;
    for (const snap of commandes) {
      const c = snap.data();
      if (!c) continue;
      const remise = Number(c.remiseGrade ?? 0);
      remiseLot = remise;
      const theorique = lignes
        .filter((l) => l.commandeId === snap.id)
        .reduce((s, l) => s + Math.ceil(l.prixUnitaire * (1 - remise / 100)) * l.quantite, 0);
      theoriqueTotal += theorique;
      if (c.repartition && !c.regularisation) {
        theoriqueReparti += theorique;
        reserveRetenue += Number(c.repartition.reserveValidation ?? 0);
        aRegulariser.push(snap.ref);
      }
    }
    const tarifs = (await tx.get(db.doc(`tarifsPays/${pays}`))).data() ?? {};
    const wallet = await lireWallet(tx, fboId);
    const identite = await identiteDe(tx, fboId);

    // Part de la facture qui revient aux commandes déjà réparties (un lot peut en mélanger).
    const factureAttribuable = theoriqueTotal > 0 ? Math.round((montantFacture * theoriqueReparti) / theoriqueTotal) : 0;
    const ecart = reserveRetenue - factureAttribuable;

    // Écritures.
    if (aRegulariser.length > 0 && ecart !== 0) {
      mouvementSysteme(tx, fboId, wallet, identite, {
        montant: ecart,
        type: "regularisation",
        libelle: ecart > 0 ? `Surplus de validation rendu, lot ${lotId.slice(0, 6)}` : `Complément de validation, lot ${lotId.slice(0, 6)}`,
        reference: lotId,
        auteurId,
      });
    }
    for (const ref of aRegulariser) tx.update(ref, { regularisation: { lotId, le: FieldValue.serverTimestamp() } });
    const compte = db.doc(`comptesPlateforme/${pays}`);
    tx.set(
      compte,
      { pays, reserveValidation: FieldValue.increment(-reserveRetenue), payeSiege: FieldValue.increment(montantFacture), majLe: FieldValue.serverTimestamp() },
      { merge: true },
    );
    tx.create(compte.collection("ecritures").doc(), {
      type: "facture_siege", lotId, fboId, montantFacture, reserveRetenue, factureAttribuable, ecart, le: FieldValue.serverTimestamp(), par: auteurId,
    });
    // Apprentissage : prix réels par produit, sinon coefficient du pays (taxes, timbres).
    for (const [produitId, prix] of Object.entries(prixFactures)) {
      tx.set(db.doc(`prixValidation/${cleePrix(pays, remiseLot, produitId)}`), { pays, remise: remiseLot, produitId, prix, majLe: FieldValue.serverTimestamp(), lotId });
    }
    if (Object.keys(prixFactures).length === 0 && theoriqueTotal > 0) {
      const observe = montantFacture / theoriqueTotal;
      if (observe >= 0.8 && observe <= 1.5) {
        const ancien = Number(tarifs.coefValidation ?? 1);
        tx.set(db.doc(`tarifsPays/${pays}`), { coefValidation: Math.round((ancien * 0.5 + observe * 0.5) * 10000) / 10000 }, { merge: true });
      }
    }
    return { ecart, reserveRetenue, factureAttribuable };
  });

  await journaliser("regularisation_lot", auteurId, lotId, { montantFacture, ...bilan });
  if (bilan.ecart !== 0) {
    const montant = `${Math.abs(bilan.ecart).toLocaleString("fr-FR")} FCFA`;
    await notifier(
      fboId,
      bilan.ecart > 0 ? "Surplus de validation rendu" : "Complément de validation",
      bilan.ecart > 0
        ? `La facture du siège était inférieure à la réserve : ${montant} ont été ajoutés à votre wallet.`
        : `La facture du siège dépassait la réserve de ${montant}. Ce montant est prélevé sur votre wallet (ou sur vos prochains gains).`,
      "",
      `regul_${lotId}`,
    ).catch((e) => logger.warn("Notification de régularisation impossible", e));
  }
  return bilan;
}
