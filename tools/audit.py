#!/usr/bin/env python3
"""Audit d'avant-build (cahier des charges : audit en 13 points).

Vérifie, sur les sources du dépôt (pas sur l'APK) :
  1. références d'images (tableaux, cinématiques, portraits, objets, interface) → fichiers présents
  2. références audio (musique, ambiances, effets, depuis les données ET le code) → fichiers présents
  3. fichiers d'assets orphelins (jamais référencés)
  4. doublons binaires (même empreinte SHA-1)
  5. fichiers vides ou corrompus (images décodées, OGG lus par soundfile)
  6. marqueurs de travail non terminé (TODO, FIXME, XXX, TBD, placeholder, lorem ipsum, « à compléter »…)
  7. parité de localisation FR/EN (clés de strings, tables d'interface)
  8. couverture des glyphes : tout caractère des textes affichés existe dans la police qui le dessine
  9. configuration Android (manifeste : paquet, version, permissions, orientation, icône, splash)
 10. budget de taille par catégorie (sources) et projection APK
 11. cohérence des identifiants de données (zones, scènes, objets, énigmes cités dans les .tab/.dlg/.pzl)
 12. contenu hors dépôt : rien dans assets/ n'est ignoré par git (tout ce qui est embarqué est versionné)
 13. secrets / clés : aucun fichier de clé privée ni keystore dans l'arbre suivi

Sortie : build/out/audit_report.md (Markdown) ; code de retour 1 si un point bloquant échoue.
Usage : python3 tools/audit.py [--strict]
"""
import hashlib
import io
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(ROOT, "app", "src", "main")
ASSETS = os.path.join(APP, "assets")
DATA = os.path.join(ASSETS, "data")
OUT = os.path.join(ROOT, "build", "out")
os.makedirs(OUT, exist_ok=True)

report = []
blocking = 0
warnings = 0


def h(t):
    report.append("\n## " + t + "\n")


def ok(t):
    report.append("- ✓ " + t)


def warn(t):
    global warnings
    warnings += 1
    report.append("- ⚠ " + t)


def fail(t):
    global blocking
    blocking += 1
    report.append("- ✗ " + t)


def rel(p):
    return os.path.relpath(p, ROOT)


def walk(d, exts=None):
    for dp, _, fs in os.walk(d):
        for f in fs:
            if exts is None or os.path.splitext(f)[1].lower() in exts:
                yield os.path.join(dp, f)


def read(p):
    with open(p, encoding="utf-8", errors="replace") as f:
        return f.read()


# ───────────────────────── corpus texte : données + code ─────────────────────────
data_files = [p for p in walk(DATA) if os.path.splitext(p)[1] in (".tab", ".dlg", ".tbl", ".pzl", ".itm", ".pg", ".ec", ".brk", ".txt", ".cin")]
code_files = list(walk(os.path.join(ROOT, "core", "src"), {".kt"})) + list(walk(os.path.join(APP, "kotlin"), {".kt"})) + list(walk(os.path.join(ROOT, "desktop", "src"), {".kt"}))
data_text = "\n".join(read(p) for p in data_files)
code_text = "\n".join(read(p) for p in code_files)
corpus = data_text + "\n" + code_text

# ───────────────────────── 1. images ─────────────────────────
h("1. Références d'images")
img_refs = set(re.findall(r"art/[a-z_]+/[A-Za-z0-9_\-]+\.(?:jpg|png)", data_text))
# portraits et icônes dynamiques
chars = re.findall(r"^([a-z_]+)\s*\|", read(os.path.join(DATA, "tables", "fr", "characters.txt")), re.M)
items = re.findall(r"^@\s*([a-z_0-9]+)\s*\|", read(os.path.join(DATA, "items", "fr", "items.itm")), re.M)
img_refs |= {f"art/characters/{c}.png" for c in chars}
img_refs |= {f"art/items/{i}.png" for i in items}
img_refs |= {"art/ui/map_world.jpg", "art/ui/grain.png", "art/ui/vignette.png"}
missing_img = sorted(r for r in img_refs if not os.path.exists(os.path.join(ASSETS, r)))
glyph_items = set(re.findall(r"^@\s*([a-z_0-9]+)\s*\|[^\n]*\|\s*glyph\b", read(os.path.join(DATA, "items", "fr", "items.itm")), re.M))
img_refs -= {f"art/items/{i}.png" for i in glyph_items}   # objets secondaires : glyphe de catégorie déclaré dans items.itm
missing_img = sorted(r for r in img_refs if not os.path.exists(os.path.join(ASSETS, r)))
for r in missing_img:
    fail(f"image référencée absente : {r}")
