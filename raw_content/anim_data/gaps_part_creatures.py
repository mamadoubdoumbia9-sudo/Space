# -*- coding: utf-8 -*-
"""Clips replies : Echassier 535-544, Mueur 567-583, Verrier/Anselme 627-635,
PNJ 674-678, et les trois demi-paires 25 / 28 / 519.
Sources : manifeste 6.x, 7.x, 8.E, 9.x. Master 06.19, 06.20, 09.11, 11.06."""

CLIPS = []
def C(**kw): CLIPS.append(kw)

# --- LES TROIS DEMI-PAIRES REPLIEES DANS UNE ENTREE L/R ----------------------
C(num=25, prefix="LOH-LOC", name="jog_R", dur=0.74, loop="BOUCLE", rm="RM-ROT (3.10 m/s)",
  layer="L0", prio=12, blend=[0.20,0.20],
  bones="jambes completes, bassin, colonne, bras, IK de pied",
  jeu="jog en diagonale a 45 deg vers la droite. Le buste anticipe la "
      "direction de 9 frames sur le bassin : c'est ce decalage qui fait que "
      "le personnage a l'air de DECIDER d'aller la-bas plutot que d'y "
      "glisser. Le bras gauche croise davantage que le droit.",
  evt="FOOTSTEP_L f0 · FOOTSTEP_R f11 · CLOTH_RUSTLE f6 · RIG_CLINK f14",
  trans="<- start_jog, walk_R ; -> jog_F, stop_walk_R, turn_run_90R",
  tag="MIROIR DE A-024", src="manifeste 1.B entree jog_L / jog_R")

C(num=28, prefix="LOH-LOC", name="run_R", dur=0.62, loop="BOUCLE", rm="RM-ROT (4.40 m/s)",
  layer="L0", prio=14, blend=[0.18,0.18],
  bones="jambes completes, bassin, colonne, bras, IK de pied, nuque",
  jeu="course en diagonale avec inclinaison laterale de 9 deg vers la droite. "
      "[OBL] L'inclinaison vient du BASSIN, pas de la colonne : incliner la "
      "colonne donne une course de patineur. La tete reste verticale — "
      "elle compense sur L3.",
  evt="FOOTSTEP_L f0 · FOOTSTEP_R f9 · CLOTH_RUSTLE f4 · BREATH toutes les 3 foulees",
  trans="<- run_F, start_run_burst ; -> run_F, turn_run_90R, jump_running",
  tag="MIROIR DE A-027", src="manifeste 1.B entree run_L / run_R")

C(num=519, prefix="ECH-LOC", name="turn_90_R", dur=2.6, loop="ONE-SHOT", rm="RM-ROT",
  layer="L0", prio=22, blend=[0.30,0.30],
  bones="4 pattes (chaines de 5 os), corps central, renflement",
  jeu="rotation vers la droite en deplacant les pattes une par une, en trois "
      "temps. LENT. [GAMEPLAY] Le joueur peut en profiter pour contourner : "
      "c'est une faiblesse DELIBEREE et LISIBLE. Le corps ne monte ni ne "
      "descend pendant la rotation — la stabilite horizontale est la "
      "signature de la creature.",
  evt="SFX(glass_leg_set) x4 · aucune secousse camera",
  trans="<- idle_standing, walk_patrol ; -> walk_slow, charge_run",
  tag="MIROIR DE A-518", src="manifeste 6.x entree turn_90_L / turn_90_R")

