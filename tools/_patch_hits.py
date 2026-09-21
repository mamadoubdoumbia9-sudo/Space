# -*- coding: utf-8 -*-
"""Zones tactiles du HUD : ce qui est dessine doit pouvoir etre touche."""
import sys

P = "/home/user/Space/android/src/java/com/velmora/lohen/render/HudRenderer.java"
s = open(P, encoding="utf-8").read()
miss = []


def rep(old, new):
    global s
    if old not in s:
        miss.append(old.splitlines()[0][:70])
        return
    s = s.replace(old, new, 1)


rep("    public void draw(LohenGame game, int w, int h, float dt) {\n",
    "    public void draw(LohenGame game, int w, int h, float dt) {\n"
    "        beginHits(w, h);\n")

# --- choix de dialogue -----------------------------------------------------
rep("            float ty = y0 + i * rowH + dp(14f);\n",
    "            float ty = y0 + i * rowH + dp(14f);\n"
    "            hit(HIT_CHOICE, i, x0, y0 + i * rowH, x0 + boxW,\n"
    "                    y0 + (i + 1) * rowH);\n")

# --- menu principal --------------------------------------------------------
rep("""            if (selected) {
                rect(x, y + dp(6f), x + dp(4f), y + dp(size * 0.9f), color, 1f);
            }""",
    """            hit(HIT_MAIN, i, x, y - dp(14f), w - x, y + dp(40f));
            if (selected) {
                rect(x, y + dp(6f), x + dp(4f), y + dp(size * 0.9f), color, 1f);
            }""")

# --- pause -----------------------------------------------------------------
rep("""                        withAlpha(sel ? Palette.BLANC_CASSE : 0xFFB4AA9C,
                                sel ? 1f : 0.75f));
                y += dp(44f);""",
    """                        withAlpha(sel ? Palette.BLANC_CASSE : 0xFFB4AA9C,
                                sel ? 1f : 0.75f));
                hit(HIT_PAUSE, i, x, y - dp(12f), w - x, y + dp(38f));
                y += dp(44f);""")

# --- pages d'options -------------------------------------------------------
rep("""            float lw = atlas.measure(label, TextAtlas.FONT_TITLE, size,
                    TextAtlas.TITLE_TRACKING);
            if (sel) {""",
    """            float lw = atlas.measure(label, TextAtlas.FONT_TITLE, size,
                    TextAtlas.TITLE_TRACKING);
            hit(HIT_OPTPAGE, p, tx, ty - dp(8f), tx + lw, ty + dp(30f));
            if (sel) {""")

# --- lignes d'options ------------------------------------------------------
rep("""                ry += dp(r.note != null && r.note.length() > 0 && sel ? 56f : 40f);""",
    """                hit(HIT_OPTROW, i, x, ry - dp(10f), w - x, ry + dp(38f));
                if (r.kind == MenuModel.Row.KIND_SLIDER) {
                    hit(HIT_SLIDER, i, w * 0.55f, ry + dp(2f),
                            w * 0.55f + w * 0.32f, ry + dp(22f));
                }
                ry += dp(r.note != null && r.note.length() > 0 && sel ? 56f : 40f);""")

# --- emplacements de sauvegarde -------------------------------------------
rep("""            atlas.draw(batch, detail, TextAtlas.FONT_UI, sp(16), x + dp(20f),
                    y + dp(30f), 0xFF8E857A);
            y += dp(92f);""",
    """            atlas.draw(batch, detail, TextAtlas.FONT_UI, sp(16), x + dp(20f),
                    y + dp(30f), 0xFF8E857A);
            hit(HIT_SAVESLOT, s, x, y - dp(6f), w - x, y + dp(74f));
            y += dp(92f);""")

# --- onglets du journal ----------------------------------------------------
rep("""            atlas.drawSmallCaps(batch, label, size, tx, ty,
                    sel ? Palette.BLANC_CASSE : 0xFF8E857A);
            tx += atlas.measure(label, TextAtlas.FONT_TITLE, size,
                    TextAtlas.TITLE_TRACKING) + dp(24f);""",
    """            atlas.drawSmallCaps(batch, label, size, tx, ty,
                    sel ? Palette.BLANC_CASSE : 0xFF8E857A);
            float tw = atlas.measure(label, TextAtlas.FONT_TITLE, size,
                    TextAtlas.TITLE_TRACKING);
            hit(HIT_JTAB, t, tx - dp(6f), ty - dp(8f), tx + tw + dp(6f),
                    ty + dp(26f));
            tx += tw + dp(24f);""")

# --- entrees du journal ----------------------------------------------------
rep("""                    atlas.draw(batch, slot.author + " · " + slot.sequence,
                            TextAtlas.FONT_UI, sp(14), x + dp(8f), y + dp(28f),
                            0xFF8E857A);
                }
                y += dp(56f);""",
    """                    atlas.draw(batch, slot.author + " · " + slot.sequence,
                            TextAtlas.FONT_UI, sp(14), x + dp(8f), y + dp(28f),
                            0xFF8E857A);
                }
                hit(HIT_JENTRY, i, x, y - dp(6f), w - x, y + dp(52f));
                y += dp(56f);""")

