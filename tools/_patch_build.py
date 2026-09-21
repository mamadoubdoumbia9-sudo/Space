# -*- coding: utf-8 -*-
"""Remplace l'ecriture zip maison par un zipalign fidele a zipfile."""
import sys

P = "/home/user/Space/tools/build_apk.py"
s = open(P, encoding="utf-8").read()

old_start = s.index("def align_zip(src, dst):")
old_end = s.index("# --------------------------------------------------------------------------\n# 6. Signature")
new = '''def align_zip(src, dst):
    """
    zipalign maison : chaque entree STOCKEE doit commencer a un offset multiple
    de 4 (les ressources et les assets sont lus par mmap cote appareil). Les
    entrees compressees n'ont pas cette contrainte.

    On reconstruit l'archive avec zipfile, en bourrant le champ `extra` de
    l'en-tete local pour decaler les donnees ; l'offset courant est suivi a la
    main, exactement comme zipfile l'ecrit (en-tete 30 + nom + extra, puis la
    charge utile deflatee au niveau 9).
    """
    with zipfile.ZipFile(src, "r") as zin:
        entries = [(zin.getinfo(n), zin.read(n)) for n in zin.namelist()]
    pos = 0
    with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED, compresslevel=9,
                         allowZip64=False) as z:
        for info, data in entries:
            zi = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            zi.compress_type = info.compress_type
            zi.external_attr = info.external_attr
            name_b = zi.filename.encode("utf-8")
            extra = b""
            if zi.compress_type == zipfile.ZIP_STORED:
                need = (4 - ((pos + 30 + len(name_b)) % 4)) % 4
                extra = b"\\x00" * need
                payload_len = len(data)
            else:
                co = zlib.compressobj(9, zlib.DEFLATED, -15)
                payload_len = len(co.compress(data) + co.flush())
            zi.extra = extra
            z.writestr(zi, data)
            pos += 30 + len(name_b) + len(extra) + payload_len
    # verification : aucun entree stockee ne doit etre desalignee
    with zipfile.ZipFile(dst, "r") as z:
        for n in z.namelist():
            info = z.getinfo(n)
            if info.compress_type == zipfile.ZIP_STORED:
                off = info.header_offset + 30 + len(info.filename.encode("utf-8")) \\
                        + len(info.extra)
                if off % 4 != 0:
                    sys.exit("ERREUR : entree non alignee : " + n)
    return dst


'''
s = s[:old_start] + new + s[old_end:]

old = """    n = sum(len(fs) for _dp, _d, fs in os.walk(classes) if True
            for fs in [fs] if False) if False else sum(
        len([f for f in fs if f.endswith(".class")]) for _dp, _d, fs in os.walk(classes))
    step("javac : %d fichiers sources, %d classes" % (len(java), n))"""
new = """    n = sum(len([f for f in fs if f.endswith(".class")])
            for _dp, _d, fs in os.walk(classes))
    step("javac : %d fichiers sources, %d classes" % (len(java), n))"""
assert old in s
s = s.replace(old, new, 1)
open(P, "w", encoding="utf-8").write(s)
print("build_apk corrige")
