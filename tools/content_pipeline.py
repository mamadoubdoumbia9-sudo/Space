#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LOHEN — Les Sept Lettres de Velmora · Chapitre 1
tools/content_pipeline.py

Convertit les annexes canoniques (raw_content/) en donnees de jeu
(content/) consommables par le runtime natif Android.

Sources (source unique de verite, cf. BLOC 12.01 et INDEX annexe C) :
  raw_content/dialogues/S1..S8/*.txt   -> content/dialogues/*.json   (2 833 lignes)
  raw_content/dialogues/BARKS/*.txt    -> cooldowns / regimes
  raw_content/dialogues/SYSTEME/*.txt  -> content/quests/choices.json (38 choix)
  raw_content/props/S1..S8/*.txt       -> content/props/props.json    (147 props)
  raw_content/anim/**/*.txt            -> content/anims/anims.json    (744 clips)
  raw_content/LOHEN_PROMPT_MASTER.txt  -> content/echos/echos.json    (31 echos)
                                        -> content/letter/lettre.json (BLOC 20)
                                        -> content/tuning/*.json      (BLOC 08)

Aucune ligne [NAR] n'est reecrite : le texte est extrait tel quel.
Usage : python3 tools/content_pipeline.py [--verbose]
"""
import argparse
import collections
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "raw_content")
OUT = os.path.join(ROOT, "content")

# ---------------------------------------------------------------------------
# Utilitaires
# ---------------------------------------------------------------------------

ENTRY_RE = re.compile(r"^\s*\[([A-Z0-9]+_[A-Z0-9]+_L[0-9]+[A-Za-z]?)\]\s*(.*)$")
DUR_RE = re.compile(r"^([0-9]+),([0-9]+)\s*s$")
PRIO_RE = re.compile(r"^p([0-9]+),([0-9]+)$")
TEXT_OPEN = "\u00ab"   # «
TEXT_CLOSE = "\u00bb"  # »

ROLE_CODES = {
    "LOH": "lohen", "LO": "lohen", "EST": "esteban", "ES": "esteban",
    "MIR": "mireille", "MI": "mireille", "TAL": "tallec", "TA": "tallec",
    "SOL": "sol", "SO": "sol", "ECH": "echo_voix", "EC": "echo_voix",
    "BK": "bark", "PAL": "pallas", "VER": "verrier", "MUE": "mueur",
    "SYS": "systeme", "FOU": "foule", "PNJ": "pnj", "OFF": "lohen_off",
    "CHA": "chanteuse",
}


def fr_number(value):
    """'1,4' -> 1.4 (notation francaise du corpus)."""
    try:
        return float(str(value).replace(",", "."))
    except (TypeError, ValueError):
        return 0.0


def clean_text(raw):
    """Retire les guillemets francais encadrants et normalise les espaces."""
    txt = raw.strip()
    txt = re.sub(r"\s+", " ", txt)
    if txt.startswith(TEXT_OPEN):
        txt = txt[1:]
    if txt.endswith(TEXT_CLOSE):
        txt = txt[:-1]
    return txt.strip()


def role_key(speaker, line_id):
    """Normalise un nom de locuteur en cle stable (minuscule, sans accent)."""
    sp = (speaker or "").strip()
    sp = re.sub(r"\(.*?\)", "", sp).strip()
    if not sp:
        code = line_id.split("_")[1]
        return ROLE_CODES.get(code, code.lower())
    low = sp.lower()
    table = {
        "lohen": "lohen", "esteban": "esteban", "sol": "sol",
        "mireille": "mireille", "tallec": "tallec", "pallas": "pallas",
        "le verrier": "verrier", "verrier": "verrier", "anselme": "verrier",
    }
    for key, val in table.items():
        if low.startswith(key):
            return val
    slug = re.sub(r"[^a-z0-9]+", "_", low).strip("_")
    return slug or "inconnu"


def write_json(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=1, sort_keys=False)
        handle.write("\n")
    return os.path.getsize(path)


# ---------------------------------------------------------------------------
# 1. DIALOGUES — 2 833 lignes canoniques
# ---------------------------------------------------------------------------

def classify_field(field):
    """Identifie la nature d'un champ separe par '·' dans une fiche de ligne."""
    if DUR_RE.match(field):
        return "dur"
    if PRIO_RE.match(field):
        return "prio"
    if TEXT_OPEN in field or TEXT_CLOSE in field or field.startswith("("):
        return "text"
    if re.match(r"^[0-9]", field):
        return "dur"
    return "speaker"


def parse_dialogue_file(path, seq):
    """Analyse un fichier DLG_*.txt : lignes, choix, branches, contexte."""
    with open(path, encoding="utf-8") as handle:
        lines = handle.read().split("\n")

    name = os.path.basename(path)
    m = re.match(r"DLG_(S\d)_(D\d+)_(.+)\.txt$", name)
    scene_id = "%s_%s" % (m.group(1), m.group(2)) if m else name[:-4]
    title = (m.group(3).replace("-", " ").upper() if m else name)

    scene = {
        "id": scene_id,
        "seq": seq,
        "file": os.path.relpath(path, RAW),
        "title": title,
        "lines": [],
        "choices": [],
        "context": "",
    }

    # contexte : bloc "--- CONTEXTE" / "--- INTENTION" jusqu'au separateur suivant
    ctx = []
    in_ctx = False
    for ln in lines:
        if re.match(r"^---\s+(CONTEXTE|INTENTION)", ln):
            in_ctx = True
            continue
        if in_ctx:
            if ln.startswith("---") or ln.startswith("==="):
                break
            if ln.strip():
                ctx.append(ln.strip())
    scene["context"] = " ".join(ctx)[:900]

    cur = None          # ligne en cours de construction
    branch = None       # branche de choix active
    pending_choice = None
    i = 0
    while i < len(lines):
        raw = lines[i]
        i += 1

        # --- choix -------------------------------------------------------
        ch = re.match(r"^\s*>\s*CHOIX\s*(\d+)?\s*(.*)$", raw)
        if ch:
            note = (ch.group(2) or "").strip()
            note = re.sub(r"^\((.*)\)$", r"\1", note).strip()
            note = re.sub(r"^\u2014\s*", "", note).strip()
            pending_choice = {
                "id": "%s_C%s" % (scene_id, (ch.group(1) or str(len(scene["choices"]) + 1)).zfill(2)),
                "no": int(ch.group(1)) if ch.group(1) else None,
                "timer": 0.0,
                "note": note,
                "options": [],
                "branches": {},
                "after": None,
            }
            tm = re.search(r"([0-9]+),([0-9]+)\s*s", pending_choice["note"])
            if tm:
                pending_choice["timer"] = fr_number("%s,%s" % (tm.group(1), tm.group(2)))
            scene["choices"].append(pending_choice)
            branch = None
            continue

        opt = re.match(r"^\s*\[(\d)\]\s*(.+)$", raw)
        if opt and pending_choice is not None and branch is None:
            pending_choice["options"].append({
                "index": int(opt.group(1)),
                "text": clean_text(opt.group(2)),
                "silence": opt.group(2).strip().startswith("("),
            })
            continue

        br = re.match(r"^\s*--\s*branche\s*\[(\d)\]", raw)
        if br:
            branch = str(br.group(1))
            if pending_choice is not None:
                pending_choice["branches"].setdefault(branch, [])
            continue
        if re.match(r"^\s*--\s*(fin de branche|apres le choix|suite)", raw):
            branch = None
            continue

        # --- ligne canonique --------------------------------------------
        m = ENTRY_RE.match(raw)
        if m:
            line_id = m.group(1)
            rest = m.group(2)
            fields = [f.strip() for f in rest.split("\u00b7") if f.strip()]
            speaker, dur, emo, prio, text = "", 0.0, "", 0.5, ""
            for f in fields:
                kind = classify_field(f)
                if kind == "dur" and not dur:
                    dm = DUR_RE.match(f)
                    if dm:
                        dur = fr_number("%s,%s" % (dm.group(1), dm.group(2)))
                    else:
                        nm = re.match(r"^([0-9]+(?:,[0-9]+)?)", f)
                        dur = fr_number(nm.group(1)) if nm else 0.0
                elif kind == "prio":
                    prio = fr_number(PRIO_RE.match(f).group(0)[1:])
                elif kind == "text" and not text:
                    text = f
                elif kind == "speaker" and not speaker:
                    speaker = f
                elif kind == "text" and not emo:
                    emo = f
                elif not emo and kind == "speaker" and speaker:
                    emo = f
            # texte eventuellement sur les lignes suivantes
            buf = [text] if text else []
            open_quote = TEXT_OPEN in " ".join(buf) and TEXT_CLOSE not in " ".join(buf)
            while i < len(lines):
                nxt = lines[i]
                if not nxt.strip():
                    break
                if ENTRY_RE.match(nxt) or nxt.strip().startswith((">", "--", "---", "===", "[")):
                    break
                if nxt.strip().startswith("dir :") or nxt.strip().startswith("dir:"):
                    break
                buf.append(nxt.strip())
                i += 1
                joined = " ".join(buf)
                if TEXT_CLOSE in joined:
                    open_quote = False
                    break
                if not open_quote and len(buf) > 3:
                    break
            body = clean_text(" ".join(buf))
            # direction d'acteur
            direction = ""
            if i < len(lines):
                dm = re.match(r"^\s*dir\s*:\s*(.*)$", lines[i])
                if dm:
                    dparts = [dm.group(1)]
                    i += 1
                    while i < len(lines) and lines[i].startswith(" " * 14) and lines[i].strip() \
                            and not ENTRY_RE.match(lines[i]):
                        dparts.append(lines[i].strip())
                        i += 1
                    direction = re.sub(r"\s+", " ", " ".join(dparts)).strip()
            cur = {
                "id": line_id,
                "spk": role_key(speaker, line_id),
                "spk_label": (speaker or "").strip() or line_id.split("_")[1],
                "dur": round(dur, 2),
                "emo": emo,
                "prio": round(prio, 2),
                "fr": body,
                "dir": direction[:300],
                "bark": "_BK_" in line_id,
                "pause_after": 0.45,
            }
            # silences ecrits dans le JSON (12.04) : "(pause 1,4 s)"
            pm = re.search(r"\(pause\s+([0-9]+),([0-9]+)\s*s\)", body)
            if pm:
                cur["pause_after"] = fr_number("%s,%s" % (pm.group(1), pm.group(2)))
                cur["fr"] = re.sub(r"\s*\(pause[^)]*\)\s*", " ", cur["fr"]).strip()
            scene["lines"].append(cur)
            if pending_choice is not None and branch is not None:
                pending_choice["branches"][branch].append(line_id)
            continue

        # pause declaree sur la ligne precedente
        pm2 = re.match(r"^\s*\(?(un temps|temps)\s*([0-9],?[0-9]?\s*s)?\)?\s*$", raw.strip(), re.I)
        if pm2 and scene["lines"]:
            scene["lines"][-1]["pause_after"] = fr_number(
                (pm2.group(2) or "0,7").replace(" s", "").replace(",", "."))
    return scene


def build_dialogues(verbose=False):
    per_seq = collections.defaultdict(list)
    total_lines = 0
    total_barks = 0
    total_choices = 0
    for path in sorted(glob.glob(os.path.join(RAW, "dialogues", "S*", "*.txt"))):
        seq = os.path.basename(os.path.dirname(path))
        scene = parse_dialogue_file(path, seq)
        if not scene["lines"] and not scene["choices"]:
            continue
        per_seq[seq].append(scene)
        total_lines += len(scene["lines"])
        total_barks += sum(1 for l in scene["lines"] if l["bark"])
        total_choices += len(scene["choices"])

    # regimes de cooldown des barks (annexe BARKS §3)
    cooldown = {"S1": 660.0, "S2": 480.0, "S3": 480.0, "S4": 480.0,
                "S5": 480.0, "S6": 1200.0, "S7": 270.0, "S8": 480.0}
    gains = {"S1": 0.75, "S2": 1.0, "S3": 1.0, "S4": 1.0, "S5": 0.55,
             "S6": 1.0, "S7": 1.0, "S8": 1.0}

    size = 0
    for seq, scenes in sorted(per_seq.items()):
        payload = {
            "seq": seq,
            "bark_cooldown_s": cooldown.get(seq, 480.0),
            "bark_gain": gains.get(seq, 1.0),
            "scenes": scenes,
        }
        size += write_json(os.path.join(OUT, "dialogues", "dialogues_%s.json" % seq.lower()), payload)
    if verbose:
        print("  dialogues : %d scenes, %d lignes (dont %d barks), %d choix, %.1f Ko"
              % (sum(len(v) for v in per_seq.values()), total_lines, total_barks,
                 total_choices, size / 1024.0))
    return {"scenes": sum(len(v) for v in per_seq.values()), "lines": total_lines,
            "barks": total_barks, "choices": total_choices}


# ---------------------------------------------------------------------------
# 2. PROPS NARRATIFS — 147
# ---------------------------------------------------------------------------

def parse_prop_file(path):
    with open(path, encoding="utf-8") as handle:
        txt = handle.read()
    name = os.path.basename(path)
    m = re.match(r"PROP_(PN-\d+)_(.+)\.txt$", name)
    pid = m.group(1) if m else name
    slug = m.group(2) if m else name

    head = re.search(r"^=+\n(PN-\d+)\s*[—-]\s*(.+?)\n", txt, re.M)
    title = head.group(2).strip() if head else slug.replace("-", " ").upper()

    def field(label):
        fm = re.search(r"^%s\s*:\s*(.+?)(?:\n(?=[A-Z][A-Za-z ]{2,}\s*:)|\n-{5,}|\Z)" % label,
                       txt, re.M | re.S)
        if not fm:
            return ""
        return re.sub(r"\s+", " ", fm.group(1)).strip()

    seq_m = re.search(r"Sequence\s*:\s*(S\d)", txt)
    pos = field("Position")
    timing = field("Timing")
    statut = field("Statut")
    asset = field("Asset")
    lods = field("LODs")

    # § 1 — CE QU'ON VOIT (texte descriptif, lu dans le journal / a l'inspection)
    sec = re.search(r"\u00a7\s*1\s*[—-]+\s*CE QU'ON VOIT\s*-+\n(.*?)\n-{20,}", txt, re.S)
    seen = ""
    if sec:
        seen = re.sub(r"[ \t]+", " ", sec.group(1)).strip()
        seen = re.sub(r"\n+", "\n", seen)

    echo = None
    em = re.search(r"\b(E\d{2})\b", statut + " " + txt[:1500])
    if em and "ECHO" in txt[:2000].upper():
        echo = em.group(1)
    collision = field("Collision")[:220]
    climbable = bool(re.search(
        r"grimpable|montable|praticable|peut y monter|peut y remonter|on peut s'y tenir|"
        r"se tenir dessus|le joueur peut marcher|le joueur entre|y grimper", txt, re.I))
    pickup = bool(re.search(r"entre dans l'inventaire|ramassable|inventaire", txt, re.I))
    readable = bool(re.search(r"texte lisible|lisible en zoom|on peut lire", txt, re.I))

    tris = 0
    tm = re.search(r"LOD0\s*([0-9][0-9 ]{2,8})\s*tris", txt)
    if tm:
        tris = int(tm.group(1).replace(" ", ""))

    return {
        "id": pid,
        "slug": slug,
        "name": title,
        "seq": seq_m.group(1) if seq_m else os.path.basename(os.path.dirname(path)),
        "asset": asset,
        "position_note": pos,
        "timing": timing,
        "statut": statut,
        "lods": lods,
        "tris_lod0": tris,
        "echo": echo,
        "climbable": climbable,
        "collision": collision,
        "pickup": pickup,
        "readable": readable,
        "seen": seen[:2600],
    }


def build_props(verbose=False):
    props = []
    for path in sorted(glob.glob(os.path.join(RAW, "props", "S*", "PROP_*.txt"))):
        props.append(parse_prop_file(path))
    props.sort(key=lambda p: p["id"])
    size = write_json(os.path.join(OUT, "props", "props.json"), {"props": props})
    if verbose:
        with_echo = sum(1 for p in props if p["echo"])
        print("  props     : %d fiches (%d porteurs d'Echo, %d grimpables), %.1f Ko"
              % (len(props), with_echo, sum(1 for p in props if p["climbable"]), size / 1024.0))
    return {"count": len(props)}


CHOICE_TABLE = os.path.join(RAW, "dialogues", "SYSTEME", "LES_38_CHOIX.txt")


def build_choices(verbose=False):
    """Table canonique des 38 choix (annexe C §7) croisee avec les scenes."""
    rows = []
    if os.path.exists(CHOICE_TABLE):
        for ln in open(CHOICE_TABLE, encoding="utf-8"):
            m = re.match(r"^\s+(\d{2})\s+(S\d)\s+(D\d{2}(?:\s*/\s*D\d{2})?)\s+"
                         r"(.{4,50}?)\s{2,}(non|\d+,\d\s*s)\s{2,}(.+?)\s*$", ln)
            if not m:
                m2 = re.match(r"^\s+(\d{2})\s+(S\d)\s+(D\d{2}(?:\s*/\s*D\d{2})?)\s+(.+)$", ln)
                if m2:
                    tail = m2.group(4)
                    tm = re.search(r"(non|\d+,\d\s*s)\s+(\S.*?)$", tail)
                    if tm:
                        m = type("M", (), {"group": lambda self, i, _t=tail, _m=m2, _tm=tm: {
                            1: _m.group(1), 2: _m.group(2), 3: _m.group(3),
                            4: _t[:_tm.start(1)], 5: _tm.group(1), 6: _tm.group(2)}[i]})()
            if m:
                rows.append({
                    "no": int(m.group(1)), "seq": m.group(2), "doc": m.group(3).replace(" ", ""),
                    "title": m.group(4).strip(),
                    "timer_s": 0.0 if m.group(5).strip() == "non" else fr_number(
                        m.group(5).replace(" s", "")),
                    "effect": m.group(6).strip(),
                    "displays_options": int(m.group(1)) not in (36, 37, 38),
                })
    # regles (annexe C §1)
    rules = {
        "no_moral_choice": True, "no_reputation_system": True,
        "no_important_marker": True, "timers": [{"no": 17, "s": 9.4}, {"no": 18, "s": 14.2}],
        "timer_visual": "perte d'opacite lineaire, jamais de barre ni de rouge",
        "silence_is_valid": True, "silence_count": 9, "silence_position": "last",
        "silence_best_branch_count": 6,
        "not_replayable": True, "single_autosave": True,
        "change_one_line": 34, "bigger_effects": [4, 18, 20, 34],
        "max_options": 3, "total": 38,
        "expiry": "le silence est joue comme une reponse a part entiere",
    }
    # croisement avec les options ecrites dans les scenes
    linked = 0
    for row in rows:
        path = os.path.join(OUT, "dialogues", "dialogues_%s.json" % row["seq"].lower())
        if not os.path.exists(path):
            continue
        data = json.load(open(path, encoding="utf-8"))
        for scene in data["scenes"]:
            if row["doc"].split("/")[0] not in scene["id"]:
                continue
            for ch in scene["choices"]:
                if ch.get("no") != row["no"]:
                    continue
                row["scene"] = scene["id"]
                row["choice_id"] = ch["id"]
                row["options"] = ch["options"]
                row["branches"] = ch["branches"]
                linked += 1
    size = write_json(os.path.join(OUT, "quests", "choices.json"),
                      {"total": len(rows), "rules": rules, "choices": rows})
    if verbose:
        print("  choix     : %d choix canoniques (%d relies a leurs options), %.1f Ko"
              % (len(rows), linked, size / 1024.0))
    return {"count": len(rows), "linked": linked}


