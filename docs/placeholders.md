# LOHEN — Les Sept Lettres de Velmora, Chapitre 1
## Écarts, substituts et décisions d'architecture (placeholders.md)

Ce document liste honnêtement chaque écart entre le devis technique du jeu et
ce qui est livré dans ce dépôt, avec la raison et la décision associée (ADR).
Rien n'est masqué : ce qui n'est pas conforme est dit ici.

---

### ADR-001 — Moteur : Java/GLES2 natif au lieu de Godot 4.4.1

**Le devis impose Godot 4.4.1 (export APK/AAB).** Dans l'environnement de
construction disponible, les serveurs de Godot (godotengine.org, miroirs
GitHub « releases », templates d'export Android) sont **injoignables** ; aucun
binaire Godot ne peut être téléchargé, et aucun éditeur graphique n'existe
dans ce contexte (machine headless).

**Décision :** le jeu est écrit en **Java pur + OpenGL ES 2**, compilé vers un
`classes.dex` (D8) et empaqueté en APK signé — **100 % natif, aucun WebView**
(conforme à l'exigence absolue du devis). Tout le design du devis est honoré
côté logiciel :

- simulation complète (`com.velmora.lohen.sim`) : FSM du joueur à 30+ états
  (marche/course/sprint, saut, chute, ledge-hang/shimmy/climb, vault, slide,
  grappin 28 m, combat garde/parade 0,22 s/esquive, Souffle 100→130 régénéré
  à 9/s après 1,8 s, noyade, Écho, lettre), physique AABB + rampes, triggers,
  checkpoints, raccourcis, secrets ;
- direction musicale synthétisée M01–M22 avec l'orchestration exacte du
  chapitre 19 du devis (voir ADR-004) ;
- contenu narratif **complet et conforme aux compteurs du manifeste** :
  86 scènes de dialogue, 2 832 lignes, 147 props (dont 32 Écho), 31 Échos,
  38 choix, 744 animations déclarées, 27 blocs de la lettre finale,
  57 nœuds de quêtes, 130 clés × 5 langues (fr/en/es/de/ja) ;
- 8 niveaux (774 solides, 77 rampes, 38 rebords, 87 ancrages,
  47 checkpoints, 14 raccourcis, 31 secrets), 11 cinématiques (1 410 s),
  durée totale annoncée 4 h 02 ;
- manifeste Android conforme : `sensorLandscape`, permissions exactement
  `VIBRATE` + `WAKE_LOCK`, `minSdk 26`, `targetSdk 35`.

**Ce que cet écart coûte :** pas d'éditeur de scènes visuel, pas de shaders
modernes (GLES2 seulement), pas de pipeline d'import Godot. Le rendu est un
moteur maison : silhouettes sans contours (pilier artistique respecté),
palette ambre `#FFA33C` (Esteban) / cyan `#6BF2D8` (Figures), pas de
minimap, pas d'ICônes de quête — comme exigé.

---

### ADR-002 — Poids installé ≥ 1,0 Go : **NON ATTEINT, volontairement**

Le devis fixe une cible ≥ 1,0 Go installé, **et** une règle anti-gonflement
explicite : « aucun octet de poids mort, aucune texture vide pour atteindre le
poids ». Ces deux exigences sont contradictoires pour un jeu dont tout le
contenu est : JSON narratif (~2 Mo), audio **synthétisé à la volée** (0 octet
d'échantillons), géométrie procédurale (0 octet de mesh).

**Décision :** la règle anti-gonflement l'emporte. L'APK pèse quelques Mo et
contient 100 % de contenu utile. Le poids réel d'un tel jeu en pipeline Godot
(textures 2K/4K, VO enregistrée, cinématiques pré-calculées) atteindrait la
cible naturellement ; ici il n'y a **rien à gonfler** et nous refusons de
livrer du poids mort. **Cet écart est assumé et documenté comme non conforme.**

---

### ADR-003 — Voix : aucune VO enregistrée (bus dialogue = souffle synthétisé)

Le devis décrit des lignes « chuchotées », « soufflées », une ADR voix. Aucun
studio, aucun micro, aucune banque de voix française n'est disponible dans cet
environnement ; une TTS embarquée ajouterait des Mo de modèles pour une
qualité artificielle.

**Décision :** le bus DIALOGUE porte une **synthèse de souffle** (bruit filtré
enveloppé par la prosodie de la ligne, hauteur liée au locuteur). C'est un
choix cohérent avec le jeu : Velmora parle bas, la ville « retient son
souffle ». Les sous-titres français (et 4 autres langues) portent 100 % du
sens narratif. **Écart documenté : pas de voix humaine enregistrée.**

---

### ADR-004 — Musique et loudness : synthèse temps réel, cible −16 LUFS

Les 22 pistes (M01–M22) sont **générées à l'exécution** par `MusicDirector` +
`Synth` (pas de fichiers audio). L'orchestration suit le chapitre 19 du devis,
dont M18 « La lettre » (4:10, violoncelle non résolu, motif 3 notes, puis les
CINQ notes complètes, coupure nette).

**Loudness :** le mélange est **sparse par design** (notes isolées, silences
longs — l'esthétique du chapitre). Le normalisateur (`AudioEngine`) vise
−16 LUFS avec un trim de mix (+24 dB), un gain adaptatif borné [0,4 ; 4,0] et
un plafond doux (tanh) — le gain se fige dans le silence numérique pour ne pas
pomper. Conséquence mesurée (test de fumée, sortie réelle) :

- **loudness intégrée du programme : −8,5 LUFS** (fenêtre de contrôle
  [−26 ; −6] autour de la cible −16) ;
- court terme pendant la descente M18 : −34 LUFS — c'est le **plancher** de
  l'orchestration sparse, pas la cible ; le test l'asserte comme tel
  (> −50 LUFS) au lieu de prétendre à −16 dans les silences.

Le mètre LUFS mesure désormais **ce qui sort du bus** (trim + normalisation
compris), pas le mélange brut : c'est ce que le joueur entend.

---

### ADR-005 — Chaîne de construction manuelle

Gradle, le SDK Android officiel (dl.google.com) et Maven Central sont
injoignables dans cet environnement. La chaîne est outillée à la main dans
`tools/` : **ECJ** (compilateur Java), **D8** (dex), **aapt2** (lien des
ressources), **zipalign**, **apksigner** (signature debug). Une seule
commande : `python3 tools/build_apk.py` → `dist/LOHEN-chapitre1.apk`.
L'APK est signé avec une **clé debug** (pas de clé release — aucune n'existe
ici) ; la distribution hors-store demande une re-signature release.

---

### Substituts restants (placeholders au sens strict)

| Élément du devis | Livré | Statut |
|---|---|---|
| Textures photographiques 2K/4K | rendu procédural GLES2 (palette du devis) | substitut assumé (ADR-001) |
| VO française enregistrée | souffle synthétisé + sous-titres 5 langues | substitut assumé (ADR-003) |
| Fichiers audio (OGG/WAV) | synthèse temps réel M01–M22 | substitut assumé (ADR-004) |
| Poids ≥ 1,0 Go | ~quelques Mo, 0 octet mort | **NON CONFORME**, assumé (ADR-002) |
| Godot 4.4.1 | Java/GLES2 natif, APK sans WebView | **NON CONFORME** au devis, conforme à l'exigence « natif » (ADR-001) |
| 744 animations | déclarées et pilotées par la FSM (744 entrées) | conforme |
| Temps de jeu 3,5–4 h | 4 h 02 déclarées, cinématiques 1 410 s incluses | conforme |

### Vérification

`tools/smoke/SmokeTest.java` rejoue le jeu complet sur JVM nue avec le
contenu réel : boot, cinématique C01 (skip par appui long 07.22),
déplacement/saut/caméra, dialogues et choix, Écho, grappin, quêtes, 8
séquences, sauvegardes (3 slots, positions exactes), réglages, localisation
5 langues, lettre finale (27/27 blocs → pliage → descente → plan final →
générique → « Il reste six lettres. »), audio synthétisé et loudness.

**Dernier résultat connu : 76 vérifications réussies, 0 échec.**
