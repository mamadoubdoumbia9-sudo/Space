===============================================================================
  A-274   echo_E16_bench_04_read_me_something
===============================================================================

  ANNEXE A2 — FICHE D'ANIMATION AUTONOME 274 / 744
  Partie 2 — ESTEBAN
  Categorie : ACTING / DIALOGUE
  Marqueur   : ECHO E16 JOUABLE 3 min 10 (11.04)
  Fichier source  : A_Esteban_Act_echo_e16_bench_04_read_me_something_v01.glb
  Reference maitre : LOHEN_PROMPT_MASTER.txt BLOC 07 · ANIM_MANIFEST_744.txt
  Origine de la fiche : conçu — bloc de plage deplie

-------------------------------------------------------------------------------
1. IDENTITE TECHNIQUE
-------------------------------------------------------------------------------

  Duree ................. 3.600 s  (108 frames a 30 fps)
  Type .................. ONE-SHOT
  Root motion ........... RM-NONE
  Blend in / out ........ 0.30 s / 0.30 s
  Couche AnimationTree .. L0+L4
  Priorite .............. 88 — narratif / interaction (bande 80-94, bareme 0.05)
  Poids compresse cible . ~166 Ko (budget global 42 Mo, 0.12)

  Os principalement sollicites :
      colonne, bassin (assis), bras, mains, tete, blendshapes

  Tolerances de compression de courbe :
      · position 0.002
      · rotation 0.0015
      · echelle 0.004
      · doigts 0.006

-------------------------------------------------------------------------------
2. CONTEXTE DE PERSONNAGE (reporte ici pour l'autonomie)
-------------------------------------------------------------------------------

  ESTEBAN — 31 ans, accordeur de cloches. Plus grand que Lohen, roux.

  Il ne tient pas en place. Il parle avec LES DEUX mains (Lohen n'en utilise
  qu'une). Il est en avance sur le temps, jamais en retard. Gestes signature
  : E1 il range sa meche et il RATE, E2 il sourit du cote gauche seulement,
  E3 il tapote deux fois avant de parler serieusement. [OBL] Seul personnage
  du jeu SANS REVERB, en toutes circonstances (11.06). [OBL] Aucun Echo ne
  le montre, sauf E21.

  Rig et costume :
      gilet (cloth simule dans E21), crayon de charpentier derriere
      l'oreille, carnet en poche de poitrine, montre

-------------------------------------------------------------------------------
3. DIRECTION D'ACTEUR — CE QUE LE MOUVEMENT DIT
-------------------------------------------------------------------------------

  IL DEMANDE A LOHEN DE LUI LIRE QUELQUE CHOSE. Il tend un livre sans le
  regarder, comme une chose sans importance. [OBL] Il ne sait pas encore. Le
  geste est leger et c'est ce qui le rend cruel a la relecture.

-------------------------------------------------------------------------------
4. TIMELINE ET EVENEMENTS
-------------------------------------------------------------------------------

  Fenetre de lisibilite [OBL 07.02] : le mouvement visible commence
  avant la frame 5 (166 ms). Aucune derogation.

  Clip ONE-SHOT : pas de contrainte de cyclage. La derniere frame
  doit poser une silhouette blendable vers les etats de sortie.

  Evenements de timeline :
      BOOK_OFFER f22 · Face : `gentle`

-------------------------------------------------------------------------------
5. TRANSITIONS
-------------------------------------------------------------------------------

      <- clip E16 precedent ; -> clip E16 suivant, turn_away_dissolve

-------------------------------------------------------------------------------
6. VALIDATION [DOD]
-------------------------------------------------------------------------------

  Regle des trois passes (0.10) :
      P1 BLOCKING  poses cles seules, lisibilite validee
      P2 SPLINE    courbes, timing, overlap, follow-through
      P3 POLISH    doigts, tissu, respiration, micro-desequilibres
  Aucun clip n'entre en build release sans P3 signee dans
  /docs/anim_manifest.csv.

  Test de la silhouette animee (0.11) : rendu en aplat noir sur fond
  blanc, regarde a 0.5x. Si la pose cle n'est pas lisible en silhouette
  a n'importe quelle frame, elle est reprise.

  Controles specifiques a ce clip :
      · les clauses [OBL] de la section 3 sont verifiees en revue, pas
      seulement en lecture
      · aucune cle sur un os que le clip n'anime pas reellement
      (`tools/strip_dead_keys.py`, 0.03)

===============================================================================
  FIN DE FICHE A-274
===============================================================================
