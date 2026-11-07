import { randomUUID } from "node:crypto";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { adminDb, cle, codeErreur, compte, fermerApplis, preparerAdmin } from "./outils.js";

let produit: string;
let produit2: string;

const stock = async (id = produit) => (await adminDb().doc(`stock/principal_${id}`).get()).data()!;
const commande = async (id: string) => (await adminDb().doc(`orders/${id}`).get()).data()!;

beforeAll(async () => {
  preparerAdmin();
  await adminDb().doc("warehouses/principal").set({ nom: "Principal", pays: "Burkina Faso", actif: true });
  await adminDb().doc("warehouses/abidjan").set({ nom: "Abidjan", pays: "Côte d'Ivoire", actif: true });
});

afterAll(fermerApplis);

beforeEach(async () => {
  produit = randomUUID();
  produit2 = randomUUID();
  await adminDb().doc(`products/${produit}`).set({ nom: "Pulpe", reference: "15", prix: 22000, cc: 0.1, actif: true });
  await adminDb().doc(`products/${produit2}`).set({ nom: "Propolis", reference: "27", prix: 28500, cc: 0.13, actif: true });
  await adminDb().doc(`stock/principal_${produit}`).set({ disponible: 10, reserve: 0 });
  await adminDb().doc(`stock/principal_${produit2}`).set({ disponible: 5, reserve: 0 });
});

const fbo = () => compte("fbo", { grade: "Manager (M)", idDistributeur: "226000111222", prenom: "Awa", nom: "Test" });

const LIVRAISON = {
  pays: "Burkina Faso",
  pourQui: "moi",
  mode: "livraison",
  clientNom: "Fatou Client",
  clientTelephone: "+22670000030",
  clientQuartier: "Secteur 15",
};

const nouvelle = (u: Awaited<ReturnType<typeof fbo>>, extra: object = {}) =>
  u.appeler("creerCommande", {
    cleIdempotence: cle(),
    lignes: [{ produitId: produit, quantite: 2 }],
    ...LIVRAISON,
    ...extra,
  });

