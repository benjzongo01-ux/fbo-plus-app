import { HttpsError } from "firebase-functions/v2/https";
import { exigerRole } from "./commandes.js";
import { db, OPTIONS_APPEL } from "./commun.js";
import { onAppel } from "./limites.js";

/**
 * « Mon équipe » : les filleuls du distributeur et leur activité du mois.
 * - filleuls inscrits sur FBO+ qui ont déclaré son ID Forever comme parrain ;
 * - filleuls pour qui il a commandé (bénéficiaire « filleul »).
 * Seuls des chiffres agrégés sont renvoyés : jamais les clients ni les commandes des filleuls.
 */
const INACTIVES = ["annulee", "retournee"];
const chiffres = (s: unknown) => String(s ?? "").replace(/\D/g, "");

interface Membre {
  idForever: string;
  nom: string;
  grade: string;
  ccMois: number;
  actif: boolean;
  surFboPlus: boolean;
}

export const monEquipe = onAppel("monEquipe", OPTIONS_APPEL, async (req) => {
  const uid = exigerRole(req, ["fbo"]);
  const moi = (await db.doc(`users/${uid}`).get()).data();
  const monId = chiffres(moi?.idDistributeur);
  if (!monId) throw new HttpsError("failed-precondition", "Renseignez votre ID Forever dans votre profil.");

  const debut = new Date();
  debut.setDate(1);
  debut.setHours(0, 0, 0, 0);
  const duMois = (c: FirebaseFirestore.DocumentData) =>
    !INACTIVES.includes(c.statut) && (c.createdAt?.toMillis?.() ?? 0) >= debut.getTime();

  const membres = new Map<string, Membre>();

  // 1. Filleuls inscrits sur FBO+.
  const inscrits = await db.collection("users").where("parrainIdForever", "==", monId).get();
  for (const f of inscrits.docs) {
    const p = f.data();
    if (p.role !== "fbo" || p.statut === "supprime") continue;
    const commandes = (await db.collection("orders").where("fboId", "==", f.id).get()).docs.map((d) => d.data());
    const cc = commandes.filter(duMois).reduce((s, c) => s + Number(c.ccTotal ?? 0), 0);
    const id = chiffres(p.idDistributeur) || f.id;
    membres.set(id, {
      idForever: p.idDistributeur ?? "",
      nom: [p.prenom, p.nom].filter(Boolean).join(" "),
      grade: p.grade ?? "Client Privilégié (CP)",
      ccMois: Math.round(cc * 1000) / 1000,
      actif: cc >= 4,
      surFboPlus: true,
    });
  }

  // 2. Filleuls pour qui le distributeur a commandé.
  const miennes = (await db.collection("orders").where("fboId", "==", uid).get()).docs.map((d) => d.data());
  for (const c of miennes) {
    if (c.beneficiaire?.type !== "filleul") continue;
    const id = chiffres(c.beneficiaire.idForever);
    const membre = membres.get(id) ?? {
      idForever: c.beneficiaire.idForever,
      nom: [c.beneficiaire.prenom, c.beneficiaire.nom].filter(Boolean).join(" "),
      grade: "",
      ccMois: 0,
      actif: false,
      surFboPlus: false,
    };
    if (!membre.surFboPlus && duMois(c)) {
      membre.ccMois = Math.round((membre.ccMois + Number(c.ccTotal ?? 0)) * 1000) / 1000;
      membre.actif = membre.ccMois >= 4;
    }
    membres.set(id, membre);
  }

  const equipe = [...membres.values()].sort((a, b) => b.ccMois - a.ccMois);
  return {
    equipe,
    ccEquipe: Math.round(equipe.reduce((s, m) => s + m.ccMois, 0) * 1000) / 1000,
    actifs: equipe.filter((m) => m.actif).length,
  };
});
