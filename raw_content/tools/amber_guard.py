#!/usr/bin/env python3
"""
amber_guard.py --scan-shaders
=============================================================================
Garde-fou CI de l'annexe D (les 10 shaders signature).
Reference maitre : LOHEN_PROMPT_MASTER.txt — 05.16, 05.17-05.26, 09.21, 21.08.

Ce script ne compile rien (Godot n'est pas requis) : il verifie les REGLES
que le master impose et qu'un shader mal relu casse en silence.

Jobs implementes
----------------
  amber-guard          #FFA33C n'apparait que dans les materiaux d'Esteban
                       et dans la derogation declaree letter_paper:backlight_tint
  no-visual-shader     aucun .tres de VisualShader dans game/world/shaders
  no-ssr               aucune reference a un screen-space reflection
  waterline-12-30      stone_wet declare bien 12 m et 30 m
  no-outline           aucun shader n'expose de uniform de contour / rim
  budget-declared      chaque .gdshader annonce son budget d'instructions
  lighthouse-dark      lighthouse_lamp reste a 0.0
  ritual-duration      2.44 s, ecrit une seule fois dans le depot
  fragments-47         le pool de fragments vaut 47

Sortie : code 0 si tout passe, 1 sinon, avec la liste des violations.
=============================================================================
"""

from __future__ import annotations
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SHADER_DIR = ROOT / "game" / "world" / "shaders"

AMBER = re.compile(r"#?FFA33C|1\.0\s*,\s*0\.639\s*,\s*0\.235", re.IGNORECASE)

# Derogations declarees explicitement, avec leur justification.
AMBER_ALLOW = {
    "memory_person.gdshader": "lisere Esteban (E21), uniform is_esteban",
    "letter_paper.gdshader": "backlight_tint = la lampe a huile d'Esteban (19.03)",
}

# 09.21 interdit le contour COMME SIGNAL D'INTERACTION. Un fresnel sur une
# Figure de verre ou sur une silhouette de souvenir n'est pas un signal
# d'interaction : c'est la matiere elle-meme. La regle ne s'applique donc
# qu'aux shaders de decor et d'accessoires, ceux qui peuvent devenir
# interactifs. Derogation declaree, avec sa raison.
OUTLINE_ALLOW = {
    "figure_body.gdshader": "fresnel de verre — la Figure EST translucide",
    "memory_person.gdshader": "silhouette d'Echo — le fantome n'a que son bord",
    "figure_core.gdshader": "coeur additif interne, jamais un bord d'objet",
}

OUTLINE_WORDS = re.compile(r"\b(outline|rim_light|rim_power|contour|highlight_edge)\b",
                           re.IGNORECASE)
SSR_WORDS = re.compile(r"\b(ssr|screen_space_reflection|reflect_screen)\b",
                       re.IGNORECASE)

EXPECTED_SHADERS = [
    "maree_glass", "figure_body", "echo_reveal", "stone_wet", "cloth_lohen",
    "fog_volumetric_cheap", "letter_paper", "grapple_cable",
    "skybox_velmora", "water_shallow",
]

violations: list[str] = []


def fail(job: str, msg: str) -> None:
    violations.append(f"[{job}] {msg}")