describe("creerCommande", () => {
  it("calcule le prix côté serveur et ignore celui envoyé par le client", async () => {
    const u = await fbo();
    const { id, total } = await u.appeler("creerCommande", {
      cleIdempotence: cle(),
      lignes: [{ produitId: produit, quantite: 2, prixUnitaire: 1 }],
      total: 1,
      ...LIVRAISON,
    });
    expect(total).toBe(44000);
    const c = await commande(id);
    expect(c.lignes[0].prixUnitaire).toBe(22000);
    expect(c.ccTotal).toBe(0.2);
    expect(c.statut).toBe("nouvelle");
    expect(c.beneficeFbo).toBe(21120);
  });

  it("réserve le stock et journalise", async () => {
    const u = await fbo();
    const { id } = await nouvelle(u);
    expect(await stock()).toMatchObject({ disponible: 8, reserve: 2 });
    const journal = await adminDb().collection("auditLogs").where("cibleId", "==", id).get();
    expect(journal.size).toBe(1);
    const mouvements = await adminDb().collection("stockMovements").where("commandeId", "==", id).get();
    expect(mouvements.docs[0].data().type).toBe("reservation");
  });

  it("refuse un stock insuffisant sans rien réserver", async () => {
    const u = await fbo();
    const erreur = await codeErreur(
      nouvelle(u, {
        lignes: [
          { produitId: produit, quantite: 1 },
          { produitId: produit2, quantite: 6 },
        ],
      }),
    );
    expect(erreur).toBe("functions/failed-precondition");
    expect((await stock()).disponible).toBe(10);
  });

  it("une nouvelle tentative avec la même clé ne crée pas de doublon", async () => {
    const u = await fbo();
    const donnees = { cleIdempotence: cle(), lignes: [{ produitId: produit, quantite: 1 }], ...LIVRAISON };
    const a = await u.appeler("creerCommande", donnees);
    const b = await u.appeler("creerCommande", donnees);
    expect(a.id).toBe(b.id);
    expect((await stock()).disponible).toBe(9);
  });

  it("deux commandes simultanées ne dépassent jamais le stock", async () => {
    const u = await fbo();
    const donnees = () => ({ cleIdempotence: cle(), lignes: [{ produitId: produit2, quantite: 3 }], ...LIVRAISON });
    const resultats = await Promise.all([
      codeErreur(u.appeler("creerCommande", donnees())),
      codeErreur(u.appeler("creerCommande", donnees())),
    ]);
    expect(resultats.filter((r) => r === "aucune erreur")).toHaveLength(1);
    expect(await stock(produit2)).toMatchObject({ disponible: 2, reserve: 3 });
  });

  it("plafonne la réduction à la marge du grade", async () => {
    const u = await compte("fbo", { grade: "Client Privilégié (CP)", idDistributeur: "226000333444" });
    expect(await codeErreur(nouvelle(u, { reduction: 2201 }))).toBe("functions/invalid-argument");
    const { total } = await nouvelle(u, { reduction: 2200 });
    expect(total).toBe(41800);
  });

  it("applique les frais de livraison des paramètres, pas ceux du client", async () => {
    await adminDb().doc("parametres/tarifs").set({ fraisLivraison: 1500 });
    const u = await fbo();
    const { id, total } = await nouvelle(u, { fraisLivraison: 0 });
    await adminDb().doc("parametres/tarifs").delete();
    expect(total).toBe(45500);
    expect((await commande(id)).fraisLivraison).toBe(1500);
  });

  it("chaque pays a ses propres frais de livraison et sa commission", async () => {
    await adminDb().doc("parametres/tarifs").set({ fraisLivraison: 1500, commissionLivreur: 400 });
    await adminDb().doc("tarifsPays/Côte d'Ivoire").set({ fraisLivraison: 2000, commissionLivreur: 700 });
    await adminDb().doc("stock/abidjan_" + produit).set({ disponible: 10, reserve: 0 });
    const u = await fbo();
    const { total: bf } = await nouvelle(u);
    const { id, total: ci } = await nouvelle(u, { pays: "Côte d'Ivoire" });
    expect(bf).toBe(45500); // tarifs communs, faute de tarifs propres au Burkina
    expect(ci).toBe(46000);

    const adminCI = await compte("admin", { pays: "Côte d'Ivoire" });
    const livreurCI = await compte("livreur", { pays: "Côte d'Ivoire" });
    await adminCI.appeler("avancerCommande", { id });
    await adminCI.appeler("avancerCommande", { id });
    await adminCI.appeler("avancerCommande", { id });
    await adminCI.appeler("assignerLivreur", { id, livreurId: livreurCI.uid });
    expect((await adminDb().doc(`deliveries/${id}`).get()).data()!.commission).toBe(700);

    await adminDb().doc("tarifsPays/Côte d'Ivoire").delete();
    await adminDb().doc("parametres/tarifs").delete();
  });

  it("refuse un produit inactif, le client d'un autre FBO et les autres rôles", async () => {
    await adminDb().doc(`products/${produit2}`).update({ actif: false });
    const u = await fbo();
    expect(
      await codeErreur(nouvelle(u, { lignes: [{ produitId: produit2, quantite: 1 }] })),
    ).toBe("functions/failed-precondition");

    await adminDb().doc("customers/autre").set({ ownerId: "quelqu_un", nom: "X" });
    expect(await codeErreur(nouvelle(u, { clientId: "autre" }))).toBe("functions/permission-denied");

    const livreur = await compte("livreur");
    expect(await codeErreur(nouvelle(livreur as never))).toBe("functions/permission-denied");
  });
});

