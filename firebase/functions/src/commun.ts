import { getApps, initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { CallableRequest, HttpsError } from "firebase-functions/v2/https";

if (getApps().length === 0) initializeApp();

export const db = getFirestore();

export const ROLES = [
  "fbo",
  "livreur",
  "operateur",
  "gestionnaire_stock",
  "admin",
  "super_admin",
] as const;

export type Role = (typeof ROLES)[number];

export const ROLES_ADMIN: readonly string[] = ["admin", "super_admin"];

export const OPTIONS_APPEL = {
  region: "europe-west1",
  // L'émulateur ne reçoit pas de jeton App Check.
  enforceAppCheck: process.env.FUNCTIONS_EMULATOR !== "true",
};

export function exigerConnexion(req: CallableRequest<unknown>): string {
  if (!req.auth) throw new HttpsError("unauthenticated", "Connexion requise.");
  return req.auth.uid;
}

export function exigerAdmin(req: CallableRequest<unknown>): { uid: string; role: string; pays: string } {
  const uid = exigerConnexion(req);
  const role = String(req.auth?.token.role ?? "");
  if (!ROLES_ADMIN.includes(role)) {
    throw new HttpsError("permission-denied", "Action réservée aux administrateurs.");
  }
  return { uid, role, pays: paysDe(req) };
}

/** Pays où FBO+ opère, chacun géré par sa propre administration. */
export const PAYS = ["Burkina Faso", "Côte d'Ivoire", "Sénégal"];

/** Données d'avant la gestion par pays : elles relèvent du Burkina Faso. */
export const PAYS_DEFAUT = "Burkina Faso";

export function estPays(valeur: unknown): valeur is string {
  return typeof valeur === "string" && PAYS.includes(valeur);
}

export function paysDe(req: CallableRequest<unknown>): string {
  return String(req.auth?.token.pays ?? "");
}

/** Le personnel n'agit que dans son pays ; le super administrateur partout. */
export function exigerMemePays(req: CallableRequest<unknown>, pays: unknown): void {
  if (req.auth?.token.role === "super_admin") return;
  const sien = paysDe(req);
  if (!sien || sien !== (typeof pays === "string" && pays ? pays : PAYS_DEFAUT)) {
    throw new HttpsError("permission-denied", "Cela relève de l'administration d'un autre pays.");
  }
}

export function estRole(valeur: unknown): valeur is Role {
  return typeof valeur === "string" && (ROLES as readonly string[]).includes(valeur);
}

/** Seul un super_admin peut attribuer ou modifier un rôle d'administration. */
export function exigerDroitSurRole(roleAppelant: string, role: string | undefined): void {
  if (role && ROLES_ADMIN.includes(role) && roleAppelant !== "super_admin") {
    throw new HttpsError("permission-denied", "Action réservée au super administrateur.");
  }
}

export async function journaliser(
  action: string,
  auteurId: string,
  cibleId: string,
  details: Record<string, unknown> = {},
): Promise<void> {
  await db.collection("auditLogs").add({
    action,
    auteurId,
    cibleId,
    details,
    date: FieldValue.serverTimestamp(),
  });
}
