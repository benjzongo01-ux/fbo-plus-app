# Avancement FBO+

**Mode de travail actuel : émulateurs uniquement, aucun déploiement.** Blaze, les vrais SMS et App Check sont gardés pour le lancement. Le projet Firebase réel est `fbo-plus`, sous le compte Google de FBO+, jamais un compte personnel.

## État par phase

| Phase | Code | Vérifié |
|---|---|---|
| 1. Socle Firebase | Terminé | Tests émulateur, parcours testé sur téléphone |
| 2. Application FBO+ | Terminé | Tests serveur ; écrans Firestore pas encore testés sur téléphone |
| 3. FBO+ Admin | Terminé | Testé dans le navigateur sur émulateurs |
| 4. FBO+ Delivery | Terminé | Tests serveur ; application pas encore lancée |
| 5. Notifications | Terminé | Notifications Firestore testées ; push FCM impossible à tester sur émulateur |
| 6. Wallet, paiements, IA | Non commencé | Plus tard, comme prévu |

Vérifications automatiques : 65 tests sur l'émulateur Firebase (`cd firebase && npm test`), `./gradlew assembleDebug` pour les deux applications, `npm run build` pour l'admin.

Chaque phase a sa branche, empilée sur la précédente, à fusionner dans cet ordre : `feature/phase-1-socle-firebase`, `feature/profil-professionnel`, `feature/phase-2-app-fbo`, `feature/phase-3-admin`, `feature/phase-4-delivery`, `feature/phase-5-notifications`.

## Phase 1 — Socle Firebase

- Émulateurs Auth, Firestore, Functions et Storage sur le projet fictif `demo-fbo-plus`.
- Règles Firestore et Storage basées sur les custom claims, tout étant refusé par défaut.
- Connexion FBO+ par téléphone/OTP, PIN côté serveur (empreinte scrypt, blocage après 5 échecs), biométrie conservée.
- Rôles par Cloud Function, journal `auditLogs`, App Check exigé hors émulateur.
- Refonte de l'écran Profil et de sa page de modification.

## Phase 2 — Application FBO+

- Catalogue, commandes, profil et clients sur Firestore, avec cache hors ligne.
- Commandes par Cloud Functions : prix recalculés, réduction plafonnée à la marge du grade, stock réservé en transaction, sans doublon en cas de nouvelle tentative.
- Suivi en temps réel avec chronologie, historique, statistiques et CC existantes conservées.
- Fiches clients avec historique.
- Boutique personnelle : lien, QR code et page publique.
- Import des données du téléphone, sur accord explicite : les ventes et le profil locaux sont copiés, jamais effacés.

## Phase 3 — FBO+ Admin (`admin-web/`)

- Connexion réservée au personnel, menu adapté au rôle.
- Centre d'appel : files par statut, modification, confirmation, annulation motivée, attribution du livreur.
- Stocks multi-entrepôts : entrées, inventaires, alertes de stock bas.
- Produits, livreurs (performance, dernière position), utilisateurs (rôles, comptes internes, suspension), centres et tarifs.

## Phase 4 — FBO+ Delivery (`delivery-app/`)

- Connexion réservée aux comptes livreurs, sans auto-inscription.
- Missions, GPS partagé pendant la livraison, itinéraire, appel client.
- Encaissement, photo et signature obligatoires, signalement de retour.
- Historique et revenus.

## Phase 5 — Notifications

- Chaque changement de commande crée une notification pour le distributeur, et une mission pour le livreur, sans doublon.
- Écran Notifications dans FBO+, pastille du nombre de nouvelles commandes dans l'admin.
- Envoi push FCM écrit et protégé : il ne s'exécute pas sur émulateur.

## Limites connues