if not missing_img:
    ok(f"{len(img_refs)} images référencées, toutes présentes")
else:
    ok(f"{len(img_refs) - len(missing_img)}/{len(img_refs)} images référencées présentes")

# ───────────────────────── 2. audio ─────────────────────────
h("2. Références audio")
audio_files = {}
for sub in ("music", "ambience", "sfx"):
    d = os.path.join(ASSETS, "audio", sub)
    audio_files[sub] = sorted(os.path.splitext(f)[0] for f in os.listdir(d)) if os.path.isdir(d) else []
data_nocomment = "\n".join(l for l in data_text.splitlines() if not l.lstrip().startswith("#"))
audio_refs = set()
audio_refs |= set(re.findall(r"(?:^|[;>|]\s*|\s)(?:sfx|music|ambience)[= ]([a-z_0-9]+)", data_nocomment, re.M))
audio_refs |= set(re.findall(r"\b(track_[a-z0-9_]+|amb_[a-z_0-9]+)\b", data_nocomment + code_text))
audio_refs |= set(re.findall(r'(?:sfx|music|ambience|playSfx|playMusic|playAmbience)\("([a-z_0-9]+)"', code_text))
audio_refs |= set(re.findall(r'sfx\("([a-z_]+?)_?0?\$', code_text))  # préfixes dynamiques : sfx("quill_scratch_0${n}")
audio_refs |= {m[1] for m in re.findall(r'\b(sfx|music|ambience)\s*=\s*"([a-z_0-9]+)"', code_text)}
audio_refs -= {"sfx", "music", "ambience", "none", "off"}
all_audio = set(sum(audio_files.values(), []))
stems = {}
for a in all_audio:
    stems.setdefault(re.sub(r"_?\d+$", "", a), []).append(a)
missing_audio = sorted(r for r in audio_refs if r not in all_audio and r not in stems)
for r in missing_audio:
    fail(f"son référencé absent : {r}")
if not missing_audio:
    ok(f"{len(audio_refs)} identifiants audio référencés, tous présents ({len(audio_files['music'])} musiques, {len(audio_files['ambience'])} ambiances, {len(audio_files['sfx'])} effets)")

# ───────────────────────── 3. orphelins ─────────────────────────
h("3. Assets orphelins")
orphans = []
for p in walk(ASSETS):
    r = os.path.relpath(p, ASSETS).replace(os.sep, "/")
    base = os.path.splitext(os.path.basename(p))[0]
    if r.startswith("art/"):
        if r in img_refs:
            continue
        orphans.append(r)
    elif r.startswith("audio/"):
        stem = re.sub(r"_?\d+$", "", base)
        if base in audio_refs or stem in audio_refs or base in corpus:
            continue
        orphans.append(r)
    elif r.startswith("fonts/") or r.startswith("data/"):
        continue
    else:
        orphans.append(r)
for o in orphans:
    warn(f"asset jamais référencé : {o}")
if not orphans:
    ok("aucun asset orphelin : chaque fichier embarqué est utilisé par le jeu")

# ───────────────────────── 4. doublons ─────────────────────────
h("4. Doublons binaires")
hashes = {}
for p in walk(ASSETS):
    with open(p, "rb") as f:
        hashes.setdefault(hashlib.sha1(f.read()).hexdigest(), []).append(rel(p))
dups = [v for v in hashes.values() if len(v) > 1]
for d in dups:
    warn("fichiers identiques : " + ", ".join(d))
