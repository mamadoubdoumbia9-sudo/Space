# -*- coding: utf-8 -*-
"""Conception des clips d'Esteban replies dans des blocs de plage du manifeste.
Chaque clip est deplie en fiche autonome. Sources : ANIM_MANIFEST_744.txt
2.C / 2.D, dialogues/S6/DLG_S6_D04_la-danse.txt (lignes canoniques),
LOHEN_PROMPT_MASTER.txt 06.12, 11.06, 19.05, 20.03."""

CLIPS = []

def C(**kw):
    CLIPS.append(kw)

# --- EST-ACT-250 a 260 : LES 11 LIGNES DE LA DANSE ---------------------------
# Ordre-cle : chaque clip porte UNE ligne canonique de S6/D04.
_DANSE = [
 (250,"banter_dance_01_ils_jouent_la_valse", 2.0,"S6_EST_L095",
  "il entend la valse avant Lohen. La tete se tourne vers l'orchestre 6 frames "
  "avant la phrase : il ecoute d'abord, il parle ensuite. Le poids passe sur la "
  "jambe avant. C'est une invitation qui n'ose pas encore se formuler.",
  "Face : `warm` 0.6 · la tete pivote f0-f8 · M12 monte de 2 dB a f0"),
 (251,"banter_dance_02_je_danse_mal", 2.4,"S6_EST_L097",
  "il annonce sa propre mediocrite en premier — c'est sa technique pour "
  "desarmer Lohen. Haussement d'epaules a une seule epaule (la gauche). Le "
  "sourire gauche (E2) monte a 0.55, pas plus : il ne se moque pas de Lohen.",
  "Face : `tease` · SHRUG_L f18 · smirk_L 0.55 f22-f60"),
 (252,"banter_dance_03_statistiquement_moyens", 2.8,"S6_EST_L098",
  "LA LIGNE QUI DOIT FAIRE RIRE. Il compte sur ses doigts — un, deux — puis "
  "ouvre les deux paumes sur « moyens ». Le geste est celui d'un homme qui "
  "presente un theoreme. [OBL] 09.xx : « Le joueur doit RIRE. C'est un ordre "
  "de design. » Si ce clip ne fait pas sourire en playtest, il est refait.",
  "Face : `joy` 0.7 · COUNT_FINGERS f20, f34 · PALMS_OPEN f62"),
 (253,"banter_dance_04_si", 2.2,"S6_EST_L100",
  "un seul mot. Il ne bouge presque pas : sourcils hausses, menton de 3 deg "
  "vers le haut. Toute la replique est dans le visage. C'est le contrepoint "
  "exact du « Non » de Lohen, qui lui ne bouge rien du tout.",
  "Face : `tease` · BROW_RAISE f6-f30"),
 (254,"banter_dance_05_donc_c_est_oui", 2.6,"S6_EST_L102",
  "[NAR] LE SEUL PERSONNAGE DU JEU QUI SAIT LIRE LES « NON » DE LOHEN. Il le "
  "dit sans triomphe, comme un constat affectueux. Il avance d'un demi-pas sur "
  "« oui » et tend deja la main a mi-phrase : il a decide avant de demander.",
  "Face : `warm` · STEP_IN 0.4 m f40 · HAND_OFFER f52"),
 (255,"banter_dance_06_y_a_pas_de_pas", 3.0,"S6_EST_L104",
  "il explique en montrant : le pied droit marque le temps deux fois au sol. "
  "Pedagogie physique, pas verbale — c'est comme ca qu'on parle a quelqu'un "
  "qui ne lit pas bien, et Esteban le fait sans jamais le nommer (10.06).",
  "FOOTTAP f30, f54 · Face : `gentle`"),
 (256,"banter_dance_07_quatre_fois", 2.4,"S6_EST_L106",
  "« Quatre fois. Apres on s'assoit. » Il annonce la duree du contrat : "
  "c'est exactement le nombre de pressions du mini-systeme (09.15). Le joueur "
  "apprend la regle par le dialogue, jamais par un tutoriel.",
  "Face : `warm` · 4 fingers f28-f44"),
 (257,"banter_dance_08_tu_regardes_tes_pieds", 2.2,"S6_EST_L112",
  "il le remarque et le dit. Le menton de Lohen est corrige d'une main — la "
  "main ne touche pas, elle s'arrete a 4 cm. [OBL] Esteban ne force jamais "
  "physiquement Lohen dans tout le chapitre. Le geste s'interrompt seul.",
  "HAND_NEAR_CHIN f30, retrait f48 · Face : `gentle`"),
 (258,"banter_dance_09_tu_as_ferme_les_yeux", 2.4,"S6_EST_L116",
  "il a vu. Amusement franc. La tete s'incline de 12 deg pour chercher le "
  "regard de Lohen par en dessous. C'est un geste de gamin.",
  "Face : `tease` 0.8 · HEAD_TILT 12 deg f14"),
 (259,"banter_dance_10_menteur", 2.0,"S6_EST_L118",
  "dit avec une tendresse totale. Un seul mot, aucune accusation. Il rit sur "
  "l'expiration, pas sur la voix : le rire est un souffle, pas un son.",
  "Face : `joy` · BREATH_LAUGH f24 · SFX(laugh_soft) f26"),
 (260,"banter_dance_11_je_tourne_pour_deux", 2.8,"S6_EST_L123",
  "LA LIGNE DE RATTRAPAGE. Jouee si le joueur rate une pression. [OBL] Aucun "
  "son d'erreur, aucun retour negatif (09.15) : Esteban absorbe l'echec et "
  "continue. Il accentue sa propre rotation pour compenser celle de Lohen. "
  "Le joueur ne saura jamais qu'il a rate quelque chose.",
  "Face : `warm` · ROT_COMPENSATE f0-f84 · aucune haptique"),
]
for n,name,d,line,jeu,evt in _DANSE:
    C(num=n, prefix="EST-ACT", name=name, dur=d, loop="ONE-SHOT", rm="RM-NONE",
      layer="L0+L4", prio=90, blend=[0.25,0.25],
      bones="colonne complete, bras, mains, tete, machoire, blendshapes faciaux",
      jeu=jeu, evt=evt,
      trans="<- ghost_ballroom / banter precedent ; -> banter suivant, dance_step",
      tag="DANSE E21 · ligne %s" % line, line=line,
      src="manifeste 2.C bloc 250-260 + dialogues/S6/DLG_S6_D04")

