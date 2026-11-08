import { getAuth } from "firebase-admin/auth";
import { FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { onAppel } from "./limites.js";
import {
  db,
  estPays,
  estRole,
  exigerAdmin,
  exigerConnexion,
  exigerDroitSurRole,
  exigerMemePays,
  journaliser,
  OPTIONS_APPEL,
} from "./commun.js";
import { REMISES } from "./commandes.js";

/**
 * Appelée après la vérification du code SMS. Un nouveau numéro devient un compte fbo :
 * les autres rôles sont attribués uniquement par l'administration.
 */
export const initialiserProfil = onAppel("initialiserProfil", OPTIONS_APPEL, async (req) => {
  const uid = exigerConnexion(req);
  const compte = await getAuth().getUser(uid);
  let role = compte.customClaims?.role as string | undefined;

  if (!role) {
    if (!compte.phoneNumber) {
      throw new HttpsError("failed-precondition", "Numéro de téléphone vérifié requis.");
    }
    role = "fbo";
    await getAuth().setCustomUserClaims(uid, { ...compte.customClaims, role });
  }

  const ref = db.doc(`users/${uid}`);
  const cree = await db.runTransaction(async (tx) => {
    if ((await tx.get(ref)).exists) return false;
    tx.create(ref, {
      nom: compte.displayName ?? "",
      telephone: compte.phoneNumber ?? null,
      email: compte.email ?? null,
      role,
      centre: null,
      statut: "actif",
      createdAt: FieldValue.serverTimestamp(),
    });
    return true;
  });
  if (cree) await journaliser("profil_cree", uid, uid, { role });

  const secret = await db.doc(`userSecrets/${uid}`).get();
  return { role, aPin: secret.exists };
});

export const definirRole = onAppel("definirRole", OPTIONS_APPEL, async (req) => {
  const appelant = exigerAdmin(req);
  const { uid, role } = (req.data ?? {}) as { uid?: unknown; role?: unknown };

  if (typeof uid !== "string" || uid.length === 0) {
    throw new HttpsError("invalid-argument", "Utilisateur manquant.");
  }
  if (!estRole(role)) throw new HttpsError("invalid-argument", "Rôle inconnu.");
  if (uid === appelant.uid) {
    throw new HttpsError("permission-denied", "Vous ne pouvez pas changer votre propre rôle.");
  }

  const auth = getAuth();
  const compte = await auth.getUser(uid).catch(() => {
    throw new HttpsError("not-found", "Utilisateur introuvable.");
  });
  const ancienRole = compte.customClaims?.role as string | undefined;
  exigerMemePays(req, (await db.doc(`users/${uid}`).get()).data()?.pays);

  exigerDroitSurRole(appelant.role, ancienRole);
  exigerDroitSurRole(appelant.role, role);

  await auth.setCustomUserClaims(uid, { ...compte.customClaims, role });
  await db.doc(`users/${uid}`).set({ role }, { merge: true });
  // Force un nouveau jeton : un rôle retiré ne reste pas actif jusqu'à l'expiration de l'ancien.
  await auth.revokeRefreshTokens(uid);
  await journaliser("role_modifie", appelant.uid, uid, { ancienRole: ancienRole ?? null, role });

  return { uid, role };
});

/** Un compte suspendu ne peut plus se connecter et perd immédiatement ses sessions. */
export const definirStatut = onAppel("definirStatut", OPTIONS_APPEL, async (req) => {
  const appelant = exigerAdmin(req);
  const { uid, statut } = (req.data ?? {}) as { uid?: unknown; statut?: unknown };

  if (typeof uid !== "string" || (statut !== "actif" && statut !== "suspendu")) {
    throw new HttpsError("invalid-argument", "Données invalides.");
  }
  if (uid === appelant.uid) {
    throw new HttpsError("permission-denied", "Vous ne pouvez pas suspendre votre propre compte.");
  }

  const auth = getAuth();
  const compte = await auth.getUser(uid).catch(() => {
    throw new HttpsError("not-found", "Utilisateur introuvable.");
  });
  exigerDroitSurRole(appelant.role, compte.customClaims?.role as string | undefined);
  exigerMemePays(req, (await db.doc(`users/${uid}`).get()).data()?.pays);

  await auth.updateUser(uid, { disabled: statut === "suspendu" });
  if (statut === "suspendu") await auth.revokeRefreshTokens(uid);
  await db.doc(`users/${uid}`).set({ statut }, { merge: true });
  await journaliser("statut_modifie", appelant.uid, uid, { statut });

  return { uid, statut };
});

/** Lit le distributeur visé et vérifie qu'il relève de l'administration de l'appelant. */
async function distributeurDuPays(req: Parameters<typeof exigerMemePays>[0], uid: unknown) {
  if (typeof uid !== "string" || uid.length === 0) throw new HttpsError("invalid-argument", "Compte manquant.");
  const ref = db.doc(`users/${uid}`);
  const compte = (await ref.get()).data();
  if (!compte || compte.role !== "fbo") throw new HttpsError("not-found", "Distributeur introuvable.");
  exigerMemePays(req, compte.pays);
  return { ref, compte };
}

/** Prévient le distributeur dans son fil de notifications, sans lien vers une commande. */
async function prevenir(userId: string, cle: string, corps: string) {
  await db.doc(`notifications/${cle}_${userId}_${Date.now()}`).set({
    userId,
    titre: "Votre compte",
    corps,
    commandeId: "",
    lu: false,
    createdAt: FieldValue.serverTimestamp(),
  });
}

/**
 * Le grade fixe la remise et le plafond des réductions : seul l'administration l'attribue.
 * Le distributeur ne fait qu'une demande (gradeDemande), acceptée ou refusée ici.
 */
export const definirGrade = onAppel("definirGrade", OPTIONS_APPEL, async (req) => {
  const appelant = exigerAdmin(req);
  const d = (req.data ?? {}) as { uid?: unknown; grade?: unknown; refuser?: unknown };
  const { ref, compte } = await distributeurDuPays(req, d.uid);

  if (d.refuser === true) {
    if (!compte.gradeDemande) throw new HttpsError("failed-precondition", "Aucune demande de grade en attente.");
    await ref.update({ gradeDemande: FieldValue.delete() });
    await journaliser("grade_refuse", appelant.uid, ref.id, { demande: compte.gradeDemande });
    await prevenir(ref.id, "grade", `Votre demande de grade « ${compte.gradeDemande} » n'a pas été acceptée.`);
    return { grade: compte.grade ?? null };
  }

  if (typeof d.grade !== "string" || !(d.grade in REMISES)) {
    throw new HttpsError("invalid-argument", "Grade inconnu.");
  }
  await ref.update({ grade: d.grade, gradeDemande: FieldValue.delete() });
  await journaliser("grade_defini", appelant.uid, ref.id, { avant: compte.grade ?? null, apres: d.grade });
  await prevenir(ref.id, "grade", `Votre grade « ${d.grade} » est validé : remise de ${REMISES[d.grade]} %.`);
  return { grade: d.grade };
});

/** L'ID Forever est verrouillé pour le distributeur ; l'administration corrige une erreur de saisie. */
export const corrigerIdForever = onAppel("corrigerIdForever", OPTIONS_APPEL, async (req) => {
  const appelant = exigerAdmin(req);
  const d = (req.data ?? {}) as { uid?: unknown; idDistributeur?: unknown };
  const { ref, compte } = await distributeurDuPays(req, d.uid);

  const id = typeof d.idDistributeur === "string" ? d.idDistributeur.trim() : "";
  if (!/^[0-9][0-9 -]{5,29}$/.test(id) || id.replace(/\D/g, "").length < 6) {
    throw new HttpsError("invalid-argument", "ID Forever invalide (au moins 6 chiffres).");
  }
  await ref.update({ idDistributeur: id });
  await journaliser("id_forever_corrige", appelant.uid, ref.id, { avant: compte.idDistributeur ?? "", apres: id });
  await prevenir(ref.id, "idforever", `Votre ID Forever a été corrigé par l'administration : ${id}.`);
  return { idDistributeur: id };
});

/** Comptes livreur et personnel : créés uniquement par l'administration. */
export const creerCompteInterne = onAppel("creerCompteInterne", OPTIONS_APPEL, async (req) => {
  const appelant = exigerAdmin(req);
  const { telephone, nom, role, centre, pays: paysDemande } = (req.data ?? {}) as Record<string, unknown>;
  // Un administrateur ne recrute que dans son pays ; le super administrateur choisit.
  const pays = appelant.role === "super_admin" ? paysDemande : appelant.pays;
  if (!estPays(pays)) throw new HttpsError("invalid-argument", "Choisissez le pays du compte.");

  if (typeof telephone !== "string" || !/^\+[1-9]\d{7,14}$/.test(telephone)) {
    throw new HttpsError("invalid-argument", "Numéro invalide (format international attendu).");
  }
  if (typeof nom !== "string" || nom.trim().length === 0) {
    throw new HttpsError("invalid-argument", "Nom manquant.");
  }
  if (!estRole(role) || role === "fbo") {
    throw new HttpsError("invalid-argument", "Rôle interne invalide.");
  }
  exigerDroitSurRole(appelant.role, role);

  const compte = await getAuth()
    .createUser({ phoneNumber: telephone, displayName: nom.trim() })
    .catch((e: { code?: string }) => {
      if (e.code === "auth/phone-number-already-exists") {
        throw new HttpsError("already-exists", "Ce numéro possède déjà un compte.");
      }
      throw e;
    });

  await getAuth().setCustomUserClaims(compte.uid, { role, pays });
  await db.doc(`users/${compte.uid}`).create({
    nom: nom.trim(),
    telephone,
    email: null,
    role,
    pays,
    centre: typeof centre === "string" ? centre : null,
    statut: "actif",
    createdAt: FieldValue.serverTimestamp(),
  });
  await journaliser("compte_interne_cree", appelant.uid, compte.uid, { role, pays });

  return { uid: compte.uid, role };
});

/** Change le pays d'un compte du personnel : réservé au super administrateur. */
export const definirPays = onAppel("definirPays", OPTIONS_APPEL, async (req) => {
  const appelant = exigerAdmin(req);
  if (appelant.role !== "super_admin") {
    throw new HttpsError("permission-denied", "Action réservée au super administrateur.");
  }
  const { uid, pays } = (req.data ?? {}) as { uid?: unknown; pays?: unknown };
  if (typeof uid !== "string" || !estPays(pays)) {
    throw new HttpsError("invalid-argument", "Données invalides.");
  }

  const auth = getAuth();
  const compte = await auth.getUser(uid).catch(() => {
    throw new HttpsError("not-found", "Utilisateur introuvable.");
  });
  await auth.setCustomUserClaims(uid, { ...compte.customClaims, pays });
  await db.doc(`users/${uid}`).set({ pays }, { merge: true });
  await auth.revokeRefreshTokens(uid);
  await journaliser("pays_modifie", appelant.uid, uid, { pays });

  return { uid, pays };
});
