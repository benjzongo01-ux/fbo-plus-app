// Crée une vraie commande sur les émulateurs, via la Cloud Function, au nom d'un distributeur fictif.
import { initializeApp as initClient } from "firebase/app";
import { connectAuthEmulator, getAuth as authClient, signInWithCustomToken } from "firebase/auth";
import { connectFunctionsEmulator, getFunctions, httpsCallable } from "firebase/functions";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { randomUUID } from "node:crypto";

process.env.FIREBASE_AUTH_EMULATOR_HOST ??= "127.0.0.1:9099";
process.env.FIRESTORE_EMULATOR_HOST ??= "127.0.0.1:8080";
const local = (hote) => /^(127\.0\.0\.1|localhost):/.test(hote);
if (!local(process.env.FIREBASE_AUTH_EMULATOR_HOST) || !local(process.env.FIRESTORE_EMULATOR_HOST)) {
  throw new Error("Ce script ne s'exécute que sur les émulateurs locaux.");
}

const projectId = "demo-fbo-plus";
initializeApp({ projectId });
const TELEPHONE = "+22670000020";

const compte = await getAuth()
  .getUserByPhoneNumber(TELEPHONE)
  .catch(() => getAuth().createUser({ phoneNumber: TELEPHONE }));
await getAuth().setCustomUserClaims(compte.uid, { role: "fbo" });
await getFirestore().doc(`users/${compte.uid}`).set(
  { prenom: "Awa", nom: "Test", telephone: TELEPHONE, role: "fbo", statut: "actif", grade: "Animateur (A)" },
  { merge: true },
);

const app = initClient({ apiKey: "demo", projectId });
const auth = authClient(app);
connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
await signInWithCustomToken(auth, await getAuth().createCustomToken(compte.uid));
const functions = getFunctions(app, "europe-west1");
connectFunctionsEmulator(functions, "127.0.0.1", 5001);

const { data } = await httpsCallable(functions, "creerCommande")({
  cleIdempotence: randomUUID(),
  titre: "Commande de démonstration",
  clientNom: "Fatou Client",
  clientTelephone: "+22670000030",
  adresse: "Secteur 15, Ouagadougou",
  reduction: 2000,
  lignes: [
    { produitId: "15", quantite: 2 },
    { produitId: "27", quantite: 1 },
  ],
});
console.log(`Commande ${data.id} : ${data.total} FCFA`);
process.exit(0);