# --- EST-ACT-271 a 278 : E16, LE BANC DE LA MEZZANINE ------------------------
_E16 = [
 (271,"echo_E16_bench_01_arrival", 4.0,
  "il arrive, s'assoit sur le banc sans demander, laisse 20 cm entre eux. "
  "Ces 20 cm sont tenus pendant les huit clips : ils ne se touchent jamais "
  "dans E16. Le joueur ne le remarquera qu'apres S6, quand il saura.",
  "CLOTH_RUSTLE f12 · SFX(bench_wood) f30 · pluie sur les vitraux, continu"),
 (272,"echo_E16_bench_02_the_bells_at_night", 6.5,
  "il parle du bruit des cloches la nuit. Les mains restent jointes entre les "
  "genoux, ce qui est ANORMAL pour lui (228 : il parle avec les deux mains). "
  "L'immobilite de ses mains est le seul signe qu'il a peur de quelque chose.",
  "Face : `anxious` 0.5 · aucune musique"),
 (273,"echo_E16_bench_03_growing_old_here", 8.0,
  "la peur de vieillir dans une ville qui coule. Il regarde droit devant, pas "
  "Lohen. [NAR] C'est la scene qui explique Ravenne 1 h 20 avant Ravenne, et "
  "personne ne la lit comme ca a la premiere ecoute.",
  "Face : `anxious` -> `careful` f120 · BREATH f90, f200"),
 (274,"echo_E16_bench_04_read_me_something", 3.6,
  "IL DEMANDE A LOHEN DE LUI LIRE QUELQUE CHOSE. Il tend un livre sans le "
  "regarder, comme une chose sans importance. [OBL] Il ne sait pas encore. "
  "Le geste est leger et c'est ce qui le rend cruel a la relecture.",
  "BOOK_OFFER f22 · Face : `gentle`"),
 (275,"echo_E16_bench_05_the_refusal_silence", 5.0,
  "LOHEN REFUSE. Esteban ne recoit qu'un « non » sec. 1,4 s de silence. Sa "
  "main reste tendue 18 frames de trop — le temps exact qu'il faut pour "
  "comprendre qu'on ne lui donnera pas de raison.",
  "HOLD_OFFER f0-f60 · retrait lent f60-f110 · Face : `careful`"),
 (276,"echo_E16_bench_06_he_does_not_insist", 4.4,
  "[OBL] IL N'INSISTE PAS. Il repose le livre a plat entre eux, page fermee. "
  "Il change de sujet dans la meme respiration. C'est un homme qui vient de "
  "decider de ne jamais reposer la question, et il tient parole tout le jeu.",
  "BOOK_DOWN f40 · Face : `neutral` f60 · BREATH f44"),
 (277,"echo_E16_bench_07_small_talk_recovery", 5.6,
  "il repart sur quelque chose de leger : le toit qui fuit, un nom de rue. "
  "Les mains recommencent a bouger. Le retour du geste est le signe que la "
  "gene est passee — et il est joue 40 frames trop tot pour etre credible, "
  "ce qui est voulu.",
  "Face : `warm` (force, 0.45) · les mains reprennent f30"),
 (278,"echo_E16_bench_08_exit_looking_up", 3.0,
  "il se leve et regarde le plafond de la Bibliotheque, l'oculus, la pluie "
  "dessus. Il ne dit rien pendant 1,1 s. Puis il part le premier.",
  "LookAt `oculus` f10-f42 · SFX(footstep_wood) f60"),
]
for n,name,d,jeu,evt in _E16:
    C(num=n, prefix="EST-ACT", name=name, dur=d, loop="ONE-SHOT", rm="RM-NONE",
      layer="L0+L4", prio=88, blend=[0.30,0.30],
      bones="colonne, bassin (assis), bras, mains, tete, blendshapes",
      jeu=jeu, evt=evt,
      trans="<- clip E16 precedent ; -> clip E16 suivant, turn_away_dissolve",
      tag="ECHO E16 JOUABLE 3 min 10 (11.04)",
      src="manifeste 2.C bloc 271-278")

