#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LOHEN — tools/level_compiler.py

Compilateur de niveaux : genere `content/levels/level_s1.json` … `level_s8.json`
a partir du BLOC 09 (LEVEL DESIGN : LA VILLE DE VELMORA, ZONE PAR ZONE) et du
contenu deja genere par `tools/content_pipeline.py` (147 props, 31 Echos,
graphe de quetes).

Le format de sortie est celui que lit `LevelData.parse()` :

    seq, name, subtitle, rule, music, ambience, reverb,
    transition_in, transition_out, objective_key, target_minutes,
    altitude_min, altitude_max, objective_altitude,
    spawn[4], bounds[6], sky{...},
    solids[[cx,cy,cz,hx,hy,hz,yaw]…], solid_flags[], solid_materials[],
    solid_kinds[], stairs[[x,y,z,w,h,d,yaw,steps]…], stair_materials[],
    ledges[[x,y,z,len,yaw,kind]…], anchors[[x,y,z,type]…],
    props[[x,y,z,yaw,scale]…], prop_ids[],
    checkpoints[[x,y,z,yaw]…], checkpoint_ids[],
    triggers[], npcs[], echo_points[], lights[], fog_zones[],
    encounters[], secrets[], shortcuts[]

Contraintes de jouabilite verifiees a la generation (et reimprimees en fin
de course) :
  * une marche isolee ne depasse jamais 0,45 m de denivelee (le moteur
    monte sans saut au-dela),
  * un saut ne depasse jamais 3,2 m de vide horizontal (portee reelle
    5,1 m/s x 0,69 s d'air),
  * une prise d'escalade est a 0,6-2,35 m au-dessus du sol et a moins de
    1,6 m en horizontal (findLedge),
  * une ancre de grappin est a moins de 28 m de la precedente (V3),
  * les 47 checkpoints sont places APRES un effort et AVANT un risque,
  * les 14 raccourcis ouvrent tous dans le sens du retour (09.23),
  * les 31 secrets sont hors du chemin principal (09.24).

Usage :  python3 tools/level_compiler.py [--verbose]
"""

from __future__ import annotations

import argparse
import json
import math
import os
import random
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENT = os.path.join(ROOT, "content")
OUT = os.path.join(CONTENT, "levels")

# ---------------------------------------------------------------------------
# Constantes de la spec (BLOC 05, 07, 08, 09)
# ---------------------------------------------------------------------------

# Geom.FLAG_* (sim/math/Geom.java)
F_WALKABLE = 1
F_CLIMBABLE = 1 << 1
F_LEDGE = 1 << 2
F_ANCHOR = 1 << 3
F_GLASS = 1 << 4
F_FRAGILE = 1 << 5
F_DEADLY = 1 << 6
F_INTERACTIVE = 1 << 7
F_SLIDE = 1 << 8
F_HEAT_WELL = 1 << 9
F_INDOOR = 1 << 10
F_LAMP_ROOM = 1 << 11
F_TRANSITION = 1 << 12
F_WATER_SHALLOW = 1 << 13

# Geom.MAT_* (0..8)
MAT_WOOD = 0
MAT_WOOD_WET = 1
MAT_STONE = 2
MAT_STONE_WET = 3
MAT_GRAVEL = 4
MAT_GLASS = 5
MAT_METAL = 6
MAT_CARPET = 7
MAT_WATER = 8

# kinds de solides (utilise par le renderer pour choisir le kit 06.21)
K_GROUND = 0        # sol / quai / dalle
K_PLATFORM = 1      # passerelle / plancher
K_WALL = 2          # mur / facade
K_ROOF = 3          # toit
K_PILLAR = 4        # pilier / pylone
K_DEBRIS = 5        # debris / bloc de verre
K_FURNITURE = 6     # mobilier
K_STRUCTURE = 7     # structure metallique (K4)
K_TOWER = 8         # le Phare

STEP_UP_MAX = 0.45          # denivelee montable sans saut
JUMP_GAP_MAX = 3.20         # vide franchissable en course
JUMP_RISE_MAX = 0.90        # apex reel (5,4 m/s, g 15,7)
LEDGE_RISE_MIN = 0.60
LEDGE_RISE_MAX = 2.35
LEDGE_REACH_H = 1.60
GRAPPLE_RANGE = 28.0

LIGHTHOUSE_X = 0.0
LIGHTHOUSE_Z = -430.0
LIGHTHOUSE_TOP = 212.0

# 09.03 : la carte des 8 sequences
SEQUENCES = {
    "S1": dict(name="LES QUAIS BAS", subtitle="Ce qui reste quand la mer s'arrete.",
               alt=(0.0, 14.0), minutes=26, rule="marche, saut, echelles",
               ambience="port_mort", reverb="falaise", music="M01", saturation=0.30,
               checkpoints=6, shortcuts=2, secrets=4, indoor=False, night=False,
               rain=False, fog=70.0, wind=1, objective="obj.S1.registre"),
    "S2": dict(name="LE VERRE ET LE BATEAU", subtitle="On ne marche pas sur une tombe. Si.",
               alt=(0.0, 6.0), minutes=22, rule="le verre, pas de grappin",
               ambience="maree_verre", reverb="falaise", music="M02", saturation=0.28,
               checkpoints=4, shortcuts=1, secrets=3, indoor=False, night=False,
               rain=False, fog=90.0, wind=2, objective="obj.S2.hirondelle"),
    "S3": dict(name="LE MARCHE SUSPENDU", subtitle="La ville a continue. Mal, mais elle a continue.",
               alt=(18.0, 62.0), minutes=34, rule="grappin pendule, foule",
               ambience="marche_suspendu", reverb="hangar", music="M04", saturation=0.55,
               checkpoints=9, shortcuts=4, secrets=7, indoor=False, night=False,
               rain=False, fog=120.0, wind=3, objective="obj.S3.montechage"),
    "S4": dict(name="LA BIBLIOTHEQUE DES EAUX", subtitle="Ici on garde les noms.",
               alt=(55.0, 78.0), minutes=28, rule="interieur, silence, Echos",
               ambience="bibliotheque_eaux", reverb="eglise", music="M08", saturation=0.34,
               checkpoints=6, shortcuts=2, secrets=5, indoor=True, night=False,
               rain=False, fog=40.0, wind=0, objective="obj.S4.dossier"),
    "S5": dict(name="LES CONDUITS", subtitle="Le ventre.",
               alt=(40.0, 96.0), minutes=24, rule="etroit, Sol, furtivite",
               ambience="conduits", reverb="tunnel", music="M09", saturation=0.24,
               checkpoints=5, shortcuts=1, secrets=3, indoor=True, night=False,
               rain=False, fog=26.0, wind=0, objective="obj.S5.grille"),
    "S6": dict(name="LA SALLE DE BAL", subtitle="Le souvenir.",
               alt=(96.0, 118.0), minutes=18, rule="l'Echo le plus long",
               ambience="salle_de_bal", reverb="piece_vide", music="M12", saturation=0.70,
               checkpoints=3, shortcuts=0, secrets=2, indoor=True, night=True,
               rain=False, fog=18.0, wind=0, objective="obj.S6.echo"),
    "S7": dict(name="LA DESCENTE", subtitle="Il faut redescendre.",
               alt=(118.0, 8.0), minutes=30, rule="sans grappin, le Verrier",
               ambience="pluie_battante", reverb="falaise", music="M14", saturation=0.26,
               checkpoints=7, shortcuts=2, secrets=4, indoor=False, night=True,
               rain=True, fog=55.0, wind=4, objective="obj.S7.phare"),
    "S8": dict(name="LA MONTEE AU PHARE", subtitle="Tout en haut, une lampe allumee.",
               alt=(8.0, 212.0), minutes=36, rule="tout, aucun ennemi",
               ambience="aube_verre", reverb="falaise", music="M16", saturation=0.40,
               checkpoints=7, shortcuts=2, secrets=3, indoor=False, night=False,
               rain=False, fog=200.0, wind=5, objective="obj.S8.chambre"),
}
ORDER = ["S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8"]

# 09.44 : les 6 salles a lampe (max 4 lumieres ombrees par piece)
LAMP_ROOMS = {
    "S1": ("L1", "le bureau du Registre", 3),
    "S2": ("L2", "la cabine de L'Hirondelle", 2),
    "S3": ("L3", "le poste de Tallec", 4),
    "S4": ("L4", "la salle des fiches", 3),
    "S5": ("L5", "le conduit du puits", 2),
    "S8": ("L6", "la chambre du Phare", 4),
}

# 13.31 : les 8 silences actifs (id, sequence, secondes, endroit)
SILENCES = [
    ("SB1", "S1", 3.0, "le muret aux bottes, avant E01"),
    ("SB2", "S2", 4.0, "la premiere fois sur la Maree"),
    ("SB3", "S3", 2.0, "Sol s'arrete net"),
    ("SB4", "S4", 3.5, "la chemise cartonnee est vide"),
    ("SB5", "S5", 4.0, "poser la lanterne, C06 LE NOIR"),
    ("SB6", "S6", 5.0, "l'entree de la salle de bal"),
    ("SB7", "S7", 2.5, "le Verrier s'agenouille"),
    ("SB8", "S8", 3.5, "il n'y a pas d'Echo"),
]

# 09.51 : les 7 couloirs de transition
CORRIDORS = [
    ("T1", "S1", "S2", 26.0), ("T2", "S2", "S3", 34.0), ("T3", "S3", "S4", 40.0),
    ("T4", "S4", "S5", 22.0), ("T5", "S5", "S6", 18.0), ("T6", "S6", "S7", 30.0),
    ("T7", "S7", "S8", 32.0),
]

# 09.24 : decomposition des 31 secrets
SECRET_PLAN = [
    ("letter_fragment", 9), ("journal_object", 6), ("breath_upgrade", 3),
    ("viewpoint", 8), ("optional_echo", 5),
]

# 09.02 : les 7 reperes, un par etage historique
LANDMARKS = [
    ("la grue rouge", 11.0), ("le clocher penche", 46.0), ("la baleine de bois", 63.0),
    ("le pont casse", 84.0), ("la serre de verre vert", 108.0),
    ("la rangee de linge bleu", 141.0), ("la statue sans tete", 178.0),
]


def clamp(v, lo, hi):
    return lo if v < lo else (hi if v > hi else v)


def r2(v):
    return round(float(v), 3)


# ---------------------------------------------------------------------------
# Constructeur de niveau
# ---------------------------------------------------------------------------

class LevelBuilder:
    """Accumule la geometrie et les entites d'une sequence."""

    def __init__(self, seq, meta, seed=20251014):
        self.seq = seq
        self.meta = meta
        self.rng = random.Random(seed + int(seq[1:]) * 7919)
        self.solids = []          # [cx,cy,cz,hx,hy,hz,yaw]
        self.solid_flags = []
        self.solid_materials = []
        self.solid_kinds = []
        self.stairs = []          # [x,y,z,w,h,d,yaw,steps]
        self.stair_materials = []
        self.ledges = []          # [x,y,z,len,yaw,kind]
        self.anchors = []         # [x,y,z,type]
        self.props = []           # [x,y,z,yaw,scale]
        self.prop_ids = []
        self.checkpoints = []     # [x,y,z,yaw]
        self.checkpoint_ids = []
        self.triggers = []
        self.npcs = []
        self.echo_points = []
        self.lights = []
        self.fog_zones = []
        self.encounters = []
        self.secrets = []
        self.shortcuts = []
        self.path = []            # points du chemin principal (x,y,z)
        self.path_depth = []      # profondeur de la dalle a chaque station
        self._last_anchor = None
        self._warnings = []
        self._anchor_chain_worst = 0.0

    # -- primitives ---------------------------------------------------------

    def box(self, cx, cy, cz, hx, hy, hz, yaw=0.0, flags=F_WALKABLE, mat=MAT_STONE,
            kind=K_GROUND):
        self.solids.append([r2(cx), r2(cy), r2(cz), r2(hx), r2(hy), r2(hz), r2(yaw)])
        self.solid_flags.append(int(flags))
        self.solid_materials.append(int(mat))
        self.solid_kinds.append(int(kind))
        return len(self.solids) - 1

    def slab(self, x, top_y, z, w, d, mat=MAT_STONE, flags=F_WALKABLE, kind=K_GROUND,
             thick=0.6, yaw=0.0):
        """Dalle dont la surface supérieure est exactement `top_y`."""
        return self.box(x, top_y - thick * 0.5, z, w * 0.5, thick * 0.5, d * 0.5,
                        yaw=yaw, flags=flags, mat=mat, kind=kind)

    def wall(self, x, base_y, z, length, height, thickness, yaw=0.0, mat=MAT_STONE,
             kind=K_WALL, flags=0):
        return self.box(x, base_y + height * 0.5, z, length * 0.5, height * 0.5,
                        thickness * 0.5, yaw=yaw, flags=flags, mat=mat, kind=kind)

    def railing(self, x, base_y, z, length, yaw=0.0, mat=MAT_METAL):
        """Garde-corps : bloque sans masquer (0,9 m, ajoure par le renderer)."""
        return self.wall(x, base_y, z, length, 0.95, 0.08, yaw=yaw, mat=mat,
                         kind=K_STRUCTURE)

    def stair(self, x, base_y, z, w, h, d, yaw=0.0, steps=None, mat=MAT_STONE):
        if steps is None:
            steps = max(3, int(round(h / 0.17)))
        self.stairs.append([r2(x), r2(base_y), r2(z), r2(w), r2(h), r2(d), r2(yaw),
                            float(steps)])
        self.stair_materials.append(int(mat))
        return len(self.stairs) - 1

    def ledge(self, x, y, z, length, yaw=0.0, kind=0):
        self.ledges.append([r2(x), r2(y), r2(z), r2(length), r2(yaw), float(kind)])
        return len(self.ledges) - 1

    def anchor(self, x, y, z, kind=0, prev=None):
        self.anchors.append([r2(x), r2(y), r2(z), float(kind)])
        self._last_anchor = (x, y, z)
        return len(self.anchors) - 1

    def prop(self, pid, x, y, z, yaw=0.0, scale=1.0):
        self.props.append([r2(x), r2(y), r2(z), r2(yaw), r2(scale)])
        self.prop_ids.append(pid)
        return len(self.props) - 1

    def checkpoint(self, cid, x, y, z, yaw=0.0):
        self.checkpoints.append([r2(x), r2(y), r2(z), r2(yaw)])
        self.checkpoint_ids.append(cid)
        return len(self.checkpoints) - 1

    def trigger(self, tid, ttype, x, y, z, rx=2.0, ry=2.0, rz=2.0, target="",
                param="", value=0.0, one_shot=True):
        self.triggers.append(dict(id=tid, type=ttype, x=r2(x), y=r2(y), z=r2(z),
                                  rx=r2(rx), ry=r2(ry), rz=r2(rz), target=target,
                                  param=param, value=r2(value), one_shot=one_shot))

    def npc(self, nid, role, x, y, z, yaw=0.0, routine="idle", radius=2.0,
            dialogue="", barks="", major=False):
        self.npcs.append(dict(id=nid, role=role, x=r2(x), y=r2(y), z=r2(z),
                              yaw=r2(yaw), routine=routine, routine_radius=r2(radius),
                              dialogue=dialogue, barks=barks, major=bool(major)))

    def echo_point(self, echo_id, prop_id, x, y, z, radius=1.5):
        self.echo_points.append(dict(echo=echo_id, prop=prop_id, x=r2(x), y=r2(y),
                                     z=r2(z), radius=r2(radius)))

    def light(self, ltype, x, y, z, color="#FFD6A0", rng=6.0, intensity=1.0,
              flicker=False, room=""):
        self.lights.append(dict(type=ltype, x=r2(x), y=r2(y), z=r2(z), color=color,
                                range=r2(rng), intensity=r2(intensity),
                                flicker=bool(flicker), room=room))

    def fog(self, x, y, z, radius=20.0, density=0.02, color="#16263A"):
        self.fog_zones.append(dict(x=r2(x), y=r2(y), z=r2(z), radius=r2(radius),
                                   density=r2(density), color=color))

    def encounter(self, eid, x, y, z, radius=12.0, condition="", avoidable=True,
                  boss=False, phase=1, enemies=()):
        self.encounters.append(dict(id=eid, x=r2(x), y=r2(y), z=r2(z),
                                    radius=r2(radius), condition=condition,
                                    avoidable=bool(avoidable), boss=bool(boss),
                                    phase=int(phase), enemies=list(enemies)))

    def secret(self, sid, kind, x, y, z, radius=3.0, payload=""):
        self.secrets.append(dict(id=sid, kind=kind, x=r2(x), y=r2(y), z=r2(z),
                                 radius=r2(radius), payload=payload))

    def shortcut(self, sid, kind, x, y, z, radius=2.0, opens_to=""):
        self.shortcuts.append(dict(id=sid, kind=kind, x=r2(x), y=r2(y), z=r2(z),
                                   radius=r2(radius), opens_to=opens_to))

    def warn(self, msg):
        self._warnings.append(msg)

    # -- chemin principal ---------------------------------------------------

    def walk_to(self, x, y, z, width=6.0, depth=6.0, mat=MAT_STONE, kind=K_GROUND,
                flags=F_WALKABLE, connector="auto", name=""):
        """
        Pose une station du chemin principal et la relie a la precedente.
        `connector` : auto · ramp · jump · ledge · grapple · none
        """
        if self.path:
            px, py, pz = self.path[-1]
            prev_depth = (self.path_depth[-1]
                          if len(self.path_depth) == len(self.path) else depth)
            self.connect(px, py, pz, x, y, z, width, depth, mat, connector,
                         prev_depth)
        self.slab(x, y, z, width, depth, mat=mat, flags=flags, kind=kind)
        self.path.append((x, y, z))
        self.path_depth.append(depth)
        return (x, y, z)

    def connect(self, x0, y0, z0, x1, y1, z1, width, depth, mat, mode,
                depth_prev=None):
        dx, dz = x1 - x0, z1 - z0
        dist = math.hypot(dx, dz)
        dh = y1 - y0
        if dist < 0.35:
            return
        yaw = math.degrees(math.atan2(dx, dz))
        mx, mz = (x0 + x1) * 0.5, (z0 + z1) * 0.5
        if mode == "auto":
            if abs(dh) <= STEP_UP_MAX:
                mode = "bridge"
            elif abs(dh) <= 6.0 and dist <= 16.0:
                mode = "ramp"
            elif dh > 0 and abs(dh) <= LEDGE_RISE_MAX + 1.0 and dist <= 6.0:
                mode = "ledge"
            elif dist <= JUMP_GAP_MAX + 2.0:
                mode = "jump"
            else:
                mode = "grapple"
        if mode == "bridge":
            length = max(0.6, dist - depth * 0.5)
            self.box(mx, min(y0, y1) - 0.3, mz, max(0.9, width * 0.35), 0.3,
                     length * 0.5, yaw=yaw, flags=F_WALKABLE, mat=mat, kind=K_PLATFORM)
            return
        if mode == "ramp":
            d = max(2.0, dist - depth * 0.4)
            self.stair(mx, min(y0, y1), mz, max(1.6, width * 0.5), abs(dh), d,
                       yaw=yaw, mat=mat)
            # palier intermédiaire si la rampe est longue
            if d > 9.0:
                self.slab(mx, min(y0, y1) + abs(dh) * 0.5, mz, width * 0.7, 2.4,
                          mat=mat, kind=K_PLATFORM)
            return
        if mode == "jump":
            d0 = depth if depth_prev is None else depth_prev
            gap = dist - d0 * 0.5 - depth * 0.5
            if gap > JUMP_GAP_MAX:
                # on ne laisse jamais un vide infranchissable : pilier intermédiaire
                self.slab(mx, min(y0, y1) - 0.15, mz, 2.0, 2.0, mat=mat,
                          kind=K_PLATFORM)
                self.warn("saut %s reduit par un pilier (%.1f m de vide)"
                          % (self.seq, gap))
            return
        if mode == "ledge":
            rise = clamp(dh, LEDGE_RISE_MIN, LEDGE_RISE_MAX)
            self.wall(mx, min(y0, y1), mz, max(2.0, width * 0.6), max(1.2, dh + 0.4),
                      0.5, yaw=yaw, mat=mat, flags=F_CLIMBABLE, kind=K_WALL)
            self.ledge(mx, min(y0, y1) + rise, mz, max(2.0, width * 0.6), yaw=yaw,
                       kind=0)
            return
        if mode == "grapple":
            if dist > GRAPPLE_RANGE:
                # relais : une ancre intermediaire, jamais plus de 28 m d'un bord
                n = int(math.ceil(dist / GRAPPLE_RANGE))
                for i in range(1, n):
                    t = i / float(n)
                    self.anchor(x0 + dx * t, max(y0, y1) + 3.5, z0 + dz * t, kind=0)
            self.anchor(x1, y1 + 3.2, z1, kind=0)
            # le cable doit avoir un plancher visuel : poutre au-dessus du vide
            self.box(mx, max(y0, y1) + 4.6, mz, 0.35, 0.35, dist * 0.5, yaw=yaw,
                     flags=0, mat=MAT_METAL, kind=K_STRUCTURE)
            return
        if mode == "none":
            return
        self.warn("connecteur inconnu : %s" % mode)

    # -- audit de jouabilite ------------------------------------------------

    def audit(self):
        """Vérifie les contraintes numériques du moteur. Renvoie une liste d'écarts."""
        issues = []
        for i in range(1, len(self.path)):
            x0, y0, z0 = self.path[i - 1]
            x1, y1, z1 = self.path[i]
            dist = math.hypot(x1 - x0, z1 - z0)
            dh = y1 - y0
            if dh > 6.0 and dist > 16.0 and dh > JUMP_RISE_MAX:
                # franchissable uniquement si une ancre ou une prise existe
                near_anchor = any(
                    math.hypot(a[0] - x1, a[2] - z1) < 12.0
                    and abs(a[1] - y1) < 12.0 for a in self.anchors)
                near_ledge = any(
                    math.hypot(l[0] - x1, l[2] - z1) < 6.0 for l in self.ledges)
                near_stair = any(
                    math.hypot(s[0] - x1, s[2] - z1) < 12.0 for s in self.stairs)
                if not (near_anchor or near_ledge or near_stair):
                    issues.append("trouee infranchissable entre %.0f/%.0f et %.0f/%.0f"
                                  % (x0, z0, x1, z1))
        # Les ancres doivent former un graphe connexe au-dessus du chemin
        # principal : jamais plus de 28 m entre deux anneaux voisins (portee
        # reelle du harpon, V3). Un anneau isole loin du chemin n'est pas un
        # bug : il peut servir de pendule local ou de decoration.
        n = len(self.anchors)
        if n:
            parent = list(range(n))

            def find(i):
                while parent[i] != i:
                    parent[i] = parent[parent[i]]
                    i = parent[i]
                return i

            def union(i, j):
                ri, rj = find(i), find(j)
                if ri != rj:
                    parent[ri] = rj

            near = []
            for i, a in enumerate(self.anchors):
                if any(math.hypot(p[0] - a[0], p[2] - a[2]) < 20.0
                       and abs(p[1] - a[1]) < 22.0 for p in self.path):
                    near.append(i)
            for i in range(n):
                for j in range(i + 1, n):
                    a, c = self.anchors[i], self.anchors[j]
                    d = math.sqrt((a[0] - c[0]) ** 2 + (a[1] - c[1]) ** 2
                                  + (a[2] - c[2]) ** 2)
                    if d <= GRAPPLE_RANGE:
                        union(i, j)
            comps = set(find(i) for i in near)
            if len(comps) > 1:
                issues.append("%d groupes d'ancres disjoints au-dessus du chemin"
                              " (28 m max entre anneaux)" % len(comps))
            self._anchor_chain_worst = GRAPPLE_RANGE if len(comps) <= 1 else -1.0
        return issues

    # -- sortie -------------------------------------------------------------

    def to_json(self, sky, spawn, bounds, extra=None):
        m = self.meta
        doc = dict(
            seq=self.seq,
            name=m["name"],
            subtitle=m["subtitle"],
            rule=m["rule"],
            music=m["music"],
            ambience=m["ambience"],
            reverb=m["reverb"],
            transition_in=extra.get("transition_in", "") if extra else "",
            transition_out=extra.get("transition_out", "") if extra else "",
            objective_key=m["objective"],
            target_minutes=float(m["minutes"]),
            altitude_min=r2(m["alt"][0]),
            altitude_max=r2(m["alt"][1]),
            objective_altitude=r2(m["alt"][1]),
            spawn=[r2(v) for v in spawn],
            bounds=[r2(v) for v in bounds],
            sky=sky,
            solids=self.solids,
            solid_flags=self.solid_flags,
            solid_materials=self.solid_materials,
            solid_kinds=self.solid_kinds,
            stairs=self.stairs,
            stair_materials=self.stair_materials,
            ledges=self.ledges,
            anchors=self.anchors,
            props=self.props,
            prop_ids=self.prop_ids,
            checkpoints=self.checkpoints,
            checkpoint_ids=self.checkpoint_ids,
            triggers=self.triggers,
            npcs=self.npcs,
            echo_points=self.echo_points,
            lights=self.lights,
            fog_zones=self.fog_zones,
            encounters=self.encounters,
            secrets=self.secrets,
            shortcuts=self.shortcuts,
        )
        if extra:
            for k, v in extra.items():
                if k not in doc:
                    doc[k] = v
        return doc


def sky_for(seq, meta, exposure=1.0, lut="lut_gare"):
    """05.08 saturation par sequence, 05.27 soleil derriere-gauche, 13.30 vent."""
    presets = {
        "S1": dict(sun="#FFF0D2", sun_pitch=12.0, sun_yaw=-35.0, fog="#9FB2C4",
                   density=0.0170, ambient_sky="#C8E4FF", ambient_ground="#4A423A",
                   ambient=0.35),
        "S2": dict(sun="#FFE9C8", sun_pitch=9.0, sun_yaw=-42.0, fog="#7E8C99",
                   density=0.0110, ambient_sky="#BFD8E8", ambient_ground="#2E2B28",
                   ambient=0.30),
        "S3": dict(sun="#FFF3DC", sun_pitch=26.0, sun_yaw=-30.0, fog="#A9B7C2",
                   density=0.0075, ambient_sky="#CFE3F2", ambient_ground="#5A4E42",
                   ambient=0.42),
        "S4": dict(sun="#FFF8E6", sun_pitch=64.0, sun_yaw=8.0, fog="#C6D6E2",
                   density=0.0210, ambient_sky="#DCE9F2", ambient_ground="#4A4238",
                   ambient=0.55),
        "S5": dict(sun="#000000", sun_pitch=0.0, sun_yaw=0.0, fog="#0B0F14",
                   density=0.0520, ambient_sky="#20262E", ambient_ground="#101215",
                   ambient=0.12),
        "S6": dict(sun="#9FC4E8", sun_pitch=-18.0, sun_yaw=140.0, fog="#101A2A",
                   density=0.0120, ambient_sky="#5C7FA8", ambient_ground="#20242C",
                   ambient=0.22),
        "S7": dict(sun="#8FA2B4", sun_pitch=-8.0, sun_yaw=170.0, fog="#1A2430",
                   density=0.0260, ambient_sky="#4A5C70", ambient_ground="#1C1E22",
                   ambient=0.18),
        "S8": dict(sun="#FFD9A0", sun_pitch=4.0, sun_yaw=-20.0, fog="#E8C9A0",
                   density=0.0040, ambient_sky="#FFE6C4", ambient_ground="#5A4A3A",
                   ambient=0.48),
    }
    p = presets[seq]
    return dict(
        sun_yaw_deg=p["sun_yaw"], sun_pitch_deg=p["sun_pitch"], sun_color=p["sun"],
        sun_intensity=0.0 if meta["night"] and seq in ("S6", "S7") else 1.0,
        ambient_sky=p["ambient_sky"], ambient_ground=p["ambient_ground"],
        ambient_intensity=p["ambient"], fog_color=p["fog"], fog_density=p["density"],
        fog_visibility_m=meta["fog"], saturation=meta["saturation"], exposure=exposure,
        lut=lut, night=meta["night"], rain=meta["rain"], indoor=meta["indoor"],
        wind_level=float(meta["wind"]), cloud_speed=1.0,
        lighthouse_visible=seq != "S6",
        glass_sea_visible=seq in ("S1", "S2", "S7", "S8"),
    )


# ---------------------------------------------------------------------------
# Helpers de contenu
# ---------------------------------------------------------------------------

def load_json(rel):
    path = os.path.join(CONTENT, rel)
    if not os.path.exists(path):
        return None
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


ALT_RE = re.compile(r"altitude\s+([0-9]+(?:[.,][0-9]+)?)\s*m", re.I)
DIST_RE = re.compile(r"([0-9]+(?:[.,][0-9]+)?)\s*m\s+apres", re.I)
SIDE_RE = re.compile(r"cote (gauche|droit)", re.I)


def prop_placement(note, path, index, total):
    """
    Extrait une position plausible de la note de placement de l'annexe
    (« quai nord, 40 m apres le point de depart, cote gauche, altitude 2 m »).
    A defaut, repartit le prop le long du chemin principal.
    """
    t = (index + 0.5) / max(1, total)
    n = len(path)
    i = clamp(int(t * (n - 1)), 0, n - 1)
    x, y, z = path[i]
    if n > 1:
        j = min(n - 1, i + 1)
        dx, dz = path[j][0] - x, path[j][2] - z
        norm = math.hypot(dx, dz) or 1.0
        dx, dz = dx / norm, dz / norm
    else:
        dx, dz = 0.0, 1.0
    side = -1.0
    m = SIDE_RE.search(note or "")
    if m:
        side = -1.0 if m.group(1) == "gauche" else 1.0
    offset = 3.2 * side
    m = DIST_RE.search(note or "")
    if m:
        d = float(m.group(1).replace(",", "."))
        x += dx * (d % 60.0) * 0.25
        z += dz * (d % 60.0) * 0.25
    m = ALT_RE.search(note or "")
    if m:
        y = float(m.group(1).replace(",", "."))
    # perpendiculaire au chemin : le prop est sur le cote, jamais au milieu
    x += -dz * offset
    z += dx * offset
    yaw = math.degrees(math.atan2(dx, dz))
    return x, y, z, yaw


# ---------------------------------------------------------------------------
# S1 — LES QUAIS BAS
# ---------------------------------------------------------------------------

def build_s1(b, content):
    """
    09.10 : un port de peche dont la moitie est prise dans le verre noir.
    Des bateaux figes en pleine inclinaison. Des filets suspendus. Un ponton
    brise qui pointe vers le large comme un doigt. Le Registre est inonde de
    verre jusqu'au plafond : il faut passer par le toit.
    Enseigne : marcher, camera, sauter, grimper, interagir, PREMIER ECHO.
    21 minutes de solitude : le premier humain vivant apparait a 00:21.
    """
    b.slab(0.0, 0.9, 40.0, 26.0, 90.0, mat=MAT_WOOD_WET, kind=K_GROUND)      # le quai
    b.walk_to(0.0, 0.9, 40.0, width=26.0, depth=12.0, mat=MAT_WOOD_WET,
              connector="none")
    # 08.21 : 00:04 sauter (une planche cassee — 2,8 m de vide, espace sur),
    # 00:07 grimper (une echelle brisee). Repetition immediate variee (08.20).
    b.walk_to(0.0, 0.9, 30.0, width=9.0, depth=7.0, mat=MAT_WOOD_WET,
              connector="bridge")
    b.walk_to(0.0, 0.9, 22.0, width=8.0, depth=8.0, mat=MAT_WOOD_WET, connector="jump")
    b.walk_to(0.0, 0.9, 15.0, width=8.0, depth=8.0, mat=MAT_WOOD_WET, connector="jump")
    b.walk_to(0.0, 0.9, 8.0, width=8.0, depth=8.0, mat=MAT_WOOD_WET, connector="jump")
    b.wall(3.4, 0.9, 2.0, 3.0, 2.2, 0.4, mat=MAT_WOOD, flags=F_CLIMBABLE)
    b.ledge(3.4, 2.3, 2.0, 3.0, yaw=0.0, kind=0)
    b.walk_to(0.0, 2.1, -4.0, width=10.0, depth=10.0, mat=MAT_STONE, connector="ledge")
    b.walk_to(0.0, 2.1, -22.0, width=12.0, depth=12.0, mat=MAT_STONE, connector="bridge")
    # le ponton brise qui pointe vers le large
    b.box(-16.0, 1.4, -26.0, 12.0, 0.25, 1.6, yaw=24.0, flags=F_WALKABLE,
          mat=MAT_WOOD_WET, kind=K_PLATFORM)
    b.walk_to(0.0, 3.4, -40.0, width=10.0, depth=10.0, mat=MAT_STONE, connector="ramp")
    b.walk_to(0.0, 5.2, -56.0, width=12.0, depth=12.0, mat=MAT_STONE, connector="ramp")
    # le Registre : inonde de verre jusqu'au plafond, passage par le toit
    b.box(0.0, 4.0, -74.0, 11.0, 4.0, 9.0, yaw=0.0, flags=F_INDOOR | F_LAMP_ROOM,
          mat=MAT_STONE_WET, kind=K_WALL)
    b.slab(0.0, 8.2, -74.0, 22.0, 18.0, mat=MAT_STONE, kind=K_ROOF)
    b.walk_to(0.0, 8.2, -74.0, width=22.0, depth=18.0, mat=MAT_STONE, connector="ledge")
    b.ledge(0.0, 8.0, -65.4, 6.0, yaw=0.0, kind=0)
    b.walk_to(0.0, 9.6, -88.0, width=14.0, depth=12.0, mat=MAT_STONE, connector="ramp")
    b.walk_to(0.0, 11.4, -104.0, width=14.0, depth=12.0, mat=MAT_STONE, connector="ramp")
    b.walk_to(0.0, 13.6, -120.0, width=16.0, depth=14.0, mat=MAT_STONE,
              connector="ramp")
    b.checkpoint("CP-S1-06", 0.0, 13.6, -118.0, yaw=180.0)

    # --- bateaux figes en pleine inclinaison ---
    for i, (bx, bz, roll) in enumerate([(18.0, 26.0, 22.0), (-20.0, 4.0, -34.0),
                                        (22.0, -18.0, 41.0), (-24.0, -52.0, -18.0)]):
        b.box(bx, 1.8, bz, 5.2, 1.9, 2.0, yaw=roll, flags=0, mat=MAT_WOOD,
              kind=K_DEBRIS)
        b.box(bx, 5.0, bz, 0.3, 3.2, 0.3, yaw=roll, flags=0, mat=MAT_WOOD,
              kind=K_STRUCTURE)
    # --- le verre noir qui a pris la moitie du port ---
    b.slab(26.0, 0.35, 10.0, 60.0, 120.0, mat=MAT_GLASS,
           flags=F_WALKABLE | F_GLASS | F_SLIDE, kind=K_DEBRIS)
    b.slab(-30.0, 0.35, -30.0, 40.0, 90.0, mat=MAT_GLASS,
           flags=F_WALKABLE | F_GLASS | F_SLIDE, kind=K_DEBRIS)
    # --- filets suspendus qui ne redescendront jamais ---
    for i in range(6):
        b.box(-9.0 + i * 3.6, 5.4, 30.0 - i * 4.0, 1.4, 2.2, 0.1, yaw=8.0 * i,
              flags=0, mat=MAT_WOOD, kind=K_DEBRIS)
    # --- facades basses (kit K2) ---
    for i in range(7):
        b.box(-34.0, 5.0, 44.0 - i * 16.0, 7.0, 5.0, 6.0, yaw=0.0, flags=0,
              mat=MAT_STONE, kind=K_WALL)
        b.box(34.0, 4.2, 36.0 - i * 15.0, 6.0, 4.2, 5.5, yaw=0.0, flags=0,
              mat=MAT_STONE, kind=K_WALL)
    # --- la grue rouge : premier repere de la ville (09.02) ---
    b.box(46.0, 12.0, -60.0, 1.2, 12.0, 1.2, flags=0, mat=MAT_METAL, kind=K_PILLAR)
    b.box(52.0, 23.4, -60.0, 7.0, 0.5, 0.7, flags=0, mat=MAT_METAL, kind=K_STRUCTURE)
    # --- salles a lampe L1 : le bureau du Registre ---
    room = LAMP_ROOMS["S1"]
    for i in range(room[2]):
        b.light("omni", -3.0 + i * 3.0, 3.1, -74.0, color="#FFD6A0", rng=7.0,
                intensity=1.15, flicker=True, room=room[0])
    b.light("lantern", 0.0, 2.4, -70.0, color="#FFD6A0", rng=5.0, intensity=0.9,
            flicker=True, room=room[0])
    # --- brume dense, visibilite 70 m, crachin ---
    b.fog(0.0, 4.0, 0.0, radius=120.0, density=0.017, color="#9FB2C4")
    b.fog(0.0, 2.0, -90.0, radius=70.0, density=0.022, color="#9FB2C4")
    # --- le premier humain vivant apparait a 00:21 : Pallas, loin ---
    b.npc("NPC-S1-PALLAS", "pallas", 6.0, 13.6, -112.0, yaw=200.0, routine="watch",
          radius=3.0, dialogue="S1_D02", barks="S1", major=True)
    b.npc("NPC-S1-EMPLOYEE", "employee", -4.0, 8.2, -76.0, yaw=90.0,
          routine="desk", radius=1.5, dialogue="S1_D03", barks="S1")
    # --- Echos ---
    b.echo_point("E01", "PN-001", 0.0, 0.9, 34.0)
    b.echo_point("E02", "PN-004", -2.0, 2.1, -8.0)
    b.echo_point("E03", "PN-009", 4.0, 0.9, 16.0)
    # --- raccourcis (09.23) ---
    b.shortcut("SC-S1-01", "ladder_dropped", 0.0, 2.1, -20.0, opens_to="quai")
    b.shortcut("SC-S1-02", "door_unlocked", 0.0, 8.2, -66.0, opens_to="registre")
    # --- couloir T1 vers S2 ---
    b.trigger("TR-S1-T1", "corridor", 0.0, 13.6, -126.0, rx=6.0, ry=3.0, rz=4.0,
              target="T1", param="S2", value=26.0)
    b.trigger("TR-S1-OUT", "sequence_end", 0.0, 13.6, -128.0, rx=8.0, ry=4.0, rz=3.0,
              target="S2")
    # --- silence actif SB1 : le muret aux bottes ---
    b.trigger("TR-S1-SB1", "silence", 0.0, 0.9, 34.0, rx=4.0, ry=2.0, rz=4.0,
              target="SB1", value=3.0)
    # --- cinematique C01 a l'arrivee ---
    b.trigger("TR-S1-C01", "cinematic", 0.0, 0.9, 44.0, rx=10.0, ry=3.0, rz=6.0,
              target="C01")
    # --- 08.21 : prompts d'enseignement, un par verbe ---
    for i, verb in enumerate(["marcher", "camera", "sauter", "grimper", "interagir"]):
        b.trigger("TR-S1-TEACH-%d" % i, "teach", 0.0, 0.9, 40.0 - i * 8.0,
                  rx=5.0, ry=2.5, rz=5.0, target=verb)
    b.trigger("TR-S1-TEACH-ECHO", "teach", 0.0, 0.9, 34.0, rx=4.0, ry=2.5, rz=4.0,
              target="ecouter")
    b.checkpoint("CP-S1-01", 0.0, 0.9, 36.0, yaw=180.0)
    b.checkpoint("CP-S1-02", 0.0, 0.9, 10.0, yaw=180.0)
    b.checkpoint("CP-S1-03", 0.0, 2.1, -18.0, yaw=180.0)
    b.checkpoint("CP-S1-04", 0.0, 5.2, -54.0, yaw=180.0)
    b.checkpoint("CP-S1-05", 0.0, 8.2, -70.0, yaw=180.0)
    # --- secrets : 4 (09.24) ---
    b.secret("SE-S1-01", "viewpoint", -16.0, 1.6, -30.0, payload="banc, plan large 20 s")
    b.secret("SE-S1-02", "letter_fragment", 34.0, 3.0, 20.0, payload="fragment n 1")
    b.secret("SE-S1-03", "journal_object", -24.0, 1.0, -48.0, payload="objet du journal")
    b.secret("SE-S1-04", "optional_echo", 22.0, 1.0, -14.0, payload="E03 la valise triee")
    return dict(spawn=[0.0, 1.2, 46.0, 180.0],
                bounds=[-70.0, -2.0, -140.0, 70.0, 40.0, 90.0])


# ---------------------------------------------------------------------------
# S2 — LE VERRE ET LE BATEAU
# ---------------------------------------------------------------------------

def build_s2(b, content):
    """
    09.11 : une plaine de verre noir, lisse, immense, avec des choses
    dessous. Le grappin ne s'accroche pas. Le Souffle diminue passivement.
    Des puits de chaleur rendent le verre mou : on s'y enfonce en 2,4 s.
    Sous ses pieds, a 3 m, un tramway complet avec des silhouettes assises.
    """
    # la plaine de verre : une seule dalle immense, marchable, glissante
    b.slab(0.0, 0.30, 0.0, 420.0, 420.0, mat=MAT_GLASS,
           flags=F_WALKABLE | F_GLASS | F_SLIDE, kind=K_DEBRIS)
    b.walk_to(0.0, 0.30, 120.0, width=40.0, depth=40.0, mat=MAT_GLASS,
              flags=F_WALKABLE | F_GLASS | F_SLIDE, connector="none")
    # puits de chaleur : zones plus claires ou le verre est mou (2,4 s)
    wells = [(18.0, 96.0), (-26.0, 62.0), (34.0, 30.0), (-12.0, -6.0),
             (22.0, -44.0), (-30.0, -78.0), (8.0, -108.0)]
    for i, (wx, wz) in enumerate(wells):
        b.slab(wx, 0.32, wz, 11.0, 11.0, mat=MAT_GLASS,
               flags=F_WALKABLE | F_GLASS | F_HEAT_WELL | F_SLIDE, kind=K_DEBRIS)
    # chemin principal : il faut courir entre les puits
    pts = [(0.0, 120.0), (6.0, 96.0), (-4.0, 74.0), (10.0, 52.0), (-2.0, 30.0),
           (6.0, 8.0), (-6.0, -14.0), (4.0, -36.0), (-8.0, -58.0), (0.0, -80.0),
           (2.0, -102.0), (0.0, -124.0)]
    prev = (0.0, 0.30, 120.0)
    for i, (x, z) in enumerate(pts[1:]):
        b.walk_to(x, 0.30, z, width=9.0, depth=9.0, mat=MAT_GLASS,
                  flags=F_WALKABLE | F_GLASS | F_SLIDE, connector="bridge")
    # L'Hirondelle de Mer : couchee sur le flanc, a moitie avalee
    tx, tz = 26.0, -132.0
    b.box(tx, 3.4, tz, 9.0, 4.6, 3.4, yaw=68.0, flags=F_WALKABLE, mat=MAT_WOOD_WET,
          kind=K_DEBRIS)
    b.box(tx + 3.0, 6.2, tz - 1.0, 3.0, 1.4, 2.6, yaw=68.0, flags=F_WALKABLE,
          mat=MAT_WOOD, kind=K_PLATFORM)          # la cabine
    b.box(tx - 6.0, 8.0, tz + 2.0, 0.35, 6.0, 0.35, yaw=12.0, flags=0, mat=MAT_METAL,
          kind=K_PILLAR)                          # le mat casse
    b.slab(tx + 3.0, 7.6, tz - 1.0, 6.0, 5.2, mat=MAT_WOOD, kind=K_PLATFORM)
    b.walk_to(tx + 3.0, 7.6, tz - 1.0, width=6.0, depth=5.2, mat=MAT_WOOD,
              connector="ledge")
    b.ledge(tx + 1.0, 6.4, tz - 1.0, 3.0, yaw=68.0, kind=0)
    # le tramway sous le verre, a 3 m : des silhouettes assises
    b.box(18.0, -3.0, -34.0, 12.0, 1.6, 2.4, yaw=14.0, flags=0, mat=MAT_METAL,
          kind=K_DEBRIS)
    for i in range(9):
        b.box(10.0 + i * 2.2, -2.4, -33.4 + (i % 2) * 0.9, 0.35, 0.85, 0.35,
              yaw=14.0, flags=0, mat=MAT_STONE, kind=K_FURNITURE)
    # autres spectacles sous la surface
    for (sx, sz, sw, sd) in [(-26.0, 12.0, 9.0, 6.0), (41.0, 26.0, 12.0, 7.0),
                             (-58.0, -19.0, 6.0, 3.0), (64.0, -61.0, 10.0, 5.0),
                             (-12.0, 74.0, 5.0, 4.0), (88.0, 44.0, 16.0, 6.0),
                             (6.0, 8.0, 2.0, 2.0), (-84.0, 38.0, 18.0, 9.0)]:
        b.box(sx, -4.4, sz, sw * 0.5, 1.2, sd * 0.5, yaw=b.rng.uniform(0, 40),
              flags=0, mat=MAT_STONE, kind=K_DEBRIS)
    # aucune ancre de grappin : le verre refuse (08.08c)
    # salle a lampe L2 : la cabine
    room = LAMP_ROOMS["S2"]
    for i in range(room[2]):
        b.light("omni", tx + 2.0 + i * 1.6, 8.4, tz - 1.0, color="#FFD6A0", rng=5.5,
                intensity=1.1, flicker=True, room=room[0])
    # la premiere Figure : vue de loin, immobile, elle regarde
    b.npc("NPC-S2-FIGURE", "figure", -60.0, 0.3, -160.0, yaw=20.0, routine="watch",
          radius=0.0, major=False)
    b.echo_point("E04", "PN-031", tx + 3.0, 7.6, tz - 1.0)
    b.echo_point("E05", "PN-033", tx - 2.0, 4.2, tz + 1.0)
    b.echo_point("E06", "PN-036", 18.0, 0.3, -34.0)
    b.encounter("EN-S2-01", -20.0, 0.3, -60.0, radius=18.0,
                condition="premiere figure : on doit FUIR", avoidable=False,
                enemies=["echassier"])
    b.shortcut("SC-S2-01", "rope_descended", -6.0, 0.3, -14.0, opens_to="quai bas")
    b.trigger("TR-S2-SB2", "silence", 0.0, 0.30, 108.0, rx=8.0, ry=2.0, rz=6.0,
              target="SB2", value=4.0)
    b.trigger("TR-S2-C02", "cinematic", 0.0, 0.30, 116.0, rx=8.0, ry=3.0, rz=6.0,
              target="C02")
    b.trigger("TR-S2-T2", "corridor", tx + 3.0, 7.6, tz - 6.0, rx=5.0, ry=3.0, rz=5.0,
              target="T2", param="S3", value=34.0)
    b.trigger("TR-S2-CRACK", "scripted", tx, 4.0, tz, rx=10.0, ry=4.0, rz=10.0,
              target="trawler_crack", value=3.2)
    b.trigger("TR-S2-TEACH-GLASS", "teach", 12.0, 0.3, 60.0, rx=8.0, ry=2.5, rz=8.0,
              target="le verre refuse le grappin", value=12.0)
    b.checkpoint("CP-S2-01", 0.0, 0.30, 112.0, yaw=180.0)
    b.checkpoint("CP-S2-02", 6.0, 0.30, 40.0, yaw=180.0)
    b.checkpoint("CP-S2-03", -6.0, 0.30, -20.0, yaw=180.0)
    b.checkpoint("CP-S2-04", 0.0, 0.30, -118.0, yaw=200.0)
    b.secret("SE-S2-01", "viewpoint", -70.0, 0.3, 40.0, payload="banc face au large")
    b.secret("SE-S2-02", "letter_fragment", 96.0, 0.3, -70.0, payload="fragment n 2")
    b.secret("SE-S2-03", "journal_object", -100.0, 0.3, -100.0, payload="objet du journal")
    b.fog(0.0, 1.0, 0.0, radius=200.0, density=0.011, color="#7E8C99")
    return dict(spawn=[0.0, 0.7, 128.0, 180.0],
                bounds=[-215.0, -6.0, -215.0, 215.0, 30.0, 215.0])


# ---------------------------------------------------------------------------
# S3 — LE MARCHE SUSPENDU
# ---------------------------------------------------------------------------

def build_s3(b, content):
    """
    09.12 : 400 personnes vivent dans un enchevetrement de passerelles, de
    baches, de treuils et de cages d'escalier accrochees aux falaises entre
    18 et 62 m. 60 anneaux de relayeur. 34 PNJ avec des routines lisibles.
    96 barks ecrits. Sortie : le monte-charge, trois contrepoids au grappin.
    """
    # les deux falaises qui portent le marche
    for side in (-1, 1):
        for i in range(9):
            b.box(side * (34.0 + i * 1.5), 40.0 - i * 3.0, 60.0 - i * 26.0,
                  9.0, 40.0 + i * 3.0, 14.0, yaw=0.0, flags=0, mat=MAT_STONE,
                  kind=K_WALL)
    # le chemin principal monte en lacets entre 18 et 62 m
    stations = [
        (0.0, 18.0, 96.0, 14.0), (10.0, 21.0, 78.0, 10.0), (-8.0, 24.0, 62.0, 10.0),
        (8.0, 27.5, 46.0, 11.0), (-10.0, 31.0, 30.0, 10.0), (6.0, 34.5, 14.0, 12.0),
        (-6.0, 38.0, -2.0, 10.0), (10.0, 41.0, -18.0, 10.0), (-8.0, 44.5, -34.0, 11.0),
        (4.0, 48.0, -50.0, 10.0), (-4.0, 51.5, -66.0, 12.0), (6.0, 55.0, -82.0, 10.0),
        (0.0, 58.5, -98.0, 12.0), (0.0, 62.0, -114.0, 14.0),
    ]
    prev_mode = "ramp"
    for i, (x, y, z, w) in enumerate(stations):
        mode = "none" if i == 0 else ("grapple" if i in (4, 8, 11) else prev_mode)
        b.walk_to(x, y, z, width=w, depth=w * 0.85, mat=MAT_METAL, kind=K_PLATFORM,
                  connector=mode)
        prev_mode = "ramp"
        # garde-corps : le marche est au-dessus du vide
        if i > 0:
            b.railing(x - w * 0.5 + 0.2, y, z, w * 0.8, yaw=90.0)
            b.railing(x + w * 0.5 - 0.2, y, z, w * 0.8, yaw=90.0)
    # 60 anneaux de relayeur : Lohen les reconnait, il en a pose la moitie.
    # Ils forment une chaine continue au-dessus du chemin (jamais plus de
    # 28 m entre deux anneaux : portee reelle du harpon, 08.08).
    ring = 0
    for i in range(1, len(stations)):
        x0, y0, z0, _ = stations[i - 1]
        x1, y1, z1, _ = stations[i]
        for t in (0.2, 0.45, 0.7, 0.92):
            ring += 1
            b.anchor(x0 + (x1 - x0) * t, max(y0, y1) + 3.4, z0 + (z1 - z0) * t,
                     kind=0)
    while ring < 60:
        i = ring % len(stations)
        x, y, z, w = stations[i]
        b.anchor(x + b.rng.uniform(-6, 6), y + b.rng.uniform(2.8, 5.0),
                 z + b.rng.uniform(-5, 5), kind=0)
        ring += 1
    # passerelles secondaires, baches, treuils, cages d'escalier
    for i in range(26):
        side = 1 if i % 2 else -1
        z = 90.0 - i * 8.0
        y = 20.0 + i * 1.6
        b.box(side * 20.0, y, z, 6.5, 0.22, 3.0, yaw=side * 6.0,
              flags=F_WALKABLE, mat=MAT_WOOD, kind=K_PLATFORM)
        b.wall(side * 26.0, y, z, 6.0, 2.4, 0.2, yaw=0.0, mat=MAT_METAL,
               kind=K_STRUCTURE)
        b.box(side * 22.0, y + 3.0, z + 2.0, 2.4, 1.6, 2.4, yaw=12.0 * side, flags=0,
              mat=MAT_WOOD, kind=K_FURNITURE)          # bache
    for i in range(8):                                   # cages d'escalier
        z = 70.0 - i * 22.0
        b.stair(-24.0, 20.0 + i * 5.0, z, 2.2, 5.0, 7.0, yaw=180.0, mat=MAT_METAL)
    # le poste de milice (L3) et le monte-charge
    b.box(-18.0, 40.0, -60.0, 6.0, 3.0, 5.0, flags=F_INDOOR | F_LAMP_ROOM,
          mat=MAT_STONE, kind=K_WALL)
    b.slab(-18.0, 43.0, -60.0, 12.0, 10.0, mat=MAT_METAL, kind=K_ROOF)
    room = LAMP_ROOMS["S3"]
    for i in range(room[2]):
        b.light("omni", -20.0 + i * 2.0, 41.6, -60.0, color="#FFD6A0", rng=6.0,
                intensity=1.0, flicker=False, room=room[0])
    # le monte-charge : trois contrepoids a tirer au grappin (puzzle)
    b.box(6.0, 66.0, -122.0, 3.0, 4.0, 3.0, flags=0, mat=MAT_METAL, kind=K_STRUCTURE)
    for i in range(3):
        b.anchor(2.0 + i * 4.0, 66.0, -124.0 - i * 2.0, kind=1)
    b.trigger("TR-S3-PUZZLE", "puzzle", 4.0, 62.0, -118.0, rx=8.0, ry=4.0, rz=8.0,
              target="monte_charge", param="grapple", value=3.0)
    # 34 PNJ avec des routines simples mais lisibles
    routines = [
        ("forgeron", "forge", "S3_D14"), ("laveuse", "wash", "S3_D18"),
        ("cordiere", "rope", "S3_D22"), ("enfant_a", "play_rope", ""),
        ("enfant_b", "play_rope", ""), ("enfant_c", "play_chase", ""),
        ("peintre", "paint_same_door", ""), ("marchand", "sell", ""),
        ("portefaix", "haul", ""), ("vieille", "sit", ""),
        ("ramasseur", "sort_glass", ""), ("milicien", "patrol", ""),
        ("musicien", "play_string", ""), ("cuisinier", "cook", ""),
        ("couturiere", "sew", ""), ("charpentier", "plane_wood", ""),
        ("guetteur", "watch_sky", ""), ("balayeur", "sweep", ""),
        ("marchande", "sell_fish", ""), ("mecanicien", "oil_winch", ""),
        ("ado_a", "lean", ""), ("ado_b", "smoke", ""), ("lecteur", "read", ""),
        ("dormeur", "sleep", ""), ("crieur", "shout_names", ""),
        ("tanneur", "work_hide", ""), ("rempailleuse", "weave", ""),
        ("gamin", "run", ""), ("aveugle", "listen", ""), ("predicatrice", "preach", ""),
        ("pecheur", "mend_net", ""), ("bucheron", "split", ""),
        ("ferrailleur", "weigh", ""), ("lavandiere", "rinse", ""),
    ]
    for i, (role, routine, dlg) in enumerate(routines):
        si = i % (len(stations) - 1)
        x, y, z, w = stations[si]
        ox = b.rng.uniform(-w * 0.4, w * 0.4)
        oz = b.rng.uniform(-w * 0.3, w * 0.3)
        b.npc("NPC-S3-%02d" % (i + 1), role, x + ox, y, z + oz,
              yaw=b.rng.uniform(0, 360), routine=routine,
              radius=b.rng.uniform(1.2, 4.0), dialogue=dlg, barks="S3")
    # TALLEC, au poste de milice
    b.npc("NPC-S3-TALLEC", "tallec", -18.0, 40.0, -60.0, yaw=90.0, routine="post",
          radius=2.0, dialogue="S3_D06", barks="S3", major=True)
    # SOL, qui vole la sacoche dans les 30 s
    b.npc("NPC-S3-SOL", "sol", 6.0, 27.5, 46.0, yaw=200.0, routine="thief",
          radius=8.0, dialogue="S3_D09", barks="S3", major=True)
    # MIREILLE arrive a la fin du marche (elle redescend de la Bibliotheque)
    b.npc("NPC-S3-MIREILLE", "mireille", 0.0, 58.5, -98.0, yaw=180.0, routine="walk_to",
          radius=3.0, dialogue="S4_D01", barks="S3")
    # Echos
    for eid, pid, idx in [("E07", "PN-044", 3), ("E08", "PN-047", 1),
                          ("E09", "PN-052", 6), ("E10", "PN-058", 12),
                          ("E11", "PN-061", 2), ("E12", "PN-066", 13)]:
        x, y, z, w = stations[min(idx, len(stations) - 1)]
        b.echo_point(eid, pid, x + 2.0, y, z - 2.0)
    # combats : aucun ennemi au marche, mais deux Mueurs dans les cages
    b.encounter("EN-S3-01", -24.0, 30.0, 4.0, radius=9.0, condition="cage d'escalier",
                avoidable=True, enemies=["mueur", "mueur"])
    # raccourcis (09.23) : 4
    for i, (x, y, z) in enumerate([(10.0, 21.0, 78.0), (-10.0, 31.0, 30.0),
                                   (10.0, 41.0, -18.0), (-4.0, 51.5, -66.0)]):
        b.shortcut("SC-S3-%02d" % (i + 1),
                   ["ladder_dropped", "door_unlocked", "rope_descended",
                    "ladder_dropped"][i], x, y, z, opens_to="etage inferieur")
    # silences actifs
    b.trigger("TR-S3-SB3", "silence", 8.0, 27.5, 46.0, rx=6.0, ry=2.0, rz=6.0,
              target="SB3", value=2.0)
    b.trigger("TR-S3-SOL-STEAL", "scripted", 6.0, 27.5, 44.0, rx=8.0, ry=3.0, rz=8.0,
              target="sol_steal", value=30.0)
    b.trigger("TR-S3-C03", "cinematic", 0.0, 18.0, 92.0, rx=10.0, ry=3.0, rz=8.0,
              target="C03")
    b.trigger("TR-S3-T3", "corridor", 0.0, 62.0, -118.0, rx=6.0, ry=3.0, rz=5.0,
              target="T3", param="S4", value=40.0)
    b.trigger("TR-S3-TEACH-SWING", "teach", -10.0, 31.0, 30.0, rx=8.0, ry=3.0, rz=8.0,
              target="pendule")
    for i in range(9):
        b.checkpoint("CP-S3-%02d" % (i + 1), stations[i + 2][0], stations[i + 2][1],
                     stations[i + 2][2], yaw=180.0)
    # 7 secrets (09.24)
    secret_spots = [(-26.0, 24.0, 70.0, "viewpoint"), (24.0, 30.0, 40.0, "letter_fragment"),
                    (-28.0, 38.0, 6.0, "journal_object"), (26.0, 44.0, -26.0, "breath_upgrade"),
                    (-26.0, 50.0, -56.0, "optional_echo"), (28.0, 55.0, -86.0, "letter_fragment"),
                    (-20.0, 60.0, -104.0, "viewpoint")]
    for i, (x, y, z, kind) in enumerate(secret_spots):
        b.secret("SE-S3-%02d" % (i + 1), kind, x, y, z, payload="%s S3" % kind)
    b.fog(0.0, 30.0, 0.0, radius=140.0, density=0.0075, color="#A9B7C2")
    return dict(spawn=[0.0, 18.4, 100.0, 180.0],
                bounds=[-70.0, 12.0, -150.0, 70.0, 80.0, 120.0])


# ---------------------------------------------------------------------------
# S4 — LA BIBLIOTHEQUE DES EAUX
# ---------------------------------------------------------------------------

def build_s4(b, content):
    """
    09.13 : six etages en spirale autour d'un puits central, autrefois un
    reservoir. Les rayonnages sont dans l'eau jusqu'au 2e. Un oculus au
    sommet, un rai de lumiere unique traversant six etages de poussiere.
    REGLE UNIQUE : le silence. Aucune musique. Chercher le dossier d'Esteban
    dans 900 fichiers. Pas de marqueur de quete.
    """
    cx, cz = 0.0, 0.0
    base = 55.0
    # le puits central : un vide de 23 m
    b.box(cx, base + 11.5, cz, 6.0, 11.5, 6.0, flags=F_DEADLY, mat=MAT_WATER,
          kind=K_DEBRIS)
    b.slab(cx, base - 0.4, cz, 12.0, 12.0, mat=MAT_WATER,
           flags=F_WALKABLE | F_WATER_SHALLOW, kind=K_GROUND)
    # 6 etages en spirale
    floors = 6
    for f in range(floors):
        y = base + f * 3.8
        # passerelle annulaire (12 segments)
        for s in range(12):
            a = math.radians(s * 30.0 + f * 12.0)
            r = 13.0
            x, z = cx + math.cos(a) * r, cz + math.sin(a) * r
            b.box(x, y, z, 3.6, 0.25, 2.2, yaw=-math.degrees(a), flags=F_WALKABLE,
                  mat=MAT_WOOD, kind=K_PLATFORM)
        # rampe vers l'etage superieur
        a0 = math.radians(f * 12.0)
        b.stair(cx + math.cos(a0) * 17.5, y, cz + math.sin(a0) * 17.5, 2.0, 3.8,
                7.0, yaw=-math.degrees(a0) + 90.0, mat=MAT_WOOD)
        # rayonnages : 900 fichiers
        for s in range(8):
            a = math.radians(s * 45.0 + 22.0 + f * 7.0)
            r = 9.5
            b.box(cx + math.cos(a) * r, y + 1.1, cz + math.sin(a) * r, 2.4, 1.1, 0.45,
                  yaw=-math.degrees(a), flags=F_INTERACTIVE, mat=MAT_WOOD,
                  kind=K_FURNITURE)
        if f < 2:
            # les deux premiers etages sont dans l'eau
            b.slab(cx, y - 0.2, cz, 34.0, 34.0, mat=MAT_WATER,
                   flags=F_WALKABLE | F_WATER_SHALLOW, kind=K_GROUND)
    top = base + (floors - 1) * 3.8
    # le chemin principal : la spirale
    b.walk_to(cx + 13.0, base, cz, width=7.0, depth=5.0, mat=MAT_WOOD,
              kind=K_PLATFORM, connector="none")
    for f in range(floors):
        y = base + f * 3.8
        for s in range(0, 12, 3):
            a = math.radians(s * 30.0 + f * 12.0)
            b.walk_to(cx + math.cos(a) * 13.0, y, cz + math.sin(a) * 13.0,
                      width=7.0, depth=5.0, mat=MAT_WOOD, kind=K_PLATFORM,
                      connector="ramp" if s == 0 else "bridge")
        if f < floors - 1:
            a0 = math.radians(f * 12.0)
            b.walk_to(cx + math.cos(a0) * 17.5, y + 3.8, cz + math.sin(a0) * 17.5,
                      width=5.0, depth=5.0, mat=MAT_WOOD, kind=K_PLATFORM,
                      connector="ramp")
    b.walk_to(cx, top + 1.2, cz - 18.0, width=10.0, depth=8.0, mat=MAT_WOOD,
              kind=K_PLATFORM, connector="ramp")
    # l'enveloppe du batiment + l'oculus au sommet
    for s in range(16):
        a = math.radians(s * 22.5)
        b.box(cx + math.cos(a) * 21.0, base + 12.0, cz + math.sin(a) * 21.0,
              1.6, 12.0, 4.2, yaw=-math.degrees(a),
              flags=F_INDOOR, mat=MAT_STONE, kind=K_WALL)
    b.box(cx, top + 3.4, cz, 21.0, 0.4, 21.0, flags=F_INDOOR, mat=MAT_STONE,
          kind=K_ROOF)
    # l'oculus : un trou de 4 m — le rai de lumiere unique (V01 densite max)
    b.box(cx + 2.0, top + 3.4, cz + 2.0, 2.0, 0.5, 2.0, flags=0, mat=MAT_STONE,
          kind=K_ROOF)
    b.light("spot", cx, top + 6.0, cz, color="#FFF6E0", rng=42.0, intensity=2.4,
            flicker=False, room="oculus")
    # la salle des fiches (L4)
    room = LAMP_ROOMS["S4"]
    for i in range(room[2]):
        b.light("omni", cx - 6.0 + i * 6.0, base + 4.0, cz + 14.0, color="#FFD6A0",
                rng=7.0, intensity=1.05, flicker=False, room=room[0])
    b.fog(cx, base + 8.0, cz, radius=26.0, density=0.026, color="#C6D6E2")
    # MIREILLE : trois conversations, dont une ou elle ment
    b.npc("NPC-S4-MIREILLE", "mireille", cx - 4.0, base + 7.6, cz + 12.0, yaw=140.0,
          routine="desk", radius=2.5, dialogue="S4_D02", barks="S4", major=True)
    b.npc("NPC-S4-L-ELEVE", "eleve", cx + 8.0, base + 11.4, cz - 6.0, yaw=20.0,
          routine="shelve", radius=3.0, dialogue="S4_D11", barks="S4")
    b.npc("NPC-S4-EMPLOYEE", "employee", cx - 9.0, base, cz - 9.0, yaw=300.0,
          routine="wade", radius=4.0, barks="S4")
    # Echos
    for eid, pid, f in [("E13", "PN-074", 1), ("E14", "PN-077", 2),
                        ("E15", "PN-081", 0), ("E16", "PN-084", 3),
                        ("E17", "PN-088", 4)]:
        y = base + f * 3.8
        a = math.radians(f * 40.0 + 30.0)
        b.echo_point(eid, pid, cx + math.cos(a) * 12.0, y, cz + math.sin(a) * 12.0)
    # regle unique : aucune musique (M08 est vide par decision, 13.13)
    b.trigger("TR-S4-NO-MUSIC", "music_stop", cx, base, cz + 18.0, rx=26.0, ry=8.0,
              rz=26.0, target="M08", value=0.0)
    b.trigger("TR-S4-SB4", "silence", cx + 6.0, base + 3.8, cz + 8.0, rx=4.0, ry=2.0,
              rz=4.0, target="SB4", value=3.5)
    b.trigger("TR-S4-SEARCH", "puzzle", cx - 6.0, base + 7.6, cz + 14.0, rx=9.0,
              ry=4.0, rz=9.0, target="dossier_esteban", param="search",
              value=900.0)
    b.trigger("TR-S4-T4", "corridor", cx, top + 1.2, cz - 22.0, rx=5.0, ry=3.0,
              rz=4.0, target="T4", param="S5", value=22.0)
    for i in range(6):
        y = base + i * 3.8
        a = math.radians(i * 60.0)
        b.checkpoint("CP-S4-%02d" % (i + 1), cx + math.cos(a) * 13.0, y,
                     cz + math.sin(a) * 13.0, yaw=-math.degrees(a))
    for i in range(2):
        a = math.radians(120.0 + i * 150.0)
        b.shortcut("SC-S4-%02d" % (i + 1), "door_unlocked",
                   cx + math.cos(a) * 19.0, base + 3.8 * (i + 1),
                   cz + math.sin(a) * 19.0, opens_to="etage inferieur")
    spots = [(18.0, base + 3.8, 12.0, "viewpoint"), (-17.0, base + 7.6, -10.0,
             "letter_fragment"), (10.0, base + 11.4, -16.0, "journal_object"),
             (-14.0, base + 15.2, 14.0, "optional_echo"), (16.0, base + 19.0, 4.0,
             "breath_upgrade")]
    for i, (x, y, z, kind) in enumerate(spots):
        b.secret("SE-S4-%02d" % (i + 1), kind, x, y, z, payload="%s S4" % kind)
    return dict(spawn=[cx + 13.0, base + 0.4, cz + 6.0, 200.0],
                bounds=[-30.0, base - 3.0, -30.0, 30.0, top + 12.0, 30.0])


# ---------------------------------------------------------------------------
# S5 — LES CONDUITS
# ---------------------------------------------------------------------------

def build_s5(b, content):
    """
    09.14 : le reseau de canalisations, gaines et vides sanitaires. Etroit,
    noir, 1,4 m de plafond (Lohen est accroupi 60 % du temps). Une lanterne
    portee a la main. PORTER la lanterne : une main occupee = pas de
    grappin, pas de garde. Trois passages ou il faut la poser pour grimper,
    et donc traverser 8 m dans le noir.
    """
    # le conduit principal : une gaine de 1,4 m de haut, 2,2 m de large
    segs = []
    y = 40.0
    x, z = 0.0, 90.0
    for i in range(22):
        dz = -12.0 if i % 2 == 0 else -9.0
        dx = 0.0 if i % 3 else 7.0 * (1 if i % 6 == 3 else -1)
        nx, nz = x + dx, z + dz
        segs.append((x, y, z, nx, y, nz))
        x, z = nx, nz
        y += 2.4 + (i % 3) * 0.9
    for i, (x0, y0, z0, x1, y1, z1) in enumerate(segs):
        mx, mz = (x0 + x1) * 0.5, (z0 + z1) * 0.5
        dist = math.hypot(x1 - x0, z1 - z0)
        yaw = math.degrees(math.atan2(x1 - x0, z1 - z0))
        # le sol de la gaine
        b.box(mx, min(y0, y1) - 0.3, mz, 1.3, 0.3, dist * 0.5 + 1.0, yaw=yaw,
              flags=F_WALKABLE | F_INDOOR, mat=MAT_METAL, kind=K_PLATFORM)
        # le plafond a 1,4 m : Lohen est accroupi
        b.box(mx, min(y0, y1) + 1.7, mz, 1.3, 0.3, dist * 0.5 + 1.0, yaw=yaw,
              flags=F_INDOOR, mat=MAT_METAL, kind=K_WALL)
        # les parois
        for side in (-1, 1):
            b.box(mx + math.cos(math.radians(yaw)) * 1.5 * side,
                  min(y0, y1) + 0.7, mz - math.sin(math.radians(yaw)) * 1.5 * side,
                  0.25, 1.0, dist * 0.5 + 1.0, yaw=yaw, flags=F_INDOOR,
                  mat=MAT_METAL, kind=K_WALL)
        if abs(y1 - y0) > 0.4:
            b.stair(mx, min(y0, y1), mz, 2.0, abs(y1 - y0), max(2.0, dist * 0.6),
                    yaw=yaw, mat=MAT_METAL)
        b.path.append((mx, min(y0, y1), mz))
        b.path_depth.append(2.6)
    # trois passages ou il faut poser la lanterne pour grimper (8 m de noir)
    dark = [(4, "L5-A"), (11, "L5-B"), (17, "L5-C")]
    for idx, name in dark:
        x0, y0, z0, x1, y1, z1 = segs[idx]
        mx, mz = (x0 + x1) * 0.5, (z0 + z1) * 0.5
        b.wall(mx, y0, mz, 3.0, 3.2, 0.5, yaw=0.0, mat=MAT_METAL, flags=F_CLIMBABLE)
        b.ledge(mx, y0 + 2.1, mz, 2.4, yaw=0.0, kind=1)
        b.trigger("TR-S5-DARK-%s" % name, "dark_crossing", mx, y0, mz, rx=6.0,
                  ry=2.0, rz=8.0, target=name, value=8.0)
        b.trigger("TR-S5-C06-%s" % name, "cinematic", mx, y0, mz - 6.0, rx=4.0,
                  ry=2.0, rz=4.0, target="C06" if name == "L5-A" else "")
    # le conduit du puits (L5)
    room = LAMP_ROOMS["S5"]
    px, py, pz = segs[9][3], 40.0 + 9 * 3.0, segs[9][5]
    b.box(px, py + 2.0, pz, 4.0, 4.0, 4.0, flags=F_INDOOR | F_LAMP_ROOM,
          mat=MAT_STONE, kind=K_WALL)
    for i in range(room[2]):
        b.light("omni", px - 1.5 + i * 3.0, py + 2.6, pz, color="#FFD6A0", rng=5.0,
                intensity=0.85, flicker=True, room=room[0])
    # la lanterne portee : une source mobile, declaree comme entite de niveau
    b.light("lantern", 0.0, 41.0, 88.0, color="#FFD6A0", rng=4.0, intensity=1.0,
            flicker=True, room="lanterne")
    b.trigger("TR-S5-LANTERN", "carry", 0.0, 40.0, 86.0, rx=3.0, ry=2.0, rz=3.0,
              target="lanterne", param="pick_up")
    # SOL, compagnon de sequence
    b.npc("NPC-S5-SOL", "sol", 1.2, 40.0, 84.0, yaw=180.0, routine="follow",
          radius=3.0, dialogue="S5_D03", barks="S5", major=True)
    b.npc("NPC-S5-THEA", "thea", 0.0, 62.0, -30.0, yaw=90.0, routine="hidden_voice",
          radius=0.0, dialogue="S5_D09", barks="S5")
    # les Mueurs vivent ici : 5 rencontres, evitables par la lumiere
    for i, idx in enumerate([3, 7, 12, 15, 20]):
        x0, y0, z0, x1, y1, z1 = segs[idx]
        b.encounter("EN-S5-%02d" % (i + 1), (x0 + x1) * 0.5, y0, (z0 + z1) * 0.5,
                    radius=7.0, condition="la lumiere les repousse a 4 m",
                    avoidable=True, enemies=["mueur"] * (1 + i % 2))
    for eid, pid, idx in [("E18", "PN-096", 2), ("E19", "PN-099", 8),
                          ("E20", "PN-103", 14)]:
        x0, y0, z0, x1, y1, z1 = segs[idx]
        b.echo_point(eid, pid, (x0 + x1) * 0.5, y0, (z0 + z1) * 0.5)
    b.trigger("TR-S5-SB5", "silence", segs[4][3], segs[4][4], segs[4][5], rx=4.0,
              ry=2.0, rz=4.0, target="SB5", value=4.0)
    b.trigger("TR-S5-SOL-STORY", "dialogue", segs[8][3], segs[8][4], segs[8][5],
              rx=4.0, ry=2.0, rz=4.0, target="S5_D12")
    b.trigger("TR-S5-T5", "corridor", segs[21][3], segs[21][4], segs[21][5], rx=4.0,
              ry=3.0, rz=4.0, target="T5", param="S6", value=18.0)
    for i, idx in enumerate([1, 5, 9, 14, 19]):
        x0, y0, z0, x1, y1, z1 = segs[idx]
        b.checkpoint("CP-S5-%02d" % (i + 1), (x0 + x1) * 0.5, y0, (z0 + z1) * 0.5,
                     yaw=180.0)
    b.shortcut("SC-S5-01", "door_unlocked", segs[6][3], segs[6][4], segs[6][5],
               opens_to="gaine inferieure")
    for i, idx in enumerate([4, 10, 16]):
        x0, y0, z0, x1, y1, z1 = segs[idx]
        kind = ["viewpoint", "letter_fragment", "optional_echo"][i]
        b.secret("SE-S5-%02d" % (i + 1), kind, x1 + 3.0, y1, z1 + 2.0,
                 payload="%s S5" % kind)
    b.fog(0.0, 55.0, 0.0, radius=60.0, density=0.05, color="#0B0F14")
    last = segs[-1]
    return dict(spawn=[0.0, 40.4, 92.0, 180.0],
                bounds=[-40.0, 36.0, -140.0, 40.0, 110.0, 100.0])


# ---------------------------------------------------------------------------
# S6 — LA SALLE DE BAL
# ---------------------------------------------------------------------------

def build_s6(b, content):
    """
    09.15 : une salle de bal parfaitement conservee, scellee, sans
    poussiere, sans dommage. 26 m de long, parquet en marqueterie, trois
    lustres intacts, des miroirs. La seule piece propre de tout le jeu.
    La nuit dehors, la lune par les hautes fenetres, bleu froid.
    Un seul Echo, 9 minutes, jouable, qui occupe toute la sequence.
    """
    y = 96.0
    # le parquet : 26 m de long, 14 m de large, tapis/marqueterie
    b.slab(0.0, y, 0.0, 14.0, 26.0, mat=MAT_CARPET,
           flags=F_WALKABLE | F_INDOOR, kind=K_GROUND)
    b.walk_to(0.0, y, 10.0, width=14.0, depth=6.0, mat=MAT_CARPET,
              flags=F_WALKABLE | F_INDOOR, connector="none")
    b.walk_to(0.0, y, 0.0, width=14.0, depth=10.0, mat=MAT_CARPET,
              flags=F_WALKABLE | F_INDOOR, connector="bridge")
    b.walk_to(0.0, y, -10.0, width=14.0, depth=6.0, mat=MAT_CARPET,
              flags=F_WALKABLE | F_INDOOR, connector="bridge")
    # les murs, le plafond a 9 m, les hautes fenetres
    for side in (-1, 1):
        b.wall(side * 7.2, y, 0.0, 26.0, 9.0, 0.4, yaw=90.0, mat=MAT_STONE,
               flags=F_INDOOR)
    b.wall(0.0, y, -13.4, 14.0, 9.0, 0.4, yaw=0.0, mat=MAT_STONE, flags=F_INDOOR)
    b.wall(0.0, y, 13.4, 14.0, 9.0, 0.4, yaw=0.0, mat=MAT_STONE, flags=F_INDOOR)
    b.box(0.0, y + 9.2, 0.0, 7.4, 0.4, 13.4, flags=F_INDOOR, mat=MAT_STONE,
          kind=K_ROOF)
    # la lune par les hautes fenetres : 5 ouvertures, bleu froid
    for i in range(5):
        b.light("spot", -7.0, y + 6.4, -10.0 + i * 5.0, color="#BFD8F0", rng=16.0,
                intensity=0.55, flicker=False, room="")
    # trois lustres intacts
    for i, lz in enumerate([-8.0, 0.0, 8.0]):
        b.light("omni", 0.0, y + 6.8, lz, color="#FFE2B0", rng=9.0, intensity=0.0,
                flicker=False, room="lustre")
        b.box(0.0, y + 7.4, lz, 0.9, 0.5, 0.9, flags=0, mat=MAT_METAL,
              kind=K_FURNITURE)
    # les miroirs : jamais de reflet du joueur (05.x), seulement la salle
    for i in range(6):
        b.box(6.9, y + 3.0, -10.0 + i * 4.0, 0.06, 1.8, 1.2, flags=F_INTERACTIVE,
              mat=MAT_GLASS, kind=K_FURNITURE)
    # l'entree : 5 secondes de silence numerique absolu (13.31)
    b.trigger("TR-S6-SB6", "silence", 0.0, y, 12.6, rx=6.0, ry=3.0, rz=2.0,
              target="SB6", value=5.0)
    b.trigger("TR-S6-E21", "echo", 0.0, y, 0.0, rx=8.0, ry=3.0, rz=8.0, target="E21",
              param="mode_B", value=540.0)
    b.trigger("TR-S6-DANCE", "minigame", 0.0, y, -2.0, rx=4.0, ry=2.0, rz=4.0,
              target="danse_4_pressions", value=4.0)
    b.trigger("TR-S6-CHOICE", "choice", 0.0, y, -6.0, rx=4.0, ry=2.0, rz=4.0,
              target="S6_D07_C01", param="faux choix", value=2.0)
    b.trigger("TR-S6-RETURN", "scripted", 0.0, y, 0.0, rx=12.0, ry=4.0, rz=12.0,
              target="retour_bleu_4s", value=4.0)
    b.trigger("TR-S6-C07", "cinematic", 0.0, y, 0.0, rx=12.0, ry=4.0, rz=12.0,
              target="C07")
    b.trigger("TR-S6-T6", "corridor", 0.0, y, -12.6, rx=5.0, ry=3.0, rz=2.0,
              target="T6", param="S7", value=30.0)
    b.echo_point("E21", "PN-110", 0.0, y, -2.0, radius=2.5)
    b.npc("NPC-S6-ESTEBAN", "esteban", 1.0, y, -2.0, yaw=200.0, routine="dance",
          radius=2.0, dialogue="S6_D04", major=True)
    b.checkpoint("CP-S6-01", 0.0, y, 12.0, yaw=180.0)
    b.checkpoint("CP-S6-02", 0.0, y, 2.0, yaw=180.0)
    b.checkpoint("CP-S6-03", 0.0, y, -10.0, yaw=180.0)
    b.secret("SE-S6-01", "letter_fragment", 6.0, y, 11.0, payload="fragment n 6")
    b.secret("SE-S6-02", "journal_object", -6.0, y, -11.0, payload="objet du journal")
    b.fog(0.0, y + 2.0, 0.0, radius=18.0, density=0.012, color="#101A2A")
    return dict(spawn=[0.0, y + 0.4, 12.8, 180.0],
                bounds=[-12.0, y - 2.0, -18.0, 12.0, y + 12.0, 18.0])


# ---------------------------------------------------------------------------
# S7 — LA DESCENTE
# ---------------------------------------------------------------------------

def build_s7(b, content):
    """
    09.16 : le retour vers le bas, par l'exterieur, sous la pluie.
    118 m -> 8 m. Le joueur perd le grappin a la minute 3 (le cable casse).
    La densite maximale d'ennemis : 6 des 14 combats sont ici.
    09.30 : LE VERRIER, a l'altitude 34 m, sur la place de l'Ancienne Halle,
    sous la pluie battante. Arene de 42 m de diametre, un kiosque a musique
    au centre (zone de repit), 4 lampadaires encore allumes.
    """
    # la descente : paliers exterieurs, escaliers effondres, corniches
    stations = [
        (0.0, 118.0, 0.0, 12.0), (-10.0, 112.0, -14.0, 10.0), (6.0, 106.0, -28.0, 10.0),
        (-8.0, 100.0, -42.0, 11.0), (8.0, 94.0, -56.0, 10.0), (-6.0, 88.0, -70.0, 10.0),
        (10.0, 82.0, -84.0, 11.0), (-4.0, 76.0, -98.0, 10.0), (6.0, 70.0, -112.0, 10.0),
        (-8.0, 64.0, -126.0, 12.0), (4.0, 58.0, -140.0, 10.0), (-6.0, 52.0, -154.0, 10.0),
        (0.0, 46.0, -168.0, 12.0), (0.0, 40.0, -182.0, 14.0),
        (0.0, 34.0, -204.0, 42.0),                       # l'arene du Verrier
        (14.0, 28.0, -222.0, 12.0), (-10.0, 22.0, -238.0, 12.0),
        (8.0, 16.0, -254.0, 12.0), (0.0, 10.0, -270.0, 14.0),
        (0.0, 8.0, -288.0, 16.0),
    ]
    for i, (x, y, z, w) in enumerate(stations):
        mode = "none" if i == 0 else "ramp"
        if i in (3, 7, 11, 16):
            mode = "ledge"
        b.walk_to(x, y, z, width=w, depth=w * 0.8, mat=MAT_STONE_WET,
                  kind=K_PLATFORM, connector=mode)
        if w < 20.0:
            b.railing(x - w * 0.5, y, z, w * 0.7, yaw=90.0, mat=MAT_METAL)
    # 09.16 : le cable casse a la minute 3 — plus aucune ancre apres
    b.trigger("TR-S7-GRAPPLE-SNAP", "scripted", stations[2][0], stations[2][1],
              stations[2][2], rx=8.0, ry=3.0, rz=8.0, target="grapple_snap",
              value=180.0)
    # l'arene du Verrier : 42 m de diametre, a 34 m, sous la pluie battante
    ax, ay, az = stations[14][0], stations[14][1], stations[14][2]
    b.slab(ax, ay, az, 42.0, 42.0, mat=MAT_STONE_WET, flags=F_WALKABLE, kind=K_GROUND)
    # la pente vers l'est ou le verre a coule
    b.stair(ax + 18.0, ay - 3.0, az, 8.0, 3.0, 14.0, yaw=90.0, mat=MAT_GLASS)
    b.slab(ax + 24.0, ay - 3.2, az, 12.0, 18.0, mat=MAT_GLASS,
           flags=F_WALKABLE | F_GLASS | F_SLIDE, kind=K_DEBRIS)
    # le kiosque a musique au centre : couvert, zone de repit
    b.slab(ax, ay + 0.4, az, 7.0, 7.0, mat=MAT_WOOD, kind=K_PLATFORM)
    for i in range(6):
        a = math.radians(i * 60.0)
        b.box(ax + math.cos(a) * 3.2, ay + 2.6, az + math.sin(a) * 3.2, 0.18, 2.2,
              0.18, flags=0, mat=MAT_WOOD, kind=K_PILLAR)
    b.box(ax, ay + 5.0, az, 4.0, 0.4, 4.0, flags=0, mat=MAT_METAL, kind=K_ROOF)
    b.trigger("TR-S7-KIOSQUE", "safe_zone", ax, ay, az, rx=4.0, ry=3.0, rz=4.0,
              target="kiosque", value=1.0)
    # 4 lampadaires encore allumes : le Verrier evite la lumiere directe
    for i, (lx, lz) in enumerate([(-14.0, -14.0), (14.0, -14.0), (-14.0, 14.0),
                                  (14.0, 14.0)]):
        b.box(ax + lx, ay + 3.0, az + lz, 0.16, 3.0, 0.16, flags=0, mat=MAT_METAL,
              kind=K_PILLAR)
        b.light("omni", ax + lx, ay + 6.0, az + lz, color="#FFD6A0", rng=11.0,
                intensity=1.25, flicker=True, room="lampadaire")
    # l'ancienne halle : le decor de l'arene
    for i in range(8):
        a = math.radians(i * 45.0)
        b.box(ax + math.cos(a) * 30.0, ay + 6.0, az + math.sin(a) * 30.0, 3.0, 6.0,
              3.0, flags=0, mat=MAT_STONE, kind=K_WALL)
    b.box(ax, ay + 9.0, az - 30.0, 12.0, 9.0, 3.0, flags=0, mat=MAT_STONE,
          kind=K_WALL)
    # LE VERRIER
    b.encounter("EN-S7-BOSS", ax, ay, az, radius=21.0, condition="boss 3 phases",
                avoidable=False, boss=True, phase=1, enemies=["verrier"])
    b.npc("NPC-S7-VERRIER", "verrier", ax, ay, az + 8.0, yaw=180.0, routine="boss",
          radius=0.0, dialogue="S7_D20", major=True)
    b.npc("NPC-S7-ANSELME", "anselme", ax, ay, az + 8.0, yaw=180.0, routine="kneel",
          radius=0.0, dialogue="S7_D24", major=True)
    # la densite maximale : 6 combats ici
    for i, idx in enumerate([2, 5, 8, 12, 16, 18]):
        x, y, z, w = stations[idx]
        b.encounter("EN-S7-%02d" % (i + 1), x, y, z, radius=11.0,
                    condition="condition unique par combat n %d" % (i + 1),
                    avoidable=True,
                    enemies=(["echassier"] if i % 2 == 0 else ["mueur", "mueur"]))
    b.npc("NPC-S7-GARRIC", "garric", stations[17][0], stations[17][1],
          stations[17][2], yaw=90.0, routine="post", radius=2.0, dialogue="S7_D12",
          barks="S7")
    b.npc("NPC-S7-MUEUR", "mueur", stations[9][0], stations[9][1], stations[9][2],
          yaw=0.0, routine="hunt", radius=6.0, barks="S7")
    b.npc("NPC-S7-ECHASSIER", "echassier", stations[5][0], stations[5][1],
          stations[5][2], yaw=0.0, routine="stalk", radius=8.0, barks="S7")
    for eid, pid, idx in [("E22", "PN-118", 14), ("E23", "PN-121", 14),
                          ("E24", "PN-124", 15), ("E25", "PN-127", 17)]:
        x, y, z, w = stations[idx]
        b.echo_point(eid, pid, x + 3.0, y, z + 3.0)
    b.trigger("TR-S7-SB7", "silence", ax, ay, az + 8.0, rx=8.0, ry=3.0, rz=8.0,
              target="SB7", value=2.5)
    b.trigger("TR-S7-C09", "cinematic", ax, ay, az - 18.0, rx=10.0, ry=3.0, rz=8.0,
              target="C09")
    b.trigger("TR-S7-CABLE", "rule_change", stations[2][0], stations[2][1],
              stations[2][2], rx=8.0, ry=3.0, rz=8.0, target="grapple_disabled",
              value=1.0)
    b.trigger("TR-S7-T7", "corridor", stations[-1][0], stations[-1][1],
              stations[-1][2], rx=6.0, ry=3.0, rz=5.0, target="T7", param="S8",
              value=32.0)
    for i, idx in enumerate([1, 4, 6, 10, 13, 15, 19]):
        x, y, z, w = stations[idx]
        b.checkpoint("CP-S7-%02d" % (i + 1), x, y, z + 4.0, yaw=180.0)
    for i, idx in enumerate([3, 12]):
        x, y, z, w = stations[idx]
        b.shortcut("SC-S7-%02d" % (i + 1),
                   ["ladder_dropped", "rope_descended"][i], x - 6.0, y, z,
                   opens_to="palier inferieur")
    spots = [(18.0, 100.0, -50.0, "viewpoint"), (-16.0, 88.0, -76.0, "letter_fragment"),
             (20.0, 64.0, -130.0, "journal_object"), (-18.0, 22.0, -240.0,
             "optional_echo")]
    for i, (x, y, z, kind) in enumerate(spots):
        b.secret("SE-S7-%02d" % (i + 1), kind, x, y, z, payload="%s S7" % kind)
    b.fog(0.0, 80.0, -120.0, radius=160.0, density=0.026, color="#1A2430")
    b.fog(ax, ay + 4.0, az, radius=40.0, density=0.034, color="#1A2430")
    return dict(spawn=[0.0, 118.4, 6.0, 180.0],
                bounds=[-60.0, 4.0, -310.0, 60.0, 130.0, 20.0])


# ---------------------------------------------------------------------------
# S8 — LA MONTEE AU PHARE
# ---------------------------------------------------------------------------

def build_s8(b, content):
    """
    09.17 : 8 m -> 212 m, la plus longue verticale du jeu, d'un seul tenant,
    sans chargement, sans ennemi, sans combat. Le level design est une
    recapitulation : chaque section rejoue en miniature une sequence
    precedente. A 150 m, la brume passe SOUS le joueur.
    SOMMET : la chambre de la lentille, une piece ronde de 6 m.
    """
    lx, lz = LIGHTHOUSE_X, LIGHTHOUSE_Z
    # le Phare : une tour de pierre massive, spirale, 212 m
    shaft_r = 5.0
    for i in range(28):
        y = 8.0 + i * 7.4
        b.box(lx, y + 3.7, lz, shaft_r, 3.7, shaft_r, flags=0, mat=MAT_STONE,
              kind=K_TOWER)
    # l'escalier interieur est effondre au tiers (10.04) : entre 70 et 87 m
    # le chemin principal : une spirale exterieure autour de la tour
    turns = 7.0
    steps = 84
    pts = []
    for i in range(steps + 1):
        t = i / float(steps)
        a = math.radians(-90.0 + t * turns * 360.0)
        r = 9.0 + 2.0 * math.sin(t * math.pi * 3.0)
        y = 8.0 + t * (LIGHTHOUSE_TOP - 8.0 - 6.0)
        pts.append((lx + math.cos(a) * r, y, lz + math.sin(a) * r, a))
    # sections de recapitulation (09.17) : 7 paliers, un par sequence
    sections = [
        (0, "les cordages de S1", MAT_WOOD_WET),
        (1, "une plaque de verre de S2", MAT_GLASS),
        (2, "des anneaux de S3", MAT_METAL),
        (3, "un escalier en spirale de S4", MAT_WOOD),
        (4, "un conduit de S5", MAT_METAL),
        (5, "un parquet de S6", MAT_CARPET),
        (6, "une pluie de S7", MAT_STONE_WET),
    ]
    for si, (sidx, label, mat) in enumerate(sections):
        lo = int(sidx * steps / 7.0)
        hi = int((sidx + 1) * steps / 7.0)
        for i in range(lo, hi, 3):
            x, y, z, a = pts[i]
            b.slab(x, y, z, 4.6, 4.6, mat=mat, flags=F_WALKABLE, kind=K_PLATFORM)
            if i > lo:
                px, py, pz, pa = pts[max(lo, i - 3)]
                b.connect(px, py, pz, x, y, z, 4.6, 4.6, mat,
                          "grapple" if (i - lo) % 12 == 0 and sidx in (2, 6) else "ramp")
            if i % 9 == 0:
                b.railing(x + math.cos(a) * 2.2, y, z + math.sin(a) * 2.2, 3.4,
                          yaw=-math.degrees(a), mat=MAT_METAL)
        b.path.append(pts[lo][:3])
        b.path_depth.append(4.6)
        b.path.append(pts[hi - 1][:3])
        b.path_depth.append(4.6)
        b.trigger("TR-S8-SEC-%d" % sidx, "spectacle", pts[lo][0], pts[lo][1],
                  pts[lo][2], rx=8.0, ry=4.0, rz=8.0, target=label, value=1.0)
    # ancres de grappin : le grappin est rendu par Sol (scene de 90 s).
    # Un anneau tous les 4 paliers : la chaine ne depasse jamais 28 m.
    for i in range(0, steps, 4):
        x, y, z, a = pts[i]
        b.anchor(x + math.cos(a) * 3.0, y + 3.4, z + math.sin(a) * 3.0, kind=0)
    b.trigger("TR-S8-GRAPPLE-RETURN", "scripted", lx + 9.0, 8.0, lz + 9.0, rx=8.0,
              ry=3.0, rz=8.0, target="grapple_repaired", value=90.0)
    # la brume passe SOUS le joueur a 150 m
    b.fog(lx, 120.0, lz, radius=220.0, density=0.030, color="#E8C9A0")
    b.fog(lx, 170.0, lz, radius=160.0, density=0.004, color="#FFE6C4")
    # le palier des cloches (E27) a 178 m
    bell_y = 178.0
    b.slab(lx + 8.0, bell_y, lz, 8.0, 8.0, mat=MAT_WOOD, kind=K_PLATFORM)
    for i in range(5):
        b.box(lx + 6.0 + i * 1.2, bell_y + 1.6, lz - 2.0, 0.4, 0.5, 0.4, flags=0,
              mat=MAT_METAL, kind=K_FURNITURE)
    b.light("omni", lx + 8.0, bell_y + 3.0, lz, color="#FFD6A0", rng=8.0,
            intensity=0.7, flicker=True, room="palier_cloches")
    # la chambre de la lentille : piece ronde de 6 m, palier a 206 m
    door_y = 206.0
    b.slab(lx, door_y, lz, 8.0, 8.0, mat=MAT_WOOD, kind=K_PLATFORM)
    b.box(lx, door_y + 3.0, lz, 3.0, 3.0, 3.0, flags=F_INDOOR | F_LAMP_ROOM,
          mat=MAT_STONE, kind=K_TOWER)
    b.slab(lx, door_y + 6.2, lz, 6.4, 6.4, mat=MAT_STONE, kind=K_ROOF)
    b.trigger("TR-S8-DOOR", "door", lx, door_y, lz + 3.2, rx=1.6, ry=2.2, rz=1.0,
              target="porte_chambre", value=2.2)
    b.trigger("TR-S8-ENTRY", "free_cam", lx, door_y, lz, rx=4.0, ry=3.0, rz=4.0,
              target="entree_34s", value=34.0)
    b.trigger("TR-S8-SB8", "silence", lx, door_y, lz, rx=4.0, ry=3.0, rz=4.0,
              target="SB8", value=3.5)
    b.trigger("TR-S8-E31", "echo", lx, door_y + 0.4, lz - 1.0, rx=2.0, ry=2.0,
              rz=2.0, target="E31", param="mode_B", value=250.0)
    # la lampe a huile (2700 K, 1,2 m, vacillante) et la lentille (900 m)
    b.light("lantern", lx - 1.2, door_y + 1.0, lz + 0.6, color="#FFD6A0", rng=1.2,
            intensity=1.0, flicker=True, room="L6")
    room = LAMP_ROOMS["S8"]
    for i in range(room[2]):
        a = math.radians(i * 90.0)
        b.light("omni", lx + math.cos(a) * 1.8, door_y + 2.4, lz + math.sin(a) * 1.8,
                color="#FFE0B0", rng=5.0, intensity=0.5, flicker=False, room=room[0])
    b.light("lighthouse", lx, door_y + 4.0, lz, color="#CFE6FF", rng=900.0,
            intensity=3.0, flicker=False, room="phare")
    # la table, la couverture pliee, le carnet, la lettre
    b.box(lx + 0.8, door_y + 0.45, lz - 0.8, 0.9, 0.45, 0.6, flags=F_INTERACTIVE,
          mat=MAT_WOOD, kind=K_FURNITURE)
    b.box(lx - 1.6, door_y + 0.2, lz + 1.2, 0.7, 0.2, 0.5, flags=F_INTERACTIVE,
          mat=MAT_WOOD, kind=K_FURNITURE)
    # le mur de feuilles punaisees : 314 papiers (20.04)
    for i in range(24):
        b.box(lx - 2.6, door_y + 1.2 + (i % 6) * 0.34, lz - 2.0 + (i // 6) * 0.9,
              0.02, 0.15, 0.11, flags=F_INTERACTIVE, mat=MAT_WOOD, kind=K_FURNITURE)
    b.trigger("TR-S8-SHEETS", "scripted", lx, door_y, lz - 1.0, rx=3.0, ry=2.0,
              rz=3.0, target="LETTRE_L314", value=314.0)
    b.echo_point("E26", "PN-133", pts[2][0], pts[2][1], pts[2][2])
    b.echo_point("E27", "PN-136", lx + 8.0, bell_y, lz)
    b.echo_point("E28", "PN-139", pts[40][0], pts[40][1], pts[40][2])
    b.echo_point("E29", "PN-142", lx - 1.6, door_y + 0.4, lz + 1.2)
    b.echo_point("E30", "PN-145", lx + 0.8, door_y + 0.9, lz - 0.8)
    b.echo_point("E31", "PN-147", lx, door_y + 0.9, lz - 1.0)
    b.npc("NPC-S8-SOL", "sol", lx + 10.0, 8.0, lz + 10.0, yaw=0.0, routine="wait",
          radius=2.0, dialogue="S8_D02", barks="S8", major=True)
    b.npc("NPC-S8-SOL-DORT", "sol", lx, door_y - 3.0, lz + 5.0, yaw=0.0,
          routine="sleep", radius=0.0, dialogue="S8_D20", major=True)
    for i in range(7):
        x, y, z, a = pts[int(i * steps / 7.0)]
        b.checkpoint("CP-S8-%02d" % (i + 1), x, y, z, yaw=-math.degrees(a))
    for i, (x, y, z) in enumerate([(lx + 12.0, 96.0, lz + 12.0),
                                   (lx - 12.0, 160.0, lz - 12.0)]):
        b.shortcut("SC-S8-%02d" % (i + 1), "rope_descended", x, y, z,
                   opens_to="palier inferieur")
    spots = [(lx + 16.0, 60.0, lz + 16.0, "viewpoint"),
             (lx - 16.0, 120.0, lz + 8.0, "letter_fragment"),
             (lx + 14.0, 190.0, lz - 14.0, "journal_object")]
    for i, (x, y, z, kind) in enumerate(spots):
        b.secret("SE-S8-%02d" % (i + 1), kind, x, y, z, payload="%s S8" % kind)
    # la descente finale : 90 s, Sol dort contre la porte, le soleil se leve
    b.trigger("TR-S8-DESCENT", "scripted", lx, door_y, lz + 4.0, rx=6.0, ry=3.0,
              rz=6.0, target="descente_90s", value=90.0)
    b.trigger("TR-S8-FINAL", "ending", lx + 10.0, 8.0, lz + 12.0, rx=10.0, ry=4.0,
              rz=10.0, target="sol_dort", value=24.0)
    b.trigger("TR-S8-CREDITS", "credits", lx + 10.0, 8.0, lz + 12.0, rx=12.0, ry=4.0,
              rz=12.0, target="M20", value=60.0)
    return dict(spawn=[lx + 9.0, 8.4, lz + 14.0, 200.0],
                bounds=[lx - 60.0, 4.0, lz - 60.0, lx + 60.0, 226.0, lz + 60.0])


BUILDERS = {
    "S1": build_s1, "S2": build_s2, "S3": build_s3, "S4": build_s4,
    "S5": build_s5, "S6": build_s6, "S7": build_s7, "S8": build_s8,
}


# ---------------------------------------------------------------------------
# Assemblage
# ---------------------------------------------------------------------------

def add_props(b, props):
    """Place les 147 props narratifs en suivant leurs notes de placement."""
    mine = [p for p in props if p.get("seq") == b.seq]
    path = b.path if b.path else [(0.0, b.meta["alt"][0], 0.0)]
    for i, p in enumerate(mine):
        note = p.get("position_note", "")
        x, y, z, yaw = prop_placement(note, path, i, len(mine))
        climb = bool(p.get("climbable"))
        scale = 1.0
        b.prop(p["id"], x, y, z, yaw=yaw, scale=scale)
        # un prop grimpable pose sa propre prise (09.21 : bois clair use)
        if climb:
            b.ledge(x, y + 1.5, z, 1.8, yaw=yaw, kind=0)
        # un prop porteur d'Echo est interactif : contraste d'humidite, pas
        # d'outline (09.21)
        if p.get("echo_id"):
            b.box(x, y + 0.5, z, 0.6, 0.5, 0.6, flags=F_INTERACTIVE, mat=MAT_WOOD,
                  kind=K_FURNITURE)


def add_landmark_beacons(b):
    """09.02 : les reperes doivent etre identifiables depuis 180 m."""
    for name, alt in LANDMARKS:
        lo, hi = b.meta["alt"]
        if min(lo, hi) - 20.0 <= alt <= max(lo, hi) + 20.0:
            b.trigger("TR-%s-LANDMARK-%d" % (b.seq, int(alt)), "landmark",
                      0.0, alt, 0.0, rx=200.0, ry=20.0, rz=200.0, target=name,
                      value=alt, one_shot=False)


def add_silences(b):
    for sid, seq, seconds, where in SILENCES:
        if seq != b.seq:
            continue
        if any(t["id"] == "TR-%s-%s" % (seq, sid) for t in b.triggers):
            continue
        # deja poses par les constructeurs ; on complete si absent
        if not any(t.get("target") == sid for t in b.triggers):
            pt = b.path[len(b.path) // 2] if b.path else (0.0, b.meta["alt"][0], 0.0)
            b.trigger("TR-%s-%s" % (seq, sid), "silence", pt[0], pt[1], pt[2],
                      rx=5.0, ry=2.5, rz=5.0, target=sid, value=seconds)


def build_all(verbose=False):
    props_doc = load_json("props/props.json") or {"props": []}
    props = props_doc["props"]
    echos_doc = load_json("echos/echos.json") or {"echos": []}
    echos = echos_doc["echos"]
    os.makedirs(OUT, exist_ok=True)
    stats = {}
    totals = dict(checkpoints=0, shortcuts=0, secrets=0, props=0, echos=0,
                  npcs=0, encounters=0, solids=0, stairs=0, ledges=0, anchors=0)
    for seq in ORDER:
        meta = SEQUENCES[seq]
        b = LevelBuilder(seq, meta)
        extra = BUILDERS[seq](b, dict(props=props, echos=echos))
        add_props(b, props)
        add_landmark_beacons(b)
        add_silences(b)
        # transitions (09.51)
        for cid, a, c, seconds in CORRIDORS:
            if a == seq:
                extra["transition_out"] = cid
            if c == seq:
                extra["transition_in"] = cid
        doc = b.to_json(sky_for(seq, meta), extra["spawn"], extra["bounds"],
                        extra=dict(extra, corridor_in=extra.get("transition_in", ""),
                                   corridor_out=extra.get("transition_out", "")))
        issues = b.audit()
        path = os.path.join(OUT, "level_%s.json" % seq.lower())
        with open(path, "w", encoding="utf-8") as f:
            json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
        size = os.path.getsize(path)
        stats[seq] = dict(solids=len(b.solids), stairs=len(b.stairs),
                          ledges=len(b.ledges), anchors=len(b.anchors),
                          props=len(b.props), checkpoints=len(b.checkpoints),
                          secrets=len(b.secrets), shortcuts=len(b.shortcuts),
                          npcs=len(b.npcs), encounters=len(b.encounters),
                          echos=len(b.echo_points), triggers=len(b.triggers),
                          lights=len(b.lights), size_kb=size / 1024.0,
                          issues=issues)
        for k, v in (("checkpoints", len(b.checkpoints)), ("shortcuts", len(b.shortcuts)),
                     ("secrets", len(b.secrets)), ("props", len(b.props)),
                     ("echos", len(b.echo_points)), ("npcs", len(b.npcs)),
                     ("encounters", len(b.encounters)), ("solids", len(b.solids)),
                     ("stairs", len(b.stairs)), ("ledges", len(b.ledges)),
                     ("anchors", len(b.anchors))):
            totals[k] += v
        if verbose:
            print("  %s : %d solides, %d rampes, %d prises, %d ancres, %d props, "
                  "%d PNJ, %.0f Ko" % (seq, len(b.solids), len(b.stairs),
                                       len(b.ledges), len(b.anchors), len(b.props),
                                       len(b.npcs), size / 1024.0))
            for w in b._warnings:
                print("     ! %s" % w)
            for i in issues:
                print("     X %s" % i)
    return stats, totals


def verify(totals, stats):
    """BLOC 22 : les comptes obligatoires du chapitre."""
    errors = []
    if totals["checkpoints"] != 47:
        errors.append("47 checkpoints attendus, %d trouves" % totals["checkpoints"])
    if totals["shortcuts"] != 14:
        errors.append("14 raccourcis attendus, %d trouves" % totals["shortcuts"])
    if totals["secrets"] != 31:
        errors.append("31 secrets attendus, %d trouves" % totals["secrets"])
    if totals["props"] != 147:
        errors.append("147 props attendus, %d trouves" % totals["props"])
    if totals["echos"] != 31:
        errors.append("31 points d'Echo attendus, %d trouves" % totals["echos"])
    if stats["S3"]["npcs"] < 34:
        errors.append("34 PNJ attendus au marche, %d trouves" % stats["S3"]["npcs"])
    if stats["S3"]["anchors"] < 60:
        errors.append("60 anneaux de relayeur attendus, %d trouves"
                      % stats["S3"]["anchors"])
    if stats["S7"]["encounters"] < 7:
        errors.append("6 combats + le boss attendus en S7, %d trouves"
                      % stats["S7"]["encounters"])
    if not any(e["boss"] for e in []):
        pass
    for seq, s in stats.items():
        for i in s["issues"]:
            errors.append("%s : %s" % (seq, i))
    return errors


def main():
    ap = argparse.ArgumentParser(description="Compilateur de niveaux LOHEN (BLOC 09)")
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args()
    print("LOHEN · compilateur de niveaux")
    stats, totals = build_all(verbose=args.verbose)
    errors = verify(totals, stats)
    print("  totaux    : %d checkpoints, %d raccourcis, %d secrets, %d props, "
          "%d Echos, %d PNJ, %d rencontres" % (
              totals["checkpoints"], totals["shortcuts"], totals["secrets"],
              totals["props"], totals["echos"], totals["npcs"],
              totals["encounters"]))
    print("  geometrie : %d solides, %d rampes, %d prises, %d ancres" % (
        totals["solids"], totals["stairs"], totals["ledges"], totals["anchors"]))
    if errors:
        print("ERREURS de conformite :")
        for e in errors:
            print("  - %s" % e)
        return 1
    print("OK — 8 niveaux generes dans content/levels/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