# ---------------------------------------------------------------------------
# 3. ANIMATIONS — 744 clips
# ---------------------------------------------------------------------------

FAMILIES = [
    ("idle", "IDLE"), ("walk", "LOCOMOTION"), ("jog", "LOCOMOTION"), ("run", "LOCOMOTION"),
    ("sprint", "LOCOMOTION"), ("turn", "LOCOMOTION"), ("start", "LOCOMOTION"),
    ("stop", "LOCOMOTION"), ("crouch", "LOCOMOTION"), ("slope", "LOCOMOTION"),
    ("stairs", "LOCOMOTION"), ("slide", "TRAVERSEE"), ("stumble", "LOCOMOTION"),
    ("wind", "ADDITIF"), ("jump", "AERIEN"), ("fall", "AERIEN"), ("land", "AERIEN"),
    ("air", "AERIEN"), ("ledge", "TRAVERSEE"), ("wallrun", "TRAVERSEE"),
    ("vault", "TRAVERSEE"), ("grapple", "GRAPPIN"), ("guard", "COMBAT"),
    ("strike", "COMBAT"), ("parry", "COMBAT"), ("dodge", "COMBAT"),
    ("stagger", "COMBAT"), ("knockdown", "COMBAT"), ("getup", "COMBAT"),
    ("execute", "COMBAT"), ("block", "COMBAT"), ("combat", "COMBAT"),
    ("hurt", "COMBAT"), ("death", "COMBAT"), ("echo", "ECHO"), ("carry", "PORTER"),
    ("give", "DONNER"), ("letter", "LETTRE"), ("unfold", "LETTRE"), ("fold", "LETTRE"),
    ("glove", "RITUEL"), ("touch", "RITUEL"), ("interact", "INTERACTION"),
    ("dance", "BAL"), ("sit", "INTERACTION"), ("push", "INTERACTION"),
    ("pull", "INTERACTION"), ("climb", "TRAVERSEE"), ("hang", "TRAVERSEE"),
    ("shimmy", "TRAVERSEE"), ("breath", "ADDITIF"), ("look", "ADDITIF"),
    ("blink", "VISAGE"), ("jaw", "VISAGE"), ("smirk", "VISAGE"),
]


