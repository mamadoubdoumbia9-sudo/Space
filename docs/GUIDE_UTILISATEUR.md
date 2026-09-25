# Guide utilisateur

## 1. Créer un compte et être vérifié

1. **Créer un compte** : email + numéro de téléphone au format international
   (ex. `+223 61 23 45 67`) + mot de passe (10 caractères minimum).
2. **Vérifier** le code reçu par email ou SMS. Tant que la vérification n'est pas
   faite, l'application n'envoie aucun signalement : c'est la première barrière
   anti-abus.
3. Lire et accepter l'avertissement : **un faux signalement est passible de
   poursuites judiciaires et entraîne le bannissement de votre compte SignalPro**

Une fois vérifié, votre tableau de bord affiche vos quotas en temps réel :
`5 signalements/heure`, `20/jour`, `10 actions/minute`, et vos avertissements (3 =
bannissement définitif).

## 1 bis. Version web (navigateur)

Tout ce qui suit existe aussi dans le navigateur (`web/`, `npm run dev`) : mêmes règles,
mêmes quotas, mêmes refus. Seule différence assumée : la **liaison WhatsApp par QR
code** se fait depuis l'application Android, car les identifiants de session WhatsApp
ne doivent jamais transiter par un navigateur. Dans la version web, les onglets sont
*Tableau de bord*, *Signalements & import*, *Demande groupée*, *Communauté*, *Modération*
(si votre compte est modérateur) et *Réglages*. Sans appareil lié, la transmission utilise le mode
guidé (étapes affichées pour signaler depuis WhatsApp) et le blocage en un clic
renvoie une erreur explicite au lieu d'un faux succès.

## 2. Connecter votre WhatsApp (facultatif, mais recommandé)

Deux possibilités, présentées honnêtement dans l'application :

- **Appareil lié (particulier)** : vous scannez un QR code dans WhatsApp →
  Appareils connectés. SignalPro peut alors vérifier automatiquement qu'un numéro
  vous a réellement écrit. Risque : même en respectant les limites, WhatsApp peut
  restreindre un appareil lié non officiel — le consentement est explicite et
  révocable à tout moment.
- **Compte professionnel** : l'entreprise connecte son compte WhatsApp Business
  Platform (API officielle). Voir `docs/DEPLOIEMENT_WHATSAPP.md`.

Sans appareil lié, vous pouvez **quand même** signaler : vous joignez alors une
capture d'écran de la conversation, contrôlée par un modérateur humain.

Ce que la connexion ne permet jamais : lire ou stocker le contenu de vos messages,
et « faire bannir » quelqu'un.

## 3. Signaler un numéro

Chaque signalement exige **cinq éléments** :

| Élément | Détail |
| --- | --- |
| Numéro complet | format international, jamais un numéro tronqué |
| Catégorie | spam, arnaque financière, usurpation, harcèlement, haine, contenu illégal, autre |
| Date et heure | de réception du message (pas de date future) |
| Preuve | capture d'écran, export de conversation, ou identifiants de message — **au moins une** |
| Description | 10 caractères minimum, faits précis |

Étapes :

1. **Nouveau signalement** → numéro, catégorie, date, description ;
2. **Ajouter les preuves** depuis votre téléphone (sélecteur système, aucun accès au
   stockage global) ;
3. éventuellement **analyser** l'extrait : l'analyse locale (hors ligne) est le
   réglage par défaut, l'analyse serveur demande votre accord explicite ;
4. **cocher la certification** puis **Enregistrer le signalement**.

Résultat : le signalement est créé, les preuves sont téléversées et leur empreinte
SHA-256 est vérifiée. **Aucun envoi à WhatsApp n'a encore lieu** : un modérateur
relit d'abord.

### Import en masse (CSV / Excel)

