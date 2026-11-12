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

/** Montant écrit sur un reçu : « 22 880 », « 22.880 », « 22,880 FCFA »… (sans centimes en FCFA). */
function montant(s: string): number | null {
  const m = s.match(/(\d{1,3}(?:[ .  ,]\d{3})+|\d{3,9})(?:[.,]\d{1,2})?\s*(?:F|FCFA|XOF|CFA)?\b/gi);
  if (!m) return null;
  const valeurs = m.map((x) => Number(chiffres(x.replace(/[.,]\d{1,2}\s*(?:F|FCFA|XOF|CFA)?$/i, "")))).filter((n) => n > 0);
  return valeurs.length ? Math.max(...valeurs) : null;
}

/** Lecture d'un reçu de validation : texte reconnu sur le téléphone, ligne par ligne. */
export function lireTexteRecu(texte: string) {
  const lignes = texte.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  // ID Forever : 12 chiffres, parfois groupés (226-000-078-011, 226 000 078 011).
  const ids = [...texte.matchAll(/(?<!\d)(\d{3}[\s-]?\d{3}[\s-]?\d{3}[\s-]?\d{3})(?!\d)/g)].map((m) => chiffres(m[1]));
  let total: number | null = null;
  let tva: number | null = null;
  let numero: string | null = null;
  for (const l of lignes) {
    const haut = l.toUpperCase();
    if (total === null && /\b(NET\s*A\s*PAYER|TOTAL\s*TTC|MONTANT\s*TTC|TOTAL)\b/.test(haut) && !/SOUS[\s-]?TOTAL/.test(haut)) total = montant(l);
    if (tva === null && /\bT\.?V\.?A\b/.test(haut)) tva = montant(l.replace(/\d{1,2}([.,]\d+)?\s*%/g, ""));
    if (numero === null) {
      const n = haut.match(/\b(?:RE[CÇ]U|FACTURE|TICKET|N[O°º]\.?)\s*(?:N[O°º]\.?)?\s*[:#]?\s*([A-Z0-9-]{4,20})/);
      if (n && /\d/.test(n[1])) numero = n[1];
    }
  }
  return { idsForever: [...new Set(ids)], total, tva, numero };
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
    }))
    .sort((a, b) => (a.ecart ?? 0) - (b.ecart ?? 0));
  return { ...lu, lots };
});
