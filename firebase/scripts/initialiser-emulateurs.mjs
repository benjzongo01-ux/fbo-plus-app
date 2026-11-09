// Remplit les émulateurs : catalogue, entrepôt, stock, tarifs et comptes fictifs.
// Refuse de s'exécuter ailleurs que sur les émulateurs locaux.
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";

process.env.FIREBASE_AUTH_EMULATOR_HOST ??= "127.0.0.1:9099";
process.env.FIRESTORE_EMULATOR_HOST ??= "127.0.0.1:8080";
const local = (hote) => /^(127\.0\.0\.1|localhost):/.test(hote);
if (!local(process.env.FIREBASE_AUTH_EMULATOR_HOST) || !local(process.env.FIRESTORE_EMULATOR_HOST)) {
  throw new Error("Ce script ne s'exécute que sur les émulateurs locaux.");
}

initializeApp({ projectId: "demo-fbo-plus" });
const auth = getAuth();
const db = getFirestore();

const ici = dirname(fileURLToPath(import.meta.url));
const produits = JSON.parse(readFileSync(resolve(ici, "../seed/products.json"), "utf8"));

const BF = "Burkina Faso";
const CI = "Côte d'Ivoire";

// Comptes fictifs du personnel : connexion par email (ou « Google de test » avec la même adresse).
// Mot de passe commun, valable uniquement sur les émulateurs : FboTest2026!
const MOT_DE_PASSE_TEST = "FboTest2026!";
const comptes = [
  { email: "superadmin@fboplus.test", telephone: "+22670000010", nom: "Super Admin Test", role: "super_admin", pays: BF },
  { email: "admin.bf@fboplus.test", telephone: "+22670000011", nom: "Admin Burkina Test", role: "admin", pays: BF },
  { email: "operateur.bf@fboplus.test", telephone: "+22670000012", nom: "Opérateur Burkina Test", role: "operateur", pays: BF },
  { email: "stock.bf@fboplus.test", telephone: "+22670000013", nom: "Gestionnaire Burkina Test", role: "gestionnaire_stock", pays: BF },
  { email: "livreur.bf@fboplus.test", telephone: "+22670000014", nom: "Livreur Burkina Test", role: "livreur", pays: BF },
  { email: "admin.ci@fboplus.test", telephone: "+2250700000011", nom: "Admin Côte d'Ivoire Test", role: "admin", pays: CI },
  { email: "livreur.ci@fboplus.test", telephone: "+2250700000014", nom: "Livreur Côte d'Ivoire Test", role: "livreur", pays: CI },
];

// Distributeur de test : se crée depuis FBO+ (Google de test ou email). PIN de test conseillé : 2468.

// Un entrepôt par pays ouvert. Le Sénégal reste fermé tant qu'il n'a pas d'entrepôt.
const entrepots = [
  { id: "principal", nom: "Entrepôt de Ouagadougou", ville: "Ouagadougou", pays: BF },
  { id: "abidjan", nom: "Entrepôt d'Abidjan", ville: "Abidjan", pays: CI },
];

const lot = db.bulkWriter();
for (const { id, ...entrepot } of entrepots) lot.set(db.doc(`warehouses/${id}`), { ...entrepot, actif: true });
lot.set(db.doc("centres/ouagadougou"), { nom: "Ouagadougou", actif: true });

// Villes d'expédition fictives et leurs frais, une liste par pays.
const villes = [
  ["bobo", BF, "Bobo-Dioulasso", 2500],
  ["koudougou", BF, "Koudougou", 1500],
  ["ouahigouya", BF, "Ouahigouya", 2000],
  ["bouake", CI, "Bouaké", 3000],
  ["yamoussoukro", CI, "Yamoussoukro", 2500],
  ["san-pedro", CI, "San-Pédro", 3500],
];
for (const [id, pays, ville, frais] of villes) {
  lot.set(db.doc(`villesExpedition/${id}`), { pays, ville, frais, actif: true });
}
// Livraison : 2 000 FCFA minimum (tout Ouagadougou) ; Abidjan dès 3 000, selon la zone.
lot.set(db.doc("parametres/tarifs"), { fraisLivraison: 2000, commissionLivreur: 500 });
lot.set(db.doc(`tarifsPays/${BF}`), { fraisLivraison: 2000, commissionLivreur: 500 });
lot.set(db.doc(`tarifsPays/${CI}`), { fraisLivraison: 3000, commissionLivreur: 700 });

for (const { id, ...produit } of produits) {
  lot.set(db.doc(`products/${id}`), produit);
  for (const e of entrepots) {
    lot.set(
      db.doc(`stock/${e.id}_${id}`),
      { entrepotId: e.id, pays: e.pays, produitId: id, disponible: 100, reserve: 0, seuilAlerte: 10 },
      { merge: true },
    );
  }
}
await lot.close();

for (const c of comptes) {
  const compte =
    (await auth.getUserByEmail(c.email).catch(() => null)) ??
    (await auth.getUserByPhoneNumber(c.telephone).catch(() => null)) ??
    (await auth.createUser({ email: c.email, phoneNumber: c.telephone, displayName: c.nom }));
  await auth.updateUser(compte.uid, { email: c.email, emailVerified: true, password: MOT_DE_PASSE_TEST });
  await auth.setCustomUserClaims(compte.uid, { role: c.role, pays: c.pays });
  await db.doc(`users/${compte.uid}`).set(
    {
      nom: c.nom,
      telephone: c.telephone,
      email: c.email,
      role: c.role,
      pays: c.pays,
      statut: "actif",
      createdAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
}

console.log(`${produits.length} produits et ${comptes.length} comptes prêts.`);
