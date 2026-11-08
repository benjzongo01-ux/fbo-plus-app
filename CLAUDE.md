CLAUDE.md — Instructions pour Claude Code (projet FBO+)
Contexte
FBO+ est une plateforme de vente, logistique et finance pour les distributeurs Forever (FBO), les opérateurs internes et les livreurs. Elle comprend trois applications connectées à un backend unique :
• FBO+ (Android, dépôt fbo-plus-app) : application des distributeurs, publiée sur le Play Store.
• FBO+ Delivery (Android) : application des livreurs. Comptes créés uniquement par l'administration, aucune auto-inscription.
• FBO+ Admin (web) : centre d'appel, stocks, livreurs, produits, utilisateurs, paramètres.
Règle fondamentale : aucune application ne communique directement avec une autre. Firebase est la seule source officielle des données.
Stack technique
• Backend : Firebase (Authentication, Cloud Firestore, Cloud Functions en TypeScript, Cloud Storage, Cloud Messaging, Hosting). L'ancien backend Ktor (fbo-plus-backend) est abandonné : ne plus le modifier.
• Applications Android : Kotlin, Jetpack Compose, architecture MVVM existante (ViewModel + Repository). Conserver le thème, les couleurs, les composants et les écrans existants de fbo-plus-app.
• FBO+ Admin : application web hébergée sur Firebase Hosting (React + TypeScript recommandé).
• Organisation conseillée : un monorepo ou des dossiers firebase/ (règles, index, functions), admin-web/, delivery-app/, en plus de l'app FBO+.
Rôles (RBAC)
Rôles : fbo, livreur, operateur, gestionnaire_stock, admin, super_admin.
• Le rôle est stocké dans les custom claims Firebase Auth, attribué uniquement par une Cloud Function réservée aux admins.
• Un utilisateur ne peut jamais modifier son rôle, ni depuis l'application ni via un appel direct.
• Chaque application refuse la connexion si le rôle ne correspond pas (ex. un FBO ne peut pas ouvrir FBO+ Delivery).
Modèle de données Firestore (première version)
• users/{uid} : nom, téléphone, email, rôle (copie en lecture seule), centre, statut, date de création.
• products/{id} : nom, référence, prix officiel, CC, catégorie, image, actif.
• warehouses/{id} et stock/{warehouseId_productId} : quantité disponible, quantité réservée, seuil d'alerte.
• stockMovements/{id} : entrée, sortie, réservation, retour, inventaire, auteur, date.
• customers/{id} : appartient à un FBO (ownerId), nom, téléphone, adresse, notes, historique.
• orders/{id} : fboId, pays, mode (livraison, validation ou expedition), expedition (ville, compagnie, frais, numéro du colis), bénéficiaire (moi ou filleul : nom, prénom, ID Forever), client (nom, deux téléphones, quartier, adresse ; seulement en livraison), lignes (produit, quantité, prix), réduction, frais de livraison, total, adresse, statut, historique des statuts, livreur assigné.
• deliveries/{id} : commande, livreur, position GPS, preuve photo, signature, montant encaissé, statut.
• notifications/{id} et auditLogs/{id}.
Statuts de commande : nouvelle → confirmee → en_preparation → prete → assignee → en_livraison → livree (ou annulee, retournee). Une commande en validation s'arrête à prete → validee : le centre d'appel la valide sur l'ID Forever du bénéficiaire, sans livreur ni frais de livraison. Une expédition s'arrête à prete → expediee : le colis part vers une ville de la liste villesExpedition du pays, dont les frais s'ajoutent au total ; la compagnie de transport est facultative à la commande et choisie par l'expéditeur sinon.

Toute nouvelle fonction appelable s'écrit avec onAppel(nom, OPTIONS_APPEL, …) (firebase/functions/src/limites.ts), jamais onCall directement : c'est ce qui limite les rafales par utilisateur. Ajuster sa limite dans LIMITES si besoin.

Grade : attribué uniquement par l'administration du pays (definirGrade) ; le distributeur n'écrit que gradeDemande. Tarifs de livraison : tarifsPays/{pays}, sinon parametres/tarifs.

