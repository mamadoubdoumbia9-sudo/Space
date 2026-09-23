# Configuration de build Android

| Paramètre | Valeur |
|---|---|
| Nom de l'application | La Cartographie des Absents (FR) / The Cartography of the Absent (EN) |
| Package | `com.ateliermareebasse.cartographie` |
| Version | versionCode 1 — versionName `1.0.0-ch1` |
| SDK | minSdk 26 (Android 8.0) — targetSdk 34 — compileSdk 34 |
| Orientation | `fullUser` : portrait et paysage, mise en page adaptative (l'écran de la lettre garde le pli courant à la rotation) |
| Résolution | indépendante : rendu vectoriel + tableaux 16:9 recadrés « cover » ; échelle UI = min(largeur, hauteur)/720 |
| Plein écran | immersif bord à bord, encoche (`shortEdges`), barres système transparentes |
| Icône | icône adaptative (fond papier + rose des vents à l'encre) + icône classique par densité (`tools/make_icons.py`) |
| Splash | fond encre + logo (rose des vents claire) via `Theme.Cartographie.Splash` |
| Permissions | `VIBRATE` uniquement (retour haptique des choix encre or et de la lettre, désactivable dans les options) |
| Réseau | aucun ; aucune donnée ne quitte l'appareil (la réponse libre est stockée dans la sauvegarde locale) |
| Stockage | `files/saves/` : `slot_1..3.sav`, `auto.sav`, `checkpoint.sav`, `settings.txt` — inclus dans la sauvegarde Android |
| Écran allumé | uniquement pendant la lecture de la lettre |
| Signature | v1 (JAR, SHA-256 + PKCS#7) et v2 (APK Signing Block, RSA-2048 PKCS#1 v1.5 SHA-256), vérifiée à la construction |
| Alignement | `resources.arsc` et toutes les entrées non compressées alignées sur 4 octets ; `.ogg .jpg .png .ttf` stockés non compressés |
| Chaîne de compilation | aapt2 2.20 → kotlinc 2.4 (cible 1.8) → d8 (`--release`, min-api 26) → assembleur zip Python → signature |
| Alternative | projet Gradle standard (`app/build.gradle.kts`, AGP 8.5, Kotlin 2.0) pour Android Studio / CI |
| Dépendances | aucune bibliothèque tierce (Kotlin stdlib seulement) |
| Qualité graphique | LOW (images ½, sans particules/grain/vignette), MEDIUM (¾, vignette), HIGH (1×, grain, 60 particules), ULTRA (120 particules, parallaxe) |
| Mémoire | cache d'images LRU = mémoire max / 5 (24–160 Mo), `largeHeap`, décodage RGB_565 pour les JPEG, `onTrimMemory` |

## Installation

```
adb install -r build/out/LaCartographieDesAbsents-ch1-release.apk
```
Le certificat étant auto-signé, l'installation manuelle demande l'autorisation « sources inconnues » ; pour une
publication, fournir la clé de production via `RELEASE_KEY` / `RELEASE_CERT`.
