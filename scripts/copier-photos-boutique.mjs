// Copie les photos des produits de l'application Android vers la boutique publique (admin-web/public/produits).
// Chaque photo est nommée d'après la référence du produit : 15.jpg, 34.jpg…
// À relancer après l'ajout d'un produit ou d'une photo : node scripts/copier-photos-boutique.mjs
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const racine = join(dirname(fileURLToPath(import.meta.url)), "..");
const catalogue = readFileSync(join(racine, "app/src/main/java/com/benjo/fboplus/data/ProductRepository.kt"), "utf8");
const dossierPhotos = join(racine, "app/src/main/res/drawable");
const destination = join(racine, "admin-web/public/produits");
mkdirSync(destination, { recursive: true });

const fichiers = readdirSync(dossierPhotos);
let copiees = 0;
const manquantes = [];

for (const [, ref, nom] of catalogue.matchAll(/ref = "([^"]+)"[^\n]*?imageRes = R\.drawable\.(\w+)/g)) {
  const fichier = fichiers.find((f) => f.replace(/\.\w+$/, "") === nom);
  if (!fichier) {
    manquantes.push(`${ref} (${nom})`);
    continue;
  }
  copyFileSync(join(dossierPhotos, fichier), join(destination, `${ref}${fichier.slice(fichier.lastIndexOf("."))}`));
  copiees++;
}

console.log(`${copiees} photos copiées dans admin-web/public/produits.`);
if (manquantes.length) console.log(`Sans photo : ${manquantes.join(", ")}`);
if (!existsSync(destination)) process.exit(1);
