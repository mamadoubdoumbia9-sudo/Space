#!/usr/bin/env python3
"""Prépare les illustrations du jeu.

Sources : build/art_src/<famille>/<id>.png  (peintures générées, non versionnées)
Sorties : app/src/main/assets/art/<famille>/<id>.jpg (versionnées, embarquées)

Familles :
  zones/      tableaux 16:9      -> 1600x900, JPEG q86
  cin/        plans de cinématique 16:9 -> 1600x900, JPEG q86
  ui/         carte du monde 16:10 -> 1600x1000 ; autres à la taille source
  characters/ portraits carrés   -> 512x512 PNG RGBA (fond papier détouré → transparence)
  items/      icônes carrées     -> 256x256 PNG RGBA (fond papier détouré → transparence)

Le script génère aussi art/ui/grain.png (grain de papier procédural, tuilable)
et art/ui/vignette.png utilisés par l'interface.
"""
import os, sys, glob, math
from PIL import Image, ImageOps, ImageFilter
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "build", "art_src")
DST = os.path.join(ROOT, "app", "src", "main", "assets", "art")

SPECS = {
    "zones": ((1600, 900), 86),
    "cin": ((1600, 900), 86),
    "ui": (None, 88),
    "characters": ((512, 512), 88),
    "items": ((256, 256), 90),
}


def fit_cover(img, size):
    """Recadre au centre pour couvrir exactement `size` (sans déformation)."""
    tw, th = size
    w, h = img.size
    scale = max(tw / w, th / h)
    nw, nh = max(tw, round(w * scale)), max(th, round(h * scale))
    img = img.resize((nw, nh), Image.LANCZOS)
    left, top = (nw - tw) // 2, (nh - th) // 2
    return img.crop((left, top, left + tw, top + th))


def unpaper(img, soft=0.10, hard=0.55):
    """Détoure une peinture faite sur papier clair : le papier devient transparent,
    l'encre et les lavis gardent leur couleur (dé-mélange alpha, papier estimé sur les bords)."""
    a = np.asarray(img.convert("RGB")).astype(np.float32) / 255.0
    h, w, _ = a.shape
    m = max(4, min(h, w) // 40)
    border = np.concatenate([a[:m].reshape(-1, 3), a[-m:].reshape(-1, 3), a[:, :m].reshape(-1, 3), a[:, -m:].reshape(-1, 3)])
    paper = np.median(border, axis=0)
    d = np.max(np.abs(a - paper), axis=2)  # distance au papier (0 = papier pur)
    alpha = np.clip((d - soft) / (hard - soft), 0.0, 1.0)
    # légère érosion du halo : on adoucit puis on remonte le contraste
    alpha_img = Image.fromarray((alpha * 255).astype(np.uint8), "L").filter(ImageFilter.GaussianBlur(0.6))
    alpha = np.asarray(alpha_img).astype(np.float32) / 255.0
    safe = np.maximum(alpha, 1e-3)[..., None]
    rgb = paper + (a - paper) / safe
    rgb = np.clip(rgb, 0.0, 1.0)
    rgb[alpha < 0.02] = paper
    out = np.dstack([rgb, alpha[..., None]])
    return Image.fromarray((out * 255).astype(np.uint8), "RGBA")


def process(family, path, force=False):
    name = os.path.splitext(os.path.basename(path))[0]
    size, q = SPECS[family]
    out_dir = os.path.join(DST, family)
    os.makedirs(out_dir, exist_ok=True)
    png = family in ("characters", "items")
    out = os.path.join(out_dir, name + (".png" if png else ".jpg"))
    if not force and os.path.exists(out) and os.path.getmtime(out) >= os.path.getmtime(path):
        return False
    img = Image.open(path).convert("RGB")
    if family == "ui" and name == "map_world":
        img = fit_cover(img, (1600, 1000))
    elif size:
        img = fit_cover(img, size)
    if png:
        unpaper(img).save(out, "PNG", optimize=True)
    else:
        img.save(out, "JPEG", quality=q, optimize=True, progressive=True, subsampling=1)
    return True


def make_grain(path, n=256, seed=7):
    rng = np.random.default_rng(seed)
    noise = rng.normal(0, 1, (n, n))
    # bruit tuilable : on symétrise par FFT (périodique)
    f = np.fft.fft2(noise)
    yy, xx = np.meshgrid(np.fft.fftfreq(n), np.fft.fftfreq(n), indexing="ij")
    r = np.sqrt(xx * xx + yy * yy) + 1e-6
    f = f * (1.0 / (r ** 0.6))
    g = np.real(np.fft.ifft2(f))
    g = (g - g.mean()) / (g.std() + 1e-9)
    a = np.clip(128 + g * 22, 0, 255).astype(np.uint8)
    Image.fromarray(a, "L").save(path, "PNG", optimize=True)


def make_vignette(path, n=512):
    yy, xx = np.meshgrid(np.linspace(-1, 1, n), np.linspace(-1, 1, n), indexing="ij")
    d = np.sqrt(xx * xx * 0.85 + yy * yy)
    a = np.clip((d - 0.55) / 0.75, 0, 1) ** 1.6
    Image.fromarray((a * 255).astype(np.uint8), "L").save(path, "PNG", optimize=True)


def main():
    force = "--force" in sys.argv
    done = 0
    for family in SPECS:
        for p in sorted(glob.glob(os.path.join(SRC, family, "*.png")) + glob.glob(os.path.join(SRC, family, "*.jpg"))):
            if process(family, p, force):
                done += 1
    ui = os.path.join(DST, "ui")
    os.makedirs(ui, exist_ok=True)
    if force or not os.path.exists(os.path.join(ui, "grain.png")):
        make_grain(os.path.join(ui, "grain.png"))
    if force or not os.path.exists(os.path.join(ui, "vignette.png")):
        make_vignette(os.path.join(ui, "vignette.png"))
    total = 0
    for dp, _, fs in os.walk(DST):
        total += sum(os.path.getsize(os.path.join(dp, f)) for f in fs)
    print(f"process_art: {done} image(s) traitée(s) ; assets/art = {total/1e6:.1f} Mo")


if __name__ == "__main__":
    main()