rep("""                            0xFF8E857A);
                }
                y += dp(34f);""",
    """                            0xFF8E857A);
                }
                hit(HIT_JENTRY, i, x, y - dp(4f), w - x, y + dp(30f));
                y += dp(34f);""")

rep("""                y += dp(48f + f.factsRevealed * 20f);""",
    """                hit(HIT_JENTRY, i, x, y - dp(6f), w - x,
                        y + dp(44f + f.factsRevealed * 20f));
                y += dp(48f + f.factsRevealed * 20f);""")

rep("""                y += dp(36f);""",
    """                hit(HIT_JENTRY, i, x, y - dp(4f), w - x, y + dp(32f));
                y += dp(36f);""")

rep("""                    y += dp(34f);
                    continue;""",
    """                    hit(HIT_JENTRY, i, x, y - dp(4f), w - x, y + dp(30f));
                    y += dp(34f);
                    continue;""")

rep("""                y += dp(34f + Math.min(3, lines.length) * 22f);""",
    """                hit(HIT_JENTRY, i, x, y - dp(6f), w - x,
                        y + dp(30f + Math.min(3, lines.length) * 22f));
                y += dp(34f + Math.min(3, lines.length) * 22f);""")

# --- la lettre : papier et porte ------------------------------------------
rep("""            rect(px, py, px + paperW, py + dp(3f), 0xFFCFC5B2, 0.9f);""",
    """            rect(px, py, px + paperW, py + dp(3f), 0xFFCFC5B2, 0.9f);
            hit(HIT_LETTER, 0, px, py, px + paperW, py + paperH);""")

rep("""                rect(bx, by, bx + bw * Maths.clamp01(p), by + dp(4f),
                        Palette.BLANC_CASSE, 0.9f);""",
    """                rect(bx, by, bx + bw * Maths.clamp01(p), by + dp(4f),
                        Palette.BLANC_CASSE, 0.9f);
                hit(HIT_DOOR, 0, bx - dp(24f), by - dp(44f), bx + bw + dp(24f),
                        by + dp(48f));""")

