import { randomUUID } from "node:crypto";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { adminDb, cle, codeErreur, compte, fermerApplis, preparerAdmin } from "./outils.js";

/*
 * Paiements sur les numéros de dépôt de FBO+, déclarés puis vérifiés par l'administration.
 * Au Sénégal, avec ses propres données (aucun effet sur les autres fichiers de tests).
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
const livraison = async (id: string) => (await adminDb().doc(`deliveries/${id}`).get()).data()!;
const ref = () => `TX${randomUUID().slice(0, 8).toUpperCase()}`;

/** Commande d'un Manager pour 2 × 22 000, en livraison au Sénégal (frais 1 500 payés par le client). */
async function enLivraison() {
  const u = await compte("fbo", { grade: "Manager (M)", idDistributeur: `221${Date.now() % 1e9}`, pays: SN });
  const operateur = await compte("operateur", { pays: SN });
  const stock = await compte("gestionnaire_stock", { pays: SN });
  const livreur = await compte("livreur", { pays: SN });
  const admin = await compte("admin", { pays: SN });
  const { id } = await u.appeler("creerCommande", {
    cleIdempotence: cle(), lignes: [{ produitId: produit, quantite: 2 }], pays: SN, pourQui: "moi", mode: "livraison",
    clientNom: "Moussa", clientTelephone: "+221770000000", clientQuartier: "Plateau",
  });
  await operateur.appeler("avancerCommande", { id, frais: 1500 });
  await stock.appeler("avancerCommande", { id });
  await stock.appeler("avancerCommande", { id });
  await operateur.appeler("assignerLivreur", { id, livreurId: livreur.uid });
  await livreur.appeler("demarrerLivraison", { id });
  return { u, operateur, livreur, admin, id };
}

const terminer = (livreur: Awaited<ReturnType<typeof compte>>, id: string, montant: number) =>
  livreur.appeler("terminerLivraison", { id, montantEncaisse: montant, preuvePhoto: `deliveries/${id}/${livreur.uid}/photo.jpg` });

describe("recharge du wallet par un numéro de dépôt", () => {
  it("créditée seulement quand l'administration du pays confirme ; une référence ne sert qu'une fois", async () => {
    const u = await compte("fbo", { pays: SN });
    const admin = await compte("admin", { pays: SN });
    const reference = ref();
    const { id } = await u.appeler("declarerPaiement", { type: "recharge", montant: 5000, operateur: "Wave", reference: reference.toLowerCase(), cleIdempotence: cle() });
    expect((await wallet(u.uid)).solde ?? 0).toBe(0);
    // Même référence chez le même opérateur : refusée, même par quelqu'un d'autre.
    const autre = await compte("fbo", { pays: SN });
    expect(await codeErreur(autre.appeler("declarerPaiement", { type: "recharge", montant: 5000, operateur: "Wave", reference, cleIdempotence: cle() })))
      .toBe("functions/already-exists");

    // Ni le centre d'appel, ni une autre administration, ni le déclarant ne peuvent confirmer.
    expect(await codeErreur((await compte("operateur", { pays: SN })).appeler("deciderDeclaration", { id, accepter: true }))).toBe("functions/permission-denied");
    expect(await codeErreur((await compte("admin", { pays: "Burkina Faso" })).appeler("deciderDeclaration", { id, accepter: true }))).toBe("functions/permission-denied");
    expect(await codeErreur(u.appeler("deciderDeclaration", { id, accepter: true }))).toBe("functions/permission-denied");

    await admin.appeler("deciderDeclaration", { id, accepter: true });
    expect((await wallet(u.uid)).solde).toBe(5000);
    expect(await codeErreur(admin.appeler("deciderDeclaration", { id, accepter: true }))).toBe("functions/failed-precondition");
    expect((await wallet(u.uid)).solde).toBe(5000);
  });

  it("refusée avec un motif : rien n'est crédité et la référence peut être redéclarée", async () => {
    const u = await compte("livreur", { pays: SN });
    const admin = await compte("admin", { pays: SN });
    const reference = ref();
    const { id } = await u.appeler("declarerPaiement", { type: "recharge", montant: 3000, operateur: "Orange Money", reference, cleIdempotence: cle() });
    expect(await codeErreur(admin.appeler("deciderDeclaration", { id, accepter: false }))).toBe("functions/invalid-argument");
    await admin.appeler("deciderDeclaration", { id, accepter: false, motif: "Montant reçu : 2 000" });
    expect((await wallet(u.uid)).solde ?? 0).toBe(0);
    await u.appeler("declarerPaiement", { type: "recharge", montant: 2000, operateur: "Orange Money", reference, cleIdempotence: cle() });
  });

  it("vérifie l'opérateur du pays, le montant et le format de la référence", async () => {
    const u = await compte("fbo", { pays: SN });
    const base = { type: "recharge", montant: 5000, operateur: "Wave", reference: ref() };
    expect(await codeErreur(u.appeler("declarerPaiement", { ...base, operateur: "Moov Money", cleIdempotence: cle() }))).toBe("functions/invalid-argument");
    expect(await codeErreur(u.appeler("declarerPaiement", { ...base, montant: 100, cleIdempotence: cle() }))).toBe("functions/invalid-argument");
    expect(await codeErreur(u.appeler("declarerPaiement", { ...base, reference: "<x>", cleIdempotence: cle() }))).toBe("functions/invalid-argument");
    expect(await codeErreur((await compte("operateur", { pays: SN })).appeler("declarerPaiement", { ...base, cleIdempotence: cle() }))).toBe("functions/permission-denied");
  });
});

