# -*- coding: utf-8 -*-
"""zipalign en deux passes : les offsets reels, jamais simules."""
import sys

P = "/home/user/Space/tools/build_apk.py"
s = open(P, encoding="utf-8").read()
start = s.index("def align_zip(src, dst):")
end = s.index("# --------------------------------------------------------------------------\n# 6. Signature")
new = '''def align_zip(src, dst):
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
            zi.extra = b"\\x00" * pads[info.filename]
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


'''
s = s[:start] + new + s[end:]
open(P, "w", encoding="utf-8").write(s)
print("align_zip en deux passes")