- **FBO+ et Delivery n'ont pas été testées sur téléphone depuis le passage à Firestore.** Sur ce point, compilé ne veut pas dire fonctionnel.
- Créer une commande exige une connexion, car le prix est calculé par le serveur. La consultation et les fiches clients fonctionnent hors ligne.
- Les commandes partent toutes de l'entrepôt `principal`. Les autres entrepôts gèrent leur stock, mais le choix de l'entrepôt par commande reste à faire.
- La boutique publique n'affiche pas encore de photos : elles sont dans l'application Android, pas sur Storage.
- Le grade reste déclaré par le distributeur, alors qu'il plafonne ses réductions.
- Le GPS du livreur n'est partagé que pendant que l'écran de mission est ouvert.
- L'ID Forever verrouillé n'a pas encore de correction possible depuis l'admin.
- Les comptes de l'ancien backend Ktor ne sont pas migrés.
- Firebase BoM reste en 34.19.0 : la version 35 exige Kotlin 2.4, le projet est en 2.2.10.

## Reste à tester sur téléphone

1. FBO+ : import des données, commande, suivi, clients, boutique, notifications, déconnexion.
2. FBO+ Delivery : connexion, mission, GPS, photo, signature, livraison, retour.
3. Biométrie, verrouillage automatique, changement de PIN, Modifier commande.
4. Parcours complet à trois applications : FBO+, Admin, Delivery.

## Pour le lancement (pas maintenant)

1. Passer `fbo-plus` en formule Blaze : Functions et SMS sont payants. Mettre une alerte de budget.
2. Activer l'authentification par téléphone, enregistrer les empreintes SHA-1 et SHA-256, vérifier la livraison des SMS au Burkina.
3. Activer App Check (Play Integrity), puis son application sur Firestore et Storage.
4. Ajouter FBO+ Delivery au projet et fusionner sa configuration dans son propre `google-services.json`.
5. Choisir pour Firestore une région cohérente avec `europe-west1`.
6. Définir le secret : `firebase functions:secrets:set PIN_PEPPER`. Il ne doit plus changer ensuite, sinon tous les PIN deviennent invalides.
7. Compiler avec `-Pfbo.firebaseReel=true`, et l'admin avec `VITE_FIREBASE_REEL=true` et sa configuration web.
8. Remplacer `demo-fbo-plus` dans `firebase/.firebaserc`, se connecter avec `firebase login` sous le compte FBO+, puis déployer.
9. Créer le premier compte avec `scripts/creer-super-admin.mjs`.
10. Refaire le parcours complet avec de vrais SMS.

Sans `-Pfbo.firebaseReel=true`, les builds Android utilisent toujours les émulateurs, même si `google-services.json` est présent. Sans vrai identifiant dans `.firebaserc`, un `firebase deploy` vise le projet fictif et échoue.

## Lancer l'environnement local

Une seule fois :

```bash
cd firebase
npm install
npm --prefix functions install
```

Créer `firebase/functions/.secret.local` (non commité) contenant `PIN_PEPPER=une-valeur-locale`.

À chaque session, dans `firebase/` :

```bash
npm run emulateurs
```

Puis, dans un second terminal, une fois les émulateurs prêts :

```bash
npm run donnees
```

Arrêter avec Ctrl+C pour conserver les données dans `firebase/.emulateurs/`. Pour créer une commande de démonstration : `node scripts/commande-test.mjs`.

Admin, dans `admin-web/` :

```bash
npm install
npm run dev
```

Téléphone en USB, à refaire à chaque branchement :

```bash
adb reverse tcp:9099 tcp:9099
adb reverse tcp:5001 tcp:5001
adb reverse tcp:8080 tcp:8080
adb reverse tcp:9199 tcp:9199
```

Comptes fictifs : super admin `+22670000010`, admin `+22670000011`, opérateur `+22670000012`, gestionnaire `+22670000013`, livreur `+22670000014`. Les codes SMS de test s'affichent dans l'interface des émulateurs (http://127.0.0.1:4000, Auth). Aucun vrai SMS n'est envoyé.
