#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
LOHEN — build_apk.py : le compilateur d'APK natif.

Ce script produit dist/LOHEN-chapitre1.apk, un APK 100 % natif : aucun
WebView, aucune ressource telechargee, aucun moteur externe. Il enchaîne :

  1. la synchronisation du contenu genere (content/) vers android/assets/ ;
  2. la compilation Java (ecj) contre android.jar ;
  3. le dexing (d8, min-api 26) ;
  4. la compilation puis l'edition des ressources (aapt2) ;
  5. l'insertion du dex, l'alignement 4 octets (zipalign maison) ;
  6. la signature (apksigner, cle de debug fournie).

Pourquoi pas Gradle : les depots Maven et Gradle sont inaccessibles dans cet
environnement (voir docs/placeholders.md, ADR-02). Les cinq outils ci-dessous
sont des binaires autonomes ; leurs emplacements se surchargent par variable
d'environnement :

  LOHEN_JAVA      binaire java           (defaut : jdk4py du site-packages)
  LOHEN_ECJ       ecj.jar
  LOHEN_D8        d8.jar
  LOHEN_ANDROID   android.jar
  LOHEN_AAPT2     binaire aapt2 Linux
  LOHEN_SIGNER    apksigner.jar
  LOHEN_KEYSTORE  keystore de signature

