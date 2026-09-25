# Inventaire des Bibliothèques Open Source & Licences (THIRD_PARTY.md)

Ce document répertorie l'ensemble des bibliothèques open source, outils et composants tiers intégrés ou référencés dans l'architecture du projet **WhAlert**, conformément aux exigences de conformité légale et d'audit open source.

---

## 1. Interface Utilisateur & Composants Système

### Jetpack Compose & AndroidX
- **Groupe d'artéfacts** : `androidx.compose.*`, `androidx.activity:activity-compose`, `androidx.navigation:navigation-compose`
- **Dépôt officiel / Source** : [Google AndroidX AOSP](https://android.googlesource.com/platform/frameworks/support/)
- **Version** : Compose BOM 2024.12.01 (Core KTX 1.15.0, Lifecycle 2.8.7)
- **Licence** : Apache License 2.0
- **Rôle dans l'application** : Rendu déclaratif moderne de l'UI Material 3, animations d'apparition progressive, thèmes clair et sombre adaptés aux directives d'accessibilité mobile, gestion fluide des transitions sans recompositions inutiles.

### Android Material Components (Material 3)
- **Groupe d'artéfacts** : `androidx.compose.material3:material3`, `androidx.compose.material:material-icons-extended`
- **Dépôt officiel / Source** : [material-components-android](https://github.com/material-components/material-components-android)
- **Licence** : Apache License 2.0
- **Rôle dans l'application** : Système de design tokens, cartes avec élévation dynamique, typographie accessible, icônes vectorielles de cybersécurité.

---

## 2. Sécurité, Cryptographie & Validation

### AndroidX Security-Crypto & Google Tink
- **Groupe d'artéfacts** : `androidx.security:security-crypto:1.1.0-alpha06` / `com.google.crypto.tink:tink-android`
- **Dépôt officiel / Source** : [Google Tink Repository](https://github.com/tink-crypto/tink-java) / [AndroidX Security](https://developer.android.com/jetpack/androidx/releases/security)
- **Licence** : Apache License 2.0
- **Rôle dans l'application** : Chiffrement matériel au repos des dossiers et des numéros WhatsApp sensibles via le Keystore Android (AES-256 GCM pour les valeurs, AES-256 SIV pour les clés).

### Libphonenumber Android (MichaelRocks)
- **Groupe d'artéfacts** : `io.michaelrocks:libphonenumber-android:8.13.52`
- **Dépôt officiel / Source** : [libphonenumber-android sur GitHub](https://github.com/MichaelRocks/libphonenumber-android)
- **Licence** : Apache License 2.0 (Port optimisé de Google libphonenumber)
- **Rôle dans l'application** : Validation stricte en temps réel des numéros de téléphone internationaux au standard UIT-T E.164, détection de l'indicatif régional et formatage sans dépendance d'API réseau privée.

---

## 3. Réseau & Sérialisation JSON

### Square OkHttp & Retrofit
- **Groupe d'artéfacts** : `com.squareup.okhttp3:okhttp:4.12.0`, `com.squareup.retrofit2:retrofit:2.11.0`
- **Dépôt officiel / Source** : [Square OkHttp](https://github.com/square/okhttp) / [Square Retrofit](https://github.com/square/retrofit)
- **Licence** : Apache License 2.0
- **Rôle dans l'application** : Couche réseau HTTPS avec forçage TLS 1.3, gestion des timeouts, vérification stricte des certificats X.509 du système et résilience aux coupures réseau temporaires.

### Square Moshi
- **Groupe d'artéfacts** : `com.squareup.moshi:moshi-kotlin:1.15.2`
- **Dépôt officiel / Source** : [Square Moshi sur GitHub](https://github.com/square/moshi)
- **Licence** : Apache License 2.0
- **Rôle dans l'application** : Sérialisation et désérialisation JSON fortement typée avec génération de code adaptée à R8/ProGuard.

---

## 4. Concurrence & Asynchronisme

### Kotlinx Coroutines
- **Groupe d'artéfacts** : `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0`
- **Dépôt officiel / Source** : [Kotlin/kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- **Licence** : Apache License 2.0
- **Rôle dans l'application** : Déport de toutes les opérations I/O et réseau hors du thread principal (UI thread) pour garantir une fluidité constante (60/120 FPS).

---

## 5. Synthèse des Licences

| Composant | Auteur / Propriétaire | Licence | Compatible Propriétaire / Commercial |
|---|---|---|---|
| Jetpack Compose | Google / Android Open Source | Apache 2.0 | Oui |
| Material 3 | Google | Apache 2.0 | Oui |
| AndroidX Security Crypto | Google | Apache 2.0 | Oui |
| Libphonenumber Android | Michael Rocks / Google | Apache 2.0 | Oui |
| OkHttp & Retrofit | Square, Inc. | Apache 2.0 | Oui |
| Moshi | Square, Inc. | Apache 2.0 | Oui |
| Kotlinx Coroutines | JetBrains s.r.o. | Apache 2.0 | Oui |
