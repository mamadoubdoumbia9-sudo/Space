# Rapport de Transparence & Architecture Connectivité (CONNECTIVITY_REPORT.md)

Ce document officiel détaille avec exactitude la séparation entre les fonctionnalités connectées à Internet via HTTPS/TLS, celles qui s'appuient sur un mécanisme officiel externe (WhatsApp / E-mail), et les garanties d'intégrité anti-abus du système **WhAlert**.

---

## 1. Règle Fondamentale de Vérité Technique

> **Aucune API WhatsApp n'est inventée.**  
> WhatsApp ne met à disposition aucune API publique ouverte permettant à des applications tierces de soumettre directement des signalements d'abus ou d'interroger le statut interne de modération d'un compte (bannissement, suspension ou avertissement).  
> **Par conséquent, l'application WhAlert n'affichera JAMAIS : « Compte suspendu » ou « Compte banni » sans une décision officielle publiquement vérifiable fournie par le canal officiel.**

---

## 2. Cartographie Précise des Fonctionnalités

| Fonctionnalité | Connectée à Internet (HTTPS) | Dépendance externe | Mécanisme Réel Utilisé | Statut & Transparence |
|---|---|---|---|---|
| **Validation du numéro cible** | Non (locale E.164) | Standard UIT-T | Algorithme et tables regex / libphonenumber | Valide la syntaxe internationale, sans prétendre que le compte existe sur WhatsApp |
| **Génération du texte formel** | Non (moteur interne) | Aucun | Moteur factuel sans invective ni diffamation | Ne promet aucun bannissement automatique |
| **Transmission In-App WhatsApp** | Oui (données/Wi-Fi) | Application officielle WhatsApp | Intent Android officiel `https://api.whatsapp.com/send?phone=...` | Redirige l'utilisateur vers la messagerie pour effectuer le signalement in-app officiel |
| **Transmission Support Documentaire** | Oui (données/Wi-Fi) | Client de messagerie système | Intent standard `mailto:android_web@support.whatsapp.com` | Remet le dossier au client e-mail officiel configuré par l'utilisateur |
| **Test de liaison Internet** | Oui (HTTPS/TLS) | Cloudflare CDN Trace (`/cdn-cgi/trace`) | OkHttp3 avec TLS 1.3 | Teste la connectivité réelle en Wi-Fi / 4G / 5G |
| **Console Privée Administrateur** | Non (contrôle local) | Keystore Android / SHA-256 | Hachage sécurisé SHA-256 | Permet l'audit des quotas et journaux techniques, sans contournement de WhatsApp |
| **Vérification du statut WhatsApp** | Non disponible publiquement | Modération interne WhatsApp | Mention explicite affichée | Affiche : *« WhatsApp ne fournit pas de confirmation publique permettant à cette application de vérifier directement si ce compte a été suspendu. »* |

---

## 3. Dispositif Anti-Abus & Quotas de Sécurité

1. **Règle des signalements unitaires :**  
   L'application interdit formellement d'envoyer plusieurs signalements pour un même événement. Si l'utilisateur saisit une quantité > 1, le message suivant est immédiatement affiché :  
   *« Les signalements doivent correspondre à des événements ou expériences réels. Cette application ne peut pas multiplier artificiellement un même signalement. »*

2. **Limite de sécurité de l'application :**  
   Par défaut, un utilisateur est limité à **3 dossiers authentiques maximum par période glissante de 24 heures**.  
   *Cette limite est clairement déclarée comme une règle interne de l'application et n'est pas présentée comme une règle officielle de WhatsApp.*

3. **Absence d'automatisation abusive :**  
   Le code ne contient aucune boucle d'envoi automatisée, aucun générateur d'identités jetables, aucun contournement de CAPTCHA et aucune interaction avec des endpoints privés non documentés.

---

## 4. Sécurité des Données Personnelles (RGPD & Confidentialité)

- **Masquage par défaut :** Dans la liste des historiques, les numéros de téléphone sont masqués (ex: `+33 •••••• 678`) afin d'éviter les fuites visuelles.
- **Stockage chiffré :** Les données sensibles sauvegardées sur l'appareil sont stockées via `EncryptedSharedPreferences` adossé à l'Android Keystore matériel (chiffrement AES-256 GCM).
- **Consentement e-mail :** Aucun e-mail n'est envoyé sans validation explicite préalable via l'écran des paramètres.
- **Chiffrement réseau obligatoire :** Toute communication utilise TLS 1.3 avec `cleartextTrafficPermitted="false"` spécifié dans le fichier de configuration réseau Android.
