import { FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { db, exigerConnexion, exigerMemePays, journaliser, OPTIONS_APPEL, PAYS_DEFAUT } from "./commun.js";
import { onAppel } from "./limites.js";

/**
 * Correspondant au siège (application FBO+ Siège).
 *
 * 1. Il voit en direct les lots à valider de son pays, en choisit plusieurs, les dépose au siège
 *    (statut « au_siege ») : l'application sait alors quels reçus restent attendus.
 * 2. Il scanne chaque reçu : le texte est lu sur le téléphone (aucune photo gardée), puis envoyé
 *    ici. Le serveur en tire l'ID Forever, le total, la TVA et le numéro du reçu, et retrouve le lot.
 * 3. Il confirme : validerLotSiege (facture réelle, régularisation du wallet, prix appris).
 */

const ROLES_SIEGE = ["correspondant", "admin", "super_admin"];

function exigerSiege(req: Parameters<typeof exigerConnexion>[0]) {
  const uid = exigerConnexion(req);
  if (!ROLES_SIEGE.includes(String(req.auth?.token.role))) {
    throw new HttpsError("permission-denied", "Réservé au correspondant du siège et à l'administration.");
  }
  return uid;
}

const chiffres = (s: string) => s.replace(/\D/g, "");

/** Nombre écrit sur un reçu : « 103 209 », « 103.209 », « 15 405 » (FCFA, sans centimes). */
function nombre(s: string): number | null {
  const m = s.match(/\d{1,3}(?:[ .\u00a0\u202f]\d{3})+|\d+/g);
  if (!m) return null;
  const n = Number(chiffres(m[m.length - 1]));
  return Number.isFinite(n) && n > 0 ? n : null;
}

const normaliserRef = (ref: string) => ref.replace(/^0+/, "") || "0";

export interface LigneRecu {
  ref: string;
  designation: string;
  quantite: number;
  prixUnitaireHT: number;
  montantHT: number;
}

/**
 * Lecture d'une facture de vente Forever (Burkina, Côte d'Ivoire…), texte reconnu sur le téléphone.
 * Repères : ID (Num FBO), « Facture de vente » (numéro), lignes (article, libellé, quantité, prix
 * unitaire HT, montant HT), Sous total / Total HT, TVA, AIRSI, timbre, Grand Total, Points caisse.
 * Un libellé et sa valeur peuvent arriver sur deux lignes (colonnes du reçu) : on regarde la suivante.
 */
export function lireTexteRecu(texte: string) {
  const lignes = texte.split(/\r?\n/).map((l) => l.replace(/\s+/g, " ").trim()).filter(Boolean);
  // ID Forever : 12 chiffres, parfois groupés (226-000-078-011, 226 000 078 011).
  const ids = [...texte.matchAll(/(?<!\d)(\d{3}[\s-]?\d{3}[\s-]?\d{3}[\s-]?\d{3})(?!\d)/g)].map((m) => chiffres(m[1]));

  /** Valeur d'un libellé : sur la même ligne (après le libellé), sinon sur la ligne suivante. */
  function valeur(motif: RegExp): number | null {
    for (let i = 0; i < lignes.length; i++) {
      const m = lignes[i].match(motif);
      if (!m) continue;
      const reste = lignes[i].slice((m.index ?? 0) + m[0].length).replace(/\d{1,2}([.,]\d+)?\s*%/g, "");
      const ici = nombre(reste);
      if (ici !== null) return ici;
      const suivante = lignes[i + 1];
      if (suivante && /^[\d .:\u00a0\u202f]+(F|FCFA)?$/i.test(suivante)) return nombre(suivante);
    }
    return null;
  }

  const total = valeur(/GRAND\s*TOTAL\s*:?/i) ?? valeur(/NET\s*[AÀ]\s*PAYER\s*:?/i);
  const sousTotalHT = valeur(/SOUS[\s-]?TOTAL\s*:?/i) ?? valeur(/TOTAL\s*HT\s*(BRUT)?\s*:?/i);
  const tva = valeur(/(TOTAL\s*)?T\.?V\.?A\.?\s*(\d{1,2}\s*%)?\s*:?/i);
  const airsi = valeur(/AIRSI\s*:?/i);
  const timbre = valeur(/TAXE\s*D[EU]\s*TIMBRE\s*:?/i);

  let numero: string | null = null;
  for (const l of lignes) {
    const n = l.toUpperCase().match(/FACTURE\s*DE\s*VENTE\s*(?:N[°ºO]\.?)?\s*:?\s*([A-Z0-9-]{6,30})/);
    if (n && /\d/.test(n[1])) { numero = n[1]; break; }
  }
  let pointsCaisse: number | null = null;
  for (const l of lignes) {
    const n = l.match(/POINTS?\s*CAISSES?\s*:?\s*(\d*[.,]\d+|\d+)/i);
    if (n) { pointsCaisse = Number(n[1].replace(",", ".")); break; }
  }

  // Lignes de produits : « 621 FOREVER IVISION 2 23 040 46 080 », « 188 B12 PLUS 1,00 E 10 200 10 200 ».
  const produits: LigneRecu[] = [];
  const montantRe = String.raw`(\d{1,3}(?:[ .]\d{3})*)`;
  const ligneRe = new RegExp(String.raw`^0*(\d{2,4})\s+(.+?)\s+(\d{1,3})(?:[.,]0{1,2})?\s+(?:[A-Z]\s+)?${montantRe}\s+${montantRe}$`);
  for (const l of lignes) {
    const m = l.toUpperCase().match(ligneRe);
    if (!m) continue;
    const quantite = Number(m[3]);
    const prix = Number(chiffres(m[4]));
    const montantHT = Number(chiffres(m[5]));
    // Garde-fou : quantité × prix unitaire ≈ montant (sinon la ligne est mal lue).
    if (quantite > 0 && Math.abs(quantite * prix - montantHT) <= quantite) {
      produits.push({ ref: m[1], designation: m[2].trim(), quantite, prixUnitaireHT: prix, montantHT });
    }
  }
  return { idsForever: [...new Set(ids)], numero, total, sousTotalHT, tva, airsi, timbre, pointsCaisse, lignes: produits };
}

/**
 * Prix réellement payés par produit, taxes comprises : prix unitaire HT du reçu, augmenté au prorata
 * de (Grand total / Sous-total HT). Associés aux produits du lot par la référence d'article.
 */
function prixFactures(lignesLot: Array<{ ref?: string; produitId?: string }>, lu: ReturnType<typeof lireTexteRecu>) {
  const facteur = lu.total && lu.sousTotalHT ? lu.total / lu.sousTotalHT : null;
  const resultat: Record<string, number> = {};
  if (!facteur || facteur < 1 || facteur > 1.6) return resultat;
  for (const ligne of lu.lignes) {
    const produit = lignesLot.find((x) => x.ref && normaliserRef(String(x.ref)) === normaliserRef(ligne.ref));
    if (produit?.produitId) resultat[produit.produitId] = Math.round(ligne.prixUnitaireHT * facteur);
  }
  return resultat;
}

/** Le correspondant dépose des lots au siège : leurs reçus deviennent attendus. */
export const deposerLotsAuSiege = onAppel("deposerLotsAuSiege", OPTIONS_APPEL, async (req) => {
  const uid = exigerSiege(req);
  const ids = ((req.data as { ids?: unknown } | null)?.ids ?? []) as unknown[];
  if (!Array.isArray(ids) || ids.length === 0 || ids.length > 100) throw new HttpsError("invalid-argument", "Choisissez de 1 à 100 lots.");
  const refs = ids.map((i) => db.doc(`lotsValidation/${String(i).slice(0, 128)}`));
  const n = await db.runTransaction(async (tx) => {
    const lots = await tx.getAll(...refs);
    let deposes = 0;
    lots.forEach((s) => {
      const lot = s.data();
      if (!lot) throw new HttpsError("not-found", "Lot introuvable.");
      exigerMemePays(req, lot.pays);
      if (lot.statut !== "a_valider") return;
      tx.update(s.ref, { statut: "au_siege", deposeLe: FieldValue.serverTimestamp(), deposePar: uid });
      deposes++;
    });
    return deposes;
  });
  await journaliser("lots_deposes_siege", uid, String(ids.length), { deposes: n });
  return { deposes: n };
});

/**
 * Analyse le texte d'un reçu scanné : renvoie ce qui a été lu et les lots du pays qui correspondent
 * (même ID Forever, encore à valider ou déposés au siège), du plus probable au moins probable.
 */
export const lireRecu = onAppel("lireRecu", OPTIONS_APPEL, async (req) => {
  exigerSiege(req);
  const texte = typeof (req.data as { texte?: unknown } | null)?.texte === "string" ? String((req.data as { texte: string }).texte).slice(0, 20_000) : "";
  if (texte.trim().length < 10) throw new HttpsError("invalid-argument", "Reçu illisible : recommencez le scan.");
  const lu = lireTexteRecu(texte);
  const role = String(req.auth?.token.role);
  const pays = role === "super_admin" ? null : String(req.auth?.token.pays || PAYS_DEFAUT);

  let requete = db.collection("lotsValidation").where("statut", "in", ["a_valider", "au_siege"]);
  if (pays) requete = requete.where("pays", "==", pays);
  const lots = (await requete.get()).docs
    .map((d) => ({ id: d.id, ...d.data() }) as Record<string, any>)
    .filter((l) => lu.idsForever.includes(chiffres(String(l.idForever ?? ""))))
    .map((l) => ({
      id: l.id,
      idForever: l.idForever,
      pays: l.pays,
      nom: l.nom,
      telephone: l.telephone ?? null,
      montant: l.montant,
      ccTotal: l.ccTotal,
      statut: l.statut,
      // Le total lu le plus proche du montant attendu passe en premier.
      ecart: lu.total !== null ? Math.abs(Number(l.montant) - lu.total) : null,
      ecartCc: lu.pointsCaisse !== null ? Math.round(Math.abs(Number(l.ccTotal ?? 0) - lu.pointsCaisse) * 1000) / 1000 : null,
      prixFactures: prixFactures(l.lignes ?? [], lu),
    }))
    .sort((a, b) => (a.ecart ?? 0) - (b.ecart ?? 0));
  return { ...lu, lots };
});