# --- EST-ACT-279 a 284 : LE CARNET (E30) -------------------------------------
_CARNET = [
 (279,"notebook_voice_01_jour_4", 6.0,"Jour 4",
  "presque gai. Le debit est rapide, la voix monte en fin de phrase. C'est "
  "un homme qui vient d'arriver quelque part et qui trouve ca curieux."),
 (280,"notebook_voice_02_jour_61", 8.0,"Jour 61",
  "il compte. Le plaisir du denombrement est encore intact : il y a de la "
  "precision et un peu de fierte. La voix est stable."),
 (281,"notebook_voice_03_jour_140", 7.5,"Jour 140",
  "neutre. Premiere fois que le debit ralentit. Une respiration de plus que "
  "necessaire au milieu de la phrase."),
 (282,"notebook_voice_04_jour_300", 9.0,"Jour 300",
  "[NAR] IL ECRIT LE MATRICULE AU LIEU DU PRENOM. La voix ne commente pas. "
  "[OBL] L'acteur ne joue AUCUNE emotion sur cette ligne. Le fait suffit."),
 (283,"notebook_voice_05_jour_612", 11.0,"Jour 612",
  "« la lampe n'est pas un signal. C'est juste que je n'aime pas le noir. » "
  "[OBL] Dit sur le ton d'une precision administrative. C'est la ligne qui "
  "annule 4 heures d'espoir du joueur, et elle est dite comme une note de bas "
  "de page."),
 (284,"notebook_voice_06_jour_1049", 6.5,"Jour 1049",
  "« Je n'ai plus de papier. » Plat. Aucune inflexion. La voix a vieilli de "
  "six extraits. [OBL] Les six sont enregistres DANS L'ORDRE, en une session, "
  "sans pause (13.33)."),
]
for n,name,d,jour,jeu in _CARNET:
    C(num=n, prefix="EST-ACT", name=name, dur=d, loop="ONE-SHOT", rm="N/A",
      layer="VOIX SEULE", prio=88, blend=None,
      bones="aucun — piste audio pure + piste faciale VIDE",
      jeu="E30, LE CARNET (19.05). " + jeu + " [OBL] Aucun personnage a l'ecran : "
          "seulement les mains de Lohen et le papier. Le clip est compte dans les "
          "744 parce qu'il porte une direction d'acteur, pas parce qu'il porte des os.",
      evt="SFX(page_turn) en entree · aucune musique · synchronise avec PROP-731",
      trans="<- notebook_page_turn ; -> extrait suivant",
      tag="E30 · extrait %s · [OBL] texte source : master 19.05 uniquement" % jour,
      src="manifeste 2.C bloc 279-284")

