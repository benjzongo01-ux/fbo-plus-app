import { randomUUID } from "node:crypto";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { adminDb, cle, codeErreur, compte, fermerApplis, preparerAdmin } from "./outils.js";

/*
 * Répartition automatique de l'argent d'une commande livrée et régularisation au siège.
 * Tout se passe au Sénégal, avec ses propres tarifs : aucun effet sur les autres fichiers de tests.
 */
const SN = "Sénégal";
let produit: string;

beforeAll(async () => {
  preparerAdmin();
  produit = randomUUID();
  await adminDb().doc("warehouses/dakar").set({ nom: "Dakar", pays: SN, actif: true });
  await adminDb().doc(`products/${produit}`).set({ nom: "Pulpe", reference: "15", prix: 22000, cc: 0.1, actif: true });
  await adminDb().doc(`stock/dakar_${produit}`).set({ disponible: 1000, reserve: 0 });
  await adminDb().doc(`tarifsPays/${SN}`).set({ fraisLivraison: 1500, fraisMin: 1000, fraisMax: 2000, commissionLivreur: 1000 });
});
afterAll(fermerApplis);

const wallet = async (uid: string) => (await adminDb().doc(`wallets/${uid}`).get()).data() ?? {};
const commande = async (id: string) => (await adminDb().doc(`orders/${id}`).get()).data()!;

/** Commande d'un Manager (48 %) pour 2 × 22 000, livrée au Sénégal, payée par mobile money ou en espèces. */
async function livrer(options: { prixClient?: number; clientPaieLivraison?: boolean; mobile: boolean; wallet?: number }) {
  const u = await compte("fbo", { grade: "Manager (M)", idDistributeur: `221${Date.now() % 1e9}`, prenom: "Awa", nom: "Diop", pays: SN });
  if (options.wallet) await adminDb().doc(`wallets/${u.uid}`).set({ uid: u.uid, solde: options.wallet, bloque: 0, dette: 0, pays: SN });
  const operateur = await compte("operateur", { pays: SN });
  const stock = await compte("gestionnaire_stock", { pays: SN });
  const livreur = await compte("livreur", { pays: SN });
  const { id, total } = await u.appeler("creerCommande", {
    cleIdempotence: cle(),
    lignes: [{ produitId: produit, quantite: 2 }],
    pays: SN,
    pourQui: "moi",
    mode: "livraison",
    clientNom: "Moussa Client",
    clientTelephone: "+221770000000",
    clientQuartier: "Plateau",
    ...(options.prixClient !== undefined ? { prixClient: options.prixClient } : {}),
    ...(options.clientPaieLivraison !== undefined ? { clientPaieLivraison: options.clientPaieLivraison } : {}),
  });
  await operateur.appeler("avancerCommande", { id, frais: 1500 });
  await stock.appeler("avancerCommande", { id });
  await stock.appeler("avancerCommande", { id });
  await operateur.appeler("assignerLivreur", { id, livreurId: livreur.uid });
  await livreur.appeler("demarrerLivraison", { id });
  const c = await commande(id);
  if (options.mobile) {
    const paiement = await livreur.appeler("demanderPaiementLivraison", { id, operateur: "Wave", numero: "77 123 45 67" });
    expect(paiement).toMatchObject({ statut: "en_attente", montant: c.total });
    await livreur.appeler("confirmerPaiementSimule", { id, succes: true });
  }
  await livreur.appeler("terminerLivraison", {
    id,
    montantEncaisse: options.mobile ? 0 : c.total,
    preuvePhoto: `deliveries/${id}/${livreur.uid}/photo.jpg`,
  });
  return { u, operateur, livreur, id, total: c.total as number, creeTotal: total as number };
}

