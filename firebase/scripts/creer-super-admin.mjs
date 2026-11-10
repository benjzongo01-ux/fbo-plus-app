// Prépare le tout premier super_admin (les invitations suivantes se font depuis l'admin).
// Usage : GOOGLE_APPLICATION_CREDENTIALS=<clé> node scripts/creer-super-admin.mjs <projet> <adresse Google> [pays]
// Avec l'émulateur : FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080
// Si l'adresse a déjà un compte, le rôle est donné tout de suite ; sinon une invitation l'attend :
// la personne se connecte à l'admin avec « Continuer avec Google » et devient super administrateur.
import { applicationDefault, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";

const [projectId, adresse, pays = "Burkina Faso"] = process.argv.slice(2);
if (!projectId || !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(adresse ?? "")) {
  console.error("Usage : node scripts/creer-super-admin.mjs <projet> <adresse email> [pays]");
  process.exit(1);
}
const email = adresse.trim().toLowerCase();

const emulateur = Boolean(process.env.FIREBASE_AUTH_EMULATOR_HOST);
initializeApp(emulateur ? { projectId } : { projectId, credential: applicationDefault() });
const auth = getAuth();
const db = getFirestore();

const compte = await auth.getUserByEmail(email).catch(() => null);
if (compte) {
  await auth.setCustomUserClaims(compte.uid, { role: "super_admin", pays });
  await db.doc(`users/${compte.uid}`).set({ email, role: "super_admin", pays, statut: "actif" }, { merge: true });
  console.log(`super_admin prêt : ${compte.uid} (${email})`);
} else {
  await db.doc(`invitations/${email}`).set({
    email,
    nom: "Super administrateur",
    role: "super_admin",
    pays,
    invitePar: "script",
    createdAt: FieldValue.serverTimestamp(),
  });
  console.log(`Invitation créée : connectez-vous à l'admin avec « Continuer avec Google » (${email}).`);
}
await db.collection("auditLogs").add({
  action: "super_admin_initialise",
  auteurId: "script",
  cibleId: email,
  date: FieldValue.serverTimestamp(),
});