Utile pour un modérateur de groupe ou une association : fichier `.csv` (ou `.xlsx`)
avec les colonnes *numéro, catégorie, date, preuve*. Les lignes sans catégorie **ou**
sans preuve sont **rejetées** et jamais transmises ; plafond 5 Mo et 50 créations
par import, quotas journaliers en plus.

## 4. Transmission à WhatsApp

Selon l'état du signalement :

- **Preuve manquante** → ajoutez une preuve ; rien n'est transmis ;
- **En relecture** → un modérateur vérifie ; vous recevez une notification à la
  décision ;
- **Vérifié** → trois canaux possibles :
  1. **Depuis votre compte (appareil lié)** — si la passerelle expose l'action ;
     sinon l'application bascule automatiquement vers le parcours guidé ;
  2. **Parcours guidé** — étapes exactes à reproduire dans WhatsApp, puis
     « J'ai signalé dans WhatsApp » (déclaration vérifiée, jamais comptée
     automatiquement) ;
  3. **Compte professionnel (Cloud API)** — pour les entreprises uniquement.

Vous devez cocher la confirmation avant chaque transmission. **Aucun statut
« envoyé » n'est affiché sans confirmation réelle** ; un échec est enregistré comme
un échec.

## 5. Suivre l'état de vos signalements

Le détail affiche : statut, preuves (avec empreinte et contrôle d'intégrité),
historique des transmissions (tentatives, résultat exact), motif de décision, et
permet de **générer un récapitulatif** (utile en cas de dépôt de plainte).

États possibles : `preuve manquante`, `en relecture`, `vérifié`, `transmis`,
`reçu par Meta`, `suspendu confirmé`, `non suspendu`, `rejeté`, `abusif`.

**Un numéro signalé n'est pas nécessairement suspendu** : Meta décide, et la
suspension n'est enregistrée que si Meta la signale ou si un modérateur la constate
avec preuve. Vous pouvez aussi **déclarer** une suspension constatée : la
déclaration est journalisée puis vérifiée, jamais comptée automatiquement.

## 6. Se protéger : liste communautaire et blocage

- **Numéros malveillants confirmés** : un numéro y entre après **3 signalements
  vérifiés** venant de personnes distinctes.
- **Blocage en masse** : en un geste, ces numéros sont bloqués sur votre compte
  (nécessite un appareil lié). Le texte affiché le rappelle : bloquer protège
  **votre** compte, cela ne suspend personne.
- **Alertes** : si un numéro de cette liste a une conversation sur votre compte,
  vous êtes notifié — par empreinte uniquement, jamais en comparant vos contacts en
  clair sur le serveur.
- **Contestation** : si votre numéro est listé, vous pouvez le contester. Un
  modérateur humain examine chaque recours ; si les preuves étaient fausses, les
  signalements sont retirés et leurs auteurs sanctionnés.

## 7. Détection de spam

- Analyse **hors ligne** par défaut : les signatures (mots-clés, domaines,
  expressions) sont téléchargées puis appliquées sur votre téléphone, sans envoi.
- Analyse serveur uniquement si vous l'autorisez explicitement ; aucun texte n'est
  stocké (seuls le score, la catégorie et le type de motif sont conservés).
- Une détection peut être **transformée en signalement** : le numéro doit alors être
  saisi et toutes les règles s'appliquent.

## 8. Vos données

- Aucune donnée n'est vendue ni partagée ; les listes de numéros ne sortent jamais
  du périmètre de traitement décrit dans la politique de confidentialité.
- Vous pouvez **exporter** vos signalements (CSV) et **supprimer votre compte** à
  tout moment : compte, signalements, preuves et cache local sont effacés.
- Les journaux d'actions (qui a fait quoi, quand, depuis quelle IP) sont conservés
  pour pouvoir répondre aux réquisitions judiciaires.
- Les preuves sont stockées chiffrées (AES-256-GCM) ; leur empreinte SHA-256 permet
  de démontrer qu'elles n'ont pas été modifiées.