describe("prix payé par le client", () => {
  it("le distributeur indique librement le prix du client ; trop bas sans wallet, refusé", async () => {
    const u = await compte("fbo", { grade: "Manager (M)", idDistributeur: "221000555666", pays: SN });
    const base = { cleIdempotence: cle(), lignes: [{ produitId: produit, quantite: 2 }], pays: SN, pourQui: "moi", mode: "livraison", clientNom: "X", clientTelephone: "+221770000001", clientQuartier: "Q" };
    const { id } = await u.appeler("creerCommande", { ...base, prixClient: 40000, clientPaieLivraison: false });
    expect(await commande(id)).toMatchObject({ reduction: 4000, prixClient: 40000, clientPaieLivraison: false, total: 40000 });
    // Au-dessus du prix public : permis, le surplus revient au distributeur.
    const { id: au } = await u.appeler("creerCommande", { ...base, cleIdempotence: cle(), prixClient: 47000, clientPaieLivraison: false });
    expect(await commande(au)).toMatchObject({ reduction: -3000, prixClient: 47000, total: 47000 });
    // Sous le prix de validation, sans wallet pour compléter : refusé.
    expect(await codeErreur(u.appeler("creerCommande", { ...base, cleIdempotence: cle(), prixClient: 22000 }))).toBe("functions/failed-precondition");
  });
});

describe("répartition automatique", () => {
  it("paiement mobile : chaque franc a sa destination, une seule fois", async () => {
    const { u, livreur, id, total } = await livrer({ mobile: true });
    expect(total).toBe(45500); // 44 000 + 1 500 de livraison payée par le client
    const r = (await commande(id)).repartition;
    expect(r).toMatchObject({ encaisse: 45500, reserveValidation: 22880, fraisLivraison: 1500, commissionLivreur: 1000, partLivraisonPlateforme: 500, commissionPlateforme: 0, benefice: 21120 });
    expect(r.reserveValidation + r.commissionLivreur + r.partLivraisonPlateforme + r.commissionPlateforme + r.benefice).toBe(r.encaisse);
    expect((await wallet(u.uid)).solde).toBe(21120);
    expect((await wallet(livreur.uid)).solde).toBe(1000);
    expect((await adminDb().doc(`deliveries/${id}`).get()).data()).toMatchObject({ encaissement: "mobile_money", montantEncaisse: 0 });
  });

  it("espèces : rien n'est versé avant que l'administration confirme la remise, puis une seule fois", async () => {
    const { u, operateur, livreur, id } = await livrer({ mobile: false, prixClient: 40000, clientPaieLivraison: false });
    expect((await wallet(u.uid)).solde ?? 0).toBe(0);
    expect(await codeErreur(livreur.appeler("confirmerRemiseEspeces", { id }))).toBe("functions/permission-denied");
    const autrePays = await compte("operateur", { pays: "Burkina Faso" });
    expect(await codeErreur(autrePays.appeler("confirmerRemiseEspeces", { id }))).toBe("functions/permission-denied");
    expect(await operateur.appeler("confirmerRemiseEspeces", { id })).toEqual({ statut: "ok" });
    expect(await operateur.appeler("confirmerRemiseEspeces", { id })).toEqual({ statut: "deja" });
    // 40 000 payés − 22 880 de validation − 1 500 de livraison non facturée au client.
    expect((await wallet(u.uid)).solde).toBe(15620);
    expect((await wallet(livreur.uid)).solde).toBe(1000);
  });

  it("prix trop bas : le complément est pris sur le wallet à la commande, l'excédent revient au bénéfice", async () => {
    const { u, operateur, id } = await livrer({ mobile: false, prixClient: 22880, clientPaieLivraison: false, wallet: 3000 });
    // Seuil estimé : 22 880 de validation + 2 000 de livraison au plus (fourchette du pays).
    expect((await commande(id)).complementWallet).toBe(2000);
    expect((await wallet(u.uid)).solde).toBe(1000);
    await operateur.appeler("confirmerRemiseEspeces", { id });
    // Encaissé 22 880 + 2 000 de complément ; livraison réelle 1 500 : 500 reviennent.
    expect((await commande(id)).repartition).toMatchObject({ encaisse: 24880, complementWallet: 2000, benefice: 500 });
    expect((await wallet(u.uid)).solde).toBe(1500);
  });

  it("une dette est réglée en priorité par le prochain dépôt", async () => {
    const u = await compte("fbo", { pays: SN });
    await adminDb().doc(`wallets/${u.uid}`).set({ uid: u.uid, solde: 0, bloque: 0, dette: 1500 });
    const { id: depot } = await u.appeler("demarrerDepot", { cleIdempotence: cle(), montant: 2000, operateur: "Wave", numero: "771234567" });
    await u.appeler("confirmerDepotSimule", { id: depot, succes: true });
    expect(await wallet(u.uid)).toMatchObject({ solde: 500, dette: 0 });
  });
});

