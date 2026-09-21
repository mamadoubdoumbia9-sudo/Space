# -*- coding: utf-8 -*-
"""Clips de Tallec replies dans les blocs 397-428 et 429-439.
Sources : manifeste 4.x, dialogues/S3/DLG_S3_D04, S7/DLG_S7_D02, master 06.17/10.08.
Regle : il parle LENTEMENT, il finit toujours ses phrases, il ne s'interrompt
jamais, on ne lui demande jamais de crier. Il reste ASSIS jusqu'a 417."""

CLIPS = []
def C(**kw): CLIPS.append(kw)

_LINES = [
 (397,"line_zero_cent_quatorze", 2.4,"S3_TAL_L088",
  "« Tiens. Zero cent quatorze. » [OBL] IL NE LEVE PAS LES YEUX DE SA "
  "PROTHESE. Il reconnait Lohen a ses pas — le clip commence 14 frames avant "
  "la porte, la tete deja immobile. Il appelle Lohen par son matricule, comme "
  "au port, et c'est une forme d'affection deguisee en administration."),
 (398,"line_still_cleaning", 2.8,"S3_TAL_L090",
  "il continue de nettoyer la prothese pendant la ligne. Le chiffon fait "
  "trois passages. La parole n'interrompt jamais le travail."),
 (399,"line_no_longer_captain", 3.6,"S3_TAL_L091",
  "« Non. Plus capitaine. Plus de bateaux, plus de capitaine. » [GESTE T1] "
  "[OBL] LES DEUX COUPS SUR LA PROTHESE TOMBENT EXACTEMENT ENTRE LES DEUX "
  "PHRASES — f42 et f52, dans le silence, jamais sous un mot. "
  "Job CI `tallec-T1-timing`."),
 (400,"line_sit_or_dont", 2.2,"S3_TAL_L093",
  "il designe la chaise sans la regarder. [GESTE T2] Lui-meme est assis de "
  "travers, jamais face a son visiteur : l'angle est de 34 deg."),
 (401,"line_eleven_hours", 2.6,"S3_TAL_L095",
  "il sait depuis combien de temps Lohen est parti. Il compte les gens. "
  "C'est son metier maintenant."),
 (402,"line_what_did_you_come_for", 2.0,"S3_TAL_L098",
  "« T'es venu chercher quoi. » [OBL] PAS DE POINT D'INTERROGATION DANS LA "
  "DICTION. Ce n'est pas une question, c'est une sommation. La ligne descend "
  "en fin de phrase au lieu de monter."),
 (403,"line_everyone_wants_the_registry", 2.7,"S3_TAL_L099",
  "il a entendu la demande cent fois. Fatigue, pas agace."),
 (404,"line_the_registry_answer", 2.4,"S3_TAL_L101",
  "il repond avant la fin de la question de Lohen — la seule fois du "
  "chapitre ou il chevauche quelqu'un, et c'est parce qu'il connait la "
  "phrase par coeur."),
 (405,"line_four_metres_of_glass", 2.9,"S3_TAL_L102",
  "« Le Registre est sous quatre metres de verre. » Il donne un chiffre "
  "exact. Il donne toujours des chiffres exacts."),
 (406,"line_so_you_already_know", 2.3,"S3_TAL_L103",
  "« Alors tu sais deja que la reponse est non. » Il laisse 1,4 s apres la "
  "ligne. Le silence est dans le clip."),
 (407,"line_who_is_it_addressed_to", 2.0,"S3_TAL_L105",
  "« Elle est adressee a qui. » Premiere fois qu'il leve les yeux. Le "
  "mouvement de tete prend 18 frames : c'est lent pour un regard."),
 (408,"line_he_looks_at_the_letter", 3.0,"S3_TAL_L107",
  "[OBL] IL REGARDE LA LETTRE. LONGTEMPS. 2,2 s sans parole, dans le clip. "
  "L'acteur n'a rien a jouer : la camera tient et la duree fait le travail."),
 (409,"line_why_do_you_carry_it", 4.4,"S3_TAL_L108",
  "« Pourquoi tu la portes, alors. » La question la plus dure du chapitre, "
  "posee sans durete. Il repose le chiffon avant de la poser."),
 (410,"line_no_thats_your_excuse", 3.2,"S3_TAL_L109",
  "« Non. C'est ton excuse. » [OBL] IL SE LEVE SUR CETTE LIGNE. Le clip est "
  "synchronise avec TAL-ACT-389 (stand_up_finally). [OBL] Le mot « excuse » "
  "tombe PILE a la frame ou il atteint sa pleine hauteur — f78. "
  "Job CI `tallec-stands-on-excuse`. C'est l'evenement de la scene : il est "
  "reste assis pendant quatre minutes pour que cette frame existe."),
 (411,"line_your_tall_redhead", 2.8,"S3_TAL_L110",
  "« Ton grand roux. » [NAR 10.16] LA SEULE MENTION DIRECTE DU COUPLE PAR UN "
  "TIERS DANS TOUT LE CHAPITRE. [OBL] Dite SANS AUCUNE EMPHASE, comme on dit "
  "« ton frere ». Pas de pause avant, pas de regard appuye, pas de musique. "
  "C'est comme ca qu'on ecrit un couple qui existe : en n'en faisant pas un "
  "sujet. Job CI `redhead-no-emphasis` : verifie qu'aucun marqueur de "
  "musique ni de camera n'est attache a cette ligne."),
 (412,"line_i_know_who_you_look_for", 3.4,"S3_TAL_L112",
  "« Tu crois vraiment que je ne sais pas qui tu cherches ? » [OBL] PREMIERE "
  "ET UNIQUE FOIS OU IL ELEVE UN PEU LA VOIX. DE 2 dB. PAS PLUS. "
  "Job CI `tallec-volume-2db` : mesure le gain sur la piste."),
 (413,"line_i_lost_thirty_one", 3.0,"S3_TAL_L113",
  "il dit le chiffre de ses morts. Il ne dit pas « hommes », il dit le "
  "nombre. [NAR] Ce sont les 31 noms qu'il compte tous les jours sur "
  "l'affiche (clip 439)."),
 (414,"line_not_helping_you_find_him", 4.0,"S3_TAL_L114",
  "« Je ne t'aide pas a le trouver. Je t'aide a arreter. » [OBL] IL NE "
  "REGARDE PAS LOHEN. IL REGARDE SES PROPRES MAINS — la valide et celle de "
  "bois. La direction d'acteur est dans le JSON (12.02). La replique-cle du "
  "personnage (10.08) et son arc entier tiennent dans ce regard baisse."),
 (415,"line_take_the_anchor", 2.6,"S3_TAL_L115",
  "il donne l'ancre de son ancien harnais. Le don se fait de la main "
  "PROTHETIQUE : il donne avec la partie de lui qui ne sent rien, parce que "
  "l'autre tremblerait."),
 (416,"line_it_is_heavy", 2.2,"S3_TAL_L116",
  "avertissement technique sur le poids de l'objet. Il ne dit jamais ce que "
  "l'objet represente."),
 (417,"line_dont_thank_me_either", 2.4,"S3_TAL_L117",
  "sec. Il se rassoit immediatement apres : il est reste debout 41 secondes "
  "en tout, et c'est la totalite de son temps debout du chapitre."),
 (418,"line_the_market_holds", 2.8,"S3_TAL_L119",
  "il parle du Marche comme d'une coque qui prend l'eau. Vocabulaire de "
  "marin, toujours."),
 (419,"line_i_count_the_walkways", 2.1,"S3_TAL_L122",
  "il inspecte les passerelles lui-meme. Il ne delegue pas les choses qui "
  "peuvent tuer."),
 (420,"line_you_were_a_runner_at_nineteen", 3.6,"S3_TAL_L125",
  "[NAR] IL A ETE RELAYEUR A 19 ANS. C'est la seule raison pour laquelle il "
  "aide (10.08). Il le dit en passant, entre deux informations pratiques, et "
  "il ne developpe pas."),
 (421,"line_the_glass_took_the_port", 2.9,"S3_TAL_L127",
  "il decrit la Maree en termes de perte materielle. [OBL] Il ne l'explique "
  "jamais (21.08). Il enumere ce qu'elle a pris."),
 (422,"line_dont_go_under", 2.5,"S3_TAL_L129",
  "avertissement sur le dessous du Marche. Il ne sait pas que Sol y vit."),
 (423,"line_there_is_a_kid_down_there", 2.4,"S7_TAL_L035",
  "S7, sous la pluie. « Alors y'a une gamine qui y vit. » Il apprend "
  "l'existence de Sol et il encaisse l'information sans commentaire moral. "
  "[OBL] Le genre de Sol n'est jamais un sujet (06.18) : Tallec se trompe "
  "et personne ne le corrige."),
 (424,"line_the_wet_brake", 3.2,"S7_TAL_L038",
  "« Le frein mouille. » Il diagnostique le cable casse en trois mots. "
  "Competence pure, aucune emotion."),
 (425,"line_i_told_the_company_in_23", 4.2,"S7_TAL_L040",
  "« Je l'ai dit a la Compagnie en 'vingt-trois. Deux fois. » [NAR] Il a "
  "signale le defaut avant la catastrophe. Il ne dit pas que c'est pour ca "
  "qu'il compte des noms. Le joueur fait le lien seul, ou jamais."),
 (426,"line_they_answered_the_second_time", 3.8,"S7_TAL_L041",
  "« Ils ont repondu la deuxieme fois. » Puis, apres 1,2 s : « Que le frein "
  "etait conforme. » [OBL] Le silence entre les deux est le coeur de la "
  "ligne. Ne pas le raccourcir au montage."),
 (427,"line_take_the_bread", 2.2,"S7_TAL_L060",
  "« J'ai pas demande si t'avais faim. Prends le pain. » Il impose le soin "
  "comme un ordre, seule facon dont il sait le donner."),
 (428,"line_abandon_go_on_then", 2.0,"S3_TAL_L134",
  "ligne d'abandon (12.06). « Bon. » Il reprend le nettoyage de sa prothese "
  "a f30 comme si rien ne s'etait passe. [OBL] Aucun reproche : le contenu "
  "reste disponible."),
]
for n,name,d,line,jeu in _LINES:
    C(num=n, prefix="TAL-ACT", name=name, dur=d, loop="ONE-SHOT", rm="RM-NONE",
      layer="L0+L4", prio=85, blend=[0.28,0.28],
      bones="buste (assis de travers, 34 deg), bras valide, prothese "
            "(12 os rigides, contraintes strictes, JAMAIS de deformation), "
            "machoire, blendshapes",
      jeu=jeu,
      evt="Face : registre `heavy` · debit LENT · phrases toujours terminees · "
          "aucune interruption · voix la plus grave du casting, jamais criee",
      trans="<- idle_sit_crooked / clean_prosthesis ; -> ligne suivante",
      tag="DIALOGUE S3/S7 · ligne %s" % line, line=line,
      src="manifeste 4.x bloc 397-428 + dialogues/S3 & S7")

