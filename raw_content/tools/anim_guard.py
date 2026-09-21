#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
anim_guard.py --scan
=============================================================================
Garde-fou CI de l'annexe A2 (les 744 fiches d'animation autonomes).
Reference : LOHEN_PROMPT_MASTER.txt BLOC 07, ANIM_MANIFEST_744.txt.

Jobs implementes
----------------
  count-744           exactement 744 fiches, une par clip, sans trou
  contract-07-20      la repartition par partie respecte le master
  self-contained      chaque fiche porte ses 6 sections obligatoires
  no-filler           aucune fiche sous le seuil de densite utile
  dialogue-single-source   aucune fiche ne recopie le texte d'une replique
  letter-single-source     le texte de la lettre n'apparait nulle part
  amber-guard         #FFA33C reste reserve a Esteban
  readability-frame5  tout clip joue annonce la regle 07.02
  loop-closure        tout clip en BOUCLE annonce sa contrainte de cyclage
  no-parry-ui         aucune fiche n'introduit d'UI de parade
  e23-single-exception un seul clip de fantome regarde Lohen
  id-traceability     toute fiche renumerotee cite son ancien identifiant
  no-redundancy       aucune direction ne dit deux fois la meme chose
=============================================================================
"""
from __future__ import annotations
import json, re, sys, unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ANIM = ROOT / "anim"
DATA = json.loads((ROOT/"anim_data"/"clips_744.json").read_text(encoding="utf-8"))

CONTRACT = {1:218, 2:96, 3:64, 4:58, 5:71, 6:34, 7:39, 8:52, 9:48, 10:64}
SECTIONS = ["1. IDENTITE TECHNIQUE", "2. CONTEXTE DE PERSONNAGE",
            "3. DIRECTION D'ACTEUR", "4. TIMELINE ET EVENEMENTS",
            "5. TRANSITIONS", "6. VALIDATION"]
MIN_LINES = 70          # seuil de densite : sous ce volume, la fiche est creuse

# Seuil de densite de la SECTION 3 (direction d'acteur), en caracteres utiles.
# Il est MODULE PAR CATEGORIE, et c'est volontaire. Mesurer un clip parle et un
# clip de deplacement lateral avec la meme regle est une erreur de conception :
# elle pousse a rallonger des directions deja completes, donc a produire
# exactement le remplissage que ce job est cense interdire (cf. 21.08).
#   - clips PARLES ou JOUES (ACT/CIN/ECH/DLG/NAR/VAR) : la direction doit dire
#     ce que fait le corps, ou vont les yeux, et quel piege de jeu eviter.
#   - clips TECHNIQUES (LOC/TRN/CBT/CLB/GRP/AIR/SLP/IDL/props) : une direction
#     juste peut tenir en deux phrases si elle porte la contrainte d'animation.
CAT_ACTED = {"ACT", "CIN", "ECH", "DLG", "NAR", "VAR"}
MIN_DIR_ACTED = 140
MIN_DIR_TECH  = 85

def cat_of(c):
    pref = c.get("prefix") or ""
    return pref.split("-")[1] if "-" in pref else ""

violations: list[str] = []
def fail(job, msg): violations.append("[%s] %s" % (job, msg))

files = sorted(ANIM.rglob("A-*.txt"))
texts = {f.name: f.read_text(encoding="utf-8") for f in files}

# --- count-744 ---------------------------------------------------------------
if len(files) != 744:
    fail("count-744", "%d fiches trouvees, 744 attendues" % len(files))
ids = sorted(int(f.name[2:5]) for f in files)
missing = [n for n in range(1, 745) if n not in set(ids)]
if missing:
    fail("count-744", "identifiants manquants : %s" % missing[:12])
dupes = {n for n in ids if ids.count(n) > 1}
if dupes:
    fail("count-744", "identifiants en double : %s" % sorted(dupes)[:12])

# --- contract-07-20 ----------------------------------------------------------
for p, exp in CONTRACT.items():
    got = len([c for c in DATA if c["part_num"] == p])
    if got != exp:
        fail("contract-07-20", "partie %d : %d clips, %d au contrat" % (p, got, exp))

# --- par fiche ---------------------------------------------------------------
AMBER = re.compile(r"#?FFA33C", re.I)
# Contextes ou l'ambre #FFA33C est autorise : ce sont les objets d'Esteban
# listes par le master. La lampe de la chambre du Phare (19.03) en fait partie
# — c'est meme la reapparition de l'ambre apres 47 minutes d'absence.
AMBER_OK = ("amber_esteban", "lampe a huile", "oil_lamp", "la lampe",
            "chambre du phare", "boite aux lettres", "porte")
QUOTE = re.compile(r"«[^»]{25,}»")
# Fragments propres au texte de la lettre (BLOC 20.03), recherches UNIQUEMENT
# sous forme citee. Le master reste la source unique de ce texte.
LETTER_QUOTE = re.compile(
    r"«[^»]*(?:tu as les mains sales|tu m'as trouve|tu peux la garder)[^»]*»",
    re.I)
UI_WORDS = re.compile(r"\b(barre de vie|jauge de parade|icone de parade|"
                      r"indicateur de riposte|quick ?time)\b", re.I)

for c in DATA:
    name = [f.name for f in files if f.name.startswith(c["id"])]
    if not name:
        fail("count-744", "fiche absente pour %s" % c["id"]); continue
    src = texts[name[0]]
    n = len(src.split("\n"))

    for s in SECTIONS:
        if s not in src:
            fail("self-contained", "%s : section manquante « %s »" % (c["id"], s))
    if n < MIN_LINES:
        fail("no-filler", "%s : %d lignes, sous le seuil de densite (%d)"
             % (c["id"], n, MIN_LINES))
    if "DIRECTION D'ACTEUR" in src:
        body = src.split("3. DIRECTION D'ACTEUR")[1].split("4. TIMELINE")[0]
        dens = len(re.sub(r"[-=\s]", "", body))
        seuil = MIN_DIR_ACTED if cat_of(c) in CAT_ACTED else MIN_DIR_TECH
        if dens < seuil:
            fail("no-filler", "%s (%s) : direction d'acteur a %d caracteres "
                 "utiles, seuil %d" % (c["id"], cat_of(c) or "PROP", dens, seuil))
        # Detection de remplissage reel : une direction peut etre longue ET vide.
        if re.search(r"\b(etc\.|et ainsi de suite|comme d'habitude|"
                     r"rien de particulier|rien a signaler|sans plus|"
                     r"a definir|a preciser|TBD|TODO|XXX)\b",
                     body, re.I):
            fail("no-filler", "%s : formule de remplissage dans la direction"
                 % c["id"])

    # dialogue-single-source : une fiche cite un ID de ligne, jamais le texte
    if c.get("line"):
        if c["line"] not in src:
            fail("dialogue-single-source",
                 "%s : la ligne %s n'est pas referencee" % (c["id"], c["line"]))
    # letter-single-source
    # On ne cherche PAS la locution « mains sales » en clair : Lohen a
    # reellement les mains sales et plusieurs fiches le decrivent legitimement
    # (A-172 : il les essuie avant de toucher les affaires des gens).
    # Ce qui est interdit, c'est de RECOPIER une phrase de la lettre : on ne
    # detecte donc que la forme CITEE, entre guillemets, sans renvoi a 20.03.
    for q in LETTER_QUOTE.findall(src):
        if "20.03" not in src:
            fail("letter-single-source",
                 "%s : phrase de la lettre citee (%s) sans renvoi a 20.03"
                 % (c["id"], q[:40]))

    for m in AMBER.finditer(src):
        ctx = src[max(0, m.start()-160):m.start()+160].lower()
        if not any(k in ctx for k in AMBER_OK):
            fail("amber-guard", "%s : ambre #FFA33C hors contexte Esteban" % c["id"])

    if c.get("frames") and c["frames"] > 1 and "07.02" not in src:
        fail("readability-frame5", "%s : regle de lisibilite non annoncee" % c["id"])
    if c.get("loop") == "BOUCLE" and "Cyclage" not in src:
        fail("loop-closure", "%s : contrainte de cyclage absente" % c["id"])
    for m in UI_WORDS.finditer(src):
        avant = src[max(0, m.start()-40):m.start()].lower()
        # « pas de barre de vie », « aucune jauge », « sans icone de parade » :
        # la fiche INTERDIT l'element, elle ne l'introduit pas.
        if re.search(r"(pas de|aucun[e]?|sans|jamais de|ni)\s*$", avant):
            continue
        fail("no-parry-ui", "%s : introduit une UI de combat interdite" % c["id"])
    # no-redundancy : deux phrases de la direction qui disent la meme chose
    if "DIRECTION D'ACTEUR" in src:
        body = src.split("3. DIRECTION D'ACTEUR")[1].split("4. TIMELINE")[0]
        # retirer la fin du titre de section (« — CE QUE LE MOUVEMENT DIT »),
        # sinon ses mots comptent comme une phrase de la direction.
        body = body.split("\n", 1)[1] if "\n" in body else body
        body = body.split("Ligne de dialogue portee")[0]
        body = re.sub(r"^[-=_]{3,}$", " ", body, flags=re.M)
        sents = [x.strip() for x in
                 re.split(r"(?<=[.!?])\s+(?=[A-Z\[«])", body) if x.strip()]
        def _w(t):
            t = re.sub(r"[-=_]{3,}", " ", t)
            t = unicodedata.normalize("NFD", t.lower())
            t = "".join(ch for ch in t if unicodedata.category(ch) != "Mn")
            t = re.sub(r"[^a-z0-9 ]", " ", t)
            return {w for w in t.split() if len(w) > 3}
        for a in range(len(sents)):
            for b in range(a + 1, len(sents)):
                wa, wb = _w(sents[a]), _w(sents[b])
                if not wa or not wb:
                    continue
                if len(wa & wb) / max(1, min(len(wa), len(wb))) >= 0.72:
                    fail("no-redundancy",
                         "%s : la direction se repete (« %s… »)"
                         % (c["id"], sents[b][:46]))
                    break
            else:
                continue
            break

    if c.get("old_id") and c["old_id"] != c["id"] and "Identifiant precedent" not in src:
        fail("id-traceability", "%s : renumerote sans mention de l'ancien id" % c["id"])

# --- e23-single-exception ----------------------------------------------------
# Regle 11.06 : « les fantomes ne regardent jamais Lohen », UNE exception —
# la fille de 9 ans dans l'Echo du Verrier. Le perimetre est donc l'ensemble
# des clips de FANTOME (Echos et apparitions), identifies par leur categorie
# ECH ou par le prefixe ghost_, et non la partie 8 entiere : cette partie
# contient aussi le Verrier lui-meme, qui n'est pas un fantome et qui a le
# droit de regarder Lohen (A-582, premiere fois qu'on voit son visage).
def is_ghost(c):
    return (cat_of(c) == "ECH" or c["name"].startswith("ghost_")
            or c["name"].startswith("echo_"))
looks = [c["id"] for c in DATA if is_ghost(c)
         and re.search(r"(regarde|tourne la tete vers)\s+(?:\w+\s+){0,2}Lohen",
                       c.get("jeu", ""), re.I)]
if sorted(looks) != ["A-631"]:
    fail("e23-single-exception",
         "exception 11.06 mal formee : attendu [A-631], trouve %s" % sorted(looks))

def main():
    if "--scan" not in sys.argv:
        print(__doc__); return 0
    if violations:
        print("ANIM GUARD — %d violation(s)\n" % len(violations))
        for v in violations[:60]:
            print("  " + v)
        if len(violations) > 60:
            print("  ... et %d autres" % (len(violations)-60))
        return 1
    print("ANIM GUARD — annexe A2 : 744/744 fiches conformes.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
