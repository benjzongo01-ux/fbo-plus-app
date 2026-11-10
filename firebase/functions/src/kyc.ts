import { FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { exigerRole } from "./commandes.js";
import { db, exigerSuperAdmin, journaliser, OPTIONS_APPEL } from "./commun.js";
import { onAppel } from "./limites.js";
import { notifier } from "./notifications.js";

/**
 * Vérification d'identité des livreurs (KYC) : pièce d'identité recto et verso, photo du visage,
 * adresse et personne à contacter. Le dossier complet (données sensibles) est dans kyc/{uid},
 * lisible par le seul super administrateur ; le profil ne porte que le statut.
 * Aucune mission n'est confiée à un livreur dont le dossier n'est pas validé.
 */
const TELEPHONE = /^\+?[0-9 ]{8,16}$/;
const texte = (v: unknown, max = 200) => (typeof v === "string" ? v.trim().slice(0, max) : "");

export const soumettreKyc = onAppel("soumettreKyc", OPTIONS_APPEL, async (req) => {
  const uid = exigerRole(req, ["livreur"]);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const numeroPiece = texte(d.numeroPiece, 40);
  const adresse = texte(d.adresse);
  const contactNom = texte(d.contactNom, 120);
  const contactTelephone = texte(d.contactTelephone, 20);
  if (!numeroPiece) throw new HttpsError("invalid-argument", "Indiquez le numéro de votre pièce d'identité.");
  if (!adresse) throw new HttpsError("invalid-argument", "Indiquez votre adresse.");
  if (!contactNom || !TELEPHONE.test(contactTelephone)) {
    throw new HttpsError("invalid-argument", "Indiquez le nom et le téléphone d'une personne à contacter.");
  }
  // Les photos doivent être dans le dossier privé du livreur.
  const photos: Record<string, string> = {};
  for (const cle of ["pieceRecto", "pieceVerso", "selfie"]) {
    const chemin = texte(d[cle], 300);
    if (!chemin.startsWith(`kyc/${uid}/`)) throw new HttpsError("invalid-argument", "Ajoutez les trois photos demandées.");
    photos[cle] = chemin;
  }

  const profil = (await db.doc(`users/${uid}`).get()).data();
  if (profil?.kyc?.statut === "valide") throw new HttpsError("failed-precondition", "Votre dossier est déjà validé.");

  await db.doc(`kyc/${uid}`).set({
    uid,
    nom: profil?.nom ?? "",
    pays: profil?.pays ?? null,
    numeroPiece,
    adresse,
    contactNom,
    contactTelephone,
    ...photos,
    soumisLe: FieldValue.serverTimestamp(),
  });
  await db.doc(`users/${uid}`).set({ kyc: { statut: "en_attente", motif: "" } }, { merge: true });
  await journaliser("kyc_soumis", uid, uid);
  return { statut: "en_attente" };
});

export const deciderKyc = onAppel("deciderKyc", OPTIONS_APPEL, async (req) => {
  const appelant = exigerSuperAdmin(req);
  const d = (req.data ?? {}) as { uid?: unknown; valider?: unknown; motif?: unknown };
  const uid = texte(d.uid, 128);
  if (!(await db.doc(`kyc/${uid}`).get()).exists) throw new HttpsError("not-found", "Aucun dossier pour ce livreur.");
  const valider = d.valider === true;
  const motif = texte(d.motif, 300);
  if (!valider && !motif) throw new HttpsError("invalid-argument", "Indiquez le motif du refus.");

  await db.doc(`users/${uid}`).set(
    { kyc: { statut: valider ? "valide" : "refuse", motif: valider ? "" : motif, decideLe: FieldValue.serverTimestamp() } },
    { merge: true },
  );
  await journaliser(valider ? "kyc_valide" : "kyc_refuse", appelant.uid, uid, { motif });
  await notifier(
    uid,
    "Votre dossier",
    valider ? "Votre identité est vérifiée : vous pouvez recevoir des missions." : `Dossier à compléter : ${motif}`,
    "",
    `kyc_${Date.now()}`,
  );
  return { statut: valider ? "valide" : "refuse" };
});
