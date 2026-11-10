import { FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { db, exigerConnexion, journaliser, OPTIONS_APPEL } from "./commun.js";
import { onAppel } from "./limites.js";

/**
 * Quiz des formations : les bonnes réponses restent sur le serveur (formationsCorrections),
 * jamais dans l'application. À partir de 80 % de bonnes réponses, un certificat est délivré.
 */
export const SEUIL_CERTIFICAT = 0.8;

export const soumettreQuiz = onAppel("soumettreQuiz", OPTIONS_APPEL, async (req) => {
  const uid = exigerConnexion(req);
  const d = (req.data ?? {}) as { formationId?: unknown; reponses?: unknown };
  const formationId = typeof d.formationId === "string" ? d.formationId : "";
  const [formation, correction] = await Promise.all([
    db.doc(`formations/${formationId}`).get(),
    db.doc(`formationsCorrections/${formationId}`).get(),
  ]);
  const attendues = correction.data()?.reponses as number[] | undefined;
  if (!formation.exists || !attendues) throw new HttpsError("not-found", "Formation introuvable.");
  const reponses = d.reponses;
  if (!Array.isArray(reponses) || reponses.length !== attendues.length || !reponses.every(Number.isInteger)) {
    throw new HttpsError("invalid-argument", "Répondez à toutes les questions.");
  }

  const justes = attendues.filter((r, i) => reponses[i] === r).length;
  const score = justes / attendues.length;
  const reussi = score >= SEUIL_CERTIFICAT;
  if (reussi) {
    const profil = (await db.doc(`users/${uid}`).get()).data();
    await db.doc(`certificats/${uid}_${formationId}`).set({
      userId: uid,
      formationId,
      titre: formation.data()!.titre,
      nom: [profil?.prenom, profil?.nom].filter(Boolean).join(" "),
      score: Math.round(score * 100),
      obtenuLe: FieldValue.serverTimestamp(),
    });
    await journaliser("certificat_obtenu", uid, formationId, { score: Math.round(score * 100) });
  }
  // Seules les questions manquées sont indiquées, pas les bonnes réponses.
  return {
    justes,
    total: attendues.length,
    reussi,
    manquees: attendues.map((r, i) => (reponses[i] === r ? -1 : i)).filter((i) => i >= 0),
  };
});
