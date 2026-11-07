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

  it("un administrateur ne crée d'entrepôt que dans son pays", async () => {
    await assertSucceeds(setDoc(doc(db("a", "admin"), "warehouses/w2"), { nom: "Bobo", pays: "Burkina Faso" }));
    await assertFails(setDoc(doc(db("a", "admin"), "warehouses/w3"), { nom: "Dakar", pays: "Sénégal" }));
  });
});

describe("catalogue et stock", () => {
  it("un FBO lit le catalogue mais ne le modifie pas", async () => {
    const d = db("fbo1", "fbo");
    await assertSucceeds(getDoc(doc(d, "products/p1")));
    await assertFails(updateDoc(doc(d, "products/p1"), { prix: 1 }));
  });

  it("un admin modifie le catalogue", async () => {
    await assertSucceeds(updateDoc(doc(db("a", "admin"), "products/p1"), { prix: 16000 }));
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
    await assertSucceeds(getDoc(doc(db("a", "admin"), "auditLogs/a1")));
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
    await assertSucceeds(uploadBytes(ref(stockage("a", "admin"), "products/a.png"), image, meta));
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
      uploadBytes(ref(stockage("a", "admin"), "products/a.txt"), image, { contentType: "text/plain" }),
    );
  });
});