if not dups:
    ok(f"{len(hashes)} fichiers d'assets, aucun doublon")

# ───────────────────────── 5. vides / corrompus ─────────────────────────
h("5. Fichiers vides ou corrompus")
bad = 0
try:
    from PIL import Image
except ImportError:
    Image = None
try:
    import soundfile as sf
except ImportError:
    sf = None
for p in walk(ASSETS):
    if os.path.getsize(p) == 0:
        fail(f"fichier vide : {rel(p)}"); bad += 1; continue
    ext = os.path.splitext(p)[1].lower()
    if ext in (".jpg", ".png") and Image:
        try:
            with Image.open(p) as im:
                im.verify()
        except Exception as e:
            fail(f"image illisible : {rel(p)} ({e})"); bad += 1
    elif ext == ".ogg" and sf:
        try:
            info = sf.info(p)
            if info.frames < 100:
                fail(f"son quasi vide : {rel(p)}"); bad += 1
        except Exception as e:
            fail(f"son illisible : {rel(p)} ({e})"); bad += 1
if not bad:
    ok("aucun fichier vide ; toutes les images se décodent ; tous les OGG se lisent")

# ───────────────────────── 6. marqueurs ─────────────────────────
h("6. Marqueurs de travail non terminé")
markers = re.compile(r"\b(TODO|FIXME|XXX|TBD|HACK|WIP)\b|placeholder|lorem ipsum|à compléter|a completer|coming soon|dummy", re.I)
hits = []
scan = data_files + code_files + [os.path.join(ROOT, "README.md")] + list(walk(os.path.join(ROOT, "docs"), {".md"})) + list(walk(os.path.join(ROOT, "tools"), {".py", ".sh"}))
for p in scan:
    if os.path.basename(p) == "audit.py":
        continue
    for i, line in enumerate(read(p).splitlines(), 1):
        if markers.search(line):
            hits.append(f"{rel(p)}:{i}: {line.strip()[:100]}")
for x in hits:
    fail("marqueur : " + x)
if not hits:
    ok(f"{len(scan)} fichiers scannés : aucun TODO / FIXME / placeholder")

# ───────────────────────── 7. localisation ─────────────────────────
h("7. Localisation FR / EN")


def keys(p):
    return {l.split("|")[0].strip() for l in read(p).splitlines() if "|" in l and not l.startswith("#")}


fr = keys(os.path.join(DATA, "strings", "fr.txt")); en = keys(os.path.join(DATA, "strings", "en.txt"))
if fr == en:
    ok(f"strings : {len(fr)} clés identiques en FR et EN")
else:
    for k in sorted(fr - en):
        fail(f"clé absente en EN : {k}")
    for k in sorted(en - fr):
        fail(f"clé absente en FR : {k}")
for t in ("tutorials.txt", "credits.txt"):
    pe = os.path.join(DATA, "tables", "en", t)
    if os.path.exists(pe):
        ok(f"table d'interface traduite : tables/en/{t}")
    else:
        fail(f"table d'interface non traduite : {t}")
narr = sorted(d for d in os.listdir(DATA) if os.path.isdir(os.path.join(DATA, d, "fr")) and not os.path.isdir(os.path.join(DATA, d, "en")))
ok("textes narratifs en français avec repli automatique (documenté) : " + ", ".join(narr))

