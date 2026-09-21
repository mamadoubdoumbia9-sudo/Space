# -*- coding: utf-8 -*-
"""Renseigne les fiches dont le manifeste ne portait qu'une ligne resumee.
Deux cas :
  (a) MIROIRS — clips L/R replies dans une entree commune. Ils heritent des
      donnees techniques du jumeau et recoivent leur propre direction.
  (b) RESUMES — props et PNJ decrits en une phrase. La phrase est conservee
      et developpee en direction d'acteur reelle, sans la contredire."""

MIRROR = {
 "A-033": ("A-034", "strafe_guard_L",
  "contournement lateral vers la GAUCHE, en garde. L'epaule avant (droite "
  "ici) protege. La pointe du harpon decrit un petit arc qui suit la cible. "
  "[OBL] L'epaule droite est celle qui est abimee « depuis Saint-Jerome » : "
  "l'arc du harpon est 3 cm plus court de ce cote, et il l'est dans TOUS les "
  "clips du chapitre. Personne ne le commente, aucun dialogue n'y touche, et "
  "l'asymetrie est mesurable par un joueur qui compare des captures."),
 "A-037": ("A-038", "crouch_walk_L",
  "deplacement lateral accroupi vers la gauche, pivot sur la plante des "
  "pieds. Les talons ne touchent jamais le sol. Le harpon est tenu bas, "
  "presque au ras du sol, pour ne pas depasser de la couverture."),
 "A-084": ("A-085", "air_control_L",
  "inclinaison aerienne de 12 deg vers la gauche quand le joueur pousse "
  "lateralement en vol. [OBL] PUREMENT COSMETIQUE : le controle aerien reel "
  "est code. Mais sans ce clip, le joueur croit que le jeu ne l'ecoute pas. "
  "C'est une animation qui ne sert qu'a rendre credible une entree de "
  "commande, et c'est une raison suffisante."),
 "A-104": ("A-105", "ledge_shimmy_fast_L",
  "version rapide vers la gauche : les mains SAUTENT au lieu de glisser. "
  "Consomme du Souffle (3/s). Disponible seulement au-dessus de 50 % de "
  "Souffle. Le corps se balance davantage que dans la version lente — la "
  "vitesse coute de la stabilite, et ca se voit."),
 "A-155": ("A-157", "dodge_roll_B",
  "roulade ARRIERE. [OBL] LA PLUS MALADROITE DES QUATRE, volontairement : "
  "reculer en roulant est difficile, et ca doit se voir. Lohen perd le "
  "contact visuel avec la cible pendant 7 frames. Le joueur qui abuse de "
  "cette esquive se fait toucher, et c'est le design qui le lui apprend."),
 "A-156": ("A-157", "dodge_roll_L",
  "roulade vers la gauche. L'epaule d'appui est l'epaule gauche, la valide : "
  "c'est la roulade la plus propre des quatre. Le joueur la prefere sans "
  "savoir pourquoi."),
 "A-159": ("A-160", "dodge_sidestep_L",
  "esquive laterale glissee vers la gauche, garde maintenue, le buste reste "
  "FACE a la cible. Plus elegante que la roulade et moins sure : la fenetre "
  "d'invulnerabilite est plus courte de 4 frames. Le jeu ne le dit jamais."),
}