describe("modifierCommande et annulerCommande", () => {
  it("ajuste le stock selon l'écart et recalcule le total", async () => {
    await adminDb().doc("parametres/tarifs").delete();
    const u = await fbo();
    const { id } = await nouvelle(u);
    await u.appeler("modifierCommande", { id, lignes: [{ produitId: produit, quantite: 5 }] });
    expect(await stock()).toMatchObject({ disponible: 5, reserve: 5 });
    expect((await commande(id)).total).toBe(110000);

    await u.appeler("modifierCommande", { id, lignes: [{ produitId: produit2, quantite: 1 }] });
    expect(await stock()).toMatchObject({ disponible: 10, reserve: 0 });
    expect(await stock(produit2)).toMatchObject({ disponible: 4, reserve: 1 });
  });

  it("un FBO ne touche pas à la commande d'un autre", async () => {
    const u = await fbo();
    const autre = await fbo();
    const { id } = await nouvelle(u);
    const lignes = [{ produitId: produit, quantite: 1 }];
    expect(await codeErreur(autre.appeler("modifierCommande", { id, lignes }))).toBe("functions/permission-denied");
    expect(await codeErreur(autre.appeler("annulerCommande", { id }))).toBe("functions/permission-denied");
  });

  it("un FBO ne modifie plus une commande confirmée, le centre d'appel si", async () => {
    const u = await fbo();
    const operateur = await compte("operateur");
    const { id } = await nouvelle(u);
    await operateur.appeler("avancerCommande", { id });
    const lignes = [{ produitId: produit, quantite: 1 }];
    expect(await codeErreur(u.appeler("modifierCommande", { id, lignes }))).toBe("functions/failed-precondition");
    await operateur.appeler("modifierCommande", { id, lignes });
    expect((await commande(id)).lignes[0].quantite).toBe(1);
  });

  it("annuler rend le stock", async () => {
    const u = await fbo();
    const { id } = await nouvelle(u);
    await u.appeler("annulerCommande", { id, motif: "Client absent" });
    expect(await stock()).toMatchObject({ disponible: 10, reserve: 0 });
    expect((await commande(id)).statut).toBe("annulee");
    expect(await codeErreur(u.appeler("annulerCommande", { id }))).toBe("functions/failed-precondition");
  });
});

describe("cycle complet jusqu'à la livraison", () => {
  async function jusquaPrete() {
    const u = await fbo();
    const operateur = await compte("operateur");
    const gestionnaire = await compte("gestionnaire_stock");
    const { id } = await nouvelle(u);
    await operateur.appeler("avancerCommande", { id });
    await gestionnaire.appeler("avancerCommande", { id });
    await gestionnaire.appeler("avancerCommande", { id });
    return { u, operateur, gestionnaire, id };
  }

  it("respecte les rôles à chaque étape", async () => {
    const u = await fbo();
    const gestionnaire = await compte("gestionnaire_stock");
    const operateur = await compte("operateur");
    const { id } = await nouvelle(u);
    expect(await codeErreur(u.appeler("avancerCommande", { id }))).toBe("functions/permission-denied");
    expect(await codeErreur(gestionnaire.appeler("avancerCommande", { id }))).toBe("functions/permission-denied");
    await operateur.appeler("avancerCommande", { id });
    expect(await codeErreur(operateur.appeler("avancerCommande", { id }))).toBe("functions/permission-denied");
  });

  it("va de la commande à la livraison, avec historique et sortie de stock", async () => {
    const { operateur, id } = await jusquaPrete();
    const livreur = await compte("livreur");

    await operateur.appeler("assignerLivreur", { id, livreurId: livreur.uid });
    expect((await commande(id)).statut).toBe("assignee");

    await livreur.appeler("demarrerLivraison", { id });
    expect((await commande(id)).statut).toBe("en_livraison");

    const dossier = `deliveries/${id}/${livreur.uid}`;
    await livreur.appeler("terminerLivraison", {
      id,
      montantEncaisse: 44000,
      preuvePhoto: `${dossier}/photo.jpg`,
      signature: `${dossier}/signature.png`,
    });

    const c = await commande(id);
    expect(c.statut).toBe("livree");
    expect(c.historiqueStatuts.map((h: { statut: string }) => h.statut)).toEqual([
      "nouvelle", "confirmee", "en_preparation", "prete", "assignee", "en_livraison", "livree",
    ]);
    expect(await stock()).toMatchObject({ disponible: 8, reserve: 0 });
  });

  it("refuse une mauvaise somme, l'absence de preuve et un autre livreur", async () => {
    const { operateur, id } = await jusquaPrete();
    const livreur = await compte("livreur");
    const intrus = await compte("livreur");
    await operateur.appeler("assignerLivreur", { id, livreurId: livreur.uid });

    expect(await codeErreur(intrus.appeler("demarrerLivraison", { id }))).toBe("functions/permission-denied");
    await livreur.appeler("demarrerLivraison", { id });

    const dossier = `deliveries/${id}/${livreur.uid}`;
    const preuves = { preuvePhoto: `${dossier}/p.jpg`, signature: `${dossier}/s.png` };
    expect(
      await codeErreur(livreur.appeler("terminerLivraison", { id, montantEncaisse: 1, ...preuves })),
    ).toBe("functions/invalid-argument");
    expect(
      await codeErreur(livreur.appeler("terminerLivraison", { id, montantEncaisse: 44000 })),
    ).toBe("functions/invalid-argument");
    expect((await commande(id)).statut).toBe("en_livraison");
  });

  it("un retour rend le stock", async () => {
    const { operateur, id } = await jusquaPrete();
    const livreur = await compte("livreur");
    await operateur.appeler("assignerLivreur", { id, livreurId: livreur.uid });
    await livreur.appeler("demarrerLivraison", { id });
    await livreur.appeler("signalerRetour", { id, motif: "Client absent" });
    expect((await commande(id)).statut).toBe("retournee");
    expect(await stock()).toMatchObject({ disponible: 10, reserve: 0 });
  });

  it("n'assigne pas un compte qui n'est pas livreur", async () => {
    const { operateur, u, id } = await jusquaPrete();
    expect(await codeErreur(operateur.appeler("assignerLivreur", { id, livreurId: u.uid }))).toBe(
      "functions/failed-precondition",
    );
  });
});

