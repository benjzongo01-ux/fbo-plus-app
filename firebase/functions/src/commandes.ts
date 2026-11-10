import { CallableRequest, HttpsError } from "firebase-functions/v2/https";
import { onAppel } from "./limites.js";
import { FieldValue, Timestamp, Transaction } from "firebase-admin/firestore";
import { db, estPays, exigerConnexion, exigerMemePays, journaliser, OPTIONS_APPEL, PAYS_DEFAUT } from "./commun.js";

export const ENTREPOT_DEFAUT = "principal";

export const REMISES: Record<string, number> = {
  "Client Privilégié (CP)": 5,
  "Animateur Adjoint (AA)": 35,
  "Animateur (A)": 38,
  "Manager Adjoint (MA)": 43,
  "Manager (M)": 48,
};

const ROLES_PERSONNEL = ["operateur", "gestionnaire_stock", "admin", "super_admin"];
const ROLES_ADMIN = ["admin", "super_admin"];

interface LigneDemandee {
  produitId: string;
  quantite: number;
}

interface LigneCommande extends LigneDemandee {
  ref: string;
  nom: string;
  prixUnitaire: number;
  cc: number;
}

export function roleDe(req: CallableRequest<unknown>): string {
  return String(req.auth?.token.role ?? "");
}

export function exigerRole(req: CallableRequest<unknown>, roles: string[]): string {
  const uid = exigerConnexion(req);
  if (!roles.includes(roleDe(req))) {
    throw new HttpsError("permission-denied", "Action non autorisée pour ce rôle.");
  }
  return uid;
}

function lireLignes(valeur: unknown): LigneDemandee[] {
  if (!Array.isArray(valeur) || valeur.length === 0 || valeur.length > 100) {
    throw new HttpsError("invalid-argument", "La commande doit contenir entre 1 et 100 produits.");
  }
  const cumul = new Map<string, number>();
  for (const ligne of valeur as Record<string, unknown>[]) {
    const { produitId, quantite } = ligne;
    if (typeof produitId !== "string" || produitId.length === 0) {
      throw new HttpsError("invalid-argument", "Produit manquant.");
    }
    if (!Number.isInteger(quantite) || (quantite as number) < 1 || (quantite as number) > 1000) {
      throw new HttpsError("invalid-argument", "Quantité invalide.");
    }
    cumul.set(produitId, (cumul.get(produitId) ?? 0) + (quantite as number));
  }
  return [...cumul].map(([produitId, quantite]) => ({ produitId, quantite }));
}

function lireMontant(valeur: unknown): number {
  const montant = valeur ?? 0;
  if (!Number.isInteger(montant) || (montant as number) < 0) {
    throw new HttpsError("invalid-argument", "Montant invalide.");
  }
  return montant as number;
}

function texte(valeur: unknown, longueurMax = 300): string {
  return typeof valeur === "string" ? valeur.trim().slice(0, longueurMax) : "";
}

const TELEPHONE = /^\+?[0-9 ]{8,16}$/;

/** Un ID Forever contient au moins 6 chiffres ; tirets et espaces sont tolérés. */
function lireIdForever(valeur: unknown): string {
  const id = texte(valeur, 30);
  if (!/^[0-9][0-9 -]*$/.test(id) || id.replace(/\D/g, "").length < 6) {
    throw new HttpsError("invalid-argument", "ID Forever invalide.");
  }
  return id;
}

interface Beneficiaire {
  type: "moi" | "filleul";
  nom: string;
  prenom: string;
  idForever: string;
}

/** La commande est toujours validée sur un ID Forever : celui du distributeur ou de son filleul. */
function lireBeneficiaire(d: Record<string, unknown>, profil: FirebaseFirestore.DocumentData | undefined): Beneficiaire {
  if (d.pourQui === "filleul") {
    const nom = texte(d.filleulNom, 60);
    const prenom = texte(d.filleulPrenom, 60);
    if (!nom || !prenom) throw new HttpsError("invalid-argument", "Nom et prénom du filleul obligatoires.");
    return { type: "filleul", nom, prenom, idForever: lireIdForever(d.filleulId) };
  }
  if (d.pourQui !== "moi") throw new HttpsError("invalid-argument", "Indiquez pour qui est la commande.");

  const idForever = texte(profil?.idDistributeur, 30);
  if (!idForever) {
    throw new HttpsError("failed-precondition", "Renseignez votre ID Forever dans votre profil avant de commander.");
  }
  return {
    type: "moi",
    nom: texte(profil?.nom, 60),
    prenom: texte(profil?.prenom, 60),
    idForever,
  };
}

/** Les coordonnées du client ne sont demandées que pour une livraison. */
async function lireClientLivraison(tx: Transaction, d: Record<string, unknown>, uid: string) {
  const nom = texte(d.clientNom, 120);
  const telephone = texte(d.clientTelephone, 20);
  const telephone2 = texte(d.clientTelephone2, 20);
  const quartier = texte(d.clientQuartier, 120);
  if (!nom) throw new HttpsError("invalid-argument", "Nom et prénom du client obligatoires.");
  if (!TELEPHONE.test(telephone)) throw new HttpsError("invalid-argument", "Téléphone du client invalide.");
  if (telephone2 && !TELEPHONE.test(telephone2)) {
    throw new HttpsError("invalid-argument", "Deuxième téléphone du client invalide.");
  }
  if (!quartier) throw new HttpsError("invalid-argument", "Quartier du client obligatoire.");

  // Une fiche client choisie dans le carnet doit appartenir au distributeur.
  const clientId = texte(d.clientId, 128) || null;
  if (clientId) {
    const fiche = (await tx.get(db.doc(`customers/${clientId}`))).data();
    if (!fiche || fiche.ownerId !== uid) {
      throw new HttpsError("permission-denied", "Ce client ne vous appartient pas.");
    }
  }
  return { id: clientId as string | null, nom, telephone, telephone2, quartier, adresse: texte(d.adresse) };
}