# --- EST-VAR-286 a 297 : LES 12 FANTOMES DE LA SALLE DE BAL ------------------
_GHOST = [
 (286,"ghost_ballroom_01_talking_to_someone_A", 9.0,
  "il parle a quelqu'un qu'on ne verra jamais de face. Deux mains en "
  "mouvement, buste engage. Vu de 18 m, on ne distingue que la silhouette et "
  "l'amplitude — c'est suffisant pour lire « cet homme est en train de vivre »."),
 (287,"ghost_ballroom_02_talking_to_someone_B", 7.0,
  "meme situation, posture refermee : une main dans la poche, l'autre qui "
  "ponctue. Conversation moins interessante, et ca se voit."),
 (288,"ghost_ballroom_03_talking_to_someone_C", 11.0,
  "il ecoute plus qu'il ne parle. Hochements toutes les 2,5 s (registre 227). "
  "Il jette un regard vers la porte a f210 : il attend quelqu'un. Le joueur "
  "ne sait pas encore que c'est lui."),
 (289,"ghost_ballroom_04_laugh", 4.0,
  "il rit. Tete en arriere de 14 deg, epaules qui montent deux fois. [ART] "
  "Ce clip existe pour une seule raison : que le joueur ait vu Esteban rire "
  "AVANT de lui parler. Sans lui, Esteban n'est qu'un distributeur de texte."),
 (290,"ghost_ballroom_05_scanning_the_room", 8.0,
  "il cherche quelqu'un du regard. Balayage lent, deux arrets. [NAR] Il "
  "cherche Lohen. Le joueur ne peut pas le savoir a ce moment-la, et il le "
  "comprendra 40 secondes plus tard, retroactivement."),
 (291,"ghost_ballroom_06_drinking", 6.0,
  "il boit. Le verre est tenu par le pied, pas par le calice — detail de "
  "quelqu'un qui a travaille dans des lieux ou on apprend ca."),
 (292,"ghost_ballroom_07_adjust_sleeve", 5.0,
  "il rajuste sa manche gauche. Deux fois. La manche ne tombe pas mieux la "
  "seconde fois."),
 (293,"ghost_ballroom_08_watching_the_door_A", 10.0,
  "il regarde la porte. 3,2 s d'immobilite quasi totale — anormal pour lui "
  "(219 : il ne tient pas en place). L'anomalie est le contenu du clip."),
 (294,"ghost_ballroom_09_watching_the_door_B", 6.0,
  "variante courte : il regarde la porte, se detourne, et regarde encore "
  "1,4 s plus tard. Il se trouve ridicule d'attendre et il attend quand meme."),
 (295,"ghost_ballroom_10_greeting_someone", 4.5,
  "il salue quelqu'un de loin, une main levee a hauteur d'epaule, un signe "
  "de tete. Chaleureux et bref : ce n'est pas la personne qu'il attend."),
 (296,"ghost_ballroom_11_checking_watch", 5.5,
  "il verifie sa montre. [NAR] Il la remet dans sa poche sans avoir "
  "enregistre l'heure — le regard quitte le cadran avant d'avoir lu. Il "
  "recommencera 12 secondes plus tard s'il reste en vue."),
 (297,"ghost_ballroom_12_still_from_behind", 12.0,
  "immobile, de dos. La boucle la plus longue, la plus calme, et la seule ou "
  "on ne voit pas son visage. [OBL] C'est celle qui joue quand le joueur "
  "entre dans la salle pour la premiere fois (11.06 : les fantomes ne "
  "regardent jamais Lohen — ici, il ne le peut meme pas)."),
]
for n,name,d,jeu in _GHOST:
    C(num=n, prefix="EST-VAR", name=name, dur=d, loop="BOUCLE", rm="RM-NONE",
      layer="L0", prio=86, blend=[0.40,0.40],
      bones="corps complet, LOD1 (mains simplifiees au-dela de 12 m)",
      jeu=jeu,
      evt="aucun evenement · la foule de l'Echo le masque partiellement · "
          "pas de son propre (il est trop loin)",
      trans="<- selection aleatoire ponderee par la distance ; -> clip suivant "
            "du pool, ou approach_intimate si Lohen s'avance",
      tag="E21 · AVANT L'ABORDAGE",
      src="manifeste 2.D bloc 286-297")