_BG = [
 (429,"bg_inspect_walkway", 12.0,
  "il inspecte une passerelle. Il appuie du pied, deux fois, aux deux tiers "
  "de la portee. Il connait l'endroit ou ca cede."),
 (430,"bg_shouting_offscreen", 8.0,
  "il engueule quelqu'un hors champ. [OBL] On n'entend pas les mots, "
  "seulement le grave. Le destinataire n'existe pas : il n'est jamais "
  "modelise, jamais montre, jamais nomme."),
 (431,"bg_distribute_rations", 16.0,
  "il distribue des rations. Une par personne, jamais deux. Il regarde "
  "chaque visage."),
 (432,"bg_repair_pulley_one_handed", 14.0,
  "il repare une poulie a une main — la prothese sert d'etau. Le geste est "
  "resolu depuis longtemps : aucune hesitation, aucune adaptation visible."),
 (433,"bg_smoking", 10.0,
  "il fume. La cigarette est tenue par la main de bois, ce qui est "
  "impraticable et qu'il fait quand meme."),
 (434,"bg_looking_at_the_lighthouse", 11.0,
  "il regarde le Phare. Il ne le commente jamais. [NAR] Il est le seul "
  "personnage adulte qui regarde le Phare sans rien en attendre."),
 (435,"bg_sleeping_sitting_up", 15.0,
  "il dort assis, de travers (T2 fonctionne meme endormi). Le souffle est "
  "lourd. Il se reveille si on approche a 2 m."),
 (436,"bg_checking_a_list", 9.0,
  "il verifie une liste avec l'index de bois. Il suit les lignes du doigt. "
  "[NAR] Le meme geste que le clip 439, sur un autre papier."),
 (437,"bg_helping_an_elder_up_a_step", 7.0,
  "il aide un vieil homme a monter une marche. [OBL] SANS UN MOT. Ni avant, "
  "ni pendant, ni apres. Les deux savent le faire depuis longtemps."),
 (438,"bg_before_the_amber_door", 13.0,
  "il reste devant une porte repeinte en ambre (06.24). Il ne la touche pas. "
  "[OBL] L'ambre est reserve a Esteban (`amber-guard`) : cette porte est la "
  "seule derogation de decor du chapitre, et elle n'est jamais expliquee. "
  "Tallec s'arrete devant sans qu'on sache s'il sait."),
 (439,"bg_counting_names_on_the_poster", 16.0,
  "[NAR] LA DERNIERE. Il compte les noms sur l'affiche « RECHERCHE : "
  "641 NOMS » avec son index de bois. IL EN EST A 31. [OBL] Ce sont ses "
  "31 hommes (10.08). Il les compte tous les jours. AUCUN DIALOGUE NE "
  "L'EXPLIQUE, dans ce chapitre ni ailleurs. Le doigt s'arrete sur le "
  "trente-et-unieme nom et redescend. Job CI `tallec-31` : verifie que le "
  "compte de l'animation vaut exactement 31 arrets."),
]
for n,name,d,jeu in _BG:
    C(num=n, prefix="TAL-VAR", name=name, dur=d, loop="BOUCLE", rm="RM-NONE",
      layer="L0", prio=78, blend=[0.45,0.45],
      bones="corps complet lourd, prothese 12 os rigides",
      jeu=jeu,
      evt="SFX(prosthesis_creak) une fois par boucle, aleatoire · "
          "pas de LookAt joueur",
      trans="<- selection par `market_ambient_state` ; -> autre activite",
      tag="ARRIERE-PLAN MARCHE SUSPENDU",
      src="manifeste 4.x bloc 429-439")