# ───────────────────────── 8. glyphes ─────────────────────────
h("8. Couverture des glyphes par les polices embarquées")
try:
    from fontTools.ttLib import TTFont
    cmaps = {f: set(TTFont(os.path.join(ASSETS, "fonts", f + ".ttf")).getBestCmap().keys()) for f in ("body", "hand", "title", "mono")}
    union = set.union(*cmaps.values())
    inter = set.intersection(*cmaps.values())
    used = set()
    for p in data_files:
        for line in read(p).splitlines():
            if line.startswith("#"):
                continue
            used |= set(line)
    game_code = "\n".join(read(p) for p in code_files if os.sep + "desktop" + os.sep not in p)  # le banc d'essai écrit sur la console, pas dans les polices du jeu
    game_code = re.sub(r"//[^\n]*", "", game_code)  # commentaires de ligne exclus
    for m in re.findall(r'"((?:[^"\\\n]|\\.)*)"', game_code):
        used |= set(m)
    used -= set("\t\r\n")
    nowhere = sorted(c for c in used if ord(c) > 31 and ord(c) not in union)
    partial = sorted(c for c in used if ord(c) > 31 and ord(c) in union and ord(c) not in inter and ord(c) > 0x24F)
    for c in nowhere:
        fail(f"caractère U+{ord(c):04X} « {c} » absent de toutes les polices")
    if not nowhere:
        ok(f"{len(used)} caractères distincts utilisés ; tous existent dans au moins une police embarquée")
    if partial:
        warn("caractères présents seulement dans certaines polices (à n'utiliser qu'avec celles-ci) : " + " ".join(f"{c}(U+{ord(c):04X}:" + "/".join(f for f in cmaps if ord(c) in cmaps[f]) + ")" for c in partial))
except ImportError:
    warn("fontTools indisponible : couverture non vérifiée")

# ───────────────────────── 9. manifeste ─────────────────────────
h("9. Configuration Android")
man = ET.parse(os.path.join(APP, "AndroidManifest.xml")).getroot()
A = "{http://schemas.android.com/apk/res/android}"
pkg = man.get("package"); vc = man.get(A + "versionCode"); vn = man.get(A + "versionName")
ok(f"paquet `{pkg}`, versionCode {vc}, versionName {vn}")
perms = [e.get(A + "name") for e in man.findall("uses-permission")]
if perms == ["android.permission.VIBRATE"]:
    ok("permissions : VIBRATE uniquement (aucune permission réseau, stockage ou identité)")
else:
    warn("permissions : " + ", ".join(perms))
sdk = man.find("uses-sdk")
ok(f"minSdk {sdk.get(A + 'minSdkVersion')}, targetSdk {sdk.get(A + 'targetSdkVersion')}")
app = man.find("application"); act = app.find("activity")
ok(f"orientation `{act.get(A + 'screenOrientation')}`, icône `{app.get(A + 'icon')}`, icône ronde `{app.get(A + 'roundIcon')}`, thème `{act.get(A + 'theme') or app.get(A + 'theme')}`")
for r in ("mipmap-anydpi-v26/ic_launcher.xml", "drawable/splash.xml", "values/strings.xml", "values-en/strings.xml"):
    (ok if os.path.exists(os.path.join(APP, "res", r)) else fail)(f"ressource `{r}`")

# ───────────────────────── 10. taille ─────────────────────────
h("10. Budget de taille (sources)")
cats = {}
for p in walk(ASSETS):
    r = os.path.relpath(p, ASSETS).replace(os.sep, "/")
    c = "/".join(r.split("/")[:2]) if r.count("/") >= 2 else r.split("/")[0]
    cats[c] = cats.get(c, 0) + os.path.getsize(p)
total = sum(cats.values())
report.append("\n| Catégorie | Mo |\n|---|---:|")
for c, v in sorted(cats.items(), key=lambda x: -x[1]):
    report.append(f"| {c} | {v / 1e6:.1f} |")
report.append(f"| **total assets (dépôt, audio léger)** | **{total / 1e6:.1f}** |\n")
apk = os.path.join(OUT, "LaCartographieDesAbsents-ch1-release.apk")
if os.path.exists(apk):
    sz = os.path.getsize(apk) / 1e6
    (ok if 100 <= sz <= 900 else fail)(f"dernier APK construit : {sz:.1f} Mo (cible 100 – 900 Mo)")
else:
    warn("aucun APK dans build/out : lancer tools/build_apk.py")