# --- le bloc de dispatch ---------------------------------------------------
BLOCK = '''    /* ------------------------------------------------------------------ */
    /* Zones tactiles                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * Tout ce qui est dessine et qui repond au doigt enregistre ici son
     * rectangle. Le dessin et le test de toucher partagent donc la meme
     * geometrie : impossible de viser un bouton qui n'existe plus a l'ecran.
     */
    private static final int MAX_HITS = 160;
    private final float[] hitRect = new float[MAX_HITS * 4];
    private final int[] hitKind = new int[MAX_HITS];
    private final int[] hitIndex = new int[MAX_HITS];
    private int hitCount;
    private int screenW = 1280;
    private int screenH = 720;
    private boolean quitRequested;
    private boolean savesLoad;

    private void beginHits(int w, int h) {
        hitCount = 0;
        screenW = w;
        screenH = h;
    }

    private void hit(int kind, int index, float x0, float y0, float x1, float y1) {
        if (hitCount >= MAX_HITS) {
            return;
        }
        int o = hitCount * 4;
        hitRect[o] = Math.min(x0, x1);
        hitRect[o + 1] = Math.min(y0, y1);
        hitRect[o + 2] = Math.max(x0, x1);
        hitRect[o + 3] = Math.max(y0, y1);
        hitKind[hitCount] = kind;
        hitIndex[hitCount] = index;
        hitCount++;
    }

    private int hitAt(float x, float y) {
        /* les dernieres zones dessinees sont au-dessus : on parcourt a l'envers */
        for (int i = hitCount - 1; i >= 0; i--) {
            int o = i * 4;
            if (x >= hitRect[o] && x <= hitRect[o + 2]
                    && y >= hitRect[o + 1] && y <= hitRect[o + 3]) {
                return i;
            }
        }
        return -1;
    }

    /** L'ecran des sauvegardes charge depuis le menu, enregistre depuis la pause. */
    public void setSavesLoad(boolean load) {
        savesLoad = load;
    }

    /** « Quitter » a ete touche : l'Activity termine. */
    public boolean consumeQuit() {
        boolean q = quitRequested;
        quitRequested = false;
        return q;
    }

    /** Le doigt est sur la jauge de la porte : la lecture pousse. */
    public boolean isDoorHeld(float x, float y) {
        int i = hitAt(x, y);
        return i >= 0 && hitKind[i] == HIT_DOOR;
    }

    /**
     * Un tap. Retourne vrai si l'interface l'a consomme : dans ce cas le
     * geste ne doit jamais atteindre le joystick ni la camera (08.06).
     */
    public boolean tap(LohenGame game, float x, float y) {
        int i = hitAt(x, y);
        MenuModel menu = game.menu;
        if (i >= 0) {
            switch (hitKind[i]) {
                case HIT_CHOICE:
                    game.dialogue.selectChoice(hitIndex[i]);
                    return true;
                case HIT_MAIN:
                    return mainMenuAction(game, hitIndex[i]);
                case HIT_PAUSE:
                    return pauseAction(game, hitIndex[i]);
                case HIT_OPTPAGE:
                    menu.movePage(hitIndex[i] - menu.page());
                    return true;
                case HIT_OPTROW: {
                    menu.moveRow(hitIndex[i] - menu.row());
                    MenuModel.Row r = menu.currentRow();
                    if (r != null && r.kind != MenuModel.Row.KIND_SLIDER) {
                        menu.adjust(1f);
                    }
                    return true;
                }
                case HIT_SAVESLOT:
                    menu.setSlot(hitIndex[i]);
                    if (savesLoad) {
                        if (game.loadSlot(hitIndex[i])) {
                            menu.close();
                        }
                    } else {
                        game.saveToSlot(hitIndex[i]);
                    }
                    return true;
                case HIT_JTAB:
                    game.journal.setTab(hitIndex[i]);
                    return true;
                case HIT_JENTRY:
                    game.journal.moveEntry(hitIndex[i] - game.journal.entryIndex());
                    return true;
                case HIT_LETTER:
                    /* le haut fait defiler vers le haut, le bas vers le bas,
                     * le centre confirme : la lettre se lit au rythme du joueur */
                    int o = i * 4;
                    float top = hitRect[o + 1];
                    float bot = hitRect[o + 3];
                    if (y < top + (bot - top) * 0.3f) {
                        game.letter.scrollBy(-0.34f);
                    } else if (y > top + (bot - top) * 0.7f) {
                        game.letter.scrollBy(0.34f);
                    } else {
                        game.interact();
                    }
                    return true;
                default:
                    return false;
            }
        }
        /* hors de toute zone : la lettre et le generique restent tactiles */
        if (game.mode() == LohenGame.MODE_LETTER || game.mode() == LohenGame.MODE_CREDITS) {
            if (y < screenH * 0.3f) {
                game.letter.scrollBy(-0.34f);
            } else if (y > screenH * 0.7f) {
                game.letter.scrollBy(0.34f);
            } else {
                game.interact();
            }
            return true;
        }
        return false;
    }

    /**
     * Un glissement sur un curseur. La valeur suit le doigt, elle ne saute
     * pas : c'est la regle 14.12 des reglages tactiles.
     */
    public boolean drag(LohenGame game, float x, float y) {
        int i = hitAt(x, y);
        if (i < 0 || hitKind[i] != HIT_SLIDER) {
            return false;
        }
        MenuModel menu = game.menu;
        menu.moveRow(hitIndex[i] - menu.row());
        MenuModel.Row r = menu.currentRow();
        if (r == null) {
            return false;
        }
        int o = i * 4;
        float x0 = hitRect[o];
        float x1 = hitRect[o + 2];
        float frac = Maths.clamp01((x - x0) / Math.max(1f, x1 - x0));
        float value = r.min + frac * (r.max - r.min);
        if (r.step > 0f) {
            value = Math.round(value / r.step) * r.step;
        }
        menu.set(r.field, Maths.clamp(value, r.min, r.max));
        return true;
    }

    private boolean mainMenuAction(LohenGame game, int index) {
        MenuModel menu = game.menu;
        switch (index) {
            case 0:                      /* Continuer */
                game.continueGame();
                return true;
            case 1:                      /* Nouvelle partie */
                game.newGame();
                return true;
            case 2:
                menu.open("options");
                return true;
            case 3:
                menu.open("journal");
                return true;
            case 4:
                quitRequested = true;
                return true;
            default:
                return false;
        }
    }

    private boolean pauseAction(LohenGame game, int index) {
        MenuModel menu = game.menu;
        switch (index) {
            case 0:                      /* Reprendre */
                menu.togglePause();
                return true;
            case 1:
                menu.open("journal");
                return true;
            case 2:
                menu.open("options");
                return true;
            case 3:
                savesLoad = false;
                menu.open("saves");
                return true;
            case 4:                      /* Menu principal */
                menu.close();
                menu.openMain();
                return true;
            default:
                return false;
        }
    }

'''
rep("    public void release() {", BLOCK + "    public void release() {")

# les constantes de zones, declarees en tete de classe
rep("public final class HudRenderer {\n",
    "public final class HudRenderer {\n\n"
    "    /* Ce qui peut etre touche (14.03 : aucun element decoratif ne l'est). */\n"
    "    public static final int HIT_CHOICE = 1;\n"
    "    public static final int HIT_MAIN = 2;\n"
    "    public static final int HIT_PAUSE = 3;\n"
    "    public static final int HIT_OPTPAGE = 4;\n"
    "    public static final int HIT_OPTROW = 5;\n"
    "    public static final int HIT_SLIDER = 6;\n"
    "    public static final int HIT_SAVESLOT = 7;\n"
    "    public static final int HIT_JTAB = 8;\n"
    "    public static final int HIT_JENTRY = 9;\n"
    "    public static final int HIT_DOOR = 10;\n"
    "    public static final int HIT_LETTER = 11;\n")

open(P, "w", encoding="utf-8").write(s)
if miss:
    print("MANQUES:")
    for m in miss:
        print("  - " + m)
    sys.exit(1)
print("HudRenderer : zones tactiles ok")
