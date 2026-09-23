# Bible graphique — comment les images du jeu ont été produites

Toutes les images embarquées (`app/src/main/assets/art/`) sont des peintures générées à partir de descriptions
écrites d'après le GDD (chapitre 5 : direction artistique « aquarelle et encre de Chine, papier texturé, palette
grise / ocre / bleu ardoise »), puis traitées par `tools/process_art.py` (recadrage 16:9 → 1600×900 JPEG progressif
pour les tableaux et plans de cinématique ; 1600×1000 pour la carte ; PNG RGBA 512² détourés pour les portraits ;
PNG RGBA 256² pour les objets). Les sources PNG (`build/art_src/`) ne sont pas versionnées ; les fichiers d'assets
le sont : le dépôt suffit à reconstruire l'APK.

## Style commun (préfixe de toutes les descriptions)

> Watercolour and India ink illustration, muted palette of grey, ochre and slate blue on textured paper, fine ink
> linework, no people, no legible text.

Variantes : nuit / Heures Bleues (« deep indigo, slate blue, pale silver »), intérieurs à la lampe (« warm lamp
light »), chambre noire (« single warm red safelight glow »). Aucun texte lisible : les étiquettes, plaques et
cadrans sont dessinés par le moteur (UI vectorielle) pour rester localisables.

## Tableaux (40) et plans (1)

La composition de chaque tableau suit les coordonnées des points d'intérêt de `data/zones/fr/*.tab` (élément décrit
« à gauche / au centre / à droite, en haut / en bas » selon `x y`). Exemples : hall de la gare (Z05 t01) —
tableau des départs à 0.40/0.30, horloge à 0.58/0.12, guichet à droite, banc 3 en bas à gauche avec la boussole,
chat sur le rebord de la fenêtre en haut à gauche (secret S11) ; épave (Z11 t02) — échelle au centre, trois
barreaux neufs, verres accordés sur la lisse en bas à gauche, écoutille au-dessus de l'échelle (chemin vers t03).

Plan du rêve (`art/cin/reve.jpg`, aussi décor de la zone cachée Z00) : eau plate sans horizon, lune ronde haute,
muret de pierre sèche posé sur l'eau, silhouette lointaine qui se dissout en pluie d'encre inversée, lucioles.
C'est la seule image où une silhouette humaine apparaît : c'est le sujet même de CIN-01.

## Carte du monde (`art/ui/map_world.jpg`, 16:10)

Carte à l'encre sur papier ancien, sans texte (les noms sont posés par `MapScreen`). Disposition imposée par les
coordonnées `map x y` des zones : ville de Port-Cendre en bas à gauche (Cabinet 0.30/0.62, quartier 0.36/0.66,
place 0.40/0.58, quai du bac 0.32/0.78), funiculaire au centre (0.50/0.50) montant vers la gare (0.55/0.40) et les
voies mortes (0.62/0.36), falaise haute (0.60/0.24), maison d'Ysolde (0.70/0.30), bois des noms (0.74/0.44),
corniche des pétrels (0.84/0.56), sommet (0.90/0.30), phare (0.93/0.24) et sa terrasse (0.93/0.18), grotte
(0.86/0.72), anse (0.78/0.86), passage (0.90/0.80), Pointe-au-Vent (0.96/0.86). Mer en bas et à droite, rose des
vents, échelle graphique, rides d'estran.

## Portraits (16, bustes à l'encre, papier détouré)

Style : buste de trois-quarts, encre de Chine et lavis léger, quatre valeurs au plus (GDD 5.x : fond, couleur,
signature, détail), fond papier uni clair (détouré au traitement). L'encre « s'affirme » avec le LIEN : le moteur
module l'opacité du portrait dans le carnet.

| id | description retenue (GDD 4.x) |
|---|---|
| lohen | 21 ans, maigre de grimpeuse, cheveux brun foncé mi-longs attachés n'importe comment avec un crayon qui dépasse, grain de beauté à la mâchoire gauche, vareuse de toile huilée vert-de-gris, écharpe grenat |
| esteban | 21 ans, épaules de rameur, cheveux noirs courts avec une mèche rebelle plaquée d'une main, pull marin écru rapiécé au coude, chapeau de feutre ridicule ; dessiné DE DOS ou de profil flou (jamais « en vrai » au chapitre 1) |
| filou | griffon Korthals de 7 ans, poil dur gris-brun, barbe, sourcil gauche blanc en forme de virgule |
| ombeline | 64 ans, carrure de roc, chignon gris armé d'un portemine, lunettes à cordon, cardigan moutarde, tablier à poches |
| marta | 52 ans, ronde et rieuse, rouge du café (#8C3B3B), tablier blanc cassé, torchon sur l'épaule |
| rosa | 47 ans, boulangère, bras nus farineux, rire de fournil, foulard, farine comme une météo |
| emeric | écrivain public, rondeurs dignes, gilet de velours côtelé brun avec une pièce de monnaie en guise de bouton, demi-manches de soie noire, demi-lunettes, stylo vert amande au revers |
| tom_till | jumeaux de 10 ans, cirés jaunes dépareillés, genoux de sable ; Tom : mèche gauche ; Till : yeux plissés (myope sans lunettes) ; badges « case 1 » / « case 9 » |
| anselme | chef de gare, 1,70 m voûté vers l'avant, veste boutonnée jusqu'en haut, montre à chaîne, casquette levée à la main |
| baz | 61 ans, passeur, barbe grise taillée au couteau, ciré orange délavé rosé aux épaules, bonnet de laine roulé trois fois, joue gauche gonflée par la chique |
| sidonie | adolescente mécanicienne, casque anti-bruit dépareillé (coquille violette, jugulaire orange), bandana constellation, combinaison tachée, calculatrice au ceinturon |
| ysolde | tisseuse aveugle, longue carrure immobile comme une proue, yeux clairs opale ouverts, cheveux blancs tressés en couronne à trois nœuds, tablier indigo sur robe garance, mains en avant |
| aristide | l'Oublieux, 1,72 m flottant dans trois couches de laine brune dépareillées, bonnet de nuit en plein jour, mains immaculées, sourire doux |
| marek | veilleur du phare, 1,86 m, sec, joues rougies par le vent, barbe naissante mal rasée, ciré jaune sale de sel ou pull bleu délavé, regard muré |
| adele | médecin, 1,66 m, blouse grise sur jean, cheveux châtains attachés d'un geste chirurgical, stéthoscope à l'olive de cuir, deux stylos dans la poche |
| aurore | moutonne des dunes, toison épaisse, oreilles en avant, air souverainement indifférent |

## Icônes d'objets (12 objets majeurs, GDD 5.17 ; les objets secondaires portent un glyphe d'encre par catégorie)

lentille_arpenteur (loupe à double foyer en laiton), boussole_esteban (boussole au verre étoilé), carnet_comptage
(carnet toilé aux marges dessinées d'oiseaux de dos), plume_esteban (plume-stylet usée d'un côté), sifflet_esteban
(sifflet à pétrel en bois et laiton), girouette_1889 (girouette-rose des vents en fer forgé), ruban_memoire (ruban
de coton garance noué), lampe_tempete (lampe-tempête au verre bombé), photo_pointe (tirage argentique d'une nuque
et d'un chapeau), fil_de_plomb (fil de plomb de passeur), cle_du_vent (clé assemblée plume + sifflet + girouette),
lettre_esteban (lettre pliée au cachet de cire).
