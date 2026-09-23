# Rapport d'audit d'avant-build et de tests — La Cartographie des Absents, chapitre 1

Généré à partir de `python3 tools/audit.py --strict` (13 points du cahier des charges) et de
`bash tools/compile_desktop.sh --tests` (14 sections) sur l'état final du dépôt. Les deux commandes sont
rejouables ; l'audit renvoie un code de sortie non nul dès qu'un point est bloquant.

## A. Audit d'avant-build (13 points) — résultat : 0 point bloquant

| # | Point | Résultat |
|---|---|---|
| 1 | Références d'images | ✓ 71 images référencées (40 tableaux, carte du monde, 16 portraits, 12 icônes d'objets majeurs, grain, vignette, cinématique), toutes présentes. Les 26 objets secondaires portent le flag `glyph` dans `items.itm` (glyphe d'encre par catégorie, décision de conception). |
| 2 | Références audio | ✓ 152 identifiants référencés (26 musiques, 21 ambiances, 137 effets), tous présents |
| 3 | Assets orphelins | ✓ aucun : chaque fichier embarqué est utilisé par le jeu |
| 4 | Doublons binaires | ✓ 306 fichiers d'assets, aucun doublon |
| 5 | Fichiers vides ou corrompus | ✓ aucun fichier vide ; toutes les images se décodent ; tous les OGG se lisent |
| 6 | Marqueurs de travail non terminé | ✓ 103 fichiers scannés : aucun marqueur de chantier ni section provisoire |
| 7 | Localisation FR / EN | ✓ 328 clés d'interface identiques en FR et EN ; tutoriels et générique traduits ; textes narratifs en français avec repli automatique documenté (`docs/CORRESPONDANCE_CAHIER_DES_CHARGES.md`) |
| 8 | Couverture des glyphes par les polices | ✓ 126 caractères distincts, tous couverts. Avertissement informatif : → ▶ ◀ n'existent que dans la police de corps (EB Garamond) — ils ne sont utilisés qu'avec elle (`boardBtn`) |
| 9 | Configuration Android | ✓ `com.ateliermareebasse.cartographie`, versionCode 1, versionName 1.0.0-ch1, minSdk 26, targetSdk 34, permission VIBRATE seule, orientation `fullUser`, icône adaptative, splash, `values` + `values-en` |
| 10 | Budget de taille | ✓ sources 59,6 Mo (audio léger dans le dépôt) ; **APK final 113,9 Mo** (cible 100 – 900 Mo, contenu réel uniquement — détail dans `docs/RAPPORT_TAILLE.md`) |
| 11 | Cohérence des identifiants de données | ✓ 19 zones, 90 scènes, 38 objets, 20 énigmes/secrets : toutes les références (zones, scènes, objets, drapeaux, énigmes, cinématiques, pensées) résolues |
| 12 | Tout ce qui est embarqué est versionné | ✓ aucun asset ignoré ni en attente : le dépôt reconstruit l'APK à l'identique |
| 13 | Clés et secrets | ✓ aucune clé privée ni keystore dans l'arbre suivi (clé de signature générée dans `build/keys`, ignorée par git) |

## B. Suite de tests (14 sections) — résultat : 0 erreur, 0 avertissement

| # | Section | Couverture |
|---|---|---|
| 1 | Analyse des fichiers | 19 zones, 90 scènes, 20 énigmes, 38 objets, 24 pages, 13 échos, 43 cloches, 40 bornes, 18 murmures, 50 étiquettes, 340 pensées, 328 chaînes |
| 2 | Volumes attendus (GDD) | comptes conformes aux chapitres 5, 6, 8 et 9 du GDD |
| 3 | Zones | image de chaque tableau, chemins bidirectionnels, points d'intérêt avec action valide |
| 4 | Dialogues | étiquettes, effets, conditions, références de scènes/objets/drapeaux, locuteurs |
| 5 | Tables de conversation et énigmes | scènes des tables `talk`, 3 indices et conclusion par énigme, scènes `after` |
| 6 | Cinématiques, personnages, objets | plans et images des cinématiques, 16 portraits, 12 icônes majeures, pensée 1 de chaque objet |
| 7 | Chaînes de l'interface | 263 clés utilisées dans le code, 328 FR = 328 EN |
| 8 | Sauvegarde | aller-retour binaire, somme de contrôle, versions LCDA v1→v3, emplacements multiples |
| 9 | Conditions et effets | unités : traits, liens, drapeaux, compteurs, marées, actes, séquencement `cin` → `travel` |
| 10 | E05 (leviers) | aucun état atteignable n'est bloquant ; le levier maître remet l'état initial |
| 11 | Parcours simulé complet | 170 branches de scènes jouées, toutes les énigmes résolues, lettre → épilogue → fin de chapitre → NG+, sauvegarde/chargement à mi-parcours, paysage et portrait |
| 12 | Graphe des zones | tout est joignable depuis le Cabinet Vasseur |
| 13 | Audio référencé | 184 fichiers audio, 49 effets référencés par le code, tous présents |
| 14 | Repli anglais | chaque scène, énigme et pensée a un repli |

## C. Vérification visuelle

`bash tools/compile_desktop.sh --tour build/out/shots` produit 37 captures (titre, options, cinématique, tableaux,
dialogue avec portrait, carnet — carte, sacoche, gens —, carte du monde, les 16 énigmes + S08, lettre, pause,
sauvegardes, écho, épilogue, générique, et trois captures en orientation portrait). Chaque planche a été relue :
cadrage 16:9 des tableaux, carte du monde inscrite au format 16:10, portraits sur cartes de papier, icônes des objets,
étiquettes de carte dans les bornes de l'image.

## D. Correctifs issus de l'audit et des tests

Voir `docs/RAPPORT_ERREURS.md` (30 entrées) : contradictions du GDD arbitrées (E05), scènes inatteignables
(`sq15_marek_maison`), anti-blocage (girouette), glyphes hors polices, ordre des effets, portraits détourés, icônes,
coordonnées de la carte, signature v2, alignement des ressources.

## E. Build final

`python3 tools/build_apk.py` → `build/out/LaCartographieDesAbsents-ch1-release.apk` : **113,9 Mo**, 345 entrées
(315 assets : 71 images, 184 sons, 55 fichiers de données, 5 polices), `classes.dex`, `resources.arsc` aligné,
signature v1 (JAR) + v2 (APK Signing Block, RSA-2048 / SHA-256) vérifiée indépendamment, `zip.testzip()` sans
erreur. Durée : 378 s (dont ré-encodage audio haute qualité). La clé est auto-signée (`build/keys`, hors git) ;
pour une clé de production : `RELEASE_KEY=… RELEASE_CERT=… python3 tools/build_apk.py`.