# --- EST-VAR-298 a 302 : LES POSES DE PHOTO (1 frame) ------------------------
_PHOTO = [
 (298,"photo_pose_01_bell_workshop_group",
  "Esteban et un groupe d'ouvriers, atelier de cloches. Il est le troisieme "
  "en partant de la gauche et il regarde a cote de l'objectif. Pose reelle, "
  "eclairee et rendue en jeu, jamais une texture peinte : le joueur peut "
  "zoomer et la coherence doit tenir."),
 (299,"photo_pose_02_laughing_blurred",
  "Esteban seul, riant, photo floue. Le flou vient d'un vrai mouvement de la "
  "pose exportee, pas d'un filtre : la main est a mi-geste."),
 (300,"photo_pose_03_lavandieres_with_lohen",
  "[NAR] LA PHOTO. Esteban et Lohen, rue des Lavandieres. [OBL] C'est la "
  "SEULE image du jeu ou ils apparaissent ensemble en dehors d'un Echo. "
  "Punaisee au mur du Marche, en S3, a 1 h de jeu, A HAUTEUR D'YEUX. "
  "[OBL] Personne ne la mentionne. Aucun marqueur, aucun son, aucun succes. "
  "Job CI `photo-300-silent` : verifie qu'aucun declencheur n'y est attache."),
 (301,"photo_pose_04_back_top_of_stairs",
  "Esteban de dos, en haut d'un escalier. La seule des quatre ou on ne voit "
  "pas son visage — et celle qui ressemble le plus a la fin du chapitre."),
 (302,"photo_pose_05_npc_wall_set",
  "Les quatre poses de PNJ du meme mur (302a, 302b, 302c, 302d). Elles "
  "existent pour que les 9 photos lisibles ne soient pas toutes des photos "
  "d'Esteban : sans elles, le mur devient un panneau narratif au lieu d'un "
  "mur de deuil collectif. [RECONCILIATION] Ces quatre poses etaient "
  "numerotees 302-305 dans le manifeste ; elles sont regroupees sous un seul "
  "identifiant de clip pour aligner le total d'Esteban sur le contrat 07.20 "
  "(96 clips). Voir RECONCILIATION_744.txt."),
]
for n,name,jeu in _PHOTO:
    C(num=n, prefix="EST-VAR", name=name, dur=0.033, frames=1, loop="POSE FIGEE",
      rm="N/A", layer="N/A", prio=None, blend=None,
      bones="corps complet fige — une seule frame, posee a la main",
      jeu=jeu,
      evt="aucun · rendu en jeu, eclaire par la scene, zoomable",
      trans="aucune — clip de 1 frame monte sur un AnimationPlayer en pause",
      tag="MUR DE 300 PHOTOS (06.24)",
      src="manifeste 2.D bloc 298-305")

