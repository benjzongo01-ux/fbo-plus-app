import { getAuth } from "firebase-admin/auth";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { onAppel } from "./limites.js";
import {
  db,
  estPays,
  estRole,
  exigerSuperAdmin,
  exigerConnexion,
  exigerDroitSurRole,
  exigerMemePays,
  journaliser,
  OPTIONS_APPEL,
} from "./commun.js";
import { REMISES } from "./commandes.js";
import { DUREE_MAX_MINUTES, MESSAGE_PAR_DEFAUT, oublierCacheMaintenance, REF_MAINTENANCE } from "./maintenance.js";

const cleEmail = (email: string) => email.trim().toLowerCase();

/**
 * Appelée après chaque connexion (Google, email ou SMS). Un nouveau compte devient distributeur (fbo),
 * sauf si le super administrateur a invité son adresse : il reçoit alors le rôle et le pays prévus.
 * Une invitation ne s'applique qu'à une adresse prouvée (Google, ou email confirmé par le lien reçu).
 */
export const initialiserProfil = onAppel("initialiserProfil", OPTIONS_APPEL, async (req) => {
  const uid = exigerConnexion(req);
  const compte = await getAuth().getUser(uid);
  let role = compte.customClaims?.role as string | undefined;
  let pays = (compte.customClaims?.pays as string | undefined) ?? null;
  let nomInvite: string | null = null;

  if (!role) {
    if (!compte.phoneNumber && !compte.email) {
      throw new HttpsError("failed-precondition", "Connexion par Google, email ou téléphone requise.");
    }
    const refInvitation = compte.email ? db.doc(`invitations/${cleEmail(compte.email)}`) : null;
    const invitation = refInvitation ? (await refInvitation.get()).data() : undefined;
    if (invitation && !compte.emailVerified) {
      // Sinon, n'importe qui pourrait créer un compte avec l'adresse d'un membre invité.
      throw new HttpsError(
        "failed-precondition",
        "Confirmez votre adresse email avec le lien reçu, puis reconnectez-vous.",
      );
    }
    if (invitation && refInvitation) {
      role = String(invitation.role);
      pays = String(invitation.pays);
      nomInvite = String(invitation.nom ?? "");
      await refInvitation.delete();
      await journaliser("invitation_acceptee", uid, uid, { role, pays });
    } else if ((req.data as { personnel?: unknown } | null)?.personnel === true) {
      // Connexion à l'admin ou à Delivery : sans invitation, aucun compte n'est créé.
      throw new HttpsError("permission-denied", "Cette adresse n'est pas autorisée. Demandez une invitation au super administrateur.");
    } else {
      role = "fbo";
    }
    await getAuth().setCustomUserClaims(uid, { ...compte.customClaims, role, ...(pays ? { pays } : {}) });
  }

  const ref = db.doc(`users/${uid}`);
  const cree = await db.runTransaction(async (tx) => {
    if ((await tx.get(ref)).exists) return false;
    tx.create(ref, {
      nom: nomInvite || compte.displayName || "",
      telephone: compte.phoneNumber ?? null,
      email: compte.email ?? null,
      role,
      ...(pays ? { pays } : {}),
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
  const appelant = exigerSuperAdmin(req);
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
  const appelant = exigerSuperAdmin(req);
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
  const appelant = exigerSuperAdmin(req);
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
  const appelant = exigerSuperAdmin(req);
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

/**
 * Supprime un compte : il ne peut plus se connecter et ses données privées sont effacées
 * (PIN, fiches clients, vitrine, appareils). Les commandes restent, pour l'historique et la comptabilité.
 */
export const supprimerCompte = onAppel("supprimerCompte", OPTIONS_APPEL, async (req) => {
  const appelant = exigerSuperAdmin(req);
  const { uid } = (req.data ?? {}) as { uid?: unknown };
  if (typeof uid !== "string" || uid.length === 0) throw new HttpsError("invalid-argument", "Compte manquant.");
  if (uid === appelant.uid) {
    throw new HttpsError("permission-denied", "Vous ne pouvez pas supprimer votre propre compte.");
  }

  const ref = db.doc(`users/${uid}`);
  const profil = (await ref.get()).data();
  if (!profil) throw new HttpsError("not-found", "Utilisateur introuvable.");

  await getAuth()
    .deleteUser(uid)
    .catch((e: { code?: string }) => {
      if (e.code !== "auth/user-not-found") throw e;
    });

  const lot = db.bulkWriter();
  for (const nom of ["customers", "fcmTokens"]) {
    const champ = nom === "customers" ? "ownerId" : "userId";
    for (const d of (await db.collection(nom).where(champ, "==", uid).get()).docs) lot.delete(d.ref);
  }
  lot.delete(db.doc(`userSecrets/${uid}`));
  lot.delete(db.doc(`boutiques/${uid}`));
  lot.delete(db.doc(`limitesDebit/${uid}`));
  // Le profil garde nom et rôle pour que l'historique des commandes reste lisible.
  lot.set(
    ref,
    {
      statut: "supprime",
      supprimeLe: FieldValue.serverTimestamp(),
      telephone: FieldValue.delete(),
      email: FieldValue.delete(),
      whatsapp: FieldValue.delete(),
    },
    { merge: true },
  );
  await lot.close();

  await journaliser("compte_supprime", appelant.uid, uid, { role: profil.role ?? null });
  return { uid };
});

/**
 * Personnel et livreurs : le super administrateur invite une adresse email avec un rôle et un pays.
 * La personne se connecte ensuite elle-même (Google, ou email confirmé) et reçoit ce rôle.
 * Si l'adresse a déjà un compte, le rôle lui est attribué tout de suite.
 */
export const inviterPersonnel = onAppel("inviterPersonnel", OPTIONS_APPEL, async (req) => {
  const appelant = exigerSuperAdmin(req);
  const { email, nom, role, pays, telephone } = (req.data ?? {}) as Record<string, unknown>;
  if (!estPays(pays)) throw new HttpsError("invalid-argument", "Choisissez le pays du compte.");
  if (typeof email !== "string" || !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email.trim())) {
    throw new HttpsError("invalid-argument", "Adresse email invalide.");
  }
  if (typeof nom !== "string" || nom.trim().length === 0) throw new HttpsError("invalid-argument", "Nom manquant.");
  if (!estRole(role) || role === "fbo") throw new HttpsError("invalid-argument", "Rôle interne invalide.");
  if (telephone !== undefined && telephone !== "" && (typeof telephone !== "string" || !/^\+[1-9]\d{7,14}$/.test(telephone))) {
    throw new HttpsError("invalid-argument", "Numéro invalide (format international attendu).");
  }
  exigerDroitSurRole(appelant.role, role);

  const adresse = cleEmail(email);
  const existant = await getAuth().getUserByEmail(adresse).catch(() => null);
  if (existant) {
    if (existant.uid === appelant.uid) throw new HttpsError("permission-denied", "Vous ne pouvez pas changer votre propre rôle.");
    await getAuth().setCustomUserClaims(existant.uid, { ...existant.customClaims, role, pays });
    await getAuth().revokeRefreshTokens(existant.uid);
    await db.doc(`users/${existant.uid}`).set(
      { nom: nom.trim(), email: adresse, role, pays, statut: "actif", ...(telephone ? { telephone } : {}) },
      { merge: true },
    );
    await journaliser("role_modifie", appelant.uid, existant.uid, { role, pays });
    return { uid: existant.uid, role, invitation: false };
  }

  await db.doc(`invitations/${adresse}`).set({
    email: adresse,
    nom: nom.trim(),
    role,
    pays,
    telephone: typeof telephone === "string" && telephone ? telephone : null,
    invitePar: appelant.uid,
    createdAt: FieldValue.serverTimestamp(),
  });
  await journaliser("personnel_invite", appelant.uid, adresse, { role, pays });
  return { role, invitation: true };
});

/** Change le pays d'un compte du personnel : réservé au super administrateur. */
export const definirPays = onAppel("definirPays", OPTIONS_APPEL, async (req) => {
  const appelant = exigerSuperAdmin(req);
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

/** Active ou arrête le mode maintenance (message et durée prévue) : réservé au super administrateur. */
export const definirMaintenance = onAppel("definirMaintenance", OPTIONS_APPEL, async (req) => {
  const appelant = exigerSuperAdmin(req);
  const d = (req.data ?? {}) as { actif?: unknown; message?: unknown; dureeMinutes?: unknown };
  if (typeof d.actif !== "boolean") throw new HttpsError("invalid-argument", "Indiquez si la maintenance est active.");

  if (!d.actif) {
    await REF_MAINTENANCE.set({
      actif: false,
      message: "",
      finPrevue: null,
      termineeLe: FieldValue.serverTimestamp(),
      auteurId: appelant.uid,
    });
    oublierCacheMaintenance();
    await journaliser("maintenance_terminee", appelant.uid, "maintenance");
    return { actif: false };
  }

  const duree = d.dureeMinutes ?? 0;
  if (!Number.isInteger(duree) || (duree as number) < 0 || (duree as number) > DUREE_MAX_MINUTES) {
    throw new HttpsError("invalid-argument", "Durée invalide (en minutes, 7 jours au plus).");
  }
  const message = typeof d.message === "string" && d.message.trim() ? d.message.trim().slice(0, 500) : MESSAGE_PAR_DEFAUT;

  await REF_MAINTENANCE.set({
    actif: true,
    message,
    debut: FieldValue.serverTimestamp(),
    finPrevue: duree ? Timestamp.fromMillis(Date.now() + (duree as number) * 60_000) : null,
    auteurId: appelant.uid,
  });
  oublierCacheMaintenance();
  await journaliser("maintenance_activee", appelant.uid, "maintenance", { dureeMinutes: duree });
  return { actif: true };
});
