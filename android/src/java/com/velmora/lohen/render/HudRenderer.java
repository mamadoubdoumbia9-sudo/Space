/*
 * LOHEN — render/HudRenderer.java
 *
 * Tout ce qui s'ecrit a l'ecran : le HUD (quatre elements, jamais cinq), les
 * sous-titres, les choix de dialogue, les menus, le journal, la lettre finale,
 * les boutons tactiles, les fondus.
 *
 * 14.01 HUD MINIMAL [OBL] : quatre elements seulement —
 *        l'arc de souffle, le compas d'altitude, l'invite de verbe (un
 *        glyphe, jamais un mot), les sous-titres.
 * 14.02 : pas de barre de vie, pas de stamina numerique, pas de compteur
 *        d'objets, pas de mini-carte, pas de marqueur 3D, jamais une fleche.
 * 14.03 [OBL] : AUCUNE mini-carte. La ville se lit par le regard.
 * 14.06 : toute animation d'interface dure 180 ms, ease_out_quint, jamais de
 *        rebond, jamais plus de 250 ms.
 * 14.10 : Source Serif 4 pour l'interface (17 a 30 sp), Cormorant Garamond
 *        pour les titres en petites capitales avec +8 % d'espacement.
 * 08.02 : quatre boutons maximum a l'ecran, en arc de cercle, 72/64/64/64 dp.
 */
package com.velmora.lohen.render;

import android.opengl.GLES20;

import com.velmora.lohen.gl.GlUtil;
import com.velmora.lohen.gl.ShaderLib;
import com.velmora.lohen.gl.TextAtlas;
import com.velmora.lohen.gl.TextureLib;
import com.velmora.lohen.gl.UiBatch;
import com.velmora.lohen.sim.core.Localization;
import com.velmora.lohen.sim.core.Options;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.narrative.DialogueRuntime;
import com.velmora.lohen.sim.narrative.JournalModel;
import com.velmora.lohen.sim.narrative.LetterReader;
import com.velmora.lohen.sim.narrative.SubtitleModel;
import com.velmora.lohen.sim.core.LohenGame;
import com.velmora.lohen.sim.input.TouchInputRouter;
import com.velmora.lohen.sim.ui.HudModel;
import com.velmora.lohen.sim.ui.MenuModel;

import java.util.List;

public final class HudRenderer {

    /* Ce qui peut etre touche (14.03 : aucun element decoratif ne l'est). */
    public static final int HIT_CHOICE = 1;
    public static final int HIT_MAIN = 2;
    public static final int HIT_PAUSE = 3;
    public static final int HIT_OPTPAGE = 4;
    public static final int HIT_OPTROW = 5;
    public static final int HIT_SLIDER = 6;
    public static final int HIT_SAVESLOT = 7;
    public static final int HIT_JTAB = 8;
    public static final int HIT_JENTRY = 9;
    public static final int HIT_DOOR = 10;
    public static final int HIT_LETTER = 11;

    private int program;
    private int aPos, aUv, aColor;
    private int uTex, uTextured, uOpacity, uResolution;
    private TextureLib textures;

    public void setTextures(TextureLib textures) {
        this.textures = textures;
    }

