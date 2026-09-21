#!/usr/bin/env python3
"""Applique les fiches renseignees de anim_data/patch_thin.py sur clips_744.json."""
import json, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "anim_data"))
import patch_thin

data = json.loads((ROOT/"anim_data"/"clips_744.json").read_text(encoding="utf-8"))
by = {c["id"]: c for c in data}

for cid,(twin,name,jeu) in patch_thin.MIRROR.items():
    c, t = by[cid], by[twin]
    for k in ("dur","frames","loop","rm","blend","layer","prio","bones","evt","trans"):
        if not c.get(k): c[k] = t.get(k)
    c["name"] = name
    c["jeu"] = jeu
    c["source"] = "manifeste (entree L/R commune) — fiche depliee"
    c["tag"] = c.get("tag") or ("MIROIR DE %s" % twin)

for cid,(jeu,evt) in patch_thin.SUMMARY.items():
    c = by[cid]
    c["jeu"] = jeu
    if evt: c["evt"] = evt
    c["source"] = "manifeste (entree resumee) — fiche developpee"

(ROOT/"anim_data"/"clips_744.json").write_text(
    json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
thin = [c["id"] for c in data if len(c.get("jeu") or "") < 40]
print("patchees :", len(patch_thin.MIRROR)+len(patch_thin.SUMMARY), "| restantes minces :", thin)
