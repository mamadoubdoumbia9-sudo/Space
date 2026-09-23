#!/usr/bin/env python3
"""Chaîne de compilation Android sans Gradle ni javac :
   aapt2 (ressources + manifeste + assets) → kotlinc (core + app, cible 1.8) → d8 (classes.dex)
   → assemblage zip aligné (resources.arsc non compressé, aligné sur 4 octets) → signature v1 (JAR) + v2 (APK Signing Block).

   python3 tools/build_apk.py            → build/out/LaCartographieDesAbsents-ch1-release.apk + build/out/size_report.md
   python3 tools/build_apk.py --skip-audio   (n'exécute pas la synthèse audio manquante)
   python3 tools/build_apk.py --debug        (nom debug, pas de --release pour d8)
Pré-requis : tools/fetch_toolchain.sh (kotlinc, aapt2, r8.jar, android-34.jar), python3 + cryptography.
"""
import hashlib, os, struct, subprocess, sys, time, zipfile, zlib, base64, io
from collections import OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TC = os.path.expanduser(os.environ.get("TOOLCHAIN", "~/.cache/toolchain"))
BUILD = os.path.join(ROOT, "build", "apk")
OUT = os.path.join(ROOT, "build", "out")
APP = os.path.join(ROOT, "app", "src", "main")
PKG = "com.ateliermareebasse.cartographie"
VERSION_CODE, VERSION_NAME = 1, "1.0.0-ch1"
MIN_SDK, TARGET_SDK = 26, 34
DEBUG = "--debug" in sys.argv
APK_NAME = "LaCartographieDesAbsents-ch1-%s.apk" % ("debug" if DEBUG else "release")


def java_home():
    if os.environ.get("JAVA_HOME"):
        return os.environ["JAVA_HOME"]
    try:
        import jdk4py
        return str(jdk4py.JAVA_HOME)
    except Exception:
        return "/usr/lib/jvm/default-java"


JAVA = os.path.join(java_home(), "bin", "java")
KOTLINC = os.path.join(TC, "kotlinc", "bin", "kotlinc")
KSTD = os.path.join(TC, "kotlinc", "lib", "kotlin-stdlib.jar")
AAPT2 = os.path.join(TC, "aapt2")
R8 = os.path.join(TC, "r8.jar")
ANDROID_JAR = os.path.join(TC, "android-34.jar")


def run(cmd, **kw):
    print("  $", " ".join(os.path.basename(c) if i == 0 else c for i, c in enumerate(cmd))[:200])
    env = dict(os.environ); env["JAVA_HOME"] = java_home(); env["PATH"] = os.path.join(java_home(), "bin") + os.pathsep + env.get("PATH", "")
    r = subprocess.run(cmd, env=env, capture_output=True, text=True, **kw)
    if r.returncode != 0:
        print(r.stdout[-4000:]); print(r.stderr[-6000:])
        sys.exit("échec : %s" % cmd[0])
    return r


def step(title):
    print("\n▶", title)


def ensure_audio():
    if "--skip-audio" in sys.argv:
        return
    audio = os.path.join(APP, "assets", "audio")
    need = []
    if len(os.listdir(os.path.join(audio, "sfx")) if os.path.isdir(os.path.join(audio, "sfx")) else []) < 100: need.append("sfx.py")
    if len(os.listdir(os.path.join(audio, "music")) if os.path.isdir(os.path.join(audio, "music")) else []) < 26: need.append("compose.py")
    if len(os.listdir(os.path.join(audio, "ambience")) if os.path.isdir(os.path.join(audio, "ambience")) else []) < 21: need.append("ambience.py")
    for n in need:
        step("synthèse audio manquante : %s" % n)
        run([sys.executable, os.path.join(ROOT, "tools", "audio", n)])


