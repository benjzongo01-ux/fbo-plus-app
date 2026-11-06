import { randomUUID } from "node:crypto";
import { afterAll, beforeAll, beforeEach, describe, expect, it } from "vitest";
import { adminDb, cle, codeErreur, compte, fermerApplis, preparerAdmin } from "./outils.js";

let produit: string;
let produit2: string;

const stock = async (id = produit) => (await adminDb().doc(`stock/principal_${id}`).get()).data()!;
const commande = async (id: string) => (await adminDb().doc(`orders/${id}`).get()).data()!;

beforeAll(async () => {
  preparerAdmin();
  await adminDb().doc("warehouses/principal").set({ nom: "Principal" });
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

const fbo = () => compte("fbo", { grade: "Manager (M)" });
const nouvelle = (u: Awaited<ReturnType<typeof fbo>>, extra: object = {}) =>
  u.appeler("creerCommande", {
    cleIdempotence: cle(),
    lignes: [{ produitId: produit, quantite: 2 }],
    ...extra,
  });

describe("creerCommande", () => {
  it("calcule le prix côté serveur et ignore celui envoyé par le client", async () => {
    const u = await fbo();
    const { id, total } = await u.appeler("creerCommande", {
      cleIdempotence: cle(),
      lignes: [{ produitId: produit, quantite: 2, prixUnitaire: 1 }],
      total: 1,
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
    const donnees = { cleIdempotence: cle(), lignes: [{ produitId: produit, quantite: 1 }] };
    const a = await u.appeler("creerCommande", donnees);
    const b = await u.appeler("creerCommande", donnees);
    expect(a.id).toBe(b.id);
    expect((await stock()).disponible).toBe(9);
  });

  it("deux commandes simultanées ne dépassent jamais le stock", async () => {
    const u = await fbo();
    const donnees = () => ({ cleIdempotence: cle(), lignes: [{ produitId: produit2, quantite: 3 }] });
    const resultats = await Promise.all([
      codeErreur(u.appeler("creerCommande", donnees())),
      codeErreur(u.appeler("creerCommande", donnees())),
    ]);
    expect(resultats.filter((r) => r === "aucune erreur")).toHaveLength(1);
    expect(await stock(produit2)).toMatchObject({ disponible: 2, reserve: 3 });
  });

  it("plafonne la réduction à la marge du grade", async () => {
    const u = await compte("fbo", { grade: "Client Privilégié (CP)" });
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
