# -*- coding: utf-8 -*-
"""Clips de Mireille Vandeck replies dans les blocs 337-368 et 370-381.
Sources : ANIM_MANIFEST_744.txt 3.x, dialogues/S4/*, master 06.16 / 10.07."""

CLIPS = []
def C(**kw): CLIPS.append(kw)

# --- MIR-ACT-337 a 368 : LES 32 LIGNES ---------------------------------------
# Regle de direction generale (manifeste) : elle ne hausse JAMAIS la voix.
# Quand elle veut appuyer, elle RALENTIT. L'actrice enregistre assise.
_LINES = [
 (337,"line_hands_of_a_man_who_touches", 4.2,"S4_MIR_L008",
  "« Vous avez les mains d'un homme qui touche trop de choses. » Elle REGARDE "
  "les mains de Lohen avant de parler : le regard descend a f0, la phrase part "
  "a f22. Premiere mention des mains par un tiers dans le chapitre. Le motif "
  "traverse tout le jeu et se referme dans la lettre (20.03)."),
 (338,"line_sit_down_youre_dripping", 2.4,"S4_MIR_L010",
  "elle designe la chaise du menton, jamais de la main. Economie de mouvement "
  "(regle du personnage) : le menton coute moins cher que le bras."),
 (339,"line_the_library_is_not_a_shelter", 3.0,"S4_MIR_L012",
  "elle corrige une erreur de categorie, pas une impolitesse. Ton de "
  "bibliothecaire, pas de gardienne."),
 (340,"line_what_are_you_looking_for", 2.2,"S4_MIR_L014",
  "sec. Pas de point d'interrogation dans la diction. Elle a pose cette "
  "question a beaucoup de gens et aucune reponse ne l'a encore surprise."),
 (341,"line_touching_is_greed", 3.6,"S4_MIR_L018",
  "« Toucher, c'est de la gourmandise. » [GESTE M1] Elle tourne sa tasse "
  "pendant la ligne. Le geste et la phrase se contredisent : elle est "
  "gourmande aussi, et elle le sait. La contradiction n'est jamais relevee."),
 (342,"line_the_registry_is_not_here", 2.8,"S4_MIR_L021",
  "surprise contenue : quelqu'un a pose la bonne question. Le sourcil gauche "
  "monte de 2 mm. C'est tout ce qu'elle laisse passer."),
 (343,"line_i_have_worked_here_since", 3.4,"S4_MIR_L024",
  "elle donne une date. Elle ne donne jamais de duree : les durees "
  "appellent des condoleances."),
 (344,"line_once_that_is_quite_enough", 2.6,"S4_MIR_L026",
  "« Un. Une fois. Ca suffit largement. » [GESTE M2] ELLE CACHE SA MAIN "
  "BRULEE PILE SUR CETTE LIGNE. [OBL] Le geste tombe sur « Une fois », pas "
  "avant, pas apres. C'est la seule fois du chapitre ou M2 est scripte au "
  "lieu d'etre declenche par le regard du joueur."),
 (345,"line_glass_remembers_heat", 3.8,"S4_MIR_L029",
  "elle parle du verre comme d'un materiau, pas comme d'un mystere. "
  "[OBL] Elle n'explique jamais la Maree. Elle explique le verre. "
  "La difference est tout le personnage (21.08)."),
 (346,"line_you_are_not_the_first", 3.0,"S4_MIR_L031",
  "elle ralentit sur « premier ». Le ralentissement est son seul outil "
  "d'insistance."),
 (347,"line_never_anything_new_in_there", 5.2,"S4_MIR_L033",
  "« On ne trouve jamais rien de neuf la-dedans. On trouve seulement la meme "
  "chose, mais en plus fort. » LA THESE DU JEU SUR LES ECHOS, dite par le "
  "personnage qui a le plus de raisons de le savoir. Retenu, presque doux. "
  "Elle regarde ailleurs sur la deuxieme proposition."),
 (348,"line_the_fourth_floor_is_eaten", 2.8,"S4_MIR_L035",
  "avertissement pratique donne sans dramatisation. Le plancher EST mange : "
  "le joueur peut tomber, et elle l'a dit."),
 (349,"line_do_not_thank_me_yet", 2.0,"S4_MIR_L037",
  "« Ne me remerciez pas encore. » Elle sait deja ce qu'elle va faire. "
  "[NAR] Le clip est joue 3 h 04 avant que le joueur comprenne."),
 (350,"line_tea_is_not_hospitality", 3.2,"S4_MIR_L040",
  "elle sert le the comme on remplit un formulaire. Le geste est precis, la "
  "main brulee reste du cote oppose au visiteur (regle de 319)."),
 (351,"line_people_look_for_a_sentence", 4.0,"S4_MIR_L398",
  "« Les gens cherchent une phrase. » Elle a vu passer des centaines de "
  "chercheurs et elle a une taxonomie. Elle l'enonce sans mepris."),
 (352,"line_locker_four_row_M", 3.0,"S4_MIR_L392",
  "« Casier 4, rangee M, troisieme tiroir. » [OBL] DITE TROP VITE, SANS "
  "CONSULTER QUOI QUE CE SOIT. Elle savait. Aucun regard vers un registre, "
  "aucune hesitation, aucun temps de recherche. [OBL] Le joueur attentif "
  "sent l'anomalie sans pouvoir la nommer. Job CI `mireille-knew` : verifie "
  "qu'aucune animation de consultation n'est blendee sur ce clip."),
 (353,"line_third_floor_touch_nothing", 2.6,"S4_MIR_L403",
  "« Ne touchez rien. » Dite a quelqu'un dont elle vient de dire que les "
  "mains touchent trop de choses. Elle s'en amuse une fraction de seconde."),
 (354,"line_they_are_short", 2.4,"S4_MIR_L399",
  "« Elles sont courtes. » Elle parle des fiches. Elle pourrait parler "
  "d'autre chose et elle le sait."),
 (355,"line_do_not_thank_me_second", 2.2,"S4_MIR_L401",
  "« Buvez. » Ordre deguise en soin. Elle ne repete jamais un ordre."),
 (356,"line_wait", 1.8,"S4_MIR_L404",
  "« Attendez. » Un mot. Elle vient de decider quelque chose pendant le "
  "silence precedent, et le clip commence par 12 frames d'immobilite."),
 (357,"line_i_just_lied_to_you", 6.0,"S4_MIR_L412",
  "« Parce que je viens de vous mentir sur une chose, et que vous allez "
  "mettre trois heures a comprendre laquelle. » [OBL] ELLE SOURIT SUR CETTE "
  "LIGNE. UNE SEULE FOIS DU CHAPITRE. Le sourire est triste, monte en "
  "20 frames, ne depasse pas 0.4, et ne redescend pas avant la coupe. "
  "[OBL] A la sortie de la Bibliotheque il reste exactement 3 h 04 de jeu. "
  "Elle a raison. Job CI `mireille-timing` verifie la duree restante."),
 (358,"line_the_grille_to_the_west", 3.4,"S4_MIR_L405",
  "elle indique la sortie par les conduits. C'est ainsi que Sol entre dans "
  "l'histoire, et elle ne le sait pas."),
 (359,"line_i_will_get_you_out", 2.8,"S4_MIR_L406",
  "sobre. Elle ne promet rien d'autre que ce qu'elle peut faire."),
 (360,"line_conv3_01_you_came_back", 2.8,"S4_MIR_L311",
  "troisieme conversation. Elle n'est pas surprise qu'il revienne. Elle "
  "etait debout avant qu'il entre (voir clip 381)."),
 (361,"line_conv3_02_the_folder_was_empty", 3.6,"S4_MIR_L313",
  "elle ne nie pas. Elle ne se justifie pas non plus. Le silence qui suit "
  "dure 1,8 s et il est dans le clip, pas dans le montage."),
 (362,"line_conv3_03_i_emptied_it", 2.2,"S4_MIR_L315",
  "[NAR] L'AVEU. Trois mots. Elle ne baisse pas les yeux. Sa main brulee "
  "reste visible pour la premiere fois de la scene : elle ne la cache plus, "
  "parce qu'elle a arrete de se proteger."),
 (363,"line_conv3_04_twenty_two_years_ago", 3.0,"S4_MIR_L316",
  "elle date son geste. Elle ralentit sur le chiffre."),
 (364,"line_conv3_05_it_was_not_for_you", 2.8,"S4_MIR_L318",
  "elle precise que ce n'etait pas dirige contre lui. C'est la chose la plus "
  "proche d'une excuse qu'elle produira."),
 (365,"line_conv3_06_i_will_not_explain", 2.6,"S4_MIR_L319",
  "[OBL] ELLE N'EXPLIQUE PAS. Le contenu du dossier n'est jamais revele, "
  "dans ce chapitre ni ailleurs (interdit absolu 21.08)."),
 (366,"line_conv3_07_go_up_the_city", 3.2,"S4_MIR_L412",
  "« Vous avez une ville a monter. » Elle renvoie Lohen au mouvement. Elle "
  "ne dit jamais « bonne chance »."),
 (367,"line_abandon_01_close_the_door", 2.0,"S4_MIR_L196",
  "ligne d'abandon (12.06) : jouee si le joueur s'eloigne pendant la "
  "conversation. Sec, sans reproche. Elle reprend son travail a f40."),
 (368,"line_abandon_02_it_will_keep", 2.4,"S4_MIR_L194",
  "seconde ligne d'abandon. « Ca attendra. » Elle a raison : le dialogue "
  "reste disponible. [OBL] Aucun contenu n'est jamais perdu par abandon."),
]
for n,name,d,line,jeu in _LINES:
    C(num=n, prefix="MIR-ACT", name=name, dur=d, loop="ONE-SHOT", rm="RM-NONE",
      layer="L0+L4", prio=84, blend=[0.25,0.25],
      bones="buste (assise ou debout), bras, main valide, main brulee, "
            "machoire, blendshapes",
      jeu=jeu,
      evt="Face : registre `dry` par defaut · aucune elevation de volume, "
          "jamais · si la ligne appuie, c'est le DEBIT qui ralentit",
      trans="<- idle_sitting_desk / idle_standing_cane ; -> ligne suivante, "
            "ou idle_listening_sharp",
      tag="DIALOGUE S4 · ligne %s" % line, line=line,
      src="manifeste 3.x bloc 337-368 + dialogues/S4")