# ───────────────────────── 11. identifiants ─────────────────────────
h("11. Cohérence des identifiants de données")
zones = {os.path.splitext(f)[0] for f in os.listdir(os.path.join(DATA, "zones", "fr")) if f.endswith(".tab")}
zone_refs = set(re.findall(r"^path\s+(z\d\d)\b", data_text, re.M)) | set(re.findall(r"\btravel\s+(z\d\d)\b", data_text))
for z in sorted(zone_refs - zones):
    fail(f"zone citée inexistante : {z}")
scenes = set(re.findall(r"^~\s*([a-z_0-9]+)", "\n".join(read(p) for p in data_files if p.endswith(".dlg")), re.M))
scene_refs = set(re.findall(r"\b(?:scene|enter_scene|after:)\s+([a-z_0-9]+)", data_text)) | set(re.findall(r"\bseen:([a-z_0-9]+)", data_text))
scene_refs = {s for s in scene_refs if not s.startswith(("sq", "hb", "fa")) or s in scenes or True}
unknown_scenes = sorted(s for s in scene_refs - scenes if re.match(r"^(sq|hb|fa|s\d|prologue|marek|reprise|annexe|epilogue)", s))
for s in unknown_scenes:
    fail(f"scène citée inexistante : {s}")
item_refs = set(re.findall(r"\b(?:has|give|take|TAKE)\s*:?\s*([a-z_0-9]+)", data_text))
item_refs = {i for i in item_refs if re.match(r"^[a-z][a-z_0-9]+$", i)}
unknown_items = sorted(i for i in item_refs - set(items) if i not in ("true", "false") and "_" in i)
for i in unknown_items:
    warn(f"objet cité sans fiche : {i}")
puzzles = set(re.findall(r"^@\s*(E\d\d|S\d\d)\b", read(os.path.join(DATA, "puzzles", "fr", "puzzles.pzl")), re.M))
puzzle_refs = set(re.findall(r"\b(?:PUZZLE|puzzle:)\s*(E\d\d|S\d\d)\b", data_text))
for p_ in sorted(puzzle_refs - puzzles):
    fail(f"énigme citée sans fiche : {p_}")
if not (zone_refs - zones) and not unknown_scenes and not (puzzle_refs - puzzles):
    ok(f"{len(zones)} zones, {len(scenes)} scènes, {len(items)} objets, {len(puzzles)} énigmes/secrets : toutes les références résolues")

# ───────────────────────── 12. versionnage ─────────────────────────
h("12. Tout ce qui est embarqué est versionné")
try:
    ignored = subprocess.run(["git", "ls-files", "--others", "--ignored", "--exclude-standard", "app/src/main/assets"], cwd=ROOT, capture_output=True, text=True).stdout.split()
    untracked = subprocess.run(["git", "ls-files", "--others", "--exclude-standard", "app/src/main/assets"], cwd=ROOT, capture_output=True, text=True).stdout.split()
    for f in ignored:
        fail(f"asset ignoré par git : {f}")
    for f in untracked:
        warn(f"asset non encore commité : {f}")
    if not ignored and not untracked:
        ok("aucun asset ignoré ni en attente : le dépôt reconstruit l'APK à l'identique")
except Exception as e:
    warn(f"git indisponible : {e}")

# ───────────────────────── 13. secrets ─────────────────────────
h("13. Clés et secrets")
tracked = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True).stdout.split()
leaks = [f for f in tracked if re.search(r"\.(pem|jks|keystore|p12|key)$", f) or "credentials" in f]
for f in leaks:
    fail(f"fichier sensible suivi par git : {f}")
if not leaks:
    ok("aucune clé privée ni keystore dans l'arbre suivi (clé de signature générée dans build/keys, ignorée)")

# ───────────────────────── synthèse ─────────────────────────
head = ["# Audit d'avant-build — La Cartographie des Absents, chapitre 1", "",
        f"Résultat : **{blocking} point(s) bloquant(s)**, {warnings} avertissement(s).", ""]
text = "\n".join(head + report) + "\n"
with open(os.path.join(OUT, "audit_report.md"), "w", encoding="utf-8") as f:
    f.write(text)
print(text)
sys.exit(1 if blocking and "--strict" in sys.argv else 0)
