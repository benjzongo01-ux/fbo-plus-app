import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from "firebase/firestore";
import { getBytes, ref, uploadBytes } from "firebase/storage";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

let env: RulesTestEnvironment;

// Le personnel travaille au Burkina Faso sauf indication contraire.
const db = (uid: string, role?: string, pays = "Burkina Faso") =>
  env.authenticatedContext(uid, role ? { role, pays } : {}).firestore();
const stockage = (uid: string, role?: string) =>
  env.authenticatedContext(uid, role ? { role, pays: "Burkina Faso" } : {}).storage();

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-fbo-plus",
    firestore: { rules: readFileSync(resolve(__dirname, "../firestore.rules"), "utf8") },
    storage: { rules: readFileSync(resolve(__dirname, "../storage.rules"), "utf8") },
  });
});

afterAll(() => env.cleanup());

beforeEach(async () => {
  await env.clearFirestore();
  await env.clearStorage();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const d = ctx.firestore();
    await setDoc(doc(d, "users/fbo1"), { nom: "Awa", role: "fbo", statut: "actif", pays: "Burkina Faso" });
    await setDoc(doc(d, "products/p1"), { nom: "Aloe Vera", prix: 15000, actif: true });
    await setDoc(doc(d, "stock/w1_p1"), { disponible: 10, pays: "Burkina Faso" });
    await setDoc(doc(d, "customers/c1"), { ownerId: "fbo1", nom: "Client A" });
    await setDoc(doc(d, "orders/o1"), { fboId: "fbo1", livreurId: "liv1", total: 15000, pays: "Burkina Faso" });
    await setDoc(doc(d, "orders/oci"), { fboId: "fbo2", total: 9000, pays: "Côte d'Ivoire" });
    await setDoc(doc(d, "deliveries/d1"), { livreurId: "liv1", montantEncaisse: 0 });
    await setDoc(doc(d, "notifications/n1"), { userId: "fbo1", lu: false, titre: "Bonjour" });
    await setDoc(doc(d, "auditLogs/a1"), { action: "test" });
    await setDoc(doc(d, "userSecrets/fbo1"), { hash: "secret" });
  });
});

describe("refus par défaut", () => {
  it("un visiteur ne lit que le catalogue actif et les boutiques", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "products/p2"), { nom: "Retiré", actif: false });
      await setDoc(doc(ctx.firestore(), "boutiques/fbo1"), { nom: "Awa" });
    });
    const d = env.unauthenticatedContext().firestore();
    await assertSucceeds(getDoc(doc(d, "products/p1")));
    await assertSucceeds(getDoc(doc(d, "boutiques/fbo1")));
    await assertFails(getDoc(doc(d, "products/p2")));
    await assertFails(getDoc(doc(d, "orders/o1")));
    await assertFails(getDoc(doc(d, "users/fbo1")));
    await assertFails(getDoc(doc(d, "customers/c1")));
  });

  it("une boutique ne s'écrit que par son propriétaire", async () => {
    await assertSucceeds(setDoc(doc(db("fbo1", "fbo"), "boutiques/fbo1"), { nom: "Awa" }));
    await assertFails(setDoc(doc(db("fbo2", "fbo"), "boutiques/fbo1"), { nom: "Faux" }));
  });

  it("une collection inconnue est refusée, même pour un super_admin", async () => {
    await assertFails(setDoc(doc(db("sa", "super_admin"), "inconnue/x"), { a: 1 }));
  });


  it("personne ne lit les empreintes de PIN", async () => {
    await assertFails(getDoc(doc(db("fbo1", "fbo"), "userSecrets/fbo1")));
    await assertFails(getDoc(doc(db("sa", "super_admin"), "userSecrets/fbo1")));
  });
});

