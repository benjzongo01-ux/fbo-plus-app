# FBO+ — Écarts avec la spécification technique v1.0

Comparaison faite le 2026-11-09 entre `FBOplus_Specification_Technique.docx` (14 modules, 3 applications) et ce qui est construit.
Légende : ✅ fait · 🟡 partiel · ❌ à faire · 💳 demande un compte ou un paiement externe.

## Les 14 modules

| # | Module | État | Ce qui existe | Ce qui manque |
|---|---|---|---|---|
| 1 | Identity | 🟡 | Comptes, connexion SMS (OTP), PIN, biométrie, rôles côté serveur, suspension, suppression | Vérification KYC (pièce d'identité), « déconnecter tous mes appareils » |
| 2 | Wallet | ❌ 💳 | — | Wallet, dépôts, retraits, historique, abonnements, répartition automatique, Wave, Orange Money, MTN, Moov, Ecobank |
| 3 | Logistics | ✅ | Commandes, validation sur ID, stock, préparation, expédition, livraison, retour, multi-pays | Demande de réapprovisionnement entre entrepôts |
| 4 | Operations | 🟡 | Centre d'appel, confirmation, modification, attribution, suivi | Contrôle qualité (vérification avant « prête », appel de satisfaction) |
| 5 | Delivery Management | 🟡 | GPS en continu, itinéraire, historique, confirmation, signature, photo, encaissement | Affectation automatique des livreurs |
| 6 | Warehouse | 🟡 | Entrées, sorties, inventaire, seuil d'alerte, multi-entrepôts | Réapprovisionnement (demande et suivi) |
| 7 | Order Finance | 🟡 | Prix officiel serveur, réductions plafonnées, frais de livraison et d'expédition, encaissement | « Qui paie quoi » (parts FBO / livreur / plateforme), historique financier détaillé |
| 8 | FBO+ AI | ❌ 💳 | — | Assistant marketing, scripts, visuels, vidéos, publicités Facebook, objections, conseils produits |
| 9 | CRM | 🟡 | Fiches clients, notes, historique des commandes | Prospects, relances programmées, fidélisation |
| 10 | Business Manager | 🟡 | Statistiques, revenus, CC, objectifs, performances | Équipes (filleuls), rapports exportables (PDF, Excel) |
| 11 | Marketplace | 🟡 | Boutique personnelle, catalogue avec photos, QR code, commande par WhatsApp | Commande en ligne directe depuis la boutique |
| 12 | Learning Center | ❌ | — | Formations, documents, quiz, certifications |
| 13 | Communication | 🟡 | Notifications dans l'application, push (prêt pour la mise en ligne) | Messagerie, chat avec le centre d'appel |
| 14 | Administration | 🟡 | Utilisateurs, centres, livreurs, stocks, tarifs, paramètres, maintenance, journal | Paiements et commissions (versement), dépend du Wallet |

## Les 3 applications

- **FBO+** : tout sauf Wallet, IA marketing, paiement des abonnements et des frais, retrait des bénéfices (💳).
- **FBO+ Delivery** : complet (connexion sécurisée, missions, GPS, navigation, appel client, encaissement, signature, photo, validation, historique, revenus).
- **FBO+ Admin** : complet sauf la gestion financière (Wallet, dépôts, retraits, répartition, paiements des commissions) et le module IA.

## Sécurité (section 13)

✅ Chiffrement (Firebase), OTP, biométrie, jetons de session, RBAC, journal d'audit, limitation des requêtes, mode maintenance.
❌ Détection de fraude (alertes sur comportements anormaux), sauvegardes automatiques (💳 forfait Blaze), déconnexion des appareils compromis.

## Ordre proposé

Faisable tout de suite, sans compte externe :
1. Affectation automatique des livreurs.
2. Commande en ligne directe depuis la boutique publique.
3. CRM : prospects, relances, fidélisation.
4. Équipes (filleuls) et rapports exportables.
5. KYC, déconnexion de tous les appareils, alertes de fraude.
6. Réapprovisionnement et contrôle qualité.
7. Parts « qui paie quoi » et historique financier (prépare le Wallet).
8. Learning Center.
9. Messagerie avec le centre d'appel.

Demande un compte ou un budget externe :
- Wallet et paiements mobiles : comptes marchands chez chaque opérateur (ou un agrégateur comme CinetPay ou PayDunya).
- FBO+ AI : clé d'API d'un modèle d'IA ; les publicités Facebook demandent un compte Meta Business.
- Mise en ligne : forfait Blaze, vrais SMS, App Check, sauvegardes, compte Google Play Développeur.
