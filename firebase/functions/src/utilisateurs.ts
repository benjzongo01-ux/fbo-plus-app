import { getAuth } from "firebase-admin/auth";
import { FieldValue } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import {
  db,
  estRole,
  exigerAdmin,
  exigerConnexion,
  exigerDroitSurRole,
  journaliser,
  OPTIONS_APPEL,
} from "./commun.js";

/**
 * Appelée après la vérification du code SMS. Un nouveau numéro devient un compte fbo :
 * les autres rôles sont attribués uniquement par l'administration.
 */
export const initialiserProfil = onCall(OPTIONS_APPEL, async (req) => {
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

export const definirRole = onCall(OPTIONS_APPEL, async (req) => {
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
export const definirStatut = onCall(OPTIONS_APPEL, async (req) => {
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

  await auth.updateUser(uid, { disabled: statut === "suspendu" });
  if (statut === "suspendu") await auth.revokeRefreshTokens(uid);
  await db.doc(`users/${uid}`).set({ statut }, { merge: true });
  await journaliser("statut_modifie", appelant.uid, uid, { statut });

  return { uid, statut };
});

/** Comptes livreur et personnel : créés uniquement par l'administration. */
export const creerCompteInterne = onCall(OPTIONS_APPEL, async (req) => {
  const appelant = exigerAdmin(req);
  const { telephone, nom, role, centre } = (req.data ?? {}) as Record<string, unknown>;

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

  await getAuth().setCustomUserClaims(compte.uid, { role });
  await db.doc(`users/${compte.uid}`).create({
    nom: nom.trim(),
    telephone,
    email: null,
    role,
    centre: typeof centre === "string" ? centre : null,
    statut: "actif",
    createdAt: FieldValue.serverTimestamp(),
  });
  await journaliser("compte_interne_cree", appelant.uid, compte.uid, { role });

  return { uid: compte.uid, role };
});