# --- ECH-VAR-535 a 544 -------------------------------------------------------
_ECH = [
 (535,"emerge_from_glass", 3.4, "ONE-SHOT", 40,
  "il emerge de la Maree. Le verre ne se brise pas : il s'ETIRE puis se "
  "referme derriere lui, sans laisser de trou. [OBL] Aucune animation de "
  "vertex sur le verre lui-meme (interdit 21.08) — l'effet est entierement "
  "dans le shader de la creature et dans PROP-744.",
  "VFX(V19) f0-f60 · SFX(glass_emerge) f8 · CAM_SHAKE(0.15) f20"),
 (536,"submerge_into_glass", 3.0, "ONE-SHOT", 40,
  "il redescend dans le verre et disparait. [NAR] C'est ainsi que se "
  "terminent les rencontres que le joueur fuit : la creature ne le "
  "poursuit pas indefiniment, elle rentre. Elle etait la avant lui.",
  "VFX(V19) inverse · SFX(glass_submerge) f30"),
 (537,"alert_freeze_and_pivot", 2.2, "ONE-SHOT", 42,
  "il se fige, puis le renflement pivote vers la source du bruit. Le corps "
  "ne bouge pas du tout pendant 0,9 s. L'immobilite totale precede toujours "
  "l'orientation : il ecoute avec son corps entier.",
  "SFX(listen_click) f26 · aucune musique ne doit demarrer ici"),
 (538,"lost_target_search", 4.6, "ONE-SHOT", 41,
  "il tourne sur lui-meme deux fois puis repart. [GAMEPLAY] C'est la fin "
  "d'un etat d'alerte : le joueur qui s'est cache peut ressortir. La "
  "lisibilite de la desescalade vaut mieux qu'une jauge de detection.",
  "SFX(glass_leg_set) x8 · pas de retour a l'idle avant f120"),
 (539,"walk_on_glass_gliding", 3.2, "BOUCLE", 20,
  "demarche differente sur le verre : il GLISSE. Les pattes ne se posent "
  "plus, elles ripent de 4 cm a chaque appui. [ART] Le meme personnage sur "
  "deux sols differents doit produire deux sons et deux lectures.",
  "SFX(glass_slide) continu · FOOTSTEP remplace par GLIDE_TICK"),
 (540,"climb_vertical_wall", 5.0, "BOUCLE", 24,
  "escalade de mur vertical. Il peut, et il le fait exactement DEUX FOIS "
  "dans le chapitre. [OBL] Pas trois. La rarete est la ressource.",
  "SFX(glass_scrape) par appui · CAM_SHAKE(0.08) si le joueur est a moins de 6 m"),
 (541,"group_subordination_posture", 8.0, "BOUCLE", 8,
  "quand deux Echassiers sont ensemble, LE SECOND SE TIENT 30 CM PLUS BAS. "
  "[NAR] Hierarchie improvisee, jamais expliquee, jamais mentionnee par "
  "aucun personnage. Le joueur la lit en une seconde et ne sait pas qu'il "
  "l'a lue.",
  "aucun · la posture EST l'information"),
 (542,"recoil_from_lantern", 1.8, "ONE-SHOT", 60,
  "il recule de deux pas devant la lanterne. Pas de peur jouee : un "
  "ajustement, comme quelqu'un qui evite une flaque.",
  "SFX(glass_leg_set) x2 · VFX(V07) sur les plaques eclairees"),
 (543,"idle_distant_LOD2", 10.0, "BOUCLE", 3,
  "idle lointain, boucle economique pour le decor. 4 os animes au lieu de "
  "22. [PERF] Utilise au-dela de 35 m, ou la difference est invisible et "
  "le gain est de 0,8 ms quand il y en a six a l'ecran.",
  "aucun evenement · aucun son"),
 (544,"cross_field_without_aggression", 11.0, "ONE-SHOT", 86,
  "[OBL] LA PREMIERE FIGURE QUE LE JOUEUR VOIT (S2, 09.11). ELLE REGARDE ET "
  "N'ATTAQUE PAS. Elle traverse le champ, s'arrete une fois, oriente le "
  "renflement vers Lohen pendant 1,6 s, puis continue et sort du cadre. "
  "[OBL] AUCUNE MENACE DANS CE CLIP : pas de tell, pas de musique de combat, "
  "pas de verrouillage de camera, pas de barre de vie. C'est ce qui rend "
  "TOUS LES COMBATS SUIVANTS INCONFORTABLES. Job CI `first-figure-passive` : "
  "verifie qu'aucun systeme de combat n'est arme pendant ce clip."),
]
for item in _ECH:
    n,name,d,loop,prio,jeu = item[:6]
    evt = item[6] if len(item)>6 else "aucun"
    C(num=n, prefix="ECH-VAR", name=name, dur=d, loop=loop, rm="RM-FULL" if loop=="ONE-SHOT" else "RM-ROT",
      layer="L0", prio=prio, blend=[0.30,0.30],
      bones="4 pattes (5 os chacune), corps central, renflement, mesh de coeur interne",
      jeu=jeu, evt=evt,
      trans="<- etat de l'IA d'Echassier ; -> idle_standing ou walk_patrol",
      tag="ECHASSIER · VARIANTE", src="manifeste 6.x bloc 535-544")