describe("users", () => {
  it("un utilisateur lit son profil mais pas celui d'un autre", async () => {
    await assertSucceeds(getDoc(doc(db("fbo1", "fbo"), "users/fbo1")));
    await assertFails(getDoc(doc(db("fbo2", "fbo"), "users/fbo1")));
  });

  it("un utilisateur modifie son nom", async () => {
    await assertSucceeds(updateDoc(doc(db("fbo1", "fbo"), "users/fbo1"), { nom: "Awa T." }));
  });

  it("un utilisateur ne peut pas changer son rôle ni son statut", async () => {
    const d = db("fbo1", "fbo");
    await assertFails(updateDoc(doc(d, "users/fbo1"), { role: "super_admin" }));
    await assertFails(updateDoc(doc(d, "users/fbo1"), { statut: "actif", nom: "X", role: "admin" }));
    await assertFails(updateDoc(doc(d, "users/fbo1"), { statut: "suspendu" }));
  });

  it("un distributeur voit sa réserve et ses lots de validation, pas ceux d'un autre", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), "reservesValidation/fbo1_226"), { fboId: "fbo1", pays: "Burkina Faso" });
      await setDoc(doc(ctx.firestore(), "lotsValidation/l1"), { fboId: "fbo1", pays: "Burkina Faso" });
    });
    await assertSucceeds(getDoc(doc(db("fbo1", "fbo"), "reservesValidation/fbo1_226")));
    await assertSucceeds(getDoc(doc(db("fbo1", "fbo"), "lotsValidation/l1")));
    await assertFails(getDoc(doc(db("fbo2", "fbo"), "lotsValidation/l1")));
    await assertFails(getDoc(doc(db("op-ci", "operateur", "Côte d'Ivoire"), "lotsValidation/l1")));
    await assertSucceeds(getDoc(doc(db("op-bf", "operateur"), "lotsValidation/l1")));
    await assertFails(setDoc(doc(db("fbo1", "fbo"), "lotsValidation/l2"), { fboId: "fbo1", statut: "valide" }));
  });

  it("les invitations du personnel ne sont visibles que du super administrateur", async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), "invitations/issa@gmail.com"), { role: "admin", pays: "Burkina Faso" }),
    );
    await assertFails(getDoc(doc(db("a", "admin"), "invitations/issa@gmail.com")));
    await assertFails(setDoc(doc(db("sa", "super_admin", ""), "invitations/moi@gmail.com"), { role: "super_admin" }));
    await assertSucceeds(getDoc(doc(db("sa", "super_admin", ""), "invitations/issa@gmail.com")));
    await assertSucceeds(deleteDoc(doc(db("sa", "super_admin", ""), "invitations/issa@gmail.com")));
  });

  it("personne ne lit ni ne remet à zéro ses compteurs de requêtes", async () => {
    const d = db("fbo1", "fbo");
    await assertFails(getDoc(doc(d, "limitesDebit/fbo1")));
    await assertFails(setDoc(doc(d, "limitesDebit/fbo1"), {}));
    await assertFails(getDoc(doc(db("sa", "super_admin", ""), "limitesDebit/fbo1")));
  });

  it("le distributeur demande un grade mais ne se l'attribue pas", async () => {
    const d = db("fbo1", "fbo");
    await assertFails(updateDoc(doc(d, "users/fbo1"), { grade: "Manager (M)" }));
    await assertSucceeds(updateDoc(doc(d, "users/fbo1"), { gradeDemande: "Manager (M)" }));
  });

  it("l'ID Forever se renseigne une seule fois", async () => {
    const d = db("fbo1", "fbo");
    await assertSucceeds(updateDoc(doc(d, "users/fbo1"), { idDistributeur: "226000", ville: "Ouaga" }));
    await assertFails(updateDoc(doc(d, "users/fbo1"), { idDistributeur: "999" }));
    await assertSucceeds(updateDoc(doc(d, "users/fbo1"), { ville: "Bobo" }));
  });

  it("un utilisateur ne peut pas créer ni supprimer de profil", async () => {
    await assertFails(setDoc(doc(db("neuf", "fbo"), "users/neuf"), { role: "admin" }));
    await assertFails(deleteDoc(doc(db("fbo1", "fbo"), "users/fbo1")));
  });
});