describe("paiement d'une commande à la livraison", () => {
  it("déclaré par le livreur : livraison terminée sans espèces, répartition à la confirmation", async () => {
    const { u, livreur, admin, id } = await enLivraison();
    const { id: declaration, montant } = await livreur.appeler("declarerPaiement", {
      type: "commande", id, operateur: "Wave", reference: ref(), montant: 1,
    });
    expect(montant).toBe(45500); // montant de la commande, pas celui envoyé
    expect((await commande(id)).paiement).toMatchObject({ statut: "a_verifier", montant: 45500 });
    expect(await codeErreur(livreur.appeler("declarerPaiement", { type: "commande", id, operateur: "Wave", reference: ref() }))).toBe("functions/failed-precondition");

    await terminer(livreur, id, 0);
    expect(await livraison(id)).toMatchObject({ statut: "livree", encaissement: "mobile_money", montantEncaisse: 0 });
    expect((await commande(id)).repartition).toBeUndefined();

    await admin.appeler("deciderDeclaration", { id: declaration, accepter: true });
    expect((await commande(id)).repartition).toMatchObject({ encaisse: 45500, benefice: 21120, commissionLivreur: 1000 });
    expect((await wallet(u.uid)).solde).toBe(21120);
    expect((await wallet(livreur.uid)).solde).toBe(1000);
  });

  it("paiement introuvable : l'argent reste à récupérer, comme des espèces", async () => {
    const { u, operateur, livreur, admin, id } = await enLivraison();
    const { id: declaration } = await livreur.appeler("declarerPaiement", { type: "commande", id, operateur: "Orange Money", reference: ref() });
    await terminer(livreur, id, 0);
    await admin.appeler("deciderDeclaration", { id: declaration, accepter: false, motif: "Aucun paiement reçu" });
    expect(await livraison(id)).toMatchObject({ encaissement: "especes", especesRemises: false, montantEncaisse: 45500 });
    expect((await wallet(u.uid)).solde ?? 0).toBe(0);
    await operateur.appeler("confirmerRemiseEspeces", { id });
    expect((await wallet(u.uid)).solde).toBe(21120);
  });

  it("seuls le livreur de la mission et le distributeur de la commande déclarent", async () => {
    const { id } = await enLivraison();
    const intrus = await compte("livreur", { pays: SN });
    const autreFbo = await compte("fbo", { pays: SN });
    for (const qui of [intrus, autreFbo]) {
      expect(await codeErreur(qui.appeler("declarerPaiement", { type: "commande", id, operateur: "Wave", reference: ref() }))).toBe("functions/permission-denied");
    }
  });
});
