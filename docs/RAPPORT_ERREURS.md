# Rapport d'erreurs et de corrections (journal de fabrication)

| # | Problème constaté | Cause | Correction |
|---|---|---|---|
| 1 | Perte complète du premier espace de travail (build de 117 Mo jamais poussé) | arbre de travail > plafond d'instantané, sources d'images et chaîne d'outils suivies par git | chaîne d'outils hors dépôt (`~/.cache/toolchain`), sources d'images dans `build/` (ignoré), engagement après chaque composant |
| 2 | `kotlin-compiler` npm : aucun zip dans l'archive | le dossier `package/` est la distribution elle-même | `fetch_toolchain.sh` utilise directement `package/bin/kotlinc` |
| 3 | Extraction des pages : 23/24 trouvées | en-tête de la page 01 avec parenthèse avant « Date » | regex tolérante, assertion 24 |
| 4 | Cloches 40/43 extraites | en-têtes sans tiret cadratin (04, 14, 41, 43) | regex élargie + table de cas particuliers (cloches sans voix) |
| 5 | Bornes 39/40, murmures 17/18 | tiret final sans espace | regex `—\s*` |
| 6 | Erreur de syntaxe Kotlin (E05 `reachabilityCheck`) | expression sur une ligne ambiguë | réécriture explicite |
| 7 | Test E05 : « un état ne rejoint pas la cible » | avec les règles d'enclenchement du GDD, 32/32 états ne sont pas tous solubles (contradiction interne du GDD) | le test vérifie ce qui compte en jeu : tout état **atteignable** depuis l'état initial rejoint la cible, et le levier maître remet l'état initial |
| 8 | Suite de tests : la section 11 ne se terminait pas | boucle « refermer les écrans » sans borne (choix de dialogue non pris, cinématique non sautable) | fonction `drain()` bornée qui choisit la première option et termine les cinématiques ; décodage d'images désactivé en test |
| 9 | Effet inconnu `cooldown anselme 60` | effet documenté dans le GDD (retenue de 60 s après G-15) non implémenté | effet `cooldown n` ajouté |
| 10 | Locuteurs TOM/TILL/VOIX/RADIO inconnus | pas de fiche personnage | chaînes `speaker.*` FR/EN + couleur des jumeaux |
| 11 | `sq15_marek_maison` inaccessible (condition circulaire porte/cuisine) | la scène ouvrait la porte qu'il fallait avoir ouverte pour l'atteindre | scène courte `marek_ouvre` à la porte + voyage vers la cuisine ; faveur lampe donne +1 lien (3 faveurs = 3 liens) |
| 12 | Blocage possible si LIEN-MAREK < 4 après E04 | scène de la girouette conditionnée au lien | scène de repli `sq16_marek_girouette_froid` (anti-blocage 12.4) ; `after: a \| b` dans les fiches d'énigme |
| 13 | Segfault de l'encodeur Vorbis (libsndfile) | écriture d'un bloc unique de 96 s | écriture par blocs de 2 s |
| 14 | `compose.py` ignorait `--force` | l'option était prise pour un nom de piste | filtrage des arguments |
| 15 | `aapt2 link` : `--no-compress-regex` mal placé | option insérée avant le fichier de sortie | option placée avant le dernier argument |
| 16 | Vérification d'alignement de `resources.arsc` fausse | lecture du champ extra du répertoire central au lieu de l'en-tête local | lecture de l'en-tête local, contrôle de toutes les entrées stockées |
| 17 | Signature v2 : un niveau de préfixe de longueur en trop autour du signataire | erreur d'implémentation | corrigé ; vérificateur indépendant intégré au build (`verify_v2`) |
| 18 | Pas de `javac` dans la chaîne (classe `R` impossible) | JDK réduit | identifiants de ressources par `getIdentifier`, thème via manifeste |
| 19 | Test des effets : `LIEN-YSOLDE` attendu 3 | attente du test erronée (lien initial 0) | test corrigé |
| 20 | Le prologue jouait sur le décor du Cabinet | scène du rêve rattachée à z01 | zone cachée `z00` (rêve) → CIN-01 → z01 ; séquencement `cin` puis `travel` dans `Effects.apply` |
| 21 | Glyphes hors couverture des polices embarquées (✕ ♪ ● ○ ◐ ◑ ⌫ ␣ ↺ ↻ ⚓ ♆ ✦ 🐟 ✓ ▸ …) rendus en « □ » | textes et écrans (E04, E05, E12, E16, clavier, journal du carnet, tutoriel) utilisaient des symboles absents d'EB Garamond / Caveat / Courier Prime / Cinzel | icônes vectorielles (`Ui.icon` : lunes, trident, sirène, ancre, poisson, retour-arrière, espace…) et substitutions typographiques ; vérification de couverture par `fontTools` |
| 22 | Tutoriels et générique uniquement en français | tables `tables/fr` seules | `tables/en/tutorials.txt` et `tables/en/credits.txt` (repli FR automatique conservé pour les tables narratives) |
| 23 | La pensée d'entrée dans un tableau se dessinait par-dessus la boîte de dialogue ouverte au même instant | `renderThought` ignorait la pile d'écrans | la pensée attend la fin du dialogue |
| 24 | Portraits de Tom et Till jamais trouvés (`art/characters/tom.png`) | clé de portrait non normalisée vers `tom_till` | même normalisation que la couleur du locuteur |
| 25 | Objets sans icône dédiée : main générique | pas d'icône par catégorie | glyphes d'encre par catégorie (outil, clé, souvenir, consommable, document) |
| 26 | Historique git local perdu à la remise à zéro du bac à sable (arbre de travail intact) | jeton GitHub expiré au tour précédent : commits non poussés | branche ré-ancrée sur `origin`, état complet recommité et poussé ; poussée après chaque étape |
| 27 | Portraits détourés « fantomatiques » dans les tableaux et sur le panneau de dialogue (les zones claires du lavis deviennent transparentes) | détourage par distance à la couleur du papier | les bustes sont posés sur une **carte de papier épinglée** (dialogue, PNJ dans les tableaux, Filou) ; dans le carnet ils restent sur le papier, où l'opacité suit le LIEN |
| 28 | Coordonnées `map x y` des zones dessinées « à l'aveugle » avant la carte | carte générée après les données | coordonnées réalignées sur le dessin final (ville en bas à gauche, funiculaire, gare, bois, phare, anse, passage, pointe) |
| 29 | Vérification de l'existence d'un portrait à chaque image (`assetExists` relit le fichier) | appel dans `render()` | mémo par écran (`portraitKnown`) |
| 30 | Icônes des objets secondaires signalées comme manquantes à chaque test | pas de distinction données entre objets majeurs et secondaires | flag `glyph` dans `items.itm` (26 objets) : glyphe de catégorie assumé, les 12 objets majeurs du GDD 5.17 exigent une icône (erreur de test sinon) |
| 31 | E14 (clé du vent) : les trois pièces libres se chevauchaient (pas de 24 px pour des cartes de 52 px) | disposition verticale héritée des libellés seuls | pièces en rangée sur vignettes de papier, zone de saisie alignée sur le dessin |

Les tests (`tools/compile_desktop.sh --tests`) rejouent l'ensemble : 170 branches de scènes, 19 écrans d'énigmes,
chaîne du final complète (lettre → épilogue → fin de chapitre → NG+), entrée dans les 40 tableaux et action sur chaque
point d'intérêt, sauvegarde/chargement au milieu, en paysage et en portrait.
