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

export function exigerAdmin(req: CallableRequest<unknown>): { uid: string; role: string } {
  const uid = exigerConnexion(req);
  const role = String(req.auth?.token.role ?? "");
  if (!ROLES_ADMIN.includes(role)) {
    throw new HttpsError("permission-denied", "Action réservée aux administrateurs.");
  }
  return { uid, role };
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
