import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    include: ["tests/**/*.test.ts"],
    // Sur une installation neuve, le premier démarrage des fonctions peut être très lent.
    testTimeout: 90000,
    hookTimeout: 90000,
    fileParallelism: false,
  },
});
