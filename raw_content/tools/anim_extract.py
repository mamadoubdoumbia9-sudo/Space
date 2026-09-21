#!/usr/bin/env python3
"""Extrait les clips du manifeste ANIM_MANIFEST_744.txt en donnees structurees.
Sortie : anim_data/clips_extracted.json — la source de verite pour la
generation des 744 fiches autonomes (annexe A2)."""
import re, json, unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "ANIM_MANIFEST_744.txt"

lines = SRC.read_text(encoding="utf-8").split("\n")
entries, cur, part, sect = [], None, None, None

for ln in lines:
    m = re.match(r"^PARTIE (\d+) — (.+?) — (\d+) CLIPS", ln)
    if m:
        part = [int(m.group(1)), m.group(2), int(m.group(3))]
    m = re.match(r"^(\d+\.[A-Z])\s+(.+?) — (\d+) clips", ln)
    if m:
        sect = [m.group(1), m.group(2), int(m.group(3))]
    m = re.match(r"^\[([A-Z]{3,4}(?:-[A-Z]{3})?)-(\d{3})\]\s*(.*)$", ln)
    if m:
        if cur:
            entries.append(cur)
        cur = dict(pref=m.group(1), num=int(m.group(2)), head=m.group(3).strip(),
                   part=part, sect=sect, body=[])
    elif cur is not None:
        if re.match(r"^-{10,}|^#{10,}|^PARTIE|^\d+\.[A-Z]\s|^\[", ln):
            entries.append(cur); cur = None
        else:
            cur["body"].append(ln.rstrip())
if cur:
    entries.append(cur)

NUMRE = r"(\d+[.,]\d+|\d+)"

def parse(e):
    body = "\n".join(e["body"])
    full = e["head"] + "\n" + body
    d = dict(id="A-%03d" % e["num"], num=e["num"], prefix=e["pref"],
             part=e["part"], section=e["sect"])

    # --- nom du clip ------------------------------------------------------
    head = e["head"]
    tag = ""
    mt = re.search(r"\[([^\]]+)\]\s*$", head)
    if mt:
        tag = mt.group(1).strip(); head = head[:mt.start()].strip()
    # format compact : "nom — 4,2 s BOUCLE — description"
    inline_desc = ""
    if "—" in head:
        bits = [b.strip() for b in head.split("—")]
        name = bits[0]
        inline_desc = " — ".join(bits[1:])
    else:
        name = head
    d["name"] = name.strip()
    d["tag"] = tag

    # --- duree ------------------------------------------------------------
    m = re.search(NUMRE + r"\s*s\b", full)
    d["dur"] = float(m.group(1).replace(",", ".")) if m else None
    m = re.search(r"\((\d+)\s*f\)", full)
    d["frames"] = int(m.group(1)) if m else (
        int(round(d["dur"] * 30)) if d["dur"] else None)

    # --- type / root motion ----------------------------------------------
    d["loop"] = "BOUCLE" if re.search(r"\bBOUCLE\b", full) else "ONE-SHOT"
    m = re.search(r"\bRM-(FULL|ROT|NONE)\b", full)
    d["rm"] = "RM-" + m.group(1) if m else None

    # --- blend / couche / priorite ----------------------------------------
    m = re.search(r"Blend\s+" + NUMRE + r"\s*/\s*" + NUMRE, full)
    d["blend"] = [float(m.group(1).replace(",", ".")),
                  float(m.group(2).replace(",", "."))] if m else None
    m = re.search(r"·\s*(L\d(?:\+L\d)*)\s*·", full)
    d["layer"] = m.group(1) if m else None
    m = re.search(r"Prio\s+(\d+)", full)
    d["prio"] = int(m.group(1)) if m else None

    # --- champs rediges ----------------------------------------------------
    def field(key):
        m = re.search(r"^\s*" + key + r"\s*:\s*(.*?)(?=^\s*(?:Os|Jeu|Evt|Trans)\s*:|\Z)",
                      body, re.S | re.M)
        if not m:
            return ""
        txt = " ".join(x.strip() for x in m.group(1).split("\n"))
        return re.sub(r"\s+", " ", txt).strip()

    d["bones"] = field("Os")
    d["jeu"] = field("Jeu")
    d["evt"] = field("Evt")
    d["trans"] = field("Trans")

    if not d["jeu"]:
        # entree compacte : la description est apres le tiret cadratin
        txt = (inline_desc + " " + " ".join(e["body"])).strip()
        txt = re.sub(r"^\d+[.,]\d+\s*s\s*(BOUCLE|ONE-SHOT)\s*—?\s*", "", txt)
        d["jeu"] = re.sub(r"\s+", " ", txt).strip()

    d["source"] = "manifeste"
    return d

clips = [parse(e) for e in entries]
out = ROOT / "anim_data"
out.mkdir(exist_ok=True)
(out / "clips_extracted.json").write_text(
    json.dumps(clips, ensure_ascii=False, indent=1), encoding="utf-8")

print("clips extraits :", len(clips))
miss = [c["id"] for c in clips if not c["dur"]]
print("sans duree :", len(miss), miss[:10])
print("sans jeu   :", len([c for c in clips if not c["jeu"]]))
