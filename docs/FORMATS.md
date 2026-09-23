# Formats des données de jeu (`app/src/main/assets/data`)

Tous les fichiers sont du texte UTF-8, une entrée par ligne, `#` = commentaire. La langue est un sous-dossier (`fr/`, `en/`) :
si un fichier manque dans la langue demandée, le français est utilisé (repli).

## Conditions `[SI: …]`
Opérateurs `&`, `|`, `!`, parenthèses. Atomes : `flag:x` (ou `FLAG-X`), `has:item`, `page:n`, `echo:E-xx`, `puzzle:Exx`,
`secret:Sxx`, `seq>=SQ-nn`, `act>=n`, `act==n`, `LIEN-NPC>=n`, `DOUCEUR>=n` (COURAGE, RUSE, HUMEUR), `CLARTE>=n`, `FILOU>=n`,
`zone:zxx`, `tableau:txx`, `tide:BM`, `low_tide`, `hb`, `talked:scene`/`seen:scene`, `decision:D-01=x`, `decision:D-03`,
`npc_here:x`, `visited:zxx`, `examined:id>=n`, `count:key>=n`, `var:k=v`, `carte>=n`, `pages>=n`, `echoes>=n`, `secrets>=n`,
`chocolats>=n`, `trait:RUSE`, `ng+`, `letter_read`, `chapter_done`.

## Effets (séparés par `;`)
État : `+DOUCEUR`, `LIEN-X +n`, `CLARTE +n`, `FILOU +n`, `flag x`, `unflag x`, `give item [n]`, `take item`, `page n`, `echo E-xx`,
`echo_available E-xx`, `secret Sxx`, `releve n`, `seq SQ-nn`, `decision D-0x=val`, `solve Exx`, `set k=v`, `count k +n`,
`filou_join`, `filou_wait`, `filou_react clé`, `borne Bxx`, `murmure M-xx`, `bell n`, `label n`, `archive AR-xx`, `chocolat`,
`cooldown n`, `letter_read`, `ending x`.
Présentation : `sfx id`, `music id|off`, `ambience id|off`, `haptic ms`, `thought clé`, `say "texte"`, `toast "texte"`,
`cin CIN-xx` (ce qui suit est joué à la fin de la cinématique), `travel zXX [tYY]`, `puzzle Exx`, `scene id`, `save "nom"`,
`checkpoint`, `letter`, `epilogue`, `end_chapter`, `credits`, `carnet [onglet]`, `tuto id`, `act_card n`, `quit_to_title`,
`retentissement`.

## Zones `.tab`
```
zone z05 / name … / subtitle … / act n / palette … / music id / ambience id / open <condition> / map x y / start t01 / hidden
tableau t01 "Nom"
image art/zones/z05_t01.jpg
prop interior|tide_view|dark|overlay beam|lamp|particles dust|petrels|fireflies|ink|spray|bluehour|snow|music id|ambience id|filou_x|filou_y|sniff clé
enter_thought clé [SI: …]          (une fois par tableau ; suffixe « ! » = à chaque fois)
enter_scene scene [SI: …]
npc id x y [SI: …] scale=1
hot KIND id x y "libellé" clé=valeur… [SI: …] -> effets
path cible "libellé" x y [SI: …] locked=clé_de_pensée hidden strict back kind=door cin=CIN-xx -> effets
```
`KIND` : EXAMINE, LISTEN (`think=`, `sfx=`, `sniff=`, `repeat`), TAKE (`item=`), USE (`item=`, `need=`, `consume`, `scene=`),
PUZZLE (`done=`, `replay`), TALK, READ/PAGE (`page=`, `archive=`, `text=`), BELL, BORNE, MURMURE, LABEL, ECHO (`hold=`),
SIT (`hold=`), SECRET/ACTION (`scene=`, `hold=`, `hold_label=`), FILOU, CIN. `lens=1` : visible seulement à la lentille ;
`once` : une seule fois. Les coordonnées sont relatives à l'image 16:9 (0–1).

## Dialogues `.dlg` et `talk.tbl`
```
~ id [SI: cond] [once]
LOCUTEUR: texte  (PENSEE: = pensée de Lohen ; * = didascalie ; [[cond|si vrai|si faux]] en ligne)
+ « choix » [SI: cond] -> effets ; goto label      (+* = encre or, définitif : 20 s de retenue émotionnelle)
+ (action sans mot) -> …
-> effets
= label   |   > label [SI: cond]   |   ! sfx|music|ambience|voice|haptic|wait|hold arg   |   end
```
`talk.tbl` : `npc id` puis une scène par ligne `[SI: cond]` ; la première dont la condition tient (et non déjà vue si `once`) est jouée.

## Énigmes `.pzl`
`@ E01 | Nom | zone` puis `phrase:` (phrase-test), `intro:`, `hint1/2/3: LOCUTEUR | texte` (`auto` = aide visuelle), `success:`,
`effects:`, `after: scène | scène de repli`, `sfx:`. L'écran de chaque énigme est dans `core/…/puzzles/`.

## Autres
- `items/*.itm` : `@ id | Nom | catégorie | sniff=… | hold`, `note:`, `1:` `2:` `10:` (`10+:` après la lettre).
- `pages/pages.pg` : `@ n | lieu | date | oiseaux` puis les lignes. Générées depuis le GDD par `tools/gdd_extract.py`.
- `echoes/echoes.ec` : `@ E-xx | titre | année | secondes` puis `LOCUTEUR: …` / `* …`.
- `barks/barks.brk` : `zone | D/C/R/NG | texte`.
- `thoughts/*.txt` : `clé | texte` ; variantes `clé.2`, `clé.10`, `clé.actN`, `clé.after`, `clé.ng`, `clé.DOUCEUR|COURAGE|RUSE`.
- `strings/fr.txt`, `en.txt` : `clé | texte {0}`.
- `tables/` : `bells.txt` (`n | nom | texte | note`), `bornes.txt` (`id | lieu | main | texte`), `murmures.txt`, `labels.txt`,
  `archives.txt` (`@ AR-xx | titre` + lignes), `filou.txt` (`clé | comportement | pensée`), `characters.txt`
  (`id | nom | rôle | portrait | couleur | notice`), `retentissements.txt` (`cond | ligne`), `credits.txt` (`type | texte`),
  `tutorials.txt` / `epilogue_models.txt` (`@ id` + lignes), `letter.txt` (`@ pli n | titre` + paragraphes ; `@ pps` = NG+).
- `cinematics/cinematics.cin` : `@ CIN-xx | titre | music=… | ambience=… | noskip` puis `image | secondes | légende | locuteur | effet | sfx`.

## Sauvegarde (Annexe G)
Fichier binaire : magic `LCDA`, version de sauvegarde (3), version du jeu, horodatage, FNV-1a-64 du corps, puis blocs
`META`, `STATE`, `WORLD`, `COLLECT`, `PUZZLES` (tag u8 + taille u32). Fichiers `slot_1..3.sav`, `auto.sav`, `checkpoint.sav`
dans le stockage privé. Une somme de contrôle fausse ou un fichier tronqué est signalé « illisible » sans planter.
