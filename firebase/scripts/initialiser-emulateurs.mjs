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

const comptes = [
  { telephone: "+22670000010", nom: "Super Admin Test", role: "super_admin" },
  { telephone: "+22670000011", nom: "Admin Test", role: "admin" },
  { telephone: "+22670000012", nom: "Opérateur Test", role: "operateur" },
  { telephone: "+22670000013", nom: "Gestionnaire Test", role: "gestionnaire_stock" },
  { telephone: "+22670000014", nom: "Livreur Test", role: "livreur" },
];

const lot = db.bulkWriter();
lot.set(db.doc("warehouses/principal"), { nom: "Entrepôt principal", ville: "Ouagadougou", actif: true });
lot.set(db.doc("centres/ouagadougou"), { nom: "Ouagadougou", actif: true });
lot.set(db.doc("parametres/tarifs"), { fraisLivraison: 1000, commissionLivreur: 500 });

for (const { id, ...produit } of produits) {
  lot.set(db.doc(`products/${id}`), produit);
  lot.set(
    db.doc(`stock/principal_${id}`),
    { entrepotId: "principal", produitId: id, disponible: 100, reserve: 0, seuilAlerte: 10 },
    { merge: true },
  );
}
await lot.close();

for (const c of comptes) {
  const compte = await auth
    .getUserByPhoneNumber(c.telephone)
    .catch(() => auth.createUser({ phoneNumber: c.telephone, displayName: c.nom }));
  await auth.setCustomUserClaims(compte.uid, { role: c.role });
  await db.doc(`users/${compte.uid}`).set(
    {
      nom: c.nom,
      telephone: c.telephone,
      role: c.role,
      statut: "actif",
      createdAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
}

console.log(`${produits.length} produits et ${comptes.length} comptes prêts.`);