describe("pour qui, livraison ou validation", () => {
  it("une commande pour soi est validée sur son propre ID Forever", async () => {
    const u = await fbo();
    const { id } = await nouvelle(u);
    expect((await commande(id)).beneficiaire).toEqual({
      type: "moi",
      nom: "Test",
      prenom: "Awa",
      idForever: "226000111222",
    });
  });

  it("refuse une commande pour soi sans ID Forever dans le profil", async () => {
    const u = await compte("fbo", { grade: "Manager (M)" });
    expect(await codeErreur(nouvelle(u))).toBe("functions/failed-precondition");
  });

  it("une commande de filleul exige son nom, son prénom et son ID", async () => {
    const u = await fbo();
    const filleul = { pourQui: "filleul", filleulNom: "Ouédraogo", filleulPrenom: "Paul", filleulId: "226-000-555-666" };
    expect(await codeErreur(nouvelle(u, { ...filleul, filleulPrenom: "" }))).toBe("functions/invalid-argument");
    expect(await codeErreur(nouvelle(u, { ...filleul, filleulId: "12" }))).toBe("functions/invalid-argument");
    const { id } = await nouvelle(u, filleul);
    expect((await commande(id)).beneficiaire).toMatchObject({ type: "filleul", idForever: "226-000-555-666" });
  });

  it("une livraison exige le nom, un téléphone valide et le quartier du client", async () => {
    const u = await fbo();
    expect(await codeErreur(nouvelle(u, { clientNom: "" }))).toBe("functions/invalid-argument");
    expect(await codeErreur(nouvelle(u, { clientTelephone: "12" }))).toBe("functions/invalid-argument");
    expect(await codeErreur(nouvelle(u, { clientTelephone2: "abc" }))).toBe("functions/invalid-argument");
    expect(await codeErreur(nouvelle(u, { clientQuartier: "" }))).toBe("functions/invalid-argument");
    const { id } = await nouvelle(u, { clientTelephone2: "+22676000000", adresse: "Près du marché" });
    const c = await commande(id);
    expect(c.client).toMatchObject({ telephone2: "+22676000000", quartier: "Secteur 15" });
    expect(c.adresse).toBe("Secteur 15, Près du marché");
  });

  it("une validation ne demande aucun client, ne paie pas de livraison et ne se livre pas", async () => {
    await adminDb().doc("parametres/tarifs").set({ fraisLivraison: 1500 });
    const u = await fbo();
    const operateur = await compte("operateur");
    const gestionnaire = await compte("gestionnaire_stock");
    const livreur = await compte("livreur");
    const { id, total } = await u.appeler("creerCommande", {
      cleIdempotence: cle(),
      lignes: [{ produitId: produit, quantite: 2 }],
      pays: "Burkina Faso",
      pourQui: "moi",
      mode: "validation",
    });
    await adminDb().doc("parametres/tarifs").delete();
    expect(total).toBe(44000);
    expect((await commande(id)).client).toBeNull();

    await operateur.appeler("avancerCommande", { id });
    await gestionnaire.appeler("avancerCommande", { id });
    await gestionnaire.appeler("avancerCommande", { id });
    expect(await codeErreur(operateur.appeler("assignerLivreur", { id, livreurId: livreur.uid }))).toBe(
      "functions/failed-precondition",
    );
    expect(await codeErreur(gestionnaire.appeler("validerSurId", { id }))).toBe("functions/permission-denied");

    await operateur.appeler("validerSurId", { id });
    expect((await commande(id)).statut).toBe("validee");
    expect(await stock()).toMatchObject({ disponible: 8, reserve: 0 });
  });

  it("une commande à livrer ne se valide pas sans livraison", async () => {
    const u = await fbo();
    const operateur = await compte("operateur");
    const gestionnaire = await compte("gestionnaire_stock");
    const { id } = await nouvelle(u);
    await operateur.appeler("avancerCommande", { id });
    await gestionnaire.appeler("avancerCommande", { id });
    await gestionnaire.appeler("avancerCommande", { id });
    expect(await codeErreur(operateur.appeler("validerSurId", { id }))).toBe("functions/failed-precondition");
  });

  it("refuse un mode ou un bénéficiaire absent", async () => {
    const u = await fbo();
    expect(await codeErreur(nouvelle(u, { mode: "" }))).toBe("functions/invalid-argument");
    expect(await codeErreur(nouvelle(u, { pourQui: "" }))).toBe("functions/invalid-argument");
  });
});

