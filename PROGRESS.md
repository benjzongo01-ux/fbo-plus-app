# Avancement FBO+

**Mode de travail actuel : émulateurs uniquement, aucun déploiement.** Blaze, les vrais SMS et App Check sont gardés pour le lancement. Le projet Firebase réel est `fbo-plus`, sous le compte Google de FBO+, jamais un compte personnel.

## Reprendre ici

Dernière mise à jour : 2026-11-07. Tout le travail est sur `main`, à jour sur GitHub.

Situation : les phases 1 à 5 sont terminées, ainsi que le nouveau parcours de commande (pays, pour moi ou un filleul, livraison, validation ou expédition). L'expédition a été testée de bout en bout sur téléphone le 2026-11-07. Le parcours complet (commande dans FBO+, traitement dans l'Admin, livraison dans Delivery) fonctionne sur téléphone avec les émulateurs. Rien n'est déployé.

Prochaines étapes, dans l'ordre :

1. Essayer sur téléphone la livraison avec filleul, et l'administration de la Côte d'Ivoire (+2250700000011).
2. Ouvrir un entrepôt au Sénégal quand l'administration y sera prête.
3. Faire les tests de la section « Reste à tester sur téléphone ».
4. Traiter les « Limites connues » choisies par l'utilisateur.
5. Quand la carte le permettra : suivre « Pour le lancement ».

Sur un autre ordinateur :

```bash
git clone https://github.com/benjzongo01-ux/fbo-plus-app.git
cd fbo-plus-app
node scripts/preparer-poste.mjs
```

Puis ouvrir le dossier dans Android Studio, et dire à Claude Code : « Lis CLAUDE.md et PROGRESS.md, puis continue ».

Ce que GitHub ne contient pas, volontairement :

- `app/google-services.json` : inutile avec les émulateurs. Pour le lancement, le retélécharger depuis la console Firebase, avec le compte FBO+.
- `firebase/functions/.secret.local` : recréé par le script d'installation.
- `local.properties` : recréé par Android Studio.
- Les ventes et le profil enregistrés sur le téléphone : ils restent sur l'appareil. Ne pas désinstaller FBO+ avant de les avoir repris sur un vrai compte.

Les données des émulateurs ne suivent pas non plus : après `npm run emulateurs`, lancer `npm run donnees` pour recréer le catalogue, le stock et les comptes fictifs. Sur le téléphone, se déconnecter une fois, car les anciens comptes de test n'existent plus.

## État par phase

| Phase | Code | Vérifié |
|---|---|---|
| 1. Socle Firebase | Terminé | Tests émulateur, parcours testé sur téléphone |
| 2. Application FBO+ | Terminé | Parcours principal testé sur téléphone |
| 3. FBO+ Admin | Terminé | Testé dans le navigateur, relié au téléphone en direct |
| 4. FBO+ Delivery | Terminé | Livraison complète testée sur téléphone |
| 5. Notifications | Terminé | Notifications Firestore testées sur téléphone ; push FCM impossible à tester sur émulateur |
| 6. Wallet, paiements, IA | Non commencé | Plus tard, comme prévu |

Vérifications automatiques : 97 tests sur l'émulateur Firebase (`cd firebase && npm test`), `./gradlew assembleDebug` pour les deux applications, `npm run build` pour l'admin.

Les branches `feature/phase-*` et `feature/profil-professionnel` gardent l'historique de chaque phase. Tout est fusionné dans `main` : le travail suivant part de `main`, sur une nouvelle branche.

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

## Commandes par pays, pour soi ou un filleul

Branche `feature/commande-livraison-validation`, fusionnée dans `main`.

- FBO+ : la commande se fait en deux écrans. D'abord les produits, puis « Continuer » : pays, pour qui (ma commande, ou mon filleul avec nom, prénom et ID Forever), puis livraison ou validation.
- Livraison : nom du client, téléphone obligatoire, deuxième téléphone facultatif, quartier et adresse. Validation : aucune information client, la commande est validée sur l'ID Forever du distributeur ou du filleul.
- « Ma commande » exige l'ID Forever renseigné dans le profil.
- Chaque commande part vers l'administration de son pays : l'admin, l'opérateur et le gestionnaire de stock ne voient que leur pays, côté règles Firestore comme côté Cloud Functions. Le super administrateur voit tout et change le pays d'un compte dans Utilisateurs.
- Admin : colonnes Pour, Type et Pays, détail avec bénéficiaire et ID Forever, bouton « Valider sur l'ID » pour une commande en validation prête. Les livreurs proposés sont ceux du pays de la commande.
- Delivery : bouton d'appel du deuxième numéro du client.
- Le Sénégal n'a pas encore d'entrepôt : ses commandes sont refusées avec un message clair.
- Comptes de test ajoutés pour la Côte d'Ivoire : admin +225 07 00 00 00 11, livreur +225 07 00 00 00 14, entrepôt `abidjan`.

