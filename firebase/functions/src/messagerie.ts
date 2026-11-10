import { FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { roleDe } from "./commandes.js";
import { db, exigerConnexion, exigerMemePays, OPTIONS_APPEL, PAYS_DEFAUT } from "./commun.js";
import { onAppel } from "./limites.js";
import { notifier } from "./notifications.js";

/**
 * Messagerie entre chaque distributeur et le centre d'appel de son pays.
 * Une conversation par distributeur (conversations/{fboId}), ses messages en sous-collection.
 * Les messages ne s'écrivent que par cette fonction : auteur, rôle et date sont garantis par le serveur.
 */
const PERSONNEL = ["operateur", "admin", "super_admin"];

export const envoyerMessage = onAppel("envoyerMessage", OPTIONS_APPEL, async (req) => {
  const uid = exigerConnexion(req);
  const role = roleDe(req);
  const d = (req.data ?? {}) as { texte?: unknown; fboId?: unknown; commandeId?: unknown };
  const texte = typeof d.texte === "string" ? d.texte.trim().slice(0, 1000) : "";
  if (!texte) throw new HttpsError("invalid-argument", "Message vide.");

  let fboId: string;
  if (role === "fbo") {
    fboId = uid;
  } else if (PERSONNEL.includes(role)) {
    fboId = typeof d.fboId === "string" ? d.fboId : "";
  } else {
    throw new HttpsError("permission-denied", "La messagerie relie les distributeurs au centre d'appel.");
  }

  const profil = (await db.doc(`users/${fboId}`).get()).data();
  if (!profil || profil.role !== "fbo") throw new HttpsError("not-found", "Distributeur introuvable.");
  const pays = profil.pays || PAYS_DEFAUT;
  if (role !== "fbo") exigerMemePays(req, pays);

  // Un message peut citer une commande, à condition qu'elle soit bien celle de ce distributeur.
  const commandeId = typeof d.commandeId === "string" && d.commandeId ? d.commandeId : null;
  if (commandeId) {
    const commande = (await db.doc(`orders/${commandeId}`).get()).data();
    if (commande?.fboId !== fboId) throw new HttpsError("permission-denied", "Cette commande n'est pas la sienne.");
  }

  const duCentre = role !== "fbo";
  const conversation = db.doc(`conversations/${fboId}`);
  await db.runTransaction(async (tx) => {
    await tx.get(conversation);
    tx.set(
      conversation,
      {
        fboId,
        pays,
        nomFbo: [profil.prenom, profil.nom].filter(Boolean).join(" ") || String(profil.email ?? ""),
        dernierMessage: texte.slice(0, 120),
        dernierAt: FieldValue.serverTimestamp(),
        ...(duCentre ? { nonLusFbo: FieldValue.increment(1) } : { nonLusCentre: FieldValue.increment(1) }),
      },
      { merge: true },
    );
    tx.create(conversation.collection("messages").doc(), {
      auteurId: uid,
      duCentre,
      texte,
      commandeId,
      createdAt: FieldValue.serverTimestamp(),
    });
  });

  if (duCentre) await notifier(fboId, "Centre d'appel", texte.slice(0, 140), commandeId ?? "", `message_${Date.now()}`);
  return { ok: true };
});
