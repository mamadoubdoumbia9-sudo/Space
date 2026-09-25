# WhAlert — Plateforme Android de Signalement Légitime & Documentation de Comptes Suspects

**WhAlert** est une application Android professionnelle et robuste conçue pour permettre aux utilisateurs et professionnels de la cybersécurité de documenter méthodiquement, horodater et signaler légitimement des comptes WhatsApp suspects ou malveillants (arnaques, usurpation d'identité, spam massif, harcèlement, liens frauduleux).

Conformément aux principes de cybersécurité défensive et d'intégrité déontologique, **WhAlert ne simule aucune API, ne promet aucun bannissement miraculeux et respecte scrupuleusement les mécanismes officiels de WhatsApp.**

---

## Fonctionnalités Principales

1. **Écran 1 — Accueil moderne & réactif**
   - Logo, identité visuelle et typographie soignée (Material 3).
   - Statut de connexion officiel du compte utilisateur (`CONNECTÉ`, `NON CONNECTÉ`, `SESSION EXPIRÉE`, `ERREUR DE CONNEXION`).
   - Accès rapide : *Signaler un compte*, *Mes signalements*, *Guide de sécurité*, *Paramètres*, *Transparence*, *Console Privée*.

2. **Écran 2 — Constitution d'un nouveau dossier**
   - Validation stricte en temps réel du numéro cible au standard international **E.164** (ex: `+33612345678`).
   - Sélection parmi 7 motifs légitimes : Spam, Arnaque financière, Usurpation d'identité, Harcèlement, Contenu frauduleux, Comportement malveillant, Autre.
   - Saisie factuelle et chronologique des faits avec relevé précis de la date et de l'heure.
   - Dispositif anti-multiplication strict (rejet des envois multiples ou campagnes automatisées).

3. **Écran 3 — Vérification & Confirmation obligatoire**
   - Récapitulatif exhaustif du dossier avant toute action.
   - Dialogue de confirmation obligatoire : *« Confirmez-vous l'envoi ? [NON, ANNULER] [OUI, CONTINUER] »*.
   - Choix du canal officiel :
     - **Canal WhatsApp In-App** (mécanisme prioritaire recommandé par WhatsApp pour examen par leurs équipes).
     - **Canal Support Documentaire E-mail** (`android_web@support.whatsapp.com`) pour les dossiers détaillés.
   - Génération automatique d'un texte formel, précis et sans accusation non étayée.
   - Fonctions [COPIER] et [MODIFIER].

4. **Écran 4 — Suivi & Timeline du dossier**
   - Timeline dynamique : *Dossier préparé* → *Confirmation utilisateur* → *Transmission tentée* → *Confirmation technique* → *Décision de modération*.
   - Distinction claire entre transmission réussie et statut de modération WhatsApp.
   - Mention transparente obligatoire : *« WhatsApp ne fournit pas de confirmation publique permettant à cette application de vérifier directement si ce compte a été suspendu. »*

5. **Écran 5 — Historique chiffré des signalements**
   - Masquage des données sensibles par défaut (`+33 •••••• 678`) pour le respect de la vie privée.
   - Démasquage ponctuel sous contrôle de l'utilisateur.
   - Consultation des états et détails des dossiers précédents.

6. **Écran 6 — Guide de sécurité & Cyberdéfense**
   - Bonnes pratiques pour reconnaître une escroquerie.
   - Règles de conservation des preuves numériques.
   - Interdiction de menacer ou d'insulter le contact suspect.
   - Protection de son propre compte WhatsApp (activation du double facteur 2FA).

7. **Écran 11 — Console Privée Administrateur**
   - Interface isolée protégée par authentification forte (code PIN haché en SHA-256).
   - Consultation des métriques techniques globales (créations, transmissions, erreurs de canal).
   - Consultation des 100 derniers journaux d'audit technique.
   - Configuration du quota de sécurité anti-abus (par défaut : 3 dossiers authentiques max / 24 heures).

8. **Écran 14 — Transparence Déontologique**
   - Détail explicite de ce que l'application **PEUT** et **NE PEUT PAS** faire.

---

## Architecture Technique & Stack

- **Langage :** Kotlin 2.1.0 (JVM Target 17).
- **Interface Utilisateur :** Jetpack Compose, Material 3, animations fluides à 60/120 FPS.
- **Architecture :** Clean Architecture / MVVM.
- **Réseau & TLS :** Square OkHttp 4.12.0, Retrofit 2.11.0, Moshi 1.15.2, forçage strict du chiffrement TLS 1.3 (`cleartextTrafficPermitted="false"`).
- **Sécurité au repos :** `androidx.security:security-crypto` (AES-256 GCM adossé au Keystore Android matériel).
- **Validation téléphonique :** Algorithmes et expressions E.164 conformes aux spécifications UIT-T.

---

## Instructions d'Installation & de Build

### 1. Fichier APK Release
L'APK release officiel signé est directement disponible dans le dépôt :
```
app/build/outputs/apk/release/app-release.apk
```

Pour installer directement l'APK sur un terminal ou un émulateur Android connecté en ADB :
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

### 2. Compilation complète depuis les sources (avec JDK 17 & Android SDK)
Si vous disposez d'un environnement complet avec Android Studio ou Android SDK en ligne de commande :
```bash
./gradlew assembleRelease
```
L'APK résultant sera généré dans `app/build/outputs/apk/release/`.

### 3. Exécution de la suite de tests unitaires
Pour exécuter la suite de tests de validation logique (validation E.164, masquage de numéros, moteur de génération textuelle) :
```bash
python3 tests_validation.py
```
Résultat : Tous les tests passent avec succès (`OK`).

---

## Documentation Complémentaire

- **`THIRD_PARTY.md`** : Inventaire des dépendances open source et vérification des licences.
- **`CONNECTIVITY_REPORT.md`** : Cartographie technique des flux réseau et des interactions externes.
