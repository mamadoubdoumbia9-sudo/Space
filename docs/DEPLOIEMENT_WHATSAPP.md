# Déploiement WhatsApp — guide complet

Ce document décrit **les deux seuls modes de connexion** que SignalPro utilise, ce
qu'ils permettent réellement, et ce qu'ils ne permettent pas.

> Règle fondatrice : **aucune API WhatsApp n'offre de bouton « bannir un compte ».**
> SignalPro n'invente aucun point d'entrée de ce type. Il aide à documenter, à
> transmettre un signalement par les canaux autorisés et à en suivre le traitement.
> Toute formulation garantissant une suspension serait mensongère et est interdite
> dans l'interface (voir `backend/app/constants.py`, textes de conformité).

## 1. Entreprises — WhatsApp Business Platform (API officielle)

### 1.1 Prérequis

1. **Compte Meta Business** vérifié (justificatifs d'entreprise) ;
2. **Application Meta for Developers** de type *Business* ;
3. **WhatsApp Business Account (WABA)** rattaché à cette application ;
4. un **numéro dédié** pour l'API (il ne peut plus être utilisé dans l'application
   WhatsApp grand public) ;
5. un **utilisateur système** avec un jeton de longue durée.

### 1.2 Permissions nécessaires

| Permission | Usage dans SignalPro |
| --- | --- |
| `whatsapp_business_messaging` | envoi de messages, envoi dans le parcours guidé entreprise, lecture des statuts |
| `whatsapp_business_management` | lecture de la configuration du numéro, des modèles (templates) et des événements |

Aucune autre permission n'est demandée : pas d'accès aux contacts du téléphone, pas
d'accès aux conversations personnelles.

### 1.3 Configuration côté serveur SignalPro

```dotenv
# backend/.env
CLOUD_API_TOKEN=EAAG...            # jeton système, permissions messaging
CLOUD_API_PHONE_NUMBER_ID=123456789012345
CLOUD_API_WABA_ID=998877665544
CLOUD_API_GRAPH_VERSION=v21.0
CLOUD_API_API_BASE=https://graph.facebook.com
```

Ces valeurs ne sont **jamais** exposées à l'application mobile : l'APK ne contient
aucun secret, uniquement l'URL de l'API SignalPro.

Ensuite, dans l'application (Réglages → Compte professionnel), l'entreprise saisit
son `WABA ID`, son `phone_number_id` et son jeton : le serveur vérifie le jeton
auprès de Meta, le **chiffre** (AES-256-GCM) et ne le renvoie jamais.

### 1.4 Webhooks

```dotenv
META_VERIFY_TOKEN=<chaîne aléatoire partagée avec Meta>
META_APP_SECRET=<secret d'application Meta>
```

Dans la console Meta :

1. *Webhooks → WhatsApp Business Account* : URL
   `https://votre-domaine/api/v1/webhooks/whatsapp`, jeton de vérification = `META_VERIFY_TOKEN` ;
2. champs à souscrire : `messages` (statuts et messages entrants) ;
3. SignalPro répond au défi de vérification (`hub.mode`, `hub.challenge`,
   `hub.verify_token`) et **vérifie la signature** `X-Hub-Signature-256` de chaque
   événement avant de l'exploiter.

Les événements alimentent le statut des transmissions (livré, lu, échoué) et le
suivi des signalements. Ils **ne** créent jamais de suspension automatique.

### 1.5 Limites et coûts à connaître

- fenêtre de service de 24 h, messages hors fenêtre = templates approuvés ;
- quotas d'envoi par numéro, dépendants du niveau de qualité du compte ;
- facturation par conversation (voir la grille Meta en vigueur) ;
- délais de vérification d'entreprise pouvant atteindre plusieurs jours.

### 1.6 Ce que l'API officielle **ne** permet pas

- ❌ signaler un numéro tiers à Meta au nom d'un utilisateur ;
- ❌ demander ou provoquer la suspension d'un compte ;
- ❌ lire les conversations d'un utilisateur (l'API ne voit que les messages
  échangés **avec le numéro professionnel**) ;
- ❌ vérifier qu'un numéro a contacté un utilisateur **personnel**.

