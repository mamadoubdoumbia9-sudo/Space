# -*- coding: utf-8 -*-
"""
LOHEN — tools/make_textures.py

Les VRAIES images du jeu (retour joueur : « génère des images dans le jeu »,
« le jeu doit peser au moins 100 Mo »). Tout est procedural et deterministe :

  content/tex/atlas_base.png     4096²  16 tuiles de matieres (S1,S2,S4,S6,S7)
  content/tex/atlas_chaud.png    4096²  grade chaude — le marche de S3
  content/tex/atlas_sombre.png   4096²  grade froide — les conduits de S5
  content/tex/atlas_aube.png     4096²  grade doree — l'aube de S8
  content/tex/menu.png           fond peint du menu principal
  content/tex/boot.png           ecran d'accueil (sceau de cire ambre)
  content/tex/journal.png        papier du journal
  content/tex/lettre.png         la feuille de la lettre finale

Ordre des tuiles (IMMUABLE — WorldRenderer.texParam et le shader comptent
dessus) : 0 pierre seche · 1 pierre humide · 2 brique · 3 platre · 4 calcaire
· 5 bois · 6 bois goudronne · 7 metal · 8 gravier · 9 tapis · 10 eau
· 11 papier · 12 pierre moussue · 13 metal rouille · 14 nuage · 15 verre.

Aucune dependance : zlib + struct + random. Technique planaire :
bytearray.translate et affectations de tranches (vitesse C).

Usage : python3 tools/make_textures.py [taille_tile=1024]
"""
import os
import random
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "content", "tex")
T = int(sys.argv[1]) if len(sys.argv) > 1 else 1024     # tuile
A = T * 4                                                 # atlas 4x4


def clampb(x):
    return 0 if x < 0 else (255 if x > 255 else int(x))


def tbl(tone, amp=40):
    """table : bruit v -> tone + (v-128)*amp/128"""
    return bytes(clampb(tone + (v - 128) * amp / 128.0) for v in range(256))


def mix_tbl(t0, t1, k, amp=40):
    return tbl(t0 * (1 - k) + t1 * k, amp)


def write_png(path, w, h, rgb):
    stride = w * 3
    raw = bytearray()
    ap = raw.append
    for y in range(h):
        ap(0)
        raw += rgb[y * stride:(y + 1) * stride]
    comp = zlib.compress(bytes(raw), 9)

    def chunk(t, d):
        return (struct.pack(">I", len(d)) + t + d
                + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
                + chunk(b"IDAT", comp) + chunk(b"IEND", b""))
    return os.path.getsize(path)


class Tile:
    """trois plans R/G/B + bruit partage"""

    def __init__(self, seed, size=T):
        self.rng = random.Random(seed)
        self.w = size
        n = size * size
        self.R = bytearray(n)
        self.G = bytearray(n)
        self.B = bytearray(n)
        self.rows = [self.rng.randbytes(size) for _ in range(11)]
        self.tc = {}

    def tables(self, tone, amp):
        key = (int(tone[0] * 4), int(tone[1] * 4), int(tone[2] * 4), amp)
        t = self.tc.get(key)
        if t is None:
            t = (tbl(tone[0], amp), tbl(tone[1], amp), tbl(tone[2], amp))
            self.tc[key] = t
        return t

    def fill(self, y0, y1, tone, amp=40, x0=0, x1=None):
        x1 = self.w if x1 is None else x1
        x0 = max(0, x0)
        x1 = min(self.w, x1)
        if x1 <= x0 or y1 <= y0:
            return
        tr, tg, tb = self.tables(tone, amp)
        w = self.w
        R, G, B, rows = self.R, self.G, self.B, self.rows
        i = y0 % 11
        for y in range(y0, y1):
            nz = rows[i]
            i = (i + 1) % 11
            s0 = y * w + x0
            s1 = y * w + x1
            R[s0:s1] = nz[x0:x1].translate(tr)
            G[s0:s1] = nz[x0:x1].translate(tg)
            B[s0:s1] = nz[x0:x1].translate(tb)

    def span(self, y, x0, x1, tone, amp=12):
        x0 = max(0, x0)
        x1 = min(self.w, x1)
        if y < 0 or y >= self.w or x1 <= x0:
            return
        w = self.w
        s0, s1 = y * w + x0, y * w + x1
        tr, tg, tb = self.tables(tone, amp)
        nz = self.rows[y % 11][x0:x1]
        self.R[s0:s1] = nz.translate(tr)
        self.G[s0:s1] = nz.translate(tg)
        self.B[s0:s1] = nz.translate(tb)

    def blotch(self, cx, cy, rad, tone, amp=30, blend=0.65):
        w = self.w
        r2 = rad * rad
        base_t = (tone[0] * blend, tone[1] * blend, tone[2] * blend)
        for y in range(max(0, int(cy - rad)), min(w, int(cy + rad) + 1)):
            dy = y - cy
            dx2 = r2 - dy * dy
            if dx2 <= 0:
                continue
            dx = int(dx2 ** 0.5)
            x0, x1 = max(0, int(cx - dx)), min(w, int(cx + dx) + 1)
            self.span(y, x0, x1, base_t, amp)

    def rgb(self):
        out = bytearray(self.w * self.h3())
        out[0::3] = self.R
        out[1::3] = self.G
        out[2::3] = self.B
        return out

    def h3(self):
        return self.w * 3