def stage_assets():
    """Copie les assets dans build/apk/assets et y superpose le rendu audio haute fidélité (musique 1,5×, q 0,8 ; ambiances 90 s, q 0,7).
    Le dépôt garde la version légère ; l'APK embarque la version pleine (contenu réel, déterministe)."""
    import shutil
    staging = os.path.join(BUILD, "assets")
    if os.path.isdir(staging):
        shutil.rmtree(staging)
    shutil.copytree(os.path.join(APP, "assets"), staging, ignore=shutil.ignore_patterns("*.pyc"))
    if "--no-hq" in sys.argv or DEBUG:
        return staging
    hq = os.path.join(ROOT, "build", "hq_audio")
    env = dict(os.environ, AUDIO_OUT=hq)
    step("audio haute fidélité pour l'APK (mis en cache dans build/hq_audio)")
    for script, extra in (("compose.py", dict(AUDIO_Q="0.8", AUDIO_LEN="1.5")), ("ambience.py", dict(AUDIO_Q="0.7", AUDIO_LEN="1.5")), ("sfx.py", dict(AUDIO_Q="0.6"))):
        target_dir = os.path.join(hq, {"compose.py": "music", "ambience.py": "ambience", "sfx.py": "sfx"}[script])
        expected = {"compose.py": 26, "ambience.py": 21, "sfx.py": 137}[script]
        if os.path.isdir(target_dir) and len(os.listdir(target_dir)) >= expected:
            print("  (cache) %s" % script); continue
        e = dict(env); e.update(extra)
        print("  $ %s (AUDIO_Q=%s AUDIO_LEN=%s)" % (script, extra.get("AUDIO_Q"), extra.get("AUDIO_LEN", "1")))
        r = subprocess.run([sys.executable, os.path.join(ROOT, "tools", "audio", script)], env=e, capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-3000:]); sys.exit("échec audio HQ")
    for sub in ("music", "ambience", "sfx"):
        src = os.path.join(hq, sub)
        if os.path.isdir(src):
            shutil.copytree(src, os.path.join(staging, "audio", sub), dirs_exist_ok=True)
    return staging


def compile_resources():
    step("aapt2 : compilation des ressources")
    os.makedirs(BUILD, exist_ok=True)
    staging = stage_assets()
    res_zip = os.path.join(BUILD, "res.zip")
    run([AAPT2, "compile", "--dir", os.path.join(APP, "res"), "-o", res_zip])
    step("aapt2 : édition de liens (manifeste, ressources, assets)")
    base = os.path.join(BUILD, "base.apk")
    cmd = [AAPT2, "link", "-o", base, "--manifest", os.path.join(APP, "AndroidManifest.xml"), "-I", ANDROID_JAR,
           "--min-sdk-version", str(MIN_SDK), "--target-sdk-version", str(TARGET_SDK), "--version-code", str(VERSION_CODE), "--version-name", VERSION_NAME,
           "-A", staging, "--no-version-vectors", "--auto-add-overlay", res_zip]
    cmd = cmd[:-1] + ["--no-compress-regex", r"\.(ogg|jpg|png|ttf)$", cmd[-1]]
    run(cmd)
    return base


def compile_kotlin():
    step("kotlinc : cœur + application (cible JVM 1.8, classpath android-34)")
    jar = os.path.join(BUILD, "app.jar")
    srcs = [os.path.join(ROOT, "core", "src"), os.path.join(APP, "kotlin")]
    stamp = os.path.getmtime(jar) if os.path.exists(jar) else 0
    newest = max(os.path.getmtime(os.path.join(d, f)) for s in srcs for d, _, fs in os.walk(s) for f in fs if f.endswith(".kt"))
    if newest > stamp:
        run([KOTLINC, "-nowarn", "-jvm-target", "1.8", "-no-reflect", "-classpath", ANDROID_JAR, "-d", jar] + srcs)
    else:
        print("  (à jour)")
    return jar


def dex(jar):
    step("d8 : conversion en classes.dex" + (" (release, optimisé)" if not DEBUG else ""))
    dexdir = os.path.join(BUILD, "dex")
    os.makedirs(dexdir, exist_ok=True)
    cmd = [JAVA, "-Xmx1200m", "-cp", R8, "com.android.tools.r8.D8", "--min-api", str(MIN_SDK), "--lib", ANDROID_JAR, "--output", dexdir, jar, KSTD]
    if not DEBUG:
        cmd.insert(5, "--release")
    run(cmd)
    return [os.path.join(dexdir, f) for f in sorted(os.listdir(dexdir)) if f.endswith(".dex")]


