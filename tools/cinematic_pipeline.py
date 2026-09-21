#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LOHEN — tools/cinematic_pipeline.py

Genere les 11 timelines de cinematique (`content/cinematics/c01.json` …
`c11.json`) au format lu par `sim/narrative/CinematicPlayer.java`.

BLOC 15 — CINEMATIQUES : LES 11 SEQUENCES, PLAN PAR PLAN.
07.21 : 11 cinematiques, toutes en TEMPS REEL dans le moteur (pas de video
        prerendue). Pistes camera (splines + FOV + DOF), acteur (anim + IK),
        audio, post-process, sous-titres.
07.22 [OBL] : skippable apres 1,5 s d'appui long (jamais sur tap simple),
        fondu de 0,4 s, application de l'etat final. Exception : la
        cinematique de la lettre n'est skippable qu'apres 20 s.
07.23 [ART] REGLE ANTI-MOLLESSE : chaque plan doit contenir un mouvement
        dans le cadre. Un plan fixe avec deux personnages immobiles qui
        parlent est INTERDIT. S'il n'y a rien a bouger, on bouge la camera
        de 2 cm par seconde.
15.01 : barres noires 2.39:1 qui arrivent en 0,5 s ; aucune cinematique ne
        depasse 3 min sauf C11.

Les sous-titres ne sont pas inventes ici : ils sont tires des scenes de
dialogue canoniques deja generees par `tools/content_pipeline.py`
(12.10 voix off, 12.11 Tallec, 12.16 le Verrier, BLOC 20 la lettre).

