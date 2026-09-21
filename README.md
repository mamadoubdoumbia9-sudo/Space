# LOHEN — Les Sept Lettres de Velmora
## Chapitre 1 : « La Ville qui retient son souffle »

Aventure narrative en vue troisième personne, **100 % native Android**
(Java + OpenGL ES 2 — **aucun WebView**), entièrement en français
(+ anglais, espagnol, allemand, japonais).

> Esteban Lohen traverse Velmora pétrifiée, de la jetée de verre jusqu'à la
> chambre de la lettre. Il écoute les objets — les **Échos** —, retient son
> souffle, grimpe, s'agrippe, et finit par lire la première des sept lettres.
> À la fin du générique : **« Il reste six lettres. »**

---

## 📱 Télécharger et installer l'APK

**`dist/LOHEN-chapitre1.apk`** (≈ 1,8 Mo, signé, vérifié `apksigner`)

1. Copiez le fichier sur le téléphone (USB, nuage, ou depuis GitHub).
2. Ouvrez-le ; autorisez « Installer des applications inconnues » si demandé.
3. Lancez **LOHEN** — le jeu démarre en paysage (capteur), Android 8.0+
   (minSdk 26, targetSdk 35). Permissions : vibration + maintien d'écran, rien d'autre.

## 🎮 Contrôles

| Action | Tactile | Manette (mapping Xbox, 08.22) |
|---|---|---|
| Se déplacer | joystick virtuel (zone gauche) | stick gauche |
| Caméra | glisser à droite | stick droit (cadrage : clic stick droit) |
| Saut / confirmer | bouton **A** | **A** |
| Interagir, parler, Écouter un Écho | bouton **C** | **X** |
| Grappin (28 m) | bouton **B** | **RT** |
| Garde / parade (fenêtre 0,22 s) | bouton **D** (apparaît en combat, remplace C) | **LT** / **LB** |
| Esquive | — | **B** |
| Sprint | — | **RB** |
| Pause / Journal | — | Start / Select |
| Cinématiques | **appui long** n'importe où (1,5 s) pour passer | maintien Start |
| Lettre finale | tap au centre = tourner la page / confirmer ; glisser = faire défiler le papier | **A** |

Sous l'eau, le **Souffle** descend ; hors de l'eau il se régénère à 9/s après
1,8 s, et son maximum grandit de 100 jusqu'à 130 au fil du chapitre.

Pas de minimap, pas de contours, pas d'icônes de quête : la ville guide par la
lumière — **ambre `#FFA33C`** pour Esteban, **cyan `#6BF2D8`** pour les Figures.

## 📦 Contenu (conforme au manifeste du devis)

- **8 séquences**, 4 h 02 de jeu déclarées, **11 cinématiques** (1 410 s)
- **86 scènes** de dialogue, **2 832 lignes**, **38 choix**, 57 nœuds de quêtes
- **147 props narratifs** dont **32 Échos** jouables, **31 Échos** au total
- **8 niveaux** : 774 solides, 77 rampes, 38 rebords, 87 ancrages,
  47 checkpoints, 14 raccourcis, 31 secrets
- **27 blocs** pour la lettre finale canonique ; **744 animations** déclarées
- Musique **synthétisée en temps réel** : 22 pistes (M01–M22), orchestration
  du chapitre 19 du devis, loudness intégrée contrôlée autour de −16 LUFS
- Sauvegardes : 3 slots, positions et progression exactes

## 🏗️ Construire et tester

Chaîne manuelle (ni Gradle ni SDK officiel nécessaires) : ECJ → D8 → aapt2 →
zipalign → apksigner, outillée dans `tools/`.

```bash
python3 tools/build_apk.py          # → dist/LOHEN-chapitre1.apk (signé)
```

Test de fumée complet de la simulation sur JVM nue (boot, cinématique,
déplacement, saut, dialogues, choix, Échos, grappin, 8 séquences, sauvegardes,
réglages, 5 langues, lettre finale jusqu'au générique, audio) :

```bash
# compilation + exécution décrites en tête de tools/smoke/SmokeTest.java
# dernier résultat connu : 76 vérifications réussies, 0 échec
```

## 🗂️ Structure

```
android/          application native (manifeste, res, src Java : GL + sim)
  assets/content/ les 37 JSON du jeu (générés depuis content/)
content/          source du contenu narratif (dialogues, niveaux, quêtes…)
docs/             placeholders.md — ADR et écarts assumés (à lire !)
tools/            build_apk.py, make_icon.py, smoke/SmokeTest.java
dist/             APK livré
```

## ⚖️ Écarts au devis — tout est documenté

Le devis imposait **Godot 4.4.1** et **≥ 1,0 Go installé**. Godot est
injoignable dans cet environnement de construction → le jeu est écrit en
Java/GLES2 natif (ADR-001). La cible de poids n'est **pas atteinte,
volontairement** : la règle anti-gonflement du devis interdit le poids mort,
et ce jeu (JSON + synthèse audio + géométrie procédurale) ne contient que des
octets utiles (ADR-002). Voix = souffle synthétisé, pas de VO enregistrée
(ADR-003). Détails complets, musique et loudness : **[`docs/placeholders.md`](docs/placeholders.md)**.
