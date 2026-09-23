# Correspondance entre le cahier des charges générique et le jeu défini par le GDD

Le GDD (source de vérité) définit un jeu narratif sans combat, sans ennemis, sans boss, sans timer, sans mort, sans
monétisation, sans joystick (2.7, 6.11). Les rubriques « si nécessaire » du cahier des charges sont résolues ainsi :

| Rubrique demandée | Résolution dans le jeu | Où |
|---|---|---|
| Menu principal | Écran-titre : Continuer, Nouvelle partie, Charger, Options, Crédits, Quitter | `screens/SystemScreens.kt` (TitleScreen) |
| Écran de chargement | Barre de progression du chargement des données + ligne de côte animée | LoadingScreen |
| Tutoriel | 10 tutoriels contextuels non bloquants (lentille, exploration, carnet, sacoche, Filou, encre or, énigmes, bornes, épilogue) | `data/tables/fr/tutorials.txt` |
| Introduction | Prologue jouable « Le rêve de velune » (z00) + CIN-01 (réveil, titre) | `zones/fr/z00.tab`, `cinematics.cin` |
| Scénario principal | 20 séquences SQ-01→SQ-20 en 4 actes + épilogue, gating par séquences, liens et objets | dialogues `.dlg`, `Game.act()` |
| Missions / quêtes secondaires | 10 faveurs (FA-01…FA-10 : fournée, billes, colis, lampe, crayon, journal, chant, dictionnaire, cloches, Aristide) + 12 secrets | dialogues, `puzzles.pzl` (S01…S12) |
| Niveaux / cartes / environnements | 19 zones, 40 tableaux illustrés, carte du monde avec voyage rapide | `zones/`, `MapScreen` |
| Personnages | 16 fiches (Lohen, Esteban, Filou, 12 PNJ, Aurore) + monsieur Éraflé (secret S11) | `characters.txt`, portraits |
| Inventaire | La sacoche du carnet : 38 objets à trois pensées (1er, 2e, 10e examen), objets-clés, souvenirs, consommables | `items.itm`, CarnetScreen |
| Compétences / progression / XP | **Pas d'XP** (GDD 2.7). Progression par traits invisibles D/C/R/H, liens 0→5, clarté 0→100, relevés cartographiques, pourcentage de carte | `GameState` |
| Récompenses | Pensées offertes, pages, échos, objets, scènes, feuillet « Ce que le monde a retenu de toi » | Effects, RetentissementScreen |
| Monnaie / boutique | **Pas d'argent** (6.7) : l'économie est la faveur ; le « carnet des dettes de cœur » d'Émeric en est le miroir | `reprises.dlg` (emeric_reprise) |
| Ennemis / boss / armes / véhicules | Non applicables (GDD 2.7). La « traversée » se fait en bac (La Decideuse) et en funiculaire (La Crevette) : des scènes, pas des véhicules pilotés | z04, z07, CIN-03 |
| Dialogues | 90 scènes arborées, choix à l'encre or définitifs, substitutions conditionnelles, 20 s de retenue émotionnelle | DialogueScreen |
| Cinématiques | CIN-01, 03, 04, 05, 07, 08, 08b, NG : plans illustrés, légendes, travelling ; non sautables pour la lettre et la fin | CinematicScreen |
| Événements | Marées (6 phases de 12 min), horaires des PNJ par acte, Heures Bleues (acte IV), fenêtres allumées | `Game.worldClock`, conditions `act`, `tide` |
| Points de contrôle | Checkpoints nommés (« La marée commence » avant le point de non-retour), sauvegarde après SQ-15, après la lettre, fin | effets `save`/`checkpoint` |
| Sauvegarde / chargement | 3 emplacements manuels + auto (90 s et changement de zone) + checkpoint ; reprise après fermeture ; format binaire versionné avec somme de contrôle | `GameState`, SaveLoadScreen |
| Options | Graphismes LOW/MEDIUM/HIGH/ULTRA, volumes (général, musique, ambiances, effets), vibrations, langue FR/EN, sensibilité tactile, aide aux énigmes (Standard/Puriste/Assurée), taille du texte, contraste, sous-titres, gaucher, réduction des mouvements | SettingsScreen |
| Pause / victoire / défaite | Pause complète ; **pas de défaite** (6.11 : aucune sanction) ; la « victoire » est la fin de chapitre (4 fins D-06) + NG+ | PauseScreen, EndCardScreen |
| Crédits | Générique déroulant | CreditsScreen |
| Interface multi-résolutions | UI vectorielle mise à l'échelle (`Ui.s`), portrait et paysage, marges d'encoche | `engine/Ui.kt` |
| Localisation | FR intégral ; EN : interface, options, tutoriels, menus, énigmes ; les textes narratifs (dialogues, pages, lettre) sont en français, avec repli automatique ; structure prête pour d'autres langues (`data/*/xx/`) | `strings/`, ContentLoader |
| Optimisation Android | Images décodées en tâche de fond avec `inSampleSize` selon la qualité, cache LRU borné à 1/5 de la mémoire, sons chargés à la demande, pas d'allocation dans la boucle de rendu, `onTrimMemory` | AndroidPainter, AndroidAudio |
| Taille de build | Voir `docs/RAPPORT_TAILLE.md` (contenu réel uniquement) | `tools/build_apk.py` |
