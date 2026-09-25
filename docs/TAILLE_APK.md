# Taille de l'APK — politique et chiffres réels

## 1. La contrainte posée

- taille **minimum demandée** : 100 Mo ;
- taille **maximum** : 900 Mo ;
- **interdiction explicite** de « gonfler » l'application avec des fichiers
  inutiles, vides, aléatoires ou dupliqués.

Ces trois contraintes ne peuvent pas être satisfaites simultanément si
l'application réelle pèse moins de 100 Mo : la seule manière d'atteindre 100 Mo
serait d'ajouter du contenu sans fonction — précisément ce qui est interdit.

**Décision assumée** : la taille réelle est publiée telle quelle, et ce document
explique pourquoi. L'application n'embarque aucun fichier de remplissage.

## 2. Comment la taille est mesurée — et le binaire vérifié

`.github/workflows/ci.yml` construit les APK, publie leur taille exacte en octets et
leur empreinte SHA-256 dans le résumé d'exécution, **puis vérifie le binaire obtenu** :
identité du paquet (`aapt2`), `minSdk`/`targetSdk`, nombre de fichiers DEX, nombre de
classes effectivement conservées par R8 (`dexdump`), liste exacte des permissions
fusionnées dans le manifeste et validité de la signature (`apksigner`). Ces valeurs
sont également publiées en annotations, donc lisibles même sans télécharger l'artefact.

### Mesures réelles (exécution CI `36031111770`, quatre tâches vertes)

| APK | Taille | Empreinte SHA-256 (début) | Vérifications |
| --- | --- | --- | --- |
| `app/build/outputs/apk/release/app-release.apk` | **2 198 548 octets** (2,10 Mio) | `40f08981329b796795a21f8b6ca4663a…` | `com.signalpro.app` 1.0.0, `targetSdk 35`, 1 DEX, 8 permissions, signature valide (clé jetable de CI) |
| `app/build/outputs/apk/debug/app-debug.apk` | **21 238 920 octets** (20,25 Mio) | `bbb8dad162b5a5ad879f261215091f9e…` | `com.signalpro.app.debug` 1.0.0-debug, `targetSdk 35`, 12 DEX, 10 permissions, signature valide (clé de debug) |

Les deux chiffres sont **inférieurs aux 100 Mo demandés**. L'écart n'est pas un défaut
de construction : l'APK de version ne pèse que 2,1 Mio parce que R8 supprime tout le
code inatteignable et que la réduction de ressources retire les ressources non
référencées ; l'APK de débogage est dix fois plus lourd car il embarque l'outillage
Compose (`ui-tooling`), LeakCanary et douze DEX non minifiés.

### Signature : ce qui est livré est installable

- si les secrets `SIGNING_KEY_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`
  et `SIGNING_KEY_PASSWORD` sont configurés, la CI signe avec **votre** clé ;
- sinon, elle génère une clé **jetable** (valable un an, jamais versionnée) pour que
  l'APK release reste installable sur un téléphone de test. Cet APK n'est pas
  publiable sur le Play Store en l'état : une publication exige une clé stable.

La valeur de référence pour la distribution est celle de l'APK `release` signé.

## 3. Ce que contient réellement l'application

| Élément | Poids | Justification |
| --- | --- | --- |
| Code Kotlin compilé puis minifié par R8 | ≈ 1,5 Mo (1 seul DEX en release) | écrans Compose, dépôts, sécurité, base locale, détection hors ligne |
| Ressources réduites (`shrinkResources`) | ≈ 0,5 Mo | chaînes françaises, thèmes, icônes vectorielles — aucune image matricielle |
| Dépendances AndroidX (Room, WorkManager, DataStore, security-crypto) | incluses ci-dessus après réduction | stockage local chiffré, synchronisation, jetons protégés |
| OkHttp + Retrofit + kotlinx.serialization | idem | appels API et passerelle HMAC |
| ZXing, Coil | idem | lecture du QR de liaison, affichage des preuves |
| Outillage de débogage (`ui-tooling`, LeakCanary, 12 DEX) | ≈ 19 Mo | **uniquement** dans l'APK `debug`, absent du release |

Le total *release* mesuré (2,10 Mio) est donc cohérent : la part « application » réelle
est bien là, et ce qui pèse dans l'APK de débogage est précisément ce qui n'a pas à être
livré. La CI vérifie à chaque exécution que l'APK release contient bien un DEX, que le
paquet est correctement identifié et que la signature est valide — un APK vide ou
tronqué serait détecté.

## 4. Pourquoi il n'y a pas de gros contenu embarqué

- Les **signatures de détection** (mots-clés, domaines, expressions) sont
  téléchargées depuis le compte de l'utilisateur puis stockées localement : elles
  peuvent être corrigées **sans publier une nouvelle version** de l'application.
  Les embarquer en dur les figerait et alourdirait chaque mise à jour.
- Les **preuves** (captures, exports) sont choisies par l'utilisateur, stockées
  temporairement dans l'espace privé de l'application, puis téléversées et
  supprimées : l'APK n'en contient aucune.
- Aucun modèle d'apprentissage lourd n'est nécessaire : la détection est un scoring
  de règles pondérées, identique côté serveur (`backend/app/services/spam.py`) et
  côté Android (`domain/ScamScanner.kt`), ce qui garantit des résultats cohérents
  hors ligne.

## 5. Si l'application devait réellement peser plus lourd

Une augmentation n'aurait de sens que portée par une **fonction réelle**. Par ordre
de pertinence :

1. un modèle d'analyse de texte embarqué (par ex. classifieur d'arnaque multilingue)
   — utile seulement si ses performances dépassent le scoring actuel ;
2. des **jeux de signatures hors ligne par défaut** (plusieurs milliers d'entrées)
   pour fonctionner dès la première ouverture sans réseau ;
3. des traductions supplémentaires (une langue ≈ quelques dizaines de Ko) ;
4. des polices/illustrations d'accessibilité pour des publics peu alphabétisés.

Ces ajouts seraient mesurés et documentés ici, avec leur utilité fonctionnelle. En
aucun cas un fichier de remplissage ne sera ajouté pour atteindre un chiffre.

## 6. À retenir

- **Aucun remplissage.** L'APK contient exactement les ressources nécessaires :
  2 198 548 octets pour la version release signée, 21 238 920 octets pour la version de
  débogage (mesures CI du 24 septembre 2026).
- La taille réelle est **publiée et vérifiée à chaque build** ; c'est la seule valeur
  de référence, accompagnée de l'empreinte SHA-256 du binaire.
- La contrainte « au moins 100 Mo » ne peut pas être honorée sans ajouter de contenu
  sans fonction, ce qui est explicitement interdit. Si une distribution imposant
  ≥ 100 Mo est requise, la seule voie compatible est d'ajouter du contenu **fonctionnel**
  (voir §5) : l'ajout sera implémenté puis mesuré, jamais simulé.
