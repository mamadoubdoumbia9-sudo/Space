#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""anim_dedup.py — supprime les redites internes aux directions d'acteur.

Les passes de densification ont AJOUTE du texte a la suite de l'existant.
Quand la phrase ajoutee reformulait la phrase d'origine, la fiche se retrouve
a dire deux fois la meme chose : c'est du remplissage, meme si le volume
augmente. Ce script normalise chaque direction et supprime les phrases
redondantes, en gardant la formulation la PLUS informative (la plus longue,
et celle qui porte un marqueur [OBL]/[NAR]/[ART] ou un chiffre).
"""
from __future__ import annotations
import json, re, unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
P = ROOT / "anim_data" / "clips_744.json"

def norm(s: str) -> str:
    s = unicodedata.normalize("NFD", s.lower())
    s = "".join(c for c in s if unicodedata.category(c) != "Mn")
    s = re.sub(r"[^a-z0-9 ]", " ", s)
    return " ".join(s.split())

def words(s: str) -> set:
    return {w for w in norm(s).split() if len(w) > 3}

def split_sentences(t: str):
    parts = re.split(r"(?<=[.!?])\s+(?=[A-Z\[«])", t.strip())
    return [p.strip() for p in parts if p.strip()]

def score(s: str) -> tuple:
    return (bool(re.search(r"\[(OBL|NAR|ART|GAMEPLAY)\]", s)),
            bool(re.search(r"\d", s)), len(s))

def dedup(text: str) -> str:
    sents = split_sentences(text)
    keep: list[str] = []
    for s in sents:
        ws = words(s)
        if not ws:
            keep.append(s); continue
        dropped = False
        for i, k in enumerate(keep):
            kw = words(k)
            if not kw:
                continue
            inter = len(ws & kw)
            j = inter / max(1, min(len(ws), len(kw)))
            # sous-ensemble : « Des deux mains. » apres « ... des deux mains — ... »
            subset = ws <= kw or kw <= ws
            if j >= 0.72 or subset:             # la phrase redit une precedente
                if score(s) > score(k):
                    keep[i] = s                 # on garde la plus informative
                dropped = True
                break
        if not dropped:
            keep.append(s)
    return " ".join(keep)

def main() -> int:
    data = json.loads(P.read_text(encoding="utf-8"))
    n = 0
    gained = 0
    for c in data:
        j = c.get("jeu") or ""
        d = dedup(j)
        if d != j:
            gained += len(j) - len(d)
            c["jeu"] = d
            n += 1
    P.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    print("directions nettoyees : %d · %d caracteres de redite supprimes" % (n, gained))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