# ---------------------------------------------------------------- matieres
def t_stone(seed, tone, mortar, blocks=6, amp=34, streaks=False):
    t = Tile(seed)
    w = T
    t.fill(0, w, tone, amp)
    bh = w // blocks
    rng = t.rng
    for by in range(blocks):
        off = (by % 2) * (w // (blocks * 2))
        bw = w // blocks
        for bx in range(-1, blocks + 1):
            x0 = bx * bw + off
            y0 = by * bh
            v = rng.randint(-16, 16)
            t.fill(y0 + 3, y0 + bh - 3, (tone[0] + v, tone[1] + v, tone[2] + v),
                   amp, max(0, x0 + 3), min(w, x0 + bw - 3))
    for by in range(blocks + 1):
        y = by * bh
        for yy in range(max(0, y - 2), min(w, y + 3)):
            t.span(yy, 0, w, mortar, 10)
    for by in range(blocks):
        off = (by % 2) * (w // (blocks * 2))
        bw = w // blocks
        for bx in range(blocks + 2):
            x = bx * bw + off
            for xx in range(max(0, x - 2), min(w, x + 3)):
                for yy in range(by * bh, min(w, (by + 1) * bh)):
                    t.span(yy, xx, xx + 1, mortar, 8)
    if streaks:
        for i in range(26):
            x = rng.randint(0, w - 1)
            y0 = rng.randint(0, w // 2)
            ln = rng.randint(w // 8, w // 2)
            dark = (tone[0] * 0.72, tone[1] * 0.74, tone[2] * 0.72)
            for y in range(y0, min(w, y0 + ln)):
                t.span(y, x, x + rng.randint(1, 3), dark, 8)
    return t


def t_brick(seed):
    t = Tile(seed)
    w = T
    base = (158, 78, 51)
    mortar = (178, 160, 138)
    t.fill(0, w, mortar, 18)
    rows = 16
    bh = w // rows
    bw = w // 8
    rng = t.rng
    for by in range(rows):
        off = (by % 2) * (bw // 2)
        for bx in range(-1, 9):
            x0 = bx * bw + off
            y0 = by * bh
            v = rng.randint(-26, 26)
            hue = rng.randint(-8, 10)
            tone = (base[0] + v + hue, base[1] + v * 0.6, base[2] + v * 0.5)
            t.fill(y0 + 2, y0 + bh - 2, tone, 26,
                   max(0, x0 + 2), min(w, x0 + bw - 2))
    return t


def t_plaster(seed, tone=(169, 151, 124), seed_blobs=9):
    t = Tile(seed)
    w = T
    t.fill(0, w, tone, 22)
    rng = t.rng
    for i in range(seed_blobs):
        cx, cy = rng.randint(0, w), rng.randint(0, w)
        rad = rng.randint(w // 12, w // 4)
        v = rng.randint(-24, 12)
        t.blotch(cx, cy, rad, (tone[0] + v, tone[1] + v - 2, tone[2] + v - 6),
                 16, 0.45)
    for i in range(240):
        x, y = rng.randint(0, w - 1), rng.randint(0, w - 1)
        t.span(y, x, x + rng.randint(1, 5), (tone[0] - 40, tone[1] - 40,
                                              tone[2] - 36), 6)
    return t


def t_limestone(seed):
    t = Tile(seed)
    w = T
    tone = (207, 187, 149)
    t.fill(0, w, tone, 20)
    rng = t.rng
    y = 0
    while y < w:
        h = rng.randint(6, 26)
        v = rng.randint(-14, 10)
        t.fill(y, min(w, y + h), (tone[0] + v, tone[1] + v, tone[2] + v * 0.8),
               16)
        y += h
    for i in range(90):
        yy = rng.randint(0, w - 1)
        t.span(yy, rng.randint(0, w - 60), rng.randint(0, w - 1) + 60,
               (tone[0] - 30, tone[1] - 28, tone[2] - 24), 8)
    return t


def t_wood(seed, tone=(107, 76, 51), dark=True):
    t = Tile(seed)
    w = T
    t.fill(0, w, tone, 22)
    rng = t.rng
    planks = 5
    pw = w // planks
    for p in range(planks):
        v = rng.randint(-14, 14)
        pt = (tone[0] + v, tone[1] + v, tone[2] + v)
        t.fill(0, w, pt, 18, p * pw + 2, (p + 1) * pw - 2)
        for x in range(max(0, p * pw - 1), min(w, p * pw + 2)):
            for y in range(0, w, 4):
                t.span(y, x, x + 1, (tone[0] * 0.5, tone[1] * 0.5,
                                     tone[2] * 0.5), 6)
        for i in range(46):
            x = p * pw + rng.randint(3, pw - 4)
            y0 = rng.randint(0, w - 40)
            ln = rng.randint(20, 120)
            g = (pt[0] * 0.82, pt[1] * 0.8, pt[2] * 0.78)
            for y in range(y0, min(w, y0 + ln)):
                t.span(y, x + int(2 * rng.random()) - 1, x + 1, g, 5)
        for i in range(2):
            kx = p * pw + rng.randint(8, pw - 8)
            ky = rng.randint(8, w - 8)
            t.blotch(kx, ky, rng.randint(4, 9),
                     (tone[0] * 0.55, tone[1] * 0.45, tone[2] * 0.35), 10, 0.8)
    return t


def t_metal(seed, tone=(110, 122, 134)):
    t = Tile(seed)
    w = T
    t.fill(0, w, tone, 14)
    rng = t.rng
    for i in range(700):
        y = rng.randint(0, w - 1)
        x0 = rng.randint(0, w - 40)
        ln = rng.randint(20, 160)
        v = rng.randint(-20, 24)
        t.span(y, x0, min(w, x0 + ln), (tone[0] + v, tone[1] + v,
                                        tone[2] + v + 4), 6)
    for ry in range(0, w, w // 4):
        for rx in range(0, w, w // 4):
            cx, cy = rx + w // 8, ry + w // 8
            t.blotch(cx, cy, 7, (tone[0] + 34, tone[1] + 36, tone[2] + 40),
                     8, 0.9)
            t.blotch(cx, cy, 3, (tone[0] - 30, tone[1] - 30, tone[2] - 26),
                     6, 0.9)
    return t


def t_gravel(seed):
    t = Tile(seed)
    w = T
    t.fill(0, w, (122, 108, 92), 60)
    rng = t.rng
    for i in range(1400):
        cx, cy = rng.randint(0, w - 1), rng.randint(0, w - 1)
        rad = rng.randint(2, 8)
        v = rng.randint(-46, 46)
        t.blotch(cx, cy, rad, (122 + v, 108 + v * 0.9, 92 + v * 0.8), 26, 0.85)
    return t


def t_carpet(seed):
    t = Tile(seed)
    w = T
    base = (131, 68, 58)
    t.fill(0, w, base, 16)
    c = w // 2
    for y in range(0, w, 8):
        for x in range(0, w, 8):
            d = abs(x - c) + abs(y - c)
            tone = (base[0] + 26, base[1] + 16, base[2] + 8) if (d // 40) % 2 \
                else (base[0] - 22, base[1] - 12, base[2] - 8)
            t.span(y, x, min(w, x + 8), tone, 12)
            t.span(y + 1, x, min(w, x + 8), (tone[0] * 0.85, tone[1] * 0.85,
                                             tone[2] * 0.85), 12)
    gold = (196, 158, 92)
    for m in range(14, 22):
        t.span(m, m, w - m, gold, 10)
        t.span(w - 1 - m, m, w - m, gold, 10)
        t.span(m, m, m + 1, gold, 10)
    for y in range(14, 22):
        for x in range(14, w - 14, 1):
            t.span(y, x, x + 1, gold, 10)
            t.span(w - 1 - y, x, x + 1, gold, 10)
    return t


def t_water(seed, tone=(24, 52, 62)):
    t = Tile(seed)
    w = T
    rng = t.rng
    for y in range(w):
        s = (y % 97) / 97.0
        v = int(18 * (0.5 + 0.5 * (s * 6.283 % 6.283 - 3.1416) ** 0))
        k = 0.5 + 0.5 * ((y * 7 % 97) / 97.0)
        tt = (tone[0] * (0.8 + 0.4 * k), tone[1] * (0.85 + 0.3 * k),
              tone[2] * (0.9 + 0.2 * k))
        t.span(y, 0, w, tt, 26)
    for i in range(180):
        y = rng.randint(0, w - 1)
        x0 = rng.randint(0, w - 80)
        t.span(y, x0, x0 + rng.randint(30, 90),
               (tone[0] + 40, tone[1] + 62, tone[2] + 60), 14)
    return t


def t_paper(seed):
    t = Tile(seed)
    w = T
    tone = (228, 220, 204)
    t.fill(0, w, tone, 14)
    rng = t.rng
    for i in range(2600):
        y = rng.randint(0, w - 1)
        x = rng.randint(0, w - 6)
        t.span(y, x, x + rng.randint(2, 7),
               (tone[0] - 16, tone[1] - 14, tone[2] - 20), 8)
    for y in range(w // 12, w - 40, (w - 80) // 16):
        t.span(y, 60, w - 60, (198, 190, 176), 6)
    return t


def t_moss(seed):
    t = t_stone(seed, (104, 98, 82), (58, 56, 46), 6, 30)
    rng = t.rng
    for i in range(30):
        t.blotch(rng.randint(0, T - 1), rng.randint(0, T - 1),
                 rng.randint(T // 24, T // 8),
                 (86 + rng.randint(-14, 20), 118 + rng.randint(-14, 24),
                  74 + rng.randint(-10, 14)), 26, 0.55)
    return t


def t_rust(seed):
    t = t_metal(seed, (104, 112, 122))
    rng = t.rng
    for i in range(34):
        t.blotch(rng.randint(0, T - 1), rng.randint(0, T - 1),
                 rng.randint(T // 28, T // 9),
                 (150 + rng.randint(-16, 30), 88 + rng.randint(-14, 20),
                  48 + rng.randint(-10, 14)), 30, 0.7)
    return t


def t_cloud(seed):
    t = Tile(seed)
    w = T
    rng = t.rng
    for y in range(w):
        k = y / float(w)
        tone = (214 - 40 * k, 224 - 36 * k, 238 - 30 * k)
        t.span(y, 0, w, tone, 22)
    for i in range(26):
        t.blotch(rng.randint(0, w), rng.randint(0, w), rng.randint(w // 10, w // 4),
                 (250, 250, 252), 12, 0.30)
    for i in range(14):
        t.blotch(rng.randint(0, w), rng.randint(0, w), rng.randint(w // 14, w // 6),
                 (150, 168, 196), 12, 0.22)
    return t


def t_glass(seed):
    t = Tile(seed)
    w = T
    rng = t.rng
    for y in range(w):
        k = 0.5 + 0.5 * ((y * 13 % 61) / 61.0)
        t.span(y, 0, w, (16 + 22 * k, 34 + 30 * k, 40 + 30 * k), 14)
    for i in range(120):
        y = rng.randint(0, w - 1)
        x0 = rng.randint(0, w - 60)
        t.span(y, x0, x0 + rng.randint(20, 70), (96, 150, 148), 20)
    return t


BUILDERS = [
    lambda s: t_stone(s, (140, 122, 96), (74, 66, 56), 6, 32),            # 0
    lambda s: t_stone(s, (94, 88, 74), (52, 50, 44), 7, 26, True),        # 1
    t_brick,                                                               # 2
    t_plaster,                                                             # 3
    t_limestone,                                                           # 4
    lambda s: t_wood(s, (107, 76, 51)),                                    # 5
    lambda s: t_wood(s, (62, 46, 34)),                                     # 6
    t_metal,                                                               # 7
    t_gravel,                                                              # 8
    t_carpet,                                                              # 9
    t_water,                                                               # 10
    t_paper,                                                               # 11
    t_moss,                                                                # 12
    t_rust,                                                                # 13
    t_cloud,                                                               # 14
    t_glass,                                                               # 15
]


def build_atlas(seed0):
    tiles = []
    for i, b in enumerate(BUILDERS):
        tiles.append(b(seed0 + i * 977).rgb())
    atlas = bytearray(A * A * 3)
    stride = A * 3
    tstride = T * 3
    for i, rgb in enumerate(tiles):
        col, row = i % 4, 3 - i // 4      # PNG haut->bas ; GL bas->haut
        ox, oy = col * T, row * T
        for y in range(T):
            dst = (oy + y) * stride + ox * 3
            atlas[dst:dst + tstride] = rgb[y * tstride:(y + 1) * tstride]
    return atlas


def graded(atlas, tr, tg, tb):
    out = bytearray(atlas)
    out[0::3] = bytes(atlas[0::3]).translate(tr)
    out[1::3] = bytes(atlas[1::3]).translate(tg)
    out[2::3] = bytes(atlas[2::3]).translate(tb)
    return out


def lut(fn):
    return bytes(clampb(fn(v)) for v in range(256))


# ---------------------------------------------------------------- fonds peints
def bg_menu():
    w, h = 2048, 1152
    rng = random.Random(9001)
    R = bytearray(w * h)
    G = bytearray(w * h)
    B = bytearray(w * h)
    rows = [rng.randbytes(w) for _ in range(9)]
    horiz = int(h * 0.66)
    for y in range(h):
        if y < horiz:
            k = y / float(horiz)
            r = 38 + 190 * k ** 1.6
            g = 54 + 118 * k ** 1.7
            b = 96 + 52 * k ** 1.5
        else:
            k = (y - horiz) / float(h - horiz)
            r = 228 - 190 * k
            g = 172 - 140 * k
            b = 148 - 118 * k
        nz = rows[y % 9]
        tr = tbl(r, 14)
        tg = tbl(g, 14)
        tb = tbl(b, 14)
        s0, s1 = y * w, (y + 1) * w
        R[s0:s1] = nz.translate(tr)
        G[s0:s1] = nz.translate(tg)
        B[s0:s1] = nz.translate(tb)

    def blob(cx, cy, rad, tone, blend):
        for y in range(max(0, cy - rad), min(h, cy + rad + 1)):
            dy = y - cy
            dx2 = rad * rad - dy * dy
            if dx2 <= 0:
                continue
            dx = int(dx2 ** 0.5)
            x0, x1 = max(0, cx - dx), min(w, cx + dx + 1)
            s0, s1 = y * w + x0, y * w + x1
            nz = rows[y % 9][x0:x1]
            R[s0:s1] = nz.translate(mix_tbl(0, tone[0], blend, 10))
            G[s0:s1] = nz.translate(mix_tbl(0, tone[1], blend, 10))
            B[s0:s1] = nz.translate(mix_tbl(0, tone[2], blend, 10))

    # halo du soleil bas, a droite
    blob(int(w * 0.74), horiz - 40, 300, (255, 196, 120), 0.55)
    blob(int(w * 0.74), horiz - 40, 150, (255, 222, 170), 0.75)
    # silhouette de la ville : toits, quais, le Phare a gauche
    def tower(x, wdt, top, tone):
        for y in range(top, h):
            s0, s1 = y * w + x, y * w + min(w, x + wdt)
            v = (y * 37) % 7
            R[s0:s1] = bytes([tone[0] - v]) * (s1 - s0)
            G[s0:s1] = bytes([tone[1] - v]) * (s1 - s0)
            B[s0:s1] = bytes([tone[2] - v]) * (s1 - s0)

    dark = (26, 30, 42)
    tower(120, 96, int(h * 0.16), (34, 38, 52))          # le Phare
    blob(168, int(h * 0.16), 26, (255, 214, 150), 0.9)   # sa lampe
    x = 0
    while x < w:
        wdt = rng.randint(60, 190)
        top = rng.randint(int(h * 0.44), int(h * 0.70))
        if x < 320:
            top = rng.randint(int(h * 0.30), int(h * 0.52))
        tower(x, wdt, top, dark)
        if rng.random() < 0.5:
            tower(x + wdt // 3, wdt // 3, top - rng.randint(20, 90),
                  (20, 24, 34))
        x += wdt + rng.randint(4, 26)
    # fenetres allumees
    for i in range(420):
        x = rng.randint(0, w - 3)
        y = rng.randint(int(h * 0.40), h - 6)
        tone = (255, 190, 110) if rng.random() < 0.75 else (150, 220, 210)
        for yy in range(y, min(h, y + rng.randint(2, 5))):
            s0, s1 = yy * w + x, yy * w + min(w, x + rng.randint(2, 4))
            R[s0:s1] = bytes([tone[0]]) * (s1 - s0)
            G[s0:s1] = bytes([tone[1]]) * (s1 - s0)
            B[s0:s1] = bytes([tone[2]]) * (s1 - s0)
    out = bytearray(w * h * 3)
    out[0::3] = R
    out[1::3] = G
    out[2::3] = B
    return w, h, out


def bg_boot():
    w, h, img = bg_menu()
    rng = random.Random(9002)
    R, G, B = bytearray(img[0::3]), bytearray(img[1::3]), bytearray(img[2::3])
    # assombrir + sceau de cire ambre au centre
    tr = lut(lambda v: v * 0.42)
    tg = lut(lambda v: v * 0.44)
    tb = lut(lambda v: v * 0.50)
    R = R.translate(tr)
    G = G.translate(tg)
    B = B.translate(tb)
    cx, cy, rad = w // 2, int(h * 0.52), 150

    def seal_ring(y):
        dy = y - cy
        dx2 = rad * rad - dy * dy
        if dx2 <= 0:
            return None
        dx = int(dx2 ** 0.5)
        return max(0, cx - dx), min(w, cx + dx)

    for y in range(cy - rad, cy + rad + 1):
        sp = seal_ring(y)
        if not sp:
            continue
        x0, x1 = sp
        s0, s1 = y * w + x0, y * w + x1
        R[s0:s1] = bytes([196]) * (s1 - s0)
        G[s0:s1] = bytes([92]) * (s1 - s0)
        B[s0:s1] = bytes([36]) * (s1 - s0)
    for y in range(cy - rad + 14, cy + rad - 13):
        sp = seal_ring(y)
        if not sp:
            continue
        x0, x1 = sp[0] + 10, sp[1] - 10
        s0, s1 = y * w + x0, y * w + x1
        R[s0:s1] = bytes([226]) * (s1 - s0)
        G[s0:s1] = bytes([122]) * (s1 - s0)
        B[s0:s1] = bytes([52]) * (s1 - s0)
    # le V de Velmora grave dans la cire
    for k in range(-60, 61):
        yy = cy - 52 + int(abs(k) * 1.5)
        if 0 <= yy < h:
            for t2 in range(-3, 4):
                x = cx + k + t2
                if 0 <= x < w:
                    p = yy * w + x
                    R[p], G[p], B[p] = 140, 60, 24
    out = bytearray(w * h * 3)
    out[0::3] = R
    out[1::3] = G
    out[2::3] = B
    return w, h, out


def bg_journal():
    w, h = 2048, 1152
    rng = random.Random(9003)
    R = bytearray(w * h)
    G = bytearray(w * h)
    B = bytearray(w * h)
    rows = [rng.randbytes(w) for _ in range(9)]
    leather = (30, 26, 22)
    paper0 = (214, 204, 184)
    mx0, mx1 = int(w * 0.06), int(w * 0.94)
    my0, my1 = int(h * 0.07), int(h * 0.93)
    for y in range(h):
        nz = rows[y % 9]
        s0, s1 = y * w, (y + 1) * w
        inside = my0 <= y < my1
        if inside:
            R[s0:s1] = nz.translate(tbl(paper0[0], 16))
            G[s0:s1] = nz.translate(tbl(paper0[1], 16))
            B[s0:s1] = nz.translate(tbl(paper0[2], 18))
            R[s0:s0 + mx0] = nz[:mx0].translate(tbl(leather[0], 20))
            G[s0:s0 + mx0] = nz[:mx0].translate(tbl(leather[1], 20))
            B[s0:s0 + mx0] = nz[:mx0].translate(tbl(leather[2], 20))
            R[s0 + mx1:s1] = nz[mx1:].translate(tbl(leather[0], 20))
            G[s0 + mx1:s1] = nz[mx1:].translate(tbl(leather[1], 20))
            B[s0 + mx1:s1] = nz[mx1:].translate(tbl(leather[2], 20))
        else:
            R[s0:s1] = nz.translate(tbl(leather[0], 20))
            G[s0:s1] = nz.translate(tbl(leather[1], 20))
            B[s0:s1] = nz.translate(tbl(leather[2], 20))
    # lignes d'ecriture
    for y in range(my0 + 70, my1 - 40, 46):
        s0, s1 = y * w + mx0 + 40, y * w + mx1 - 40
        R[s0:s1] = bytes([176]) * (s1 - s0)
        G[s0:s1] = bytes([166]) * (s1 - s0)
        B[s0:s1] = bytes([148]) * (s1 - s0)
    # reliure ambre au centre
    cxx = w // 2
    for y in range(my0, my1):
        s0, s1 = y * w + cxx - 5, y * w + cxx + 5
        R[s0:s1] = bytes([92]) * 10
        G[s0:s1] = bytes([58]) * 10
        B[s0:s1] = bytes([34]) * 10
    out = bytearray(w * h * 3)
    out[0::3] = R
    out[1::3] = G
    out[2::3] = B
    return w, h, out


def bg_letter():
    w, h = 1600, 1200
    rng = random.Random(9004)
    R = bytearray(w * h)
    G = bytearray(w * h)
    B = bytearray(w * h)
    rows = [rng.randbytes(w) for _ in range(9)]
    tone = (232, 224, 206)
    for y in range(h):
        nz = rows[y % 9]
        edge = min(y, h - y) / 90.0
        k = 1.0 if edge >= 1 else 0.86 + 0.14 * edge
        s0, s1 = y * w, (y + 1) * w
        R[s0:s1] = nz.translate(tbl(tone[0] * k, 12))
        G[s0:s1] = nz.translate(tbl(tone[1] * k, 12))
        B[s0:s1] = nz.translate(tbl(tone[2] * k, 12))
    # fibres
    for i in range(5200):
        y = rng.randint(0, h - 1)
        x = rng.randint(0, w - 8)
        ln = rng.randint(2, 9)
        s0, s1 = y * w + x, y * w + min(w, x + ln)
        R[s0:s1] = bytes([tone[0] - 22]) * (s1 - s0)
        G[s0:s1] = bytes([tone[1] - 20]) * (s1 - s0)
        B[s0:s1] = bytes([tone[2] - 24]) * (s1 - s0)
    # lignes d'encre pale
    for y in range(150, h - 220, 54):
        x0 = 130 + rng.randint(-6, 6)
        x1 = w - 130 - rng.randint(0, 220)
        s0, s1 = y * w + x0, y * w + x1
        R[s0:s1] = bytes([120]) * (s1 - s0)
        G[s0:s1] = bytes([112]) * (s1 - s0)
        B[s0:s1] = bytes([104]) * (s1 - s0)
        s0, s1 = (y + 1) * w + x0, (y + 1) * w + x1
        R[s0:s1] = bytes([150]) * (s1 - s0)
        G[s0:s1] = bytes([142]) * (s1 - s0)
        B[s0:s1] = bytes([132]) * (s1 - s0)
    # sceau de cire en bas a droite
    cx, cy, rad = w - 240, h - 180, 74
    for y in range(cy - rad, min(h, cy + rad + 1)):
        dy = y - cy
        dx2 = rad * rad - dy * dy
        if dx2 <= 0 or y < 0:
            continue
        dx = int(dx2 ** 0.5)
        x0, x1 = max(0, cx - dx), min(w, cx + dx)
        s0, s1 = y * w + x0, y * w + x1
        R[s0:s1] = bytes([178]) * (s1 - s0)
        G[s0:s1] = bytes([74]) * (s1 - s0)
        B[s0:s1] = bytes([30]) * (s1 - s0)
    out = bytearray(w * h * 3)
    out[0::3] = R
    out[1::3] = G
    out[2::3] = B
    return w, h, out


def main():
    os.makedirs(OUT, exist_ok=True)
    total = 0
    base = build_atlas(424242)
    n = write_png(os.path.join(OUT, "atlas_base.png"), A, A, base)
    total += n
    print("atlas_base %.1f Mo" % (n / 1048576.0))
    chaud = graded(base,
                   lut(lambda v: v * 1.06 + 6), lut(lambda v: v * 0.99 + 3),
                   lut(lambda v: v * 0.86))
    n = write_png(os.path.join(OUT, "atlas_chaud.png"), A, A, chaud)
    total += n
    print("atlas_chaud %.1f Mo" % (n / 1048576.0))
    sombre = graded(base,
                    lut(lambda v: v * 0.70), lut(lambda v: v * 0.74 + 2),
                    lut(lambda v: v * 0.84 + 6))
    n = write_png(os.path.join(OUT, "atlas_sombre.png"), A, A, sombre)
    total += n
    print("atlas_sombre %.1f Mo" % (n / 1048576.0))
    aube = graded(base,
                  lut(lambda v: v * 1.12 + 12), lut(lambda v: v * 1.02 + 5),
                  lut(lambda v: v * 0.90))
    n = write_png(os.path.join(OUT, "atlas_aube.png"), A, A, aube)
    total += n
    print("atlas_aube %.1f Mo" % (n / 1048576.0))
    del base, chaud, sombre, aube
    for name, fn in (("menu", bg_menu), ("boot", bg_boot),
                     ("journal", bg_journal), ("lettre", bg_letter)):
        w, h, img = fn()
        n = write_png(os.path.join(OUT, name + ".png"), w, h, img)
        total += n
        print("%s %.1f Mo" % (name, n / 1048576.0))
    print("TOTAL %.1f Mo" % (total / 1048576.0))


if __name__ == "__main__":
    main()
