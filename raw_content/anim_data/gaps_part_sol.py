# -*- coding: utf-8 -*-
"""Clips de Sol replies dans le bloc 483-510 (28 lignes de dialogue).
Sources : manifeste 5.C, dialogues/S3 S5 S7 S8, master 06.18 / 10.09.
CONTRAINTE D'ECRITURE 06.18 : quatre mots maximum par phrase.
GESTE S1 : iel se balance en permanence. GESTE S2 : immobilite totale quand
c'est grave. PIEDS NUS : orteils animes individuellement."""

CLIPS = []
def C(**kw): CLIPS.append(kw)

_LINES = [
 (483,"line_zero_un_un_quatre", 2.6,"S3_SOL_L059",
  "« Zero. Un. Un. Quatre. » Iel compte sur les doigts — QUATRE pressions de "
  "doigt pour quatre chiffres, une par syllabe. [OBL] Joue avec le clip 451. "
  "Iel lit le matricule de Lohen a voix haute parce que c'est la seule chose "
  "qu'iel peut lire dessus."),
 (484,"line_thats_a_number_not_a_name", 2.0,"S3_SOL_L061",
  "iel constate la difference entre un matricule et un prenom. Sans malice. "
  "Le balancement (S1) continue pendant toute la ligne."),
 (485,"line_i_can_read_numbers", 2.2,"S3_SOL_L063",
  "« Je sais lire les chiffres. » Ton de defi. Le menton monte de 8 deg. "
  "Iel s'attend a etre contredit·e et iel a deja la reponse prete."),
 (486,"line_letters_are_different", 1.8,"S3_SOL_L065",
  "iel nuance immediatement, ce qui ruine le defi precedent. Iel s'en "
  "apercoit et hausse les epaules."),
 (487,"line_i_said_letters_not_words", 3.0,"S3_SOL_L066",
  "« J'ai dit les lettres. Pas les mots. » [NAR] LA DISTINCTION QUE SOL FAIT "
  "ICI EST EXACTEMENT CELLE QUE LOHEN NE PEUT PAS FAIRE (10.06). Deux "
  "illettrismes differents qui se rencontrent. [OBL] AUCUN DES DEUX NE LE "
  "SAIT. Ni le dialogue ni la mise en scene ne le soulignent. "
  "Job CI `two-illiteracies-silent`."),
 (488,"line_you_carry_a_letter", 2.4,"S3_SOL_L067",
  "iel remarque la lettre. Iel ne demande pas ce qu'il y a dedans : "
  "Sol ne demande jamais rien (10.09)."),
 (489,"line_youre_a_bad_postman", 2.2,"S3_SOL_L069",
  "« T'es un mauvais facteur. » Dit en mangeant. La bouche pleine fait "
  "partie de la direction d'acteur, pas du bruit."),
 (490,"line_everyone_here_is", 2.0,"S3_SOL_L071",
  "iel generalise sa propre condition. Le balancement ralentit de moitie "
  "avant la ligne suivante."),
 (491,"line_here_everyone_is_that", 3.4,"S3_SOL_L073",
  "« Ca, ici, tout le monde l'est. » [OBL] JOUE AVEC LE CLIP 441 "
  "(immobilite totale, GESTE S2). IEL S'ARRETE DE BOUGER PILE AVANT LA "
  "LIGNE — 14 frames d'immobilite absolue en amorce. Le contraste avec "
  "l'agitation permanente fait toute la gravite. "
  "Job CI `sol-S2-stillness` : verifie l'amorce d'immobilite."),
 (492,"line_where_do_you_sleep", 1.6,"S3_SOL_L075",
  "« Tu dors ou. » Sans point d'interrogation dans la diction : Sol pose "
  "des questions comme on pose un objet."),
 (493,"line_nowhere_is_an_answer", 2.2,"S3_SOL_L077",
  "iel accepte « nulle part » comme une reponse valide. Iel ne juge pas."),
 (494,"line_everyone_says_no", 2.8,"S5_SOL_L031",
  "« Tout le monde dit non la premiere nuit. » Iel a vu passer des gens. "
  "L'information est donnee comme une meteo."),
 (495,"line_lower_the_lantern", 2.4,"S5_SOL_L277",
  "« Baisse la lanterne, tu vas les enerver. » Puis, apres un temps : "
  "« Enfin. Si y'en a. » [OBL] Iel se retracte toujours a moitie : c'est "
  "un enfant qui ne veut pas avoir tort."),
 (496,"line_my_brother_said", 3.2,"S5_SOL_L279",
  "« Mon frere disait qu'il en avait vu un. » Suivi de « Mon frere disait "
  "plein de trucs. » [NAR] Premiere mention du frere. Aucune emotion. "
  "Le demontage de sa propre source est immediat."),
 (497,"line_he_said_he_could_swim", 2.6,"S5_SOL_L281",
  "« Il disait aussi qu'il savait nager. » [OBL] LE JOUEUR COMPREND SEUL. "
  "Aucun temps de reaction n'est laisse, aucune musique ne souligne, "
  "Sol enchaine immediatement sur le clip suivant."),
 (498,"line_four_and_the_dog", 8.0,"S5_SOL_L136",
  "« Moi c'etait quatre. Ma mere, mon frere, ma tante, et le chien. Le chien "
  "s'appelait Pomme. » [OBL] LA LIGNE LA PLUS DIFFICILE DU CHAPITRE A DIRE. "
  "Direction : AUCUNE emotion. Iel ENUMERE. Le rythme est celui d'une liste "
  "de courses. [OBL] Iel continue de macher entre « tante » et « et le "
  "chien ». [OBL] Le nom du chien est dit EXACTEMENT comme les trois autres. "
  "[OBL] L'ACTEUR NE DOIT PAS MARQUER « POMME ». Si « Pomme » est joue avec "
  "de l'emotion, LA SCENE EST DETRUITE. [OBL] La musique M11 (guitare seule) "
  "ne demarre qu'APRES la ligne, sur le silence de Lohen. JAMAIS PENDANT. "
  "Job CI `pomme-flat` : la piste ne doit porter aucune inflexion sur le mot, "
  "et M11 ne doit pas se declencher avant la fin du clip."),
 (499,"line_you_say_nothing", 1.8,"S5_SOL_L139",
  "« Tu dis rien ? » Le balancement a repris. C'est le signe que la ligne "
  "precedente est derriere iel — ou qu'iel fait semblant."),
 (500,"line_theres_none", 1.4,"S5_SOL_L141",
  "« Y'en a pas. » Reponse a « Je cherche quelque chose a dire qui soit pas "
  "idiot. » Iel repond a la place de Lohen, vite, pour le soulager."),
 (501,"line_better_than_those_who_find", 3.0,"S5_SOL_L143",
  "« C'est deja mieux que les gens qui trouvent. » [NAR] Un enfant de 13 ans "
  "vient d'absoudre un adulte de son incapacite a consoler. Dit en regardant "
  "ailleurs."),
 (502,"line_you_talk_to_yourself", 2.0,"S5_SOL_L353",
  "« Tu parles tout seul. » Puis « Moi aussi. C'est pas grave. » Puis "
  "« C'est quand tu reponds que c'est grave. » Trois phrases, quatre mots "
  "maximum chacune, et une chute comique parfaite."),
 (503,"line_walk_in_the_middle", 2.2,"S5_SOL_L282",
  "« Marche au milieu, les cotes sont creux. » Information de survie donnee "
  "sans insistance. Iel sait ou tout se trouve (06.18)."),
 (504,"line_hollow_isnt_dangerous", 2.8,"S5_SOL_L283",
  "« Creux ca veut dire creux. » Iel corrige la peur de Lohen par du "
  "vocabulaire. C'est exactement ce que fait Mireille, en plus jeune."),
 (505,"line_your_boots_make_noise", 3.2,"S5_SOL_L284",
  "« Toi tu fais du bruit comme trois personnes. » / « C'est tes bottes. » / "
  "« Moi j'ai pas de bottes, alors je fais pas de bruit. » [OBL] PIEDS NUS : "
  "les orteils sont animes individuellement pendant cette ligne, et l'IK de "
  "pied epouse la tole du conduit."),
 (506,"line_212_metres", 2.0,"S7_SOL_L248",
  "« Y'a deux cent douze metres. » Chiffre exact. Sol donne toujours des "
  "chiffres exacts, comme Tallec, et personne ne releve la ressemblance."),
 (507,"line_theres_nobody_up_there", 2.4,"S7_SOL_L259",
  "« Y'a personne en haut. » Puis « Je monte pas. » Iel a raison sur les "
  "deux points et iel le sait."),
 (508,"line_then_why_do_you_go_up", 2.6,"S7_SOL_L270",
  "« Alors pourquoi tu montes. » [OBL] LA QUESTION QUE LE JEU POSE AU "
  "JOUEUR, placee dans la bouche du personnage le plus jeune. Aucune reponse "
  "n'est fournie, ni par Lohen ni par le jeu."),
 (509,"line_four_metres_less", 3.0,"S8_SOL_L013",
  "« Quatre metres de moins. Je l'ai dit. » Puis « Ca veut dire que les "
  "sauts que tu faisais avant, tu les fais plus. » [OBL] Iel annonce la "
  "reduction de portee du grappin AVANT que le joueur la subisse. "
  "Le level design de S8 est concu pour 18 m (voir grapple_cable_mesh.gd)."),
 (510,"line_i_wait_here", 4.0,"S8_SOL_L032",
  "« Ca, c'etait pas idiot. » / « Je t'attends ici. » / « J'ai pas dit que "
  "je t'attendais pour toi. » [OBL] LA DERNIERE REPLIQUE DU PERSONNAGE DANS "
  "LE CHAPITRE. Dite en haussant les epaules (clip 480), EN REGARDANT "
  "AILLEURS. C'est une declaration d'affection deguisee en insolence, et "
  "c'est exactement comme ca qu'un enfant de 13 ans dit « tu comptes pour "
  "moi ». [OBL] Aucune musique. Aucun ralenti. Lohen ne repond pas."),
]
for n,name,d,line,jeu in _LINES:
    C(num=n, prefix="SOL-ACT", name=name, dur=d, loop="ONE-SHOT", rm="RM-NONE",
      layer="L0+L4", prio=85, blend=[0.22,0.22],
      bones="squelette allege 112 os · colonne, bras, mains, tete, machoire, "
            "10 os d'orteils (pieds nus), blendshapes",
      jeu=jeu,
      evt="Face : registre `kid` · [OBL] GESTE S1 : le balancement d'appui "
          "continue SAUF si la ligne porte S2 · IK de pied actif en permanence",
      trans="<- idle_fidget / walk ; -> ligne suivante, action_shrug_full",
      tag="DIALOGUE · ligne %s · max 4 mots par phrase (06.18)" % line, line=line,
      src="manifeste 5.C bloc 483-510 + dialogues/S3 S5 S7 S8")