describe("administration par pays", () => {
  it("le personnel ne lit que les commandes, stocks et comptes de son pays", async () => {
    const ci = db("op-ci", "operateur", "Côte d'Ivoire");
    await assertSucceeds(getDoc(doc(ci, "orders/oci")));
    await assertFails(getDoc(doc(ci, "orders/o1")));
    await assertFails(getDoc(doc(ci, "stock/w1_p1")));
    await assertFails(getDoc(doc(ci, "users/fbo1")));
    await assertFails(getDoc(doc(db("op-bf", "operateur"), "orders/oci")));
  });

  it("le super administrateur voit tous les pays", async () => {
    const sa = db("sa", "super_admin", "");
    await assertSucceeds(getDoc(doc(sa, "orders/oci")));
    await assertSucceeds(getDoc(doc(sa, "orders/o1")));
  });

  it("seul le super administrateur règle entrepôts, tarifs, villes, centres et paramètres", async () => {
    const sa = db("sa", "super_admin", "");
    const reglages: [string, Record<string, unknown>][] = [
      ["warehouses/w2", { nom: "Bobo", pays: "Burkina Faso" }],
      ["tarifsPays/Burkina Faso", { fraisLivraison: 1000, commissionLivreur: 500 }],
      ["villesExpedition/v1", { pays: "Burkina Faso", ville: "Bobo-Dioulasso", frais: 2500, actif: true }],
      ["centres/c1", { nom: "Ouagadougou", actif: true }],
      ["parametres/tarifs", { fraisLivraison: 1000 }],
    ];
    for (const [chemin, donnees] of reglages) {
      await assertFails(setDoc(doc(db("a", "admin"), chemin), donnees));
      await assertFails(setDoc(doc(db("op", "operateur"), chemin), donnees));
      await assertSucceeds(setDoc(doc(sa, chemin), donnees));
    }
    await assertSucceeds(getDoc(doc(db("f", "fbo"), "tarifsPays/Burkina Faso")));
    await assertSucceeds(getDoc(doc(db("f", "fbo"), "villesExpedition/v1")));
  });
});

describe("mode maintenance", () => {
  it("l'état de maintenance est lisible par tous, même sans compte, et modifiable par personne", async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), "parametres/maintenance"), { actif: false }),
    );
    await assertSucceeds(getDoc(doc(env.unauthenticatedContext().firestore(), "parametres/maintenance")));
    await assertFails(setDoc(doc(db("sa", "super_admin", ""), "parametres/maintenance"), { actif: true }));
    await assertFails(setDoc(doc(db("fbo1", "fbo"), "parametres/maintenance"), { actif: false }));
  });

  it("pendant la maintenance, plus aucune écriture sauf celles du super administrateur", async () => {
    await env.withSecurityRulesDisabled((ctx) =>
      setDoc(doc(ctx.firestore(), "parametres/maintenance"), { actif: true }),
    );
    const d = db("fbo1", "fbo");
    await assertFails(setDoc(doc(d, "customers/c9"), { ownerId: "fbo1", nom: "Client" }));
    await assertFails(updateDoc(doc(d, "users/fbo1"), { nom: "Awa T." }));
    await assertFails(updateDoc(doc(db("liv1", "livreur"), "deliveries/d1"), { position: { lat: 1, lng: 1 } }));
    await assertSucceeds(getDoc(doc(d, "customers/c1")));
    await assertSucceeds(updateDoc(doc(db("sa", "super_admin", ""), "products/p1"), { prix: 15500 }));
  });
});

