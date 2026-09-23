#!/usr/bin/env python3
"""Extrait du GDD les contenus à format régulier vers app/src/main/assets/data.

  pages     5.16  -> data/pages/fr/pages.pg        (24 pages d'Esteban)
  bells     Ann.M -> data/tables/fr/bells.txt      (cloches nominatives du verger)
  bornes    Ann.N -> data/tables/fr/bornes.txt     (bornes / repères géodésiques)
  murmures  Ann.O -> data/tables/fr/murmures.txt
  labels    Ann.Q/F -> data/tables/fr/labels.txt   (étiquettes du bois des noms)
  letter    LETTRE_ESTEBAN_A_LOHEN.txt -> data/tables/fr/letter.txt (11 plis)

Les dialogues (BLOC 05) et les fiches d'énigmes sont transcrits à la main
(data/dialogues/fr/*.dlg, data/puzzles/fr/puzzles.pzl) : leur mise en forme
mêle didascalies et commentaires de conception qu'un extracteur ne sait pas
trier proprement.
"""
import os, re, sys, json

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GDD = os.path.join(ROOT, "GDD_LA_CARTOGRAPHIE_DES_ABSENTS.txt")
LETTER = os.path.join(ROOT, "LETTRE_ESTEBAN_A_LOHEN.txt")
DATA = os.path.join(ROOT, "app", "src", "main", "assets", "data")

with open(GDD, encoding="utf-8") as f:
    LINES = f.read().split("\n")


def section(start_pat, end_pat, start_from=0):
    """Retourne (index_debut, lignes) de la section délimitée par deux regex."""
    s = None
    for i in range(start_from, len(LINES)):
        if re.search(start_pat, LINES[i]):
            s = i
            break
    if s is None:
        raise SystemExit(f"section introuvable: {start_pat}")
    e = len(LINES)
    for i in range(s + 1, len(LINES)):
        if re.search(end_pat, LINES[i]):
            e = i
            break
    return s, LINES[s:e]


def write(rel, text):
    path = os.path.join(DATA, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"  -> {rel} ({len(text)} o)")


# ───────────────────────────── PAGES (5.16) ──────────────────────────────
def extract_pages():
    _, sec = section(r"^ 5\.16 — LES VINGT-QUATRE PAGES", r"^ NOTES DESIGN DES PAGES")
    text = "\n".join(sec)
    out = ["# Les vingt-quatre pages d'Esteban (GDD 5.16) — texte intégral.",
           "# @ n | lieu de découverte | date | oiseaux    puis le texte, ligne par ligne ; « [barré: …] » = rature visible.",
           ""]
    pat = re.compile(r"PAGE (\d\d) — Trouvée : (.*?)\.?\s*Date(?: du texte)? : ([^.]*?)\.?\s*(?:\(.*?\))?\s*Oiseaux : (\d+)\.(.*?)«(.*?)»", re.S)
    found = 0
    for m in pat.finditer(text):
        n, where, date, birds, _, body = m.groups()
        where = re.sub(r"\s+", " ", where).strip()
        where = re.sub(r"\[SQ-\d+\]\s*", "", where)
        where = re.sub(r"\s*\(.*?\)\s*", " ", where).strip()
        date = re.sub(r"\s+", " ", date).strip()
        if len(date) > 24: date = date.split(",")[0]
        body = re.sub(r"[ \t]*\n[ \t]*", "\n", body.strip())
        # Recolle les lignes coupées par la mise en page du GDD : une ligne du GDD
        # qui ne finit pas par « — » ou « ) » continue la suivante.
        lines, cur = [], ""
        for ln in body.split("\n"):
            ln = ln.strip()
            if not ln:
                continue
            cur = (cur + " " + ln).strip() if cur else ln
            if ln.endswith("—") or ln.endswith(")") or ln.endswith("]") or ln.endswith(":"):
                lines.append(cur)
                cur = ""
        if cur:
            lines.append(cur)
        out.append(f"@ {int(n)} | {where} | {date} | {birds}")
        out.extend(lines)
        out.append("")
        found += 1
    assert found == 24, f"pages trouvées: {found}"
    write("pages/fr/pages.pg", "\n".join(out))


# ───────────────────────────── LETTRE (BLOC 14 / fichier) ─────────────────
def extract_letter():
    with open(LETTER, encoding="utf-8") as f:
        raw = f.read()
    out = ["# La lettre d'Esteban à Lohen — texte intégral (LETTRE_ESTEBAN_A_LOHEN.txt / GDD BLOC 14).",
           "# @ pli n | titre    puis les paragraphes ; ligne vide = saut de paragraphe.", ""]
    out.append(raw.strip())
    write("tables/fr/letter_raw.txt", "\n".join(out))


def main():
    what = sys.argv[1:] or ["pages", "letter"]
    for w in what:
        print(f"== {w}")
        globals()["extract_" + w]()


if __name__ == "__main__":
    main()