def scan_shaders() -> None:
    files = sorted(SHADER_DIR.rglob("*.gdshader"))
    names = {f.stem for f in files}

    for want in EXPECTED_SHADERS:
        if want not in names:
            fail("annexe-d-complete", f"shader manquant : {want}.gdshader")

    for f in files:
        src = f.read_text(encoding="utf-8")
        rel = f.name

        # --- amber-guard ---
        if rel not in AMBER_ALLOW:
            for m in AMBER.finditer(src):
                if _is_comment(src, m.start()):
                    continue   # une mention en commentaire n'est pas un pixel
                line = src[: m.start()].count("\n") + 1
                fail("amber-guard",
                     f"{rel}:{line} — ambre #FFA33C sans derogation declaree")

        # --- no-outline (09.21) ---
        if rel not in OUTLINE_ALLOW:
            for m in OUTLINE_WORDS.finditer(src):
                line = src[: m.start()].count("\n") + 1
                if not _is_comment(src, m.start()):
                    fail("no-outline",
                         f"{rel}:{line} — '{m.group(0)}' interdit (09.21)")

        # --- no-ssr (05.16) ---
        for m in SSR_WORDS.finditer(src):
            if not _is_comment(src, m.start()):
                fail("no-ssr", f"{rel} — reflexion ecran interdite sur Forward Mobile")

        # --- budget-declared (05.16) ---
        if "BUDGET" not in src:
            fail("budget-declared", f"{rel} n'annonce pas son budget d'instructions")

        # --- lighthouse-dark (21.08) ---
        if "lighthouse_lamp" in src:
            decl = re.search(r"lighthouse_lamp[^=;]*=\s*([0-9.]+)", src)
            if decl is None or float(decl.group(1)) != 0.0:
                fail("lighthouse-dark",
                     f"{rel} — le Phare ne s'allume pas dans le chapitre 1")

    # --- waterline-12-30 (05.20) ---
    stone = (SHADER_DIR / "stone_wet.gdshader").read_text(encoding="utf-8")
    if "12.0" not in stone or "30.0" not in stone:
        fail("waterline-12-30", "stone_wet.gdshader : seuils 12 m / 30 m absents")

    # --- no-visual-shader (05.16) ---
    for t in SHADER_DIR.rglob("*.tres"):
        if "VisualShader" in t.read_text(encoding="utf-8", errors="ignore"):
            fail("no-visual-shader", f"{t.name} — VisualShader interdit")


def scan_controllers() -> None:
    ctrl = SHADER_DIR / "_controllers"

    ritual = ctrl / "echo_history_buffer.gd"
    src = ritual.read_text(encoding="utf-8")
    if "RITUAL_DURATION := 2.44" not in src:
        fail("ritual-duration", "echo_history_buffer.gd : la duree n'est plus 2.44 s")
    ripple = (ctrl / "maree_ripple_buffer.gd").read_text(encoding="utf-8")
    code = _strip_gd_comments(ripple)
    if re.search(r"\bdecay\b|\bfade_out_ripple\b", code):
        fail("ripple-persistent",
             "maree_ripple_buffer.gd : une decroissance a ete ajoutee")
    if "CLEAR_MODE_NEVER" not in code:
        fail("ripple-persistent", "maree_ripple_buffer.gd : le buffer est efface")

    frag = (ctrl / "figure_shatter_pool.gd").read_text(encoding="utf-8")
    if "FRAGMENT_COUNT := 47" not in frag:
        fail("fragments-47", "figure_shatter_pool.gd : le pool ne vaut plus 47")
    if "instantiate()" in frag.split("func shatter")[1]:
        fail("fragments-47", "instanciation pendant le combat")


def _is_comment(src: str, pos: int) -> bool:
    """Vrai si la position tombe dans un commentaire `//` (GLSL) ou `#` (GDScript)."""
    line_start = src.rfind("\n", 0, pos) + 1
    prefix = src[line_start:pos].lstrip()
    return prefix.startswith("//") or prefix.startswith("#")


def _strip_gd_comments(src: str) -> str:
    out = []
    for line in src.splitlines():
        stripped = line.lstrip()
        if stripped.startswith("#"):
            continue
        out.append(line.split("#")[0] if "#" in line else line)
    return "\n".join(out)


def main() -> int:
    if "--scan-shaders" not in sys.argv:
        print(__doc__)
        return 0
    scan_shaders()
    scan_controllers()
    if violations:
        print("AMBER GUARD — %d violation(s)\n" % len(violations))
        for v in violations:
            print("  " + v)
        return 1
    print("AMBER GUARD — annexe D : 10/10 shaders conformes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
