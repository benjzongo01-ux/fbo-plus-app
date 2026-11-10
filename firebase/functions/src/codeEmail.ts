import { createHash, randomBytes, randomInt, timingSafeEqual } from "node:crypto";
import { getAuth } from "firebase-admin/auth";
import { FieldValue } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { db, journaliser, OPTIONS_APPEL } from "./commun.js";
import { limiterDebit } from "./limites.js";

/**
 * Connexion par code reçu par email, sans mot de passe (comme Binance) :
 * 1. demanderCodeEmail : un code à 6 chiffres part vers l'adresse, valable 10 minutes ;
 * 2. verifierCodeEmail : le bon code donne un jeton de connexion ; l'adresse est alors prouvée.
 * Le compte est créé au premier code validé, puis initialiserProfil fait le reste (rôle, PIN).
 *
 * Sécurité : seule l'empreinte salée du code est gardée (collection codesEmail, illisible par les
 * clients), 5 essais au plus par code, un code ne sert qu'une fois, débit limité par adresse et
 * par appareil. Les réponses sont les mêmes que l'adresse ait un compte ou non.
 *
 * Envoi : un document dans « mail », lu par l'extension Firebase « Trigger Email » au lancement.
 * Sur les émulateurs, le code s'affiche aussi dans le journal des fonctions.
 */
const DUREE_MS = 10 * 60 * 1000;
const ESSAIS_MAX = 5;
const DELAI_RENVOI_S = 60;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

const empreinte = (texte: string) => createHash("sha256").update(texte).digest("hex");

function lireEmail(data: unknown) {
  const brut = (data as { email?: unknown } | null)?.email;
  const email = typeof brut === "string" ? brut.trim().toLowerCase() : "";
  if (email.length > 254 || !EMAIL.test(email)) {
    throw new HttpsError("invalid-argument", "Adresse email invalide.");
  }
  return email;
}

/** Limite par adresse et par appareil (adresse IP), même sans être connecté. */
async function limiter(action: string, email: string, ip: string | undefined) {
  await limiterDebit(`email_${empreinte(email).slice(0, 32)}`, action);
  if (ip) await limiterDebit(`ip_${empreinte(ip).slice(0, 32)}`, `${action}Appareil`);
}

function messageEmail(code: string) {
  return {
    subject: `${code} est votre code de connexion FBO+`,
    text:
      `Votre code de connexion FBO+ : ${code}\n\n` +
      "Il est valable 10 minutes. Ne le communiquez à personne, même pas à l'équipe FBO+.\n" +
      "Si vous n'avez pas demandé ce code, ignorez ce message.",
    html:
      `<div style="font-family:Arial,sans-serif;max-width:420px;margin:auto;padding:24px;background:#0b0d12;color:#fff;border-radius:16px">` +
      `<h2 style="color:#d4af37;margin:0 0 12px">FBO+</h2>` +
      `<p>Votre code de connexion :</p>` +
      `<p style="font-size:34px;letter-spacing:8px;font-weight:bold;color:#d4af37;margin:12px 0">${code}</p>` +
      `<p style="color:#bbb">Valable 10 minutes. Ne le communiquez à personne, même pas à l'équipe FBO+.</p>` +
      `<p style="color:#888;font-size:12px">Si vous n'avez pas demandé ce code, ignorez ce message.</p></div>`,
  };
}

export const demanderCodeEmail = onCall(OPTIONS_APPEL, async (req) => {
  const email = lireEmail(req.data);
  await limiter("demanderCodeEmail", email, req.rawRequest?.ip);

  const ref = db.doc(`codesEmail/${empreinte(email)}`);
  const code = String(randomInt(0, 1_000_000)).padStart(6, "0");
  const sel = randomBytes(16).toString("hex");
  const maintenant = Date.now();

  const attente = await db.runTransaction(async (tx) => {
    const avant = (await tx.get(ref)).data();
    const ecoule = avant ? (maintenant - Number(avant.envoyeA)) / 1000 : Infinity;
    if (ecoule < DELAI_RENVOI_S) return Math.ceil(DELAI_RENVOI_S - ecoule);
    tx.set(ref, { empreinte: empreinte(sel + code), sel, essais: 0, envoyeA: maintenant, expire: maintenant + DUREE_MS });
    tx.create(db.collection("mail").doc(), { to: email, message: messageEmail(code), creeLe: FieldValue.serverTimestamp() });
    return 0;
  });
  if (attente > 0) {
    throw new HttpsError("resource-exhausted", `Un code vient d'être envoyé. Réessayez dans ${attente} secondes.`, { attente });
  }
  if (process.env.FUNCTIONS_EMULATOR === "true") logger.info(`Émulateurs : code de connexion de ${email} : ${code}`);
  return { envoye: true, validiteMinutes: DUREE_MS / 60000, renvoiSecondes: DELAI_RENVOI_S };
});

export const verifierCodeEmail = onCall(OPTIONS_APPEL, async (req) => {
  const email = lireEmail(req.data);
  const brut = (req.data as { code?: unknown } | null)?.code;
  const code = typeof brut === "string" ? brut.replace(/\D/g, "") : "";
  if (code.length !== 6) throw new HttpsError("invalid-argument", "Le code a 6 chiffres.");
  await limiter("verifierCodeEmail", email, req.rawRequest?.ip);

  const ref = db.doc(`codesEmail/${empreinte(email)}`);
  const resultat = await db.runTransaction(async (tx) => {
    const d = (await tx.get(ref)).data();
    if (!d || Date.now() > Number(d.expire)) {
      if (d) tx.delete(ref);
      return "expire" as const;
    }
    const attendu = Buffer.from(String(d.empreinte), "hex");
    const recu = Buffer.from(empreinte(String(d.sel) + code), "hex");
    if (attendu.length === recu.length && timingSafeEqual(attendu, recu)) {
      tx.delete(ref); // un code ne sert qu'une fois
      return "ok" as const;
    }
    const essais = Number(d.essais ?? 0) + 1;
    if (essais >= ESSAIS_MAX) tx.delete(ref);
    else tx.update(ref, { essais });
    return ESSAIS_MAX - essais;
  });

  if (resultat === "expire") {
    throw new HttpsError("deadline-exceeded", "Ce code a expiré ou n'existe plus. Demandez un nouveau code.");
  }
  if (resultat !== "ok") {
    throw new HttpsError(
      "permission-denied",
      resultat > 0
        ? `Code incorrect. Essais restants : ${resultat}.`
        : "Code incorrect, trop d'essais. Demandez un nouveau code.",
      { essaisRestants: resultat },
    );
  }

  // L'adresse est prouvée : compte existant (Google ou email) ou nouveau compte.
  const auth = getAuth();
  let uid: string;
  try {
    const existant = await auth.getUserByEmail(email);
    uid = existant.uid;
    if (existant.disabled) throw new HttpsError("permission-denied", "Ce compte est suspendu.");
    if (!existant.emailVerified) await auth.updateUser(uid, { emailVerified: true });
  } catch (e) {
    if ((e as { code?: string }).code !== "auth/user-not-found") throw e;
    uid = (await auth.createUser({ email, emailVerified: true })).uid;
    await journaliser("compte_cree_par_code_email", uid, uid, {});
  }
  return { jeton: await auth.createCustomToken(uid) };
});