type Mode = "livraison" | "validation" | "expedition";

/**
 * Zone de livraison facultative (commune, quartier) : son tarif remplace celui du pays.
 * Ex. Ouagadougou 2 000 FCFA partout ; Abidjan de 3 000 à 5 000 selon la commune.
 */
async function lireZone(tx: Transaction, d: Record<string, unknown>, pays: string) {
  const zoneId = texte(d.zoneId, 128);
  if (!zoneId) return null;
  const zone = (await tx.get(db.doc(`zonesLivraison/${zoneId}`))).data();
  if (!zone || zone.actif !== true || zone.pays !== pays) {
    throw new HttpsError("invalid-argument", "Zone de livraison inconnue pour ce pays.");
  }
  return { id: zoneId, nom: String(zone.nom), frais: Number(zone.frais ?? 0) };
}

function lireMode(valeur: unknown): Mode {
  if (valeur === "livraison" || valeur === "validation" || valeur === "expedition") return valeur;
  throw new HttpsError("invalid-argument", "Choisissez livraison, validation ou expédition.");
}

/**
 * Expédition vers une autre ville : la ville vient de la liste tenue par l'administration du pays,
 * qui fixe aussi ses frais. Le distributeur donne le destinataire et, s'il la connaît, la compagnie
 * de transport ; sinon celui qui expédie la choisit.
 */
async function lireExpedition(tx: Transaction, d: Record<string, unknown>, pays: string) {
  const villeId = texte(d.villeExpeditionId, 128);
  const ville = villeId ? (await tx.get(db.doc(`villesExpedition/${villeId}`))).data() : undefined;
  if (!ville || ville.actif !== true || ville.pays !== pays) {
    throw new HttpsError("invalid-argument", "Choisissez une ville d'expédition de la liste.");
  }
  const nom = texte(d.destinataireNom, 120);
  const telephone = texte(d.destinataireTelephone, 20);
  const telephone2 = texte(d.destinataireTelephone2, 20);
  if (!nom) throw new HttpsError("invalid-argument", "Nom et prénom du destinataire obligatoires.");
  if (!TELEPHONE.test(telephone)) throw new HttpsError("invalid-argument", "Téléphone du destinataire invalide.");
  if (telephone2 && !TELEPHONE.test(telephone2)) {
    throw new HttpsError("invalid-argument", "Deuxième téléphone du destinataire invalide.");
  }
  return {
    expedition: {
      villeId,
      ville: String(ville.ville),
      compagnie: texte(d.compagnie, 80),
      frais: Number(ville.frais ?? 0),
      numeroColis: "",
    },
    destinataire: { id: null, nom, telephone, telephone2, quartier: "", adresse: texte(d.adresse) },
  };
}

/** Les prix viennent toujours de products : ceux envoyés par un client sont ignorés. */
async function lignesAuPrixOfficiel(tx: Transaction, demandes: LigneDemandee[]): Promise<LigneCommande[]> {
  const refs = demandes.map((d) => db.doc(`products/${d.produitId}`));
  const docs = await tx.getAll(...refs);
  return docs.map((doc, i) => {
    const produit = doc.data();
    if (!produit || produit.actif !== true) {
      throw new HttpsError("failed-precondition", `Produit indisponible : ${demandes[i].produitId}.`);
    }
    return {
      produitId: doc.id,
      quantite: demandes[i].quantite,
      ref: String(produit.reference ?? doc.id),
      nom: String(produit.nom),
      prixUnitaire: Number(produit.prix),
      cc: Number(produit.cc ?? 0),
    };
  });
}

function calculerMontants(lignes: LigneCommande[], remiseGrade: number, reduction: number, frais: number) {
  const sousTotal = lignes.reduce((s, l) => s + l.prixUnitaire * l.quantite, 0);
  const margeBrute = Math.floor((sousTotal * remiseGrade) / 100);
  if (reduction > margeBrute) {
    throw new HttpsError("invalid-argument", `Réduction maximale : ${margeBrute} FCFA.`);
  }
  return {
    sousTotal,
    reduction,
    fraisLivraison: frais,
    total: sousTotal - reduction + frais,
    ccTotal: Math.round(lignes.reduce((s, l) => s + l.cc * l.quantite, 0) * 1000) / 1000,
    remiseGrade,
    beneficeFbo: margeBrute - reduction,
  };
}

/**
 * Déplace `quantite` du disponible vers le réservé (ou l'inverse si négatif).
 * Toutes les lectures doivent précéder les écritures dans une transaction : d'où les deux temps.
 */