Pays : Burkina Faso, Côte d'Ivoire, Sénégal (liste PAYS dans firebase/functions/src/commun.ts). Chaque commande part vers l'entrepôt actif de son pays. Le personnel porte un pays dans ses claims et ne voit que son pays ; le super administrateur voit tout. Les données sans pays comptent comme Burkina Faso.
Règles de sécurité (obligatoires)
• Écrire des Firestore Security Rules strictes, basées sur les custom claims. Tout est refusé par défaut.
• Un FBO ne lit et n'écrit que ses propres clients et commandes. Un livreur ne voit que les livraisons qui lui sont assignées.
• Les changements de statut sensibles, les mouvements de stock, les calculs de prix et l'attribution des rôles passent uniquement par des Cloud Functions, jamais directement depuis le client.
• Le prix d'une commande est toujours recalculé côté serveur à partir de products.
• Journaliser chaque action sensible dans auditLogs.
• Activer Firebase App Check. Aucun secret dans le code ; utiliser les secrets des Cloud Functions.
• Écrire des tests des règles avec l'émulateur Firebase.
Flux principal à faire fonctionner
FBO crée une commande → Cloud Function valide et réserve le stock → l'opérateur la voit dans FBO+ Admin et la confirme → le gestionnaire de stock la prépare → un livreur est assigné → le livreur reçoit la mission dans FBO+ Delivery, livre, encaisse, ajoute photo et signature → la commande passe à livree → le FBO voit le suivi en temps réel.
Phases de travail (dans cet ordre)
1. Socle Firebase : projet, émulateurs, règles, custom claims, Cloud Functions de base, migration de l'authentification de FBO+ vers Firebase Auth (téléphone/OTP + PIN + biométrie existants).
2. Application FBO+ complète : catalogue depuis Firestore, clients (CRM), création et modification de commandes avec réductions, suivi en temps réel, historique, statistiques et CC, boutique personnelle (lien et QR code). Remplacer tout le stockage local (SharedPreferences de VentesRepository, ProfilRepository) par Firestore, avec cache hors ligne.
3. FBO+ Admin : centre d'appel (confirmation, modification, validation), gestion des stocks et multi-entrepôts, gestion des produits, gestion des livreurs (attribution, localisation, performance), utilisateurs, centres, paramètres et tarifs.
4. FBO+ Delivery : connexion sécurisée, missions, GPS et navigation, appel client, encaissement, photo et signature, validation, historique, revenus.
5. Notifications : Firebase Cloud Messaging entre les trois applications.
6. Plus tard, à ne pas commencer maintenant : FBO+ Wallet (dépôts, retraits, répartition automatique, Wave, Orange Money, MTN, Moov, Ecobank), paiement des abonnements et des frais, FBO+ AI.
   Règles de travail pour Claude Code
   • Travailler phase par phase, sur une branche Git par fonctionnalité, avec des commits clairs en français.
   • Après chaque changement Android, compiler avec ./gradlew assembleDebug et corriger les erreurs avant de continuer.
   • Tester les Cloud Functions et les règles avec l'émulateur Firebase.
   • Ne jamais supprimer ou casser les écrans existants sans raison ; réutiliser le thème et les composants.
   • Ne jamais committer google-services.json de production, de clés ou de mots de passe.
   • Interface en français. Monnaie : FCFA.
   • À la fin de chaque phase, résumer ce qui a été fait et ce qui reste dans PROGRESS.md.

Reprise du projet (à lire en premier)
Avant tout travail, lire PROGRESS.md : la section « Reprendre ici » dit où en est le projet et quelle est la prochaine étape. Les phases 1 à 5 sont terminées et testées sur téléphone. La phase 6 reste à ne pas commencer. Répondre à l'utilisateur en français simple : il n'est pas développeur.
Sur un nouvel ordinateur : installer Node.js 22 ou plus, Java et Android Studio, puis lancer `node scripts/preparer-poste.mjs`.

Décisions à respecter
• Émulateurs uniquement, aucun déploiement : l'utilisateur garde la formule Blaze, les vrais SMS et App Check pour le lancement, et sa carte prépayée a été refusée. Ne jamais lancer `firebase deploy` ni utiliser le vrai projet sans sa demande explicite.
• Le projet Firebase réel est `fbo-plus`, sous le compte Google de FBO+ (fboplusapp@gmail.com), jamais un compte personnel. Avant tout déploiement futur, vérifier le compte avec `firebase login:list`.
• `firebase/.firebaserc` reste sur `demo-fbo-plus`. Les applications Android n'utilisent le vrai projet qu'avec `-Pfbo.firebaseReel=true`, même si google-services.json est présent, et l'admin qu'avec `VITE_FIREBASE_REEL=true`.
• Connexion par téléphone/OTP + PIN + biométrie. L'ID Forever se saisit une seule fois puis se verrouille.
• Les données locales du téléphone ne sont jamais effacées : elles sont copiées sur accord explicite, compte par compte.
• Firebase BoM reste en 34.19.0 : la version 35 exige Kotlin 2.4 et le projet est en 2.2.10.

Méthode de travail apprise
• La compilation et les tests ne suffisent pas : le test sur téléphone a révélé des défauts qu'ils ne pouvaient pas voir. Tester sur appareil tout changement visible, et dire clairement ce qui n'a pas été vérifié.
• Test sur téléphone : `adb reverse` des ports 9099, 5001, 8080 et 9199, à refaire après chaque coupure USB. `uiautomator dump` se bloque sur les écrans Compose : piloter avec `adb shell input` et des captures. Le premier appel d'une Cloud Function sur émulateur peut prendre plusieurs secondes : attendre avant de conclure à une panne.
• Avec npm, utiliser le chemin complet de Node s'il n'est pas dans le PATH du shell.