def anim_family(clip):
    low = clip.lower()
    for prefix, fam in FAMILIES:
        if low.startswith(prefix) or ("_" + prefix) in low:
            return fam
    return "ACTING"


def parse_anim_file(path):
    with open(path, encoding="utf-8") as handle:
        txt = handle.read()
    name = os.path.basename(path)
    m = re.match(r"(A-\d+)_(.+)\.txt$", name)
    aid = m.group(1) if m else name
    clip = (m.group(2) if m else name).replace("_", "_")

    def grab(label, default=""):
        fm = re.search(r"%s\s*(?:[.:]{2,}|:)\s*(.+)" % label, txt)
        return fm.group(1).strip() if fm else default

    def section(num, stop_nums=(1, 2, 3, 4, 5, 6, 7)):
        pat = r"\n%s\.\s+[A-Z][^\n]*\n-+\n(.*?)(?=\n-+\n\s*\d+\.|\Z)" % num
        sm = re.search(pat, txt, re.S)
        return re.sub(r"\s+", " ", sm.group(1)).strip() if sm else ""

    dur = 0.0
    dm = re.search(r"Duree\s*[.:]{2,}\s*([0-9]+[.,][0-9]+)\s*s", txt)
    if dm:
        dur = fr_number(dm.group(1))
    frames = 0
    fm2 = re.search(r"\(([0-9]+)\s*frames", txt)
    if fm2:
        frames = int(fm2.group(1))
    if not dur and frames:
        dur = frames / 30.0
    loop = "BOUCLE" in grab("Type", "") or "BOUCLE" in txt[:1200]
    root = grab("Root motion", "RM-NONE")
    blend = grab(r"Blend in / out", "0.20 s / 0.20 s")
    bm = re.findall(r"([0-9]+[.,][0-9]+)\s*s", blend)
    bin_ = fr_number(bm[0]) if bm else 0.2
    bout = fr_number(bm[1]) if len(bm) > 1 else 0.2
    layer = grab("Couche AnimationTree", "L0")
    prio = 0
    pm = re.search(r"Priorite\s*[.:]{2,}\s*([0-9]+)", txt)
    if pm:
        prio = int(pm.group(1))
    part = re.search(r"Partie\s+(\d+)\s*[—-]+\s*(.+)", txt)
    character = part.group(2).strip() if part else "INCONNU"
    cat = grab("Categorie", "") or grab("Cat\u00e9gorie", "")

    jeu = section(3)[:500]
    if not jeu:
        jm = re.search(r"\n\s*Jeu\s*(?:[.:]{2,}|:)\s*(.+?)(?:\n\s*[A-Z][a-z]+\s*(?:[.:]{2,}|:)|\n-{10,}|\Z)", txt, re.S)
        jeu = re.sub(r"\s+", " ", jm.group(1)).strip()[:500] if jm else ""
    evt = section(4)[:400]
    if not evt:
        em = re.search(r"\n\s*Evt\s*(?:[.:]{2,}|:)\s*(.+?)(?:\n\s*[A-Z][a-z]+\s*(?:[.:]{2,}|:)|\n-{10,}|\Z)", txt, re.S)
        evt = re.sub(r"\s+", " ", em.group(1)).strip()[:400]
    trans = ""
    tm2 = re.search(r"<-\s*([^;\n]+)\s*;\s*->\s*([^\n]+)", txt)
    if tm2:
        trans = "%s->%s" % (tm2.group(1).strip(), tm2.group(2).strip())

    return {
        "id": aid, "clip": clip, "char": character, "cat": cat,
        "dur": round(dur, 3), "loop": bool(loop), "root": root,
        "blend_in": bin_, "blend_out": bout, "layer": layer[:3], "prio": prio,
        "family": anim_family(clip), "jeu": jeu, "evt": evt, "trans": trans,
    }


def build_anims(verbose=False):
    clips = []
    for path in sorted(glob.glob(os.path.join(RAW, "anim", "*", "A-*.txt"))):
        clips.append(parse_anim_file(path))
    clips.sort(key=lambda c: c["id"])
    by_char = collections.Counter(c["char"] for c in clips)
    size = write_json(os.path.join(OUT, "anims", "anims.json"), {
        "fps": 30, "face_fps": 60, "total": len(clips),
        "by_character": dict(by_char), "clips": clips,
    })
    if verbose:
        print("  anims     : %d clips (%d personnages), %.1f Ko"
              % (len(clips), len(by_char), size / 1024.0))
    return {"count": len(clips), "by_char": dict(by_char)}


# ---------------------------------------------------------------------------
# 4. ECHOS — les 31 (BLOC 11.04, table canonique)
# ---------------------------------------------------------------------------

ECHOS = [
    ("E01", "S1", "le muret aux bottes", "A", 50, True, "PN-001"),
    ("E02", "S1", "le registre dechire", "A", 70, True, "PN-008"),
    ("E03", "S1", "la valise triee", "A", 45, False, "PN-002"),
    ("E04", "S2", "la veste dans la cabine", "A", 160, True, "PN-031"),
    ("E05", "S2", "la barre du chalutier", "A", 65, True, "PN-032"),
    ("E06", "S2", "le tramway sous le verre", "C", 90, False, "PN-035"),
    ("E07", "S3", "l'anneau de relayeur n\u00b012", "A", 40, True, "PN-046"),
    ("E08", "S3", "le comptoir du forgeron", "A", 75, True, "PN-052"),
    ("E09", "S3", "la corde a linge bleue", "A", 55, False, "PN-055"),
    ("E10", "S3", "le poste de milice", "A", 110, True, "PN-058"),
    ("E11", "S3", "la sacoche volee par Sol", "A", 35, True, "PN-061"),
    ("E12", "S3", "le monte-charge", "A", 60, True, "PN-064"),
    ("E13", "S4", "le fichier vide", "A", 125, True, "PN-075"),
    ("E14", "S4", "la tasse de Mireille", "A", 80, False, "PN-078"),
    ("E15", "S4", "le vitrail brise", "A", 50, True, "PN-081"),
    ("E16", "S4", "le banc de la mezzanine", "B", 190, True, "PN-084"),
    ("E17", "S4", "l'echelle roulante", "A", 40, False, "PN-087"),
    ("E18", "S5", "la lanterne", "A", 45, True, "PN-096"),
    ("E19", "S5", "le matelas de Sol", "A", 85, True, "PN-099"),
    ("E20", "S5", "la grille du puits", "C", 100, True, "PN-102"),
    ("E21", "S6", "LA SALLE DE BAL", "B", 540, True, "PN-110"),
    ("E22", "S7", "le kiosque a musique", "A", 60, True, "PN-124"),
    ("E23", "S7", "la fille du Verrier", "C", 140, True, "PN-126"),
    ("E24", "S7", "le dossier rendu", "A", 95, True, "PN-119"),
    ("E25", "S7", "la lettre de Mireille", "A", 50, True, "PN-120"),
    ("E26", "S8", "la premiere marche", "A", 30, True, "PN-129"),
    ("E27", "S8", "le palier des cloches", "A", 105, True, "PN-131"),
    ("E28", "S8", "la corde de secours", "A", 40, False, "PN-133"),
    ("E29", "S8", "la couverture pliee", "A", 65, True, "PN-138"),
    ("E30", "S8", "le carnet sur la table", "A", 150, True, "PN-141"),
    ("E31", "S8", "LA LETTRE", "B", 250, True, "PN-143"),
]


