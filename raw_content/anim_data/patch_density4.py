# -*- coding: utf-8 -*-
"""Cinquieme passe : fonds de scene (VAR/bg), locomotion et combat de Lohen,
props et decor. Meme regle : on ajoute de l'information d'animation, jamais
de la prose. Pour un prop, l'information utile est le pilotage (par quoi le
clip est declenche), la persistance (ce qui reste apres) et la contrainte de
boucle."""
ADD = {
# ---- LOHEN : locomotion / traversee / combat ----
"A-128": " Le bras retombe en 6 frames et la main reste ouverte une demi-seconde "
 "de plus, doigts ecartes. [OBL] Sobre : aucune pose de reception, aucun temps "
 "d'arret heroique. C'est le relachement le plus frequent du jeu et il ne doit "
 "jamais attirer l'oeil.",
"A-147": " Il baisse le harpon et expire d'un coup par le nez. Les epaules "
 "retombent de 3 cm. [OBL] Frustration CONTENUE : pas de juron corporel, pas de "
 "secouement de tete. Le joueur a annule, il le sait deja, le personnage n'a "
 "pas a le lui reprocher.",
"A-152": " Les bras lachent, le harpon descend jusqu'a la cuisse, il souffle. "
 "0,6 s de vulnerabilite reelle. [OBL] C'est la punition du joueur passif : "
 "elle doit etre lisible a l'avance, donc la garde tremble pendant les "
 "1,5 s qui precedent.",
"A-161": " Le buste se plie de 15 deg, un pas en arriere, retour en garde en "
 "9 frames. [OBL] Le retour en garde est RAPIDE : un stagger leger ne doit "
 "jamais voler le controle plus longtemps que la frappe qui l'a cause, sinon "
 "le joueur cesse d'attaquer.",
"A-180": " Il s'accroupit legerement, prend, se releve en regardant l'objet 0,3 s "
 "avant de le ranger. [OBL] Les 0,3 s de regard sont la difference entre "
 "ramasser un objet et recolter une ressource. Le jeu n'a pas de butin : il a "
 "des choses que les gens ont posees.",
"A-184": " Il cale un pied contre la base, tire a deux mains, le levier resiste "
 "puis cede d'un coup a f30 et il manque de tomber en arriere. [OBL] Le "
 "rattrapage n'est pas un gag : il dure 7 frames et le joueur reprend le "
 "controle avant la fin.",
"A-190": " Il pousse sur les cuisses pour se lever — il ne se leve jamais sans les "
 "mains, contrairement a Sol. Puis 0,4 s a regarder une derniere fois avant de "
 "se retourner. [OBL] Ce que regarde le personnage depend du banc : le systeme "
 "oriente la tete vers le point d'interet le plus proche, et il y en a un a "
 "chaque banc du chapitre.",
"A-194": " Il plie les genoux, pose, et reste accroupi 0,4 s a souffler, une main "
 "encore sur l'objet. [OBL] La main qui reste est l'information : l'objet a ete "
 "lourd, et le personnage a un rapport de soin avec ce qu'il transporte. Regle "
 "commune a tous les clips de portage.",
"A-202": " Un tour complet, rate de 20 deg, rattrape. Les deux rient (audio). "
 "[OBL] Declenche par la 3e pression du joueur, et l'erreur de 20 deg est "
 "SCRIPTEE : elle arrive quel que soit le timing. Aucune des quatre pressions "
 "de la danse ne peut echouer (07.20 / 09.15).",
"A-233": " Il trebuche en marchant a reculons, se rattrape d'un moulinet du bras "
 "gauche, et rit de lui-meme. Le rire est bref et sincere. [OBL] Il ne "
 "verifie pas si Lohen a vu. C'est la difference entre un homme qui joue et un "
 "homme qui seduit.",
"A-445": " Boucle de surveillance mefiante : le poids passe d'un pied sur l'autre "
 "toutes les 40 frames, la tete balaye 30 deg. [OBL] Aucune arme sortie. La "
 "mefiance de ce PNJ est sociale, pas martiale.",
"A-459": " Reception legere suivie d'une roulade d'amortissement, sans arret entre "
 "les deux. [OBL] La roulade conserve 80 % de la vitesse horizontale : elle "
 "sert a continuer, pas a se poser. Sol l'utilise, Lohen ne l'a pas.",
"A-466": " Iel se retourne en courant et sourit par-dessus l'epaule, 11 frames, "
 "puis repart. [ART] Le seul sourire franc de Sol dans le chapitre. Il n'est "
 "pas adresse a la camera et le joueur peut le manquer.",

# ---- FONDS DE SCENE : MIREILLE / TALLEC ----
"A-368": " Elle epoussette une etagere du plat de la main valide, sans monter sur "
 "l'escabeau. Elle ne nettoie que ce qu'elle atteint. [NAR] Les rayonnages "
 "hauts sont gris de poussiere depuis vingt-deux ans et le joueur peut le voir.",
"A-369": " Elle pousse l'echelle roulante d'une main et la freine du pied. "
 "[OBL] L'echelle roule REELLEMENT sur son rail et s'arrete ou elle l'a "
 "laissee : sa position persiste d'une visite a l'autre.",
"A-371": " Elle nettoie l'unique verre de ses lunettes avec un pan de son gilet, "
 "en trois passages circulaires. Elle les remet sans verifier. [OBL] Le verre "
 "manquant n'est jamais nettoye. Personne ne le mentionne.",
"A-372": " Elle boit son the seule, tasse tournee de 90 deg (tic M1), le regard "
 "dans le vide a 2 m. [NAR] C'est le seul clip ou elle ne fait rien d'autre. "
 "Il ne se declenche que si le joueur revient a un moment ou elle ne l'attend "
 "pas.",
"A-373": " Elle regarde par l'oculus, la lumiere de face. [ART] SEUL MOMENT DU "
 "CHAPITRE OU SON VISAGE EST ENTIEREMENT ECLAIRE. La main brulee reste dans "
 "l'ombre, du cote oppose. Aucune ligne, aucune musique.",
"A-375": " Elle compte des fiches par paquets de dix, en les tapotant sur la "
 "table pour les aligner. Elle recompte une fois. [OBL] Le recomptage est "
 "systematique : c'est une femme qui ne se fie pas a un seul passage, ce qui "
 "rend son mensonge d'autant plus delibere.",
"A-377": " Elle ramasse un livre tombe, regarde le dos, et le range AU BON "
 "ENDROIT, pas sur la pile. [OBL] Le rangement est exact : le joueur qui "
 "cherche ce livre plus tard le trouvera a cette place.",
"A-426": " Il inspecte une passerelle : il tape le platelage du talon tous les "
 "deux metres et ecoute. [OBL] Le son change reellement au-dessus des sections "
 "creuses, et c'est la meme information que Sol donnera en mots 40 minutes "
 "plus tard.",
"A-428": " Il distribue des rations, une par personne, jamais deux. Il regarde "
 "chaque visage avant de tendre. [OBL] La file avance reellement et se vide : "
 "un joueur qui reste voit la caisse se terminer, et le dernier de la file "
 "repart sans rien.",
"A-429": " Il repare une poulie d'une seule main, la prothese servant d'etau. "
 "[OBL] La prothese ne manipule pas : elle bloque. Toute la mecanique du "
 "personnage tient dans cette distinction, et elle vaut pour ses 58 clips.",
"A-430": " Il fume, adosse, la cigarette dans la main valide. Trois bouffees par "
 "cycle, espacees. [NAR] Il regarde toujours dans la direction du port "
 "englouti pendant qu'il fume. Il ne le sait probablement pas.",
"A-431": " Il regarde le Phare. Les bras retombent le long du corps, ce qui "
 "n'arrive dans aucun autre de ses clips. [OBL] Aucun presage, aucune musique, "
 "aucun mouvement de camera. Le Phare est eteint et le restera — interdit "
 "21.08.",
"A-432": " Il dort assis, le dos droit, la prothese en travers des genoux. Le "
 "sommeil est leger : il se reveille si le joueur s'approche a moins de 2 m, en "
 "2 frames, sans sursaut. [NAR] Un homme qui dort assis est un homme qui "
 "compte reprendre bientot.",
"A-433": " Il verifie une liste, du pouce valide, ligne par ligne. Il revient "
 "deux fois en arriere. [OBL] Le retour en arriere est le meme geste que "
 "lorsqu'il compte l'affiche des 641 noms et s'arrete a 31.",
"A-434": " Il aide une personne agee a monter une marche, en offrant la prothese "
 "comme appui — c'est a ca qu'elle sert le mieux. [ART] Le seul contact "
 "physique volontaire du personnage dans le chapitre.",

# ---- PNJ / DECOR ----
"A-633": " Trois coups, une pause, il retourne la piece. Le rythme ne varie "
 "jamais. [OBL] SFX synchronises sur les frames d'impact, pas sur la boucle : "
 "si le clip est ralenti par la charge, le son suit. Le joueur entend la forge "
 "depuis deux etages.",
"A-637": " Il porte une caisse sur 40 m, la depose, repart. Trajet NON BOUCLE : "
 "le PNJ a un depart et une arrivee reels. [OBL] Il croise le chemin du joueur "
 "sans l'eviter — c'est au joueur de se decaler, et c'est ce qui donne au "
 "Marche sa densite.",
"A-639": " Deux enfants a un jeu de cordes. [OBL] Les regles sont inventees mais "
 "VISIBLEMENT COHERENTES : la meme faute produit toujours la meme reaction. Un "
 "joueur qui regarde 30 s peut deviner comment on perd.",
"A-641": " Il repeint la meme porte, par bandes verticales, de haut en bas. "
 "[NAR] LA PORTE EST DEJA PEINTE. Aucun signe de detresse dans le geste : le "
 "clip doit etre entierement paisible, sinon il devient une metaphore et perd "
 "sa force.",
"A-643": " Il repare un filet de peche dont personne n'aura plus jamais l'usage. "
 "La navette passe, il serre, il verifie du doigt. [OBL] Le geste est expert et "
 "reellement juste : c'est un vrai noeud de ramendeur, pas une mime.",
"A-650": " La dispute se termine pendant que le joueur passe : l'un part, l'autre "
 "reste et regarde ailleurs. [OBL] ELLE NE RECOMMENCE PAS. Le PNJ qui reste "
 "bascule ensuite sur une boucle d'attente ordinaire et n'y revient jamais.",
"A-659": " Un enfant court et passe entre les jambes du joueur, une seule fois, "
 "scripte, en C03. [OBL] La collision est desactivee pendant 8 frames pour "
 "eviter de bloquer Lohen — c'est le seul endroit du chapitre ou on triche sur "
 "la collision d'un PNJ.",
"A-660": " Un enfant s'arrete et regarde Lohen. LONGTEMPS — 4,5 s, sans "
 "expression particuliere. Puis repart. [OBL] Aucun dialogue, aucun "
 "declencheur, aucune recompense. L'enfant ne reapparait pas ailleurs.",
"A-663": " Quelqu'un devant le mur de photos, immobile, une main a plat sur le "
 "mur. La main ne designe aucune photo en particulier. [NAR] Le clip ne dit pas "
 "s'il prie ou s'il attend, et le titre meme de l'animation laisse la question "
 "ouverte.",
"A-666": " Quelqu'un barre un nom sur la liste. [OBL] IL Y A 200 CROIX ROUGES AU "
 "DEBUT DU CHAPITRE ET 201 EN S7. La 201e est ajoutee hors champ : le joueur ne "
 "voit jamais ce clip se jouer sur la bonne ligne, il constate seulement la "
 "difference s'il compare.",
"A-667": " Un salut de tete a Lohen, bref, sans arret de la marche. "
 "[OBL] 7 PNJ ont ce comportement, toujours les memes, et ils le font a chaque "
 "passage. Le joueur finit par les reconnaitre.",
"A-668": " D'autres l'evitent : changement de trajectoire de 1,5 m, sans "
 "acceleration ni regard. [OBL] 4 PNJ, toujours les memes. NI CE "
 "COMPORTEMENT NI LE PRECEDENT N'EST EXPLIQUE, dans aucun dialogue du "
 "chapitre.",
"A-683": " Le contrepoids tombe quand le grappin le libere. Chute de 6 m, arret "
 "sec en bout de chaine, oscillation residuelle de 4 s. [OBL] Trois "
 "contrepoids a activer (09.12) : les trois utilisent le meme clip, et les "
 "trois restent en bas definitivement.",
"A-690": " Le tiroir du casier 4 (C05). [OBL] IL COINCE A MI-COURSE et il faut "
 "tirer plus fort — le clip comporte donc deux phases et un temps d'arret de "
 "0,5 s entre les deux. Ce coincement est la derniere resistance physique du "
 "chapitre avant la lettre.",
"A-692": " Une planche qui cede : flexion progressive, craquement, rupture. "
 "[OBL] 22 occurrences scriptees. La planche cassee RESTE cassee et le "
 "raccourci reste ferme pour le reste de la partie.",
"A-693": " Une passerelle qui bascule sous le poids. [OBL] L'angle depend de la "
 "position REELLE de Lohen sur la planche, pas d'un declencheur. C'est l'une "
 "des conditions de combat uniques (17.02 C4) : se battre dessus deplace le "
 "sol.",
"A-695": " La grande grue rouge, repere visuel de l'etage 3, tourne tres lentement "
 "dans le vent. Cycle de 48 s, jamais boucle a l'identique. [OBL] Visible "
 "depuis 9 points du chapitre : son orientation sert de boussole au joueur, "
 "donc elle doit changer assez lentement pour rester fiable.",
"A-700": " Une fissure progresse dans la Maree (S2, la fuite). [OBL] Animation de "
 "MASQUE DE SHADER, pas de mesh : la fissure suit une courbe peinte et sa "
 "vitesse est pilotee par la distance du joueur. Elle n'atteint jamais son "
 "terme tant qu'il n'a pas bouge.",
"A-705": " Le linge bleu, repere visuel de l'etage 2, visible depuis 6 points du "
 "chapitre. Ondulation lente, amplitude 8 cm. [OBL] La couleur bleue de ce "
 "linge est reservee : aucun autre tissu du niveau ne l'utilise, pour que le "
 "repere reste unique.",
"A-708": " La couverture pliee de la chambre du Phare, quand Lohen la soulève. "
 "[OBL] ELLE GARDE LE PLI — le tissu ne retombe pas a plat. Quelqu'un l'a "
 "pliee, il n'y a pas si longtemps, et le jeu ne dira jamais qui.",
"A-719": " La flamme de la lanterne au repos : VFX V22 plus un rig de 3 os pour "
 "le vacillement de la source lumineuse. [OBL] La lumiere projetee bouge avec "
 "les os, pas avec le VFX : ce sont les ombres portees qui vacillent, et c'est "
 "ce qui rend les couloirs vivants.",
"A-732": " Une frame : le piano droit au couvercle cadenasse. [NAR] IL NE S'OUVRE "
 "JAMAIS. Il n'y a pas de cle dans le jeu, aucun dialogue n'y fait allusion, et "
 "aucune quete ne le concerne. Il est la parce que la salle de bal en avait un.",
"A-735": " Les puits de chaleur de S2 : le verre s'assouplit visiblement, la "
 "surface ondule en cercles lents. [OBL] La zone molle est exactement la zone "
 "dangereuse — le rendu EST le signal de gameplay, il n'y a aucun marqueur "
 "d'UI par-dessus.",
"A-736": " Lohen s'enfonce. [OBL] COMPTE A REBOURS VISUEL : a 2,4 s c'est fini. La "
 "profondeur d'enfoncement est lineaire et lisible sur ses bottes — le joueur "
 "doit pouvoir estimer le temps qui reste en regardant ses pieds, sans jauge.",
"A-740": " S7. [OBL] LA PLUIE NE REBONDIT PAS SUR LA MAREE : elle GLISSE, en "
 "nappes, parce que la surface est tiede. C'est la seule surface du jeu qui "
 "traite la pluie ainsi, et c'est la derniere information que le joueur recoit "
 "sur la nature du verre avant BLOC 20.",
}