# --- MUE-VAR-567 a 583 -------------------------------------------------------
_MUE = [
 (567,"pack_waiting_posture", 9.0,"BOUCLE",8,
  "posture d'attente en groupe : ILS SE TOUCHENT. Epaule contre epaule, "
  "plaque contre plaque. [NAR] C'est la seule tendresse du bestiaire et "
  "elle appartient a l'ennemi le plus lache."),
 (568,"plate_clack_communication", 2.4,"ONE-SHOT",30,
  "un claquement de plaque en REPONSE a un autre. [OBL] C'est un langage. "
  "Il n'est JAMAIS explique, jamais sous-titre, jamais decode par aucun "
  "personnage. La structure des echanges est coherente : trois motifs "
  "distincts, reutilises, avec des reponses appropriees."),
 (569,"coordinated_encirclement", 6.0,"BOUCLE",44,
  "encerclement coordonne, 3 positions synchronisees. Les trois clips "
  "partagent une horloge commune : ils arrivent ensemble, jamais en file."),
 (570,"the_first_one_to_advance", 3.0,"ONE-SHOT",45,
  "celui qui teste. [OBL] TOUJOURS LE PLUS ABIME du groupe — le systeme "
  "trie par integrite de plaques et envoie le plus casse en premier. "
  "Personne ne le dit. Le joueur le remarque au troisieme combat."),
 (571,"collective_recoil_on_death", 1.2,"ONE-SHOT",62,
  "recul collectif quand un des leurs meurt : 1,2 s de FLOTTEMENT ou AUCUN "
  "n'attaque. [OBL] Cette fenetre est un cadeau au joueur et un probleme "
  "moral : elle recompense le fait de tuer, et elle montre le deuil."),
 (572,"scavenger_repair_self", 5.0,"ONE-SHOT",35,
  "il ramasse un fragment d'un mort et se le colle sur le corps. "
  "[NAR] IL SE REPARE AVEC SES SEMBLABLES. Aucun commentaire, aucun "
  "sous-titre, aucun succes."),
 (573,"ambient_crouched_in_corner", 12.0,"BOUCLE",4,
  "accroupi dans un coin. Zone sans combat. Il ne reagit pas au joueur qui "
  "passe a 3 m."),
 (574,"ambient_scratching_a_wall", 10.0,"BOUCLE",4,
  "il gratte un mur. Le meme endroit. Il y a une marque, et la marque est "
  "dans la texture du niveau."),
 (575,"ambient_watching_a_window", 14.0,"BOUCLE",4,
  "il regarde une fenetre. [NAR] La fenetre donne sur rien : elle est "
  "murée de l'autre cote. Il regarde quand meme."),
 (576,"ambient_rocking", 9.0,"BOUCLE",4,
  "il se balance. Amplitude 6 cm, cycle de 2,2 s. Exactement le rythme du "
  "balancement de Sol (S1), ralenti de moitie. [ART] La ressemblance n'est "
  "jamais soulignee et c'est pour ca qu'elle fonctionne."),
 (577,"ambient_facing_a_corner", 11.0,"BOUCLE",4,
  "immobile, face a un angle de mur, a 20 cm. Il ne bouge pas de la boucle."),
 (578,"ambient_sleeping", 16.0,"BOUCLE",3,
  "il dort. Les plaques se soulevent et retombent tres lentement. "
  "[OBL] S'il est attaque pendant ce clip, il ne se releve qu'a la "
  "deuxieme frappe."),
 (579,"reaction_to_grapple_sound", 2.0,"ONE-SHOT",58,
  "[GAMEPLAY] ILS DETESTENT CE BRUIT. Tous ceux qui sont a moins de 18 m "
  "jouent le clip en meme temps. Le joueur apprend a ne pas grappiner en "
  "furtivite, sans qu'aucun texte ne le lui dise."),
 (580,"flee_on_echassier_death", 3.4,"ONE-SHOT",64,
  "reaction a la mort d'un Echassier : ILS FUIENT TOUS. [NAR] La hierarchie "
  "du bestiaire est etablie en une seule animation, sans une ligne de "
  "dialogue ni une entree de codex."),
 (581,"curious_non_hostile_follow", 12.0,"BOUCLE",40,
  "[NAR] S5 : UN MUEUR SUIT LOHEN A 15 M SANS JAMAIS ATTAQUER, PENDANT "
  "TROIS MINUTES. Il s'arrete quand Lohen s'arrete. Il ne se cache pas. "
  "[OBL] Il ne devient jamais hostile, meme si le joueur l'attaque : il "
  "fuit. Une seule occurrence dans le chapitre."),
 (582,"doorway_blocking", 10.0,"BOUCLE",42,
  "il se met dans l'encadrement et ne bouge pas. Il faut le contourner ou "
  "le tuer. [OBL] Il n'attaque pas tant qu'on ne le touche pas : c'est un "
  "obstacle qui respire, et le joueur doit CHOISIR."),
 (583,"sitting_before_the_amber_mailbox", 20.0,"BOUCLE",39,
  "[NAR] LE DERNIER. Un Mueur assis devant la boite aux lettres repeinte en "
  "ambre (06.24). IL NE BOUGE PAS. [OBL] SI LE JOUEUR L'ATTAQUE, IL NE SE "
  "DEFEND PAS. Une seule occurrence, OPTIONNELLE, JAMAIS SIGNALEE : pas de "
  "marqueur, pas de son, pas de succes, pas de ligne de dialogue. "
  "[OBL] Aucun chapitre ne reviendra dessus. Job CI `mailbox-mueur-silent`."),
]
for n,name,d,loop,prio,jeu in _MUE:
    C(num=n, prefix="MUE-VAR", name=name, dur=d, loop=loop,
      rm="RM-NONE" if loop=="BOUCLE" else "RM-FULL",
      layer="L0", prio=prio, blend=[0.25,0.25],
      bones="72 os · colonne tres courbee, 4 membres, plaques de verre "
            "(chaine secondaire de 14 os de jiggle)",
      jeu=jeu,
      evt="SFX(plate_shift) aleatoire une fois par boucle · "
          "les plaques repoussent en shader, jamais en anim",
      trans="<- etat de meute ; -> idle_crouched, run_flee",
      tag="MUEUR · VARIANTE DE MEUTE", src="manifeste 7.x bloc 567-583")

