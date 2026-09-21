#!/usr/bin/env python3
"""Fusionne les clips extraits du manifeste et les clips conçus pour combler
les blocs de plage. Produit anim_data/clips_744.json : la liste complete,
normalisee, qui alimente la generation des fiches autonomes."""
import json, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "anim_data"))

import gaps_part_esteban, gaps_part_mireille, gaps_part_tallec
import gaps_part_sol, gaps_part_creatures

extracted = json.loads((ROOT / "anim_data" / "clips_extracted.json").read_text(encoding="utf-8"))

# Les 15 blocs de plage du manifeste : leur entree unique est remplacee par
# les fiches individuelles conçues dans anim_data/gaps_part_*.py
BLOCK_HEADS = {250, 271, 279, 286, 298, 307, 337, 370, 397, 429, 483,
               535, 567, 627, 674}

PARTS = {
 1:("LOHEN", 218), 2:("ESTEBAN", 96), 3:("MIREILLE VANDECK", 64),
 4:("CAPITAINE ORVAL TALLEC", 58), 5:("SOL", 71), 6:("FIGURE — L'ECHASSIER", 34),
 7:("FIGURE — LE MUEUR", 39), 8:("LE VERRIER (ANSELME ROUX)", 52),
 9:("PNJ GENERIQUES DU MARCHE SUSPENDU", 48), 10:("PROPS ANIMES ET MECANISMES", 64),
}
RANGES = {1:(1,218), 2:(219,314), 3:(315,378), 4:(379,436), 5:(437,507),
          6:(508,541), 7:(542,580), 8:(581,632), 9:(633,680), 10:(681,744)}

clips = {}
for c in extracted:
    if c["num"] in BLOCK_HEADS:
        continue          # remplace par les fiches depliees
    clips[c["num"]] = c

for mod in (gaps_part_esteban, gaps_part_mireille, gaps_part_tallec,
            gaps_part_sol, gaps_part_creatures):
    for c in mod.CLIPS:
        c = dict(c)
        c.setdefault("source", "conçu — bloc de plage deplie")
        c.setdefault("frames", int(round(c.get("dur") or 0, 3) * 30))
        c["id"] = "A-%03d" % c["num"]
        if c["num"] in clips:
            raise SystemExit("collision sur %d" % c["num"])
        clips[c["num"]] = c

# --- CONVENTION 0.08 : LES VARIANTES D'UN MEME ETAT COMPTENT POUR UN CLIP ----
# « Quand un clip a des variantes, elles comptent pour UN clip dans le total
#   de 744 si elles sont selectionnees aleatoirement par le meme etat. »
# crowd_idle_generic_A / B / C sont tirees au sort par le meme etat de foule :
# elles forment donc UN clip a 3 variantes, et non trois. C'est ce qui ramene
# la partie 9 de 50 identifiants a 48 clips contractuels.
MERGE_VARIANTS = {683: [684, 685]}
for head, tail in MERGE_VARIANTS.items():
    if head in clips:
        base = clips[head]
        merged = [clips[t]["name"] for t in tail if t in clips]
        base["variants"] = 1 + len(merged)
        base["variant_names"] = [base["name"]] + merged
        base["name"] = base["name"].rsplit("_", 1)[0] + "_ABC"
        base["jeu"] = (
            "trois variantes d'attente de foule tirees au sort par le MEME etat "
            "(convention 0.08 : elles comptent donc pour un seul clip des 744). "
            "A : appui neutre. B : variante d'appui, poids sur l'autre jambe. "
            "C : avec un objet en main. [OBL] Le tirage est pondere par la "
            "morphologie : les trois doivent etre retargetables sur mince, "
            "moyen et large sans retouche.")
        for t in tail:
            clips.pop(t, None)

ordered = [clips[k] for k in sorted(clips)]
print("clips totaux avant renumerotation :", len(ordered))

# --- RENUMEROTATION CONTRACTUELLE -------------------------------------------
# Le manifeste avait derive de 5 identifiants (749 au lieu de 744) : la
# partie 2 en portait 99 pour 96 annonces, la partie 9 en portait 50 pour 48.
# On renumerote en sequence pour respecter le contrat 07.20, en conservant
# l'ordre narratif et en journalisant chaque deplacement.
PREFIX_PART = {
 "LOH":1, "EST":2, "MIR":3, "TAL":4, "SOL":5, "ECH":6, "MUE":7,
 "VER":8, "PNJ":9, "PROP":10,
}
def part_of(c):
    root = c["prefix"].split("-")[0]
    return PREFIX_PART[root]

remap = []
counters = {p: RANGES[p][0] for p in RANGES}
for c in ordered:
    p = part_of(c)
    new = counters[p]
    counters[p] += 1
    old_id = c["id"]
    c["part_num"] = p
    c["part_name"] = PARTS[p][0]
    c["old_id"] = old_id
    c["num"] = new
    c["id"] = "A-%03d" % new
    if old_id != c["id"]:
        remap.append((old_id, c["id"], c["name"]))

for p,(a,b) in RANGES.items():
    got = counters[p] - a
    exp = PARTS[p][1]
    status = "OK" if got == exp else "ECART %+d" % (got - exp)
    print("partie %2d %-34s %3d / %3d  %s" % (p, PARTS[p][0][:34], got, exp, status))

print("total :", len(ordered), "· renumerotes :", len(remap))
(ROOT / "anim_data" / "clips_744.json").write_text(
    json.dumps(ordered, ensure_ascii=False, indent=1), encoding="utf-8")
(ROOT / "anim_data" / "remap.json").write_text(
    json.dumps(remap, ensure_ascii=False, indent=1), encoding="utf-8")