def build_echos(verbose=False):
    # Cout en Souffle par mode (08.13 : -18 a -40 selon l'intensite)
    out = []
    for eid, seq, obj, mode, dur, mandatory, prop in ECHOS:
        cost = {"A": 18, "B": 26, "C": 40}[mode]
        if dur >= 150:
            cost += 6
        out.append({
            "id": eid, "seq": seq, "object": obj, "mode": mode,
            "duration_s": dur, "mandatory": mandatory, "prop": prop,
            "breath_cost": min(cost, 40),
            "ritual_s": 2.4,
            "skippable": False,
            "ghost_max": 17 if eid == "E01" else 12,
            "amber_rim": eid in ("E04", "E16", "E21", "E27", "E30", "E31"),
            "looks_at_player": eid == "E23",
        })
    size = write_json(os.path.join(OUT, "echos", "echos.json"), {
        "count": len(out), "echos": out,
        "rules": {
            "ritual_seconds": 2.4,
            "no_regeneration_during": True,
            "modes": {"A": "contemplatif", "B": "jouable", "C": "instable"},
            "ghosts_never_look": True,
            "exception_look": "E23",
            "esteban_voice_dry": True,
        },
    })
    if verbose:
        print("  echos     : %d echos (%d A / %d B / %d C), %.1f Ko"
              % (len(out), sum(1 for e in out if e["mode"] == "A"),
                 sum(1 for e in out if e["mode"] == "B"),
                 sum(1 for e in out if e["mode"] == "C"), size / 1024.0))
    return {"count": len(out)}


# ---------------------------------------------------------------------------
# 5. LA LETTRE (BLOC 20 — texte canonique, non reecrit)
# ---------------------------------------------------------------------------

def extract_letter(verbose=False):
    master = os.path.join(RAW, "LOHEN_PROMPT_MASTER.txt")
    txt = open(master, encoding="utf-8").read()
    m = re.search(r"20\.03\s+TEXTE INTEGRAL.*?\n\s*-{10,}\n(.*?)\n\s*-{10,}", txt, re.S)
    if not m:
        raise SystemExit("lettre introuvable dans le master")
    body = m.group(1)
    # decoupe en paragraphes / lignes de mise en scene
    blocks = []
    for para in re.split(r"\n\s*\n", body):
        para = para.strip("\n")
        if not para.strip():
            continue
        blocks.append(re.sub(r"[ \t]+$", "", para, flags=re.M).strip())
    # notes de mise en scene ligne par ligne (20.04)
    notes = []
    nm = re.search(r"20\.04\s+NOTES DE MISE EN SCENE(.*?)20\.05", txt, re.S)
    if nm:
        for line in nm.group(1).split("\n"):
            line = line.strip()
            if line.startswith("- \u00ab") or line.startswith("- «"):
                notes.append(line[2:].strip())
    payload = {
        "id": "LETTRE_ESTEBAN_0114",
        "canonical": True,
        "do_not_rewrite": True,
        "paper": {"w_cm": 21.0, "h_cm": 29.7, "folds": 3,
                  "stain_mm": 9.0, "stain_pos": "haut-gauche",
                  "crossed_out_words": 3, "outer_fold_text": "0114"},
        "blocks": blocks,
        "staging_notes": notes,
        "music": {
            "track": "M18", "duration_s": 250,
            "timeline": [
                {"t": 0, "what": "rien. Le vent. Le gr\u00e9sillement de la lampe."},
                {"t": 70, "what": "une seule note de violoncelle, tenue, qui ne r\u00e9sout pas (motif de Lohen)"},
                {"t": 125, "what": "le piano entre, trois notes du motif d'Esteban"},
                {"t": 180, "what": "UNE CLOCHE. Les cinq notes compl\u00e8tes : LA - DO - MI - RE - LA",
                 "sync_line": "Tu as les mains sales."},
                {"t": 180, "end": 250, "what": "tout ensemble, tr\u00e8s doux, voix sans paroles sur les 40 derni\u00e8res secondes"},
                {"t": 250, "what": "coupure nette. Silence. Le vent revient."},
            ],
        },
        "reading": {
            "player_paced": True, "voice_follows_scroll": True,
            "camera_cuts": 3, "third_cut_cries": True,
            "ritual_index": 31, "no_echo": True, "silence_after_hand_s": 3.5,
            "unfold_anim_s": 4.1, "tremble": {"mm": 2.0, "hz": 7.0, "from_second": 3.0},
        },
    }
    size = write_json(os.path.join(OUT, "letter", "lettre_esteban.json"), payload)
    if verbose:
        print("  lettre    : %d blocs, %d notes de mise en scene, %.1f Ko"
              % (len(blocks), len(notes), size / 1024.0))
    return {"blocks": len(blocks)}


# ---------------------------------------------------------------------------
# 6. TUNING (BLOC 08 — chiffres de design, data-driven, 04.06)
# ---------------------------------------------------------------------------

