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


# ───────────────────────────── CLOCHES (Annexe M) ─────────────────────────
def _entries(sec_lines, head_re):
    """Découpe une annexe en entrées commençant par head_re (le reste = continuation)."""
    entries, cur = [], None
    for ln in sec_lines:
        m = re.match(head_re, ln)
        if m:
            if cur: entries.append(cur)
            cur = [m, ln[m.end():]]
        elif cur is not None:
            if ln.strip().startswith("────") or ln.strip().startswith("════"):
                continue
            cur[1] += " " + ln.strip()
    if cur: entries.append(cur)
    return entries


def _clean(t):
    t = re.sub(r"\s+", " ", t).strip()
    return t


def _quoted(t):
    m = re.search(r"«(.*?)»", t, re.S)
    return _clean(m.group(1)) if m else ""


def _strip_parens(t):
    # supprime les parenthèses de commentaire (imbriquées un niveau)
    prev = None
    while prev != t:
        prev = t
        t = re.sub(r"\([^()]*\)", "", t)
    return _clean(t)


BELL_OVERRIDES = {
    4: ("L'ÉQUIPAGE DU SAINT-EUMÈLE (neuf noms), 1707", "", "neuf coups, un par sauvé — pas de voix : la cloche compte."),
    8: ("RAOUL, 1917", "", "morse : ·−·· · ·· −·−− (RAOUL) — la seule cloche morse du verger."),
    14: ("PIERRE (LE PÈRE SORÈNE), 2009", "", "trois coups, comme le pilote du large clignote — pas de voix."),
    41: ("ESTEBAN", "", "sans fiche — si la grande cloche a été nommée ESTEBAN, la 41 tient une seule note, neuf secondes, avec elle."),
    43: ("LA DERNIÈRE", "", "muette : le fondeur l'a laissée sans battant. « la dernière cloche du verger sonnera pour la fin de la Brume. on n'en est pas là. on l'attend. »"),
}


def extract_bells():
    _, sec = section(r"^ ANNEXE M — LES 43 CLOCHES", r"^ ANNEXE N —")
    out = ["# Les 43 cloches nominatives du verger (GDD Annexe M). Chaque cloche sonnée joue sa micro-fiche.",
           "# Format : n° | nom, année | texte (vide = cloche sans voix : voir note) | note", ""]
    n = 0
    for m, body in _entries(sec, r"^ (\d\d) (.+?)(?: — |—$| —$)"):
        num, name = m.group(1), _clean(m.group(2)).rstrip(",")
        text = _quoted(body)
        note = ""
        if not text:
            note = _strip_parens(body) or _clean(body.strip(" ()"))
            note = _clean(body).strip("() ")
        num = int(num)
        if num in BELL_OVERRIDES:
            name, text, note = BELL_OVERRIDES[num]
        out.append(f"{num} | {name} | {text} | {note}")
        n += 1
    assert n == 43, n
    write("tables/fr/bells.txt", "\n".join(out))


def extract_bornes():
    _, sec = section(r"^ ANNEXE N — LES 40 BORNES", r"^ ANNEXE O —")
    out = ["# Les 40 bornes d'arpenteur (GDD Annexe N) : relevé gravé, lisible à la lentille.",
           "# Format : id | lieu | main, année | texte", ""]
    n = 0
    for m, body in _entries(sec, r"^ \[B(\d\d)\] ([^—]+?) — ([^—]+?) —"):
        bid, place, hand = m.group(1), _clean(m.group(2)), _clean(m.group(3))
        text = _quoted(body)
        out.append(f"B{bid} | {place} | {hand} | {text}")
        n += 1
    assert n >= 39, n
    write("tables/fr/bornes.txt", "\n".join(out))


def extract_murmures():
    _, sec = section(r"^ ANNEXE O — REGISTRE COMPLET DES 40", r"^ ANNEXE P —")
    out = ["# Les 18 murmures de niveau 1 (GDD Annexe O) : détail caché + pensée.",
           "# Format : id | titre | zone-condition | pensée", ""]
    n = 0
    for m, body in _entries(sec, r"^ \[M-(\d\d)\] ([^—]+?) — ([^—]+?) —\s*"):
        mid, title, cond = m.group(1), _clean(m.group(2)), _clean(m.group(3))
        text = _quoted(body)
        out.append(f"M-{mid} | {title} | {cond} | {text}")
        n += 1
    assert n >= 18, n
    write("tables/fr/murmures.txt", "\n".join(out))


def extract_labels():
    out = ["# Les étiquettes du bois des noms (GDD Annexe F 1→20 et Annexe Q 51→80).",
           "# Format : n° | texte", ""]
    n = 0
    for start, end in ((r"^ ANNEXE F — LES 80 ÉTIQUETTES", r"^ ANNEXE G —"), (r"^ ANNEXE Q — LES ÉTIQUETTES 51", r"^ ANNEXE R —")):
        _, sec = section(start, end)
        for m, body in _entries(sec, r"^ (\d{1,2}) «"):
            num = int(m.group(1))
            text = _clean(("«" + body).split("»")[0].lstrip("«"))
            out.append(f"{num} | {text}")
            n += 1
    assert n >= 50, n
    write("tables/fr/labels.txt", "\n".join(out))


def extract_archives():
    _, sec = section(r"^ ANNEXE R — ARCHIVES", r"^ ANNEXE S —|^ ANNEXE T —|^ FIN DES ANNEXES|^═+$")
    out = ["# Archives (GDD Annexe R) : documents croisés, texte intégral.",
           "# @ AR-xx | titre    puis les lignes du document.", ""]
    cur = None
    n = 0
    for ln in sec[1:]:
        m = re.match(r"^ \[(AR-\d\d)\] (.+?)(?: — EXTRAITS)?\s*(?:\(|:|$)", ln)
        if m:
            if cur: out.append("")
            out.append(f"@ {m.group(1)} | {_clean(m.group(2))}")
            cur = m.group(1); n += 1
            continue
        if cur is None:
            continue
        t = ln.strip()
        if not t or t.startswith("────") or t.startswith("════"):
            continue
        t = t.replace("── ", "— ")
        if out[-1].startswith("@ ") and re.search(r"\)\s*:\s*$", t):
            continue  # fin de la parenthèse d'en-tête (conditions d'accès)
        out.append(t)
    assert n >= 3, n
    write("tables/fr/archives.txt", "\n".join(out))


def main():
    what = sys.argv[1:] or ["pages", "bells", "bornes", "murmures", "labels", "archives"]
    for w in what:
        print(f"== {w}")
        globals()["extract_" + w]()


if __name__ == "__main__":
    main()