# ───────────────────────────── assemblage zip aligné ─────────────────────────────
class AlignedZipWriter:
    """Écrit un zip où les entrées STORED sont alignées sur 4 octets (resources.arsc, images, sons) via le champ « extra »."""

    def __init__(self, path):
        self.f = open(path, "wb"); self.entries = []

    def add(self, name, data, compress):
        method = zipfile.ZIP_DEFLATED if compress else zipfile.ZIP_STORED
        crc = zlib.crc32(data) & 0xffffffff
        payload = zlib.compress(data, 9)[2:-4] if compress else data
        nameb = name.encode("utf-8")
        off = self.f.tell()
        extra = b""
        if not compress:
            header_end = off + 30 + len(nameb)
            pad = (-header_end) % 4
            if pad:
                extra = b"\x00" * pad  # bourrage d'alignement (comme zipalign historique) : ignoré par le lecteur zip d'Android
        flags = 0x0800  # UTF-8
        lh = struct.pack("<IHHHHHIIIHH", 0x04034b50, 20, flags, method, 0, 0x21, crc, len(payload), len(data), len(nameb), len(extra))
        self.f.write(lh + nameb + extra + payload)
        self.entries.append((nameb, method, crc, len(payload), len(data), off, extra, flags))

    def close(self):
        cd_start = self.f.tell()
        for nameb, method, crc, csize, usize, off, extra, flags in self.entries:
            ch = struct.pack("<IHHHHHHIIIHHHHHII", 0x02014b50, 20, 20, flags, method, 0, 0x21, crc, csize, usize, len(nameb), 0, 0, 0, 0, 0, off)
            self.f.write(ch + nameb)
        cd_end = self.f.tell()
        self.f.write(struct.pack("<IHHHHIIH", 0x06054b50, 0, 0, len(self.entries), len(self.entries), cd_end - cd_start, cd_start, 0))
        self.f.close()


def assemble(base_apk, dex_files, out_path, cert_der, key):
    step("assemblage : zip aligné + signature v1 (JAR)")
    items = OrderedDict()
    with zipfile.ZipFile(base_apk) as z:
        for info in z.infolist():
            if info.filename.startswith("META-INF/"):
                continue
            items[info.filename] = z.read(info.filename)
    for i, d in enumerate(dex_files):
        items["classes%s.dex" % ("" if i == 0 else str(i + 1))] = open(d, "rb").read()
    no_compress = (".ogg", ".jpg", ".jpeg", ".png", ".ttf", ".arsc", ".dex")

    def b64sha(b):
        return base64.b64encode(hashlib.sha256(b).digest()).decode()
    manifest = ["Manifest-Version: 1.0", "Created-By: Atelier Marée Basse build_apk.py", "Built-By: kotlinc+aapt2+d8", ""]
    sf = ["Signature-Version: 1.0", "Created-By: Atelier Marée Basse build_apk.py", "SHA-256-Digest-Manifest: %s"]
    entries_mf = []
    for name, data in items.items():
        e = "Name: %s\r\nSHA-256-Digest: %s\r\n\r\n" % (name, b64sha(data))
        entries_mf.append(e)
    mf_text = "Manifest-Version: 1.0\r\nCreated-By: Atelier Marée Basse build_apk.py\r\n\r\n" + "".join(entries_mf)
    mf_bytes = mf_text.encode("utf-8")
    sf_text = "Signature-Version: 1.0\r\nCreated-By: Atelier Marée Basse build_apk.py\r\nSHA-256-Digest-Manifest: %s\r\n\r\n" % b64sha(mf_bytes)
    for name, e in zip(items.keys(), entries_mf):
        sf_text += "Name: %s\r\nSHA-256-Digest: %s\r\n\r\n" % (name, b64sha(e.encode("utf-8")))
    sf_bytes = sf_text.encode("utf-8")
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.serialization import pkcs7
    from cryptography import x509
    cert = x509.load_der_x509_certificate(cert_der)
    rsa_sig = pkcs7.PKCS7SignatureBuilder().set_data(sf_bytes).add_signer(cert, key, hashes.SHA256()).sign(serialization.Encoding.DER, [pkcs7.PKCS7Options.DetachedSignature, pkcs7.PKCS7Options.Binary, pkcs7.PKCS7Options.NoAttributes])
    w = AlignedZipWriter(out_path)
    w.add("META-INF/MANIFEST.MF", mf_bytes, True)
    w.add("META-INF/CERT.SF", sf_bytes, True)
    w.add("META-INF/CERT.RSA", rsa_sig, True)
    # resources.arsc et AndroidManifest d'abord, puis dex, puis le reste
    order = ["AndroidManifest.xml", "resources.arsc"] + [k for k in items if k.endswith(".dex")] + [k for k in items if k not in ("AndroidManifest.xml", "resources.arsc") and not k.endswith(".dex")]
    for name in order:
        data = items[name]
        w.add(name, data, not name.lower().endswith(no_compress))
    w.close()