async function preparerReservations(tx: Transaction, entrepot: string, ecarts: Map<string, number>) {
  const ids = [...ecarts.keys()].filter((id) => ecarts.get(id) !== 0);
  const docs = ids.length ? await tx.getAll(...ids.map((id) => db.doc(`stock/${entrepot}_${id}`))) : [];

  docs.forEach((doc, i) => {
    const ecart = ecarts.get(ids[i])!;
    if (ecart > 0 && Number(doc.data()?.disponible ?? 0) < ecart) {
      throw new HttpsError("failed-precondition", `Stock insuffisant pour le produit ${ids[i]}.`);
    }
  });

  return (auteurId: string, commandeId: string) => {
    docs.forEach((doc, i) => {
      const ecart = ecarts.get(ids[i])!;
      tx.set(
        doc.ref,
        {
          entrepotId: entrepot,
          produitId: ids[i],
          disponible: FieldValue.increment(-ecart),
          reserve: FieldValue.increment(ecart),
        },
        { merge: true },
      );
      tx.create(db.collection("stockMovements").doc(), {
        type: ecart > 0 ? "reservation" : "retour",
        entrepotId: entrepot,
        produitId: ids[i],
        quantite: Math.abs(ecart),
        commandeId,
        auteurId,
        date: FieldValue.serverTimestamp(),
      });
    });
  };
}

/**
 * Frais de livraison et commission du livreur : chaque pays a les siens (tarifsPays/{pays}).
 * Les anciens tarifs communs (parametres/tarifs) servent tant qu'un pays n'a pas réglé les siens.
 */
async function tarifsDuPays(tx: Transaction, pays: string) {
  const [propres, communs] = await tx.getAll(
    db.doc(`tarifsPays/${pays || PAYS_DEFAUT}`),
    db.doc("parametres/tarifs"),
  );
  const t = propres.exists ? propres.data()! : communs.data() ?? {};
  const fraisLivraison = Number(t.fraisLivraison ?? 0);
  return {
    fraisLivraison,
    // Fourchette de livraison du pays (Burkina 1 000 à 2 000, Côte d'Ivoire 2 000 à 5 000).
    fraisMin: Number(t.fraisMin ?? fraisLivraison),
    fraisMax: Number(t.fraisMax ?? fraisLivraison),
    commissionLivreur: Number(t.commissionLivreur ?? 0),
  };
}

const PALIER_FRAIS = 500;

/**
 * Frais de livraison choisis à la commande (proche ou plus reculé) : ils doivent rester dans la
 * fourchette du pays, par paliers de 500 FCFA. Sans choix, le tarif de base du pays s'applique.
 */
function fraisDansFourchette(valeur: unknown, t: { fraisLivraison: number; fraisMin: number; fraisMax: number }) {
  if (valeur === undefined || valeur === null || valeur === "" || t.fraisMin === t.fraisMax) {
    return Math.min(Math.max(t.fraisLivraison, t.fraisMin), t.fraisMax);
  }
  const frais = Number(valeur);
  if (!Number.isInteger(frais) || frais < t.fraisMin || frais > t.fraisMax || frais % PALIER_FRAIS !== 0) {
    throw new HttpsError(
      "invalid-argument",
      `Frais de livraison entre ${t.fraisMin} et ${t.fraisMax} FCFA, par paliers de ${PALIER_FRAIS}.`,
    );
  }
  return frais;
}

/**
 * Un pays peut avoir plusieurs entrepôts : la commande part du premier (par ordre de priorité,
 * puis de nom) qui a tout le stock demandé. Sinon, le premier entrepôt signalera le manque.
 */
async function choisirEntrepot(tx: Transaction, pays: string, demandes: LigneDemandee[]) {
  const entrepots = (
    await tx.get(db.collection("warehouses").where("pays", "==", pays).where("actif", "==", true))
  ).docs.sort(
    (a, b) =>
      Number(a.data().priorite ?? 100) - Number(b.data().priorite ?? 100) ||
      String(a.data().nom ?? a.id).localeCompare(String(b.data().nom ?? b.id)),
  );
  if (entrepots.length === 0) {
    throw new HttpsError("failed-precondition", `Les commandes ne sont pas encore ouvertes pour : ${pays}.`);
  }
  if (entrepots.length === 1) return entrepots[0].id;

  for (const entrepot of entrepots) {
    const stocks = await tx.getAll(...demandes.map((l) => db.doc(`stock/${entrepot.id}_${l.produitId}`)));
    if (stocks.every((s, i) => Number(s.data()?.disponible ?? 0) >= demandes[i].quantite)) return entrepot.id;
  }
  return entrepots[0].id;
}

const etapeHistorique = (statut: string, auteurId: string) => ({
  statut,
  auteurId,
  date: Timestamp.now(),
});

