// Génère seed/products.json depuis le catalogue embarqué dans l'application Android.
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ici = dirname(fileURLToPath(import.meta.url));
const source = resolve(ici, "../../app/src/main/java/com/benjo/fboplus/data/ProductRepository.kt");
const texte = readFileSync(source, "utf8");

const motif =
  /Product\(id = (\d+), ref = "([^"]*)", nameFr = "([^"]*)", categorie = "([^"]*)", prixPublic = (\d+), cc = ([\d.]+)[^\n]*\r?\n?\s*(?:description = "((?:[^"\\]|\\.)*)")?/g;

const produits = [...texte.matchAll(motif)].map((m) => ({
  id: m[2],
  reference: m[2],
  nom: m[3],
  categorie: m[4],
  prix: Number(m[5]),
  cc: Number(m[6]),
  description: m[7] ?? "",
  actif: true,
}));

const refs = new Set(produits.map((p) => p.id));
if (produits.length !== 93 || refs.size !== produits.length) {
  throw new Error(`Extraction incorrecte : ${produits.length} produits, ${refs.size} références uniques.`);
}

mkdirSync(resolve(ici, "../seed"), { recursive: true });
writeFileSync(resolve(ici, "../seed/products.json"), JSON.stringify(produits, null, 2) + "\n");
console.log(`${produits.length} produits extraits`);