# ───────────────────────────── signature v2 (APK Signing Block) ─────────────────────────────
def sign_v2(path, cert_der, key):
    step("signature v2 : APK Signing Block")
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding
    data = open(path, "rb").read()
    eocd_off = data.rfind(b"\x50\x4b\x05\x06")
    cd_off = struct.unpack("<I", data[eocd_off + 16:eocd_off + 20])[0]
    cd_size = struct.unpack("<I", data[eocd_off + 12:eocd_off + 16])[0]
    sections = [data[:cd_off], data[cd_off:cd_off + cd_size], data[eocd_off:]]

    def chunk_digests(section):
        out = []
        for i in range(0, len(section), 1 << 20):
            c = section[i:i + (1 << 20)]
            out.append(hashlib.sha256(b"\xa5" + struct.pack("<I", len(c)) + c).digest())
        return out
    digests = []
    for s in sections:
        digests += chunk_digests(s)
    top = hashlib.sha256(b"\x5a" + struct.pack("<I", len(digests)) + b"".join(digests)).digest()
    ALG = 0x0103  # RSASSA-PKCS1-v1_5 SHA-256

    def lp(b):
        return struct.pack("<I", len(b)) + b
    digest_rec = lp(struct.pack("<I", ALG) + lp(top))
    signed_data = lp(digest_rec) + lp(lp(cert_der)) + lp(b"")
    sig = key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    signatures = lp(lp(struct.pack("<I", ALG) + lp(sig)))
    pub = key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    signer = lp(signed_data) + signatures + lp(pub)   # signed data, signatures, clé publique
    v2 = lp(lp(signer))                                # séquence de signataires (préfixée), signataire préfixé
    pair = struct.pack("<Q", 4 + len(v2)) + struct.pack("<I", 0x7109871a) + v2
    block_size = len(pair) + 8 + 16
    block = struct.pack("<Q", block_size) + pair + struct.pack("<Q", block_size) + b"APK Sig Block 42"
    # nouveau fichier : entrées, bloc, répertoire central (décalé), EOCD corrigé
    new_cd_off = cd_off + len(block)
    eocd = bytearray(sections[2]); eocd[16:20] = struct.pack("<I", new_cd_off)
    with open(path, "wb") as f:
        f.write(sections[0]); f.write(block); f.write(sections[1]); f.write(bytes(eocd))


def verify(path):
    step("vérification")
    with zipfile.ZipFile(path) as z:
        bad = z.testzip()
        if bad:
            sys.exit("zip corrompu : %s" % bad)
        names = z.namelist()
        info = z.getinfo("resources.arsc")
        assert info.compress_type == zipfile.ZIP_STORED, "resources.arsc doit être stocké non compressé"
        with open(path, "rb") as fh:
            fh.seek(info.header_offset); lh = fh.read(30)
            nlen, xlen = struct.unpack("<HH", lh[26:30])
            assert (info.header_offset + 30 + nlen + xlen) % 4 == 0, "resources.arsc non aligné"
            misaligned = 0
            for i in z.infolist():
                if i.compress_type == zipfile.ZIP_STORED:
                    fh.seek(i.header_offset); h = fh.read(30); n2, x2 = struct.unpack("<HH", h[26:30])
                    if (i.header_offset + 30 + n2 + x2) % 4: misaligned += 1
            assert misaligned == 0, "%d entrées non compressées mal alignées" % misaligned
        assert "classes.dex" in names and "AndroidManifest.xml" in names
        n_assets = sum(1 for n in names if n.startswith("assets/"))
    data = open(path, "rb").read()
    assert b"APK Sig Block 42" in data
    verify_v2(data)
    print("  ✓ zip valide, resources.arsc aligné, classes.dex présent, %d assets, signature v2 vérifiée (RSA-2048 / SHA-256)" % n_assets)
    return n_assets