C'est pourquoi le parcours entreprise sert à **prouver les échanges avec le numéro
professionnel** et à suivre les transmissions, tandis que le parcours particulier
(§2) sert à prouver les conversations personnelles.

## 2. Particuliers — appareil lié WhatsApp Web (passerelle locale)

### 2.1 Pourquoi une passerelle locale

La session WhatsApp d'un utilisateur **ne doit jamais** transiter ni résider sur les
serveurs SignalPro : faire tourner la session d'un tiers sur son infrastructure est
contraire à notre politique de sécurité et à celle de WhatsApp. La passerelle est
donc installée **chez l'utilisateur** et le backend ne lui parle qu'en HTTP signé.
`CONNECTOR_REMOTE_MODE=1` est refusé en production par le backend.

### 2.2 Installation

```bash
git clone <ce dépôt> && cd gateway
npm install                       # Node ≥ 20
cp .env.example .env              # renseigner GATEWAY_SHARED_SECRET (≥ 24 caractères)
npm start                         # écoute sur 127.0.0.1:8787
```

Puis, côté serveur :

```dotenv
CONNECTOR_BASE_URL=http://127.0.0.1:8787
CONNECTOR_SHARED_SECRET=<le même secret que la passerelle>
```

### 2.3 Sécurité de la passerelle

- toutes les routes sont authentifiées en **HMAC-SHA256** :
  `signature = HMAC(secret, "METHOD\nPATH\nTS\n" + corps)`, horodatage accepté sur
  ±300 s (anti-rejeu) ;
- écoute par défaut sur `127.0.0.1` uniquement ;
- aucune réponse de la passerelle ne contient de contenu de message ;
- la session WhatsApp (`LocalAuth`) est stockée localement, jamais transmise ;
- la passerelle refuse de démarrer si le secret est trop court.

### 2.4 Risques — à afficher à l'utilisateur (consentement obligatoire)

`whatsapp-web.js` s'appuie sur le protocole multi-appareils, **sans contrat
commercial avec WhatsApp**. Même en respectant scrupuleusement les limites, un
compte peut être restreint. L'application impose donc :

1. une case de consentement explicite, versionnée (`CONSENT_VERSION`) ;
2. des plafonds bas côté passerelle (10 actions/minute, 5/heure, 20/jour) ;
3. l'action de signalement natif **désactivée par défaut**
   (`GATEWAY_ENABLE_NATIVE_REPORT=false`) : le parcours guidé manuel est proposé,
   avec les étapes exactes à reproduire dans WhatsApp ;
4. une révocation possible à tout moment (dans l'application **et** dans WhatsApp →
   Appareils connectés).

### 2.5 Vérification « ce numéro m'a bien contacté »

Sans preuve de contact, un signalement n'a pas de sens et sert le harcèlement :
le serveur maintient un **index d'empreintes** (HMAC) des conversations remontées
par la passerelle. Un signalement n'est accepté que si l'empreinte du numéro
signalé y figure — exception prévue uniquement pour les modérateurs de groupe.

## 3. Vérifications avant mise en production

```bash
# 1. L'API répond et la passerelle est diagnostiquée honnêtement
curl -s https://votre-domaine/api/v1/devices/gateway/status | jq

# 2. Le webhook Meta est vérifié (le challenge doit être renvoyé tel quel)
curl -s "https://votre-domaine/api/v1/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token=$META_VERIFY_TOKEN&hub.challenge=1234"

# 3. La connexion Cloud API est valide (jeton + numéro)
curl -s -H "Authorization: Bearer $JWT_UTILISATEUR" https://votre-domaine/api/v1/devices/business/status | jq

# 4. Les tests serveur passent
cd backend && ENV=test python -m pytest tests/ -q     # 51 passés attendus
```

## 4. Rappel de conformité

- Les conditions d'utilisation de WhatsApp et les politiques Meta s'appliquent :
  aucun contournement de limite, aucune automatisation de masse, aucune création de
  comptes fictifs.
- Un signalement doit être **fondé** : c'est une déclaration dont l'auteur est
  responsable. Un faux signalement expose son auteur à des poursuites et au
  bannissement de SignalPro (au-delà de 2 signalements abusifs confirmés).
- Les données transmises à Meta sont celles du signalement que l'utilisateur
  déclenche lui-même. SignalPro ne vend ni ne partage aucune liste de numéros.