## Protection contre les rafales de requêtes (2026-11-08)

Branche `feature/limitation-requetes`, fusionnée dans `main`.

Firebase n'a pas de serveur à nous devant lequel placer un reverse proxy : les applications parlent directement à Google, qui absorbe déjà les attaques massives. La protection porte donc sur chaque utilisateur :

- **Limiteur par compte** (`firebase/functions/src/limites.ts`) : toute fonction appelable passe par `onAppel`, qui compte les appels par compte et par action (fenêtre fixe, compteurs dans `limitesDebit/{uid}`, illisibles par les clients). Exemples : 10 commandes par minute, 5 changements de PIN et 10 vérifications de PIN par 10 minutes, 120 appels par minute toutes actions confondues. Au-delà : refus immédiat « Réessayez dans N secondes », sans toucher aux données, et sans gêner les autres comptes.
- **Plafond de serveurs** : 10 instances au plus par fonction (`MAX_INSTANCES`), pour borner la facture sous une rafale.
- Déjà en place : PIN bloqué 15 minutes après 5 erreurs, règles Firestore strictes, App Check exigé en production.

## Réglages de fiabilité (2026-11-07)

Branche `feature/reglages-fiabilite`, fusionnée dans `main`.

- **PIN oublié** : juste après le code SMS, « Code oublié ? Choisir un nouveau code » mène directement à la création d'un nouveau PIN. Plus tard, il ramène à la connexion par SMS. Les ventes du téléphone sont conservées.
- **Grade** : le distributeur ne fait qu'une demande (`gradeDemande`). Seule l'administration de son pays attribue le grade (`definirGrade`), dans Utilisateurs (bouton « Demandes de grade »). En attendant, la remise est celle du grade validé, ou de Client Privilégié. Le distributeur est prévenu par une notification.
- **ID Forever** : l'administration corrige un ID erroné (`corrigerIdForever`), avec trace dans le journal et notification.
- **Tarifs par pays** : frais de livraison et commission du livreur dans `tarifsPays/{pays}`, réglés par l'administration de chaque pays dans Paramètres. Les tarifs communs servent tant qu'un pays n'a pas les siens. FBO+ affiche les frais et le total avant l'envoi.
- **Nom des commandes** : une commande sans titre porte le nom du client ou du bénéficiaire.
- **Boutique publique** : photos des produits (`admin-web/public/produits`, à recopier avec `node scripts/copier-photos-boutique.mjs` après l'ajout d'un produit).
- **Livreur** : la position est partagée pendant toute la livraison, écran éteint compris, par un service avec notification permanente. Il s'arrête seul à la fin de la livraison ou à la déconnexion.
- **Entrepôts** : dans un pays à plusieurs entrepôts, la commande part du premier (champ `priorite`, puis nom) qui a tout le stock.

## Expédition vers une autre ville

Branche `feature/expedition`, fusionnée dans `main`.

