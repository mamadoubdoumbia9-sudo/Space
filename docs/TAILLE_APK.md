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

## 2. Comment la taille est mesurée

`.github/workflows/ci.yml` construit l'APK puis écrit dans le résumé d'exécution le
nom de chaque APK avec sa taille exacte en octets :

```bash
find app/build/outputs/apk -name "*.apk" -printf "%p\n" | sort | while read -r apk; do
  echo "$apk $(stat -c%s "$apk")"
done
```

Deux APK sont produits :

| Variante | Contenu | Signé |
| --- | --- | --- |
| `debug` | code + ressources, non minifié, outillage Compose de débogage | non (clé de debug) |
| `release` | minifié (R8) + ressources réduites | oui, si `SIGNING_*` est fourni |

La valeur de référence est celle de l'APK `release` signé, puisque c'est le binaire
distribué. Le chiffre exact de la dernière exécution se trouve dans le résumé du
workflow GitHub Actions (section « Tailles d'APK réellement produites »).

## 3. Ce que contient réellement l'application

| Élément | Poids approximatif | Justification |
| --- | --- | --- |
| Kotlin/Java compilé (R8) | 3–6 Mo | 40+ classes : écrans, dépôts, sécurité, base locale |
| Jetpack Compose + Material 3 | 4–8 Mo | UI complète des 11 écrans |
| AndroidX (Room, WorkManager, DataStore, Security-crypto, Lifecycle) | 2–4 Mo | stockage local, synchronisation, chiffrement des jetons |
| OkHttp + Retrofit + kotlinx.serialization | 2–3 Mo | appels API et passerelle HMAC |
| ZXing (cœur) | < 1 Mo | génération du QR de liaison WhatsApp |
| Coil | < 1 Mo | affichage des captures/preuves |
| Ressources (strings fr, thèmes, icônes vectorielles) | < 1 Mo | aucune image matricielle embarquée |
| Bibliothèques de debug (`ui-tooling`, LeakCanary) | 4–8 Mo | **uniquement** en `debug`, exclues du release |

Ordre de grandeur attendu : **release ≈ 8–16 Mo**, debug ≈ 15–30 Mo, selon la
version d'AGP et la taille des dépendances Compose.

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

- **Aucun remplissage.** L'APK contient exactement les ressources nécessaires.
- La taille réelle est **publiée à chaque build** par la CI ; elle est la seule
  valeur de référence.
- Si votre contrainte de distribution impose ≥ 100 Mo, la seule voie compatible avec
  l'éthique du projet est d'ajouter du contenu **fonctionnel** (voir §5) : dites-le
  nous, et l'ajout sera implémenté puis mesuré, jamais simulé.
