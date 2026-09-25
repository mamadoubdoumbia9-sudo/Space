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
npm run check:parcours                               # parcours réel de bout en bout (43 contrôles)

# 3) Passerelle locale WhatsApp (sur la machine de l'utilisateur, pas sur le serveur)
cd ../gateway
npm install
cp .env.example .env                                 # GATEWAY_SHARED_SECRET ≥ 24 caractères
npm start                                            # http://127.0.0.1:8787

# 4) Android (l'APK est produit par la CI : aucun SDK Android dans cet environnement)
cd ../android
gradle testDebugUnitTest assembleDebug               # ou ./gradlew, si vous ajoutez le wrapper
```

## Vérifications réellement exécutables

| Commande | Ce qu'elle prouve |
| --- | --- |
| `cd backend && ENV=test python -m pytest tests/ -q` | règles métier, sécurité, preuves, modération, quotas |
| `cd backend && ENV=test python scripts/anti_abuse_check.py` | les 9 protections anti-abus, sur une base temporaire (aucun serveur à lancer) |
| `cd web && npm run check:contrats` | les catégories de l'interface correspondent à l'énumération du serveur et **chaque route appelée existe** (`openapi.json`) |
| `cd web && npm run check:parcours` | parcours web complet : accueil + avertissements, signalement avec capture réelle, import CSV français, contestation, détection, modération (décision, preuve téléchargée, dossier PDF) |
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
| Web (parcours réel) | `cd web && npm run check:parcours` | **43/43 contrôles** |
| Android | `cd android && gradle testDebugUnitTest assembleDebug` | exécuté par la CI (aucun SDK local) : 3 suites unitaires passées |

## APK réellement produits

Les APK ne sont pas construits sur ce poste (aucun SDK Android disponible) : ils sont
produits par la CI à chaque envoi, puis joints à l'exécution sous l'artefact
`signalpro-apk`. Dernières mesures vérifiées (exécution `36031111770`, quatre tâches
vertes) :

| APK | Taille | Détails vérifiés |
| --- | --- | --- |
| `app-release.apk` | **2 198 548 octets** (2,10 Mio) | `com.signalpro.app` 1.0.0, `targetSdk 35`, 1 DEX, **4 566 classes** conservées par R8, **8 permissions** (aucune de stockage, contacts, SMS ni journal d'appels), **signature valide** |
| `app-debug.apk` | **21 238 920 octets** (20,25 Mio) | `com.signalpro.app.debug` 1.0.0-debug, `targetSdk 35`, 12 DEX, 31 684 classes, 10 permissions (deux de stockage apportées par l'outillage de debug uniquement), signature valide |

Aucune clé n'est versionnée : si le dépôt ne contient pas de secrets `SIGNING_*`, la CI
génère une clé **jetable** pour que l'APK release soit installable. Cet APK jetable
n'est pas publiable sur le Play Store en l'état — pour une vraie publication, fournissez
vos propres `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` et
`SIGNING_KEY_PASSWORD` (voir `docs/TAILLE_APK.md`).

L'intégralité des tests est rejouée par `.github/workflows/ci.yml` (quatre tâches :
backend, passerelle, web, Android), qui produit les APK (debug et release), vérifie le
binaire obtenu puis publie sa taille, son empreinte SHA-256 et ses permissions.