Sortie : dist/LOHEN-chapitre1.apk
"""
import os
import shutil
import struct
import subprocess
import sys
import zipfile
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANDROID = os.path.join(ROOT, "android")
SRC = os.path.join(ANDROID, "src", "java")
RES = os.path.join(ANDROID, "res")
ASSETS = os.path.join(ANDROID, "assets")
CONTENT = os.path.join(ROOT, "content")
BUILD = os.path.join(ROOT, "build")
DIST = os.path.join(ROOT, "dist")

DEFAULTS = {
    "LOHEN_JAVA": "/usr/local/lib/python3.11/dist-packages/jdk4py/java-runtime/bin/java",
    "LOHEN_ECJ": "/tmp/tools/ecj-3.45.0.jar",
    "LOHEN_D8": "/tmp/tools/d8.jar",
    "LOHEN_ANDROID": "/tmp/tools/android.jar",
    "LOHEN_AAPT2": "/tmp/npm2/package/bin/x64/linux/aapt2",
    "LOHEN_SIGNER": "/tmp/tools/apksigner.jar",
    "LOHEN_KEYSTORE": "/tmp/tools/debug.keystore",
}


def tool(key):
    p = os.environ.get(key, DEFAULTS[key])
    if not os.path.exists(p):
        sys.exit("ERREUR : outil introuvable %s=%s\n"
                 "Re-provisionnez la toolchain (voir docs/placeholders.md, ADR-02)."
                 % (key, p))
    return p


def run(cmd, **kw):
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    if r.returncode != 0:
        sys.exit("ERREUR (%d) : %s\n%s\n%s"
                 % (r.returncode, " ".join(os.path.basename(c) for c in cmd[:3]),
                    r.stdout[-4000:], r.stderr[-4000:]))
    return r.stdout


def step(msg):
    print("· " + msg)


# --------------------------------------------------------------------------
# 1. Contenu -> assets
# --------------------------------------------------------------------------
def sync_content():
    dst = os.path.join(ASSETS, "content")
    os.makedirs(dst, exist_ok=True)
    copied = 0
    for dirpath, _dirs, files in os.walk(CONTENT):
        rel = os.path.relpath(dirpath, CONTENT)
        out = dst if rel == "." else os.path.join(dst, rel)
        os.makedirs(out, exist_ok=True)
        for f in files:
            if not (f.endswith(".json") or f.endswith(".png")):
                continue
            s = os.path.join(dirpath, f)
            d = os.path.join(out, f)
            if not os.path.exists(d) or os.path.getmtime(s) > os.path.getmtime(d):
                shutil.copyfile(s, d)
                copied += 1
    total = sum(os.path.getsize(os.path.join(dp, f))
                for dp, _d, fs in os.walk(dst) for f in fs)
    step("contenu : %d fichier(s) mis a jour, %d Ko dans assets" % (copied, total // 1024))


# --------------------------------------------------------------------------
# 2..3. Java -> classes -> dex
# --------------------------------------------------------------------------
def compile_java():
    classes = os.path.join(BUILD, "classes")
    shutil.rmtree(classes, ignore_errors=True)
    os.makedirs(classes, exist_ok=True)
    srcs = os.path.join(BUILD, "sources.txt")
    os.makedirs(BUILD, exist_ok=True)
    java = []
    for dp, _d, fs in os.walk(SRC):
        for f in fs:
            if f.endswith(".java"):
                java.append(os.path.join(dp, f))
    java.sort()
    with open(srcs, "w", encoding="utf-8") as fh:
        fh.write("\n".join(java) + "\n")
    run([tool("LOHEN_JAVA"), "-jar", tool("LOHEN_ECJ"),
         "-source", "8", "-target", "8", "-encoding", "UTF-8",
         "-bootclasspath", tool("LOHEN_ANDROID"), "-nowarn",
         "-d", classes, "@" + srcs])
    n = sum(len([f for f in fs if f.endswith(".class")])
            for _dp, _d, fs in os.walk(classes))
    step("javac : %d fichiers sources, %d classes" % (len(java), n))
    return classes


def dex(classes):
    dexout = os.path.join(BUILD, "dex")
    shutil.rmtree(dexout, ignore_errors=True)
    os.makedirs(dexout, exist_ok=True)
    class_files = []
    for dp, _d, fs in os.walk(classes):
        for f in fs:
            if f.endswith(".class"):
                class_files.append(os.path.join(dp, f))
    class_files.sort()
    run([tool("LOHEN_JAVA"), "-cp", tool("LOHEN_D8"), "com.android.tools.r8.D8",
         "--lib", tool("LOHEN_ANDROID"), "--min-api", "26",
         "--output", dexout] + class_files)
    step("d8 : classes.dex (%d Ko)"
         % (os.path.getsize(os.path.join(dexout, "classes.dex")) // 1024))
    return os.path.join(dexout, "classes.dex")


# --------------------------------------------------------------------------
# 4. Ressources
# --------------------------------------------------------------------------
def resources():
    os.makedirs(BUILD, exist_ok=True)
    reszip = os.path.join(BUILD, "res.zip")
    base = os.path.join(BUILD, "base.apk")
    if os.path.exists(reszip):
        os.remove(reszip)
    run([tool("LOHEN_AAPT2"), "compile", "--dir", RES, "-o", reszip])
    run([tool("LOHEN_AAPT2"), "link", "-o", base,
         "-I", tool("LOHEN_ANDROID"),
         "--manifest", os.path.join(ANDROID, "AndroidManifest.xml"),
         "-A", ASSETS,
         "--auto-add-overlay",
         "--min-sdk-version", "26", "--target-sdk-version", "35",
         "--version-code", "1", "--version-name", "1.0-chapitre1",
         reszip])
    step("aapt2 : ressources + manifeste + assets lies")
    return base


# --------------------------------------------------------------------------
# 5. Assemblage + alignement
# --------------------------------------------------------------------------
def assemble(base, dex_path):
    unaligned = os.path.join(BUILD, "unaligned.apk")
    aligned = os.path.join(BUILD, "aligned.apk")
    with zipfile.ZipFile(base, "r") as zin:
        names = zin.namelist()
        with zipfile.ZipFile(unaligned, "w", zipfile.ZIP_DEFLATED) as zout:
            for n in names:
                info = zin.getinfo(n)
                data = zin.read(n)
                zinfo = zipfile.ZipInfo(n, date_time=(2026, 1, 1, 0, 0, 0))
                zinfo.compress_type = info.compress_type
                zinfo.external_attr = info.external_attr
                zout.writestr(zinfo, data)
            zinfo = zipfile.ZipInfo("classes.dex", date_time=(2026, 1, 1, 0, 0, 0))
            zinfo.compress_type = zipfile.ZIP_DEFLATED
            with open(dex_path, "rb") as fh:
                zout.writestr(zinfo, fh.read())
    align_zip(unaligned, aligned)
    step("assemblage + alignement 4 octets")
    return aligned


def align_zip(src, dst):
    """
    zipalign maison, en deux passes.

    Chaque entree STOCKEE doit voir ses donnees commencer a un offset multiple
    de 4 : c'est ainsi que l'appareil mmap resources.arsc et les assets sans
    copie (aapt2 les ecrit deja non compresses). Les entrees compressees n'ont
    pas cette contrainte.

    Passe 1 : on ecrit l'archive sans bourrage et on releve les offsets REELS
    des en-tetes locaux (la longueur deflatee depend du niveau de compression,
    on ne la devine pas).
    Passe 2 : on reecrit en ajoutant a chaque entree stockee un bourrage dans
    le champ `extra`, calcule pour que donnees + bourrages precedents tombent
    sur un multiple de 4.
    """
    def local_data_offset(path, info):
        with open(path, "rb") as f:
            f.seek(info.header_offset + 26)
            nl, el = struct.unpack("<HH", f.read(4))
        return info.header_offset + 30 + nl + el

    pads = {}
    shift = 0
    with zipfile.ZipFile(src, "r") as z:
        names = z.namelist()
        infos = [(z.getinfo(n), z.read(n)) for n in names]
    # passe 1 : archive temoin, sans bourrage
    tmp_path = dst + ".tmp"
    with zipfile.ZipFile(tmp_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9,
                         allowZip64=False) as z:
        for info, data in infos:
            zi = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            zi.compress_type = info.compress_type
            zi.external_attr = info.external_attr
            z.writestr(zi, data)
    with zipfile.ZipFile(tmp_path, "r") as z:
        for n in names:
            info = z.getinfo(n)
            if info.compress_type != zipfile.ZIP_STORED:
                pads[n] = 0
                continue
            d = local_data_offset(tmp_path, info) + shift
            need = (4 - d % 4) % 4
            pads[n] = need
            shift += need
    os.remove(tmp_path)

    # passe 2 : l'archive finale, bourree
    with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED, compresslevel=9,
                         allowZip64=False) as z:
        for info, data in infos:
            zi = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            zi.compress_type = info.compress_type
            zi.external_attr = info.external_attr
            zi.extra = b"\x00" * pads[info.filename]
            z.writestr(zi, data)

    # verification : aucune entree stockee desalignee, archive relisible
    with zipfile.ZipFile(dst, "r") as z:
        if z.testzip() is not None:
            sys.exit("ERREUR : archive corrompue apres alignement")
        for n in z.namelist():
            info = z.getinfo(n)
            if info.compress_type == zipfile.ZIP_STORED:
                if local_data_offset(dst, info) % 4 != 0:
                    sys.exit("ERREUR : entree non alignee : " + n)
    return dst


# --------------------------------------------------------------------------
# 6. Signature
# --------------------------------------------------------------------------
def sign(aligned):
    os.makedirs(DIST, exist_ok=True)
    out = os.path.join(DIST, "LOHEN-chapitre1.apk")
    if os.path.exists(out):
        os.remove(out)
    shutil.copyfile(aligned, out)
    run([tool("LOHEN_JAVA"), "-jar", tool("LOHEN_SIGNER"), "sign",
         "--ks", tool("LOHEN_KEYSTORE"),
         "--ks-pass", "pass:android",
         "--ks-key-alias", "androiddebugkey",
         "--key-pass", "pass:android",
         out])
    run([tool("LOHEN_JAVA"), "-jar", tool("LOHEN_SIGNER"), "verify",
         "--print-certs", out])
    step("signature : %s (%d Mo)" % (os.path.relpath(out, ROOT),
                                     os.path.getsize(out) // (1024 * 1024)))
    return out


def main():
    os.makedirs(BUILD, exist_ok=True)
    sync_content()
    classes = compile_java()
    dex_path = dex(classes)
    base = resources()
    aligned = assemble(base, dex_path)
    out = sign(aligned)
    print("\nAPK pret : %s" % os.path.relpath(out, ROOT))


if __name__ == "__main__":
    main()
