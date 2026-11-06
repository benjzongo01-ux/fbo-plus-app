import { randomUUID } from "node:crypto";
import { deleteApp, initializeApp as initClient } from "firebase/app";
import { connectAuthEmulator, getAuth as getClientAuth, signInWithCustomToken } from "firebase/auth";
import { connectFunctionsEmulator, getFunctions, httpsCallable } from "firebase/functions";
import { getApps, initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { afterAll, beforeAll, describe, expect, it } from "vitest";

const PROJET = "demo-fbo-plus";
const REGION = "europe-west1";
const applis: ReturnType<typeof initClient>[] = [];
let numero = 10000000;

beforeAll(() => {
  if (getApps().length === 0) initializeApp({ projectId: PROJET });
});

afterAll(async () => {
  await Promise.all(applis.map((a) => deleteApp(a)));
});

const telephone = () => `+2267${++numero}`;

async function compte(role?: string, avecTelephone = true) {
  const uid = randomUUID();
  await getAuth().createUser({ uid, ...(avecTelephone ? { phoneNumber: telephone() } : {}) });
  if (role) await getAuth().setCustomUserClaims(uid, { role });

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

const codeErreur = (p: Promise<unknown>) =>
  p.then(
    () => "aucune erreur",
    (e: { code: string }) => e.code,
  );

describe("initialiserProfil", () => {
  it("crée un compte fbo avec un numéro vérifié, sans doublon", async () => {
    const u = await compte();
    expect(await u.appeler("initialiserProfil")).toEqual({ role: "fbo", aPin: false });
    expect(await u.appeler("initialiserProfil")).toEqual({ role: "fbo", aPin: false });

    const profil = await getFirestore().doc(`users/${u.uid}`).get();
    expect(profil.data()?.role).toBe("fbo");
    expect((await getAuth().getUser(u.uid)).customClaims?.role).toBe("fbo");
  });

  it("refuse un compte sans téléphone vérifié", async () => {
    const u = await compte(undefined, false);
    expect(await codeErreur(u.appeler("initialiserProfil"))).toBe("functions/failed-precondition");
  });

  it("conserve le rôle d'un compte interne", async () => {
    const u = await compte("livreur");
    expect((await u.appeler("initialiserProfil")).role).toBe("livreur");
  });
});

describe("definirRole", () => {
  it("un FBO ne peut pas se donner ni donner un rôle", async () => {
    const fbo = await compte("fbo");
    const cible = await compte("fbo");
    expect(await codeErreur(fbo.appeler("definirRole", { uid: fbo.uid, role: "super_admin" }))).toBe(
      "functions/permission-denied",
    );
    expect(await codeErreur(fbo.appeler("definirRole", { uid: cible.uid, role: "admin" }))).toBe(
      "functions/permission-denied",
    );
  });

  it("un admin attribue un rôle ordinaire, avec journalisation", async () => {
    const admin = await compte("admin");
    const cible = await compte("fbo");
    await admin.appeler("definirRole", { uid: cible.uid, role: "operateur" });

    expect((await getAuth().getUser(cible.uid)).customClaims?.role).toBe("operateur");
    const journal = await getFirestore()
      .collection("auditLogs")
      .where("cibleId", "==", cible.uid)
      .where("action", "==", "role_modifie")
      .get();
    expect(journal.size).toBe(1);
    expect(journal.docs[0].data().auteurId).toBe(admin.uid);
  });

  it("un admin ne promeut pas en admin et ne rétrograde pas un admin", async () => {
    const admin = await compte("admin");
    const cible = await compte("fbo");
    const autreAdmin = await compte("admin");
    expect(await codeErreur(admin.appeler("definirRole", { uid: cible.uid, role: "admin" }))).toBe(
      "functions/permission-denied",
    );
    expect(await codeErreur(admin.appeler("definirRole", { uid: autreAdmin.uid, role: "fbo" }))).toBe(
      "functions/permission-denied",
    );
  });

  it("un super_admin nomme un admin mais ne change pas son propre rôle", async () => {
    const sa = await compte("super_admin");
    const cible = await compte("fbo");
    await sa.appeler("definirRole", { uid: cible.uid, role: "admin" });
    expect((await getAuth().getUser(cible.uid)).customClaims?.role).toBe("admin");
    expect(await codeErreur(sa.appeler("definirRole", { uid: sa.uid, role: "fbo" }))).toBe(
      "functions/permission-denied",
    );
  });

  it("refuse un rôle inconnu", async () => {
    const sa = await compte("super_admin");
    const cible = await compte("fbo");
    expect(await codeErreur(sa.appeler("definirRole", { uid: cible.uid, role: "pirate" }))).toBe(
      "functions/invalid-argument",
    );
  });
});

describe("creerCompteInterne", () => {
  it("un admin crée un livreur, un FBO non", async () => {
    const admin = await compte("admin");
    const fbo = await compte("fbo");
    const data = { telephone: telephone(), nom: "Issa", role: "livreur" };

    expect(await codeErreur(fbo.appeler("creerCompteInterne", data))).toBe(
      "functions/permission-denied",
    );
    const { uid } = await admin.appeler("creerCompteInterne", data);
    expect((await getAuth().getUser(uid)).customClaims?.role).toBe("livreur");
    expect(await codeErreur(admin.appeler("creerCompteInterne", data))).toBe(
      "functions/already-exists",
    );
  });

  it("un admin ne crée pas d'admin", async () => {
    const admin = await compte("admin");
    const data = { telephone: telephone(), nom: "X", role: "admin" };
    expect(await codeErreur(admin.appeler("creerCompteInterne", data))).toBe(
      "functions/permission-denied",
    );
  });
});

describe("PIN", () => {
  it("vérifie le bon PIN et ne stocke jamais le PIN en clair", async () => {
    const u = await compte("fbo");
    await u.appeler("definirPin", { pin: "4827" });

    expect((await u.appeler("verifierPin", { pin: "4827" })).valide).toBe(true);
    const secret = (await getFirestore().doc(`userSecrets/${u.uid}`).get()).data();
    expect(JSON.stringify(secret)).not.toContain("4827");
    expect((await u.appeler("initialiserProfil")).aPin).toBe(true);
  });

  it("refuse un PIN mal formé", async () => {
    const u = await compte("fbo");
    expect(await codeErreur(u.appeler("definirPin", { pin: "12" }))).toBe(
      "functions/invalid-argument",
    );
  });

  it("bloque le compte après 5 échecs, même avec le bon PIN", async () => {
    const u = await compte("fbo");
    await u.appeler("definirPin", { pin: "4827" });

    for (let restants = 4; restants >= 0; restants--) {
      expect(await u.appeler("verifierPin", { pin: "0000" })).toEqual({
        valide: false,
        essaisRestants: restants,
      });
    }
    expect(await codeErreur(u.appeler("verifierPin", { pin: "4827" }))).toBe(
      "functions/resource-exhausted",
    );
  });

  // L'émulateur ne vérifie pas la signature : ce jeton simule une connexion datant d'une heure.
  async function definirPinSessionAncienne(uid: string, data: unknown) {
    const b64 = (o: object) => Buffer.from(JSON.stringify(o)).toString("base64url");
    const maintenant = Math.floor(Date.now() / 1000);
    const jeton = [
      b64({ alg: "none", typ: "JWT" }),
      b64({
        iss: `https://securetoken.google.com/${PROJET}`,
        aud: PROJET,
        sub: uid,
        user_id: uid,
        iat: maintenant,
        exp: maintenant + 3600,
        auth_time: maintenant - 3600,
      }),
      "",
    ].join(".");

    const reponse = await fetch(`http://127.0.0.1:5001/${PROJET}/${REGION}/definirPin`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${jeton}` },
      body: JSON.stringify({ data }),
    });
    return ((await reponse.json()) as { error?: { status?: string } }).error?.status;
  }

  it("une session ancienne ne remplace pas le PIN sans le PIN actuel", async () => {
    const u = await compte("fbo");
    await u.appeler("definirPin", { pin: "4827" });

    expect(await definirPinSessionAncienne(u.uid, { pin: "1234" })).toBe("FAILED_PRECONDITION");
    expect(await definirPinSessionAncienne(u.uid, { pin: "1234", pinActuel: "0000" })).toBe(
      "PERMISSION_DENIED",
    );
    expect((await u.appeler("verifierPin", { pin: "4827" })).valide).toBe(true);
  });

  it("une session ancienne remplace le PIN avec le bon PIN actuel", async () => {
    const u = await compte("fbo");
    await u.appeler("definirPin", { pin: "4827" });

    expect(await definirPinSessionAncienne(u.uid, { pin: "1234", pinActuel: "4827" })).toBeUndefined();
    expect((await u.appeler("verifierPin", { pin: "1234" })).valide).toBe(true);
  });
});
