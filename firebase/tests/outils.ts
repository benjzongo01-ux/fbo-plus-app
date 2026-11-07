import { randomUUID } from "node:crypto";
import { deleteApp, initializeApp as initClient } from "firebase/app";
import { connectAuthEmulator, getAuth as getClientAuth, signInWithCustomToken } from "firebase/auth";
import { connectFunctionsEmulator, getFunctions, httpsCallable } from "firebase/functions";
import { getApps, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

export const PROJET = "demo-fbo-plus";
const REGION = "europe-west1";
const applis: ReturnType<typeof initClient>[] = [];
let numero = 40000000;

export function preparerAdmin() {
  if (getApps().length === 0) initializeApp({ projectId: PROJET });
}

export const fermerApplis = () => Promise.all(applis.splice(0).map((a) => deleteApp(a)));

export const adminDb = () => getFirestore();

export const telephone = () => `+2265${++numero}`;

export async function compte(role?: string, profil: Record<string, unknown> = {}) {
  const uid = randomUUID();
  await getAuth().createUser({ uid, phoneNumber: telephone() });
  // Le personnel et les livreurs travaillent dans un pays, le Burkina Faso par défaut.
  const pays = (profil.pays as string | undefined) ?? "Burkina Faso";
  if (role) await getAuth().setCustomUserClaims(uid, role === "fbo" ? { role } : { role, pays });
  await getFirestore().doc(`users/${uid}`).set({ role: role ?? null, statut: "actif", pays, ...profil });

  const app = initClient({ apiKey: "demo", projectId: PROJET }, uid);
  applis.push(app);
  const auth = getClientAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  await signInWithCustomToken(auth, await getAuth().createCustomToken(uid));
  const functions = getFunctions(app, REGION);
  connectFunctionsEmulator(functions, "127.0.0.1", 5001);

  return {
    uid,
    appeler: async (nom: string, data: unknown = {}) =>
      (await httpsCallable(functions, nom)(data)).data as Record<string, any>,
  };
}

export const codeErreur = (p: Promise<unknown>) =>
  p.then(
    () => "aucune erreur",
    (e: { code: string }) => e.code,
  );

export const cle = () => randomUUID();