def build_tuning(verbose=False):
    tuning = {
        "movement": {
            "walk_speed": 1.4, "walk_slow": 0.8, "jog_speed": 3.2,
            "run_speed": 5.1, "sprint_speed": 6.4, "run_lean_deg": 14.0,
            "turn_in_place": [90.0, 90.0, 180.0],
            "stop_brake_s": 0.45, "slope_speed_factor": 0.86,
            "stair_speed_factor": 0.9, "accel_ground": 14.0, "accel_air": 3.5,
            "friction_ground": 11.0, "stumble_speed_threshold": 4.0,
            "slide_surface": "glass", "gravity": 9.81 * 1.6,
        },
        "air": {
            "jump_velocity": 5.4, "coyote_time_s": 0.14,
            "fall_short_loop_s": 1.8, "fall_flail_after_s": 3.5,
            "land_soft_m": 3.0, "land_medium_m": 7.0, "land_hard_m": 11.0,
            "land_hard_breath_cost": 8.0,
            "ledge_grab_window_s": 0.35, "ledge_capsule_widen_m": 0.20,
            "ledge_climb_fast_s": 0.7, "ledge_climb_exhausted_s": 1.3,
            "exhausted_below_breath_pct": 25.0,
            "wallrun_max_s": 2.1, "wallrun_breath_per_s": 8.0,
            "vault_low_s": 0.45, "air_correction_deg_s": 12.0,
            "platform_magnet_m": 0.35, "edge_stop_no_jump_ms": 300,
        },
        "grapple": {
            "range_m": 28.0, "projectile_speed": 62.0, "pull_speed": 14.0,
            "pull_accel_s": 0.3, "pendulum_gravity_factor": 1.15,
            "release_velocity_keep": 0.78, "cooldown_s": 0.45,
            "breath_per_shot": 4.0, "breath_per_swing_s": 1.5,
            "auto_aim_cone_deg": 40.0,
            "auto_aim_weights": {"camera_align": 0.5, "distance": 0.3, "progress": 0.2},
            "cable_segments": 32, "glass_refuses": True,
            "zip_ride_speed": 11.0, "zip_brake_decel": 6.5,
            "snap_scripted": ["S7_minute_3"],
        },
        "breath": {
            "max": 100.0, "max_end_of_chapter": 130.0, "upgrades": 3,
            "upgrade_amount": 10.0,
            "regen_per_s": 9.0, "regen_delay_s": 1.8,
            "regen_combat_per_s": 4.0, "regen_combat_delay_s": 3.0,
            "regen_during_echo": 0.0,
            "cost_sprint_per_s_after_3s": 6.0, "cost_jump": 0.0,
            "cost_grapple_shot": 4.0, "cost_swing_per_s": 1.5,
            "cost_wallrun_per_s": 8.0, "gain_parry_success": 6.0,
            "cost_parry_fail": 14.0, "cost_hit_light_taken": 12.0,
            "cost_hit_heavy_taken": 26.0, "cost_hard_landing": 8.0,
            "cost_echo_min": 18.0, "cost_echo_max": 40.0,
            "cost_refuse_echo": 0.0,
            "collapse_vulnerable_s": 3.5, "collapse_restore": 35.0,
            "glass_passive_drain_per_s": 1.0,
        },
        "combat": {
            "parry_window_s": 0.22, "parry_window_wide_s": 0.45,
            "parry_stagger_s": 1.4, "riposte_damage_factor": 3.0,
            "hitstop_light_ms": 70, "hitstop_heavy_ms": 130, "hitstop_parry_ms": 160,
            "shake_amp_deg": 0.6, "shake_freq_hz": 22.0, "shake_decay_s": 0.25,
            "dodge_s": 0.55, "dodge_iframes": 12,
            "heavy_charge_s": 1.1, "block_hold_max_s": 2.5,
            "combat_exit_s": 1.8, "combat_exit_lock_s": 0.6,
            "max_simultaneous": 4, "attack_token_above": 2,
            "encounters_total": 14, "avoidable": 9,
            "duration_min_s": 22, "duration_max_s": 70,
            "tell_echassier_s": 0.8, "tell_mueur_s": 0.5,
            "shatter_fragments": 47,
            "difficulty_levels": ["calme", "soutenu", "brutal"],
        },
        "boss_verrier": {
            "arena_diameter_m": 42.0, "altitude_m": 34.0,
            "phase1_range": [0.0, 0.35], "phase2_range": [0.35, 0.70],
            "phase3_range": [0.70, 1.0],
            "chase_max_m": 12.0,
            "p1_attacks": {"balayage": 1.1, "eclats": 0.9, "verticale": 1.4},
            "p1_riposte_window_s": 2.2,
            "p2_mueur_shields": 2,
            "p3_peaceful_stop_s": 4.0,
            "target_duration_fight_s": 200, "target_duration_peace_s": 70,
            "respawn_at_phase": True,
        },
        "camera": {
            "distance_m": 3.4, "target_height_m": 1.55, "shoulder_offset_m": 0.45,
            "touch_sensitivity_deg_per_dp": 0.22, "response_curve": 1.25,
            "smooth_pos_s": 0.12, "smooth_rot_s": 0.08,
            "spring_arm_radius_m": 0.32, "spring_arm_return_s": 0.15,
            "recenter_after_s": 2.5, "recenter_speed_deg_s": 45.0,
            "pitch_clamp": [-62.0, 58.0], "fov_speed_bonus_deg": 6.0,
            "fov_lerp_s": 0.8, "fov_explore_deg": 46.0, "fov_dialogue_deg": 34.0,
            "fov_fall_deg": 62.0, "horizon_rule_outdoor": 0.333,
            "horizon_rule_indoor": 0.667, "dof_far_start_m": 45.0,
            "dof_far_intensity": 0.6,
        },
        "input": {
            "joystick_radius_dp": 90.0, "joystick_deadzone_pct": 8.0,
            "joystick_curve": 1.4, "button_a_dp": 72.0, "button_b_dp": 64.0,
            "button_c_dp": 64.0, "button_d_dp": 64.0,
            "max_visible_buttons": 4, "sticky_press_dp": 20.0,
            "input_buffer_ms": 180, "coyote_ms": 140,
            "prompt_fade_s": 0.15, "edge_dead_zone_dp": 48.0,
            "min_touch_target_mm": 9.0,
            "no_qte": True,
        },
        "haptics": {
            "budget_per_second": 3,
            "patterns": {
                "hap_step_stone": [8], "hap_step_glass": [6, 4, 6],
                "hap_land_soft": [14], "hap_land_hard": [30],
                "hap_grapple_fire": [16], "hap_grapple_taut": [22],
                "hap_cable_strain": [6], "hap_parry": [11, 20, 11],
                "hap_hit_given_light": [18], "hap_hit_given_heavy": [14, 24, 14],
                "hap_hit_taken": [34], "hap_breath_break": [26, 30, 26, 30, 26],
                "hap_echo_start": [40], "hap_echo_end": [24],
                "hap_letter_open": [9, 18, 9, 18, 9],
                "hap_phare_sweep": [6],
            },
            "cable_strain_period_s": 0.4, "phare_sweep_period_s": 20.0,
        },
        "echo_system": {
            "ritual_s": 2.4, "reveal_speed_m_s": 3.5, "reveal_radius_m": 18.0,
            "desaturation": 0.85, "grain": 0.12, "vignette": 0.55,
            "history_frames": 6, "mode_c_breath_cost": 40.0,
            "sound_lowpass_hz": 5200.0, "sound_predelay_ms": 90.0,
            "esteban_voice_dry": True,
        },
        "post_process": {
            "order": ["bloom", "echo_reveal", "tonemap_aces", "lut_zone",
                      "vignette", "grain", "chromatic", "fsr_upscale", "sharpen", "ui"],
            "bloom_threshold": 1.15, "bloom_intensity": 0.28, "bloom_mips": 5,
            "grain": 0.035, "grain_fps": 24.0, "chromatic_px": 0.4,
            "chromatic_breath_break_px": 2.2, "vignette": 0.22,
            "sharpen": 0.22, "fsr_min": 0.55, "fsr_max": 1.0,
        },
        "palette": {
            "noir_verre": "#05070C", "bleu_noir": "#0C1420", "bleu_brume": "#16263A",
            "pierre_humide": "#4A423A", "pierre_seche": "#6E6254",
            "platre": "#8E7F6C", "calcaire": "#B9A88E", "bois_goudron": "#3B2A21",
            "brique": "#7A3F2C", "terre_cuite": "#A8552F",
            "lanterne": "#FFD6A0", "soleil_brume": "#FFF0D2", "ciel_ombre": "#C8E4FF",
            "ambre": "#FFA33C", "ambre_sature": "#FF7A18", "cyan_verre": "#6BF2D8",
            "speaker_label": "#B8A08A", "breath_low": "#A8442F",
            "jacket": "#3B3A2E",
        },
        "sequences": [
            {"id": "S1", "name": "LES QUAIS BAS", "alt": [0, 14], "minutes": 26,
             "rule": "marche, saut, echelles", "saturation": 0.35,
             "lut": "lut_gare", "objective": "Trouver le bureau du Registre des Disparus"},
            {"id": "S2", "name": "LE VERRE ET LE BATEAU", "alt": [0, 6], "minutes": 22,
             "rule": "le verre, pas de grappin", "saturation": 0.45,
             "lut": "lut_maree", "objective": "Atteindre L'Hirondelle de Mer"},
            {"id": "S3", "name": "LE MARCHE SUSPENDU", "alt": [18, 62], "minutes": 34,
             "rule": "grappin pendule, foule", "saturation": 0.55,
             "lut": "lut_marche", "objective": "Reparer le monte-charge (3 contrepoids)"},
            {"id": "S4", "name": "LA BIBLIOTHEQUE DES EAUX", "alt": [55, 78], "minutes": 28,
             "rule": "interieur, silence, Echos", "saturation": 0.40,
             "lut": "lut_bibliotheque", "objective": "Trouver le dossier d'Esteban"},
            {"id": "S5", "name": "LES CONDUITS", "alt": [40, 96], "minutes": 24,
             "rule": "etroit, Sol, furtivite", "saturation": 0.30,
             "lut": "lut_descente", "objective": "Traverser le ventre avec la lanterne"},
            {"id": "S6", "name": "LA SALLE DE BAL", "alt": [96, 118], "minutes": 18,
             "rule": "l'Echo le plus long", "saturation": 0.70,
             "lut": "lut_bal_souvenir", "objective": "Vivre le 14 octobre, 21 h 50"},
            {"id": "S7", "name": "LA DESCENTE", "alt": [118, 8], "minutes": 30,
             "rule": "sans grappin, le Verrier", "saturation": 0.20,
             "lut": "lut_verrier", "objective": "Redescendre a 8 m"},
            {"id": "S8", "name": "LA MONTEE AU PHARE", "alt": [8, 212], "minutes": 36,
             "rule": "tout, aucun ennemi", "saturation": 0.65,
             "lut": "lut_chambre_lettre", "objective": "Monter les 212 metres"},
        ],
        "world": {
            "lighthouse_altitude_m": 212.0, "lighthouse_built": 1871,
            "sweep_turns_per_s": 0.05, "sweep_period_s": 20.0,
            "beam_range_m": 900.0, "beam_exposure_ev": 0.4, "beam_exposure_s": 1.8,
            "maree_date": "14 octobre, 23 h 41", "maree_duration_s": 9,
            "maree_temperature_c": 41.0, "maree_depth_m": [30, 60],
            "maree_radius_km": 4.0, "missing_people": 641,
            "remaining_inhabitants": 400, "checkpoints": 47,
            "shortcuts": 14, "secrets": 31, "transition_corridors": 7,
            "lamp_rooms": ["L1 registre (S1)", "L2 cabine Hirondelle (S2)",
                           "L3 poste de Tallec (S3)", "L4 salle des fiches (S4)",
                           "L5 conduit du puits (S5)", "L6 chambre du Phare (S8)"],
            "stream_cell_m": [48, 48, 32], "stream_max_concurrent": 2,
            "stream_budget_ms": 4.0,
        },
        "audio": {
            "mix_hz": 48000, "buses": ["MASTER", "MUS", "SFX", "VO", "AMB", "DUCKING"],
            "loudness_lufs": -16.0, "true_peak_dbtp": -1.5,
            "duck_vo_music_db": -7.0, "duck_vo_sfx_db": -3.0,
            "duck_attack_ms": 40, "duck_release_ms": 350,
            "music_minutes_total": 47, "gameplay_minutes_total": 218,
            "motif_esteban": ["A3", "C4", "E4", "D4", "A3"],
            "motif_esteban_occurrences": 23, "motif_full_at": "lettre 3:00",
            "tracks": 22, "footstep_surfaces": 9, "footstep_variations": 8,
            "footstep_speeds": 3, "ambience_beds": 14, "wind_levels": 6,
            "reverb_impulses": ["eglise", "parking", "hangar", "cage_escalier",
                                "piece_vide", "tunnel", "falaise", "chambre_meublee",
                                "cabine_bateau"],
            "pause_music_db": -12.0,
            "speaker_preset_mid_boost_db": 4.0,
            "speaker_preset_range_hz": [300, 4000],
        },
        "carry": {
            "_ref": "08.11 V6 - PORTER / 09.14 S5 la lanterne",
            "speed_factor_lantern": 0.86,
            "speed_factor_chest": 0.62,
            "speed_factor_body": 0.55,
            "speed_factor_lever": 0.72,
            "speed_factor_dossier": 0.92,
            "speed_factor_letter": 1.0,
            "grapple_allowed": False,
            "guard_allowed": False,
            "parry_allowed": False,
            "sprint_allowed": False,
            "wallrun_allowed": False,
            "camera_distance_factor": 0.88,
            "camera_fov_delta": -2.0,
            "breath_profile": "effort",
            "breath_regen_factor": 0.8,
            "pickup_seconds": 0.9,
            "setdown_seconds": 0.7,
            "swap_hand_seconds": 0.45,
            "lantern_light_radius_m": 4.0,
            "lantern_color_k": 2700,
            "lantern_flicker": True,
            "mueur_repelled_at_m": 4.0,
            "mueur_attracted_in_dark": True,
            "dark_crossing_m": 8.0,
            "dark_crossings_s5": 3,
            "crouch_time_pct_s5": 0.6,
            "ceiling_height_s5_m": 1.4,
            "taught_at": "01:19",
        },
        "perf": {
            "tiers": {"LOW": {"fps": 30, "shadow_atlas": 1024, "cascades": 1,
                              "tris": 180000, "particles": 400, "fsr": 0.7,
                              "vram_mb": 720},
                      "MID": {"fps": 60, "shadow_atlas": 2048, "cascades": 2,
                              "tris": 310000, "particles": 900, "fsr": 0.85,
                              "vram_mb": 1300},
                      "HIGH": {"fps": 60, "shadow_atlas": 4096, "cascades": 3,
                               "tris": 480000, "particles": 900, "fsr": 1.0,
                               "vram_mb": 2100}},
            "draw_calls_max_mid": 420, "frametime_budget_ms": {
                "opaque": 5.8, "shadows": 2.1, "transparency_vfx": 2.4,
                "post": 2.2, "physics": 0.9, "script": 1.4, "anim_ik": 1.1,
                "audio": 0.3, "margin": 0.4},
            "thermal": {"MODERATE": {"fsr": -0.10, "cascades": -1},
                        "SEVERE": {"fps": 30, "volumetric": False, "vfx": 0.5},
                        "CRITICAL": {"fps": 30, "resolution": 0.55, "notify": True}},
            "thermal_smooth_s": 3.0,
            "battery_pct_per_hour_mid": 18.0,
            "launch_low_s": 6.5, "launch_high_s": 3.5,
            "zone_load_s": 2.5, "background_resume_s": 3.0,
        },
    }
    size = write_json(os.path.join(OUT, "tuning", "tuning.json"), tuning)
    if verbose:
        print("  tuning    : %d familles de reglages, %.1f Ko" % (len(tuning), size / 1024.0))
    return {"families": len(tuning)}