describe("catalogue et stock", () => {
  it("un FBO lit le catalogue mais ne le modifie pas", async () => {
    const d = db("fbo1", "fbo");
    await assertSucceeds(getDoc(doc(d, "products/p1")));
    await assertFails(updateDoc(doc(d, "products/p1"), { prix: 1 }));
  });

  it("seul le super administrateur modifie le catalogue", async () => {
    await assertFails(updateDoc(doc(db("a", "admin"), "products/p1"), { prix: 16000 }));
    await assertSucceeds(updateDoc(doc(db("sa", "super_admin", ""), "products/p1"), { prix: 16000 }));
  });

  it("le personnel ne lit pas le carnet de clients des distributeurs", async () => {
    await assertFails(getDoc(doc(db("op", "operateur"), "customers/c1")));
    await assertFails(getDoc(doc(db("a", "admin"), "customers/c1")));
    await assertSucceeds(getDoc(doc(db("fbo1", "fbo"), "customers/c1")));
  });

  it("le stock n'est modifiable par aucun client", async () => {
    await assertFails(getDoc(doc(db("fbo1", "fbo"), "stock/w1_p1")));
    await assertSucceeds(getDoc(doc(db("g", "gestionnaire_stock"), "stock/w1_p1")));
    await assertFails(updateDoc(doc(db("g", "gestionnaire_stock"), "stock/w1_p1"), { disponible: 99 }));
    await assertFails(setDoc(doc(db("sa", "super_admin"), "stockMovements/m1"), { type: "entree" }));
  });
});

describe("clients (CRM)", () => {
  it("un FBO gère ses clients", async () => {
    const d = db("fbo1", "fbo");
    await assertSucceeds(getDoc(doc(d, "customers/c1")));
    await assertSucceeds(setDoc(doc(d, "customers/c2"), { ownerId: "fbo1", nom: "Client B" }));
    await assertSucceeds(updateDoc(doc(d, "customers/c1"), { notes: "VIP" }));
    await assertSucceeds(deleteDoc(doc(d, "customers/c1")));
  });

  it("un FBO n'accède pas aux clients d'un autre", async () => {
    const d = db("fbo2", "fbo");
    await assertFails(getDoc(doc(d, "customers/c1")));
    await assertFails(updateDoc(doc(d, "customers/c1"), { notes: "x" }));
    await assertFails(deleteDoc(doc(d, "customers/c1")));
    await assertFails(setDoc(doc(d, "customers/c3"), { ownerId: "fbo1", nom: "Faux" }));
  });

  it("un FBO ne peut pas céder un client ni le vider de son nom", async () => {
    const d = db("fbo1", "fbo");
    await assertFails(updateDoc(doc(d, "customers/c1"), { ownerId: "fbo2" }));
    await assertFails(updateDoc(doc(d, "customers/c1"), { nom: "" }));
  });
});

describe("commandes et livraisons", () => {
  it("un FBO lit sa commande mais pas celle d'un autre", async () => {
    await assertSucceeds(getDoc(doc(db("fbo1", "fbo"), "orders/o1")));
    await assertFails(getDoc(doc(db("fbo2", "fbo"), "orders/o1")));
  });

  it("aucun client n'écrit une commande, pas même son auteur", async () => {
    await assertFails(setDoc(doc(db("fbo1", "fbo"), "orders/o2"), { fboId: "fbo1", total: 1 }));
    await assertFails(updateDoc(doc(db("fbo1", "fbo"), "orders/o1"), { total: 1 }));
    await assertFails(updateDoc(doc(db("sa", "super_admin"), "orders/o1"), { statut: "livree" }));
  });

  it("un livreur ne voit que ce qui lui est assigné", async () => {
    await assertSucceeds(getDoc(doc(db("liv1", "livreur"), "deliveries/d1")));
    await assertSucceeds(getDoc(doc(db("liv1", "livreur"), "orders/o1")));
    await assertFails(getDoc(doc(db("liv2", "livreur"), "deliveries/d1")));
    await assertFails(getDoc(doc(db("liv2", "livreur"), "orders/o1")));
  });

  it("un livreur met à jour sa position, rien d'autre", async () => {
    await assertSucceeds(
      updateDoc(doc(db("liv1", "livreur"), "deliveries/d1"), { position: { lat: 12.3, lng: -1.5 } }),
    );
    await assertFails(updateDoc(doc(db("liv1", "livreur"), "deliveries/d1"), { montantEncaisse: 9999 }));
    await assertFails(updateDoc(doc(db("liv2", "livreur"), "deliveries/d1"), { position: { lat: 0, lng: 0 } }));
  });

  it("un FBO suit la livraison de sa commande, pas celle d'un autre", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), "deliveries/d1"), { fboId: "fbo1" });
    });
    await assertSucceeds(getDoc(doc(db("fbo1", "fbo"), "deliveries/d1")));
    await assertFails(getDoc(doc(db("fbo2", "fbo"), "deliveries/d1")));
    await assertFails(updateDoc(doc(db("fbo1", "fbo"), "deliveries/d1"), { statut: "livree" }));
  });

  it("un FBO dont l'identifiant vaut celui d'un livreur n'en a pas les droits", async () => {
    await assertFails(getDoc(doc(db("liv1", "fbo"), "deliveries/d1")));
  });
});

