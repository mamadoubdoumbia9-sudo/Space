# Guide du modérateur

La modération est **humaine et obligatoire** : aucun signalement n'est transmis
automatiquement, et aucune suspension n'est jamais déduite d'un simple nombre de
signalements. Le rôle du modérateur est de protéger les victimes **et** les
personnes injustement signalées.

## 1. Rôles

| Rôle | Droits |
| --- | --- |
| `moderator` | relecture des signalements, décisions, contestations, dossiers, suspension constatée |
| `admin` | tout ce qui précède + bannissement définitif d'un compte (`ban_user`), gestion des rôles |

Le bannissement d'un utilisateur est réservé à un administrateur : c'est une
décision lourde et irréversible, tracée dans le journal d'audit.

## 1 bis. Où modérer

Deux outils équivalents, mêmes routes d'API donc mêmes décisions et même journal :

- **Application Android** → onglet *Modération* ;
- **Interface web** → onglet *Modération* (`web/`, comptes `moderator`/`admin`).

La console web ajoute deux éléments utiles à la relecture : la **liste des preuves**
de chaque signalement (type, taille, empreinte, intégrité, nombre d'identifiants de
message) avec téléchargement pour contrôle humain, et le **journal d'audit** des
50 dernières actions. Une décision exige toujours un motif écrit d'au moins
5 caractères : il est conservé et opposable en cas de demande judiciaire.

## 2. File de relecture

API : `GET /api/v1/moderation/queue?status_filter=pending_verification`
(présente aussi dans l'application Android, onglet **Modération**).

Chaque élément contient : référence publique, numéro masqué, catégorie, résumé de
la description, date, nombre de preuves, et **signaux automatiques** (mots-clés
détectés, numéro déjà signalé, incohérences de date, etc.).

Contrôles recommandés avant décision :

1. **Preuve lisible** : capture montrant bien le numéro et le message, export
   cohérent, empreinte SHA-256 valide (l'application et l'API affichent le contrôle
   d'intégrité) ;
2. **Preuve de contact** : elle est automatique — le serveur vérifie que le numéro
   figure dans les conversations synchronisées de l'appareil lié de l'auteur. Aucune
   déclaration de l'utilisateur ne remplace ce contrôle (seuls les comptes modérateurs
   en sont dispensés, et cette exemption est tracée dans le journal d'audit) ;
3. **Cohérence** : catégorie ↔ contenu, date non future, description factuelle ;
4. **Doublons** : même auteur / même cible / même horodatage est refusé par le
   serveur ; les preuves réutilisées mot pour mot sont traitées comme abusives.

### Décisions possibles

| Décision | Effet |
| --- | --- |
| `verify` | le signalement devient vérifié, il peut être transmis par l'utilisateur |
| `reject` | rejeté (motif affiché à l'auteur) — pas de sanction |
| `reject_abusive` | rejeté **et** un avertissement d'abus est posé sur l'auteur |

L'auteur peut contester une décision : la contestation arrive dans la file des
appels. La sanction d'un auteur est automatique au seuil `MAX_ABUSIVE_STRIKES`
(par défaut **2** avertissements → bannissement définitif).

## 3. Escalade et dossiers

Un numéro atteint le seuil d'escalade après **3 signalements vérifiés** provenant
d'au moins **3 personnes distinctes**. L'API agrège alors un **dossier PDF**
(reportlab) qui contient : le numéro masqué, les catégories, la chronologie, les
empreintes de preuves et le journal des actions. Ce dossier sert :

- à la transmission vers les autorités compétentes (réquisition judiciaire) ;
- à justifier une suspension constatée.

API : `POST /api/v1/moderation/targets/{id}/dossier`,
`GET /api/v1/moderation/dossiers/{public_ref}/download`.

## 4. Suspension : jamais supposée

Le statut de suspension d'un numéro ne peut changer que de deux manières :

1. **confirmation Meta** via webhook officiel ;
2. **constat d'un modérateur** avec preuve fournie
   (`POST /api/v1/moderation/targets/{id}/suspension?confirmed=true&evidence=…`).

Sans preuve textuelle, l'appel est refusé. Les numéros non suspendus conservent le
statut `not_confirmed` : c'est une information honnête, pas un échec.

## 5. Contestations

API : `GET /api/v1/moderation/appeals`, puis
`POST /api/v1/moderation/appeals/{id}/decision` avec une décision parmi :

| Décision | Effet |
| --- | --- |
| `clear_target` | la contestation est **acceptée** : le numéro est retiré de la vitrine, les signalements en attente sont rejetés (« retiré après contestation acceptée ») |
| `verify` | la contestation est **rejetée** : le numéro reste listé comme confirmé malveillant |
| `reject` | contestation clôturée sans effet sur le numéro (cas d'un dossier vide ou hors périmètre) |

Le motif est obligatoire (5 caractères minimum) et journalisé. Si une contestation
révèle que des preuves étaient fausses : retirez le numéro **et** posez un
avertissement sur les auteurs concernés (`POST /api/v1/moderation/users/{id}/strike`),
en vérifiant chaque cas individuellement — un signalement peut être erroné de bonne
foi.

## 6. Sanctions utilisateur

| Décision | Effet |
| --- | --- |
| `suspend_user` | compte suspendu (connexion bloquée, données conservées) |
| `ban_user` | **admin uniquement** — bannissement définitif |
| `reinstate_user` | réintégration (contestation acceptée) |

API : `POST /api/v1/moderation/users/{id}/sanction` (corps `{decision, reason}`),
`POST /api/v1/moderation/users/{id}/strike?reason=…&report_id=…`.

## 7. Journal d'audit et réquisitions

Toute action de modération écrit une entrée d'audit : modérateur, rôle, action,
entité, IP, date, détail. L'API expose :

- `GET /api/v1/moderation/audit` — journal filtrable ;
- `GET /api/v1/moderation/stats` — volumétrie, délais, taux de rejet.

Règle de conservation : ne jamais supprimer une ligne d'audit, même après une
décision annulée ; la trace d'une annulation fait partie du dossier.

## 8. Bonnes pratiques

- **Ne jamais** vérifier « en gros » : une décision sans lecture de la preuve est
  une faute professionnelle ;
- répondre par un motif **factuel** (jamais « refusé » seul) : l'auteur doit
  comprendre ce qui manque ;
- en cas de doute sur une identité (usurpation de numéro d'entreprise), demander un
  justificatif de titularité avant toute décision ;
- ne jamais communiquer le numéro complet d'un tiers hors d'un dossier officiel ;
- signaler immédiatement toute suspicion de tentative d'abus coordonné (plusieurs
  comptes visant une même victime avec des textes similaires).
