# Avancement FBO+

## Phase 1 — Socle Firebase : code terminé, à déployer et à tester sur appareil

Branche : `feature/phase-1-socle-firebase`

### Fait

**Firebase (`firebase/`)**
- Émulateurs Auth, Firestore, Functions et Storage, avec le projet fictif `demo-fbo-plus`.
- Règles Firestore et Storage basées sur les custom claims, tout étant refusé par défaut.
- Index Firestore des requêtes prévues.
- Cloud Functions (région `europe-west1`) :
  - `initialiserProfil` : crée le profil ; un nouveau numéro vérifié devient `fbo`.
  - `definirRole` : réservée aux admins ; seul un `super_admin` touche aux rôles d'administration ; personne ne change son propre rôle.
  - `creerCompteInterne` : comptes livreur et personnel, créés par l'administration.
  - `definirPin` et `verifierPin` : empreinte scrypt salée et poivrée, blocage de 15 minutes après 5 échecs.
- Journal `auditLogs` pour les actions sensibles.
- App Check exigé sur les fonctions hors émulateur.
- Script `scripts/creer-super-admin.mjs` pour le premier `super_admin`.

**Application FBO+**
- Connexion Firebase par téléphone/OTP, avec PIN côté serveur et biométrie conservée.
- Refus de la connexion si le rôle n'est pas `fbo`.
- Modification du PIN depuis Sécurité reportée sur le serveur.
- `AuthRepository` ajouté ; Retrofit et l'ancien client du backend Ktor retirés.
- App Check : Play Integrity en release, fournisseur debug en debug.
- HTTP en clair autorisé uniquement en debug.
- Accès direct temporaire au catalogue retiré : la connexion est de nouveau obligatoire.

### Vérifié

- `./gradlew assembleDebug` et `compileReleaseKotlin` réussissent.
- 40 tests réussis sur l'émulateur Firebase (`cd firebase && npm test`) : 25 sur les règles, 15 sur les fonctions.

### Non vérifié

- **L'application n'a jamais été lancée** : ce PC n'a ni émulateur Android ni appareil connecté. Le parcours SMS → PIN → catalogue reste à tester.
- Rien n'est déployé : aucun vrai projet Firebase n'existe encore.
- App Check n'a pas pu être testé, car l'émulateur Firebase ne le gère pas.

### Reste à faire pour clore la phase 1

1. Créer le projet Firebase, en formule Blaze : Functions et SMS sont payants.
2. Activer l'authentification par téléphone et enregistrer les empreintes SHA-1 et SHA-256.
3. Placer `google-services.json` dans `app/`. Il n'est pas commité. Sans lui, le build debug utilise les émulateurs et un build release ne peut pas démarrer.
4. Enregistrer l'application dans App Check (Play Integrity), puis activer l'application sur Firestore et Storage dans la console.
5. Définir le secret : `firebase functions:secrets:set PIN_PEPPER`.
6. Déployer : `firebase deploy --only firestore,storage,functions`.
7. Créer le premier compte avec `scripts/creer-super-admin.mjs`.
8. Tester le parcours complet sur un appareil, y compris la réception réelle des SMS.

### Points d'attention

- Les comptes de l'ancien backend Ktor ne sont pas migrés : ces utilisateurs devront se réinscrire.
- `PIN_PEPPER` ne doit plus changer une fois en production : tous les PIN existants deviendraient invalides.
- Firebase BoM reste en 34.19.0 : la version 35 exige Kotlin 2.4, le projet est en 2.2.10.
- La suspension d'un compte n'existe pas encore : à prévoir avec les utilisateurs en phase 3.
- `orders`, `stock` et `deliveries` n'ont pas encore de fonctions d'écriture : elles arrivent en phases 2 à 4.

### Lancer l'environnement local

```bash
cd firebase
npm install
npm --prefix functions install
npm --prefix functions run build
```

Créer `firebase/functions/.secret.local` (non commité) contenant `PIN_PEPPER=une-valeur-locale`, puis :

```bash
npm run emulateurs
```

Sur un téléphone branché en USB (débogage activé), rediriger les ports vers le PC, à refaire à chaque branchement :

```bash
adb reverse tcp:9099 tcp:9099
adb reverse tcp:5001 tcp:5001
```

L'interface des émulateurs est sur http://127.0.0.1:4000. Les codes SMS de test s'y affichent dans les journaux Auth, aucun vrai SMS n'est envoyé.

## Phases suivantes

- Phase 2 : application FBO+ complète — à venir.
- Phase 3 : FBO+ Admin — à venir.
- Phase 4 : FBO+ Delivery — à venir.
- Phase 5 : notifications — à venir.
- Phase 6 : plus tard, à ne pas commencer.