# --- MIR-VAR-370 a 381 : LES 12 ACTIVITES D'ARRIERE-PLAN ---------------------
_BG = [
 (370,"bg_sorting_cards", 11.0,
  "elle classe des fiches. Le rythme est constant, quatre fiches par cycle. "
  "Elle ne regarde pas ce qu'elle fait : elle regarde la porte."),
 (371,"bg_dusting_shelf", 9.0,
  "elle epoussette une etagere. Un seul passage par planche. Elle ne "
  "repasse jamais deux fois : le temps lui coute."),
 (372,"bg_rolling_ladder", 7.0,
  "elle remonte l'echelle roulante d'un metre et la bloque. Elle ne monte "
  "pas dessus. Elle ne monte jamais dessus."),
 (373,"bg_stop_and_listen_to_the_building", 12.0,
  "elle s'arrete et ecoute le batiment. Immobilite quasi totale, tete de "
  "trois quarts. [NAR] La Bibliotheque craque et elle sait lire ces bruits "
  "comme d'autres lisent la meteo. Aucun dialogue ne l'explique."),
 (374,"bg_clean_single_lens", 6.0,
  "elle nettoie son unique verre de lunettes avec un pan de manteau. "
  "L'autre monture est vide et elle la nettoie quand meme."),
 (375,"bg_drinking_tea_alone", 8.0,
  "elle boit, seule. [GESTE M1] La rotation de 90 deg a lieu meme sans "
  "temoin. Un tic n'a pas besoin de public : c'est ce qui le distingue "
  "d'une pose."),
 (376,"bg_looking_through_oculus", 10.0,
  "elle regarde par l'oculus. La lumiere lui arrive de face. C'est le seul "
  "moment du chapitre ou son visage est entierement eclaire."),
 (377,"bg_mending_coat_patch_15", 14.0,
  "elle recoud une piece de son manteau — la quinzieme. Le manteau en avait "
  "quatorze au debut du chapitre (06.16). [OBL] La quinziemme piece est "
  "visible sur son mesh pour tout le reste du jeu apres ce clip. "
  "Job CI `coat-patch-15`."),
 (378,"bg_counting_cards", 9.0,
  "elle compte des fiches a voix basse, sans son audible. Les levres "
  "bougent. Le compte est juste : l'animateur a compte pour de vrai."),
 (379,"bg_sitting_doing_nothing", 14.0,
  "elle s'assoit et ne fait rien pendant 14 secondes. [ART] Le clip le plus "
  "difficile de la partie 3 : il doit rester lisible et habite alors qu'il "
  "ne contient qu'une respiration et deux micro-ajustements de poids."),
 (380,"bg_replacing_fallen_book", 5.0,
  "elle ramasse un livre tombe et le replace. Elle regarde le dos avant de "
  "le ranger, et elle le range au bon endroit du premier coup."),
 (381,"bg_standing_before_locker_four", 13.0,
  "[NAR] LA DERNIERE. Elle reste debout devant le casier 4 sans l'ouvrir. "
  "[OBL] Ce clip ne se declenche QUE si le joueur n'a pas encore trouve le "
  "dossier vide. Elle regarde le casier qu'elle a vide il y a vingt-deux ans. "
  "[OBL] Si le joueur la surprend, elle bascule en 320 (idle_hide_burned_hand) "
  "en 0,3 s de blend — la transition est brutale exprès. "
  "Job CI `locker-four-guard` : verifie la condition de declenchement."),
]
for n,name,d,jeu in _BG:
    C(num=n, prefix="MIR-VAR", name=name, dur=d, loop="BOUCLE", rm="RM-NONE",
      layer="L0", prio=78, blend=[0.45,0.45],
      bones="corps complet, economie de mouvement maximale (regle du personnage)",
      jeu=jeu,
      evt="SFX(coat_creak) une fois par boucle · aucune musique · "
          "aucun LookAt vers le joueur tant qu'il ne parle pas",
      trans="<- selection par `library_ambient_state` ; -> autre activite, "
            "ou idle_listening_sharp si Lohen approche a moins de 3 m",
      tag="ARRIERE-PLAN BIBLIOTHEQUE",
      src="manifeste 3.x bloc 370-381")
