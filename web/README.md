# SignalPro — interface web

Interface web réelle de SignalPro : signalements avec preuves, import CSV/Excel, base
communautaire, blocage, contestations, tableau de bord et console de modération.
Elle consomme la même API FastAPI que l'application Android (`backend/`).

## 1. Démarrage (5 minutes)

```bash
# 1) API (terminal 1)
cd backend
pip install -r requirements.txt
ENV=dev ALLOW_CONSOLE_VERIFICATION=true python3 scripts/seed_demo.py --reset
ENV=dev ALLOW_CONSOLE_VERIFICATION=true \
  DATABASE_URL="sqlite+pysqlite:///./signalpro-web-demo.db" \
  python3 -m uvicorn app.main:app --host 0.0.0.0 --port 8000

# 2) Interface web (terminal 2)
cd web
npm ci
API_PROXY_TARGET=http://127.0.0.1:8000 npm run dev     # http://localhost:3000
```

Comptes de démonstration créés par `seed_demo.py` (mots de passe de test, jamais en
production) :

| Compte | Email | Rôle |
| --- | --- | --- |
| Administrateur | `admin@signalpro-demo.com` | `admin` |
| Modératrice | `moderateur@signalpro-demo.com` | `moderator` |
| Utilisateur (appareil WhatsApp lié) | `alice@example.org` | `user` |
| Utilisateur | `bruno@example.org` | `user` |

## 2. Ce que fait réellement chaque écran

| Onglet | Fonctions réellement exécutées |
| --- | --- |
| **Connexion** | inscription (email + téléphone), code de vérification obligatoire, connexion, rafraîchissement de jeton automatique |
| **Tableau de bord** | quotas réels (signalements heure/jour, avertissements, seuil de bannissement), appareils liés, compte business, analyse des conversations contre la liste communautaire, déclaration honnête d'une suspension constatée, export CSV de mes signalements |
| **Signalements & import** | création (numéro international, catégorie, date/heure de réception, description, preuve de contact), dépôt de preuve (capture, export de conversation, identifiant de message, en-têtes), transmission via l'appareil lié ou mode guidé WhatsApp, import CSV/Excel avec analyse préalable et refus des lignes incomplètes, téléchargement du modèle, texte de signalement exportable |
| **Demande groupée** | numéro cible + nombre de signalements souhaité, vérification de ce qui est **réellement exécutable** avant toute création (comptes réellement contactés, plafond dur publié par le serveur), création, suivi (demandés / éligibles / mis en file / transmis) et arrêt d'une demande en cours |
| **Communauté** | liste des numéros confirmés malveillants (masqués), export authentifié des numéros complets, blocage en un clic depuis l'appareil lié (résultat par numéro), dépôt et suivi d'une contestation, statistiques honnêtes |
| **Modération** | file par statut avec preuves téléchargeables, décision motivée (valider / rejeter / rejeter comme abusif), avertissement de l'auteur, confirmation ou non d'une suspension, constitution et téléchargement du dossier PDF, traitement des contestations, journal d'audit |
| **Réglages** | profil, limites anti-abus, consentements horodatés, appareils liés (synchronisation, révocation), liaison WhatsApp Business Platform, changement de mot de passe, suppression définitive du compte et des données |

## 3. Architecture et sécurité

- **Aucun appel direct au backend depuis le navigateur** : le client utilise des URL
  relatives (`/proxy/...`) et Next.js relaie côté serveur vers `API_PROXY_TARGET`
  (`next.config.mjs`). Aucune adresse interne, aucun `127.0.0.1` n'est exposé au
  navigateur, et il n'y a donc pas de CORS à ouvrir.
- **Jetons** : `Authorization: Bearer` en mémoire du navigateur, rafraîchissement
  automatique sur 401 (`lib/api.ts`). Les téléchargements protégés passent par
  `lib/download.ts` (un simple lien `<a>` n'enverrait pas l'en-tête et recevrait 401).
- **Aucun secret serveur** dans le code du front ; la clé de stockage business est
  saisie par l'utilisateur et n'est jamais relue depuis l'API.
- **Rien n'est simulé** : chaque bouton appelle une route réelle et affiche le message
  exact renvoyé par le serveur. Les refus (quota, preuve manquante, passerelle absente,
  suspension non confirmée) sont affichés tels quels — jamais transformés en succès.

## 4. Vérifications automatiques

```bash
npm run typecheck          # TypeScript strict
npm run build              # build de production
npm run check:contrats     # catégories et routes alignées sur le backend (openapi.json)
npm run check:parcours     # parcours réel de bout en bout (API + web démarrés)
```

- `scripts/verifier_contrats.mjs` compare la liste des catégories de l'interface avec
  l'énumération du serveur et vérifie que **chaque route appelée existe réellement**
  (issue de `openapi.json`) ; si le backend n'est pas joignable, le contrôle est
  explicitement « ignoré », jamais compté comme réussi.
- `scripts/parcours.mjs` joue un parcours complet (inscription exclue) : page
  d'accueil et avertissements légaux, signalement + vraie capture PNG, import CSV à
  en-têtes français, contestation, détection, campagne, puis parcours modérateur
  (file, téléchargement de preuve, décision, dossier PDF). Le script est rejouable :
  les refus volontaires du serveur (quota horaire, doublon de contestation, file vide)
  sont signalés comme conformes.

## 5. Limites assumées

- La liaison WhatsApp (scan du QR) se fait depuis l'application **Android** : le
  navigateur ne manipule jamais les identifiants de session WhatsApp.
- Le blocage en un clic exige un appareil lié et connecté ; sans lui l'interface
  renvoie l'erreur du serveur et propose l'export de la liste pour un blocage manuel.
- **SignalPro ne peut pas suspendre un compte WhatsApp.** Seul Meta examine et décide.
  L'interface n'affiche « suspendu » que si Meta l'a signalé ou si un modérateur l'a
  constaté (`suspension_source`).
