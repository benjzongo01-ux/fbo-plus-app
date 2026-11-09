import { HttpsError } from "firebase-functions/v2/https";
import { db } from "./commun.js";

/**
 * Mode maintenance : un seul document public, parametres/maintenance, lu par les trois applications
 * (même avant connexion) pour afficher l'écran d'attente. Pendant la maintenance, le serveur refuse
 * toute action, sauf celles du super administrateur qui effectue la maintenance.
 */
export const REF_MAINTENANCE = db.doc("parametres/maintenance");

export const MESSAGE_PAR_DEFAUT =
  "Nous améliorons FBO+ pour mieux vous servir. Merci de votre patience et de votre confiance : " +
  "l'application sera de nouveau disponible très bientôt.";

export const DUREE_MAX_MINUTES = 7 * 24 * 60;

// Lecture mise en cache quelques secondes : un appel ne coûte pas une lecture de plus à chaque fois.
const CACHE_MS = process.env.FUNCTIONS_EMULATOR === "true" ? 0 : 10_000;
let cache = { actif: false, lu: 0 };

/** Après un changement, la prochaine vérification relit le document. */
export function oublierCacheMaintenance() {
  cache = { actif: false, lu: 0 };
}

export async function maintenanceActive(): Promise<boolean> {
  if (Date.now() - cache.lu < CACHE_MS) return cache.actif;
  const actif = (await REF_MAINTENANCE.get()).data()?.actif === true;
  cache = { actif, lu: Date.now() };
  return actif;
}

/** Refuse l'action pendant la maintenance, sauf pour le super administrateur. */
export async function exigerHorsMaintenance(role: string) {
  if (role === "super_admin") return;
  if (await maintenanceActive()) {
    throw new HttpsError(
      "failed-precondition",
      "FBO+ est en maintenance. Merci de patienter, l'application revient très bientôt.",
      { maintenance: true },
    );
  }
}
