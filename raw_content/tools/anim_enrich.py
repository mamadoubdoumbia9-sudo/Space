#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Complete les champs `os`, `trans` et `evt` absents du manifeste.
Les valeurs sont DERIVEES de la categorie, de la couche, du root motion et
du type de clip — jamais inventees au hasard, jamais identiques d'une
categorie a l'autre. Un clip dont le manifeste declarait deja le champ
n'est pas touche."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
data = json.loads((ROOT/"anim_data"/"clips_744.json").read_text(encoding="utf-8"))

# --- OS PAR CATEGORIE --------------------------------------------------------
BONES = {
 "IDL":"pelvis, spine_01-03, neck, clavicules, appuis de jambes, doigts au repos",
 "LOC":"jambes completes, pelvis, spine_01-03, bras contrebalances, IK de pied",
 "TRN":"pelvis (pivot), jambes, colonne en torsion, bras, nuque (anticipation)",
 "SLP":"jambes completes, cheville (amplitude accrue), pelvis, colonne, bras d'equilibre, IK de pied",
 "AIR":"corps complet en vol, pelvis moteur, jambes (tuck/extension), bras, nuque",
 "CLB":"bras complets, mains et doigts (prise), omoplates, colonne, jambes en appui, IK de main",
 "GRP":"bras droit (lanceur), main droite, epaule, colonne, jambes en reception, IK de main sur le cable",
 "CBT":"corps complet, bras d'arme, hanches (rotation motrice), appuis, nuque (regard cible)",
 "NAR":"bras et mains (dominantes), doigts, colonne, tete, blendshapes faciaux",
 "CIN":"corps complet + piste faciale 60 fps + doigts + tissu simule",
 "ACT":"buste, bras, mains, tete, machoire, blendshapes faciaux 60 fps",
 "VAR":"corps complet, variante economique de la chaine principale",
 "ECH":"corps complet, piste faciale, doigts",
}
BONES_PART = {
 6:"4 pattes (5 os chacune), corps central, renflement, mesh de coeur interne",
 7:"72 os, colonne courbee, 4 membres, chaine de 14 os de jiggle pour les plaques",
 8:"240 os dont 74 pour la coulee (jiggle-bones), visage humain intact, machoire bloquee",
 9:"squelette generique partage (96 os), compatible mince / moyen / large",
 10:"rig d'objet dedie, 3 a 32 os selon le mecanisme, aucune deformation de peau",
}

# --- EVENEMENTS PAR CATEGORIE ------------------------------------------------
def events(c):
    cat = c["prefix"].split("-")[-1]
    p, loop, fr = c["part_num"], c.get("loop"), c.get("frames") or 0
    if p == 10:
        return ("son pilote par la cinematique du rig (vitesse ou angle), "
                "jamais par un declencheur a frame fixe · aucun VFX magique")
    if p == 9:
        return ("SFX de metier spatialise a la position du PNJ · "
                "[OBL] aucun LookAt prolonge vers le joueur (regle de la partie 9)")
    if cat == "LOC":
        return ("FOOTSTEP_L / FOOTSTEP_R aux poses de pied (son matiere 13.28 "
                "+ haptique) · CLOTH_RUSTLE · RIG_CLINK selon la charge")
    if cat == "TRN":
        return "FOOTSTEP au pivot · CLOTH_RUSTLE · CANCEL_OK des la frame 6"
    if cat == "SLP":
        return ("FOOTSTEP_L / FOOTSTEP_R (matiere pente ou marche) · "
                "BREATH_IN / BREATH_OUT si la montee dure · CLOTH_RUSTLE")
    if cat == "AIR":
        return ("HAPTIC a l'impulsion et a la reception · CAM_SHAKE a "
                "l'atterrissage selon la hauteur · FOOTSTEP a la reception")
    if cat == "CLB":
        return "HAND_CONTACT a chaque prise · CLOTH_RUSTLE · BREATH sous effort"
    if cat == "GRP":
        return ("RIG_CLINK au lancer · SFX(cable) pilote par la tension · "
                "HAPTIC a l'accroche · HAND_CONTACT a l'impact")
    if cat == "CBT":
        return ("HIT_ACTIVE_ON / OFF autour de la fenetre de degats · "
                "PARRY_WINDOW_ON / OFF (0,22 s) · IFRAME_ON / OFF si esquive · "
                "STATE_COMMIT au point de non-retour · CANCEL_OK")
    if cat in ("ACT", "ECH", "CIN"):
        return ("piste faciale 60 fps · BREATH aux respirations ecrites · "
                "aucun son ajoute qui ne soit pas dans la scene")
    if cat == "IDL":
        return ("BREATH_IN / BREATH_OUT sur le cycle · CLOTH_RUSTLE une a "
                "deux fois par boucle, jamais au meme endroit que la respiration")
    return "aucun evenement declare — le clip ne declenche rien, et c'est un choix"

# --- TRANSITIONS PAR CATEGORIE -----------------------------------------------
def trans(c):
    cat = c["prefix"].split("-")[-1]
    p = c["part_num"]
    if p == 10:
        return ("<- appel du systeme qui possede le prop (zone, sequence ou "
                "personnage) ; -> etat de repos du rig, qui ne bouge plus")
    if p == 9:
        return ("<- selection par la routine du PNJ (`npc_routine_state`) ; "
                "-> etape suivante de la meme routine, jamais un aller-retour")
    if p in (6, 7, 8):
        return ("<- etat courant de l'IA (`ai_state`) ; -> retour a l'idle de "
                "la creature, ou enchainement declare par le comportement")
    if cat == "IDL":
        return "<- stop_walk, land_soft, sortie de combat ; -> tout etat"
    if cat == "LOC":
        return ("<- transition de demarrage correspondante ; -> autre vitesse "
                "du BlendSpace2D, ou transition d'arret")
    if cat == "TRN":
        return "<- etat de locomotion source ; -> etat de locomotion cible"
    if cat == "AIR":
        return "<- impulsion ou perte d'appui ; -> boucle de chute, puis reception"
    if cat == "CLB":
        return "<- prise de rebord ou d'echelle ; -> deplacement en suspension, ou sortie"
    if cat == "GRP":
        return "<- visee du grappin ; -> traction, balancier, ou retraction"
    if cat == "CBT":
        return ("<- garde ou clip de combat precedent ; -> garde, enchainement, "
                "ou reaction si touche")
    if cat in ("ACT", "ECH"):
        return "<- idle de dialogue du personnage ; -> ligne suivante, ou idle d'ecoute"
    if cat == "CIN":
        return "<- declencheur scripte de la sequence ; -> plan suivant, ou rendu du controle"
    return "<- etat parent de la categorie ; -> retour a l'attente de la meme couche"

nb = nt = ne = 0
for c in data:
    cat = c["prefix"].split("-")[-1]
    if not (c.get("bones") or "").strip():
        c["bones"] = BONES_PART.get(c["part_num"]) or BONES.get(cat) or \
                     "chaine principale du personnage"
        nb += 1
    if not (c.get("trans") or "").strip():
        c["trans"] = trans(c); nt += 1
    if not (c.get("evt") or "").strip():
        c["evt"] = events(c); ne += 1

(ROOT/"anim_data"/"clips_744.json").write_text(
    json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
print("os completes :", nb, "| transitions :", nt, "| evenements :", ne)