- FBO+ : troisième choix « Expédition ». Le distributeur choisit la ville dans la liste du pays (avec ses frais), puis donne le nom et le téléphone du destinataire (deuxième téléphone facultatif), la compagnie de transport s'il la connaît, et des précisions (gare, point de retrait).
- Les frais de la ville s'ajoutent au total. Le centre d'appel peut les ajuster tant que la commande est nouvelle ou confirmée.
- Admin, Paramètres : chaque pays tient sa liste de villes d'expédition et leurs frais (ajouter, changer les frais, fermer).
- Admin, Centre d'appel : une expédition prête se marque « Expédiée » avec la compagnie (obligatoire à ce moment si le distributeur ne l'a pas donnée) et le numéro du colis. Le stock sort à ce moment.
- Villes de test : Bobo-Dioulasso, Koudougou, Ouahigouya (Burkina) ; Bouaké, Yamoussoukro, San-Pédro (Côte d'Ivoire).

Testé sur téléphone le 2026-11-07 : commande en expédition vers Bobo-Dioulasso sans compagnie, frais ajustés de 2 500 à 3 000 FCFA dans l'admin, confirmée, préparée, expédiée par STAF avec un numéro de colis. Le suivi du téléphone a tout affiché en direct, le stock est sorti (100 → 99, rien de réservé), 4 notifications envoyées. Une validation a aussi été envoyée.

Défauts trouvés par ce test et corrigés :
- après l'envoi, FBO+ restait sur une liste de produits vide au lieu de revenir aux ventes ;
- l'écran du PIN n'avait aucune sortie si le code était oublié : ajout de « Code oublié ? Se reconnecter avec mon numéro », qui conserve les ventes du téléphone ;
- l'admin affichait « Livraison » pour les frais d'une expédition.

À savoir : après `npm run donnees`, l'ancien compte du téléphone n'existe plus, donc son PIN est refusé. Utiliser « Code oublié ? » pour se reconnecter.

## Test de bout en bout sur téléphone

Réalisé le 2026-11-06 sur TECNO Spark 10C (Android 12), avec les émulateurs Firebase et des codes SMS simulés. Une commande créée dans FBO+ a été suivie en direct pendant son traitement dans l'Admin, puis livrée avec FBO+ Delivery.

Vérifié : première connexion sur un compte neuf, déconnexion réelle, reprise des données du téléphone sans doublon, réduction plafonnée, stock réservé puis sorti, chronologie en temps réel, 6 notifications sans doublon, refus d'un numéro inconnu dans Delivery, position GPS, photo, signature, encaissement, commission, QR code.

Défauts trouvés par ce test et corrigés :
- plantage à l'ouverture après 5 minutes d'absence (verrouillage automatique) ;
- application vide à la première connexion (écoutes Firestore refusées avant l'attribution du rôle) ;
- reprise des données proposée avant le déverrouillage ;
- Admin bloqué sur « Chargement » après une session révoquée ;
- Delivery : ancien code conservé après un refus, et pas de moyen de corriger le numéro.

## Limites connues

- Créer une commande exige une connexion, car le prix est calculé par le serveur. La consultation et les fiches clients fonctionnent hors ligne.
- Les comptes de l'ancien backend Ktor ne sont pas migrés.
- Firebase BoM reste en 34.19.0 : la version 35 exige Kotlin 2.4, le projet est en 2.2.10.

## Reste à tester sur téléphone

1. FBO+ : fiches clients, annulation et modification d'une commande, boutique publique ouverte depuis le QR code, consultation hors ligne.
2. FBO+ Delivery : signalement d'un retour, itinéraire, appel client.
3. Biométrie, saisie du PIN après verrouillage automatique, changement de PIN.
4. Lien entre deux téléphones : le test a été fait sur un seul appareil.

## Pour le lancement (pas maintenant)

1. Passer `fbo-plus` en formule Blaze : Functions et SMS sont payants. Mettre une alerte de budget.
2. Activer l'authentification par téléphone, enregistrer les empreintes SHA-1 et SHA-256, vérifier la livraison des SMS au Burkina.
3. Activer App Check (Play Integrity pour les applications, reCAPTCHA Enterprise pour l'admin web), puis son application sur Firestore, Storage et l'authentification.
   Dans Authentication, limiter les SMS aux pays ouverts (Burkina Faso, Côte d'Ivoire, Sénégal) pour éviter la fraude aux SMS.
4. Ajouter FBO+ Delivery au projet et fusionner sa configuration dans son propre `google-services.json`.
5. Choisir pour Firestore une région cohérente avec `europe-west1`.
6. Définir le secret : `firebase functions:secrets:set PIN_PEPPER`. Il ne doit plus changer ensuite, sinon tous les PIN deviennent invalides.
7. Compiler avec `-Pfbo.firebaseReel=true`, et l'admin avec `VITE_FIREBASE_REEL=true` et sa configuration web.
8. Remplacer `demo-fbo-plus` dans `firebase/.firebaserc`, se connecter avec `firebase login` sous le compte FBO+, puis déployer.
9. Créer le premier compte avec `scripts/creer-super-admin.mjs`.
10. Refaire le parcours complet avec de vrais SMS.

Sans `-Pfbo.firebaseReel=true`, les builds Android utilisent toujours les émulateurs, même si `google-services.json` est présent. Sans vrai identifiant dans `.firebaserc`, un `firebase deploy` vise le projet fictif et échoue.

## Lancer l'environnement local

Une seule fois, depuis la racine du projet :

```bash
node scripts/preparer-poste.mjs
```

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
