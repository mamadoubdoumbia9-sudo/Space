# -*- coding: utf-8 -*-
"""Deuxieme passe de densite, declenchee par le job CI `no-filler`.

Deux categories de defauts corriges ici :
  (a) FUITES DE PARSING — 12 fiches dont le champ « direction d'acteur »
      contenait en realite la ligne technique du manifeste (duree, RM, prio)
      parce que l'entree du clip etait un miroir sans texte propre.
      Ces fiches recoivent la direction qui leur manquait.
  (b) DIRECTIONS TROP COURTES — clips reels mais decrits en une demi-phrase.
      On developpe ce que la phrase impliquait deja. On n'invente pas un
      autre clip : on ecrit ce que l'animateur doit savoir pour l'animer.
"""

FIX = {
# ---------- (a) FUITES DE PARSING : MIROIRS SANS TEXTE PROPRE --------------
"A-048": "miroir de stop_walk_L : l'arret se fait sur le pied DROIT. Un pas "
 "et demi. Le dernier pied vient se poser A COTE de l'autre, jamais devant — "
 "poser devant donne un arret de patineur. Le buste continue de 6 cm puis "
 "revient. [OBL] Le choix du clip gauche ou droit depend de la phase du "
 "cycle de marche au moment du relachement : c'est ce qui evite le pas "
 "fantome que produisent 90 % des jeux a cet endroit.",
"A-053": "miroir de turn_in_place_90L : pivot vers la DROITE sur le talon "
 "gauche, le pied droit se souleve et se repose. La tete amorce la rotation "
 "5 frames avant le bassin, et le bassin 3 frames avant les epaules. Ce "
 "decalage en cascade est ce qui fait qu'un demi-tour a l'air decide plutot "
 "que telecommande.",
"A-056": "miroir de turn_run_90L : virage a DROITE en course. L'appui "
 "exterieur (pied gauche) mord le sol et le corps s'incline de 11 deg vers "
 "l'interieur. [OBL] L'inclinaison vient du bassin, jamais de la colonne. "
 "Le bras interieur se replie, l'exterieur s'ouvre pour contrebalancer.",
"A-103": "miroir de ledge_shimmy_L : deplacement lateral en suspension vers "
 "la DROITE. La main droite lache, avance de 28 cm, se repose ; le corps "
 "suit en pendule. Les pieds cherchent un appui a chaque cycle et le "
 "trouvent une fois sur deux. [OBL] L'epaule droite, abimee, reste 2 cm plus "
 "basse pendant tout le cycle : le deplacement vers la droite est donc "
 "legerement moins propre que vers la gauche, et c'est volontaire.",
"A-107": "miroir de ledge_corner_inner_L : passage d'angle RENTRANT vers la "
 "droite. Les deux mains se rapprochent, le corps se tasse contre l'angle, "
 "un pied cherche la nouvelle face. Le mouvement est fait de petits "
 "ajustements, pas d'un grand geste : personne ne franchit un angle "
 "interieur avec elegance.",
"A-109": "miroir de ledge_corner_outer_L : passage d'angle SORTANT vers la "
 "droite. Le plus expose des quatre : le corps s'ecarte de la paroi, une "
 "main seule tient pendant 9 frames, et le vide est visible derriere. "
 "[ART] Ces 9 frames sont le seul moment d'escalade ou le joueur retient "
 "son souffle, et elles ne sont jamais accompagnees de musique.",
"A-118": "miroir de wallrun_L : course sur paroi verticale a DROITE. Le "
 "corps est incline, les appuis sont hauts et brefs, la main exterieure "
 "frole la paroi sans s'appuyer. [OBL] Duree maximale 2,1 s : au-dela, la "
 "gravite reprend et le clip enchaine sur la chute. Le joueur doit SENTIR "
 "la limite arriver, donc le dernier demi-cycle perd 2 cm de hauteur.",
"A-388": "demi-tour lourd de Tallec, 90 deg. Il ne pivote pas : il fait "
 "trois appuis. Le bras prothetique ne participe pas au mouvement — il "
 "PEND et il retarde la rotation du buste de 4 frames. [OBL] La prothese "
 "est une chaine de 12 os rigides : elle ne se deforme jamais, elle suit "
 "en inertie.",
"A-587": "demi-tour du Verrier, 180 deg, 4,2 secondes. [ART] La duree EST "
 "le personnage : il n'est pas lent parce que c'est un boss, il est lent "
 "parce qu'il porte une coulee de verre de plusieurs centaines de kilos. "
 "La coulee balaye le sol pendant toute la rotation et arrive en retard de "
 "14 frames sur le corps. Le joueur a largement le temps de le contourner, "
 "et le contourner ne sert a rien.",
"A-560": "reaction a un coup lourd. Le Mueur est projete de 90 cm en "
 "arriere, les plaques du dos s'entrechoquent, il se rattrape sur une main. "
 "[ART] Il se recroqueville davantage a chaque coup encaisse : la posture "
 "de protection s'accentue au fil du combat, et c'est ce qui finit par "
 "rendre le joueur mal a l'aise.",
"A-562": "la mort du Mueur. Le corps se brise et les plaques partent en "
 "dernier, apres le corps, parce qu'elles etaient deja mortes. "
 "[OBL] Le drop d'objet tombe a f26, apres l'impact, jamais pendant : un "
 "objet qui apparait pendant une mort transforme un adversaire en "
 "distributeur.",
"A-020": "miroir de walk_R avec une difference volontaire : l'epaule droite "
 "— la mauvaise, celle de Saint-Jerome — reste plus haute de 2 cm pendant "
 "tout le cycle. Le balancement du bras droit est reduit d'un tiers. "
 "[OBL] Cette asymetrie est presente dans TOUS les clips de locomotion du "
 "chapitre. Aucun dialogue ne la commente. Un joueur qui compare deux "
 "captures peut la mesurer.",

# ---------- (b) DIRECTIONS TROP COURTES -----------------------------------
"A-036": "recul accroupi, tres lent, une main en arriere qui tatonne le sol. "
 "Le regard reste devant : il recule sans regarder ou il va, ce qui est "
 "exactement ce qu'on fait quand on a peur de ce qu'on a en face. La main "
 "arriere touche le sol une fois par cycle et cherche 6 frames avant de se "
 "poser.",
"A-038": "deplacement lateral accroupi vers la droite, pivot sur la plante "
 "des pieds, talons jamais au sol. Le harpon est tenu bas, au ras du sol, "
 "pour ne pas depasser de la couverture. C'est la posture la plus "
 "inconfortable du jeu a tenir, et le clip doit le faire sentir : les "
 "cuisses tremblent legerement en fin de cycle.",
"A-060": "descente rapide en position accroupie. Une main touche le sol a la "
 "fin pour stabiliser — elle n'est pas decorative, elle porte reellement du "
 "poids pendant 4 frames. Le harpon passe dans l'autre main en cours de "
 "mouvement, sans que le joueur ait a le demander.",
"A-182": "pousser une porte normale. Main a plat, poussee, il passe. "
 "[OBL] La main touche REELLEMENT la porte : l'IK de main se cale sur le "
 "panneau et la porte demarre sa rotation a la frame du contact, pas avant. "
 "Une porte qui s'ouvre avant d'etre touchee detruit la credibilite de tout "
 "un couloir.",
"A-241": "il se laisse tomber assis, sans precaution, jambes ecartees. "
 "L'assise est bruyante et le rebond du corps dure 8 frames. [ART] Esteban "
 "ne menage jamais son corps ni le mobilier : c'est le contraire exact de "
 "Mireille, qui s'assoit comme si elle avait mal — et les deux clips "
 "existent pour etre compares.",
"A-248": "il tend la main. [OBL] LE GESTE PRECEDE LA PHRASE DE 8 FRAMES. "
 "C'est la regle de tout le personnage : Esteban decide avant de parler, et "
 "le corps part en premier. Si l'animation est synchronisee sur la voix, le "
 "personnage devient un lecteur de repliques.",
"A-324": "version fatiguee de la marche a la canne, dans les escaliers de la "
 "Bibliotheque. Elle prend une marche a la fois, la canne d'abord, puis le "
 "pied valide, puis l'autre. Trois temps par marche. Elle ne s'appuie pas a "
 "la rampe : la rampe est du cote de sa main brulee.",
"A-328": "elle pivote autour de la canne comme autour d'un axe, en trois "
 "petits pas. Le corps ne se souleve pas. [ART] Economie totale : la "
 "rotation coute deux appuis et rien d'autre, la ou Lohen en depense quatre "
 "et Tallec cinq. Les trois demi-tours du jeu racontent trois corps.",
"A-332": "elle designe une direction avec la canne, pas avec le doigt. Le "
 "geste part de l'epaule et s'arrete net — aucun accompagnement, aucune "
 "insistance. Elle indique une fois. Si on n'a pas compris, elle ne "
 "recommence pas.",
"A-352": "« Buvez. » Ordre deguise en soin. Elle pousse la tasse de 4 cm "
 "vers Lohen et retire sa main immediatement. Elle ne repete jamais un "
 "ordre et elle ne verifie pas s'il obeit.",
"A-356": "sobre. Elle ne promet rien d'autre que ce qu'elle peut faire, et "
 "elle le dit debout, deja tournee vers la porte. La phrase est une "
 "logistique, pas un reconfort — c'est sa facon d'etre fiable.",
"A-360": "elle date son geste : vingt-deux ans. Elle RALENTIT sur le "
 "chiffre, seul outil d'insistance du personnage. Aucun regard vers Lohen "
 "pendant la ligne ; elle regarde le casier.",
"A-387": "il se rassoit de travers, comme il fait toujours (geste T2). "
 "L'angle est de 34 deg, le meme que dans tous ses clips assis. "
 "[NAR] Un homme qui ne se met jamais face a son interlocuteur est un homme "
 "qui a arrete de croire aux conversations. Personne ne le formule.",
"A-400": "il a entendu la demande cent fois. Fatigue, pas agace : le debit "
 "ne change pas, mais il ne leve pas les yeux de sa prothese, et le chiffon "
 "continue ses passages pendant toute la ligne.",
"A-419": "avertissement sur le dessous du Marche, donne comme une consigne "
 "de securite. [NAR] IL NE SAIT PAS QUE SOL Y VIT. Le joueur, lui, le sait "
 "deja : l'ironie est entierement a la charge du spectateur, et aucun "
 "personnage ne la releve.",
"A-456": "glissade sous un obstacle bas, sur le cote, en un seul mouvement. "
 "Sol ne ralentit pas avant : iel evalue en courant. Les orteils accrochent "
 "le sol a la sortie pour relancer la course sans temps mort.",
"A-458": "saut par-dessus un vide, sans hesitation ni preparation. [ART] "
 "Le contraste avec Lohen est le sujet du clip : Lohen prepare ses sauts, "
 "Sol ne les prepare pas. Un enfant qui vit ici depuis trois ans connait "
 "chaque ecart par coeur.",
"A-467": "iel s'assoit en tailleur d'un seul mouvement, sans les mains. "
 "Le corps descend en 11 frames. [ART] Personne d'autre du casting ne peut "
 "faire ca : Mireille a mal, Tallec est lourd, Lohen est raide. "
 "La souplesse de Sol est une information sur son age, pas une cascade.",
"A-474": "iel indique une direction du MENTON, jamais du doigt. Le geste est "
 "bref et un peu insolent. [NAR] Mireille designe a la canne, Tallec de la "
 "prothese, Sol du menton : trois personnages, trois facons de montrer, "
 "aucune explication.",
"A-478": "iel s'essuie le nez sur la manche trop longue. Le geste est si "
 "rapide qu'il passe presque inapercu, et il revient 9 fois dans le "
 "chapitre. [OBL] Aucun personnage ne le commente et aucun dialogue n'y "
 "fait allusion.",
"A-490": "iel accepte « nulle part » comme une reponse valide. Iel ne juge "
 "pas, iel ne s'etonne pas, iel enchaine. [NAR] C'est la premiere fois du "
 "chapitre que quelqu'un ne demande pas d'explication a Lohen, et c'est un "
 "enfant de 13 ans qui le fait.",
"A-517": "demi-tour tres lent de l'Echassier : 3,2 secondes de fenetre pour "
 "fuir. [GAMEPLAY] La lenteur est une faiblesse DELIBEREE et LISIBLE. Le "
 "corps ne monte ni ne descend pendant la rotation — la stabilite "
 "horizontale est maintenue meme dans le mouvement le plus long.",
"A-519": "il se redresse a pleine hauteur, 3,1 m. Intimidant sans etre "
 "theatral : pas de rugissement, pas de pose, pas de ralenti. Il se "
 "redresse comme on se leve. [ART] C'est ce refus du spectaculaire qui rend "
 "la creature credible plutot que decorative.",
"A-526": "il recule d'une demi-patte, le corps s'abaisse de 30 cm. Des "
 "fissures s'ouvrent dans le canal de dommage du shader. [OBL] Il ne crie "
 "pas : aucune Figure du jeu n'emet de cri de douleur. Le seul son est "
 "celui du verre.",
"A-544": "marche voutee, les mains pres du sol, le dos tres rond. "
 "[ART] La posture est celle de quelqu'un qui se protege, pas de quelqu'un "
 "qui chasse. C'est ce que le manifeste appelle « ce qui reste quand une "
 "personne se recroqueville trop longtemps », et ca doit etre lisible en "
 "silhouette a 20 m.",
"A-547": "il grimpe les murs verticaux sans effort apparent. Quatre appuis, "
 "aucune recherche de prise : il ne cherche pas, il sait. [ART] La facilite "
 "est plus inquietante qu'une difficulte, parce qu'elle dit que le mur "
 "n'est pas un obstacle pour lui — et qu'il n'y a donc nulle part ou "
 "monter pour lui echapper.",
"A-551": "recul hesitant quand il est seul ou face a la lanterne. Il fait un "
 "demi-pas en arriere, s'arrete, recommence. [GAMEPLAY] L'hesitation est "
 "reelle : l'IA teste plusieurs fois avant de fuir, et le joueur peut voir "
 "la decision se prendre.",
"A-574": "immobile, face a un angle de mur, a 20 cm. Il ne bouge pas de la "
 "boucle — seule la respiration souleve les plaques. [NAR] Le joueur peut "
 "rester a le regarder aussi longtemps qu'il veut. Rien n'arrivera. C'est "
 "l'un des clips les plus derangeants du bestiaire et il ne contient aucune "
 "menace.",
"A-586": "rotation lente en trois appuis. La coulee balaye le sol et "
 "deplace les debris qui s'y trouvent. [OBL] Les debris sont de vrais props "
 "physiques : la coulee les pousse reellement, elle ne passe pas au "
 "travers.",
"A-636": "elle accroche le linge bleu, repere visuel de l'etage 2 (09.02). "
 "Elle secoue chaque piece une fois avant de l'etendre. [OBL] Le linge "
 "qu'elle accroche est celui du prop A-706 : la meme piece de tissu, le "
 "meme shader, et elle restera accrochee pour le reste du chapitre.",
"A-642": "il recule et regarde son travail. Satisfait. Tous les jours. "
 "[NAR] La porte est deja peinte — il repeint la meme porte depuis la "
 "Maree. Le clip ne comporte aucun signe de detresse : il est reellement "
 "content, et c'est ca qui serre le coeur.",
"A-655": "balayer une passerelle au-dessus du vide. Le balai va jusqu'au "
 "bord et s'arrete a 10 cm : elle ne regarde jamais en bas et elle n'a pas "
 "besoin de regarder pour savoir ou est le bord.",
"A-658": "attendre dans une file : changer d'appui, regarder devant, "
 "avancer d'un pas quand la file avance. [OBL] La file avance REELLEMENT — "
 "elle n'est pas decorative. Un joueur qui reste deux minutes voit "
 "quelqu'un arriver au bout et repartir avec sa ration.",
"A-661": "une personne agee assise qui regarde la Maree. Elle ne bouge "
 "quasiment pas : deux respirations et un ajustement de chale sur 14 "
 "secondes. [NAR] Elle regarde dans la direction du port disparu. Personne "
 "ne lui parle et elle ne parle a personne.",
"A-664": "ajout d'une photo au mur. [OBL] IL Y EN A UNE DE PLUS A CHAQUE "
 "VISITE DU JOUEUR dans le Marche. Le compteur est persistant. Personne ne "
 "le mentionne, aucun dialogue n'y renvoie, et le mur est simplement plus "
 "charge a la fin du chapitre qu'au debut.",
"A-665": "debout devant l'affiche « RECHERCHE : 641 NOMS ». Il lit en "
 "suivant du doigt. [NAR] C'est le meme geste que Tallec qui compte ses 31 "
 "hommes, fait par un anonyme qui en cherche un seul.",
"A-669": "reception d'une lettre livree par Lohen (verbe V7). Les deux mains "
 "se tendent. [OBL] Le contact main-a-main est reel : les deux IK se "
 "rencontrent a la frame de l'echange, et la lettre change de parent a "
 "cette frame exacte, jamais avant.",
"A-687": "un volet arrache par le grappin ou par le vent. Il claque contre "
 "le mur, rebondit, et reste ouvert de travers. [OBL] Il ne se referme "
 "jamais tout seul : ce qui est casse dans Velmora reste casse.",
"A-691": "le montant de l'echelle ploie de 2 cm sous le poids. "
 "[OBL] Synchronise avec le clip de montee de Lohen : la flexion suit la "
 "position de ses mains, barreau par barreau. Sans ce prop, l'echelle est "
 "un decor ; avec lui, elle porte quelqu'un.",
"A-694": "le pont de cordes oscille. [OBL] PILOTE PAR LA POSITION DU JOUEUR, "
 "PAS PAR LE TEMPS. Un pont qui bouge tout seul est un decor anime ; un "
 "pont qui bouge parce qu'on marche dessus est une surface. L'amplitude "
 "maximale est au milieu de la portee.",
"A-701": "effondrement scripte d'un echafaudage, 30 morceaux pre-fractures. "
 "Les pieces tombent dans un ordre fixe, jamais aleatoire : la sequence a "
 "ete reglee pour rester lisible et pour ne jamais boucher le passage du "
 "joueur, quelle que soit sa position.",
"A-711": "les filets suspendus de S1, qui ne redescendront jamais. Ils "
 "derivent tres lentement — cycle de 7 s, amplitude 5 cm. [NAR] Ils sont "
 "restes accroches la ou ils etaient le 14 octobre, et la seule chose qui "
 "leur arrive depuis, c'est le vent.",
"A-718": "un rideau de perles que Lohen traverse. [OBL] Il reagit au passage "
 "du CORPS, pas a un declencheur de zone : chaque rang est deplace par la "
 "partie du corps qui le touche, et le bruit continue 2 s apres le passage.",
"A-726": "la page du carnet se tourne. E30, six occurrences, une par extrait "
 "lu. [OBL] Le tournage de page est declenche par LE JOUEUR, pas par la "
 "voix : c'est lui qui avance, et la voix d'Esteban s'arrete au marqueur "
 "suivant s'il s'arrete. Le rythme de la scene la plus importante du "
 "chapitre appartient au joueur.",
}
