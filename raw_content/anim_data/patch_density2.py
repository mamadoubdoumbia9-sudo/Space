# -*- coding: utf-8 -*-
"""Troisieme passe : directions d'acteur des clips PARLES (ACT/ECH/CIN).

Regle appliquee : sur un clip qui porte une replique, la direction doit dire
au moins (a) ce que fait le CORPS, (b) ou vont les YEUX, (c) le piege de jeu
a eviter. Les entrees ci-dessous etaient conformes au manifeste mais ne
disaient qu'une de ces trois choses. On complete les deux autres. On n'ajoute
aucune phrase qui ne porte pas d'information d'animation.
"""
ADD = {
# ---------------- ESTEBAN ----------------
"A-246": " Il compte sur ses doigts sans s'en rendre compte : quatrieme lettre, "
 "quatre doigts, et il les baisse un par un. Les yeux restent sur Lohen tout du "
 "long. [OBL] Ne pas jouer la nostalgie : au moment ou il dit cette phrase, ce "
 "n'est encore qu'une plaisanterie sur la frequence du courrier.",
"A-247": " Les yeux descendent sur la bouche de Lohen pendant 6 frames avant de "
 "remonter. [OBL] Piege a eviter : ne pas jouer « je suis amoureux ». Il est "
 "simplement content, et le spectateur fait le reste du travail.",
"A-249": " Le haussement part des deux epaules en meme temps et retombe lentement, "
 "sur 11 frames. Les yeux ne quittent jamais la main qu'il vient de prendre. "
 "[OBL] Il ne cherche pas l'accord de Lohen : la main est prise avant que la "
 "reponse puisse arriver, et c'est ce qui rend le geste possible.",
"A-258": " Le buste se penche en avant de 7 cm en meme temps que la tete "
 "s'incline. Les sourcils montent une fois, vite. [OBL] Aucun triomphe dans le "
 "regard : il n'a pas surpris Lohen en faute, il l'a trouve attendrissant.",
"A-259": " Le corps est deja en train de repartir dans le pas de danse quand le "
 "mot tombe — la ligne n'interrompt rien. Les yeux se ferment un quart de "
 "seconde sur l'expiration. [OBL] Un seul mot, aucune accusation : si le mot "
 "est appuye, la scene devient un reproche et le chapitre entier change de ton.",
"A-262": " Le geste s'arrete completement pendant la ligne : c'est le seul moment "
 "de la danse ou Esteban est immobile. Les yeux cherchent ceux de Lohen et ne "
 "les trouvent pas. [OBL] Pas de colere, pas de supplication. De la peine, et "
 "la main qui reste tendue.",
"A-264": " Le demi-pas en arriere n'est pas decide : les jambes reculent, le buste "
 "reste. Les yeux tombent sur le sol a 1 m devant lui. [OBL] Il a raison et il "
 "en souffre — jouer la justesse, jamais la victoire.",
"A-269": " Il repose la main de Lohen a plat, paume vers le bas, comme on repose "
 "un objet fragile sur une table. Puis il leve enfin les yeux a f100, une "
 "seconde pleine, et s'ecarte. [OBL] Cette seconde est la derniere fois que le "
 "joueur voit les deux visages dans le meme plan avant BLOC 20.",
"A-278": " Le menton monte de 22 deg, la gorge s'expose, les epaules descendent. "
 "1,1 s de silence avant qu'il ne parte. [OBL] Il part LE PREMIER. Dans chaque "
 "Echo, c'est toujours Esteban qui se leve en premier, et le joueur finit par "
 "l'anticiper sans qu'on le lui ait jamais dit.",
"A-287": " La main libre ponctue trois fois, toujours au meme endroit de l'espace, "
 "a hauteur de taille. Les yeux glissent vers la porte deux fois par cycle. "
 "[OBL] La posture refermee est l'information : c'est le meme homme que dans la "
 "variante A, dans une conversation qu'il subit.",
"A-291": " Le poignet est souple, le verre ne tremble pas. Il boit une gorgee "
 "courte et repose le verre avant d'avoir fini d'avaler. [NAR] Tenir un verre "
 "par le pied est un savoir d'employe de maison, pas d'invite : le clip dit "
 "d'ou il vient sans qu'une ligne ait a le dire.",
"A-292": " Le regard suit la manche la premiere fois, puis ne la suit plus la "
 "seconde. [OBL] La manche ne tombe pas mieux : le second ajustement echoue "
 "exactement comme le premier, et il n'y a pas de troisieme essai.",
"A-293": " Seules la respiration et un battement de paupieres bougent pendant "
 "3,2 s. Le poids est sur la jambe gauche et n'en change pas. [OBL] L'anomalie "
 "EST le contenu : c'est l'homme qui ne tient pas en place, arrete net.",
"A-294": " Le detournement est rapide, le second regard est lent. Entre les deux, "
 "il regarde son verre sans le voir. [OBL] Il se trouve ridicule d'attendre et "
 "il attend quand meme — les deux etats doivent etre lisibles dans la meme "
 "boucle de 1,4 s.",
"A-295": " La main monte a hauteur d'epaule, s'ouvre, redescend en 9 frames. Le "
 "signe de tete part avant la main. [OBL] Chaleureux et BREF : le corps est "
 "deja retourne vers la porte quand la main redescend. Ce n'est pas la personne "
 "qu'il attend, et ca se voit a la duree du geste.",
"A-299": " La main est a mi-geste et le visage est deja en train de changer "
 "d'expression : la photo a ete prise trop tot, comme toutes les bonnes photos. "
 "[OBL] Le flou est un vrai deplacement de la pose exportee, pas un filtre de "
 "post-traitement — l'artiste doit livrer la pose intermediaire elle-meme.",
"A-301": " Les epaules sont carrees, une main sur la rampe, le poids sur la marche "
 "du haut. Rien n'indique qu'il va se retourner. [ART] C'est la seule des "
 "quatre ou on ne voit pas son visage, et c'est celle que le joueur regardera "
 "le plus longtemps apres BLOC 20.",
"A-306": " Les doigts marquent chaque pli de l'ongle du pouce avant de l'ecraser. "
 "Le deuxieme pli resiste et il insiste une fois. Les yeux restent sur le "
 "papier. [OBL] Synchronise a la frame pres avec le rig de la lettre (9 os) : "
 "le papier plie parce que les doigts le plient, jamais l'inverse.",
"A-311": " Le pouce et l'index se ferment sans hesitation ni preparation, et le "
 "geste ne s'accompagne d'aucune grimace. La main repart immediatement. "
 "[NAR] Un homme qui a travaille avec du verre chaud n'a plus peur d'une meche "
 "— information donnee en 14 frames, jamais par un dialogue.",

# ---------------- MIREILLE ----------------
"A-330": " Le tiroir sort de 40 cm et elle le retient de la hanche pour garder les "
 "deux mains libres. Les yeux vont aux intercalaires, pas aux fiches. "
 "[OBL] Elle sait exactement ou elle cherche : aucune hesitation, aucun "
 "balayage du regard.",
"A-331": " Les 14 frames d'hesitation sont la ligne : la cle est deja dans la main "
 "de Lohen et elle ne l'a pas encore lachee. Elle regarde la cle, pas lui. "
 "[OBL] Puis elle lache et retire sa main vite, comme si le geste avait ete "
 "trop long — jouer le retrait, pas l'emotion.",
"A-333": " Le registre se ferme d'un coup sec, les deux paumes a plat dessus, et "
 "elle ne sursaute pas de son propre bruit. [OBL] SEUL GESTE BRUSQUE DU "
 "PERSONNAGE DANS TOUT LE CHAPITRE, une seule occurrence. Toute autre "
 "brusquerie ailleurs annule celle-ci.",
"A-335": " Le menton designe la chaise, les mains restent sur le registre. Elle ne "
 "regarde pas Lohen pendant la ligne, elle regarde la flaque qu'il fait sur le "
 "parquet. [OBL] Economie de mouvement : le menton coute moins cher que le bras, "
 "et cette regle vaut pour les 64 clips du personnage.",
"A-336": " Le debit ne change pas d'un mot a l'autre. Aucun soupir, aucun "
 "haussement. [OBL] Elle corrige une erreur de CATEGORIE, pas une impolitesse : "
 "ton de bibliothecaire, jamais de gardienne. Si la ligne sonne comme un "
 "reproche, le personnage est perdu pour le reste du chapitre.",
"A-337": " Les yeux se levent a la premiere syllabe et redescendent a la derniere. "
 "Aucune montee d'intonation a la fin : pas de point d'interrogation dans la "
 "diction. [NAR] Elle a pose cette question a des centaines de gens et aucune "
 "reponse ne l'a encore surprise.",
"A-339": " Le sourcil gauche monte de 2 mm et redescend en 8 frames. Les mains "
 "s'arretent de trier. [OBL] C'est tout ce qu'elle laisse passer, et c'est le "
 "premier signe du chapitre que Lohen a pose la bonne question.",
"A-340": " Elle donne une date, pas une duree, et le corps ne bouge pas pendant la "
 "ligne. [NAR] Les durees appellent des condoleances — elle a supprime de sa "
 "langue tout ce qui pourrait attirer de la pitie. Aucun personnage ne le "
 "remarque, le joueur peut le remarquer.",
"A-343": " Elle ralentit sur « premier », de 20 % environ, et le reste de la "
 "phrase reprend son debit normal. Les yeux restent sur les fiches. "
 "[OBL] LE RALENTISSEMENT EST SON SEUL OUTIL D'INSISTANCE. Elle ne hausse "
 "jamais la voix, dans aucun des 64 clips.",
"A-345": " Ton de consigne, comme une horaire d'ouverture. Elle pointe l'etage du "
 "menton. [OBL] Le plancher EST mange : le joueur PEUT tomber, et elle l'a dit. "
 "L'avertissement n'est pas un effet dramatique, c'est une information de "
 "niveau qui sera verifiee 40 minutes plus tard.",
"A-346": " Elle est deja retournee vers ses fiches quand la phrase se termine. "
 "Aucun regard d'adieu. [NAR] Elle sait deja ce qu'elle va faire. Le clip est "
 "joue 3 h 04 avant que le joueur comprenne — donc il ne doit contenir AUCUN "
 "signe avant-coureur jouable.",
"A-347": " Le geste de service est precis et rapide, cinq secondes en tout. La "
 "main brulee reste du cote oppose au visiteur. [OBL] Elle sert le the comme on "
 "remplit un formulaire : pas de sourire, pas de proposition de sucre, pas "
 "d'attente de remerciement.",
"A-348": " Elle enonce une taxonomie, sans mepris et sans tendresse, comme un "
 "resultat d'observation. Les yeux vont vers l'escalier pendant la phrase. "
 "[NAR] Elle a vu passer des centaines de chercheurs et Lohen vient d'entrer "
 "dans une categorie qu'elle connait.",
"A-350": " Elle vient de dire que les mains de Lohen touchent trop de choses et "
 "elle s'en amuse une fraction de seconde — 5 frames, coin gauche de la bouche, "
 "puis plus rien. [OBL] C'est le premier des deux sourires du personnage. "
 "L'autre est triste et arrive en S7.",
"A-351": " Elle parle des fiches. Le debit est plat, le regard est sur le "
 "tiroir. [NAR] Elle pourrait parler d'autre chose et elle le sait. Ne jamais "
 "jouer le double sens : si l'actrice le souligne, la ligne meurt.",
"A-353": " Un seul mot, dit sans lever la tete, avec la main gauche levee de 15 cm. "
 "Lohen s'arrete. [OBL] Elle ne dit jamais « s'il vous plait » et elle n'a "
 "jamais besoin de repeter.",
"A-355": " Elle indique l'ouest a la canne, sans se lever. La direction est juste "
 "et verifiable sur la carte du niveau. [OBL] Toutes les indications spatiales "
 "de Mireille sont exactes : c'est le personnage sur lequel le joueur apprend a "
 "compter, ce qui rend son mensonge unique plus efficace.",
"A-357": " Elle le reconnait avant de lever les yeux — au bruit des bottes "
 "mouillees. Les mains continuent de trier pendant deux mots puis s'arretent. "
 "[NAR] Elle ne s'attendait pas a le revoir et elle ne le montre pas.",
"A-358": " Elle constate. Aucune excuse dans la voix, aucune dans le corps. "
 "[OBL] Le dossier etait vide et elle le savait avant de l'envoyer chercher : "
 "c'est ici que le mensonge du personnage devient visible retrospectivement, "
 "sans qu'une seule ligne ne l'admette.",
"A-361": " Elle ralentit sur « pour vous ». Le reste est a debit normal. Les yeux "
 "restent baisses pendant toute la ligne, ce qui n'arrive nulle part ailleurs "
 "chez elle. [OBL] C'est la seule fois ou elle evite le regard d'un "
 "interlocuteur.",
"A-362": " Le corps se tourne d'un quart vers les rayonnages avant la fin de la "
 "phrase : la conversation est close pendant qu'elle parle encore. "
 "[OBL] Elle n'expliquera pas, et le jeu ne l'expliquera pas non plus — "
 "interdit 21.08.",
"A-363": " Elle donne un itineraire, pas un conseil. Trois segments, la canne "
 "marque chacun d'un petit coup au sol. [OBL] Monter la ville est une "
 "instruction de niveau : les trois segments correspondent aux trois etages du "
 "chapitre, et le joueur les fera dans cet ordre.",
"A-364": " « Fermez la porte. » Dite de dos. Elle ne verifie pas qu'il obeit. "
 "[NAR] Premiere ligne de la branche d'abandon : si le joueur quitte la "
 "Bibliotheque sans poser de question, c'est tout ce qu'il obtiendra d'elle.",
"A-365": " Elle repose la fiche dans le tiroir, sans la ranger a sa place. "
 "[OBL] Le seul classement approximatif du personnage dans tout le chapitre, et "
 "il n'arrive que dans cette branche. Personne ne le verra jamais sauf le "
 "joueur qui abandonne.",
}