# ---------------------------------------------------------------------------
# 7. LOCALISATION (00.10 : FR complet, EN/ES/PT-BR/JA)
# ---------------------------------------------------------------------------

def build_locales(verbose=False):
    """Cles UI/systeme. Le recit reste en FR (langue de reference, 00.10)."""
    fr = {
        "app.title": "LOHEN",
        "app.subtitle": "Les Sept Lettres de Velmora",
        "app.chapter": "Chapitre 1 — La Ville qui retient son souffle",
        "menu.continue": "Continuer",
        "menu.new": "Nouvelle partie",
        "menu.load": "Charger",
        "menu.save": "Sauvegarder",
        "menu.options": "Options",
        "menu.journal": "Journal",
        "menu.credits": "G\u00e9n\u00e9rique",
        "menu.quit": "Quitter",
        "menu.resume": "Reprendre",
        "menu.autosave": "Sauvegarde automatique",
        "menu.slot": "Emplacement %d",
        "menu.empty": "vide",
        "menu.delete": "Effacer",
        "menu.export": "Exporter",
        "menu.import": "Importer",
        "opt.image": "IMAGE",
        "opt.sound": "SON",
        "opt.game": "JEU",
        "opt.controls": "CONTROLES",
        "opt.access": "ACCESSIBILIT\u00c9",
        "opt.data": "DONN\u00c9ES",
        "opt.quality": "Qualit\u00e9",
        "opt.quality.auto": "Auto",
        "opt.quality.low": "Bas",
        "opt.quality.mid": "Moyen",
        "opt.quality.high": "Haut",
        "opt.render_scale": "R\u00e9solution de rendu",
        "opt.fps": "FPS cible",
        "opt.shadows": "Ombres",
        "opt.fog": "Brouillard",
        "opt.effects": "Effets",
        "opt.grain": "Grain",
        "opt.chromatic": "Aberration",
        "opt.vignette": "Vignette",
        "opt.bloom": "Bloom",
        "opt.motion_blur": "Flou de mouvement",
        "opt.brightness": "Luminosit\u00e9",
        "opt.hdr": "HDR si support\u00e9",
        "opt.volumes": "Volumes",
        "opt.bus.master": "G\u00e9n\u00e9ral",
        "opt.bus.music": "Musique",
        "opt.bus.sfx": "Effets",
        "opt.bus.vo": "Voix",
        "opt.bus.amb": "Ambiances",
        "opt.output_preset": "Sortie",
        "opt.output.auto": "Auto",
        "opt.output.headphone": "Casque",
        "opt.output.speaker": "Haut-parleur",
        "opt.vo_language": "Langue des voix",
        "opt.subtitles": "Sous-titres",
        "opt.difficulty": "Difficult\u00e9 du combat",
        "opt.assist": "Assistance de travers\u00e9e",
        "opt.assist.normal": "Normale",
        "opt.assist.generous": "G\u00e9n\u00e9reuse",
        "opt.assist.auto": "Automatique",
        "opt.narration_only": "Mode narration seule",
        "opt.prompts": "Prompts",
        "opt.prompts.auto": "Auto",
        "opt.prompts.always": "Toujours",
        "opt.prompts.never": "Jamais",
        "opt.hud": "HUD",
        "opt.hud.full": "Complet",
        "opt.hud.minimal": "Minimal",
        "opt.hud.none": "Aucun",
        "opt.recenter": "Recentrage auto cam\u00e9ra",
        "opt.shake": "Secousse de cam\u00e9ra",
        "opt.button_size": "Taille des boutons",
        "opt.reposition": "Repositionner les boutons",
        "opt.tap_mode": "Tap au lieu de maintien",
        "opt.sensitivity_x": "Sensibilit\u00e9 cam\u00e9ra (X)",
        "opt.sensitivity_y": "Sensibilit\u00e9 cam\u00e9ra (Y)",
        "opt.invert_x": "Inverser l'axe X",
        "opt.invert_y": "Inverser l'axe Y",
        "opt.gamepad": "Manette",
        "opt.vibration": "Vibrations",
        "opt.subtitle_size": "Taille des sous-titres",
        "opt.subtitle_bg": "Fond opaque",
        "opt.subtitle_speaker": "Indicateur de locuteur",
        "opt.subtitle_extended": "Sous-titres \u00e9tendus (sons)",
        "opt.colorblind": "Daltonisme",
        "opt.cb.none": "Aucun",
        "opt.cb.protan": "Protanopie",
        "opt.cb.deutan": "Deut\u00e9ranopie",
        "opt.cb.tritan": "Tritanopie",
        "opt.remap_amber": "Couleur de l'ambre",
        "opt.remap_cyan": "Couleur du cyan",
        "opt.reduced_flash": "Flashs r\u00e9duits",
        "opt.wide_parry": "Fen\u00eatre de parade large",
        "opt.language": "Langue",
        "journal.letters": "LETTRES",
        "journal.echos": "\u00c9CHOS",
        "journal.people": "GENS",
        "journal.objects": "OBJETS",
        "journal.notes": "CARNET",
        "journal.letters_slots": "%d / 7",
        "hud.altitude": "%d m",
        "hud.breath": "Souffle",
        "prompt.echo": "Poser la main",
        "prompt.grapple": "Grappin",
        "prompt.climb": "Grimper",
        "prompt.jump": "Sauter",
        "prompt.guard": "Garde",
        "prompt.carry": "Porter",
        "prompt.give": "Donner",
        "prompt.push": "Pousser",
        "prompt.read": "Lire",
        "prompt.talk": "Parler",
        "prompt.sit": "S'asseoir",
        "prompt.hold": "Maintenir",
        "dialogue.continue": "Continuer",
        "cinematic.skip": "Maintenir pour passer",
        "chapter.title": "CHAPITRE 1 — LA VILLE QUI RETIENT SON SOUFFLE",
        "chapter.end": "FIN DU CHAPITRE 1",
        "chapter.next": "Il reste six lettres.",
        "thermal.warning": "Le t\u00e9l\u00e9phone chauffe — qualit\u00e9 r\u00e9duite",
        "crash.found": "Un rapport d'incident a \u00e9t\u00e9 \u00e9crit localement.",
        "crash.share": "Partager manuellement",
        "loading.tip": "",
        "access.narration_only.desc": "Les combats sont r\u00e9solus automatiquement et la travers\u00e9e est assist\u00e9e. Vous pouvez voir toute l'histoire.",
        "seq.S1": "S1 — Les Quais Bas",
        "seq.S2": "S2 — Le Verre et le Bateau",
        "seq.S3": "S3 — Le March\u00e9 Suspendu",
        "seq.S4": "S4 — La Biblioth\u00e8que des Eaux",
        "seq.S5": "S5 — Les Conduits",
        "seq.S6": "S6 — La Salle de Bal",
        "seq.S7": "S7 — La Descente",
        "seq.S8": "S8 — La Mont\u00e9e au Phare",
    }
    en = {
        "app.title": "LOHEN", "app.subtitle": "The Seven Letters of Velmora",
        "app.chapter": "Chapter 1 — The City Holding Its Breath",
        "menu.continue": "Continue", "menu.new": "New game", "menu.load": "Load",
        "menu.save": "Save", "menu.options": "Options", "menu.journal": "Journal",
        "menu.credits": "Credits", "menu.quit": "Quit", "menu.resume": "Resume",
        "menu.autosave": "Autosave", "menu.slot": "Slot %d", "menu.empty": "empty",
        "menu.delete": "Delete", "menu.export": "Export", "menu.import": "Import",
        "opt.image": "IMAGE", "opt.sound": "SOUND", "opt.game": "GAME",
        "opt.controls": "CONTROLS", "opt.access": "ACCESSIBILITY", "opt.data": "DATA",
        "opt.quality": "Quality", "opt.quality.auto": "Auto", "opt.quality.low": "Low",
        "opt.quality.mid": "Medium", "opt.quality.high": "High",
        "opt.render_scale": "Render scale", "opt.fps": "Target FPS",
        "opt.shadows": "Shadows", "opt.fog": "Fog", "opt.effects": "Effects",
        "opt.grain": "Grain", "opt.chromatic": "Chromatic aberration",
        "opt.vignette": "Vignette", "opt.bloom": "Bloom",
        "opt.motion_blur": "Motion blur", "opt.brightness": "Brightness",
        "opt.hdr": "HDR if supported", "opt.volumes": "Volumes",
        "opt.bus.master": "Master", "opt.bus.music": "Music", "opt.bus.sfx": "SFX",
        "opt.bus.vo": "Voice", "opt.bus.amb": "Ambience", "opt.output_preset": "Output",
        "opt.output.auto": "Auto", "opt.output.headphone": "Headphones",
        "opt.output.speaker": "Speaker", "opt.vo_language": "Voice language",
        "opt.subtitles": "Subtitles", "opt.difficulty": "Combat difficulty",
        "opt.assist": "Traversal assist", "opt.assist.normal": "Normal",
        "opt.assist.generous": "Generous", "opt.assist.auto": "Automatic",
        "opt.narration_only": "Story-only mode", "opt.prompts": "Prompts",
        "opt.prompts.auto": "Auto", "opt.prompts.always": "Always",
        "opt.prompts.never": "Never", "opt.hud": "HUD", "opt.hud.full": "Full",
        "opt.hud.minimal": "Minimal", "opt.hud.none": "None",
        "opt.recenter": "Auto camera recenter", "opt.shake": "Camera shake",
        "opt.button_size": "Button size", "opt.reposition": "Reposition buttons",
        "opt.tap_mode": "Tap instead of hold", "opt.sensitivity_x": "Camera sensitivity (X)",
        "opt.sensitivity_y": "Camera sensitivity (Y)", "opt.invert_x": "Invert X",
        "opt.invert_y": "Invert Y", "opt.gamepad": "Gamepad", "opt.vibration": "Vibration",
        "opt.subtitle_size": "Subtitle size", "opt.subtitle_bg": "Opaque background",
        "opt.subtitle_speaker": "Speaker name", "opt.subtitle_extended": "Extended subtitles (sounds)",
        "opt.colorblind": "Colour blindness", "opt.cb.none": "None",
        "opt.cb.protan": "Protanopia", "opt.cb.deutan": "Deuteranopia",
        "opt.cb.tritan": "Tritanopia", "opt.remap_amber": "Amber colour",
        "opt.remap_cyan": "Cyan colour", "opt.reduced_flash": "Reduced flashes",
        "opt.wide_parry": "Wide parry window", "opt.language": "Language",
        "journal.letters": "LETTERS", "journal.echos": "ECHOES",
        "journal.people": "PEOPLE", "journal.objects": "OBJECTS",
        "journal.notes": "NOTEBOOK", "journal.letters_slots": "%d / 7",
        "hud.altitude": "%d m", "hud.breath": "Breath",
        "prompt.echo": "Place your hand", "prompt.grapple": "Grapple",
        "prompt.climb": "Climb", "prompt.jump": "Jump", "prompt.guard": "Guard",
        "prompt.carry": "Carry", "prompt.give": "Hand over", "prompt.push": "Push",
        "prompt.read": "Read", "prompt.talk": "Talk", "prompt.sit": "Sit",
        "prompt.hold": "Hold", "dialogue.continue": "Continue",
        "cinematic.skip": "Hold to skip",
        "chapter.title": "CHAPTER 1 — THE CITY HOLDING ITS BREATH",
        "chapter.end": "END OF CHAPTER 1", "chapter.next": "Six letters remain.",
        "thermal.warning": "The phone is getting warm — quality reduced",
        "crash.found": "A crash report was written locally.",
        "crash.share": "Share manually",
        "access.narration_only.desc": "Combat resolves automatically and traversal is assisted. You can experience the whole story.",
        "seq.S1": "S1 — The Low Quays", "seq.S2": "S2 — The Glass and the Boat",
        "seq.S3": "S3 — The Hanging Market", "seq.S4": "S4 — The Library of Waters",
        "seq.S5": "S5 — The Ducts", "seq.S6": "S6 — The Ballroom",
        "seq.S7": "S7 — The Descent", "seq.S8": "S8 — The Climb to the Lighthouse",
    }
    es = dict(en)
    es.update({
        "app.subtitle": "Las Siete Cartas de Velmora",
        "app.chapter": "Cap\u00edtulo 1 — La ciudad que contiene el aliento",
        "menu.continue": "Continuar", "menu.new": "Nueva partida", "menu.load": "Cargar",
        "menu.save": "Guardar", "menu.options": "Opciones", "menu.journal": "Diario",
        "menu.credits": "Cr\u00e9ditos", "menu.quit": "Salir", "menu.resume": "Reanudar",
        "opt.image": "IMAGEN", "opt.sound": "SONIDO", "opt.game": "JUEGO",
        "opt.controls": "CONTROLES", "opt.access": "ACCESIBILIDAD", "opt.data": "DATOS",
        "opt.quality": "Calidad", "journal.letters": "CARTAS", "journal.echos": "ECOS",
        "journal.people": "GENTE", "journal.objects": "OBJETOS", "journal.notes": "CUADERNO",
        "prompt.echo": "Apoyar la mano", "prompt.jump": "Saltar", "prompt.guard": "Guardia",
        "chapter.end": "FIN DEL CAP\u00cdTULO 1", "chapter.next": "Quedan seis cartas.",
    })
    pt = dict(en)
    pt.update({
        "app.subtitle": "As Sete Cartas de Velmora",
        "app.chapter": "Cap\u00edtulo 1 — A cidade que prende a respira\u00e7\u00e3o",
        "menu.continue": "Continuar", "menu.new": "Novo jogo", "menu.load": "Carregar",
        "menu.save": "Salvar", "menu.options": "Op\u00e7\u00f5es", "menu.journal": "Di\u00e1rio",
        "menu.credits": "Cr\u00e9ditos", "menu.quit": "Sair", "menu.resume": "Retomar",
        "opt.image": "IMAGEM", "opt.sound": "\u00c1UDIO", "opt.game": "JOGO",
        "opt.controls": "CONTROLES", "opt.access": "ACESSIBILIDADE", "opt.data": "DADOS",
        "journal.letters": "CARTAS", "journal.echos": "ECOS", "journal.people": "PESSOAS",
        "journal.objects": "OBJETOS", "journal.notes": "CADERNO",
        "prompt.echo": "Pousar a m\u00e3o", "chapter.end": "FIM DO CAP\u00cdTULO 1",
        "chapter.next": "Restam seis cartas.",
    })
    ja = dict(en)
    ja.update({
        "app.subtitle": "\u30f4\u30a7\u30eb\u30e2\u30e9\u306e\u4e03\u901a\u306e\u624b\u7d19",
        "app.chapter": "\u7b2c1\u7ae0 \u2014 \u606f\u3092\u6bba\u3059\u8857",
        "menu.continue": "\u3064\u3065\u304d\u304b\u3089", "menu.new": "\u306f\u3058\u3081\u304b\u3089",
        "menu.load": "\u30ed\u30fc\u30c9", "menu.save": "\u30bb\u30fc\u30d6",
        "menu.options": "\u30aa\u30d7\u30b7\u30e7\u30f3", "menu.journal": "\u65e5\u8a18",
        "menu.credits": "\u30af\u30ec\u30b8\u30c3\u30c8", "menu.quit": "\u7d42\u4e86",
        "menu.resume": "\u3082\u3069\u308b", "opt.image": "\u753b\u50cf", "opt.sound": "\u97f3\u58f0",
        "opt.game": "\u30b2\u30fc\u30e0", "opt.controls": "\u64cd\u4f5c",
        "opt.access": "\u30a2\u30af\u30bb\u30b7\u30d3\u30ea\u30c6\u30a3",
        "opt.data": "\u30c7\u30fc\u30bf", "journal.letters": "\u624b\u7d19",
        "journal.echos": "\u30a8\u30b3\u30fc", "journal.people": "\u4eba\u3005",
        "journal.objects": "\u7269\u4ef6", "journal.notes": "\u30ce\u30fc\u30c8",
        "prompt.echo": "\u624b\u3092\u3042\u3066\u308b", "chapter.end": "\u7b2c1\u7ae0 \u7d42\u308f\u308a",
        "chapter.next": "\u3042\u3068\u516d\u901a\u3002",
    })
    payload = {
        "reference": "fr",
        "delivered": {"fr": "texte complet", "en": "texte UI complet",
                      "es": "texte UI complet", "pt-BR": "texte UI",
                      "ja": "texte UI"},
        "narrative_language": "fr",
        "locales": {"fr": fr, "en": en, "es": es, "pt-BR": pt, "ja": ja},
    }
    size = write_json(os.path.join(OUT, "locales", "locales.json"), payload)
    if verbose:
        print("  locales   : %d cles x %d langues, %.1f Ko"
              % (len(fr), len(payload["locales"]), size / 1024.0))
    return {"keys": len(fr), "langs": len(payload["locales"])}


