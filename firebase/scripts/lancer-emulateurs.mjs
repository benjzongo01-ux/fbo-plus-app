// Lance les émulateurs sur le projet fictif, avec les données de la session précédente si elles existent.
// Arrêter avec Ctrl+C pour que les données soient sauvegardées dans .emulateurs/.
import { spawn, spawnSync } from "node:child_process";
import { existsSync } from "node:fs";

// Le chargement des fonctions dépasse les 10 secondes par défaut sur une installation neuve :
// l'émulateur démarrerait alors sans aucune fonction.
process.env.FUNCTIONS_DISCOVERY_TIMEOUT ??= "120";

const options = { stdio: "inherit", shell: true };
const build = spawnSync("npm --prefix functions run build", options);
if (build.status !== 0) process.exit(build.status ?? 1);

const importer = existsSync(".emulateurs/firebase-export-metadata.json") ? "--import=.emulateurs" : "";
spawn(`npx firebase emulators:start --project demo-fbo-plus ${importer} --export-on-exit=.emulateurs`, options)
  .on("exit", (code) => process.exit(code ?? 0));