describe("expédition vers une autre ville", () => {
  const EXPEDITION = {
    mode: "expedition",
    villeExpeditionId: "bobo",
    destinataireNom: "Issa Destinataire",
    destinataireTelephone: "+22670000040",
  };

  beforeAll(async () => {
    await adminDb().doc("villesExpedition/bobo").set({ pays: "Burkina Faso", ville: "Bobo-Dioulasso", frais: 2500, actif: true });
    await adminDb().doc("villesExpedition/bouake").set({ pays: "Côte d'Ivoire", ville: "Bouaké", frais: 3000, actif: true });
    await adminDb().doc("villesExpedition/fermee").set({ pays: "Burkina Faso", ville: "Gaoua", frais: 3000, actif: false });
  });

  it("applique les frais de la ville et garde le destinataire et la compagnie", async () => {
    const u = await fbo();
    const { id, total } = await nouvelle(u, { ...EXPEDITION, compagnie: "STAF", adresse: "Gare STAF" });
    expect(total).toBe(46500);
    const c = await commande(id);
    expect(c.expedition).toMatchObject({ ville: "Bobo-Dioulasso", compagnie: "STAF", frais: 2500 });
    expect(c.client).toMatchObject({ nom: "Issa Destinataire", telephone: "+22670000040" });
    expect(c.adresse).toBe("Bobo-Dioulasso, Gare STAF");
  });

  it("exige une ville de la liste du pays, le nom et le téléphone du destinataire", async () => {
    const u = await fbo();
    expect(await codeErreur(nouvelle(u, { ...EXPEDITION, villeExpeditionId: "bouake" }))).toBe("functions/invalid-argument");
    expect(await codeErreur(nouvelle(u, { ...EXPEDITION, villeExpeditionId: "fermee" }))).toBe("functions/invalid-argument");
    expect(await codeErreur(nouvelle(u, { ...EXPEDITION, destinataireNom: "" }))).toBe("functions/invalid-argument");
    expect(await codeErreur(nouvelle(u, { ...EXPEDITION, destinataireTelephone: "12" }))).toBe("functions/invalid-argument");
  });

  it("sans compagnie, celui qui expédie la choisit ; le stock sort à l'expédition", async () => {
    const u = await fbo();
    const operateur = await compte("operateur");
    const gestionnaire = await compte("gestionnaire_stock");
    const livreur = await compte("livreur");
    const { id } = await nouvelle(u, EXPEDITION);
    expect((await commande(id)).expedition.compagnie).toBe("");

    await operateur.appeler("fixerFraisExpedition", { id, frais: 4000 });
    expect(await commande(id)).toMatchObject({ fraisLivraison: 4000, total: 48000, expedition: { frais: 4000 } });

    await operateur.appeler("avancerCommande", { id });
    await gestionnaire.appeler("avancerCommande", { id });
    await gestionnaire.appeler("avancerCommande", { id });
    expect(await codeErreur(operateur.appeler("fixerFraisExpedition", { id, frais: 0 }))).toBe("functions/failed-precondition");
    expect(await codeErreur(operateur.appeler("assignerLivreur", { id, livreurId: livreur.uid }))).toBe(
      "functions/failed-precondition",
    );
    expect(await codeErreur(operateur.appeler("expedierCommande", { id }))).toBe("functions/invalid-argument");
    expect(await codeErreur(gestionnaire.appeler("expedierCommande", { id, compagnie: "TSR" }))).toBe(
      "functions/permission-denied",
    );

    await operateur.appeler("expedierCommande", { id, compagnie: "TSR", numeroColis: "B-1234" });
    expect(await commande(id)).toMatchObject({
      statut: "expediee",
      expedition: { compagnie: "TSR", numeroColis: "B-1234" },
    });
    expect(await stock()).toMatchObject({ disponible: 8, reserve: 0 });
  });

  it("l'administration d'un autre pays ne peut pas expédier", async () => {
    const u = await fbo();
    const { id } = await nouvelle(u, { ...EXPEDITION, compagnie: "STAF" });
    await adminDb().doc(`orders/${id}`).update({ statut: "prete" });
    const adminCI = await compte("admin", { pays: "Côte d'Ivoire" });
    expect(await codeErreur(adminCI.appeler("expedierCommande", { id }))).toBe("functions/permission-denied");
  });
});

