# SignalPro — signalement **légitime** de comptes WhatsApp malveillants

SignalPro permet à des utilisateurs **réels** de documenter puis de transmettre des
signalements d'arnaques, de spam, d'usurpation et de harcèlement subis sur WhatsApp,
d'en suivre l'état honnêtement, et de protéger la communauté (liste de numéros
confirmés malveillants, blocage en masse, contestation).

## Ce que ce projet ne fait pas (et ne fera jamais)

- Il **ne bannit personne** : aucun service, aucune API ne permet de suspendre un
  compte WhatsApp à la demande. Seul Meta examine les signalements et décide.
  L'avertissement est affiché sur l'accueil **et** avant chaque envoi.
- Il **ne fabrique aucun faux signalement** : une preuve valide est obligatoire, un
  signalement sans preuve n'est jamais transmis, et un faux signalement confirmé
  entraîne le bannissement de l'auteur de l'application.
- Il **ne contourne aucune limite** de WhatsApp : 10 actions/minute, 5 signalements/
  heure, 20 signalements/jour par compte, en plus des quotas du serveur.

## Chaque exigence, son implémentation et sa preuve

| Exigence | Implémentation | Preuve exécutable |
| --- | --- | --- |
| Connexion WhatsApp sécurisée, officielle pour une entreprise | `backend/app/services/cloud_api.py` (WhatsApp Business Platform), `docs/DEPLOIEMENT_WHATSAPP.md` | tests backend + documentation des prérequis et webhooks |
| Connexion d'un particulier, avec consentement au risque et limitations strictes | passerelle locale `gateway/` (protocole multi-appareils), requêtes signées HMAC-SHA256 ± 300 s | `cd gateway && node --test test/*.test.js` — **7/7** |
| Vérification que l'utilisateur utilise réellement WhatsApp avant l'accès complet | `LinkStartRequest{risk_consent, consent_version}` + scan de l'appareil lié (`routers/devices.py`) | parcours web — liaison **refusée** sans consentement explicite et avec une version de consentement obsolète (HTTP 400 motivé) |
| Un signalement = numéro international + catégorie + date/heure + ≥ 1 preuve + description | `backend/app/schemas.py`, `backend/app/services/reports.py`, `services/evidence.py` | pytest — schemas, preuves obligatoires, preuve dupliquée refusée |
| Import CSV/Excel rejetant toute ligne sans catégorie ou sans preuve | `backend/app/services/csv_import.py` (alias français NFKD, ≤ 5 Mo, ≤ 50 lignes, 422 si 0 ligne valide) | parcours web — aperçu puis rejet d'un fichier incomplet |
| Détection automatique (mots-clés d'arnaque, liens de phishing, numéros déjà signalés) | `backend/app/services/spam.py` et moteur **hors ligne** `android/.../domain/ScamScanner.kt` | `ScamScannerTest` + tests backend de détection |
| Regroupement des signalements d'un même numéro et envoi d'un dossier au-delà de 3 signalements valides | `backend/app/services/dossiers.py`, `routers/campaigns.py`, onglet *Demande groupée* (`web/components/CampaignPanel.tsx`), `CampaignScreen.kt` | parcours web — 100 signalements demandés sont ramenés au nombre de comptes réellement contactés, plafond dur publié par le serveur, création refusée sans confirmation de l'avertissement, dossier PDF |
| Statut honnête : envoyé / reçu par Meta / suspendu / **non suspendu** | `backend/app/routers/webhooks.py`, champ `suspension_status` | parcours web — « suspension non confirmée (aucune invention) » |
| Base communautaire des numéros confirmés (≥ 3 signalements vérifiés) + blocage en un clic | `backend/app/routers/community.py`, `web/components/CommunityPanel.tsx`, `CommunityScreen.kt` | parcours web — liste, export, blocage groupé via la passerelle |
| Contestation examinée par un **humain**, retrait si les preuves sont fausses | `backend/app/routers/moderation.py` (contestations), `ModerationPanel.tsx`, `ModerationScreen.kt` | parcours web — file, téléchargement de preuve, décision motivée |
| Tableau de bord : mes numéros et leur état, compteur de suspensions, export de ma liste bloquée, alerte de contact malveillant | `backend/app/routers/dashboard.py`, `DashboardPanel.tsx`, `DashboardScreen.kt` | parcours web — exports CSV/PDF/XLSX et alertes |
| Anti-abus **non désactivable** : vérification e-mail/téléphone, 20 signalements/jour, 5/heure, 10 actions/minute, rejet automatique sans preuve, 2 avertissements puis bannissement définitif | `backend/app/services/limits.py`, `services/escalation.py`, `routers/auth.py` | `python scripts/anti_abuse_check.py` — **9/9** et pytest |
| Interdiction de signaler un numéro qui ne vous a jamais contacté (exception modérateurs de groupe) | `backend/app/services/reports.py` — `check_contact_proof` décide seul de la méthode ; la valeur envoyée par le client ne la remplace pas | pytest + parcours web : un compte neuf sans WhatsApp lié reçoit un refus explicite (« Liez d'abord votre WhatsApp »), y compris en déclarant une autre méthode |
| Avertissement d'accueil **et** avant envoi : fausse déclaration poursuivable, aucune garantie de bannissement | `web/app/page.tsx` (rendu serveur), `ReportsPanel.tsx`, `ReportNewScreen.kt`, `DisclaimerBanner` | parcours web — présence vérifiée dans le HTML rendu côté serveur |
| Suppression des données à la demande, aucune revente ni partage | `routers/auth.py` (`DELETE /me`), `services/audit.py` | pytest + parcours web |
| Chiffrement des données sensibles, aucun secret dans l'APK, jamais de message stocké sans consentement | AES-256-GCM + index aveugles HMAC (`core/crypto`), `SecureStore`, `BuildConfig` sans clé | pytest + **permissions réelles de l'APK livré** (8, aucune de stockage, contacts, SMS ou journal d'appels) |
| Journal d'audit complet pour réquisition judiciaire | `backend/app/services/audit.py`, `routers/moderation.py` | pytest + parcours web (journal d'audit) |
| Adresse du serveur configurable dans l'application (aucune adresse imposée) | `core/net/ServerUrl.kt` (validation), `ui/ServerSettings.kt` (écran + test réel de `/health`), `apps/…/SecureStore` (adresse mémorisée) | tests unitaires `ServerUrlTest` + `NetworkErrorsTest` exécutés en CI |
| APK de version livré | CI `.github/workflows/ci.yml` → artefact `signalpro-apk` | `app-release.apk` **2 198 548 octets**, 1 DEX, 4 566 classes, `minSdk 26`, `targetSdk 35`, signature valide |

## Architecture

| Composant | Rôle | Techno |
| --- | --- | --- |
| `backend/` | API, règles anti-abus, preuves chiffrées, modération, dossiers, webhooks Meta | FastAPI + SQLAlchemy (SQLite en dev, PostgreSQL en production) |
| `gateway/` | Passerelle **locale** qui parle WhatsApp depuis la connexion de l'utilisateur (`whatsapp-web.js`) | Node ≥ 20, ESM, HMAC |
| `android/` | Application réelle : signalements, preuves, détection hors ligne, alertes, modération | Kotlin + Jetpack Compose |
| `web/` | Interface web : signalements, import CSV/Excel, communauté, modération (mêmes règles que l'Android) | Next.js 14 + TypeScript |

Le backend ne parle **jamais** directement à WhatsApp : il dialogue avec la
passerelle locale de l'utilisateur via HMAC (`CONNECTOR_SHARED_SECRET`).
Pour les entreprises, la liaison utilise la **WhatsApp Business Platform** (API
officielle) : voir `docs/DEPLOIEMENT_WHATSAPP.md`.

## Démarrage rapide

```bash
# 1) Backend (API + tests)
cd backend
python -m pip install -r requirements.txt
ENV=test python -m pytest tests/ -q                 # 52 tests
ENV=test python scripts/anti_abuse_check.py         # interface de test anti-abus : 9 contrôles
cp .env.example .env
ENV=dev python scripts/seed_demo.py --reset         # comptes et données de démonstration
ENV=dev ALLOW_CONSOLE_VERIFICATION=true \
  DATABASE_URL="sqlite+pysqlite:///./signalpro-web-demo.db" \
  python -m uvicorn app.main:app --host 0.0.0.0 --port 8000

# 2) Interface web (autre terminal)
cd web
npm ci
API_PROXY_TARGET=http://127.0.0.1:8000 npm run dev   # http://localhost:3000
npm run check:contrats                               # catégories et routes alignées sur le backend
npm run check:parcours                               # parcours réel de bout en bout (jusqu'à 69 contrôles)

# 3) Passerelle locale WhatsApp (sur la machine de l'utilisateur, pas sur le serveur)
cd ../gateway
npm install
cp .env.example .env                                 # GATEWAY_SHARED_SECRET ≥ 24 caractères
npm start                                            # http://127.0.0.1:8787

# 4) Android (l'APK est produit par la CI : aucun SDK Android dans cet environnement)
cd ../android
gradle testDebugUnitTest assembleDebug               # ou ./gradlew, si vous ajoutez le wrapper
```

## Brancher l'application Android sur VOTRE serveur (indispensable)

L'application **n'a pas de serveur « par défaut »** : elle est auto-hébergeable. Un APK
livré sans adresse configurée ne peut rien envoyer — c'est le défaut corrigé ici : au lieu
d'un « réseau indisponible » trompeur, l'application dit maintenant quelle adresse elle a
contactée et pourquoi elle a échoué, et permet de la changer.

1. **Lancez l'API sur votre ordinateur**, en écoutant sur toutes les interfaces :
   ```bash
   cd backend
   ENV=dev ALLOW_CONSOLE_VERIFICATION=true python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
   ```
2. **Trouvez l'adresse IP de l'ordinateur sur le réseau local** :
   ```bash
   ip addr | grep "inet 192"      # Linux
   ipconfig                        # Windows : « Adresse IPv4 »
   ```
   Exemple : `192.168.1.20`. Le téléphone doit être sur le **même réseau Wi-Fi**.
3. **Autorisez le port 8000** dans le pare-feu de l'ordinateur (sans quoi la connexion
   sera refusée) :
   ```bash
   sudo ufw allow 8000/tcp         # Linux (ufw)
   ```
   Sous Windows : « Pare-feu Windows Defender » → Autoriser une application → port 8000.
4. **Dans l'application**, soit sur l'écran de connexion, soit dans *Réglages → Serveur
   SignalPro*, saisissez `http://192.168.1.20:8000/` puis **Tester la connexion** : le
   résultat est un vrai appel à `/health` (« Serveur joignable (HTTP 200) » ou la cause
   exacte de l'échec). Enregistrez ensuite l'adresse.
5. Hors réseau local, exposez l'API en **HTTPS** (reverse proxy) et utilisez
   `https://votre-domaine/`. L'application signale explicitement une connexion non
   chiffrée.

**Option la plus rapide pour un essai sur votre téléphone** : si une session Arena fait
tourner l'API (`uvicorn … --port 8000`), l'aperçu en direct expose cette même API en
HTTPS via un hôte public de la forme `https://8000-<identifiant>.e2b.app/`. Saisissez
cette adresse dans l'application et appuyez sur *Tester la connexion*. Elle ne vit que
le temps de la session : pour un usage durable, auto-hébergez l'API (points 1 à 4).

Messages d'erreur désormais affichés, avec l'adresse réellement contactée :

| Cause | Message |
| --- | --- |
| Aucun serveur configuré / domaine inexistant | « Serveur introuvable : `api.signalpro.example:443` n'existe pas… Vérifiez l'adresse dans Réglages → Serveur » |
| Serveur éteint ou port fermé | « Connexion refusée par `192.168.1.20:8000` : le serveur n'écoute pas sur ce port » |
| Réseau différent / route absente | « Aucune route vers `192.168.1.20:8000` : le téléphone et le serveur ne sont pas sur le même réseau » |
| Pare-feu silencieux | « Le serveur `192.168.1.20:8000` ne répond pas (délai dépassé) » |
| Certificat HTTPS invalide | « Connexion chiffrée refusée par … (certificat invalide, expiré ou non reconnu) » |

Aucun de ces messages ne prétend qu'une action a réussi : dans tous les cas, **rien n'a
été transmis**.

## Vérifications réellement exécutables

| Commande | Ce qu'elle prouve |
| --- | --- |
| `cd backend && ENV=test python -m pytest tests/ -q` | règles métier, sécurité, preuves, modération, quotas |
| `cd backend && ENV=test python scripts/anti_abuse_check.py` | les 9 protections anti-abus, sur une base temporaire (aucun serveur à lancer) |
| `cd web && npm run check:contrats` | les catégories de l'interface correspondent à l'énumération du serveur et **chaque route appelée existe** (`openapi.json`) |
| `cd web && npm run check:parcours` | parcours web complet : accueil + avertissements, inscription et vérification par e-mail d'un compte neuf, refus de signaler sans WhatsApp lié, signalement avec capture réelle, import CSV français, contestation, détection, liaison WhatsApp refusée sans consentement ou avec une version obsolète, fonctions présentes dans le paquet JavaScript livré, modération (décision, preuve téléchargée, dossier PDF) |
| `cd gateway && npm test` | signature HMAC, fenêtre temporelle, routes de la passerelle |
| CI `.github/workflows/ci.yml` | backend, passerelle, web (build + contrats + parcours) et Android (tests + APK), puis vérification du binaire livré : paquet, `minSdk`/`targetSdk`, nombre de DEX, permissions exactes et validité de la signature |

## Documentation

- `docs/DEPLOIEMENT_WHATSAPP.md` — API WhatsApp Business : prérequis, jetons,
  webhooks, limites, et ce qui reste impossible.
- `docs/GUIDE_UTILISATEUR.md` — parcours complet côté utilisateur.
- `docs/GUIDE_MODERATEUR.md` — relecture, contestations, dossiers, sanctions.
- `docs/LIMITES_RISQUES_CONFORMITE.md` — risques, conformité, données personnelles.
- `docs/TAILLE_APK.md` — taille réelle de l'APK, mesure et vérification du binaire
  livré, et pourquoi la taille ne peut pas être « gonflée » artificiellement.

## Tests

| Suite | Commande | État |
| --- | --- | --- |
| Backend | `cd backend && ENV=test python -m pytest tests/ -q` | **52 passés** |
| Anti-abus (interface de test) | `cd backend && ENV=test python scripts/anti_abuse_check.py` | **9/9 contrôles** |
| Passerelle | `cd gateway && node --test test/*.test.js` | **7 passés** |
| Web (contrats) | `cd web && npm run check:contrats` | **contrats respectés** |
| Web (parcours réel) | `cd web && npm run check:parcours` | **69/69 contrôles** sur un jeu neuf (les étapes qui exigent une file de modération non vide sont signalées « ignorées » si la file a déjà été traitée) |
| Android | `cd android && gradle testDebugUnitTest assembleDebug` | exécuté par la CI (aucun SDK local) : **28 tests sur 5 classes** — `Validation`, `ScamScanner`, contrats d'API, `ServerUrlTest`, `NetworkErrorsTest`. Le nombre exact est publié en annotation à chaque exécution |

## APK réellement produits

Les APK ne sont pas construits sur ce poste (aucun SDK Android disponible) : ils sont
produits par la CI à chaque envoi, puis joints à l'exécution sous l'artefact
`signalpro-apk`. Dernières mesures vérifiées (exécution `36031111770`, quatre tâches
vertes) :

| APK | Taille | Détails vérifiés |
| --- | --- | --- |
| `app-release.apk` | **2 198 548 octets** (2,10 Mio) | `com.signalpro.app` 1.0.0, `minSdk 26`, `targetSdk 35`, 1 DEX, **4 565 classes** conservées par R8, **8 permissions** (aucune de stockage, contacts, SMS ni journal d'appels), **signature valide** |
| `app-debug.apk` | **21 255 367 octets** (20,27 Mio) | `com.signalpro.app.debug` 1.0.0-debug, `targetSdk 35`, 12 DEX, 10 permissions (deux de stockage apportées par l'outillage de debug uniquement), signature valide |

Aucune clé n'est versionnée : si le dépôt ne contient pas de secrets `SIGNING_*`, la CI
génère une clé **jetable** pour que l'APK release soit installable. Cet APK jetable
n'est pas publiable sur le Play Store en l'état — pour une vraie publication, fournissez
vos propres `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` et
`SIGNING_KEY_PASSWORD` (voir `docs/TAILLE_APK.md`).

L'intégralité des tests est rejouée par `.github/workflows/ci.yml` (quatre tâches :
backend, passerelle, web, Android), qui produit les APK (debug et release), vérifie le
binaire obtenu puis publie sa taille, son empreinte SHA-256 et ses permissions.