describe("notifications et journal", () => {
  it("un utilisateur lit et marque ses notifications uniquement", async () => {
    const d = db("fbo1", "fbo");
    await assertSucceeds(getDoc(doc(d, "notifications/n1")));
    await assertSucceeds(updateDoc(doc(d, "notifications/n1"), { lu: true }));
    await assertFails(updateDoc(doc(d, "notifications/n1"), { titre: "Faux" }));
    await assertFails(getDoc(doc(db("fbo2", "fbo"), "notifications/n1")));
  });

  it("un jeton d'appareil n'appartient qu'à son utilisateur et n'est pas lisible", async () => {
    await assertSucceeds(setDoc(doc(db("fbo1", "fbo"), "fcmTokens/t1"), { userId: "fbo1" }));
    await assertFails(setDoc(doc(db("fbo2", "fbo"), "fcmTokens/t2"), { userId: "fbo1" }));
    await assertFails(getDoc(doc(db("fbo1", "fbo"), "fcmTokens/t1")));
    await assertFails(deleteDoc(doc(db("fbo2", "fbo"), "fcmTokens/t1")));
  });

  it("le journal est lisible par les admins et n'est jamais modifiable", async () => {
    await assertFails(getDoc(doc(db("o", "operateur"), "auditLogs/a1")));
    await assertFails(getDoc(doc(db("a", "admin"), "auditLogs/a1")));
    await assertSucceeds(getDoc(doc(db("sa", "super_admin", ""), "auditLogs/a1")));
    await assertFails(updateDoc(doc(db("sa", "super_admin"), "auditLogs/a1"), { action: "efface" }));
    await assertFails(deleteDoc(doc(db("sa", "super_admin"), "auditLogs/a1")));
  });
});

describe("Cloud Storage", () => {
  const image = new Uint8Array([1, 2, 3]);
  const meta = { contentType: "image/png" };

  it("le chemin non prévu est refusé", async () => {
    await assertFails(uploadBytes(ref(stockage("sa", "super_admin"), "divers/a.png"), image, meta));
  });

  it("seul un admin dépose une image produit", async () => {
    await assertFails(uploadBytes(ref(stockage("a", "admin"), "products/a.png"), image, meta));
    await assertSucceeds(uploadBytes(ref(stockage("sa", "super_admin"), "products/a.png"), image, meta));
    await assertFails(uploadBytes(ref(stockage("fbo1", "fbo"), "products/b.png"), image, meta));
  });

  it("une preuve de livraison ne se dépose qu'à son nom et ne se remplace pas", async () => {
    const liv = stockage("liv1", "livreur");
    await assertSucceeds(uploadBytes(ref(liv, "deliveries/d1/liv1/preuve.png"), image, meta));
    await assertFails(uploadBytes(ref(liv, "deliveries/d1/liv1/preuve.png"), image, meta));
    await assertFails(uploadBytes(ref(liv, "deliveries/d1/liv2/preuve.png"), image, meta));
    await assertFails(getBytes(ref(stockage("fbo1", "fbo"), "deliveries/d1/liv1/preuve.png")));
  });

  it("un fichier qui n'est pas une image est refusé", async () => {
    await assertFails(
      uploadBytes(ref(stockage("sa", "super_admin"), "products/a.txt"), image, { contentType: "text/plain" }),
    );
  });
});
