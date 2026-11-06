import { initializeApp } from "firebase/app";
import { connectAuthEmulator, getAuth } from "firebase/auth";
import { connectFirestoreEmulator, getFirestore } from "firebase/firestore";
import { connectFunctionsEmulator, getFunctions, httpsCallable } from "firebase/functions";
import { connectStorageEmulator, getStorage } from "firebase/storage";

/** Seul un build de lancement explicite (VITE_FIREBASE_REEL=true) peut viser le vrai projet. */
export const surEmulateurs = import.meta.env.VITE_FIREBASE_REEL !== "true";

const app = initializeApp(
  surEmulateurs
    ? { projectId: "demo-fbo-plus", apiKey: "demo", authDomain: "demo-fbo-plus.firebaseapp.com", storageBucket: "demo-fbo-plus.appspot.com" }
    : {
        projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
        apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
        authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
        storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
        appId: import.meta.env.VITE_FIREBASE_APP_ID,
      },
);

export const auth = getAuth(app);
export const db = getFirestore(app);
export const stockage = getStorage(app);
const functions = getFunctions(app, "europe-west1");

if (surEmulateurs) {
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  connectFirestoreEmulator(db, "127.0.0.1", 8080);
  connectFunctionsEmulator(functions, "127.0.0.1", 5001);
  connectStorageEmulator(stockage, "127.0.0.1", 9199);
  auth.settings.appVerificationDisabledForTesting = true;
}

export function appeler<T = unknown>(nom: string, donnees: unknown = {}): Promise<T> {
  return httpsCallable<unknown, T>(functions, nom)(donnees).then((r) => r.data);
}
