// Crée le tout premier super_admin : la fonction definirRole exige déjà un administrateur.
// Usage : GOOGLE_APPLICATION_CREDENTIALS=<clé> node scripts/creer-super-admin.mjs <projet> +226XXXXXXXX
// Avec l'émulateur : FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080
import { applicationDefault, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";

const [projectId, telephone] = process.argv.slice(2);
if (!projectId || !/^\+[1-9]\d{7,14}$/.test(telephone ?? "")) {
  console.error("Usage : node scripts/creer-super-admin.mjs <projet> <téléphone E.164>");
  process.exit(1);
}

const emulateur = Boolean(process.env.FIREBASE_AUTH_EMULATOR_HOST);
initializeApp(emulateur ? { projectId } : { projectId, credential: applicationDefault() });

const auth = getAuth();
const compte = await auth
  .getUserByPhoneNumber(telephone)
  .catch(() => auth.createUser({ phoneNumber: telephone }));

await auth.setCustomUserClaims(compte.uid, { role: "super_admin" });
await getFirestore().doc(`users/${compte.uid}`).set(
  {
    telephone,
    role: "super_admin",
    statut: "actif",
    createdAt: FieldValue.serverTimestamp(),
  },
  { merge: true },
);
await getFirestore().collection("auditLogs").add({
  action: "super_admin_initialise",
  auteurId: "script",
  cibleId: compte.uid,
  date: FieldValue.serverTimestamp(),
});

console.log(`super_admin prêt : ${compte.uid}`);