# --- EST-VAR-307 a 314 : LES HUIT INSERTS DE MAINS ---------------------------
_HANDS = [
 (307,"hand_insert_01_writing", 6.0,
  "il ecrit a la plume. Rythme rapide, poignet souple, UNE rature. [OBL] La "
  "rature est celle qui sera visible sur la lettre finale (05.23, canal G du "
  "masque d'encre) : le meme geste produit le meme defaut, trois heures plus "
  "tard. C'est la definition d'un monde coherent."),
 (308,"hand_insert_02_tuning_a_bell", 4.5,
  "il accorde une cloche. Trois frappes de maillet, puis la main a plat sur "
  "le bronze pour etouffer. Entre la deuxieme et la troisieme frappe, il "
  "attend 1,1 s : il ecoute. Le son est enregistre en foley reel."),
 (309,"hand_insert_03_folding_a_letter", 3.2,
  "il plie une lettre en trois. Le deuxieme pli resiste legerement. "
  "[OBL] Synchronise a la frame pres avec PROP-729 (rig de la lettre, 9 os)."),
 (310,"hand_insert_04_letter_into_coat_pocket", 2.4,
  "il pose une lettre dans une poche de veste. La poche n'est pas la sienne. "
  "Le jeu ne le dira jamais et le cadre ne montre jamais a qui appartient la "
  "veste. [OBL] Ne pas elargir ce plan, jamais, dans aucun chapitre."),
 (311,"hand_insert_05_cup_two_hands", 5.0,
  "il tient une tasse a deux mains. [ART] Le contraste avec Mireille (321, "
  "qui tourne la sienne de 90 deg) et avec Lohen (qui ne boit jamais rien "
  "assis) fait trois rapports differents au meme objet."),
 (312,"hand_insert_06_turning_pages", 4.0,
  "il tourne les pages d'un livre. Vite. Il lit vite. [NAR] Le motif des "
  "mains oppose ses doigts fins et precis a ceux de Lohen (06.12) pendant "
  "quatre heures, avant que la lettre ne dise pourquoi (20.03)."),
 (313,"hand_insert_07_stone_on_paper", 1.5,
  "[OBL] LE GESTE FINAL DU PERSONNAGE. Il pose une pierre plate sur une "
  "feuille. On ne voit QUE la main qui se retire — le geste n'est jamais "
  "montre en entier, dans aucun chapitre, sous aucune condition. "
  "La pierre est encore sur la table en S8 (PROP-730 : la lettre ne "
  "s'envole jamais). C'est le dernier acte connu d'Esteban et il dure "
  "45 frames."),
 (314,"hand_insert_08_pinching_a_candle", 2.0,
  "il eteint une bougie entre deux doigts. Sans hesiter, sans souffler. "
  "Un homme qui a travaille avec du verre chaud n'a plus peur d'une meche."),
]
for n,name,d,jeu in _HANDS:
    C(num=n, prefix="EST-VAR", name=name, dur=d, loop="ONE-SHOT", rm="N/A",
      layer="L1 (plan d'insert, camera dediee)", prio=92, blend=[0.10,0.15],
      bones="avant-bras, poignet, 5 doigts complets (3 phalanges), pouce oppose",
      jeu=jeu,
      evt="foley dedie, enregistre separement, micro tres proche (13.33)",
      trans="aucune — plan de coupe monte par la cinematique",
      tag="INSERT MAINS (06.12)",
      src="manifeste 2.D bloc 307-314")