describe("grade et ID Forever tenus par l'administration", () => {
  it("la remise suit le grade validé, pas le grade demandé", async () => {
    const u = await compte("fbo", { gradeDemande: "Manager (M)", idDistributeur: "226000111222", prenom: "Ali", nom: "Test" });
    const admin = await compte("admin");
    const { id: avant } = await nouvelle(u);
    expect((await commande(avant)).beneficeFbo).toBe(2200); // Client Privilégié : 5 %

    await admin.appeler("definirGrade", { uid: u.uid, grade: "Manager (M)" });
    const profil = (await adminDb().doc(`users/${u.uid}`).get()).data()!;
    expect(profil.grade).toBe("Manager (M)");
    expect(profil.gradeDemande).toBeUndefined();
    const { id: apres } = await nouvelle(u);
    expect((await commande(apres)).beneficeFbo).toBe(21120); // Manager : 48 %

    const avis = await adminDb().collection("notifications").where("userId", "==", u.uid).get();
    expect(avis.docs.map((n) => n.data().corps).join()).toContain("Manager (M)");
  });

  it("une demande refusée est effacée sans changer le grade", async () => {
    const u = await compte("fbo", { grade: "Animateur (A)", gradeDemande: "Manager (M)" });
    const admin = await compte("admin");
    await admin.appeler("definirGrade", { uid: u.uid, refuser: true });
    const profil = (await adminDb().doc(`users/${u.uid}`).get()).data()!;
    expect(profil.grade).toBe("Animateur (A)");
    expect(profil.gradeDemande).toBeUndefined();
    expect(await codeErreur(admin.appeler("definirGrade", { uid: u.uid, refuser: true }))).toBe(
      "functions/failed-precondition",
    );
  });

  it("seule l'administration du pays du distributeur décide", async () => {
    const u = await compte("fbo", { gradeDemande: "Manager (M)" });
    const operateur = await compte("operateur");
    const adminCI = await compte("admin", { pays: "Côte d'Ivoire" });
    const admin = await compte("admin");
    expect(await codeErreur(operateur.appeler("definirGrade", { uid: u.uid, grade: "Manager (M)" }))).toBe(
      "functions/permission-denied",
    );
    expect(await codeErreur(adminCI.appeler("definirGrade", { uid: u.uid, grade: "Manager (M)" }))).toBe(
      "functions/permission-denied",
    );
    expect(await codeErreur(admin.appeler("definirGrade", { uid: u.uid, grade: "Roi" }))).toBe(
      "functions/invalid-argument",
    );
    expect(await codeErreur(admin.appeler("definirGrade", { uid: admin.uid, grade: "Manager (M)" }))).toBe(
      "functions/not-found",
    );
  });

  it("l'administration corrige un ID Forever erroné", async () => {
    const u = await compte("fbo", { idDistributeur: "226000111222" });
    const admin = await compte("admin");
    expect(await codeErreur(admin.appeler("corrigerIdForever", { uid: u.uid, idDistributeur: "12" }))).toBe(
      "functions/invalid-argument",
    );
    await admin.appeler("corrigerIdForever", { uid: u.uid, idDistributeur: "226-000-999-888" });
    expect((await adminDb().doc(`users/${u.uid}`).get()).data()!.idDistributeur).toBe("226-000-999-888");
    const journal = await adminDb().collection("auditLogs").where("cibleId", "==", u.uid).get();
    expect(journal.docs.some((j) => j.data().action === "id_forever_corrige")).toBe(true);
  });
});

