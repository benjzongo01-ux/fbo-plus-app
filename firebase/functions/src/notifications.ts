import { FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { db } from "./commun.js";

const MESSAGES_FBO: Record<string, string> = {
  confirmee: "Votre commande est confirmée.",
  en_preparation: "Votre commande est en préparation.",
  prete: "Votre commande est prête.",
  assignee: "Un livreur a été assigné à votre commande.",
  en_livraison: "Votre commande est en cours de livraison.",
  livree: "Votre commande a été livrée.",
  annulee: "Votre commande a été annulée.",
  retournee: "Votre commande n'a pas pu être livrée.",
};

/** L'émulateur n'envoie pas de push : seule la notification Firestore y est produite. */
async function envoyerPush(userId: string, titre: string, corps: string, commandeId: string) {
  if (process.env.FUNCTIONS_EMULATOR === "true") return;

  const tokens = await db.collection("fcmTokens").where("userId", "==", userId).get();
  if (tokens.empty) return;

  const envoi = await getMessaging().sendEachForMulticast({
    tokens: tokens.docs.map((d) => d.id),
    notification: { title: titre, body: corps },
    data: { commandeId },
  });

  // Un appareil désinstallé renvoie un jeton périmé : on le retire pour ne plus l'essayer.
  await Promise.all(
    envoi.responses.map((r, i) => {
      const code = r.error?.code;
      return code === "messaging/registration-token-not-registered" ||
        code === "messaging/invalid-registration-token"
        ? tokens.docs[i].ref.delete()
        : undefined;
    }),
  );
}

async function notifier(userId: string, titre: string, corps: string, commandeId: string, cle: string) {
  // Un déclencheur peut s'exécuter deux fois : l'identifiant fixe évite le doublon.
  const creee = await db
    .doc(`notifications/${commandeId}_${cle}_${userId}`)
    .create({
      userId,
      titre,
      corps,
      commandeId,
      lu: false,
      createdAt: FieldValue.serverTimestamp(),
    })
    .then(
      () => true,
      () => false,
    );
  if (!creee) return;

  try {
    await envoyerPush(userId, titre, corps, commandeId);
  } catch (e) {
    // La notification reste visible dans l'application même si le push échoue.
    logger.warn("Échec de l'envoi push", e);
  }
}

export const notifierCommande = onDocumentUpdated(
  { document: "orders/{id}", region: "europe-west1" },
  async (evenement) => {
    const avant = evenement.data?.before.data();
    const apres = evenement.data?.after.data();
    if (!avant || !apres) return;
    const id = evenement.params.id;

    if (avant.statut !== apres.statut && MESSAGES_FBO[apres.statut]) {
      const client = apres.client?.nom ? ` (${apres.client.nom})` : "";
      await notifier(apres.fboId, "Suivi de commande", `${MESSAGES_FBO[apres.statut]}${client}`, id, apres.statut);
    }

    if (apres.livreurId && avant.livreurId !== apres.livreurId) {
      await notifier(apres.livreurId, "Nouvelle mission", `Livraison à ${apres.adresse || "préciser"}`, id, "mission");
    }
  },
);