describe("régularisation au siège", () => {
  it("rend le surplus de la facture et retient les prix réels pour la suite", async () => {
    const { u, operateur, id } = await livrer({ mobile: true });
    let lot: FirebaseFirestore.QueryDocumentSnapshot | undefined;
    for (let i = 0; i < 40 && !lot; i++) {
      lot = (await adminDb().collection("lotsValidation").where("fboId", "==", u.uid).get()).docs[0];
      if (!lot) await new Promise((r) => setTimeout(r, 250));
    }
    expect(lot).toBeDefined();
    const resultat = await operateur.appeler("validerLotSiege", {
      id: lot!.id, reference: "FACT-1", montantFacture: 22000, prixFactures: { [produit]: 11000 },
    });
    expect(resultat.regularisation).toMatchObject({ ecart: 880, reserveRetenue: 22880 });
    expect((await wallet(u.uid)).solde).toBe(21120 + 880);
    expect((await commande(id)).regularisation).toBeDefined();
    const appris = (await adminDb().doc(`prixValidation/${SN}__48__${produit}`).get()).data();
    expect(appris).toMatchObject({ prix: 11000 });

    // La commande suivante utilise le prix appris : réserve de 22 000, plus d'écart.
    const suivante = await livrer({ mobile: true });
    expect((await commande(suivante.id)).repartition.reserveValidation).toBe(22000);
  });
});

describe("sécurité des retraits", () => {
  it("un distributeur confirme chaque retrait avec son PIN ; plafond de 24 heures", async () => {
    const u = await compte("fbo", { pays: SN });
    const sa = await compte("super_admin");
    await sa.appeler("ajusterWallet", { uid: u.uid, montant: 700000, motif: "Test plafond" });
    await u.appeler("definirPin", { pin: "4827" });
    const base = { operateur: "Wave", numero: "771234567" };
    expect(await codeErreur(u.appeler("demanderRetrait", { ...base, cleIdempotence: cle(), montant: 10000 }))).toBe("functions/permission-denied");
    expect(await codeErreur(u.appeler("demanderRetrait", { ...base, cleIdempotence: cle(), montant: 10000, pin: "1111" }))).toBe("functions/permission-denied");
    const { id } = await u.appeler("demanderRetrait", { ...base, cleIdempotence: cle(), montant: 400000, pin: "4827" });
    expect((await adminDb().doc(`retraits/${id}`).get()).data()).toMatchObject({ nouveauNumero: true });
    expect(await codeErreur(u.appeler("demanderRetrait", { ...base, cleIdempotence: cle(), montant: 200000, pin: "4827" }))).toBe("functions/failed-precondition");
  });
});

describe("prix au-dessus du prix public, envoyé comme réduction négative par l'application", () => {
  it("le client paie plus, le supplément va au bénéfice", async () => {
    const u = await compte("fbo", { grade: "Manager (M)", idDistributeur: "221000777888", pays: SN });
    const { id } = await u.appeler("creerCommande", {
      cleIdempotence: cle(), lignes: [{ produitId: produit, quantite: 2 }], pays: SN, pourQui: "moi", mode: "livraison",
      clientNom: "X", clientTelephone: "+221770000002", clientQuartier: "Q", reduction: -2000,
    });
    expect(await commande(id)).toMatchObject({ reduction: -2000, prixClient: 46000, total: 46000 });
  });
});
