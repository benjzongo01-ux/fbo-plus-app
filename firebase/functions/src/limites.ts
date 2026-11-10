import { logger } from "firebase-functions";
import { CallableRequest, CallableOptions, HttpsError, onCall } from "firebase-functions/v2/https";
import { db } from "./commun.js";
import { exigerHorsMaintenance } from "./maintenance.js";

/**
 * Limitation du débit par utilisateur : l'équivalent, pour Firebase, d'un reverse proxy qui filtre
 * les rafales. Google absorbe déjà les attaques massives ; ici, on empêche un seul compte d'inonder
 * le serveur (volontairement ou par un bug), sans gêner les autres.
 *
 * Fenêtre fixe : au plus `max` appels par `fenetreS` secondes pour une action donnée, et au plus
 * TOTAL appels par minute toutes actions confondues. Un appel refusé ne touche à aucune donnée.
 */
interface Limite {
  max: number;
  fenetreS: number;
}

const PAR_DEFAUT: Limite = { max: 30, fenetreS: 60 };
const TOTAL: Limite = { max: 120, fenetreS: 60 };

export const LIMITES: Record<string, Limite> = {
  // Connexion et sécurité : peu d'appels légitimes, beaucoup d'intérêt pour un attaquant.
  initialiserProfil: { max: 10, fenetreS: 60 },
  definirPin: { max: 5, fenetreS: 600 },
  verifierPin: { max: 10, fenetreS: 600 },
  // Commandes du distributeur.
  creerCommande: { max: 10, fenetreS: 60 },
  modifierCommande: { max: 20, fenetreS: 60 },
  annulerCommande: { max: 20, fenetreS: 60 },
  importerVentes: { max: 3, fenetreS: 600 },
  envoyerMessage: { max: 30, fenetreS: 60 },
  // Empêche de deviner les réponses d'un quiz par essais en rafale.
  soumettreQuiz: { max: 10, fenetreS: 600 },
  // Boutique publique : un appareil ne peut pas inonder un distributeur de fausses commandes.
  commanderEnBoutique: { max: 5, fenetreS: 600 },
  // Administration : un centre d'appel enchaîne beaucoup d'actions, on reste large.
  avancerCommande: { max: 60, fenetreS: 60 },
  ajusterStock: { max: 60, fenetreS: 60 },
  inviterPersonnel: { max: 20, fenetreS: 600 },
};

interface Compteur {
  debut: number;
  nombre: number;
}

/** Retourne le compteur mis à jour, ou le nombre de secondes à attendre si la limite est atteinte. */
function compter(c: Compteur | undefined, limite: Limite, maintenant: number) {
  if (!c || maintenant - c.debut >= limite.fenetreS * 1000) return { suivant: { debut: maintenant, nombre: 1 } };
  if (c.nombre >= limite.max) {
    return { attente: Math.ceil((c.debut + limite.fenetreS * 1000 - maintenant) / 1000) };
  }
  return { suivant: { debut: c.debut, nombre: c.nombre + 1 } };
}

export async function limiterDebit(uid: string, action: string, maintenant = Date.now()) {
  const ref = db.doc(`limitesDebit/${uid}`);
  const attente = await db.runTransaction(async (tx) => {
    const compteurs = ((await tx.get(ref)).data() ?? {}) as Record<string, Compteur>;
    const pourAction = compter(compteurs[action], LIMITES[action] ?? PAR_DEFAUT, maintenant);
    const total = compter(compteurs._total, TOTAL, maintenant);
    const refus = pourAction.attente ?? total.attente;
    if (refus !== undefined) return refus;
    tx.set(ref, { [action]: pourAction.suivant, _total: total.suivant }, { merge: true });
    return 0;
  });

  if (attente > 0) {
    logger.warn("Limite de requêtes atteinte", { uid, action, attente });
    throw new HttpsError(
      "resource-exhausted",
      `Trop de demandes en peu de temps. Réessayez dans ${attente} seconde${attente > 1 ? "s" : ""}.`,
      { attente },
    );
  }
}

/** onCall avec limitation du débit : à utiliser pour toute fonction appelable depuis les applications. */
export function onAppel<T>(
  nom: string,
  options: CallableOptions,
  traiter: (req: CallableRequest<unknown>) => Promise<T>,
) {
  return onCall(options, async (req) => {
    // Sans connexion, la fonction refuse elle-même l'appel aussitôt : rien à compter.
    if (req.auth) {
      await limiterDebit(req.auth.uid, nom);
      await exigerHorsMaintenance(String(req.auth.token.role ?? ""));
    }
    return traiter(req);
  });
}