    /**
     * Une VRAIE image dans le jeu (retour joueur) : fond peint pour le menu,
     * l'accueil, le journal et le papier de la lettre. Vide d'abord les quads
     * en attente, puis trace l'image avec sa propre texture.
     */
    public void imageQuad(int w, int h, String path, float x0, float y0,
                          float x1, float y1, int argb) {
        if (textures == null || batch == null) {
            return;
        }
        int tex = textures.getHQ(path);
        if (tex == 0) {
            return;
        }
        flush(w, h, true);
        batch.beginFrame();
        batch.clear();
        batch.quad(x0, y0, x1, y1, 0f, 0f, 1f, 1f, argb);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uResolution, w, h);
        GLES20.glUniform1f(uOpacity, 1f);
        GLES20.glUniform1f(uTextured, 1f);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        batch.draw(program, aPos, aUv, aColor, w, h);
        GLES20.glDisable(GLES20.GL_BLEND);
    }
    private final UiBatch batch = new UiBatch(4096);
    private TextAtlas atlas;
    private float density = 2.75f;

    public void init() {
        program = GlUtil.program(ShaderLib.UI_VS, ShaderLib.UI_FS);
        aPos = GlUtil.attrib(program, "aPos");
        aUv = GlUtil.attrib(program, "aUv");
        aColor = GlUtil.attrib(program, "aColor");
        uTex = GlUtil.uniform(program, "uTex");
        uTextured = GlUtil.uniform(program, "uTextured");
        uOpacity = GlUtil.uniform(program, "uOpacity");
        uResolution = GlUtil.uniform(program, "uResolution");
    }

    public void setAtlas(TextAtlas a) {
        atlas = a;
    }

    public void setDensity(float d) {
        density = d <= 0f ? 1f : d;
    }

    /* RETOUR JOUEUR « textes tres petits voire illisibles » : tout le HUD
     * est mis a l'echelle — texte x1,45, gabarits x1,12. */
    public static final float TEXT_SCALE = 1.45f;
    public static final float LAYOUT_SCALE = 1.12f;

    public float dp(float value) {
        return value * density * LAYOUT_SCALE;
    }

    /** sp vers pixels : 17 sp minimum pour les sous-titres (14.10). */
    public int sp(float value) {
        return Math.round(value * density * TEXT_SCALE);
    }

    /* ------------------------------------------------------------------ */
    /* Tracé complet d'une image                                           */
    /* ------------------------------------------------------------------ */

    public void draw(LohenGame game, int w, int h, float dt) {
        beginHits(w, h);
        if (program == 0) {
            return;
        }
        batch.beginFrame();
        batch.clear();

        int mode = game.mode();
        MenuModel menu = game.menu;

        if (mode == LohenGame.MODE_MENU) {
            drawMainMenu(game, menu, w, h);
        } else if (mode == LohenGame.MODE_LOADING) {
            drawLoading(game, w, h);
        } else {
            if (mode == LohenGame.MODE_LETTER || mode == LohenGame.MODE_CREDITS) {
                drawLetter(game, w, h, dt);
            } else {
                drawHud(game, w, h);
                drawTouchButtons(game, w, h);
            }
            drawSubtitles(game, w, h);
            drawChoices(game, w, h);
            if (menu.paused() || menu.open()) {
                drawPauseAndMenus(game, menu, w, h);
            }
            drawCinematicOverlay(game, w, h);
        }
        drawDeathFade(game, w, h);
        flush(w, h, true);
    }

    /* ------------------------------------------------------------------ */
    /* HUD (14.01 : quatre elements)                                       */
    /* ------------------------------------------------------------------ */

    private void drawHud(LohenGame game, int w, int h) {
        HudModel hud = game.hud;
        if (hud.mode() == HudModel.MODE_NONE) {
            return;
        }
        /* 1. l'arc de souffle, en bas a gauche, 90 degres, jamais un chiffre */
        if (hud.elementVisible(HudModel.EL_BREATH)) {
            float op = hud.breathOpacity();
            if (op > 0.01f) {
                float cx = dp(96f);
                float cy = h - dp(96f);
                float r = dp(52f);
                int color = hud.breathColor();
                arc(cx, cy, r, dp(9f), -180f, -180f + hud.breathArcDegrees(),
                        color, op);
            }
        }
        /* 2. le compas d'altitude : un filet de 2 dp, jamais une fleche */
        if (hud.elementVisible(HudModel.EL_COMPASS) && hud.compassVisible()) {
            float x0 = w * 0.5f - dp(110f);
            float x1 = w * 0.5f + dp(110f);
            float y = dp(28f);
            float t = hud.compassPlayerT();
            float o = hud.compassObjectiveT();
            rect(x0, y - dp(1f), x1, y + dp(1f), Palette.BLANC_CASSE, 0.42f);
            tick(x0 + (x1 - x0) * Maths.clamp01(t), y, Palette.BLANC_CASSE, 0.95f);
            tick(x0 + (x1 - x0) * Maths.clamp01(o), y, Palette.BLANC_CASSE, 0.45f);
        }
        /* 3. l'invite de verbe : un glyphe de 44 dp, jamais un mot (14.07) */
        if (hud.elementVisible(HudModel.EL_PROMPT) && hud.promptVisible()) {
            String glyph = hud.promptGlyph();
            if (glyph != null && glyph.length() > 0) {
                int size = sp(30);
                float tw = atlas == null ? size : atlas.measure(glyph, TextAtlas.FONT_UI, size);
                float x = w * 0.5f - tw * 0.5f;
                float y = h - dp(190f);
                if (atlas != null) {
                    atlas.draw(batch, glyph, TextAtlas.FONT_UI, size, x, y,
                            withAlpha(Palette.BLANC_CASSE, hud.promptOpacity()));
                }
            }
        }
    }

    /** 08.02 : quatre boutons en arc de cercle, jamais cinq. */
    private void drawTouchButtons(LohenGame game, int w, int h) {
        TouchInputRouter t = game.touch;
        for (int b = 0; b < TouchInputRouter.BUTTON_COUNT; b++) {
            if (!t.isVisible(b)) {
                continue;
            }
            float r = t.buttonRadius(b);
            float cx = t.buttonX(b);
            float cy = t.buttonY(b);
            boolean down = t.isDown(b);
            int fill = down ? 0x33EDE6DA : 0x14EDE6DA;
            circle(cx, cy, r, fill, 0.85f);
            circleOutline(cx, cy, r, dp(2f), withAlpha(Palette.BLANC_CASSE, 0.34f), 1f);
            String glyph = buttonGlyph(b, game);
            if (atlas != null && glyph.length() > 0) {
                int size = sp(20);
                float tw = atlas.measure(glyph, TextAtlas.FONT_UI, size);
                atlas.draw(batch, glyph, TextAtlas.FONT_UI, size, cx - tw * 0.5f,
                        cy - size * 0.5f, withAlpha(Palette.BLANC_CASSE, down ? 0.95f : 0.6f));
            }
        }
    }

    private static String buttonGlyph(int b, LohenGame game) {
        switch (b) {
            case TouchInputRouter.BTN_A:
                return game.lohen.inCombat ? "◈" : "◇";
            case TouchInputRouter.BTN_B:
                return "⌖";
            case TouchInputRouter.BTN_C:
                return game.interactVerb().length() > 0
                        ? HudModel.glyphFor(game.interactVerb()) : "○";
            case TouchInputRouter.BTN_D:
                return "◗";
            default:
                return "";
        }
    }

    /* ------------------------------------------------------------------ */
    /* Sous-titres et choix                                                */
    /* ------------------------------------------------------------------ */

    private void drawSubtitles(LohenGame game, int w, int h) {
        HudModel hud = game.hud;
        if (!hud.elementVisible(HudModel.EL_SUBTITLES) || !hud.subtitlesVisible()) {
            return;
        }
        SubtitleModel subs = game.dialogue.subtitles();
        SubtitleModel.Cue cue = subs == null ? null : subs.current();
        if (cue == null || cue.lineCount == 0) {
            return;
        }
        int size = sp(game.options.subtitleSizeSp);
        int lh = atlas == null ? (int) (size * 1.35f) : atlas.lineHeight(TextAtlas.FONT_UI, size);
        float maxW = w * 0.86f;
        float y = h - dp(96f) - lh * cue.lineCount;
        /* le voile : 55 % d'opacite, jamais un cadre (14.09) */
        rect(w * 0.07f, y - dp(8f), w * 0.93f, y + lh * cue.lineCount + dp(8f),
                0xFF000000, SubtitleModel.BACKDROP_ALPHA * 0.6f);
        if (cue.speakerLabel != null && cue.speakerLabel.length() > 0) {
            int ss = Math.max(sp(13), size - sp(6));
            if (atlas != null) {
                atlas.draw(batch, cue.speakerLabel, TextAtlas.FONT_TITLE, ss,
                        w * 0.5f - atlas.measure(cue.speakerLabel, TextAtlas.FONT_TITLE,
                                ss, TextAtlas.TITLE_TRACKING) * 0.5f,
                        y - lh * 0.9f, SubtitleModel.SPEAKER_COLOR);
            }
        }
        if (atlas == null) {
            return;
        }
        /* les mots apparaissent au rythme de la parole, jamais d'un bloc */
        int words = subs.visibleWords();
        for (int i = 0; i < cue.lineCount; i++) {
            String line = cue.lines[i];
            if (line == null || line.length() == 0) {
                continue;
            }
            String shown = revealWords(line, cue, words);
            float tw = atlas.measure(shown, TextAtlas.FONT_UI, size);
            atlas.draw(batch, shown, TextAtlas.FONT_UI, size, w * 0.5f - tw * 0.5f,
                    y + i * lh, Palette.BLANC_CASSE);
        }
    }

    /** Revele les mots deja prononces (12.05 : le texte suit la voix). */
    private static String revealWords(String line, SubtitleModel.Cue cue, int words) {
        if (cue.wordCount <= 0 || words >= cue.wordCount) {
            return line;
        }
        int cut = line.length();
        int counted = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                counted++;
                if (counted >= words) {
                    cut = i;
                    break;
                }
            }
        }
        return line.substring(0, cut);
    }

    /** 12.06 : trois choix maximum, un silence possible, jamais de QTE. */
    private void drawChoices(LohenGame game, int w, int h) {
        DialogueRuntime d = game.dialogue;
        if (!d.active() || !d.choiceOffered()) {
            return;
        }
        int n = d.choiceCount();
        int size = sp(19);
        float boxW = Math.min(w * 0.88f, dp(620f));
        float x0 = (w - boxW) * 0.5f;
        float rowH = dp(52f);
        float y0 = h * 0.52f;
        rect(x0, y0 - dp(10f), x0 + boxW, y0 + rowH * n + dp(10f),
                Palette.ECRAN_NUIT, 0.86f);
        if (atlas == null) {
            return;
        }
        for (int i = 0; i < n; i++) {
            String text = d.choiceText(i);
            boolean silence = d.choiceIsSilence(i);
            String label = (i + 1) + ".  " + (silence ? "—" : "") + text;
            float ty = y0 + i * rowH + dp(14f);
            hit(HIT_CHOICE, i, x0, y0 + i * rowH, x0 + boxW,
                    y0 + (i + 1) * rowH);
            atlas.draw(batch, label, TextAtlas.FONT_UI, size, x0 + dp(20f), ty,
                    silence ? 0xFF9A9186 : Palette.BLANC_CASSE);
        }
        if (d.choiceHasTimer()) {
            float frac = Maths.clamp01(d.choiceTimerRemaining() / 8f);
            rect(x0, y0 + rowH * n + dp(6f), x0 + boxW * frac,
                    y0 + rowH * n + dp(9f), Palette.BLANC_CASSE, 0.5f);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Menus (14.05, 14.09, 14.12)                                         */
    /* ------------------------------------------------------------------ */

    private void drawMainMenu(LohenGame game, MenuModel menu, int w, int h) {
        imageQuad(w, h, "content/tex/menu.png", 0f, 0f, w, h, 0xFFFFFFFF);
        rect(0, 0, w, h, Palette.ECRAN_NUIT, 0.38f);
        if (atlas == null) {
            return;
        }
        Localization loc = game.loc;
        int title = sp(44);
        String t1 = loc.text("menu.title", "LOHEN");
        String t2 = loc.text("menu.subtitle", "Les Sept Lettres de Velmora");
        String chap = loc.text("menu.chapter", "Chapitre 1 — La Ville qui retient son souffle");
        float x = dp(56f);
        atlas.drawSmallCaps(batch, t1, title, x, h * 0.24f, Palette.BLANC_CASSE);
        atlas.draw(batch, t2, TextAtlas.FONT_TITLE, sp(22), x, h * 0.24f + dp(58f),
                0xFFBFB6A6);
        atlas.draw(batch, chap, TextAtlas.FONT_UI, sp(17), x, h * 0.24f + dp(94f),
                0xFF9A9186);

        /* 14.05 : le menu principal est une SCENE VIVE, pas une illustration.
         * Le ponton de S1 avant le chapitre, le sommet du Phare apres. */
        String scene = menu.menuScene();
        atlas.draw(batch, MenuModel.SCENE_PHARE.equals(scene)
                        ? loc.text("menu.scene.lighthouse", "Le sommet du Phare")
                        : loc.text("menu.scene.ponton", "Le ponton"),
                TextAtlas.FONT_UI, sp(15), x, h * 0.24f + dp(124f), 0xFF7E766A);

        String[] items = {"menu.continue", "menu.new", "menu.options", "menu.journal",
                "menu.quit"};
        String[] fallback = {"Continuer", "Nouvelle partie", "Options", "Journal",
                "Quitter"};
        float y = h * 0.56f;
        for (int i = 0; i < items.length; i++) {
            if ("menu.continue".equals(items[i]) && !game.hasSave()) {
                continue;
            }
            boolean selected = i == menu.row();
            int color = menu.colorFor(items[i]);
            String label = loc.text(items[i], fallback[i]);
            int size = selected ? sp(26) : sp(22);
            atlas.draw(batch, label, TextAtlas.FONT_UI, size, x + dp(selected ? 12f : 0f),
                    y, withAlpha(color, selected ? 1f : 0.72f));
            hit(HIT_MAIN, i, x, y - dp(14f), w - x, y + dp(40f));
            if (selected) {
                rect(x, y + dp(6f), x + dp(4f), y + dp(size * 0.9f), color, 1f);
            }
            y += dp(46f);
        }
        atlas.draw(batch, loc.text("menu.hint", "Deux doigts, deux taps : HUD minimal"),
                TextAtlas.FONT_UI, sp(14), x, h - dp(40f), 0xFF6E665C);
    }

    private void drawPauseAndMenus(LohenGame game, MenuModel menu, int w, int h) {
        /* 14.05 : le fond reste la scene, floutee. Jamais une image fixe. */
        rect(0, 0, w, h, 0xFF04070B, 0.42f + 0.28f * menu.openProgress());
        if (atlas == null) {
            return;
        }
        Localization loc = game.loc;
        int screen = menu.screen();
        float x = dp(56f);
        float y = dp(64f);
        if (screen == MenuModel.SCREEN_PAUSE) {
            atlas.drawSmallCaps(batch, loc.text("pause.title", "Pause"), sp(34), x, y,
                    Palette.BLANC_CASSE);
            String[] items = {"pause.resume", "pause.journal", "pause.options",
                    "pause.saves", "pause.menu"};
            String[] fb = {"Reprendre", "Journal", "Options", "Sauvegardes",
                    "Menu principal"};
            y += dp(84f);
            for (int i = 0; i < items.length; i++) {
                boolean sel = i == menu.row();
                atlas.draw(batch, loc.text(items[i], fb[i]), TextAtlas.FONT_UI,
                        sel ? sp(24) : sp(21), x + dp(sel ? 12f : 0f), y,
                        withAlpha(sel ? Palette.BLANC_CASSE : 0xFFB4AA9C,
                                sel ? 1f : 0.75f));
                hit(HIT_PAUSE, i, x, y - dp(12f), w - x, y + dp(38f));
                y += dp(44f);
            }
            atlas.draw(batch, loc.text("pause.autosave", "Progression enregistree"),
                    TextAtlas.FONT_UI, sp(14), x, h - dp(40f), 0xFF7E766A);
        } else if (screen == MenuModel.SCREEN_OPTIONS) {
            drawOptions(game, menu, w, h, loc);
        } else if (screen == MenuModel.SCREEN_JOURNAL) {
            drawJournal(game, w, h, loc);
        } else if (screen == MenuModel.SCREEN_SAVES) {
            drawSaves(game, menu, w, h, loc);
        }
    }

    /** 14.12 : six pages, 55 lignes au moins, toutes fonctionnelles. */
    private void drawOptions(LohenGame game, MenuModel menu, int w, int h,
                             Localization loc) {
        float x = dp(48f);
        atlas.drawSmallCaps(batch, loc.text("options.title", "Options"), sp(32), x,
                dp(48f), Palette.BLANC_CASSE);
        String[] pages = {"options.image", "options.sound", "options.game",
                "options.controls", "options.accessibility", "options.data"};
        String[] pagesFb = {"IMAGE", "SON", "JEU", "CONTROLES", "ACCESSIBILITE",
                "DONNEES"};
        float tx = x;
        float ty = dp(104f);
        for (int p = 0; p < MenuModel.PAGE_COUNT; p++) {
            boolean sel = p == menu.page();
            String label = loc.text(pages[p], pagesFb[p]);
            int size = sp(16);
            atlas.drawSmallCaps(batch, label, size, tx, ty,
                    withAlpha(sel ? Palette.BLANC_CASSE : 0xFF8E857A, sel ? 1f : 0.7f));
            float lw = atlas.measure(label, TextAtlas.FONT_TITLE, size,
                    TextAtlas.TITLE_TRACKING);
            hit(HIT_OPTPAGE, p, tx, ty - dp(8f), tx + lw, ty + dp(30f));
            if (sel) {
                rect(tx, ty + dp(24f), tx + lw, ty + dp(26f), Palette.BLANC_CASSE, 0.8f);
            }
            tx += lw + dp(28f);
        }
        List<MenuModel.Row> rows = MenuModel.PAGES.get(menu.page());
        float ry = dp(160f);
        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                MenuModel.Row r = rows.get(i);
                boolean sel = i == menu.row();
                String label = loc.text(r.key, r.key);
                String value = menu.rowLabel(r);
                int size = sp(18);
                atlas.draw(batch, label, TextAtlas.FONT_UI, size, x + dp(sel ? 10f : 0f),
                        ry, withAlpha(sel ? Palette.BLANC_CASSE : 0xFFB4AA9C,
                                sel ? 1f : 0.8f));
                if (r.kind == MenuModel.Row.KIND_SLIDER) {
                    float frac = (MenuModel.valueOf(game.options, r.field) - r.min)
                            / Math.max(1e-4f, r.max - r.min);
                    float bx = w * 0.55f;
                    float bw = w * 0.32f;
                    rect(bx, ry + dp(10f), bx + bw, ry + dp(12f), 0xFF3A3A38, 0.9f);
                    rect(bx, ry + dp(10f), bx + bw * Maths.clamp01(frac),
                            ry + dp(12f), Palette.BLANC_CASSE, 0.9f);
                }
                if (value != null && value.length() > 0) {
                    float vw = atlas.measure(value, TextAtlas.FONT_UI, size);
                    atlas.draw(batch, value, TextAtlas.FONT_UI, size,
                            w - dp(48f) - vw, ry,
                            withAlpha(sel ? Palette.BLANC_CASSE : 0xFF9A9186, 1f));
                }
                if (r.note != null && r.note.length() > 0 && sel) {
                    atlas.draw(batch, loc.text(r.note, r.note), TextAtlas.FONT_UI,
                            sp(14), x + dp(10f), ry + dp(24f), 0xFF7E766A);
                }
                hit(HIT_OPTROW, i, x, ry - dp(10f), w - x, ry + dp(38f));
                if (r.kind == MenuModel.Row.KIND_SLIDER) {
                    hit(HIT_SLIDER, i, w * 0.55f, ry + dp(2f),
                            w * 0.55f + w * 0.32f, ry + dp(22f));
                }
                ry += dp(r.note != null && r.note.length() > 0 && sel ? 56f : 40f);
                if (ry > h - dp(70f)) {
                    break;
                }
            }
        }
        atlas.draw(batch, loc.text("options.hint",
                        "Glisser pour regler · Deux taps pour revenir"),
                TextAtlas.FONT_UI, sp(14), x, h - dp(40f), 0xFF6E665C);
    }

    private void drawSaves(LohenGame game, MenuModel menu, int w, int h,
                           Localization loc) {
        float x = dp(48f);
        atlas.drawSmallCaps(batch, loc.text("saves.title", "Sauvegardes"), sp(32), x,
                dp(48f), Palette.BLANC_CASSE);
        float y = dp(140f);
        for (int s = 0; s < 3; s++) {
            boolean sel = s == menu.slot();
            String title = loc.text("saves.slot", "Emplacement") + " " + (s + 1);
            String detail = game.describeSlot(s);
            rect(x, y - dp(6f), w - x, y + dp(74f),
                    sel ? 0xFF1A2028 : 0xFF10151B, sel ? 0.95f : 0.75f);
            atlas.draw(batch, title, TextAtlas.FONT_TITLE, sp(20), x + dp(20f), y,
                    sel ? Palette.BLANC_CASSE : 0xFFB4AA9C);
            atlas.draw(batch, detail, TextAtlas.FONT_UI, sp(16), x + dp(20f),
                    y + dp(30f), 0xFF8E857A);
            hit(HIT_SAVESLOT, s, x, y - dp(6f), w - x, y + dp(74f));
            y += dp(92f);
        }
        atlas.draw(batch, loc.text("saves.hint", "Trois emplacements, trois autosaves"),
                TextAtlas.FONT_UI, sp(14), x, h - dp(40f), 0xFF6E665C);
    }

    /**
     * 14.04 : cinq onglets — LETTRES (7 emplacements), ECHOS (31 vignettes),
     * GENS (17 fiches), OBJETS (11), CARNET (8). Aucun compteur affiche.
     */
    private void drawJournal(LohenGame game, int w, int h, Localization loc) {
        JournalModel j = game.journal;
        imageQuad(w, h, "content/tex/journal.png", 0f, 0f, w, h, 0xFFFFFFFF);
        rect(0, 0, w, h, Palette.ENCRE, 0.62f);
        float x = dp(44f);
        atlas.drawSmallCaps(batch, loc.text("journal.title", "Journal"), sp(30), x,
                dp(40f), Palette.BLANC_CASSE);
        float tx = x;
        float ty = dp(92f);
        for (int t = 0; t < JournalModel.TAB_COUNT; t++) {
            boolean sel = t == j.tab();
            String label = loc.text(JournalModel.TAB_KEYS[t], JournalModel.tabName(t));
            int size = sp(15);
            atlas.drawSmallCaps(batch, label, size, tx, ty,
                    sel ? Palette.BLANC_CASSE : 0xFF8E857A);
            float tw = atlas.measure(label, TextAtlas.FONT_TITLE, size,
                    TextAtlas.TITLE_TRACKING);
            hit(HIT_JTAB, t, tx - dp(6f), ty - dp(8f), tx + tw + dp(6f),
                    ty + dp(26f));
            tx += tw + dp(24f);
        }
        rect(x, ty + dp(24f), w - x, ty + dp(25f), 0xFF3A3A38, 0.8f);
        float y = ty + dp(48f);
        int tab = j.tab();
        if (tab == JournalModel.TAB_LETTERS) {
            for (int i = 0; i < JournalModel.LETTER_SLOTS; i++) {
                JournalModel.LetterSlot slot = j.slot(i);
                boolean sel = i == j.entryIndex();
                if (slot == null || !slot.filled) {
                    atlas.draw(batch, loc.text("journal.letter.empty",
                                    "Un emplacement vide"), TextAtlas.FONT_LOHEN,
                            sp(19), x + dp(8f), y,
                            withAlpha(0xFF6E665C, sel ? 1f : 0.6f));
                } else {
                    /* 14.06 : les lettres d'Esteban sont en Rouge Script */
                    int font = "rouge_script".equals(slot.font)
                            ? TextAtlas.FONT_ESTEBAN : TextAtlas.FONT_LOHEN;
                    atlas.draw(batch, slot.title, font, sp(22), x + dp(8f), y,
                            sel ? Palette.AMBRE_ESTEBAN : Palette.PAPIER);
                    atlas.draw(batch, slot.author + " · " + slot.sequence,
                            TextAtlas.FONT_UI, sp(14), x + dp(8f), y + dp(28f),
                            0xFF8E857A);
                }
                hit(HIT_JENTRY, i, x, y - dp(6f), w - x, y + dp(52f));
                y += dp(56f);
            }
        } else if (tab == JournalModel.TAB_ECHOS) {
            List<JournalModel.EchoVignette> v = j.vignettes();
            for (int i = 0; i < v.size() && y < h - dp(80f); i++) {
                JournalModel.EchoVignette e = v.get(i);
                boolean sel = i == j.entryIndex();
                atlas.draw(batch, e.echoId + "  " + e.object, TextAtlas.FONT_UI,
                        sp(17), x + dp(8f), y,
                        withAlpha(e.read ? Palette.PAPIER : 0xFF6E665C,
                                sel ? 1f : 0.75f));
                if (e.portraitCaptured) {
                    atlas.draw(batch, loc.text("journal.portrait", "portrait"),
                            TextAtlas.FONT_LOHEN, sp(14), w - dp(150f), y,
                            0xFF8E857A);
                }
                hit(HIT_JENTRY, i, x, y - dp(4f), w - x, y + dp(30f));
                y += dp(34f);
            }
        } else if (tab == JournalModel.TAB_PEOPLE) {
            List<JournalModel.PeopleFile> p = j.people();
            for (int i = 0; i < p.size() && y < h - dp(80f); i++) {
                JournalModel.PeopleFile f = p.get(i);
                boolean sel = i == j.entryIndex();
                atlas.draw(batch, f.name, TextAtlas.FONT_LOHEN, sp(20), x + dp(8f), y,
                        withAlpha(Palette.PAPIER, sel ? 1f : 0.8f));
                atlas.draw(batch, f.role, TextAtlas.FONT_UI, sp(14), x + dp(8f),
                        y + dp(24f), 0xFF8E857A);
                for (int k = 0; k < f.factsRevealed && k < f.facts.length; k++) {
                    atlas.draw(batch, "· " + f.facts[k], TextAtlas.FONT_LOHEN,
                            sp(15), x + dp(20f), y + dp(44f + k * 20f),
                            0xFFB4AA9C);
                }
                hit(HIT_JENTRY, i, x, y - dp(6f), w - x,
                        y + dp(44f + f.factsRevealed * 20f));
                y += dp(48f + f.factsRevealed * 20f);
            }
        } else if (tab == JournalModel.TAB_OBJECTS) {
            List<JournalModel.FigureObject> o = j.objects();
            for (int i = 0; i < o.size() && y < h - dp(80f); i++) {
                JournalModel.FigureObject fo = o.get(i);
                boolean sel = i == j.entryIndex();
                atlas.draw(batch, fo.held ? fo.name : loc.text("journal.object.unknown",
                                "Un objet, sans nom"), TextAtlas.FONT_LOHEN, sp(19),
                        x + dp(8f), y, withAlpha(fo.held ? Palette.PAPIER : 0xFF6E665C,
                                sel ? 1f : 0.75f));
                hit(HIT_JENTRY, i, x, y - dp(4f), w - x, y + dp(32f));
                y += dp(36f);
            }
        } else {
            List<JournalModel.NotebookEntry> n = j.notebook();
            for (int i = 0; i < n.size() && y < h - dp(80f); i++) {
                JournalModel.NotebookEntry e = n.get(i);
                boolean sel = i == j.entryIndex();
                if (!e.written) {
                    atlas.draw(batch, loc.text("journal.notebook.blank",
                                    "Une page blanche"), TextAtlas.FONT_LOHEN,
                            sp(18), x + dp(8f), y, 0xFF5E574E);
                    hit(HIT_JENTRY, i, x, y - dp(4f), w - x, y + dp(30f));
                    y += dp(34f);
                    continue;
                }
                atlas.draw(batch, e.sequence + " — " + e.title, TextAtlas.FONT_LOHEN,
                        sp(20), x + dp(8f), y,
                        withAlpha(Palette.PAPIER, sel ? 1f : 0.85f));
                String[] lines = atlas.wrap(e.text, TextAtlas.FONT_LOHEN, sp(16),
                        w - x * 2 - dp(20f));
                for (int k = 0; k < lines.length && k < 3; k++) {
                    atlas.draw(batch, lines[k], TextAtlas.FONT_LOHEN, sp(16),
                            x + dp(20f), y + dp(28f + k * 22f), 0xFFB4AA9C);
                }
                hit(HIT_JENTRY, i, x, y - dp(6f), w - x,
                        y + dp(30f + Math.min(3, lines.length) * 22f));
                y += dp(34f + Math.min(3, lines.length) * 22f);
            }
        }
        atlas.draw(batch, loc.text("journal.hint", "Aucun compteur. Le journal se "
                        + "remplit de ce que Lohen a vu."),
                TextAtlas.FONT_UI, sp(14), x, h - dp(40f), 0xFF6E665C);
    }

    /* ------------------------------------------------------------------ */
    /* La lettre finale (BLOC 19 / 20)                                     */
    /* ------------------------------------------------------------------ */

    private void drawLetter(LohenGame game, int w, int h, float dt) {
        LetterReader lr = game.letter;
        int step = lr.step();
        if (step == LetterReader.STEP_CREDITS || step > LetterReader.STEP_CREDITS) {
            drawCredits(game, w, h);
            return;
        }
        if (step == LetterReader.STEP_NOTEBOOK) {
            /* E30 : le carnet, six extraits, 2 min 30 */
            rect(w * 0.12f, h * 0.10f, w * 0.88f, h * 0.90f, Palette.ENCRE, 0.97f);
            if (atlas != null) {
                atlas.drawSmallCaps(batch, game.loc.text("letter.notebook",
                                "Le carnet d'Esteban"), sp(26), w * 0.16f,
                        h * 0.14f, Palette.PAPIER);
                int page = lr.notebookPage();
                String text = page >= 0 && page < lr.blockCount() ? lr.block(page) : "";
                drawHandwritten(text, w * 0.16f, h * 0.22f, w * 0.72f,
                        TextAtlas.FONT_ESTEBAN, sp(21), Palette.AMBRE_ESTEBAN, 1.42f);
            }
            return;
        }
        if (step >= LetterReader.STEP_UNFOLD && step <= LetterReader.STEP_AFTER) {
            /* la lettre elle-meme : papier, encre d'Esteban, au rythme du joueur */
            float unfold = Maths.clamp01((step == LetterReader.STEP_UNFOLD)
                    ? lr.stepTime() / LetterReader.UNFOLD_DURATION : 1f);
            float paperW = w * 0.78f;
            float paperH = h * 0.80f * unfold;
            float px = (w - paperW) * 0.5f;
            float py = (h - paperH) * 0.5f;
            imageQuad(w, h, "content/tex/lettre.png", px, py, px + paperW,
                    py + paperH, 0xFFFFFFFF);
            rect(px, py, px + paperW, py + paperH, Palette.PAPIER, 0.20f);
            rect(px, py, px + paperW, py + dp(3f), 0xFFCFC5B2, 0.9f);
            hit(HIT_LETTER, 0, px, py, px + paperW, py + paperH);
            if (atlas != null && unfold > 0.35f) {
                int visible = lr.visibleBlocks();
                float y = py + dp(28f);
                float scroll = lr.scroll();
                y -= scroll * dp(220f);
                for (int i = 0; i < visible && i < lr.blockCount(); i++) {
                    String block = lr.block(i);
                    if (block == null || block.length() == 0) {
                        continue;
                    }
                    y = drawHandwritten(block, px + dp(34f), y, paperW - dp(68f),
                            TextAtlas.FONT_ESTEBAN, sp(20), 0xFF3A2E22, 1.5f);
                    y += dp(14f);
                    if (y > py + paperH) {
                        break;
                    }
                }
            }
            /* 19.08 : les trois coupures sur le visage sont du ressort de la
             * camera ; ici, seule la tremble de 2 mm trahit la lecture. */
            if (step == LetterReader.STEP_READING) {
                float tremble = (float) Math.sin(game.gameTime() * 2.1f) * dp(1f);
                rect(px + tremble, py + paperH - dp(4f), px + paperW,
                        py + paperH, 0xFFCFC5B2, 0.6f);
            }
            String glyph = HudModel.glyphFor("read");
            if (atlas != null && glyph.length() > 0 && lr.promptGlyph() >= 0) {
                int size = sp(28);
                atlas.draw(batch, glyph, TextAtlas.FONT_UI, size,
                        w * 0.5f - size * 0.5f, h - dp(80f),
                        withAlpha(Palette.BLANC_CASSE, 0.7f));
            }
            return;
        }
        if (step == LetterReader.STEP_DOOR || step == LetterReader.STEP_ENTRY
                || step == LetterReader.STEP_ROOM_FREE) {
            /* la porte poussee des deux mains : une jauge de 2,2 s, pas un QTE */
            float p = lr.doorProgress();
            if (p > 0f && atlas != null) {
                float bw = dp(220f);
                float bx = (w - bw) * 0.5f;
                float by = h - dp(140f);
                rect(bx, by, bx + bw, by + dp(4f), 0xFF2A2A28, 0.8f);
                rect(bx, by, bx + bw * Maths.clamp01(p), by + dp(4f),
                        Palette.BLANC_CASSE, 0.9f);
                hit(HIT_DOOR, 0, bx - dp(24f), by - dp(44f), bx + bw + dp(24f),
                        by + dp(48f));
                atlas.draw(batch, game.loc.text("letter.door", "Pousser"),
                        TextAtlas.FONT_UI, sp(16), bx, by - dp(26f),
                        withAlpha(Palette.BLANC_CASSE, 0.75f));
            }
        }
    }

    /**
     * Le texte manuscrit : Caveat pour Lohen, Rouge Script pour Esteban.
     * Retourne le y suivant la derniere ligne.
     */
    private float drawHandwritten(String text, float x, float y, float maxW,
                                  int font, int size, int color, float lineScale) {
        if (atlas == null || text == null || text.length() == 0) {
            return y;
        }
        String[] lines = atlas.wrap(text, font, size, maxW);
        float lh = atlas.lineHeight(font, size) * lineScale;
        for (String line : lines) {
            atlas.draw(batch, line, font, size, x, y, color);
            y += lh;
        }
        return y;
    }

    private void drawCredits(LohenGame game, int w, int h) {
        rect(0, 0, w, h, 0xFF04060A, 1f);
        if (atlas == null) {
            return;
        }
        LetterReader lr = game.letter;
        Localization loc = game.loc;
        float y = h - lr.creditsTime() * dp(34f);
        String last = lr.lastLine();
        /* 20.12 [OBL] : le chapitre se ferme sur « Il reste six lettres. » */
        String[] lines = {
                loc.text("credits.chapter", "Chapitre 1 — La Ville qui retient son souffle"),
                loc.text("credits.design", "D'apres le dossier de Velmora"),
                loc.text("credits.engine", "Moteur natif Velmora — Java, OpenGL ES 2.0"),
                loc.text("credits.audio", "Musique et bruitages synthetises, -16 LUFS"),
                loc.text("credits.fonts", "Source Serif 4 · Cormorant Garamond · Caveat · "
                        + "Rouge Script (SIL Open Font License)"),
                "",
                last == null || last.length() == 0
                        ? loc.text("credits.last", "Il reste six lettres.") : last,
        };
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].length() == 0) {
                y += dp(30f);
                continue;
            }
            int size = i == lines.length - 1 ? sp(26) : sp(18);
            int font = i == lines.length - 1 ? TextAtlas.FONT_TITLE : TextAtlas.FONT_UI;
            float tw = atlas.measure(lines[i], font, size);
            atlas.draw(batch, lines[i], font, size, (w - tw) * 0.5f, y,
                    i == lines.length - 1 ? Palette.BLANC_CASSE : 0xFF9A9186);
            y += dp(i == lines.length - 1 ? 46f : 34f);
        }
        if (lr.creditsSkippable()) {
            atlas.draw(batch, loc.text("credits.skip", "Appui long pour passer"),
                    TextAtlas.FONT_UI, sp(14), dp(48f), h - dp(40f), 0xFF6E665C);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Cinematiques, chargement, mort                                      */
    /* ------------------------------------------------------------------ */

    private void drawCinematicOverlay(LohenGame game, int w, int h) {
        if (game.mode() != LohenGame.MODE_CINEMATIC || !game.cine.active()) {
            return;
        }
        /* 07.22 : l'appui long est visible, sans jamais etre un QTE */
        float p = game.cine.skipHoldProgress();
        if (p > 0.01f && atlas != null) {
            float bw = dp(160f);
            float bx = w - bw - dp(32f);
            float by = h - dp(48f);
            rect(bx, by, bx + bw, by + dp(3f), 0xFF2A2A28, 0.7f);
            rect(bx, by, bx + bw * Maths.clamp01(p), by + dp(3f),
                    Palette.BLANC_CASSE, 0.85f);
            atlas.draw(batch, game.loc.text("cine.skip", "Maintenir pour passer"),
                    TextAtlas.FONT_UI, sp(14), bx - dp(4f), by - dp(24f),
                    withAlpha(Palette.BLANC_CASSE, 0.7f));
        }
    }

    /**
     * L'ecran du tout premier chargement : celui ou le contenu est lu depuis
     * les assets. La simulation n'est pas encore prete, donc cette methode ne
     * touche a rien d'autre qu'au modele de HUD et a l'atlas.
     */
    public void drawBoot(LohenGame game, int w, int h, float dt, float progress,
                         String stage) {
        beginHits(w, h);
        if (program == 0) {
            return;
        }
        batch.beginFrame();
        batch.clear();
        imageQuad(w, h, "content/tex/boot.png", 0f, 0f, w, h, 0xFFFFFFFF);
        rect(0, 0, w, h, 0xFF04060A, 0.55f);
        if (atlas != null) {
            /* le titre, en petites capitales, sans aucune decoration */
            String title = "LOHEN";
            int size = sp(40);
            float tw = atlas.measure(title, TextAtlas.FONT_TITLE, size,
                    TextAtlas.TITLE_TRACKING);
            atlas.drawSmallCaps(batch, title, size, (w - tw) * 0.5f, h * 0.42f,
                    Palette.BLANC_CASSE);
            String sub = "Les Sept Lettres de Velmora";
            int s2 = sp(18);
            float sw = atlas.measure(sub, TextAtlas.FONT_LOHEN, s2);
            atlas.draw(batch, sub, TextAtlas.FONT_LOHEN, s2, (w - sw) * 0.5f,
                    h * 0.42f + dp(56f), withAlpha(0xFF9A9186, 0.9f));
            float bw = Math.min(w * 0.42f, dp(360f));
            float bx = (w - bw) * 0.5f;
            float by = h * 0.62f;
            rect(bx, by, bx + bw, by + dp(2f), 0xFF2A2A28, 0.9f);
            rect(bx, by, bx + bw * Maths.clamp01(progress), by + dp(2f),
                    Palette.BLANC_CASSE, 0.9f);
            if (stage == null || stage.length() == 0) {
                stage = "Velmora";
            }
            int s3 = sp(14);
            float gw = atlas.measure(stage, TextAtlas.FONT_UI, s3);
            atlas.draw(batch, stage, TextAtlas.FONT_UI, s3, (w - gw) * 0.5f,
                    by + dp(20f), withAlpha(0xFF7E766A, 0.9f));
        }
        flush(w, h, true);
    }

    private void drawLoading(LohenGame game, int w, int h) {
        rect(0, 0, w, h, 0xFF04060A, 1f);
        if (atlas == null) {
            return;
        }
        HudModel hud = game.hud;
        float p = hud.loadProgress();
        /* 02.13 : un chargement dit ce qu'il charge, il ne ment jamais */
        String label = game.loc.text("loading." + game.sequence().toLowerCase(),
                game.loc.text("loading.default", "Velmora"));
        int size = sp(20);
        float tw = atlas.measure(label, TextAtlas.FONT_TITLE, size,
                TextAtlas.TITLE_TRACKING);
        atlas.drawSmallCaps(batch, label, size, (w - tw) * 0.5f, h * 0.48f,
                Palette.BLANC_CASSE);
        if (hud.hasProgressBar()) {
            float bw = Math.min(w * 0.5f, dp(420f));
            float bx = (w - bw) * 0.5f;
            float by = h * 0.58f;
            rect(bx, by, bx + bw, by + dp(2f), 0xFF2A2A28, 0.9f);
            rect(bx, by, bx + bw * Maths.clamp01(p), by + dp(2f),
                    Palette.BLANC_CASSE, 0.9f);
        }
    }

    /** 12.08 : la mort est un fondu de 1,2 s, jamais un ecran de game over. */
    private void drawDeathFade(LohenGame game, int w, int h) {
        float f = game.hud.deathFade();
        if (f > 0.001f) {
            rect(0, 0, w, h, 0xFF04060A, Maths.clamp01(f));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Primitives                                                          */
    /* ------------------------------------------------------------------ */

    private void rect(float x0, float y0, float x1, float y1, int argb, float alpha) {
        batch.quad(x0, y0, x1, y1, TextAtlas.WHITE_UV, TextAtlas.WHITE_UV, TextAtlas.WHITE_UV, TextAtlas.WHITE_UV,
                withAlpha(argb, alpha));
    }

    private void circle(float cx, float cy, float r, int argb, float alpha) {
        int seg = 22;
        for (int i = 0; i < seg; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / seg);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / seg);
            batch.tri(cx, cy, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
                    cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r,
                    withAlpha(argb, alpha));
        }
    }

    private void circleOutline(float cx, float cy, float r, float thickness,
                               int argb, float alpha) {
        int seg = 30;
        for (int i = 0; i < seg; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / seg);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / seg);
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float ri = r - thickness;
            batch.quadCorners(cx + c0 * ri, cy + s0 * ri, cx + c1 * ri,
                    cy + s1 * ri, cx + c1 * r, cy + s1 * r, cx + c0 * r,
                    cy + s0 * r, TextAtlas.WHITE_UV, TextAtlas.WHITE_UV,
                    withAlpha(argb, alpha));
        }
    }

    /** L'arc du souffle : 90 degres maximum, jamais un chiffre (14.02). */
    private void arc(float cx, float cy, float r, float thickness, float fromDeg,
                     float toDeg, int argb, float alpha) {
        int seg = 26;
        float span = toDeg - fromDeg;
        for (int i = 0; i < seg; i++) {
            float a0 = (float) Math.toRadians(fromDeg + span * (i / (float) seg));
            float a1 = (float) Math.toRadians(fromDeg + span * ((i + 1) / (float) seg));
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float ri = r - thickness;
            batch.quadCorners(cx + c0 * ri, cy + s0 * ri, cx + c1 * ri,
                    cy + s1 * ri, cx + c1 * r, cy + s1 * r, cx + c0 * r,
                    cy + s0 * r, TextAtlas.WHITE_UV, TextAtlas.WHITE_UV,
                    withAlpha(argb, alpha));
        }
    }

    private void tick(float x, float y, int argb, float alpha) {
        rect(x - dp(1.5f), y - dp(7f), x + dp(1.5f), y + dp(7f), argb, alpha);
    }

    public static int withAlpha(int argb, float alpha) {
        int a = (int) (Maths.clamp01(alpha) * ((argb >> 24) & 0xFF));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Un seul atlas, un seul draw call : les aplats lisent le bloc blanc de
     * l'atlas (TextAtlas.WHITE_BLOCK), le texte lit ses glyphes. Ni l'un ni
     * l'autre ne change de texture en cours de route.
     */
    private void flush(int w, int h, boolean textured) {
        if (atlas == null) {
            return;
        }
        atlas.upload();
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uResolution, w, h);
        GLES20.glUniform1f(uOpacity, 1f);
        GLES20.glUniform1f(uTextured, 1f);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlas.texture());
        batch.draw(program, aPos, aUv, aColor, w, h);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    /* ------------------------------------------------------------------ */
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
                        game.letterConfirm();
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
                game.letterConfirm();
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

    /**
     * Activation sans le doigt : une manette, un clavier, un lecteur d'ecran.
     * Le meme code que le tap, donc le meme resultat (08.22).
     */
    public boolean activateMain(LohenGame game, int index) {
        if (index == 0 && !game.hasSave()) {
            index = 1;         /* « Continuer » n'existe pas : on saute la ligne */
        }
        return mainMenuAction(game, index);
    }

    public boolean activatePause(LohenGame game, int index) {
        return pauseAction(game, index);
    }

    /** Un emplacement de sauvegarde : charge depuis le menu, ecrit depuis la pause. */
    public boolean activateSlot(LohenGame game, int slot) {
        game.menu.setSlot(slot);
        if (savesLoad) {
            if (game.loadSlot(slot)) {
                game.menu.close();
                return true;
            }
            return false;
        }
        return game.saveToSlot(slot);
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

    public void release() {
        batch.release();
        if (program != 0) {
            GLES20.glDeleteProgram(program);
            program = 0;
        }
    }
}
