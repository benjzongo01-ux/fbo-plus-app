// Lance tous les tests sur les émulateurs, avec les fonctions compilées à partir du code actuel.
import { spawnSync } from "node:child_process";

// Le chargement des fonctions dépasse les 10 secondes par défaut sur une installation neuve :
// l'émulateur démarrerait alors sans aucune fonction.
process.env.FUNCTIONS_DISCOVERY_TIMEOUT ??= "120";

const options = { stdio: "inherit", shell: true };
const build = spawnSync("npm --prefix functions run build", options);
if (build.status !== 0) process.exit(build.status ?? 1);

const tests = spawnSync(
  'npx firebase emulators:exec --project demo-fbo-plus --only auth,firestore,functions,storage "npx vitest run"',
  options,
);
process.exit(tests.status ?? 1);