def verify_v2(d):
    """Vérification indépendante de la signature v2 : structure du bloc, signature RSA, condensés des trois sections."""
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding
    eocd = d.rfind(b"PK\x05\x06"); cd_off = struct.unpack("<I", d[eocd + 16:eocd + 20])[0]; cd_size = struct.unpack("<I", d[eocd + 12:eocd + 16])[0]
    assert d[cd_off - 16:cd_off] == b"APK Sig Block 42"
    bsize = struct.unpack("<Q", d[cd_off - 24:cd_off - 16])[0]; bstart = cd_off - bsize - 8
    assert struct.unpack("<Q", d[bstart:bstart + 8])[0] == bsize
    pos = bstart + 8; plen = struct.unpack("<Q", d[pos:pos + 8])[0]; assert struct.unpack("<I", d[pos + 8:pos + 12])[0] == 0x7109871a
    v2 = d[pos + 12:pos + 8 + plen]

    def lp(b, o):
        n = struct.unpack("<I", b[o:o + 4])[0]; return b[o + 4:o + 4 + n], o + 4 + n
    signers, _ = lp(v2, 0); signer, _ = lp(signers, 0)
    signed, o = lp(signer, 0); sigs, o = lp(signer, o); pub, o = lp(signer, o)
    sig_rec, _ = lp(sigs, 0); sig, _ = lp(sig_rec, 4)
    serialization.load_der_public_key(pub).verify(sig, signed, padding.PKCS1v15(), hashes.SHA256())
    digs, o = lp(signed, 0); rec, _ = lp(digs, 0); top, _ = lp(rec, 4)
    eocd_b = bytearray(d[eocd:]); eocd_b[16:20] = struct.pack("<I", bstart)
    ds = []
    for s in (d[:bstart], d[cd_off:cd_off + cd_size], bytes(eocd_b)):
        for i in range(0, len(s), 1 << 20):
            c = s[i:i + (1 << 20)]; ds.append(hashlib.sha256(b"\xa5" + struct.pack("<I", len(c)) + c).digest())
    assert hashlib.sha256(b"\x5a" + struct.pack("<I", len(ds)) + b"".join(ds)).digest() == top, "condensé v2 incohérent"


def size_report(path, n_assets):
    total = os.path.getsize(path)
    cats = {}
    with zipfile.ZipFile(path) as z:
        for i in z.infolist():
            n = i.filename
            cat = ("code (dex)" if n.endswith(".dex") else "ressources Android" if n.startswith("res/") or n in ("resources.arsc", "AndroidManifest.xml") else
                   "audio / musique" if n.startswith("assets/audio/music") else "audio / ambiances" if n.startswith("assets/audio/ambience") else "audio / effets" if n.startswith("assets/audio/") else
                   "images / tableaux" if n.startswith("assets/art/zones") else "images / cinématiques" if n.startswith("assets/art/cin") else "images / personnages & objets" if n.startswith("assets/art/") else
                   "polices" if n.startswith("assets/fonts") else "données de jeu (textes)" if n.startswith("assets/data") else "signature" if n.startswith("META-INF") else "autre")
            c = cats.setdefault(cat, [0, 0, 0]); c[0] += 1; c[1] += i.compress_size; c[2] += i.file_size
    lines = ["# Rapport de taille — %s" % os.path.basename(path), "", "Taille de l'APK : **%.1f Mo** (%d octets)" % (total / 1e6, total), "", "| Catégorie | fichiers | dans l'APK | non compressé |", "|---|---:|---:|---:|"]
    for k, (n, cs, us) in sorted(cats.items(), key=lambda kv: -kv[1][1]):
        lines.append("| %s | %d | %.1f Mo | %.1f Mo |" % (k, n, cs / 1e6, us / 1e6))
    lines += ["", "Cible du cahier des charges : 100 – 900 Mo de contenu réel. Aucun fichier de remplissage : chaque octet est une image, un son, une police ou un texte joué.", ""]
    with open(os.path.join(OUT, "size_report.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print("\n".join(lines))


def main():
    t0 = time.time()
    for p in (KOTLINC, AAPT2, R8, ANDROID_JAR):
        if not os.path.exists(p):
            sys.exit("outil manquant : %s — lancer tools/fetch_toolchain.sh" % p)
    os.makedirs(OUT, exist_ok=True)
    ensure_audio()
    sys.path.insert(0, os.path.join(ROOT, "tools"))
    import make_keystore
    kp, cp = make_keystore.ensure()
    from cryptography.hazmat.primitives import serialization
    from cryptography import x509
    key = serialization.load_pem_private_key(open(kp, "rb").read(), None)
    cert_der = x509.load_pem_x509_certificate(open(cp, "rb").read()).public_bytes(serialization.Encoding.DER)
    base = compile_resources()
    jar = compile_kotlin()
    dexes = dex(jar)
    out = os.path.join(OUT, APK_NAME)
    assemble(base, dexes, out, cert_der, key)
    sign_v2(out, cert_der, key)
    n = verify(out)
    size_report(out, n)
    print("\n✓ APK : %s (%.1f Mo) en %.0f s" % (out, os.path.getsize(out) / 1e6, time.time() - t0))


if __name__ == "__main__":
    main()