# --- VER-ECH-627 a 635 : ANSELME AVANT LA MAREE ------------------------------
_ANS = [
 (627,"echo_blowing_glass", 22.0,"BOUCLE",
  "il souffle une piece de verre. Cycle de travail complet de 22 secondes : "
  "il cueille, il souffle, il tourne la canne en permanence, il rechauffe. "
  "[OBL] Le geste est EXACT. Un souffleur de verre doit reconnaitre le "
  "metier. La canne ne s'arrete JAMAIS de tourner — si elle s'arrete une "
  "seule frame, la piece tombe, et un professionnel le verra."),
 (628,"echo_laughing", 4.0,"ONE-SHOT",
  "il rit. [NAR] C'est le meme homme que le boss du chapitre. Le mesh est "
  "distinct (26 000 tris, non deforme) mais le visage est le meme, et c'est "
  "insupportable exactement pour cette raison."),
 (629,"echo_daughter_on_shoulders", 9.0,"BOUCLE",
  "il porte sa fille sur les epaules. Elle a 9 ans. Il tient ses chevilles "
  "des deux mains — les memes mains qui, dans 22 ans de temps de jeu, "
  "seront prises dans la coulee."),
 (630,"echo_teaching_the_cane", 12.0,"ONE-SHOT",
  "il lui montre comment tenir la canne de souffleur. Il corrige la "
  "position de ses doigts a elle, une fois, doucement. Elle ne comprend "
  "pas. Il recommence."),
 (631,"echo_wiping_his_forehead", 3.0,"ONE-SHOT",
  "il s'essuie le front de l'avant-bras, pas de la main : on ne touche pas "
  "son visage avec des mains de verrier."),
 (632,"echo_closing_the_workshop", 14.0,"ONE-SHOT",
  "il ferme l'atelier le soir. Il verifie le four, il eteint, il fait le "
  "tour. Le rituel prend 14 secondes et il est complet."),
 (633,"echo_looking_at_the_sea", 16.0,"BOUCLE",
  "[OBL] IL REGARDE LA MER PAR LA FENETRE. 14 OCTOBRE, 22 H. La Maree "
  "arrive a 23 h 41. Il ne voit rien. Il n'y a rien a voir. [OBL] Aucun "
  "presage (interdit absolu 21.08) : pas de musique inquietante, pas de "
  "reflet anormal, pas de silence soudain. C'est juste un homme fatigue qui "
  "regarde dehors avant de rentrer. Job CI `no-omen-633`."),
 (634,"echo_daughter_turns_to_lohen", 1.4,"ONE-SHOT",
  "[OBL 11.06] LA FILLE TOURNE LA TETE VERS LOHEN. C'EST LA SEULE EXCEPTION "
  "DU JEU A LA REGLE « LES FANTOMES NE REGARDENT JAMAIS LOHEN ». Elle le "
  "regarde pendant EXACTEMENT 1,4 SECONDE. Elle a 9 ans. ELLE NE DIT RIEN. "
  "Puis elle retourne a son jeu. [OBL] Aucun son, aucune musique, aucun "
  "effet, aucune reaction de Lohen, aucun sous-titre. [OBL] C'EST LE MOMENT "
  "LE PLUS EFFRAYANT DU CHAPITRE ET IL N'Y A AUCUN MONSTRE. "
  "Job CI `e23-single-exception` : verifie qu'aucun autre clip de fantome "
  "du jeu n'oriente un LookAt vers le joueur."),
 (635,"echo_shatters", 2.8,"ONE-SHOT",
  "l'Echo se brise. La scene se fissure COMME UN VITRAIL — les lignes de "
  "fracture suivent les plombs d'un vitrail reel, pas un pattern aleatoire — "
  "et le joueur est rejete. VFX V25."),
]
for n,name,d,loop,jeu in _ANS:
    C(num=n, prefix="VER-ECH", name=name, dur=d, loop=loop,
      rm="RM-NONE" if loop=="BOUCLE" else "RM-FULL",
      layer="L0+L4", prio=88, blend=[0.35,0.35],
      bones="squelette humain standard (mesh distinct, 26 000 tris, NON "
            "deforme — ce n'est pas encore le Verrier)",
      jeu=jeu,
      evt="ambiance d'atelier : four, verre, rue · [OBL] aucune musique "
          "pendant E23 · aucun liseré ambre (Esteban n'est pas la)",
      trans="<- entree dans E23 ; -> clip suivant de l'Echo, echo_shatters",
      tag="E23 (S7) · ANSELME AVANT LA MAREE",
      src="manifeste 8.E bloc 627-635")