Usage :  python3 tools/cinematic_pipeline.py [--verbose]
"""

from __future__ import annotations

import argparse
import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENT = os.path.join(ROOT, "content")
OUT = os.path.join(CONTENT, "cinematics")
DIALOGUES = os.path.join(CONTENT, "dialogues")

ASPECT = 2.39             # 15.01 : letterbox 2.39:1
LETTERBOX_IN = 0.5        # les barres arrivent en 0,5 s
SKIP_HOLD = 1.5           # 07.22
SKIP_HOLD_LETTER = 20.0   # 07.22 : la lettre, assumée
FADE_OUT = 0.4            # 07.22 : fondu de 0,4 s
MAX_MINUTES = 3.0         # 15.01 : aucune ne depasse 3 min sauf C11
DRIFT_CM_PER_S = 2.0      # 07.23 : la camera bouge de 2 cm/s si rien ne bouge

# palettes / post-process (05.08 saturation par sequence)
LUTS = {
    "S1": "lut_quais", "S2": "lut_verre", "S3": "lut_marche", "S4": "lut_bibliotheque",
    "S5": "lut_conduits", "S6": "lut_bal_froid", "S7": "lut_pluie", "S8": "lut_aube",
}
SATURATION = {"S1": 0.30, "S2": 0.28, "S3": 0.55, "S4": 0.34, "S5": 0.24,
              "S6": 0.42, "S7": 0.26, "S8": 0.40}
SATURATION_GOLD = 0.70     # 09.15 : le pic absolu, dans l'Echo de la salle

# 07.23 : un geste court ne laisse jamais l'acteur fige — il enchainne sur
# une respiration bouclee (clip reel du manifeste des 744).
HOLD = {
    "lohen": "A-001",      # idle_neutral 12,0 s
    "esteban": "A-229",    # idle_night_anxious 13,0 s
    "tallec": "A-379",     # idle_sitting_askew 14,0 s
    "verrier": "A-581",    # idle_standing_burdened 14,0 s
    "sol": "A-443",        # idle_sleeping_against_door 20,0 s
    "mireille": "A-316",   # idle_sitting_desk 13,0 s
}

AMBIENCE = {"S1": "port_mort", "S2": "maree_verre", "S3": "marche_suspendu",
            "S4": "bibliotheque_eaux", "S5": "conduits", "S6": "salle_de_bal",
            "S7": "pluie_battante", "S8": "aube_verre"}


def r2(v):
    return round(float(v), 3)


def drift(points, seconds):
    """07.23 : si rien ne bouge dans le cadre, la camera derive de 2 cm/s."""
    if not points:
        return points
    out = [list(p) for p in points]
    total = DRIFT_CM_PER_S * seconds / 100.0
    for i, p in enumerate(out):
        t = i / float(max(1, len(out) - 1))
        p[0] += total * t
        p[1] += total * 0.35 * t
    return [tuple(r2(v) for v in p) for p in out]


class Shot(object):
    def __init__(self, sid, t0, t1, path, look, fov=46.0, fov_end=None, dof=45.0,
                 dof_end=None, shake=0.0, who="", anim="", speed=1.0, ik="",
                 music="", ambience="", fade=1.0, sfx=(), lut="", saturation=None,
                 exposure=1.0, vignette=0.20, subs=(), hands_control=False,
                 moving=True, note="", hold=None):
        self.id = sid
        self.t0 = float(t0)
        self.t1 = float(t1)
        self.path = [tuple(r2(v) for v in p) for p in path]
        self.look = [tuple(r2(v) for v in p) for p in look]
        self.fov = float(fov)
        self.fov_end = float(fov if fov_end is None else fov_end)
        self.dof = float(dof)
        self.dof_end = float(dof if dof_end is None else dof_end)
        self.shake = float(shake)
        self.who = who
        self.anim = anim
        self.speed = float(speed)
        self.ik = ik
        self.music = music
        self.ambience = ambience
        self.fade = float(fade)
        self.sfx = list(sfx)
        self.lut = lut
        self.saturation = saturation
        self.exposure = float(exposure)
        self.vignette = float(vignette)
        self.subs = list(subs)
        self.hands_control = bool(hands_control)
        self.moving = bool(moving)
        self.note = note
        self.hold = hold

    @property
    def duration(self):
        return self.t1 - self.t0

    def to_json(self, seq):
        sat = self.saturation if self.saturation is not None else SATURATION[seq]
        # 07.23 : la regle anti-mollesse. Un plan sans acteur anime doit avoir
        # une camera qui bouge ; si l'auteur ne l'a pas prevu, on derive.
        path = self.path
        if not self.moving and not self.anim:
            path = drift(path, self.duration)
        doc = {
            "id": self.id,
            "t0": r2(self.t0),
            "t1": r2(self.t1),
            "camera": {
                "path": [list(p) for p in path],
                "look": [list(p) for p in self.look],
                "fov": r2(self.fov),
                "fov_end": r2(self.fov_end),
                "dof": r2(self.dof),
                "dof_end": r2(self.dof_end),
                "shake": r2(self.shake),
            },
            "post": {
                "lut": self.lut or LUTS[seq],
                "saturation": r2(sat),
                "exposure": r2(self.exposure),
                "vignette": r2(self.vignette),
            },
            "hands_control": self.hands_control,
        }
        if self.note:
            doc["note"] = self.note
        if self.who or self.anim:
            doc["actor"] = {"who": self.who, "anim": self.anim, "speed": r2(self.speed)}
            if self.ik:
                doc["actor"]["ik"] = self.ik
            hold = self.hold if self.hold is not None else HOLD.get(self.who, "")
            if hold:
                doc["actor"]["hold"] = hold
        if self.music or self.ambience or self.sfx:
            doc["audio"] = {"music": self.music, "ambience": self.ambience,
                            "fade": r2(self.fade)}
            if self.sfx:
                doc["audio"]["sfx"] = [{"t": r2(t), "id": i} for t, i in self.sfx]
        if self.subs:
            doc["subtitles"] = [{"t": r2(self.t0 + off), "speaker": spk, "line": line}
                                for off, spk, line in self.subs]
        return doc


def load_scene(scene_id):
    """Charge une scene de dialogue canonique pour ses sous-titres."""
    seq = scene_id.split("_")[0].lower()
    path = os.path.join(DIALOGUES, "dialogues_%s.json" % seq)
    if not os.path.exists(path):
        return []
    with open(path, "r", encoding="utf-8") as f:
        doc = json.load(f)
    for s in doc.get("scenes", []):
        if s.get("id") == scene_id:
            return s.get("lines", [])
    return []


def spread_subtitles(lines, shots, max_lines=None):
    """
    Repartit les lignes d'une scene sur les plans, au prorata de leur duree.
    Aucune ligne n'est reecrite : ce sont les lignes canoniques de l'annexe.
    """
    if not lines:
        return
    total = sum(max(0.4, s.duration) for s in shots)
    if max_lines:
        lines = lines[:max_lines]
    idx = 0
    acc = 0.0
    for s in shots:
        share = max(0.4, s.duration) / total
        budget = int(round(len(lines) * share))
        if budget <= 0:
            continue
        taken = lines[idx:idx + budget]
        idx += len(taken)
        if not taken:
            break
        step = s.duration / float(len(taken) + 1)
        for i, ln in enumerate(taken):
            spk = ln.get("spk_label") or ln.get("spk") or ""
            s.subs.append((step * (i + 1), spk, ln.get("fr", "")))
    # les lignes restantes vont sur le dernier plan
    if idx < len(lines):
        last = shots[-1]
        step = max(0.5, last.duration / float(len(lines) - idx + 1))
        for i, ln in enumerate(lines[idx:]):
            spk = ln.get("spk_label") or ln.get("spk") or ""
            last.subs.append((step * (i + 1), spk, ln.get("fr", "")))


class Cinematic(object):
    def __init__(self, cid, title, seq, shots, skip_hold=SKIP_HOLD,
                 hands_control_at=-1.0, player_keeps_control=False,
                 letterbox=True, subtitle_scene=None, note=""):
        self.id = cid
        self.title = title
        self.seq = seq
        self.shots = shots
        self.skip_hold = skip_hold
        self.hands_control_at = hands_control_at
        self.player_keeps_control = player_keeps_control
        self.letterbox = letterbox
        self.note = note
        if subtitle_scene:
            spread_subtitles(load_scene(subtitle_scene), shots)

    @property
    def duration(self):
        return max(s.t1 for s in self.shots) if self.shots else 0.0

    def to_json(self):
        return {
            "id": self.id,
            "title": self.title,
            "seq": self.seq,
            "duration": r2(self.duration),
            "skippable_after": r2(self.skip_hold),
            "hands_control_at": r2(self.hands_control_at),
            "letterbox": self.letterbox,
            "letterbox_in_s": LETTERBOX_IN,
            "aspect": ASPECT,
            "fade_out_s": FADE_OUT,
            "player_keeps_control": self.player_keeps_control,
            "note": self.note,
            "shots": [s.to_json(self.seq) for s in self.shots],
        }


# ---------------------------------------------------------------------------
# C01 « ARRIVEE » — 1:40 — ouverture du jeu (15.02)
# ---------------------------------------------------------------------------

def c01():
    seq = "S1"
    shots = [
        # Plan 1 (8 s) : noir. Le son d'une rame qui casse la surface. Puis rien.
        Shot("C01_P1", 0.0, 8.0, [(0.0, 1.2, 60.0), (0.0, 1.2, 59.6)],
             [(0.0, 0.4, 40.0)], fov=46, dof=8.0, exposure=0.06, vignette=0.85,
             ambience=AMBIENCE[seq], sfx=[(1.2, "oar_against_glass"),
                                         (3.4, "water_drip_single")],
             saturation=0.12, moving=True),
        # Plan 2 (14 s) : plan large sur la Maree depuis l'eau. Une barque.
        # Lohen rame. Il ne regarde pas devant. Il regarde une enveloppe.
        Shot("C01_P2", 8.0, 22.0, [(-9.0, 1.1, 74.0), (-4.0, 1.0, 62.0),
                                   (0.0, 0.9, 54.0)],
             [(0.0, 1.1, 46.0), (0.4, 1.0, 44.0)], fov=40, dof=120.0,
             who="lohen", anim="A-204", speed=1.0, ambience=AMBIENCE[seq],
             sfx=[(9.0, "oar_stroke_1"), (12.5, "oar_stroke_2"),
                  (16.0, "oar_stroke_3")], saturation=0.22),
        # Plan 3 (6 s) : gros plan sur l'enveloppe. « 0114 » de la main de Lohen.
        Shot("C01_P3", 22.0, 28.0, [(0.35, 1.28, 47.4), (0.30, 1.24, 47.1)],
             [(0.30, 1.20, 46.9)], fov=28, dof=1.2, dof_end=0.8,
             ambience=AMBIENCE[seq], saturation=0.26, vignette=0.35,
             moving=False),
        # Plan 4 (11 s) : la rame heurte le verre. Le son est celui de 13.29.
        Shot("C01_P4", 28.0, 39.0, [(1.6, 1.4, 50.0), (0.6, 1.0, 47.0)],
             [(0.0, 0.5, 45.0)], fov=44, shake=0.35, ambience=AMBIENCE[seq],
             sfx=[(30.0, "glass_signature_13_29"), (31.2, "glass_hum_low"),
                  (34.0, "oar_scrape_glass")], saturation=0.24),
        # Plan 5 (9 s) : contre-plongee. Velmora immense, verticale, le Phare
        # qui balaye. PREMIERE VUE DU PHARE. M01 entre a cet instant.
        Shot("C01_P5", 39.0, 48.0, [(0.0, 0.8, 52.0), (0.0, 1.6, 51.0)],
             [(0.0, 60.0, -200.0), (0.0, 190.0, -430.0)], fov=52, fov_end=46,
             dof=200.0, music="M01", fade=1.2, ambience=AMBIENCE[seq],
             saturation=0.30, sfx=[(39.0, "lighthouse_bell_distant")]),
        # Plan 6 (22 s) : il descend, pose un pied sur le verre, teste, appuie,
        # pose le second pied. Il attache la barque. Le noeud raconte tout.
        Shot("C01_P6", 48.0, 70.0, [(3.2, 2.0, 50.0), (2.0, 1.3, 47.5),
                                    (1.2, 1.0, 46.2)],
             [(0.6, 0.6, 45.4), (0.4, 0.3, 45.0)], fov=42, dof=24.0, dof_end=8.0,
             who="lohen", anim="A-205", speed=1.0, music="M01", fade=0.8,
             ambience=AMBIENCE[seq],
             sfx=[(52.0, "boot_on_glass_test"), (58.0, "boot_weight_press"),
                  (63.0, "rope_knot_tie"), (67.0, "cleat_metal_tap")],
             saturation=0.30),
        # Plan 7 (16 s) : il marche vers le ponton. La camera s'abaisse et
        # passe en camera de jeu SANS COUPURE. LE JOUEUR A LE CONTROLE.
        Shot("C01_P7", 70.0, 86.0, [(0.0, 2.4, 44.0), (0.0, 1.9, 41.0),
                                    (0.0, 1.62, 39.4)],
             [(0.0, 1.4, 34.0), (0.0, 1.2, 26.0)], fov=46, dof=45.0,
             who="lohen", anim="A-030", speed=1.0, music="M01", fade=0.6,
             ambience=AMBIENCE[seq], saturation=0.30, hands_control=True),
        # Puis la voix off 12.10 par-dessus les premiers pas (86 -> 100 s).
        Shot("C01_P8", 86.0, 100.0, [(0.0, 1.62, 39.4), (0.0, 1.60, 34.0)],
             [(0.0, 1.2, 20.0)], fov=46, dof=45.0, who="lohen", anim="A-030",
             music="M01", fade=0.4, ambience=AMBIENCE[seq], saturation=0.30,
             hands_control=True, moving=False),
    ]
    c = Cinematic("C01", "ARRIVEE", seq, shots, hands_control_at=86.0,
                  subtitle_scene="S1_D01",
                  note="15.02 · 1:40 · le joueur reprend le controle sans coupure "
                       "au plan 7, et la voix off 12.10 couvre les premiers pas")
    return c


# ---------------------------------------------------------------------------
# C02 « CE QUI EST DESSOUS » — 1:05 — un seul plan (15.03)
# ---------------------------------------------------------------------------

def c02():
    seq = "S2"
    shots = [
        Shot("C02_P1", 0.0, 65.0,
             [(0.0, 1.62, 96.0), (0.0, 0.9, 92.0), (0.0, 0.32, 86.0),
              (0.0, -1.2, 74.0), (0.0, -3.0, 60.0), (0.0, -3.2, 44.0),
              (0.0, -1.0, 30.0), (0.0, 0.32, 20.0), (0.0, 1.3, 12.0)],
             [(0.0, 0.3, 80.0), (0.0, -0.4, 70.0), (18.0, -3.0, -34.0),
              (14.0, -2.6, -34.0), (10.0, -2.4, -33.0), (0.0, 0.6, -10.0),
              (0.0, 1.0, -30.0)],
             fov=46, fov_end=38, dof=45.0, dof_end=12.0, exposure=0.55,
             ambience="maree_verre", saturation=0.20,
             sfx=[(4.0, "glass_hum_low"), (18.0, "glass_hum_low"),
                  (30.0, "underwater_muffle"), (44.0, "glass_hum_low")],
             moving=True,
             note="aucun dialogue, aucun son sauf le bourdonnement grave du verre"),
    ]
    return Cinematic("C02", "CE QUI EST DESSOUS", seq, shots,
                     note="15.03 · 1:05 · un seul plan : la camera descend, passe "
                          "SOUS la surface, revele le tramway et les silhouettes "
                          "assises, remonte. AUCUN DIALOGUE.")


# ---------------------------------------------------------------------------
# C03 « LE MARCHE » — 1:25 — plan-sequence (15.04)
# ---------------------------------------------------------------------------

def c03():
    seq = "S3"
    path = [(0.0, 19.6, 100.0), (6.0, 22.6, 82.0), (-4.0, 25.6, 66.0),
            (6.0, 29.1, 50.0), (-6.0, 32.6, 34.0), (4.0, 36.1, 18.0),
            (-2.0, 39.6, 2.0), (6.0, 42.6, -14.0)]
    look = [(4.0, 20.0, 84.0), (-2.0, 24.0, 68.0), (4.0, 27.0, 52.0),
            (-4.0, 31.0, 36.0), (2.0, 34.0, 20.0), (-2.0, 38.0, 4.0),
            (4.0, 41.0, -12.0), (0.0, 44.0, -30.0)]
    shots = []
    n = 5
    for i in range(n):
        t0 = i * 17.0
        t1 = 85.0 if i == n - 1 else (i + 1) * 17.0
        # le bruit monte de -40 dB a 0 dB sur 40 s : on le note en fade
        fade = 1.0 if i == 0 else 0.6
        shots.append(Shot("C03_P%d" % (i + 1), t0, t1,
                          path[i:i + 3] if i + 3 <= len(path) else path[-3:],
                          look[i:i + 3] if i + 3 <= len(look) else look[-3:],
                          fov=48, fov_end=44, dof=60.0, dof_end=30.0,
                          who="lohen", anim="A-031", speed=1.05,
                          music="M04" if i >= 2 else "", fade=fade,
                          ambience=AMBIENCE[seq], saturation=0.55,
                          sfx=[(t0 + 3.0, "market_crowd_wall_%d" % (i + 1)),
                               (t0 + 9.0, "winch_creak"),
                               (t0 + 13.0, "child_running")]))
    return Cinematic("C03", "LE MARCHE", seq, shots, subtitle_scene="S3_D01",
                     note="15.04 · 1:25 · plan-sequence de 85 s, 22 PNJ avec "
                          "chacun une action, le bruit monte de -40 dB a 0 dB "
                          "sur 40 s. Le plan le plus cher du chapitre.")


# ---------------------------------------------------------------------------
# C04 « TALLEC » — 0:50 — l'ancre posee sur la table (15.05, 12.11)
# ---------------------------------------------------------------------------

def c04():
    seq = "S3"
    shots = [
        Shot("C04_P1", 0.0, 12.0, [(-16.0, 41.6, -57.0), (-16.6, 41.4, -58.2)],
             [(-18.0, 41.0, -60.0)], fov=38, dof=12.0, who="tallec", anim="A-402",
             ambience=AMBIENCE[seq], saturation=0.40, moving=False),
        Shot("C04_P2", 12.0, 24.0, [(-17.4, 41.2, -59.0), (-17.9, 41.0, -59.6)],
             [(-18.0, 40.9, -60.2)], fov=30, dof=4.0, dof_end=1.6,
             ambience=AMBIENCE[seq], saturation=0.40,
             sfx=[(13.0, "anchor_on_table"), (18.0, "paper_slide")]),
        Shot("C04_P3", 24.0, 38.0, [(-18.6, 41.3, -58.4), (-18.2, 41.5, -59.2)],
             [(-18.0, 41.0, -60.0)], fov=42, dof=8.0, who="tallec", anim="A-404",
             ambience=AMBIENCE[seq], saturation=0.40),
        Shot("C04_P4", 38.0, 50.0, [(-18.0, 41.6, -57.6), (-17.4, 41.4, -56.8)],
             [(-16.0, 41.0, -55.0)], fov=46, dof=20.0, who="lohen", anim="A-187",
             ambience=AMBIENCE[seq], saturation=0.40, hands_control=True),
    ]
    return Cinematic("C04", "TALLEC", seq, shots, hands_control_at=48.0,
                     subtitle_scene="S3_D04",
                     note="15.05 · 0:50 · l'ancre posee sur la table (12.11)")


# ---------------------------------------------------------------------------
# C05 « LE FICHIER VIDE » — 1:15 (15.06)
# ---------------------------------------------------------------------------

def c05():
    seq = "S4"
    shots = [
        Shot("C05_P1", 0.0, 14.0, [(2.0, 63.4, 12.0), (1.2, 63.0, 13.4)],
             [(0.0, 62.8, 14.6)], fov=40, dof=10.0, who="lohen", anim="A-184",
             ambience=AMBIENCE[seq], saturation=0.34,
             sfx=[(2.0, "drawer_slide_wood"), (9.0, "paper_bundle_lift")]),
        Shot("C05_P2", 14.0, 30.0, [(0.6, 63.0, 14.0), (0.35, 62.9, 14.35)],
             [(0.2, 62.75, 14.6)], fov=28, dof=2.4, dof_end=1.0,
             who="lohen", anim="A-206", speed=1.0, ambience=AMBIENCE[seq],
             saturation=0.34, sfx=[(16.0, "cardboard_folder_open")]),
        # gros plan sur la poussiere : un rectangle propre
        Shot("C05_P3", 30.0, 44.0, [(0.24, 62.82, 14.52), (0.20, 62.80, 14.56)],
             [(0.20, 62.78, 14.60)], fov=24, dof=0.9, exposure=1.15,
             ambience=AMBIENCE[seq], saturation=0.30, vignette=0.4,
             moving=False, sfx=[(33.0, "dust_settle")]),
        Shot("C05_P4", 44.0, 60.0, [(0.5, 62.95, 14.2), (0.9, 63.0, 13.8),
                                    (0.5, 62.95, 14.2)],
             [(0.2, 62.8, 14.6)], fov=32, dof=3.0, who="lohen", anim="A-206",
             speed=0.92, ambience=AMBIENCE[seq], saturation=0.34,
             sfx=[(45.0, "folder_close"), (50.0, "folder_open_again"),
                  (55.0, "folder_close_again")]),
        Shot("C05_P5", 60.0, 75.0, [(0.9, 63.0, 13.6), (1.6, 63.2, 12.4)],
             [(0.0, 62.9, 14.4)], fov=44, dof=14.0, who="lohen", anim="A-206",
             speed=0.85, ambience=AMBIENCE[seq], saturation=0.34,
             sfx=[(62.0, "folder_open_third_time"), (70.0, "breath_shaky")],
             hands_control=True),
    ]
    return Cinematic("C05", "LE FICHIER VIDE", seq, shots, hands_control_at=72.0,
                     subtitle_scene="S4_D04",
                     note="15.06 · 1:15 · il repose la chemise, la rouvre, la "
                          "referme, la rouvre une troisieme fois. C'est tout ce "
                          "qu'on voit du chagrin.")


# ---------------------------------------------------------------------------
# C06 « LE NOIR » — 0:45 (15.07)
# ---------------------------------------------------------------------------

def c06():
    seq = "S5"
    shots = [
        Shot("C06_P1", 0.0, 8.0, [(0.0, 41.4, 60.0), (0.0, 41.2, 58.4)],
             [(0.0, 41.0, 54.0)], fov=44, dof=8.0, exposure=0.42,
             who="lohen", anim="A-192", ambience=AMBIENCE[seq], saturation=0.20,
             sfx=[(4.0, "lantern_set_down_metal")]),
        # 30 s presque entierement noirs : on entend
        Shot("C06_P2", 8.0, 38.0, [(0.0, 41.1, 58.0), (0.0, 41.0, 50.0),
                                   (0.0, 41.0, 42.0)],
             [(0.0, 40.8, 40.0)], fov=46, dof=4.0, exposure=0.06, vignette=0.92,
             who="lohen", anim="A-040", speed=0.7, ambience=AMBIENCE[seq],
             saturation=0.10, shake=0.08,
             sfx=[(10.0, "breath_slow_1"), (14.5, "drip_far"),
                  (19.0, "mueur_chitter_distant"), (24.0, "breath_slow_2"),
                  (28.0, "cloth_rustle"), (33.0, "gravel_shift_far")],
             moving=True),
        Shot("C06_P3", 38.0, 45.0, [(0.0, 41.0, 41.0), (0.0, 41.2, 43.0)],
             [(0.0, 41.4, 52.0)], fov=46, dof=10.0, exposure=0.55,
             who="lohen", anim="A-191", ambience=AMBIENCE[seq], saturation=0.22,
             sfx=[(39.5, "lantern_pickup"), (42.0, "flame_catch")],
             hands_control=True),
    ]
    return Cinematic("C06", "LE NOIR", seq, shots, hands_control_at=43.0,
                     note="15.07 · 0:45 · 30 s presque entierement noires. "
                          "On entend. C'est le moment ou il faut poser la lanterne.")


# ---------------------------------------------------------------------------
# C07 « 21H50 » — 2:10 (15.08)
# ---------------------------------------------------------------------------

def c07():
    seq = "S6"
    shots = [
        # le bleu-lune desature de la salle vide
        Shot("C07_P1", 0.0, 16.0, [(0.0, 97.6, 12.0), (0.0, 97.4, 6.0)],
             [(0.0, 97.0, -4.0)], fov=44, dof=30.0, exposure=0.75,
             ambience=AMBIENCE[seq], saturation=0.22,
             sfx=[(2.0, "room_tone_sealed")]),
        # la bascule vers l'or : les 3 lustres s'allument un par un, en 3 temps,
        # sur 3 notes
        Shot("C07_P2", 16.0, 34.0, [(0.0, 97.4, 6.0), (0.0, 97.5, 0.0)],
             [(0.0, 100.0, -8.0), (0.0, 102.8, 0.0)], fov=48, fov_end=42,
             dof=40.0, exposure=0.95, saturation=0.46, music="M12", fade=1.4,
             ambience=AMBIENCE[seq],
             sfx=[(18.0, "chandelier_light_1"), (23.0, "chandelier_light_2"),
                  (28.0, "chandelier_light_3")]),
        # la salle se remplit de 60 personnes en 6 s, par la porte, en accelere
        Shot("C07_P3", 34.0, 52.0, [(0.0, 97.5, 0.0), (-2.0, 97.6, -2.0),
                                    (0.0, 97.5, -4.0)],
             [(4.0, 97.0, 8.0), (0.0, 97.0, 10.0)], fov=42, dof=24.0,
             exposure=1.05, saturation=0.60, music="M12", fade=0.8,
             ambience=AMBIENCE[seq],
             sfx=[(35.0, "crowd_arrival_accelerated"), (41.0, "ballroom_murmur"),
                  (46.0, "glass_clink_far")]),
        # la valse : 60 couples, la salle doree, saturation 70 % (pic absolu)
        Shot("C07_P4", 52.0, 96.0, [(-3.0, 97.8, -3.0), (3.0, 97.8, 2.0),
                                    (-3.0, 97.8, 6.0), (3.0, 97.8, 1.0)],
             [(0.0, 97.2, -2.0), (1.0, 97.2, -4.0)], fov=46, fov_end=40,
             dof=18.0, dof_end=6.0, saturation=SATURATION_GOLD, music="M12",
             fade=0.6, ambience=AMBIENCE[seq], exposure=1.10,
             sfx=[(60.0, "dance_floor_wood"), (78.0, "laughter_distant")]),
        # Esteban, de dos, au fond — il se retourne a la 118e frame
        Shot("C07_P5", 96.0, 118.0, [(0.0, 97.6, 4.0), (0.0, 97.4, -2.0),
                                     (0.0, 97.2, -6.0)],
             [(0.0, 97.2, -10.0)], fov=38, fov_end=32, dof=10.0, dof_end=3.0,
             who="esteban", anim="A-230", speed=1.0, saturation=SATURATION_GOLD,
             music="M12", fade=0.5, ambience=AMBIENCE[seq], exposure=1.10),
        Shot("C07_P6", 118.0, 130.0, [(0.0, 97.2, -6.4), (0.0, 97.15, -6.8)],
             [(0.0, 97.15, -10.0)], fov=32, fov_end=28, dof=2.4, dof_end=1.2,
             who="esteban", anim="A-236", speed=1.0, saturation=SATURATION_GOLD,
             music="M12", fade=0.4, ambience=AMBIENCE[seq], exposure=1.12,
             sfx=[(118.0, "turn_cloth"), (121.0, "breath_esteban")],
             note="118e frame : PREMIERE FOIS que le joueur voit son visage"),
    ]
    return Cinematic("C07", "21H50", seq, shots, subtitle_scene="S6_D02",
                     note="15.08 · 2:10 · la transition du bleu vers l'or, les "
                          "3 lustres en 3 temps sur 3 notes, 60 personnes en 6 s "
                          "par la porte, et Esteban qui se retourne a la 118e "
                          "frame (1 h 55 de jeu).")


# ---------------------------------------------------------------------------
# C08 « LA FIN DE L'ECHO » — 1:30 (15.09)
# ---------------------------------------------------------------------------

def c08():
    seq = "S6"
    shots = [
        # VFX V25 : la salle se brise comme un vitrail, de l'exterieur vers le
        # centre, en 8 s, pendant que M12 se decompose
        Shot("C08_P1", 0.0, 8.0, [(0.0, 97.6, 6.0), (0.0, 97.8, 2.0)],
             [(0.0, 97.2, -2.0)], fov=46, fov_end=54, shake=0.45, dof=30.0,
             saturation=0.55, music="M12", fade=0.2, ambience=AMBIENCE[seq],
             sfx=[(0.4, "glass_shatter_outer"), (2.2, "glass_shatter_mid"),
                  (4.6, "glass_shatter_inner"), (6.4, "piano_single_note")]),
        Shot("C08_P2", 8.0, 22.0, [(0.0, 97.8, 2.0), (0.0, 97.6, -2.0),
                                   (0.0, 97.4, -6.0)],
             [(0.0, 97.2, -8.0)], fov=54, fov_end=46, shake=0.25, dof=20.0,
             saturation=0.42, music="M12", fade=0.3, ambience=AMBIENCE[seq],
             sfx=[(10.0, "piano_single_note"), (16.0, "room_tone_collapse")],
             note="le dernier morceau a disparaitre est celui ou se tient Esteban"),
        Shot("C08_P3", 22.0, 40.0, [(0.0, 97.2, -6.0), (0.0, 96.8, -2.0)],
             [(0.0, 96.4, 0.0)], fov=44, dof=12.0, exposure=0.7,
             saturation=0.22, ambience=AMBIENCE[seq],
             sfx=[(24.0, "silence_ballroom_return"), (32.0, "breath_lohen")]),
        # 22 s de camera qui recule sur Lohen a genoux
        Shot("C08_P4", 40.0, 90.0, [(0.0, 96.6, 2.0), (0.0, 97.0, 8.0),
                                    (0.0, 97.6, 16.0), (0.0, 98.4, 26.0)],
             [(0.0, 96.2, 0.0)], fov=40, fov_end=34, dof=18.0, dof_end=40.0,
             who="lohen", anim="A-207", speed=1.0, exposure=0.62,
             saturation=0.20, music="", ambience=AMBIENCE[seq],
             sfx=[(44.0, "breath_only"), (70.0, "breath_only")],
             hands_control=True,
             note="aucun son sauf sa respiration"),
    ]
    return Cinematic("C08", "LA FIN DE L'ECHO", seq, shots, hands_control_at=86.0,
                     subtitle_scene="S6_D06",
                     note="15.09 · 1:30 · l'effondrement, VFX V25 en 8 s pendant "
                          "que M12 se decompose, puis 22 s de recul sur Lohen a "
                          "genoux. La salle redevient bleue et vide en 4 s.")


# ---------------------------------------------------------------------------
# C09 « LE VERRIER S'AGENOUILLE » — 1:20 (15.10, 12.16)
# ---------------------------------------------------------------------------

def c09():
    seq = "S7"
    shots = [
        Shot("C09_P1", 0.0, 14.0, [(6.0, 36.0, -196.0), (3.0, 35.4, -198.0)],
             [(0.0, 34.8, -204.0)], fov=44, dof=26.0, exposure=0.7,
             who="verrier", anim="A-581", ambience=AMBIENCE[seq], saturation=0.26,
             sfx=[(2.0, "rain_heavy"), (8.0, "glass_drag_slow")]),
        Shot("C09_P2", 14.0, 32.0, [(2.0, 35.0, -199.0), (0.6, 34.6, -201.0)],
             [(0.0, 34.4, -204.0)], fov=38, fov_end=32, dof=14.0, dof_end=5.0,
             who="verrier", anim="A-617", ambience=AMBIENCE[seq], saturation=0.26,
             music="M14", fade=1.0, sfx=[(18.0, "kneel_glass_crack")]),
        # silence total P3 : aucune musique, aucune percussion (13.18)
        Shot("C09_P3", 32.0, 52.0, [(0.4, 34.6, -201.4), (0.2, 34.5, -202.2)],
             [(0.0, 34.3, -204.0)], fov=32, fov_end=28, dof=4.0, dof_end=1.6,
             who="verrier", anim="A-619", music="", ambience="",
             saturation=0.22, sfx=[(36.0, "breath_verrier"), (46.0, "rain_muffled")],
             note="silence total : M14 coupe tout en phase 3"),
        Shot("C09_P4", 52.0, 68.0, [(0.2, 34.5, -202.2), (-1.4, 34.8, -200.0)],
             [(0.0, 34.4, -203.4)], fov=30, fov_end=40, dof=3.0, dof_end=12.0,
             who="lohen", anim="A-208", ambience=AMBIENCE[seq], saturation=0.26,
             sfx=[(54.0, "folder_handover"), (60.0, "paper_wet")]),
        Shot("C09_P5", 68.0, 80.0, [(-1.4, 34.8, -200.0), (-2.0, 35.2, -196.0)],
             [(0.0, 34.6, -204.0)], fov=42, dof=22.0, who="verrier", anim="A-591",
             ambience=AMBIENCE[seq], saturation=0.26, hands_control=True,
             sfx=[(72.0, "glass_hardening")]),
    ]
    return Cinematic("C09", "LE VERRIER S'AGENOUILLE", seq, shots,
                     hands_control_at=76.0, subtitle_scene="S7_D05",
                     note="15.10 · 1:20 · il s'agenouille (6 s), il tend le "
                          "dossier, il montre le Phare, puis il se laisse durcir. "
                          "Silence total en phase 3 (13.18).")


# ---------------------------------------------------------------------------
# C10 « L'AUBE » — 1:50 (15.11) : le joueur garde les commandes
# ---------------------------------------------------------------------------

def c10():
    seq = "S8"
    shots = []
    # 5 plans de 22 s pendant lesquels le joueur CONTINUE de grimper
    for i in range(5):
        t0 = i * 22.0
        y = 150.0 + i * 12.0
        shots.append(Shot("C10_P%d" % (i + 1), t0, t0 + 22.0,
                          [(14.0, y + 2.0, -416.0), (10.0, y + 2.6, -424.0)],
                          [(0.0, y - 4.0, -430.0), (0.0, y + 20.0, -430.0)],
                          fov=52, fov_end=46, dof=200.0, dof_end=400.0,
                          exposure=0.85 + i * 0.06,
                          saturation=0.34 + i * 0.015,
                          music="M16", fade=0.8, ambience=AMBIENCE[seq],
                          sfx=[(t0 + 6.0, "wind_high_altitude"),
                               (t0 + 14.0, "cable_sway")],
                          hands_control=True, moving=True))
    return Cinematic("C10", "L'AUBE", seq, shots, skip_hold=0.0,
                     hands_control_at=0.0, player_keeps_control=True,
                     letterbox=False,
                     note="15.11 · 1:50 · a 150 m la brume passe SOUS le joueur. "
                          "Ce n'est pas vraiment une cinematique : un evenement "
                          "scripte pendant lequel on ne prend jamais les "
                          "commandes. Pas de barres noires.")


# ---------------------------------------------------------------------------
# C11 « LA LETTRE » — 9:40 (15.12, BLOC 19)
# ---------------------------------------------------------------------------

def c11():
    """
    15.12 / BLOC 19 · 9:40 (580 s) · LA CINEMATIQUE FINALE.
    Decoupage canonique : entree 34 s en camera libre, carnet E30 2:30,
    rituel 2,4 s (11.03 jamais raccourci), 3,5 s sans Echo, depliement 4,1 s,
    lecture au rythme du joueur, trois coupures sur le visage, pliage 5,2 s,
    descente 90 s, Sol 24 s, generique 60 s.
    """
    seq = "S8"
    lx, lz = 0.0, -430.0
    dy = 206.0
    shots = [
        # 19.02 : le palier, la porte — poussee des deux mains, 2,2 s d'appui
        Shot("C11_P1", 0.0, 10.0, [(lx, dy + 1.6, lz + 9.0), (lx, dy + 1.5, lz + 4.4)],
             [(lx, dy + 1.2, lz)], fov=42, dof=16.0, exposure=0.8,
             who="lohen", anim="A-209", speed=1.0, ambience=AMBIENCE[seq],
             saturation=0.36, sfx=[(2.2, "door_push_heavy_hold")]),
        # 34 s d'entree en camera libre : la piece ronde de 6 m
        Shot("C11_P2", 10.0, 44.0, [(lx + 2.4, dy + 1.5, lz + 2.4),
                                    (lx - 1.8, dy + 1.6, lz + 1.6),
                                    (lx - 1.2, dy + 1.4, lz - 2.0),
                                    (lx + 1.6, dy + 1.5, lz - 1.4)],
             [(lx - 1.2, dy + 1.0, lz + 0.6), (lx + 0.8, dy + 0.9, lz - 0.8),
              (lx, dy + 1.1, lz)],
             fov=38, fov_end=44, dof=8.0, dof_end=18.0, exposure=0.85,
             ambience="chambre_phare", saturation=0.38, music="M18", fade=2.0,
             sfx=[(16.0, "oil_lamp_flicker"), (30.0, "wind_outside_glass")]),
        # E30 le carnet : 2:30, 6 extraits lus par la voix d'Esteban
        Shot("C11_P3", 44.0, 194.0, [(lx + 0.9, dy + 1.35, lz - 0.2),
                                     (lx + 0.6, dy + 1.2, lz - 0.6)],
             [(lx + 0.8, dy + 0.95, lz - 0.8)], fov=30, fov_end=26,
             dof=2.0, dof_end=1.0, exposure=0.95, who="lohen", anim="A-213",
             speed=1.0, ambience="chambre_phare", saturation=0.40, music="M18",
             fade=1.0, moving=False),
        # le 31e rituel : 2,4 s, jamais raccourci (11.03)
        Shot("C11_P4", 194.0, 196.4, [(lx + 0.3, dy + 1.3, lz - 0.9),
                                      (lx + 0.1, dy + 1.25, lz - 1.0)],
             [(lx, dy + 0.95, lz - 1.0)], fov=28, dof=1.2, exposure=0.95,
             who="lohen", anim="A-210", speed=1.0, ambience="chambre_phare",
             saturation=0.40, music="M18", fade=0.4,
             sfx=[(194.0, "gloves_off_1_1s"), (195.4, "palm_on_paper")],
             note="11.03 [OBL] : les 2,4 s du gant, jamais raccourcies"),
        # 19.07 : il n'y a pas d'Echo. 3,5 s de silence
        Shot("C11_P5", 196.4, 199.9, [(lx + 0.1, dy + 1.25, lz - 1.0)],
             [(lx, dy + 0.95, lz - 1.0)], fov=28, dof=1.2, exposure=0.95,
             who="lohen", anim="A-211", speed=1.0, ambience="", music="",
             saturation=0.38, moving=False,
             note="AUCUN Echo. Silence de 3,5 s."),
        # le depliement : 4,1 s
        Shot("C11_P6", 199.9, 204.0, [(lx + 0.2, dy + 1.2, lz - 1.1),
                                      (lx + 0.05, dy + 1.15, lz - 1.15)],
             [(lx, dy + 0.95, lz - 1.0)], fov=26, fov_end=24, dof=0.9,
             exposure=1.0, who="lohen", anim="A-212", speed=1.0,
             ambience="chambre_phare", saturation=0.42, music="M18", fade=1.0,
             sfx=[(200.1, "paper_unfold_4_1s")]),
        # la lecture : au rythme du joueur, musique M18 scoree a la seconde
        Shot("C11_P7", 204.0, 329.8, [(lx - 0.4, dy + 1.3, lz - 1.6),
                                      (lx + 0.4, dy + 1.3, lz - 1.6)],
             [(lx, dy + 0.95, lz - 1.0)], fov=28, fov_end=32, dof=1.6,
             exposure=1.0, who="lohen", anim="A-213", speed=1.0,
             ambience="chambre_phare", saturation=0.42, music="M18", fade=0.8,
             sfx=[(250.0, "page_turn")], moving=False),
        # coupure n 1 : jaw_clench 0,8 s (A-214, 2,8 s)
        Shot("C11_P8", 329.8, 332.6, [(lx, dy + 1.55, lz - 1.9),
                                      (lx + 0.02, dy + 1.55, lz - 1.94)],
             [(lx, dy + 1.5, lz - 1.4)], fov=24, dof=0.8, exposure=1.0,
             who="lohen", anim="A-214", speed=1.0, ambience="chambre_phare",
             saturation=0.42, music="M18", fade=0.3, moving=False),
        # coupure n 2 : LA CLOCHE. Les cinq notes. yeux fermes (A-215, 4,2 s)
        Shot("C11_P9", 332.6, 336.8, [(lx, dy + 1.55, lz - 1.9)],
             [(lx, dy + 1.5, lz - 1.4)], fov=24, fov_end=22, dof=0.7,
             who="lohen", anim="A-215", speed=1.0, ambience="chambre_phare",
             saturation=0.42, music="M18", fade=0.2,
             sfx=[(333.0, "letter_bell_five_notes")], moving=False),
        # « trois cent quatorze fois » : le faisceau eclaire le mur de feuilles
        Shot("C11_P10", 336.8, 348.8, [(lx - 1.4, dy + 1.5, lz - 0.6),
                                       (lx - 2.2, dy + 1.6, lz + 0.4)],
             [(lx - 2.6, dy + 1.4, lz - 2.0)], fov=34, fov_end=40, dof=6.0,
             exposure=1.35, saturation=0.44, ambience="chambre_phare",
             music="M18", fade=0.4,
             sfx=[(338.8, "lighthouse_beam_through_window"),
                  (340.8, "paper_wall_revealed_314")],
             note="20.04 [OBL] raccord scripte a la frame pres : le joueur voit "
                  "les 314 papiers d'un coup"),
        # « Tu m'as trouve. / Tu peux la garder. » — 2,5 s de silence total
        Shot("C11_P11", 348.8, 360.8, [(lx - 0.6, dy + 1.5, lz - 1.4),
                                       (lx + 0.6, dy + 1.5, lz - 1.4)],
             [(lx, dy + 1.5, lz - 1.4)], fov=26, dof=1.0, who="lohen",
             anim="A-216", speed=1.0, ambience="", music="", saturation=0.40,
             moving=False,
             note="silence total de 2,5 s entre les deux lignes ; la camera est "
                  "deja en mouvement de recul"),
        # le pliage : 5,2 s, il la met dans sa sacoche — la ou il met les
        # lettres des autres
        Shot("C11_P12", 360.8, 366.0, [(lx + 0.2, dy + 1.35, lz - 1.2),
                                       (lx + 0.4, dy + 1.4, lz - 0.6)],
             [(lx + 0.2, dy + 1.0, lz - 0.9)], fov=30, dof=2.0, who="lohen",
             anim="A-217", speed=1.0, ambience="chambre_phare", saturation=0.42,
             music="M19", fade=1.2, sfx=[(361.8, "paper_fold_three"),
                                         (364.5, "satchel_close")]),
        # la descente : 90 s, aucun son sauf le vent
        Shot("C11_P13", 366.0, 456.0, [(lx + 6.0, dy - 20.0, lz + 6.0),
                                       (lx + 10.0, 60.0, lz + 14.0),
                                       (lx + 12.0, 14.0, lz + 18.0)],
             [(lx, dy - 30.0, lz), (lx, 20.0, lz + 8.0), (lx + 10.0, 8.0, lz + 12.0)],
             fov=48, fov_end=44, dof=120.0, exposure=1.15, who="lohen",
             anim="A-031", speed=1.0, ambience="aube_verre", saturation=0.46,
             music="M19", fade=1.0),
        # Sol dort contre la porte : 24 s, la veste posee sur lui
        Shot("C11_P14", 456.0, 480.0, [(lx + 11.0, 9.4, lz + 13.0),
                                       (lx + 10.4, 9.0, lz + 11.6)],
             [(lx + 10.0, 8.6, lz + 10.0)], fov=36, fov_end=32, dof=8.0,
             dof_end=3.0, exposure=1.25, who="lohen", anim="A-218", speed=1.0,
             ambience="aube_verre", saturation=0.50, music="M19", fade=0.8,
             sfx=[(462.0, "coat_on_shoulders"), (472.0, "breath_sleeping_child")]),
        # dernier plan : le soleil se leve
        Shot("C11_P15", 480.0, 520.0, [(lx + 10.0, 9.6, lz + 12.0),
                                       (lx + 14.0, 12.0, lz + 20.0),
                                       (lx + 18.0, 18.0, lz + 30.0)],
             [(lx, 60.0, lz), (lx, 120.0, lz), (60.0, 200.0, 200.0)],
             fov=44, fov_end=52, dof=300.0, exposure=1.45, saturation=0.55,
             ambience="aube_verre", music="M19", fade=1.2,
             sfx=[(500.0, "wind_dawn_open")],
             note="10.17 : le soleil se leve. Fin du Chapitre 1."),
        # le generique : 60 s, non skippable, M20 (la seule piste avec paroles)
        Shot("C11_P16", 520.0, 580.0, [(0.0, 40.0, 120.0), (0.0, 60.0, 140.0)],
             [(0.0, 120.0, -200.0)], fov=46, dof=600.0, exposure=1.3,
             saturation=0.50, music="M20", fade=2.0, ambience="aube_verre",
             hands_control=False, moving=True),
    ]
    return Cinematic("C11", "LA LETTRE", seq, shots, skip_hold=SKIP_HOLD_LETTER,
                     hands_control_at=456.0, subtitle_scene="S8_D05",
                     note="15.12 / BLOC 19 · 9:40 · la seule cinematique de plus "
                          "de 3 min. Skippable apres 20 s seulement (07.22). "
                          "M18 est scoree a la seconde (19.09).")


CINEMATICS = [c01, c02, c03, c04, c05, c06, c07, c08, c09, c10, c11]


# ---------------------------------------------------------------------------
# Verification (BLOC 18 / 22)
# ---------------------------------------------------------------------------

ANIM_OWNER = {
    "lohen": "LOHEN", "esteban": "ESTEBAN", "sol": "SOL", "tallec": "CAPITAINE",
    "mireille": "MIREILLE", "verrier": "VERRIER", "mueur": "MUEUR",
    "echassier": "ECHASSIER",
}


def load_anims():
    path = os.path.join(CONTENT, "anims", "anims.json")
    if not os.path.exists(path):
        return {}
    with open(path, "r", encoding="utf-8") as f:
        doc = json.load(f)
    return {c["id"]: c for c in doc.get("clips", [])}


# durees canoniques annoncees par BLOC 15 (secondes)
TARGET = {"C01": 100.0, "C02": 65.0, "C03": 85.0, "C04": 50.0, "C05": 75.0,
          "C06": 45.0, "C07": 130.0, "C08": 90.0, "C09": 80.0, "C10": 110.0,
          "C11": 580.0}


def verify(cines, verbose=False):
    errors = []
    total = 0.0
    anims = load_anims()
    for c in cines:
        d = c.duration
        total += d
        if c.id != "C11" and d > MAX_MINUTES * 60.0 + 0.01:
            errors.append("%s dure %.1f s > 3 min (15.01)" % (c.id, d))
        if c.id == "C11" and c.skip_hold != SKIP_HOLD_LETTER:
            errors.append("C11 doit etre skippable apres 20 s (07.22)")
        want = TARGET.get(c.id)
        if want is not None and abs(d - want) > 0.5:
            errors.append("%s dure %.1f s, BLOC 15 annonce %.1f s" % (c.id, d, want))
        if c.id != "C11" and c.skip_hold not in (SKIP_HOLD, 0.0):
            errors.append("%s : appui long de %.1f s (07.22 attend 1,5 s)"
                          % (c.id, c.skip_hold))
        prev = 0.0
        for s in c.shots:
            if s.t0 < prev - 0.001:
                errors.append("%s/%s : plan chevauchant" % (c.id, s.id))
            prev = s.t1
            if s.t1 <= s.t0:
                errors.append("%s/%s : duree nulle" % (c.id, s.id))
            if not s.path or not s.look:
                errors.append("%s/%s : spline camera vide" % (c.id, s.id))
            # 07.23 regle anti-mollesse
            moves = len(set(s.path)) > 1 or bool(s.anim) or bool(s.sfx)
            if not moves:
                errors.append("%s/%s : plan fixe sans mouvement (07.23)"
                              % (c.id, s.id))
            if s.fov < 20 or s.fov > 70:
                errors.append("%s/%s : FOV %.0f hors cadre (08.23)"
                              % (c.id, s.id, s.fov))
            # chaque clip reference doit exister dans le manifeste des 744
            # animations et appartenir au bon personnage (BLOC 17).
            if s.anim and anims:
                clip = anims.get(s.anim)
                if clip is None:
                    errors.append("%s/%s : clip %s absent du manifeste"
                                  % (c.id, s.id, s.anim))
                else:
                    want = ANIM_OWNER.get(s.who, "")
                    if want and want not in clip["char"]:
                        errors.append("%s/%s : clip %s (%s) ne correspond pas a %s"
                                      % (c.id, s.id, s.anim, clip["char"], s.who))
                    # 07.23 : un clip NON boucle plus court que le plan laisse
                    # l'acteur fige. Un clip plus long est simplement coupe au
                    # raccord de plan, c'est le cas normal en cinematique.
                    if not clip.get("loop") and clip["dur"] < s.duration - 0.05:
                        hold = s.hold if s.hold is not None else HOLD.get(s.who, "")
                        hc = anims.get(hold)
                        if not hc or not hc.get("loop"):
                            errors.append("%s/%s : clip %s (%.1f s, non boucle) "
                                          "plus court que le plan (%.1f s) et "
                                          "aucune respiration de tenue -> acteur "
                                          "fige (07.23)"
                                          % (c.id, s.id, s.anim, clip["dur"],
                                             s.duration))
            for off, spk, line in s.subs:
                if off > s.duration + 0.5:
                    errors.append("%s/%s : sous-titre hors plan" % (c.id, s.id))
                if len(line) > 200:
                    errors.append("%s/%s : ligne trop longue" % (c.id, s.id))
    if len(cines) != 11:
        errors.append("%d cinematiques, 11 attendues (07.21)" % len(cines))
    if verbose:
        print("  duree cumulee : %.1f s (%d min %02d s)"
              % (total, int(total // 60), int(round(total % 60))))
        print("  07.21 annonce 23 min 40 s (1420 s) ; la somme des durees de "
              "BLOC 15 fait %d s — ecart de %.0f s documente dans "
              "docs/placeholders.md" % (round(total), 1420.0 - total))
    return errors, total


def main():
    ap = argparse.ArgumentParser(
        description="Genere les 11 timelines de cinematique (BLOC 15)")
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args()
    print("LOHEN · pipeline cinematiques")
    os.makedirs(OUT, exist_ok=True)
    cines = [f() for f in CINEMATICS]
    errors, total = verify(cines, verbose=args.verbose)
    for c in cines:
        path = os.path.join(OUT, "%s.json" % c.id.lower())
        with open(path, "w", encoding="utf-8") as f:
            json.dump(c.to_json(), f, ensure_ascii=False, separators=(",", ":"))
        if args.verbose:
            subs = sum(len(s.subs) for s in c.shots)
            print("  %s « %s » %s · %.0f s · %d plans · %d sous-titres · %.0f Ko"
                  % (c.id, c.title, c.seq, c.duration, len(c.shots), subs,
                     os.path.getsize(path) / 1024.0))
    print("  total     : %d cinematiques, %.1f s cumulees" % (len(cines), total))
    if errors:
        print("ERREURS de conformite :")
        for e in errors:
            print("  - %s" % e)
        return 1
    print("OK — content/cinematics/ genere")
    return 0


if __name__ == "__main__":
    sys.exit(main())