export const creerCommande = onAppel("creerCommande", OPTIONS_APPEL, async (req) => {
  const uid = exigerRole(req, ["fbo"]);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const demandes = lireLignes(d.lignes);
  const reduction = lireMontant(d.reduction);
  const cle = texte(d.cleIdempotence, 64);
  if (!/^[\w-]{8,64}$/.test(cle)) {
    throw new HttpsError("invalid-argument", "Clé d'idempotence invalide.");
  }

  // Une nouvelle tentative après une coupure réseau ne doit pas créer une seconde commande.
  const ref = db.doc(`orders/${uid}_${cle}`);

  const resultat = await db.runTransaction(async (tx) => {
    const existante = await tx.get(ref);
    if (existante.exists) return { id: ref.id, total: existante.data()!.total as number, cree: false };

    const profil = (await tx.get(db.doc(`users/${uid}`))).data();

    if (!estPays(d.pays)) throw new HttpsError("invalid-argument", "Choisissez le pays de la commande.");
    const pays = d.pays;
    const tarifs = await tarifsDuPays(tx, pays);
    const entrepotId = await choisirEntrepot(tx, pays, demandes);
    const mode = lireMode(d.mode);
    const beneficiaire = lireBeneficiaire(d, profil);
    const client = mode === "livraison" ? await lireClientLivraison(tx, d, uid) : null;
    // Carnet de clients : retrouve la fiche par le téléphone, ou en prépare une nouvelle.
    let nouvelleFiche: FirebaseFirestore.DocumentReference | null = null;
    if (client && !client.id) {
      const existantes = await tx.get(
        db.collection("customers").where("ownerId", "==", uid).where("telephone", "==", client.telephone).limit(1),
      );
      if (existantes.empty) nouvelleFiche = db.collection("customers").doc();
      client.id = existantes.empty ? nouvelleFiche!.id : existantes.docs[0].id;
    }
    const zone = mode === "livraison" ? await lireZone(tx, d, pays) : null;
    const envoi = mode === "expedition" ? await lireExpedition(tx, d, pays) : null;
    const frais =
      mode === "livraison" ? (zone?.frais ?? fraisDansFourchette(d.fraisLivraison, tarifs)) : (envoi?.expedition.frais ?? 0);

    const lignes = await lignesAuPrixOfficiel(tx, demandes);
    const montants = calculerMontants(
      lignes,
      REMISES[String(profil?.grade)] ?? REMISES["Client Privilégié (CP)"],
      reduction,
      frais,
    );

    const ecarts = new Map(lignes.map((l) => [l.produitId, l.quantite]));
    const reserver = await preparerReservations(tx, entrepotId, ecarts);
    reserver(uid, ref.id);
    if (nouvelleFiche && client) {
      tx.create(nouvelleFiche, {
        ownerId: uid,
        nom: client.nom,
        telephone: client.telephone,
        adresse: [client.quartier, client.adresse].filter(Boolean).join(", "),
        notes: "",
        creeeParCommande: true,
      });
    }

    tx.create(ref, {
      pays,
      fboId: uid,
      titre: texte(d.titre, 120) || "Commande",
      // Note du distributeur pour le centre d'appel et le livreur (horaires, consignes…).
      note: texte(d.note, 500),
      mode,
      beneficiaire,
      // En expédition, « client » désigne le destinataire du colis.
      client: client ?? envoi?.destinataire ?? null,
      zone: zone ? { id: zone.id, nom: zone.nom } : null,
      expedition: envoi?.expedition ?? null,
      adresse: client
        ? [client.quartier, client.adresse].filter(Boolean).join(", ")
        : envoi
          ? [envoi.expedition.ville, envoi.destinataire.adresse].filter(Boolean).join(", ")
          : "",
      lignes,
      ...montants,
      entrepotId,
      statut: "nouvelle",
      historiqueStatuts: [etapeHistorique("nouvelle", uid)],
      livreurId: null,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    return { id: ref.id, total: montants.total, cree: true };
  });

  if (resultat.cree) await journaliser("commande_creee", uid, resultat.id, { total: resultat.total });
  return { id: resultat.id, total: resultat.total };
});

export const modifierCommande = onAppel("modifierCommande", OPTIONS_APPEL, async (req) => {
  const uid = exigerConnexion(req);
  const role = roleDe(req);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const id = texte(d.id, 200);
  const demandes = lireLignes(d.lignes);
  const reduction = lireMontant(d.reduction);
  const ref = db.doc(`orders/${id}`);

  await db.runTransaction(async (tx) => {
    const commande = (await tx.get(ref)).data();
    if (!commande) throw new HttpsError("not-found", "Commande introuvable.");

    const estProprietaire = role === "fbo" && commande.fboId === uid;
    const estCentreAppel = ["operateur", ...ROLES_ADMIN].includes(role);
    if (!estProprietaire && !estCentreAppel) {
      throw new HttpsError("permission-denied", "Cette commande ne vous appartient pas.");
    }
    if (!estProprietaire) exigerMemePays(req, commande.pays);
    const statuts = estCentreAppel ? ["nouvelle", "confirmee"] : ["nouvelle"];
    if (!statuts.includes(commande.statut)) {
      throw new HttpsError("failed-precondition", "Cette commande ne peut plus être modifiée.");
    }

    const lignes = await lignesAuPrixOfficiel(tx, demandes);
    const montants = calculerMontants(lignes, commande.remiseGrade, reduction, commande.fraisLivraison);

    const ecarts = new Map<string, number>();
    for (const l of commande.lignes as LigneCommande[]) ecarts.set(l.produitId, -l.quantite);
    for (const l of lignes) ecarts.set(l.produitId, (ecarts.get(l.produitId) ?? 0) + l.quantite);
    const reserver = await preparerReservations(tx, commande.entrepotId, ecarts);
    reserver(uid, id);

    tx.update(ref, {
      lignes,
      ...montants,
      titre: texte(d.titre, 120) || commande.titre,
      adresse: d.adresse === undefined ? commande.adresse : texte(d.adresse),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  await journaliser("commande_modifiee", uid, id);
  return { id };
});

/** Rend au stock disponible tout ce qui était réservé pour la commande. */
async function libererStock(tx: Transaction, commande: FirebaseFirestore.DocumentData) {
  const ecarts = new Map(
    (commande.lignes as LigneCommande[]).map((l) => [l.produitId, -l.quantite]),
  );
  return preparerReservations(tx, commande.entrepotId, ecarts);
}

export const annulerCommande = onAppel("annulerCommande", OPTIONS_APPEL, async (req) => {
  const uid = exigerConnexion(req);
  const role = roleDe(req);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const id = texte(d.id, 200);
  const ref = db.doc(`orders/${id}`);

  await db.runTransaction(async (tx) => {
    const commande = (await tx.get(ref)).data();
    if (!commande) throw new HttpsError("not-found", "Commande introuvable.");

    const estProprietaire = role === "fbo" && commande.fboId === uid;
    const estCentreAppel = ["operateur", ...ROLES_ADMIN].includes(role);
    if (!estProprietaire && !estCentreAppel) {
      throw new HttpsError("permission-denied", "Cette commande ne vous appartient pas.");
    }
    if (!estProprietaire) exigerMemePays(req, commande.pays);
    const statuts = estCentreAppel
      ? ["nouvelle", "confirmee", "en_preparation", "prete", "assignee"]
      : ["nouvelle", "confirmee"];
    if (!statuts.includes(commande.statut)) {
      throw new HttpsError("failed-precondition", "Cette commande ne peut plus être annulée.");
    }

    const liberer = await libererStock(tx, commande);
    liberer(uid, id);
    tx.update(ref, {
      statut: "annulee",
      motifAnnulation: texte(d.motif),
      historiqueStatuts: FieldValue.arrayUnion(etapeHistorique("annulee", uid)),
      updatedAt: FieldValue.serverTimestamp(),
    });
    if (commande.statut === "assignee") {
      tx.update(db.doc(`deliveries/${id}`), {
        statut: "annulee",
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
  });

  await journaliser("commande_annulee", uid, id, { motif: texte(d.motif) });
  return { id };
});

const TRANSITIONS: Record<string, { vers: string; roles: string[] }> = {
  nouvelle: { vers: "confirmee", roles: ["operateur", ...ROLES_ADMIN] },
  confirmee: { vers: "en_preparation", roles: ["gestionnaire_stock", ...ROLES_ADMIN] },
  en_preparation: { vers: "prete", roles: ["gestionnaire_stock", ...ROLES_ADMIN] },
};

/** Avance la commande d'une étape : confirmation, préparation, prête à livrer. */
export const avancerCommande = onAppel("avancerCommande", OPTIONS_APPEL, async (req) => {
  const uid = exigerConnexion(req);
  const id = texte((req.data as Record<string, unknown> | null)?.id, 200);
  const ref = db.doc(`orders/${id}`);

  const statut = await db.runTransaction(async (tx) => {
    const commande = (await tx.get(ref)).data();
    if (!commande) throw new HttpsError("not-found", "Commande introuvable.");
    exigerMemePays(req, commande.pays);
    const transition = TRANSITIONS[commande.statut];
    if (!transition) throw new HttpsError("failed-precondition", "Aucune étape suivante possible.");
    if (!transition.roles.includes(roleDe(req))) {
      throw new HttpsError("permission-denied", "Action non autorisée pour ce rôle.");
    }
    tx.update(ref, {
      statut: transition.vers,
      historiqueStatuts: FieldValue.arrayUnion(etapeHistorique(transition.vers, uid)),
      updatedAt: FieldValue.serverTimestamp(),
    });
    return transition.vers;
  });

  await journaliser("commande_statut", uid, id, { statut });
  return { id, statut };
});

/**
 * Assigne un livreur à une commande prête (écritures dans la transaction fournie) : mission créée,
 * commission du pays. Utilisé par le centre d'appel et par l'affectation automatique.
 */
export async function affecterLivreur(
  tx: Transaction,
  ref: FirebaseFirestore.DocumentReference,
  commande: FirebaseFirestore.DocumentData,
  livreurId: string,
  uid: string,
) {
  const id = ref.id;
  if (commande.mode === "validation" || commande.mode === "expedition") {
    throw new HttpsError("failed-precondition", "Seule une commande en livraison reçoit un livreur.");
  }
  if (!["prete", "assignee"].includes(commande.statut)) {
    throw new HttpsError("failed-precondition", "La commande n'est pas prête à être livrée.");
  }
  const livreur = (await tx.get(db.doc(`users/${livreurId}`))).data();
  if (!livreur || livreur.role !== "livreur" || livreur.statut !== "actif") {
    throw new HttpsError("failed-precondition", "Ce compte n'est pas un livreur actif.");
  }
  if (livreur.kyc?.statut !== "valide") {
    throw new HttpsError("failed-precondition", "L'identité de ce livreur n'est pas encore vérifiée.");
  }
  if ((livreur.pays || PAYS_DEFAUT) !== (commande.pays || PAYS_DEFAUT)) {
    throw new HttpsError("failed-precondition", "Ce livreur travaille dans un autre pays.");
  }

  const tarifs = await tarifsDuPays(tx, commande.pays);
  tx.update(ref, {
    statut: "assignee",
    livreurId,
    historiqueStatuts: FieldValue.arrayUnion(etapeHistorique("assignee", uid)),
    updatedAt: FieldValue.serverTimestamp(),
  });
  tx.set(db.doc(`deliveries/${id}`), {
    commandeId: id,
    livreurId,
    fboId: commande.fboId,
    pays: commande.pays || PAYS_DEFAUT,
    client: commande.client,
    adresse: commande.adresse,
    note: commande.note ?? "",
    lignes: commande.lignes,
    montantAEncaisser: commande.total,
    commission: tarifs.commissionLivreur,
    statut: "assignee",
    montantEncaisse: 0,
    preuvePhoto: null,
    signature: null,
    position: null,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });
}

export const assignerLivreur = onAppel("assignerLivreur", OPTIONS_APPEL, async (req) => {
  const uid = exigerRole(req, ["operateur", ...ROLES_ADMIN]);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const id = texte(d.id, 200);
  const livreurId = texte(d.livreurId, 128);
  const ref = db.doc(`orders/${id}`);

  await db.runTransaction(async (tx) => {
    const commande = (await tx.get(ref)).data();
    if (!commande) throw new HttpsError("not-found", "Commande introuvable.");
    exigerMemePays(req, commande.pays);
    await affecterLivreur(tx, ref, commande, livreurId, uid);
  });

  await journaliser("livreur_assigne", uid, id, { livreurId });
  return { id };
});

export const demarrerLivraison = onAppel("demarrerLivraison", OPTIONS_APPEL, async (req) => {
  const uid = exigerRole(req, ["livreur"]);
  const id = texte((req.data as Record<string, unknown> | null)?.id, 200);

  await db.runTransaction(async (tx) => {
    const livraison = (await tx.get(db.doc(`deliveries/${id}`))).data();
    if (!livraison || livraison.livreurId !== uid) {
      throw new HttpsError("permission-denied", "Cette mission ne vous est pas assignée.");
    }
    if (livraison.statut !== "assignee") {
      throw new HttpsError("failed-precondition", "Cette mission est déjà démarrée.");
    }
    tx.update(db.doc(`deliveries/${id}`), {
      statut: "en_livraison",
      updatedAt: FieldValue.serverTimestamp(),
    });
    tx.update(db.doc(`orders/${id}`), {
      statut: "en_livraison",
      historiqueStatuts: FieldValue.arrayUnion(etapeHistorique("en_livraison", uid)),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  await journaliser("livraison_demarree", uid, id);
  return { id };
});

export const terminerLivraison = onAppel("terminerLivraison", OPTIONS_APPEL, async (req) => {
  const uid = exigerRole(req, ["livreur"]);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const id = texte(d.id, 200);
  const montant = lireMontant(d.montantEncaisse);
  const preuvePhoto = texte(d.preuvePhoto);
  const signature = texte(d.signature);
  const dossier = `deliveries/${id}/${uid}/`;
  if (!preuvePhoto.startsWith(dossier) || !signature.startsWith(dossier)) {
    throw new HttpsError("invalid-argument", "Photo et signature obligatoires.");
  }
  const refCommande = db.doc(`orders/${id}`);
  const refLivraison = db.doc(`deliveries/${id}`);

  await db.runTransaction(async (tx) => {
    const livraison = (await tx.get(refLivraison)).data();
    if (!livraison || livraison.livreurId !== uid) {
      throw new HttpsError("permission-denied", "Cette mission ne vous est pas assignée.");
    }
    if (livraison.statut !== "en_livraison") {
      throw new HttpsError("failed-precondition", "Cette mission n'est pas en cours.");
    }
    if (montant !== livraison.montantAEncaisser) {
      throw new HttpsError("invalid-argument", `Montant attendu : ${livraison.montantAEncaisser} FCFA.`);
    }

    const commande = (await tx.get(refCommande)).data()!;
    const stocks = await tx.getAll(
      ...(commande.lignes as LigneCommande[]).map((l) =>
        db.doc(`stock/${commande.entrepotId}_${l.produitId}`),
      ),
    );

    // Le produit quitte l'entrepôt : il sort du réservé sans revenir au disponible.
    (commande.lignes as LigneCommande[]).forEach((l, i) => {
      tx.update(stocks[i].ref, { reserve: FieldValue.increment(-l.quantite) });
      tx.create(db.collection("stockMovements").doc(), {
        type: "sortie",
        entrepotId: commande.entrepotId,
        produitId: l.produitId,
        quantite: l.quantite,
        commandeId: id,
        auteurId: uid,
        date: FieldValue.serverTimestamp(),
      });
    });
    tx.update(refLivraison, {
      statut: "livree",
      montantEncaisse: montant,
      preuvePhoto,
      signature,
      livreeAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    tx.update(refCommande, {
      statut: "livree",
      historiqueStatuts: FieldValue.arrayUnion(etapeHistorique("livree", uid)),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  await journaliser("livraison_terminee", uid, id, { montant });
  return { id };
});

export const signalerRetour = onAppel("signalerRetour", OPTIONS_APPEL, async (req) => {
  const uid = exigerRole(req, ["livreur"]);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const id = texte(d.id, 200);
  const motif = texte(d.motif);
  if (!motif) throw new HttpsError("invalid-argument", "Indiquez le motif du retour.");
  const refCommande = db.doc(`orders/${id}`);
  const refLivraison = db.doc(`deliveries/${id}`);

  await db.runTransaction(async (tx) => {
    const livraison = (await tx.get(refLivraison)).data();
    if (!livraison || livraison.livreurId !== uid) {
      throw new HttpsError("permission-denied", "Cette mission ne vous est pas assignée.");
    }
    if (livraison.statut !== "en_livraison") {
      throw new HttpsError("failed-precondition", "Cette mission n'est pas en cours.");
    }
    const commande = (await tx.get(refCommande)).data()!;
    const liberer = await libererStock(tx, commande);
    liberer(uid, id);
    tx.update(refLivraison, {
      statut: "retournee",
      motifRetour: motif,
      updatedAt: FieldValue.serverTimestamp(),
    });
    tx.update(refCommande, {
      statut: "retournee",
      motifRetour: motif,
      historiqueStatuts: FieldValue.arrayUnion(etapeHistorique("retournee", uid)),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  await journaliser("livraison_retournee", uid, id, { motif });
  return { id };
});

/**
 * Clôture d'une commande sans livraison : l'achat est validé sur l'ID Forever du bénéficiaire
 * et le produit quitte l'entrepôt.
 */
/** Le stock réservé quitte définitivement l'entrepôt. Lectures d'abord, écritures ensuite. */
async function sortirStock(tx: Transaction, commande: FirebaseFirestore.DocumentData, id: string, uid: string) {
  const lignes = commande.lignes as LigneCommande[];
  const stocks = await tx.getAll(...lignes.map((l) => db.doc(`stock/${commande.entrepotId}_${l.produitId}`)));
  lignes.forEach((l, i) => {
    tx.update(stocks[i].ref, { reserve: FieldValue.increment(-l.quantite) });
    tx.create(db.collection("stockMovements").doc(), {
      type: "sortie",
      entrepotId: commande.entrepotId,
      produitId: l.produitId,
      quantite: l.quantite,
      commandeId: id,
      auteurId: uid,
      date: FieldValue.serverTimestamp(),
    });
  });
}

/** Le centre d'appel ajuste les frais d'une expédition (colis lourd, autre tarif de la compagnie). */
export const fixerFraisExpedition = onAppel("fixerFraisExpedition", OPTIONS_APPEL, async (req) => {
  const uid = exigerRole(req, ["operateur", ...ROLES_ADMIN]);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const id = texte(d.id, 200);
  const frais = lireMontant(d.frais);
  const ref = db.doc(`orders/${id}`);

  await db.runTransaction(async (tx) => {
    const commande = (await tx.get(ref)).data();
    if (!commande) throw new HttpsError("not-found", "Commande introuvable.");
    exigerMemePays(req, commande.pays);
    if (commande.mode !== "expedition" && commande.mode !== "livraison") {
      throw new HttpsError("failed-precondition", "Cette commande n'a pas de frais de transport.");
    }
    if (!["nouvelle", "confirmee"].includes(commande.statut)) {
      throw new HttpsError("failed-precondition", "Les frais ne peuvent plus changer à cette étape.");
    }
    if (commande.mode === "livraison") fraisDansFourchette(frais, await tarifsDuPays(tx, commande.pays));
    const montants = calculerMontants(commande.lignes, commande.remiseGrade, commande.reduction, frais);
    tx.update(ref, {
      ...montants,
      ...(commande.mode === "expedition" ? { "expedition.frais": frais } : {}),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  await journaliser("frais_expedition", uid, id, { frais });
  return { id };
});

/**
 * Le colis est remis à la compagnie : le stock sort et le numéro du colis est noté.
 * Si le distributeur n'a pas indiqué de compagnie, celui qui expédie la choisit ici.
 */
export const expedierCommande = onAppel("expedierCommande", OPTIONS_APPEL, async (req) => {
  const uid = exigerRole(req, ["operateur", ...ROLES_ADMIN]);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const id = texte(d.id, 200);
  const numeroColis = texte(d.numeroColis, 60);
  const ref = db.doc(`orders/${id}`);

  await db.runTransaction(async (tx) => {
    const commande = (await tx.get(ref)).data();
    if (!commande) throw new HttpsError("not-found", "Commande introuvable.");
    exigerMemePays(req, commande.pays);
    if (commande.mode !== "expedition") {
      throw new HttpsError("failed-precondition", "Cette commande n'est pas une expédition.");
    }
    if (commande.statut !== "prete") {
      throw new HttpsError("failed-precondition", "La commande doit d'abord être prête.");
    }
    const compagnie = texte(d.compagnie, 80) || String(commande.expedition?.compagnie ?? "");
    if (!compagnie) throw new HttpsError("invalid-argument", "Indiquez la compagnie qui transporte le colis.");

    await sortirStock(tx, commande, id, uid);
    tx.update(ref, {
      statut: "expediee",
      "expedition.compagnie": compagnie,
      "expedition.numeroColis": numeroColis,
      historiqueStatuts: FieldValue.arrayUnion(etapeHistorique("expediee", uid)),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  await journaliser("commande_expediee", uid, id, { numeroColis });
  return { id };
});

export const validerSurId = onAppel("validerSurId", OPTIONS_APPEL, async (req) => {
  const uid = exigerRole(req, ["operateur", ...ROLES_ADMIN]);
  const id = texte((req.data as Record<string, unknown> | null)?.id, 200);
  const ref = db.doc(`orders/${id}`);

  await db.runTransaction(async (tx) => {
    const commande = (await tx.get(ref)).data();
    if (!commande) throw new HttpsError("not-found", "Commande introuvable.");
    exigerMemePays(req, commande.pays);
    if (commande.mode !== "validation") {
      throw new HttpsError("failed-precondition", "Cette commande est à livrer, pas à valider.");
    }
    if (commande.statut !== "prete") {
      throw new HttpsError("failed-precondition", "La commande doit d'abord être prête.");
    }

    await sortirStock(tx, commande, id, uid);
    tx.update(ref, {
      statut: "validee",
      historiqueStatuts: FieldValue.arrayUnion(etapeHistorique("validee", uid)),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  await journaliser("commande_validee", uid, id);
  return { id };
});

/** Entrée de marchandise ou correction après inventaire. */
export const ajusterStock = onAppel("ajusterStock", OPTIONS_APPEL, async (req) => {
  const uid = exigerRole(req, ["gestionnaire_stock", ...ROLES_ADMIN]);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const entrepotId = texte(d.entrepotId, 80) || ENTREPOT_DEFAUT;
  const produitId = texte(d.produitId, 80);
  const type = d.type;
  const quantite = d.quantite;
  if (type !== "entree" && type !== "inventaire") {
    throw new HttpsError("invalid-argument", "Type de mouvement inconnu.");
  }
  if (!Number.isInteger(quantite) || (quantite as number) < 0 || (type === "entree" && quantite === 0)) {
    throw new HttpsError("invalid-argument", "Quantité invalide.");
  }
  const ref = db.doc(`stock/${entrepotId}_${produitId}`);

  await db.runTransaction(async (tx) => {
    if (!(await tx.get(db.doc(`products/${produitId}`))).exists) {
      throw new HttpsError("not-found", "Produit introuvable.");
    }
    const entrepot = (await tx.get(db.doc(`warehouses/${entrepotId}`))).data();
    if (!entrepot) throw new HttpsError("not-found", "Entrepôt introuvable.");
    exigerMemePays(req, entrepot.pays);
    const actuel = (await tx.get(ref)).data();
    const disponible =
      type === "entree" ? Number(actuel?.disponible ?? 0) + (quantite as number) : (quantite as number);
    tx.set(
      ref,
      {
        entrepotId,
        pays: entrepot.pays || PAYS_DEFAUT,
        produitId,
        disponible,
        reserve: actuel?.reserve ?? 0,
        seuilAlerte: actuel?.seuilAlerte ?? 10,
      },
      { merge: true },
    );
    tx.create(db.collection("stockMovements").doc(), {
      type,
      entrepotId,
      produitId,
      quantite,
      motif: texte(d.motif),
      auteurId: uid,
      date: FieldValue.serverTimestamp(),
    });
  });

  await journaliser("stock_ajuste", uid, ref.id, { type, quantite });
  return { id: ref.id };
});

/**
 * Recopie l'historique local des ventes. Ces ventes sont déjà conclues : elles ne touchent
 * pas au stock, et leurs montants d'époque sont conservés tels quels. Le relancer ne crée pas de doublon.
 */
export const importerVentes = onAppel("importerVentes", OPTIONS_APPEL, async (req) => {
  const uid = exigerRole(req, ["fbo"]);
  const ventes = (req.data as Record<string, unknown> | null)?.ventes;
  if (!Array.isArray(ventes) || ventes.length > 2000) {
    throw new HttpsError("invalid-argument", "Historique invalide.");
  }

  let importees = 0;
  const lot = db.bulkWriter();
  for (const v of ventes as Record<string, unknown>[]) {
    const idLocal = texte(v.id, 40);
    if (!/^\d+$/.test(idLocal) || !Array.isArray(v.lignes)) continue;
    const lignes = (v.lignes as Record<string, unknown>[]).map((l) => ({
      produitId: texte(l.produitRef, 80),
      ref: texte(l.produitRef, 80),
      nom: texte(l.produitNom, 120),
      quantite: Number(l.quantite) || 0,
      prixUnitaire: Number(l.prixUnitaire) || 0,
      cc: Number(l.ccUnitaire) || 0,
    }));
    const sousTotal = lignes.reduce((s, l) => s + l.prixUnitaire * l.quantite, 0);
    const remiseGrade = Number(v.reductionPourcentage) || 0;
    const date = Timestamp.fromMillis(Number(v.dateMillis) || Date.now());

    lot.create(db.doc(`orders/import_${uid}_${idLocal}`), {
      fboId: uid,
      titre: texte(v.titre, 120) || "Commande",
      client: { id: null, nom: "", telephone: "" },
      adresse: "",
      lignes,
      sousTotal,
      reduction: 0,
      fraisLivraison: 0,
      total: sousTotal,
      ccTotal: Math.round(lignes.reduce((s, l) => s + l.cc * l.quantite, 0) * 1000) / 1000,
      remiseGrade,
      beneficeFbo: Math.floor((sousTotal * remiseGrade) / 100),
      statut: "livree",
      source: "import_local",
      historiqueStatuts: [{ statut: "livree", auteurId: uid, date }],
      livreurId: null,
      createdAt: date,
      updatedAt: date,
    }).then(
      () => {
        importees++;
      },
      () => undefined, // Déjà importée lors d'un précédent passage.
    );
  }
  await lot.close();

  if (importees > 0) await journaliser("ventes_importees", uid, uid, { importees });
  return { importees };
});

export { ROLES_PERSONNEL };