# --- PNJ-674 a 678 : LES REACTIONS DE LIVRAISON ------------------------------
_PNJ = [
 (674,"delivery_reaction_02_refusal", 4.0,
  "il refuse la lettre. Les deux mains se levent a hauteur de poitrine, "
  "paumes vers Lohen. Il recule d'un pas. [NAR] Tout le monde ne veut pas "
  "savoir."),
 (675,"delivery_reaction_03_wrong_name", 5.0,
  "elle lit le nom, secoue la tete, rend la lettre. Elle la rend des DEUX "
  "mains : le respect de l'objet survit a l'erreur d'adresse."),
 (676,"delivery_reaction_04_silent_nod", 3.2,
  "il prend la lettre, hoche la tete une fois, et referme sa porte. "
  "[OBL] Aucun dialogue. Aucun remerciement. La porte se ferme en 0,8 s."),
 (677,"delivery_reaction_05_reads_and_sits_down", 7.0,
  "elle ouvre, lit trois lignes, et s'assoit par terre la ou elle est. "
  "[OBL] Le clip se termine AVANT toute reaction emotionnelle lisible. "
  "On ne reste pas. Lohen s'en va. Le joueur ne saura jamais."),
 (678,"delivery_reaction_06_already_knew", 6.0,
  "il prend la lettre sans la regarder et la pose sur une pile. La pile "
  "contient onze autres lettres, non ouvertes. [NAR] Le prop existe et il "
  "est zoomable. Personne n'en parle."),
]
for n,name,d,jeu in _PNJ:
    C(num=n, prefix="PNJ", name=name, dur=d, loop="ONE-SHOT", rm="RM-NONE",
      layer="L0+L4", prio=80, blend=[0.30,0.30],
      bones="squelette generique partage (96 os), compatible 3 morphologies",
      jeu=jeu,
      evt="SFX(paper_handle) · aucune musique · pas de LookAt prolonge "
          "vers le joueur (interdit de la partie 9)",
      trans="<- hand_over_letter_receive (672) ; -> routine du PNJ",
      tag="LIVRAISON DE LETTRE · reaction",
      src="manifeste 9.x bloc 674-678")
