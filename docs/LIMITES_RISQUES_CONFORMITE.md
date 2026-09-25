# Limites, risques et conformité

## 1. La limite la plus importante : SignalPro ne peut pas bannir un compte

Aucune API WhatsApp — ni la WhatsApp Business Platform, ni un appareil lié — ne
permet de suspendre le compte d'un tiers à la demande. **Seul Meta examine les
signalements et décide.** Toute promesse contraire serait mensongère.

Conséquence assumée dans tout le produit :

- l'avertissement « ce service ne garantit pas le bannissement » est affiché sur
  l'accueil, dans le formulaire, avant chaque envoi et dans les récapitulatifs ;
- un statut « suspendu » n'est enregistré que s'il provient d'une **confirmation
  Meta** ou d'un **constat de modérateur avec preuve** ;
- une déclaration d'utilisateur (« ce numéro est suspendu ») est journalisée puis
  vérifiée : elle n'est jamais comptée automatiquement.

## 2. Textes de conformité (imposés dans l'interface)

1. **Faux signalement** : passible de poursuites judiciaires et du bannissement de
   votre compte WhatsApp.
2. **Aucune garantie de suspension** : seul Meta décide.
3. **Risque de l'appareil lié** : WhatsApp peut restreindre un compte utilisant un
   client non officiel, même dans le respect des limites.
4. **Blocage ≠ suspension** : bloquer protège votre compte, n'affecte pas le
   compte bloqué, et ne contribue à aucune sanction.
5. **Consentement obligatoire** avant toute transmission (case à cocher, versionné
   par `CONSENT_VERSION`).

Ces textes vivent dans `backend/app/constants.py` et sont exposés à l'application ;
ils ne doivent jamais être affaiblis.

## 3. Anti-abus — non désactivable

| Mesure | Valeur par défaut | Effet |
| --- | --- | --- |
| Vérification email + téléphone | obligatoire | empêche les comptes jetables |
| Signalements / heure / utilisateur | 5 | freine les campagnes |
| Signalements / jour / utilisateur | 20 | idem |
| Actions / minute / utilisateur | 10 | freine l'automatisation |
| Preuve obligatoire | ≥ 1 | aucun signalement sans preuve n'est transmis |
| Avertissements d'abus | 2 | 3ᵉ = bannissement définitif |
| Preuve de contact | requise | impossible de signaler un numéro qui ne vous a jamais écrit ; le serveur décide seul de la vérification (exception : comptes modérateurs, exemption tracée dans le journal d'audit) |
| Doublons (même auteur/cible/date) | refusés | évite l'acharnement |
| Preuves identiques réutilisées | refusées + abus | évite le copier-coller de « preuves » |

Toutes ces règles sont appliquées **côté serveur** : modifier l'application ne
permet pas de les contourner.

## 4. Risques identifiés et parades

| Risque | Gravité | Parade mise en œuvre |
| --- | --- | --- |
| Faux signalements en masse | élevée | vérification, quotas, preuve obligatoire, relecture humaine, sanctions |
| Harcèlement d'une personne via l'outil | élevée | preuve de contact obligatoire, contestation, retrait des signalements jugés faux |
| Restriction du compte de l'utilisateur (appareil lié) | moyenne | consentement explicite, plafonds bas, signalement natif désactivé par défaut, révocation possible |
| Fuite de conversations | élevée | aucune conversation lue/stockée par défaut ; analyse locale ; consentement explicite pour conserver un extrait |
| Fuite de jetons WhatsApp | élevée | jetons chiffrés (AES-256-GCM), jamais renvoyés, aucun secret dans l'APK |
| Fausse impression de « réussite » | moyenne | états honnêtes : un échec est enregistré comme échec, jamais comme « envoyé » |
| Utilisation pour doxxing | moyenne | numéros masqués par défaut, numéro complet réservé au propriétaire et aux modérateurs, exports journalisés |
| Abus des exports communautaires | moyenne | export authentifié, journalisé, usage personnel ; revente interdite par les CGU |

## 5. Données personnelles

- **Minimisation** : le serveur ne stocke pas le contenu des messages ; il stocke des
  **empreintes HMAC** des numéros (index de contacts) pour prouver qu'un contact a
  eu lieu sans conserver de carnet d'adresses en clair.
- **Chiffrement** : numéros et jetons chiffrés au repos (AES-256-GCM) ; mots de
  passe hachés (bcrypt) ; jeton d'appareil lié jamais stocké côté serveur.
- **Durées** : les preuves et signalements sont conservés le temps nécessaire au
  traitement et aux obligations légales ; les journaux d'audit sont conservés pour
  répondre aux réquisitions.
- **Droits** : export des données (CSV) et **suppression du compte** en libre
  service, avec purge du cache local et des preuves téléversées.
- **Aucune revente, aucun partage commercial**, jamais. Les seuls transferts ont
  lieu vers les canaux nécessaires au traitement (Meta, pour les transmissions que
  l'utilisateur déclenche).
- **Réquisitions judiciaires** : le journal d'audit complet (acteur, action,
  entité, IP, horodatage) est disponible pour répondre aux autorités.

## 6. Conditions d'utilisation des plateformes

- Les conditions WhatsApp/Meta s'appliquent ; l'outil **ne contourne aucune limite**
  et n'automatise pas d'envoi de masse.
- La passerelle `whatsapp-web.js` est un client non officiel : son usage est
  réservé aux **particuliers** qui l'acceptent explicitement, avec plafonds bas et
  sans aucune automatisation de masse.
- Les entreprises doivent passer par la **WhatsApp Business Platform** officielle
  (voir `docs/DEPLOIEMENT_WHATSAPP.md`).
- Aucun scraping, aucune création de comptes fictifs, aucune usurpation.

## 7. Limites techniques connues

- Sans appareil lié, **aucun signalement n'est accepté** : la preuve de contact ne peut
  pas être contournée par une déclaration. Concrètement, un utilisateur qui refuse la
  liaison WhatsApp ne peut pas utiliser le service pour signaler — c'est une limite
  d'usage assumée, préférée à un outil de signalement de masse (voir §3).
- Le signalement natif via appareil lié dépend de la version de WhatsApp et reste
  désactivé par défaut ; le parcours guidé est le mode fiable.
- Les suspensions ne sont connues que si Meta les notifie ou si un modérateur les
  constate : l'absence d'information n'est pas une preuve de suspension.
- L'analyse hors ligne dépend de la fraîcheur des signatures téléchargées.
- Le traitement d'un signalement prend le temps de la relecture humaine — c'est
  volontaire et non négociable.
