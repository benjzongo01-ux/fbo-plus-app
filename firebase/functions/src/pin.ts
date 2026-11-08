import { randomBytes, scrypt, timingSafeEqual } from "node:crypto";
import { promisify } from "node:util";
import { Timestamp } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { HttpsError } from "firebase-functions/v2/https";
import { onAppel } from "./limites.js";
import { db, exigerConnexion, journaliser, OPTIONS_APPEL } from "./commun.js";

const PIN_PEPPER = defineSecret("PIN_PEPPER");
const OPTIONS_PIN = { ...OPTIONS_APPEL, secrets: [PIN_PEPPER] };

const ESSAIS_MAX = 5;
const BLOCAGE_MS = 15 * 60 * 1000;
const FRAICHEUR_OTP_S = 5 * 60;

const deriver = promisify(scrypt) as (
  mot: string,
  sel: Buffer,
  longueur: number,
) => Promise<Buffer>;

function lirePin(valeur: unknown): string {
  if (typeof valeur !== "string" || !/^\d{4}$/.test(valeur)) {
    throw new HttpsError("invalid-argument", "Le PIN doit contenir 4 chiffres.");
  }
  return valeur;
}

const empreinte = (pin: string, sel: Buffer) => deriver(pin + PIN_PEPPER.value(), sel, 32);

/** Compare le PIN et tient le compteur d'échecs, partagé par toutes les vérifications. */
async function controlerPin(uid: string, pin: string) {
  const ref = db.doc(`userSecrets/${uid}`);

  // La transaction empêche des essais parallèles de contourner le compteur d'échecs.
  const resultat = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("failed-precondition", "Aucun PIN défini.");

    const s = snap.data()!;
    const bloqueJusqua = s.bloqueJusqua as Timestamp | null;
    if (bloqueJusqua && bloqueJusqua.toMillis() > Date.now()) {
      throw new HttpsError("resource-exhausted", "Trop d'essais. Réessayez plus tard.");
    }

    const attendu = Buffer.from(s.hash as string, "base64");
    const calcule = await empreinte(pin, Buffer.from(s.sel as string, "base64"));

    if (timingSafeEqual(attendu, calcule)) {
      tx.update(ref, { echecs: 0, bloqueJusqua: null });
      return { valide: true, essaisRestants: ESSAIS_MAX };
    }

    const echecs = Number(s.echecs ?? 0) + 1;
    if (echecs >= ESSAIS_MAX) {
      tx.update(ref, {
        echecs: 0,
        bloqueJusqua: Timestamp.fromMillis(Date.now() + BLOCAGE_MS),
      });
      return { valide: false, essaisRestants: 0 };
    }
    tx.update(ref, { echecs });
    return { valide: false, essaisRestants: ESSAIS_MAX - echecs };
  });

  if (!resultat.valide && resultat.essaisRestants === 0) {
    await journaliser("pin_bloque", uid, uid);
  }
  return resultat;
}

/** Créer ou remplacer le PIN exige un code SMS de moins de 5 minutes, ou le PIN actuel. */
export const definirPin = onAppel("definirPin", OPTIONS_PIN, async (req) => {
  const uid = exigerConnexion(req);
  const { pin: valeur, pinActuel } = (req.data ?? {}) as { pin?: unknown; pinActuel?: unknown };
  const pin = lirePin(valeur);

  const authTime = Number(req.auth?.token.auth_time ?? 0);
  if (Date.now() / 1000 - authTime > FRAICHEUR_OTP_S) {
    if (pinActuel === undefined) {
      throw new HttpsError("failed-precondition", "Vérification SMS récente requise.");
    }
    if (!(await controlerPin(uid, lirePin(pinActuel))).valide) {
      throw new HttpsError("permission-denied", "PIN actuel incorrect.");
    }
  }

  const sel = randomBytes(16);
  const hash = await empreinte(pin, sel);
  await db.doc(`userSecrets/${uid}`).set({
    hash: hash.toString("base64"),
    sel: sel.toString("base64"),
    echecs: 0,
    bloqueJusqua: null,
  });
  await journaliser("pin_defini", uid, uid);

  return { ok: true };
});

export const verifierPin = onAppel("verifierPin", OPTIONS_PIN, async (req) => {
  const uid = exigerConnexion(req);
  return controlerPin(uid, lirePin((req.data as { pin?: unknown } | null)?.pin));
});