SUMMARY = {
 "A-554": ("griffade laterale rapide. [OBL] LATERALE : le Mueur n'attaque "
  "jamais de face (regle du personnage). Le bras part de derriere le corps et "
  "traverse le champ de vision du joueur de droite a gauche. La fenetre de "
  "degats dure 4 frames sur 21.", None),
 "A-555": ("deux griffades enchainees, gauche puis droite. La seconde part "
  "12 frames apres la premiere — trop vite pour une parade reactive, assez "
  "lent pour une parade anticipee. C'est la difference entre un joueur qui "
  "subit et un joueur qui a compris.", None),
 "A-559": ("reaction a un coup leger. Le corps tressaute, les plaques "
  "cliquettent, mais il ne recule pas : le Mueur encaisse les petits coups "
  "parce qu'il est couvert. C'est ce qui rend les gros coups satisfaisants.",
  "SFX(plate_rattle) f2 · VFX(V17) f0 · aucun recul de position"),
 "A-605": ("la coulee-bras se dresse a 3,4 m. TELL de l'attaque en ecrasement. "
  "[ART] Le mouvement est LENT parce que la coulee est LOURDE — 0,9 s de "
  "montee. Le joueur a largement le temps. Le Verrier n'essaie pas de le "
  "surprendre : il n'a jamais essaye de surprendre personne.",
  "SFX(flow_rise) f0-f27 · CAM_SHAKE(0.12) f24 · aucune musique de tell"),
 "A-611": ("titubement de phase 2. Il pose un genou, la coulee s'affaisse et "
  "met 0,6 s a le rattraper. [OBL] Il ne crie pas. Il ne peut pas : sa "
  "machoire est prise. Le seul son est celui du verre qui frotte.",
  "SFX(glass_grind) f0-f60 · fenetre de riposte OUVERTE f18-f48"),
 "A-634": ("un enfant actionne le soufflet de la forge. Il y met tout son "
  "poids et ca suffit a peine. Deux pressions par cycle, la seconde plus "
  "faible que la premiere. Il s'arrete 4 frames pour reprendre son souffle.",
  "SFX(bellows) f0, f54 · synchronise avec la respiration du feu"),
 "A-635": ("une femme lave, essore, pose. Le cycle complet fait trois gestes "
  "et se referme. [OBL] L'eau qu'elle utilise est de l'eau de pluie "
  "recuperee : le seau est sous une gouttiere, et le prop est place. "
  "Personne ne l'explique.", "SFX(water_wring) f84 · SFX(cloth_slap) f12"),
 "A-638": ("il depose la caisse. Les genoux plient AVANT que la caisse touche "
  "le sol — c'est ce qui distingue un porteur experimente d'un figurant. "
  "Il souffle une fois en se relevant.", "SFX(crate_down) f38 · BREATH_OUT f44"),
 "A-640": ("le partenaire du jeu de cordes. [OBL] Les deux clips sont "
  "synchronises par une horloge partagee : les regles inventees par les deux "
  "enfants doivent paraitre COHERENTES a l'observation. Un joueur qui "
  "regarde 30 secondes doit pouvoir deviner qui gagne.", None),
 "A-644": ("il aiguise une lame sur une pierre. Le geste est rapide, court, "
  "repete. Il verifie le fil du pouce une fois par cycle — et il le fait "
  "correctement, en travers, jamais dans le sens du tranchant.",
  "SFX(whetstone) x6 par cycle"),
 "A-645": ("elle remue un pot. Le poignet tourne, pas le bras. Elle gouttera "
  "a la fin du cycle et remettra du sel sans regarder.",
  "SFX(pot_stir) continu, faible"),
 "A-646": ("il mange debout, vite, sans poser ce qu'il tient dans l'autre "
  "main. [NAR] Personne ne s'assoit pour manger dans le Marche Suspendu. "
  "Ce n'est jamais dit et c'est visible partout.", None),
 "A-647": ("il fume, adosse. Le seul PNJ immobile autorise de la partie 9, "
  "parce qu'il fait UNE chose et qu'elle justifie l'immobilite. Trois "
  "bouffees par cycle, espacements irreguliers.", "VFX(smoke) f20, f110, f230"),
 "A-648": ("la dispute, celui qui ACCUSE. Index tendu, buste en avant, "
  "amplitude large. [OBL] On n'entend jamais les mots : la dispute est "
  "entierement lisible en silhouette, et c'est le test qu'elle doit passer.",
  "aucun dialogue · aucun sous-titre"),
 "A-649": ("celui qui se DEFEND. Paumes ouvertes, buste en retrait, "
  "amplitude deux fois plus faible. [ART] Le desequilibre d'amplitude "
  "entre les deux clips raconte qui a raison — ou qui croit avoir raison.",
  "aucun dialogue · aucun sous-titre"),
 "A-653": ("manoeuvre d'un treuil. Le corps entier participe : il tire avec "
  "le dos, pas avec les bras. La manivelle resiste au quart de tour et il "
  "doit relancer.", "SFX(winch_ratchet) x4 par cycle · CLOTH_RUSTLE f30"),
 "A-654": ("hisser une charge a la corde, en equipe de deux positions "
  "decalees. Les deux tirent en alternance, jamais ensemble : c'est comme "
  "ca qu'on hisse reellement.", "SFX(rope_haul) alterne"),
 "A-656": ("clouer une planche. Trois coups de marteau, une pause pour "
  "verifier l'alignement d'un oeil, puis deux coups. Le cinquieme coup "
  "enfonce le clou de travers et il ne le reprend pas.",
  "SFX(hammer) x5, le dernier plus sourd"),
 "A-657": ("elle distribue des rations. Une par personne, jamais deux. "
  "Elle regarde chaque visage avant de tendre. [NAR] Le meme geste que "
  "Tallec (clip A-428), fait par quelqu'un qui n'a pas d'autorite.", None),
 "A-662": ("une personne agee se leve, lentement. Trois appuis : accoudoir, "
  "genou, dos. Le clip dure 4,2 s et il ne doit pas etre raccourci : la "
  "duree EST l'information.", "SFX(joints) f40 · BREATH_OUT f96"),
 "A-676": ("quelqu'un dort dans une alcove, roule en boule sous une bache. "
  "Seule la respiration bouge. [OBL] Le joueur peut passer a 1 m sans le "
  "reveiller : les habitants du Marche ont appris a dormir malgre le "
  "passage.", "respiration lente, 11 cycles par minute"),
 "A-677": ("il tousse. Trois fois, la troisieme plus longue. Il se tient les "
  "cotes. [OBL] Aucun personnage ne le soigne, aucun dialogue ne le "
  "mentionne, il n'y a pas de quete. Il tousse, c'est tout.",
  "SFX(cough) x3 · le PNJ ne regarde jamais le joueur"),
 "A-679": ("il sursaute en apercevant une Figure au loin et se detourne "
  "aussitot. [OBL] Il ne fuit pas, il ne crie pas : il DETOURNE LES YEUX. "
  "Les habitants ont appris que regarder ne sert a rien.",
  "aucun cri · aucun son · la foule autour ne reagit pas"),
 "A-682": ("le monte-charge descend. Plus lent a la descente qu'a la montee "
  "(12 s contre 14 s ? non : 12 s, parce qu'il descend en charge et que le "
  "frein travaille). Arret sans rebond : le poids ecrase la suspension.",
  "SFX(lift_brake) f300-f360 · CAM_SHAKE(0.06) a l'arret"),
 "A-684": ("le tambour du treuil tourne. Rotation reguliere, un grincement "
  "par tour. Le cable s'enroule VISIBLEMENT : le nombre de spires augmente, "
  "et il diminue quand le treuil tourne a l'envers.",
  "SFX(drum_creak) une fois par tour"),
 "A-685": ("poulies et cordes du Marche. Plusieurs poulies sur une meme "
  "boucle, vitesses differentes selon le diametre. [OBL] Les rapports sont "
  "JUSTES : une petite poulie tourne plus vite qu'une grande, et un joueur "
  "mecanicien le verifiera.", "SFX(pulley) continu, faible"),
 "A-686": ("la grille est tiree au grappin. Elle monte par a-coups — trois "
  "saccades — parce que le mecanisme est grippe. Elle reste ouverte : "
  "aucune grille du chapitre ne se referme derriere le joueur.",
  "SFX(grille_rise) f0-f156 · RIG_CLINK f12, f70, f128"),
 "A-688": ("une porte s'ouvre en grincant. Le grincement est produit par la "
  "VITESSE ANGULAIRE, pas par un declencheur a frame fixe : une porte "
  "poussee doucement ne grince pas pareil.",
  "SFX pilote par la vitesse du rig"),
 "A-702": ("une trappe s'ouvre et ce qui est dessus tombe. 1,8 s, dont 0,6 s "
  "de basculement et 1,2 s de chute libre. La trappe rebondit une fois "
  "contre son cadre.", "SFX(trapdoor) f0 · SFX(wood_bounce) f54"),
 "A-703": ("la grande bache du Marche — l'une des 6 seules pieces de tissu "
  "en simulation cloth reelle du chapitre (05.38 V15). Le vent la gonfle et "
  "la relache sur un cycle de 8 s, jamais regulier.",
  "SFX(tarp_flap) aleatoire · vent partage avec le systeme global"),
 "A-704": ("rafale sur la bache. Elle claque une fois, fort, et se calme en "
  "1,4 s. [OBL] Declenche par le meme evenement de vent que A-706 et A-709 : "
  "quand il y a une rafale, TOUT le tissu de la zone reagit ensemble. "
  "Job CI `wind-coherence`.", "SFX(tarp_snap) f6"),
 "A-706": ("le linge claque dans une rafale. Le linge bleu est le repere "
  "visuel de l'etage 2 : il est visible depuis 6 points du chapitre, et "
  "cette animation est ce qui le rend reperable de loin.",
  "SFX(laundry_snap) f4 · meme evenement de vent que A-704"),
 "A-709": ("une corde pend et se balance. Amplitude 9 cm, amortissement "
  "lent. [OBL] Elle ne se balance PAS si le joueur ne l'a pas touchee et "
  "s'il n'y a pas de vent : rien ne bouge tout seul sans raison (regle de "
  "la partie 10).", None),
 "A-710": ("une corde sous tension vibre. Frequence 14 Hz, amplitude 4 mm, "
  "amortissement en 0,8 s. [OBL] La vibration est declenchee par un "
  "evenement de charge, jamais par une boucle d'ambiance.",
  "SFX(rope_thrum) f0-f24"),
 "A-712": ("un drapeau dechire dans le vent. La dechirure a une forme fixe, "
  "modelisee, pas procedurale : c'est toujours le meme drapeau, et il "
  "vieillit du meme cote.", "SFX(flag_flap) irregulier"),
 "A-714": ("des papiers s'envolent dans une rafale. VFX V14. [OBL] Ils "
  "retombent et RESTENT au sol : le systeme ne les despawne pas avant la "
  "sortie de zone. Un joueur qui revient les retrouve ou ils sont tombes.",
  "VFX(V14) f0-f90 · SFX(paper_scatter) f0"),
 "A-715": ("un buisson dans le vent. Vertex-anim, pas de squelette : c'est "
  "un prop a plus de 30 exemplaires par zone, donc MultiMesh obligatoire "
  "(regle PERF de la partie 10). Le clip sert de reference pour caler le "
  "shader.", None),
 "A-716": ("des algues sechees qui craquent au passage. Elles ne repoussent "
  "jamais. [NAR] Elles marquent le niveau d'eau d'avant, comme la ligne "
  "d'humidite de la pierre (12 m / 30 m), et comme elle, personne ne "
  "l'explique.", "SFX(dry_crackle) au contact uniquement"),
 "A-717": ("de la mousse qui goutte. Une goutte toutes les 2,1 s, jamais "
  "synchronisee avec les autres sources d'eau de la zone. [ART] La "
  "desynchronisation est ce qui fait qu'un lieu sonne habite plutot que "
  "boucle.", "SFX(drip) espacement irregulier 1,8-2,4 s"),
 "A-721": ("la lanterne portee oscille. [OBL] Synchronise avec le clip de "
  "marche lanterne en main : la lanterne suit la main avec 4 frames de "
  "retard, et l'ombre projetee suit la lanterne. C'est ce decalage en "
  "cascade qui donne du poids a un objet de 600 g.", None),
 "A-724": ("la lettre se replie en trois. L'inverse exact du depliage, mais "
  "PAS la meme animation jouee a l'envers : les plis ne se referment pas "
  "dans l'ordre ou ils se sont ouverts, et le papier resiste au dernier. "
  "[OBL] Synchronise a la frame pres avec le clip de Lohen qui la range.",
  "SFX(paper_fold) x3 · le dernier pli plus appuye"),
 "A-727": ("les 300 photos du mur bougent legerement dans le courant d'air. "
  "Amplitude 2 mm. [OBL] La photo de la rue des Lavandieres bouge comme les "
  "299 autres : aucun traitement particulier, aucun eclairage dedie, aucun "
  "ralentissement quand le joueur s'approche.", None),
 "A-728": ("la vapeur du the de Mireille. Elle monte, elle devie quand "
  "quelqu'un passe. [OBL] Elle s'arrete de monter quand le the refroidit — "
  "le prop a une temperature, et la scene S4 dure assez longtemps pour "
  "qu'on le voie.", None),
 "A-730": ("le rabat de la sacoche s'ouvre. 1 seconde, un seul mouvement, "
  "amorti par la lanière. [OBL] Joue a chaque fois que Lohen verifie sa "
  "sacoche (tic L5, 1 fois toutes les 3 minutes) : c'est donc l'un des "
  "clips de prop les plus vus du jeu, et il doit tenir au 47e visionnage.",
  "SFX(satchel_flap) f4 · CLOTH_RUSTLE f0"),
}
