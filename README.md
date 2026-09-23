# La Cartographie des Absents — Chapitre 1 : La Marée Basse

Jeu d'aventure narrative pour Android (tactile, portrait et paysage), réalisé d'après le document de conception
`GDD_LA_CARTOGRAPHIE_DES_ABSENTS.txt` (source de vérité) et `LETTRE_ESTEBAN_A_LOHEN.txt` (texte intégral de la lettre).

Lohen, apprentie cartographe, suit pendant neuf jours les traces d'Esteban sur une presqu'île qui mesure le temps en marées :
18 zones, 40 tableaux illustrés, 90 scènes de dialogue arborées, 16 énigmes, 12 secrets, 24 pages, 12 échos, 43 cloches,
40 bornes, une lettre en onze plis et quatre fins de chapitre. Pas de combat, pas d'ennemis, pas de timer, pas de mort,
pas de monétisation : les mécaniques sont l'exploration, le dialogue à choix (encre or = définitif), les énigmes
« justes, prouvables, chantantes », les faveurs (économie sociale), les traits invisibles (douceur, courage, ruse, humeur),
les liens (0→5 par personnage), la clarté et le compagnon Filou.

## Arborescence (GDD 12.2 — correspondance avec un projet moteur classique en fin de fichier)

```
core/src/…/core/          moteur pur Kotlin, sans dépendance Android
  platform/               interfaces Platform, Painter, Audio, Input
  data/                   modèle + analyseurs des formats texte (docs/FORMATS.md)
  state/                  GameState (sauvegarde binaire LCDA v3), Conditions, Effects
  engine/                 Game (pile d'écrans, horloge des marées, autosauvegarde), Ui, Settings, Hooks
  screens/                monde, dialogues, carnet, carte, lecture, cinématiques, lettre, épilogue, menus, options
  puzzles/                les 16 énigmes E01–E16 + secrets-énigmes S01, S06, S08
app/src/main/
  kotlin/…/               MainActivity, GameView (Choreographer), AndroidPlatform, AndroidPainter, AndroidAudio
  AndroidManifest.xml, res/   icône adaptative, splash, thème plein écran, chaînes FR/EN, règles de sauvegarde
  assets/
    data/                 zones (.tab), dialogues (.dlg + talk.tbl), puzzles (.pzl), items (.itm), pages (.pg),
                          echoes (.ec), barks (.brk), thoughts, strings (fr/en), tables (cloches, bornes, murmures,
                          étiquettes, archives, Filou, personnages, retentissements, crédits, tutoriels, lettre), cinematics
    art/                  zones/ (tableaux 16:9), cin/ (plans de cinématiques), ui/ (carte, grain, vignette), characters/, items/
    audio/                music/ (26 pistes), ambience/ (21 boucles), sfx/ (137 effets) — synthétisés par tools/audio/
    fonts/                EB Garamond, Caveat, Cinzel, Courier Prime (OFL)
desktop/src/…/            harnais bureau Java2D : fenêtre jouable, suite de tests de contenu, tour de captures d'écran
tools/                    fetch_toolchain.sh, compile_desktop.sh, build_apk.py, make_keystore.py, make_icons.py,
                          gdd_extract.py (pages, cloches, bornes, murmures, étiquettes, archives), process_art.py, audio/
docs/                     formats, rapport d'audit, rapport d'erreurs, rapport de taille, configuration Android, correspondance
build/out/                sorties (APK signé, size_report.md, captures) — hors git
```

## Construire

```bash
bash tools/fetch_toolchain.sh          # kotlinc, aapt2, r8.jar (d8), android-34.jar dans ~/.cache/toolchain
pip install numpy soundfile cryptography pillow jdk4py
python3 tools/build_apk.py             # → build/out/LaCartographieDesAbsents-ch1-release.apk (signé v1 + v2) + size_report.md
```

Le script `tools/build_apk.py` est autonome (aapt2 → kotlinc → d8 → zip aligné → signature v1 JAR + v2 APK Signing Block,
vérifiée indépendamment). Le projet est aussi un projet Gradle standard (`settings.gradle.kts`, `app/build.gradle.kts`)
ouvrable dans Android Studio ; les sources du cœur sont incluses par `sourceSets`.

Clé de signature : `tools/make_keystore.py` génère une clé RSA-2048 auto-signée dans `build/keys/` (hors git).
Pour une clé de production : `RELEASE_KEY=… RELEASE_CERT=… python3 tools/build_apk.py`.

## Tester

```bash
bash tools/compile_desktop.sh --tests    # 14 sections : parseurs, volumes GDD, références, dialogues, énigmes,
                                         # chaînes FR/EN, sauvegarde, conditions, E05, parcours simulé complet
                                         # (toutes les scènes, toutes les énigmes, lettre → épilogue → fin → NG+),
                                         # graphe des zones, audio référencé, repli anglais
bash tools/compile_desktop.sh --tour build/out/shots   # captures d'écran automatiques (paysage + portrait)
bash tools/compile_desktop.sh --run      # jouer au clavier/souris (Échap = retour)
```

## Configuration Android

Package `com.ateliermareebasse.cartographie`, versionCode 1, versionName `1.0.0-ch1`, minSdk 26, targetSdk 34,
orientation `fullUser` (portrait et paysage, interface adaptative), plein écran bord à bord, encoche gérée,
permission unique `VIBRATE`, aucune connexion réseau, sauvegardes dans le stockage privé (`files/saves/`, sauvegarde
Android incluse). Voir `docs/CONFIG_ANDROID.md`.

## Correspondance avec un projet « moteur classique »

| Demande | Ici |
|---|---|
| Assets/Scripts, Prefabs, Scenes | `core/src` (moteur) + `app/src/main/kotlin` ; les « scènes » sont les 19 zones `.tab` et les écrans `screens/` |
| Assets/Art, Audio, UI, Fonts | `app/src/main/assets/art`, `audio`, `fonts` ; l'UI est vectorielle (nette à toute résolution) |
| Data (dialogues, quêtes, niveaux) | `app/src/main/assets/data` (formats documentés dans `docs/FORMATS.md`) |
| Resources/Streaming | non nécessaire : tout est dans l'APK, images décodées à la demande avec cache LRU |
| Build config | `AndroidManifest.xml`, `app/build.gradle.kts`, `tools/build_apk.py` |

Les éléments « si nécessaire » du cahier des charges (ennemis, boss, armes, véhicules, XP, boutique) ne font pas partie
du jeu défini par le GDD ; leurs équivalents narratifs (faveurs, liens, traits, clarté, relevés, secrets) sont détaillés
dans `docs/CORRESPONDANCE_CAHIER_DES_CHARGES.md`.
