// Prépare un ordinateur après un git clone : node scripts/preparer-poste.mjs
// Installe les dépendances et crée les fichiers locaux, jamais commités, dont les émulateurs ont besoin.
import { execSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import { existsSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const racine = resolve(dirname(fileURLToPath(import.meta.url)), "..");

const versionNode = Number(process.versions.node.split(".")[0]);
if (versionNode < 22) {
  console.error(`Node.js 22 ou plus est requis (version actuelle : ${process.version}).`);
  process.exit(1);
}

function outil(nom, commande, aide) {
  try {
    execSync(commande, { stdio: "ignore" });
    console.log(`OK      ${nom}`);
  } catch {
    console.log(`ABSENT  ${nom} : ${aide}`);
  }
}

outil("Git", "git --version", "à installer");
outil("Java", "java -version", "requis par Gradle et les émulateurs Firebase");

for (const dossier of ["firebase", "firebase/functions", "admin-web"]) {
  console.log(`\nInstallation : ${dossier}`);
  execSync("npm ci --no-fund --no-audit", { cwd: resolve(racine, dossier), stdio: "inherit" });
}

execSync("npm run build", { cwd: resolve(racine, "firebase/functions"), stdio: "inherit" });

const secret = resolve(racine, "firebase/functions/.secret.local");
if (!existsSync(secret)) {
  // Valeur propre à ce poste : elle ne sert qu'aux émulateurs, jamais au vrai projet.
  writeFileSync(secret, `PIN_PEPPER=${randomBytes(24).toString("hex")}\n`);
  console.log("\nCréé : firebase/functions/.secret.local");
}

console.log(`
Poste prêt. Vérifier :        cd firebase && npm test
Android :                     ouvrir le dossier une fois dans Android Studio (crée local.properties)
Suite du travail :            PROGRESS.md, section « Reprendre ici »`);