# ---------------------------------------------------------------------------
# 8. QUETES / GRAPH NARRATIF (04.02 quest_graph, 09.03 objectifs)
# ---------------------------------------------------------------------------

def build_quests(verbose=False):
    graph = {
        "chapter": 1,
        "nodes": [
            {"id": "S1_arrival", "seq": "S1", "kind": "cinematic", "cine": "C01",
             "next": ["S1_walk"], "objective": None},
            {"id": "S1_walk", "seq": "S1", "kind": "beat", "verb": "walk",
             "objective": "obj.S1.registre", "next": ["S1_first_echo"],
             "teach": ["marcher", "camera"]},
            {"id": "S1_first_echo", "seq": "S1", "kind": "echo", "echo": "E01",
             "forced": True, "next": ["S1_jump_teach"], "teach": ["interagir"]},
            {"id": "S1_jump_teach", "seq": "S1", "kind": "beat", "verb": "jump",
             "prop": "PN-006", "next": ["S1_climb_teach"], "teach": ["sauter"]},
            {"id": "S1_climb_teach", "seq": "S1", "kind": "beat", "verb": "climb",
             "prop": "PN-021", "next": ["S1_pallas"], "teach": ["grimper"]},
            {"id": "S1_pallas", "seq": "S1", "kind": "dialogue", "scene": "S1_D02",
             "choices": ["S1_D05_C01", "S1_D05_C02"], "next": ["S1_roof_exit"]},
            {"id": "S1_roof_exit", "seq": "S1", "kind": "beat", "verb": "climb",
             "next": ["S1_to_S2"], "checkpoint": "CP-004"},
            {"id": "S1_to_S2", "seq": "S1", "kind": "corridor", "corridor": "T1",
             "next": ["S2_glass"]},

            {"id": "S2_glass", "seq": "S2", "kind": "beat", "rule": "glass_no_grapple",
             "objective": "obj.S2.hirondelle", "next": ["S2_grapple_refus"]},
            {"id": "S2_grapple_refus", "seq": "S2", "kind": "teach",
             "lesson": "le verre refuse le grappin", "duration_s": 12,
             "next": ["S2_heat_wells"]},
            {"id": "S2_heat_wells", "seq": "S2", "kind": "beat", "rule": "heat_wells",
             "next": ["S2_tramway"], "cinematic": "C02"},
            {"id": "S2_tramway", "seq": "S2", "kind": "spectacle", "echo": "E06",
             "optional": True, "next": ["S2_cabin"]},
            {"id": "S2_cabin", "seq": "S2", "kind": "echo", "echo": "E04",
             "major": True, "next": ["S2_escape"]},
            {"id": "S2_escape", "seq": "S2", "kind": "beat", "rule": "urgent_escape",
             "combat": "F01", "next": ["S2_to_S3"], "figure_watches": True},
            {"id": "S2_to_S3", "seq": "S2", "kind": "corridor", "corridor": "T2",
             "next": ["S3_arrival"]},

            {"id": "S3_arrival", "seq": "S3", "kind": "cinematic", "cine": "C03",
             "objective": "obj.S3.montechage", "next": ["S3_sol_chase"]},
            {"id": "S3_sol_chase", "seq": "S3", "kind": "beat", "rule": "chase_90s",
             "unwinnable": True, "next": ["S3_sol_dialogue"],
             "dialogue": "S3_D06"},
            {"id": "S3_sol_dialogue", "seq": "S3", "kind": "dialogue",
             "scene": "S3_D06", "next": ["S3_pendulum"]},
            {"id": "S3_pendulum", "seq": "S3", "kind": "beat", "rule": "pendulum_rings",
             "rings": 60, "next": ["S3_tallec"], "teach": ["grappin pendule"]},
            {"id": "S3_tallec", "seq": "S3", "kind": "dialogue", "scene": "S3_D03",
             "cinematic": "C04", "combat": "F02_parry_lesson",
             "next": ["S3_letters"], "reward": "ancre_harnais"},
            {"id": "S3_letters", "seq": "S3", "kind": "delivery", "letters": 4,
             "next": ["S3_counterweights"]},
            {"id": "S3_counterweights", "seq": "S3", "kind": "puzzle",
             "rule": "pull_three_counterweights", "next": ["S3_to_S4"]},
            {"id": "S3_to_S4", "seq": "S3", "kind": "corridor", "corridor": "T3",
             "next": ["S4_arrival"]},

            {"id": "S4_arrival", "seq": "S4", "kind": "beat", "music": "M08_empty",
             "objective": "obj.S4.dossier", "next": ["S4_search"]},
            {"id": "S4_search", "seq": "S4", "kind": "puzzle", "rule": "file_search_900",
             "clues": ["date", "quartier", "metier"], "next": ["S4_mireille_1"]},
            {"id": "S4_mireille_1", "seq": "S4", "kind": "dialogue", "scene": "S4_D04",
             "next": ["S4_empty_file"]},
            {"id": "S4_empty_file", "seq": "S4", "kind": "cinematic", "cine": "C05",
             "echo": "E13", "major": True, "next": ["S4_mireille_2"]},
            {"id": "S4_mireille_2", "seq": "S4", "kind": "dialogue", "scene": "S4_D07",
             "lie": True, "next": ["S4_to_S5"], "reward": "cle_mireille"},
            {"id": "S4_to_S5", "seq": "S4", "kind": "corridor", "corridor": "T4",
             "next": ["S5_arrival"]},

            {"id": "S5_arrival", "seq": "S5", "kind": "beat", "rule": "carry_lantern",
             "objective": "obj.S5.traversee", "next": ["S5_stealth"]},
            {"id": "S5_stealth", "seq": "S5", "kind": "beat", "rule": "light_repels_4m",
             "combat": "F05", "next": ["S5_dark_8m"]},
            {"id": "S5_dark_8m", "seq": "S5", "kind": "beat", "rule": "lantern_down_8m",
             "cinematic": "C06", "next": ["S5_sol_pomme"]},
            {"id": "S5_sol_pomme", "seq": "S5", "kind": "dialogue", "scene": "S5_D05",
             "music": "M11", "next": ["S5_to_S6"]},
            {"id": "S5_to_S6", "seq": "S5", "kind": "corridor", "corridor": "T5",
             "next": ["S6_arrival"]},

            {"id": "S6_arrival", "seq": "S6", "kind": "beat", "silence_s": 5.0,
             "objective": "obj.S6.bal", "next": ["S6_echo21"]},
            {"id": "S6_echo21", "seq": "S6", "kind": "echo_playable", "echo": "E21",
             "cinematic": "C07", "duration_s": 540, "next": ["S6_dance"]},
            {"id": "S6_dance", "seq": "S6", "kind": "minigame", "rule": "dance_4_press",
             "no_fail": True, "music": "M12", "next": ["S6_argument"]},
            {"id": "S6_argument", "seq": "S6", "kind": "dialogue", "scene": "S6_D03",
             "fake_choice": True, "next": ["S6_collapse"]},
            {"id": "S6_collapse", "seq": "S6", "kind": "cinematic", "cine": "C08",
             "vfx": "V25", "next": ["S6_to_S7"]},
            {"id": "S6_to_S7", "seq": "S6", "kind": "corridor", "corridor": "T6",
             "next": ["S7_arrival"]},

            {"id": "S7_arrival", "seq": "S7", "kind": "beat", "rule": "rain",
             "objective": "obj.S7.descendre", "next": ["S7_snap"]},
            {"id": "S7_snap", "seq": "S7", "kind": "scripted", "anim": "grapple_snap",
             "at_minute": 3, "next": ["S7_combats"]},
            {"id": "S7_combats", "seq": "S7", "kind": "combat_chain",
             "encounters": ["F08", "F09", "F10", "F11", "F12"], "next": ["S7_boss"]},
            {"id": "S7_boss", "seq": "S7", "kind": "boss", "boss": "verrier",
             "phases": 3, "peaceful_possible": True, "music": "M14",
             "next": ["S7_kneel"]},
            {"id": "S7_kneel", "seq": "S7", "kind": "cinematic", "cine": "C09",
             "echo": "E23", "next": ["S7_dossier"], "optional_echo": "E23bis"},
            {"id": "S7_dossier", "seq": "S7", "kind": "beat", "flag": "dossier_recu",
             "choice_mercy": True, "next": ["S7_to_S8"]},
            {"id": "S7_to_S8", "seq": "S7", "kind": "corridor", "corridor": "T7",
             "next": ["S8_sol_repairs"]},

            {"id": "S8_sol_repairs", "seq": "S8", "kind": "dialogue", "scene": "S8_D02",
             "duration_s": 90, "reward": "grapple_repaired", "next": ["S8_climb"]},
            {"id": "S8_climb", "seq": "S8", "kind": "beat", "rule": "all_verbs",
             "music": "M16", "altitude_tiers": 6, "objective": "obj.S8.monter",
             "next": ["S8_fog_below"]},
            {"id": "S8_fog_below", "seq": "S8", "kind": "scripted", "cine": "C10",
             "at_altitude": 150, "player_keeps_control": True, "next": ["S8_landing"]},
            {"id": "S8_landing", "seq": "S8", "kind": "beat", "at_altitude": 206,
             "interaction": "push_door_2_2s", "next": ["S8_room"]},
            {"id": "S8_room", "seq": "S8", "kind": "explore", "discoveries": 7,
             "echo_optional": "E30", "next": ["S8_letter"]},
            {"id": "S8_letter", "seq": "S8", "kind": "letter", "cine": "C11",
             "echo": "E31", "no_echo": True, "music": "M18", "next": ["S8_fold"]},
            {"id": "S8_fold", "seq": "S8", "kind": "beat", "anim": "fold_and_store",
             "next": ["S8_descent"]},
            {"id": "S8_descent", "seq": "S8", "kind": "beat", "duration_s": 90,
             "silent": True, "next": ["S8_sol_sleeps"]},
            {"id": "S8_sol_sleeps", "seq": "S8", "kind": "ending",
             "music": "M19", "next": ["CREDITS"]},
            {"id": "CREDITS", "seq": "S8", "kind": "credits", "music": "M20",
             "unskippable_s": 60, "next": []},
        ],
        "flags_chapter2": ["dossier_recu", "verrier_acheve", "verrier_epargne",
                           "e23bis_lu", "objets_figures_11"],
        "seven_letters": {"slots": 7, "filled_chapter1": 1,
                          "deliverable_chapter1": 6},
    }
    size = write_json(os.path.join(OUT, "quests", "quest_graph.json"), graph)
    if verbose:
        print("  quetes    : %d noeuds narratifs, %.1f Ko" % (len(graph["nodes"]), size / 1024.0))
    return {"nodes": len(graph["nodes"])}


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args()
    print("LOHEN · pipeline de contenu")
    stats = {}
    stats["dialogues"] = build_dialogues(args.verbose)
    stats["props"] = build_props(args.verbose)
    stats["anims"] = build_anims(args.verbose)
    stats["echos"] = build_echos(args.verbose)
    stats["letter"] = extract_letter(args.verbose)
    stats["tuning"] = build_tuning(args.verbose)
    stats["locales"] = build_locales(args.verbose)
    stats["quests"] = build_quests(args.verbose)
    stats["choices"] = build_choices(args.verbose)
    write_json(os.path.join(OUT, "manifest.json"), {
        "project": "LOHEN — Les Sept Lettres de Velmora",
        "chapter": 1, "pipeline_version": "1.0", "stats": stats,
    })
    print("OK — contenu genere dans content/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