describe("administration par pays", () => {
  it("la commande se sert dans l'entrepôt de son pays", async () => {
    await adminDb().doc(`stock/abidjan_${produit}`).set({ disponible: 4, reserve: 0 });
    const u = await fbo();
    const { id } = await nouvelle(u, { pays: "Côte d'Ivoire", clientTelephone: "+2250700000000" });
    expect(await commande(id)).toMatchObject({ pays: "Côte d'Ivoire", entrepotId: "abidjan" });
    expect((await adminDb().doc(`stock/abidjan_${produit}`).get()).data()).toMatchObject({ disponible: 2, reserve: 2 });
    expect((await stock()).disponible).toBe(10);
  });

  it("refuse un pays sans entrepôt ou hors de la liste", async () => {
    const u = await fbo();
    expect(await codeErreur(nouvelle(u, { pays: "Sénégal" }))).toBe("functions/failed-precondition");
    expect(await codeErreur(nouvelle(u, { pays: "France" }))).toBe("functions/invalid-argument");
  });

  it("l'administration d'un pays ne traite pas les commandes d'un autre", async () => {
    await adminDb().doc(`stock/abidjan_${produit}`).set({ disponible: 4, reserve: 0 });
    const u = await fbo();
    const operateurBf = await compte("operateur");
    const operateurCi = await compte("operateur", { pays: "Côte d'Ivoire" });
    const superAdmin = await compte("super_admin");
    const { id } = await nouvelle(u, { pays: "Côte d'Ivoire", clientTelephone: "+2250700000000" });

    expect(await codeErreur(operateurBf.appeler("avancerCommande", { id }))).toBe("functions/permission-denied");
    expect(await codeErreur(operateurBf.appeler("annulerCommande", { id }))).toBe("functions/permission-denied");
    await operateurCi.appeler("avancerCommande", { id });
    expect((await commande(id)).statut).toBe("confirmee");
    await superAdmin.appeler("annulerCommande", { id, motif: "Test" });
    expect((await commande(id)).statut).toBe("annulee");
  });

  it("n'assigne qu'un livreur du pays de la commande", async () => {
    const u = await fbo();
    const operateur = await compte("operateur");
    const gestionnaire = await compte("gestionnaire_stock");
    const livreurCi = await compte("livreur", { pays: "Côte d'Ivoire" });
    const livreurBf = await compte("livreur");
    const { id } = await nouvelle(u);
    await operateur.appeler("avancerCommande", { id });
    await gestionnaire.appeler("avancerCommande", { id });
    await gestionnaire.appeler("avancerCommande", { id });
    expect(await codeErreur(operateur.appeler("assignerLivreur", { id, livreurId: livreurCi.uid }))).toBe(
      "functions/failed-precondition",
    );
    await operateur.appeler("assignerLivreur", { id, livreurId: livreurBf.uid });
    expect((await adminDb().doc(`deliveries/${id}`).get()).data()?.pays).toBe("Burkina Faso");
  });

  it("un admin crée des comptes dans son pays ; seul le super admin choisit le pays", async () => {
    const adminCi = await compte("admin", { pays: "Côte d'Ivoire" });
    const superAdmin = await compte("super_admin");
    const { uid } = await adminCi.appeler("creerCompteInterne", {
      telephone: "+2250700001234",
      nom: "Koffi",
      role: "livreur",
      pays: "Burkina Faso",
    });
    expect((await adminDb().doc(`users/${uid}`).get()).data()?.pays).toBe("Côte d'Ivoire");

    expect(await codeErreur(adminCi.appeler("definirPays", { uid, pays: "Sénégal" }))).toBe(
      "functions/permission-denied",
    );
    await superAdmin.appeler("definirPays", { uid, pays: "Sénégal" });
    expect((await adminDb().doc(`users/${uid}`).get()).data()?.pays).toBe("Sénégal");
  });

  it("un admin ne suspend pas un compte d'un autre pays", async () => {
    const adminBf = await compte("admin");
    const livreurCi = await compte("livreur", { pays: "Côte d'Ivoire" });
    expect(await codeErreur(adminBf.appeler("definirStatut", { uid: livreurCi.uid, statut: "suspendu" }))).toBe(
      "functions/permission-denied",
    );
  });
});

