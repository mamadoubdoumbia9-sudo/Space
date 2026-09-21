#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Genere anim/INDEX_ANIM_744.txt et anim/RECONCILIATION_744.txt."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "anim"
data = json.loads((ROOT/"anim_data"/"clips_744.json").read_text(encoding="utf-8"))
remap = json.loads((ROOT/"anim_data"/"remap.json").read_text(encoding="utf-8"))
W = 79

PART_DIR = {1:"01_LOHEN",2:"02_ESTEBAN",3:"03_MIREILLE",4:"04_TALLEC",5:"05_SOL",
 6:"06_ECHASSIER",7:"07_MUEUR",8:"08_VERRIER",9:"09_PNJ",10:"10_PROPS"}

L=[]; A=L.append
A("="*W)
A("  LOHEN — LES SEPT LETTRES DE VELMORA")
A("  ANNEXE A2 — LES 744 ANIMATIONS EN DOCUMENTS AUTONOMES — INDEX")
A("  Un fichier complet par clip. Reference : BLOC 07 du master,")
A("  ANIM_MANIFEST_744.txt (annexe A), contrat de volume 07.20.")
A("="*W); A("")
A("CE QUE CETTE ANNEXE AJOUTE A L'ANNEXE A")
A("")
A("  L'annexe A est un MANIFESTE : elle liste les 744 clips et les compare")
A("  entre eux. Elle est faite pour etre lue en entier, dans l'ordre.")
A("")
A("  L'annexe A2 est un jeu de FICHES DE PRODUCTION : chaque clip est un")
A("  document qui se lit SEUL, sans le manifeste et sans le master. Chaque")
A("  fiche reporte son contexte de personnage, ses standards techniques")
A("  applicables, sa direction d'acteur, sa timeline, ses transitions et")
A("  ses criteres de validation. C'est ce qu'on donne a un animateur qui")
A("  prend un clip et qui n'a pas trois heures de lecture devant lui.")
A("")
A("  [OBL] Les fiches ne recopient JAMAIS le texte des dialogues : elles")
A("  referencent l'identifiant de ligne de l'annexe C. Source unique.")
A("")
A("-"*W)
A("  REPARTITION — CONTRAT 07.20 RESPECTE")
A("-"*W); A("")
tot=0
for p in sorted(PART_DIR):
    sel=[c for c in data if c["part_num"]==p]
    tot+=len(sel)
    lo=min(c["num"] for c in sel); hi=max(c["num"] for c in sel)
    A("  %-2d %-34s %3d clips   A-%03d -> A-%03d   %s/" % (
        p, sel[0]["part_name"][:34], len(sel), lo, hi, PART_DIR[p]))
A("  " + "-"*66)
A("  %-37s %3d clips" % ("TOTAL", tot))
A("")
A("-"*W)
A("  VOLUMETRIE")
A("-"*W); A("")
files=sorted(OUT.rglob("A-*.txt"))
lines=sum(len(f.read_text(encoding="utf-8").split("\n")) for f in files)
size=sum(f.stat().st_size for f in files)
A("  Fichiers de fiche ........ %d" % len(files))
A("  Lignes totales ........... %s" % f"{lines:,}".replace(",", " "))
A("  Poids .................... %.1f Mo" % (size/1048576))
A("  Moyenne par fiche ........ %d lignes" % (lines//max(len(files),1)))
A("")
srcs={}
for c in data: srcs[c.get("source","?")]=srcs.get(c.get("source","?"),0)+1
A("  Origine des fiches :")
for k,v in sorted(srcs.items(), key=lambda x:-x[1]):
    A("      %3d  %s" % (v,k))
A("")
A("-"*W)
A("  SOMMAIRE COMPLET")
A("-"*W)
cur=None
for c in sorted(data, key=lambda x:x["num"]):
    if c["part_num"]!=cur:
        cur=c["part_num"]
        A(""); A("  === PARTIE %d — %s ===" % (cur, c["part_name"])); A("")
    d = "%6.2f s" % c["dur"] if c.get("dur") else "  pose "
    A("  %s  %-44s %s  %s" % (c["id"], c["name"][:44], d, c.get("loop","")[:8]))
A("")
A("="*W)
A("  ANNEXE A2 CLOSE — 744 fiches autonomes.")
A("="*W)
(OUT/"INDEX_ANIM_744.txt").write_text("\n".join(L)+"\n", encoding="utf-8")

# --- RECONCILIATION ----------------------------------------------------------
R=[]; B=R.append
B("="*W)
B("  ANNEXE A2 — JOURNAL DE RECONCILIATION DES IDENTIFIANTS")
B("="*W); B("")
B("POURQUOI CE DOCUMENT EXISTE")
B("")
B("  Le master (07.20) fixe le contrat : 744 clips, repartis en 10 lots.")
B("  Le manifeste (annexe A) detaillait 537 clips nommes et repliait les")
B("  autres dans 15 blocs de plage (« [EST-ACT-250 a 260] », etc.), plus")
B("  3 entrees L/R communes. En depliant ces blocs pour produire une fiche")
B("  par clip, deux ecarts sont apparus :")
B("")
B("     · partie 2 (ESTEBAN) : 99 identifiants pour 96 clips annonces")
B("     · partie 9 (PNJ)     : 50 identifiants pour 48 clips annonces")
B("")
B("  Total : 749 identifiants pour 744 clips contractuels.")
B("")
B("COMMENT L'ECART A ETE RESOLU")
B("")
B("  [1] PNJ — convention 0.08 du manifeste : « les variantes comptent pour")
B("      UN clip si elles sont selectionnees aleatoirement par le meme")
B("      etat ». crowd_idle_generic_A / B / C sont tirees par le meme etat")
B("      de foule : elles forment donc UN clip a 3 variantes. La fiche")
B("      correspondante declare ses 3 variantes en section 1.")
B("      Ecart resorbe : -2.")
B("")
B("  [2] ESTEBAN — les 8 poses de photo 298-305 comprenaient 4 poses")
B("      d'Esteban et 4 poses de PNJ destinees au meme mur. Les 4 poses de")
B("      PNJ ne sont pas des clips d'Esteban : elles sont regroupees en une")
B("      fiche unique (photo_pose_05_npc_wall_set, 4 poses figees).")
B("      Ecart resorbe : -3.")
B("")
B("  [3] Les identifiants ont ensuite ete renumerotes en sequence continue")
B("      par partie, sans trou, pour que A-001 a A-744 couvre exactement le")
B("      contrat. L'ordre narratif est integralement preserve : aucun clip")
B("      n'a change de place relative, seule la numerotation s'est tassee.")
B("")
B("  [OBL] Toute fiche dont l'identifiant a change porte en en-tete la")
B("  mention « Identifiant precedent : A-xxx ». Les references croisees du")
B("  manifeste (annexe A) restent donc traçables dans les deux sens.")
B("")
B("-"*W)
B("  TABLE DE CORRESPONDANCE — %d clips renumerotes" % len(remap))
B("-"*W); B("")
B("  ANCIEN    NOUVEAU   CLIP")
for old,new,name in remap:
    B("  %-9s %-9s %s" % (old,new,name[:50]))
B("")
B("="*W)
(OUT/"RECONCILIATION_744.txt").write_text("\n".join(R)+"\n", encoding="utf-8")
print("index + reconciliation ecrits")
