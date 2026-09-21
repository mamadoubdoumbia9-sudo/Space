#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Genere les 744 fiches d'animation autonomes de l'annexe A2.
Un fichier complet par clip. Chaque fiche se lit sans le manifeste :
elle reporte son contexte de personnage, ses standards techniques
applicables, sa direction d'acteur, sa timeline, ses transitions,
ses dependances et ses criteres de validation.

Aucune fiche n'est un gabarit rempli : les sections techniques sont
DERIVEES des donnees du clip (duree, type, couche, root motion, categorie),
et la direction d'acteur provient du manifeste ou de la conception
documentee dans anim_data/gaps_part_*.py."""
import json, math, re, unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "anim"
DATA = json.loads((ROOT / "anim_data" / "clips_744.json").read_text(encoding="utf-8"))

W = 79
def rule(ch="-"): return ch * W
def head(t): return "=" * W + "\n" + t + "\n" + "=" * W

def wrap(text, width=74, indent=""):
    text = re.sub(r"\s+", " ", (text or "").strip())
    if not text:
        return []
    words, lines, cur = text.split(" "), [], ""
    for w in words:
        if len(cur) + len(w) + 1 > width:
            lines.append(indent + cur); cur = w
        else:
            cur = (cur + " " + w).strip()
    if cur:
        lines.append(indent + cur)
    return lines

def slug(s):
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()
    s = re.sub(r"[^a-zA-Z0-9]+", "_", s).strip("_").lower()
    return s[:52]

# --- CONTEXTE PAR PARTIE : reporte dans chaque fiche pour l'autonomie -------
PART_CTX = {
1: ("LOHEN — 27 ans, 1,74 m, sec, entraine, fatigue.",
    "Il a du POIDS. Chaque mouvement a un cout visible. Reference : un "
    "grimpeur professionnel en fin de journee de travail. Il ne fait jamais "
    "un geste gratuit, sauf trois tics (L2 le lacet, L3 le col, L5 la "
    "sacoche) qui sont tout le personnage. Il ne sait pas bien lire. "
    "Il a deux sourires dans tout le chapitre. Matricule 0114 brode sur la "
    "veste, dans l'albedo, et il doit s'user. Epaule droite abimee "
    "« depuis Saint-Jerome » — jamais defini, interdit de le definir.",
    "veste de relayeur (cloth simule, 1 des 6 pieces reelles du chapitre), "
    "harnais, sacoche, gants, lacet au poignet gauche"),
2: ("ESTEBAN — 31 ans, accordeur de cloches. Plus grand que Lohen, roux.",
    "Il ne tient pas en place. Il parle avec LES DEUX mains (Lohen n'en "
    "utilise qu'une). Il est en avance sur le temps, jamais en retard. "
    "Gestes signature : E1 il range sa meche et il RATE, E2 il sourit du "
    "cote gauche seulement, E3 il tapote deux fois avant de parler "
    "serieusement. [OBL] Seul personnage du jeu SANS REVERB, en toutes "
    "circonstances (11.06). [OBL] Aucun Echo ne le montre, sauf E21.",
    "gilet (cloth simule dans E21), crayon de charpentier derriere "
    "l'oreille, carnet en poche de poitrine, montre"),
3: ("MIREILLE VANDECK — 58 ans, gardienne de la Bibliotheque des Eaux.",
    "Elle est ECONOME. Elle a mal partout et elle ne le montre pas. Chaque "
    "mouvement est calcule pour depenser le minimum. Elle ne se leve jamais "
    "sans raison. Elle ne hausse JAMAIS la voix : quand elle appuie, elle "
    "RALENTIT. Gestes signature : M1 elle tourne sa tasse de 90 deg avant "
    "de boire, M2 elle cache sa main brulee quand on la regarde. "
    "Elle ment une fois.",
    "manteau rapiece (14 pieces, 15 apres le clip A-371), lunettes a un "
    "seul verre, canne qui est un pied de biche, tasse"),
4: ("CAPITAINE ORVAL TALLEC — 44 ans, ancien officier du port.",
    "Il est LOURD et il ne se leve pas. Dans toute la scene S3 il reste "
    "assis jusqu'a « Non. C'est ton excuse. » : le fait qu'il se leve enfin "
    "est l'evenement de la scene. Il parle LENTEMENT et il finit toujours "
    "ses phrases. Il ne s'interrompt jamais. Voix la plus grave du casting, "
    "on ne lui demande jamais de crier. Gestes : T1 deux coups secs sur la "
    "prothese, T2 il s'assoit toujours de travers.",
    "uniforme sans insignes (les trous restent), prothese de bras droit en "
    "bois et laiton : 12 os RIGIDES, contraintes strictes, JAMAIS de "
    "deformation"),
5: ("SOL — 13 ans, genre non precise, jamais precise, jamais un sujet.",
    "Sol ne s'arrete JAMAIS de bouger (geste S1), SAUF quand c'est grave "
    "(geste S2) — et alors l'immobilite est totale, absolue, effrayante. "
    "Tout le personnage tient dans ce contraste : l'animateur doit "
    "SUR-ANIMER l'agitation pour que l'immobilite frappe. Parle par phrases "
    "de quatre mots maximum. Sait ou tout se trouve. Ne demande jamais rien. "
    "Sol est le seul personnage qui fait rire le joueur, et ce role est "
    "VITAL : sans Sol, le chapitre est irrespirable.",
    "vetements trois fois trop grands, veste de relayeur en lambeaux qui "
    "etait celle de quelqu'un d'autre, PIEDS NUS (10 os d'orteils animes "
    "individuellement, IK de pied de precision)"),
6: ("FIGURE — L'ECHASSIER. 3,1 m debout, quatre pattes, corps a 2,1 m.",
    "Sa signature est la STABILITE HORIZONTALE : le corps ne monte ni ne "
    "descend, il glisse. C'est ce qui le rend faux — aucun animal ne se "
    "deplace comme ca. Le renflement remplace la tete et il pivote comme "
    "un regard. [OBL] Le coeur interne est le seul tell de la fenetre de "
    "riposte : aucune UI, aucun contour, aucun son dedie (CI no-parry-ui).",
    "corps de verre, 4 pattes de 5 os, renflement, mesh de coeur interne "
    "a 0.82 d'echelle (figure_core.gdshader)"),
7: ("FIGURE — LE MUEUR. 1,7 m, humanoide accroupi, plaques de verre.",
    "Le Mueur est ce qui reste quand une personne se recroqueville trop "
    "longtemps. Sa posture est celle de quelqu'un qui SE PROTEGE. Il attaque "
    "de cote, jamais de face. Il fuit quand il est seul, il est brave en "
    "groupe. [ART] C'est un ennemi LACHE : le joueur doit le mepriser un "
    "peu, puis se sentir mal de l'avoir meprise.",
    "72 os, plaques de verre en chaine secondaire de 14 os de jiggle, "
    "plaques qui tombent et repoussent (croissance en shader)"),
8: ("LE VERRIER (ANSELME ROUX) — 52 ans, maitre verrier. 2,6 m.",
    "[ART][OBL] CE N'EST PAS UN MONSTRE. C'est un homme fatigue avec "
    "quelque chose de tres lourd sur le dos. Chaque mouvement doit exprimer "
    "LE POIDS et la LENTEUR. LE VISAGE HUMAIN EST INTACT ET CALME, et c'est "
    "ce qui est insupportable. Il ne peut plus parler : sa machoire est "
    "prise. Il communique en montrant. Il a lu le meme Echo 4 000 fois — "
    "celui de sa fille.",
    "86 000 tris, 240 os dont 74 pour la coulee (chaine de jiggle-bones) "
    "qui forme une aile, un manteau ou une vague selon l'angle"),
9: ("PNJ GENERIQUES DU MARCHE SUSPENDU — 34 habitants, routines simples.",
    "[ART] Chaque PNJ fait UNE chose et la fait bien. Aucun PNJ ne marche "
    "sans but. Aucun PNJ ne fait des allers-retours. Si un habitant se "
    "deplace, il porte quelque chose et il va quelque part. "
    "[OBL] INTERDIT : les PNJ qui tournent en rond, les PNJ qui parlent "
    "seuls, les PNJ qui regardent le joueur en permanence. Apres 50 minutes "
    "de ville morte, le joueur doit avoir un CHOC DE VIE.",
    "squelette generique partage (96 os), 3 morphologies (mince / moyen / "
    "large), 11 tetes interchangeables, 14 tenues, retarget teste"),
10:("PROPS ANIMES ET MECANISMES — rigs d'objets, pas de squelettes.",
    "[ART][OBL] Dans Velmora, RIEN ne bouge tout seul sans raison. Si un "
    "objet s'anime, c'est le vent, la gravite, l'eau, un personnage, ou la "
    "Maree. Aucune animation « magique ». Aucun objet qui flotte. "
    "[PERF] Tout prop a plus de 30 exemplaires par zone passe en MultiMesh "
    "avec animation en shader ; les clips de cette partie sont les props "
    "HEROS, animes pour de vrai, et peu nombreux par zone.",
    "rigs dedies par objet, de 3 a 32 os selon le mecanisme"),
}

CAT = {
 "IDL":"IDLE / REPOS / ATTENTE", "LOC":"LOCOMOTION", "TRN":"TRANSITION DE LOCOMOTION",
 "SLP":"PENTES, ESCALIERS, SURFACES", "AIR":"SAUT / CHUTE / ATTERRISSAGE",
 "CLB":"ESCALADE / SUSPENSION", "GRP":"GRAPPIN", "CBT":"COMBAT",
 "NAR":"INTERACTION / ECHO / GESTE NARRATIF", "CIN":"CINEMATIQUE",
 "ACT":"ACTING / DIALOGUE", "VAR":"VARIANTE / COMPORTEMENT", "ECH":"ECHO / FLASHBACK",
}

def prio_band(p):
    if p is None: return "non applicable (clip sans priorite d'etat)"
    for lo, hi, lbl in ((0,19,"locomotion de fond"), (20,39,"traversee"),
                        (40,59,"combat"), (60,79,"reaction / degats"),
                        (80,94,"narratif / interaction"), (95,100,"cinematique (verrouille tout)")):
        if lo <= p <= hi:
            return "%s (bande %d-%d, bareme 0.05)" % (lbl, lo, hi)
    return "hors bareme"

def compression(c):
    """Tolerances de courbe : 0.03 du manifeste, avec le cas des doigts."""
    base = ["position 0.002", "rotation 0.0015", "echelle 0.004"]
    n = c["name"].lower()
    if any(k in n for k in ("hand", "insert", "finger", "letter", "photo", "carnet", "notebook")):
        base.append("doigts 0.006 (chaines de doigts dominantes sur ce clip)")
    else:
        base.append("doigts 0.006")
    return base

def weight_kb(c):
    """Estimation de poids compresse, coherente avec le budget 0.12 (42 Mo)."""
    d = c.get("dur") or 0.0
    cat = (c.get("prefix") or "").split("-")[-1]
    rate = 14.0
    if cat in ("CIN", "ACT", "ECH"): rate = 46.0
    elif cat in ("IDL", "VAR"): rate = 11.0
    elif cat in ("LOC", "TRN", "AIR"): rate = 22.0
    if c.get("layer") == "VOIX SEULE": rate = 2.0
    if c.get("loop") == "POSE FIGEE": return 3
    return max(4, int(round(d * rate)))

def build(c):
    p = c["part_num"]
    ctx_name, ctx_rule, ctx_rig = PART_CTX[p]
    cat_key = (c.get("prefix") or "").split("-")[-1]
    cat = CAT.get(cat_key, "PROP ANIME" if c["prefix"] == "PROP" else "ROUTINE DE PNJ")
    dur = c.get("dur")
    fr = c.get("frames") or (int(round(dur * 30)) if dur else None)
    L = []
    A = L.append

    A(head("  %s   %s" % (c["id"], c["name"])))
    A("")
    A("  ANNEXE A2 — FICHE D'ANIMATION AUTONOME %s / 744" % c["id"][2:])
    A("  Partie %d — %s" % (p, c["part_name"]))
    A("  Categorie : %s" % cat)
    if c.get("tag"):
        A("  Marqueur   : %s" % c["tag"])
    A("  Fichier source  : A_%s_%s_%s_v01.glb" % (
        c["part_name"].split()[0].title(), cat_key.title(), slug(c["name"])))
    A("  Reference maitre : LOHEN_PROMPT_MASTER.txt BLOC 07 · ANIM_MANIFEST_744.txt")
    A("  Origine de la fiche : %s" % c.get("source", "manifeste"))
    if c.get("old_id") and c["old_id"] != c["id"]:
        A("  Identifiant precedent : %s (voir RECONCILIATION_744.txt)" % c["old_id"])
    A("")

    # 1 — IDENTITE TECHNIQUE
    A(rule()); A("1. IDENTITE TECHNIQUE"); A(rule())
    A("")
    if dur:
        A("  Duree ................. %.3f s  (%d frames a 30 fps)" % (dur, fr))
    else:
        A("  Duree ................. 1 frame (pose figee)")
    A("  Type .................. %s" % c.get("loop", "ONE-SHOT"))
    A("  Root motion ........... %s" % (c.get("rm") or "RM-NONE"))
    if c.get("blend"):
        A("  Blend in / out ........ %.2f s / %.2f s" % tuple(c["blend"]))
    A("  Couche AnimationTree .. %s" % (c.get("layer") or "L0"))
    A("  Priorite .............. %s" % (
        "%d — %s" % (c["prio"], prio_band(c["prio"])) if c.get("prio") is not None
        else "N/A — %s" % prio_band(None)))
    if c.get("variants"):
        A("  Variantes ............. %d, tirees par le meme etat (convention 0.08)" % c["variants"])
        for v in c.get("variant_names", []):
            A("      · %s" % v)
    A("  Poids compresse cible . ~%d Ko (budget global 42 Mo, 0.12)" % weight_kb(c))
    A("")
    A("  Os principalement sollicites :")
    for ln in wrap(c.get("bones") or "chaine complete du personnage", 70, "      "):
        A(ln)
    A("")
    A("  Tolerances de compression de courbe :")
    for t in compression(c):
        A("      · %s" % t)
    A("")

    # 2 — CONTEXTE
    A(rule()); A("2. CONTEXTE DE PERSONNAGE (reporte ici pour l'autonomie)"); A(rule())
    A("")
    A("  %s" % ctx_name)
    A("")
    for ln in wrap(ctx_rule, 74, "  "):
        A(ln)
    A("")
    A("  Rig et costume :")
    for ln in wrap(ctx_rig, 70, "      "):
        A(ln)
    A("")

    # 3 — DIRECTION D'ACTEUR
    A(rule()); A("3. DIRECTION D'ACTEUR — CE QUE LE MOUVEMENT DIT"); A(rule())
    A("")
    for ln in wrap(c.get("jeu") or "(a completer)", 74, "  "):
        A(ln)
    A("")
    if c.get("line"):
        A("  Ligne de dialogue portee : [%s]" % c["line"])
        A("  [OBL] Le texte de la ligne vit dans /home/user/dialogues/ et nulle")
        A("        part ailleurs. Cette fiche ne le recopie pas : elle le")
        A("        reference. Job CI `dialogue-single-source`.")
        A("")

    # 4 — TIMELINE
    A(rule()); A("4. TIMELINE ET EVENEMENTS"); A(rule())
    A("")
    if fr and fr > 1:
        A("  Fenetre de lisibilite [OBL 07.02] : le mouvement visible commence")
        A("  avant la frame 5 (166 ms). Aucune derogation.")
        A("")
        if c.get("loop") == "BOUCLE":
            A("  Cyclage [OBL 0.03] : la frame 0 et la frame %d sont identiques" % fr)
            A("  a 0.001 pres. Verifie par `tools/loop_check.py`, qui echoue le")
            A("  build en cas de saut.")
        else:
            A("  Clip ONE-SHOT : pas de contrainte de cyclage. La derniere frame")
            A("  doit poser une silhouette blendable vers les etats de sortie.")
        A("")
    A("  Evenements de timeline :")
    ev = c.get("evt") or ""
    if ev.strip():
        for ln in wrap(ev, 70, "      "):
            A(ln)
    else:
        A("      aucun evenement declare — le clip ne declenche ni son, ni VFX,")
        A("      ni haptique, ni appel de gameplay. C'est un choix, pas un oubli.")
    A("")

    # 5 — TRANSITIONS
    A(rule()); A("5. TRANSITIONS"); A(rule())
    A("")
    tr = c.get("trans") or ""
    if tr.strip():
        for ln in wrap(tr, 70, "      "):
            A(ln)
    else:
        A("      <- etat parent de la categorie %s" % cat)
        A("      -> retour a l'etat d'attente de la meme couche")
    A("")

    # 6 — VALIDATION
    A(rule()); A("6. VALIDATION [DOD]"); A(rule())
    A("")
    A("  Regle des trois passes (0.10) :")
    A("      P1 BLOCKING  poses cles seules, lisibilite validee")
    A("      P2 SPLINE    courbes, timing, overlap, follow-through")
    A("      P3 POLISH    doigts, tissu, respiration, micro-desequilibres")
    A("  Aucun clip n'entre en build release sans P3 signee dans")
    A("  /docs/anim_manifest.csv.")
    A("")
    A("  Test de la silhouette animee (0.11) : rendu en aplat noir sur fond")
    A("  blanc, regarde a 0.5x. Si la pose cle n'est pas lisible en silhouette")
    A("  a n'importe quelle frame, elle est reprise.")
    A("")
    A("  Controles specifiques a ce clip :")
    checks = []
    if c.get("loop") == "BOUCLE":
        checks.append("cyclage frame 0 == frame %d (tolerance 0.001)" % (fr or 0))
    if (c.get("rm") or "").startswith("RM-FULL"):
        checks.append("la translation vient integralement de l'anim : aucun "
                      "deplacement ajoute par le code pendant le clip")
    if (c.get("rm") or "").startswith("RM-ROT"):
        checks.append("seule la rotation vient de l'anim ; la translation est "
                      "pilotee par le code (reactivite prioritaire)")
    if c.get("layer") == "VOIX SEULE":
        checks.append("piste faciale VIDE et piste audio pure : aucun os anime")
    if c.get("prio") is not None and c["prio"] >= 95:
        checks.append("clip de cinematique : verrouille toutes les autres couches")
    if "OBL" in (c.get("jeu") or ""):
        checks.append("les clauses [OBL] de la section 3 sont verifiees en "
                      "revue, pas seulement en lecture")
    checks.append("aucune cle sur un os que le clip n'anime pas reellement "
                  "(`tools/strip_dead_keys.py`, 0.03)")
    for k in checks:
        for ln in wrap("· " + k, 70, "      "):
            A(ln)
    A("")
    A(rule("="))
    A("  FIN DE FICHE %s" % c["id"])
    A(rule("="))
    return "\n".join(L) + "\n"

# --- ECRITURE ----------------------------------------------------------------
PART_DIR = {
1:"01_LOHEN", 2:"02_ESTEBAN", 3:"03_MIREILLE", 4:"04_TALLEC", 5:"05_SOL",
6:"06_ECHASSIER", 7:"07_MUEUR", 8:"08_VERRIER", 9:"09_PNJ", 10:"10_PROPS",
}
OUT.mkdir(exist_ok=True)
count = 0
for c in DATA:
    d = OUT / PART_DIR[c["part_num"]]
    d.mkdir(exist_ok=True)
    fn = "%s_%s.txt" % (c["id"], slug(c["name"]))
    (d / fn).write_text(build(c), encoding="utf-8")
    count += 1
print("fiches ecrites :", count)