describe("notifications", () => {
  async function attendreNotification(userId: string, commandeId: string, nombre: number) {
    for (let i = 0; i < 40; i++) {
      const r = await adminDb()
        .collection("notifications")
        .where("userId", "==", userId)
        .where("commandeId", "==", commandeId)
        .get();
      if (r.size >= nombre) return r.docs.map((d) => d.data());
      await new Promise((ok) => setTimeout(ok, 250));
    }
    return [];
  }

  it("prévient le FBO à chaque étape et le livreur de sa mission, sans doublon", async () => {
    const u = await fbo();
    const operateur = await compte("operateur");
    const gestionnaire = await compte("gestionnaire_stock");
    const livreur = await compte("livreur");
    const { id } = await nouvelle(u, { clientQuartier: "Secteur 15" });

    await operateur.appeler("avancerCommande", { id });
    await gestionnaire.appeler("avancerCommande", { id });
    await gestionnaire.appeler("avancerCommande", { id });
    await operateur.appeler("assignerLivreur", { id, livreurId: livreur.uid });

    const pourFbo = await attendreNotification(u.uid, id, 4);
    expect(pourFbo.map((n) => n.corps).sort()).toEqual([
      "Un livreur a été assigné à votre commande. (Fatou Client)",
      "Votre commande est confirmée. (Fatou Client)",
      "Votre commande est en préparation. (Fatou Client)",
      "Votre commande est prête. (Fatou Client)",
    ]);
    const pourLivreur = await attendreNotification(livreur.uid, id, 1);
    expect(pourLivreur[0]).toMatchObject({ titre: "Nouvelle mission", lu: false });
    expect(pourLivreur[0].corps).toContain("Secteur 15");
  });
});

describe("definirStatut", () => {
  it("un admin suspend un FBO, pas un autre admin ni lui-même", async () => {
    const admin = await compte("admin");
    const f = await fbo();
    const autreAdmin = await compte("admin");
    await admin.appeler("definirStatut", { uid: f.uid, statut: "suspendu" });
    expect((await adminDb().doc(`users/${f.uid}`).get()).data()?.statut).toBe("suspendu");
    expect(await codeErreur(admin.appeler("definirStatut", { uid: autreAdmin.uid, statut: "suspendu" }))).toBe(
      "functions/permission-denied",
    );
    expect(await codeErreur(admin.appeler("definirStatut", { uid: admin.uid, statut: "suspendu" }))).toBe(
      "functions/permission-denied",
    );
  });

  it("un opérateur ne suspend personne", async () => {
    const operateur = await compte("operateur");
    const f = await fbo();
    expect(await codeErreur(operateur.appeler("definirStatut", { uid: f.uid, statut: "suspendu" }))).toBe(
      "functions/permission-denied",
    );
  });
});

describe("ajusterStock et importerVentes", () => {
  it("enregistre une entrée et un inventaire, réservés au personnel de stock", async () => {
    const g = await compte("gestionnaire_stock");
    const f = await fbo();
    const entree = { produitId: produit, type: "entree", quantite: 5 };
    expect(await codeErreur(f.appeler("ajusterStock", entree))).toBe("functions/permission-denied");
    const gestionnaireCi = await compte("gestionnaire_stock", { pays: "Côte d'Ivoire" });
    expect(await codeErreur(gestionnaireCi.appeler("ajusterStock", entree))).toBe("functions/permission-denied");
    await g.appeler("ajusterStock", entree);
    expect((await stock()).disponible).toBe(15);
    await g.appeler("ajusterStock", { produitId: produit, type: "inventaire", quantite: 7 });
    expect((await stock()).disponible).toBe(7);
  });

  it("importe l'historique local sans doublon et sans toucher au stock", async () => {
    const u = await fbo();
    const ventes = [
      {
        id: "1793926000000",
        titre: "Vente",
        dateMillis: 1793926000000,
        reductionPourcentage: 35,
        lignes: [{ produitRef: "15", produitNom: "Pulpe", quantite: 2, prixUnitaire: 22000, ccUnitaire: 0.1 }],
      },
    ];
    expect((await u.appeler("importerVentes", { ventes })).importees).toBe(1);
    expect((await u.appeler("importerVentes", { ventes })).importees).toBe(0);
    const c = await commande(`import_${u.uid}_1793926000000`);
    expect(c).toMatchObject({ total: 44000, statut: "livree", beneficeFbo: 15400, source: "import_local" });
    expect((await stock()).disponible).toBe(10);
  });
});
