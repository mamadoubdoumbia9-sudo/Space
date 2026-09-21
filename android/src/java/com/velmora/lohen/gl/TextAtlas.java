/*
 * LOHEN — gl/TextAtlas.java
 *
 * La typographie du jeu, rasterisee depuis les quatre polices du chapitre
 * (toutes sous licence SIL OFL, embarquees dans assets/fonts) :
 *
 *   Source Serif 4      — interface, sous-titres (17 a 30 sp, 14.10)
 *   Cormorant Garamond  — titres, petites capitales, espacement +8 % (14.10)
 *   Caveat              — l'ecriture de Lohen (matricule 0114, carnets)
 *   Rouge Script        — l'ecriture d'Esteban (la lettre finale, BLOC 20)
 *
 * Aucun texte n'est une image prealable : les glyphes sont rasterises a la
 * volee dans un atlas 1024 x 1024 puis traces comme des quads. C'est ce qui
 * permet les 5 langues, la taille de sous-titres reglable, et l'ecriture
 * manuscrite de la lettre sans ajouter un seul octet d'asset binaire.
 */
package com.velmora.lohen.gl;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public final class TextAtlas {

    public static final int SIZE = 1024;
    public static final int PAD = 2;

    public static final int FONT_UI = 0;          /* Source Serif 4 */
    public static final int FONT_TITLE = 1;       /* Cormorant Garamond */
    public static final int FONT_LOHEN = 2;       /* Caveat */
    public static final int FONT_ESTEBAN = 3;     /* Rouge Script */
    public static final int FONT_COUNT = 4;

    public static final String[] FONT_PATHS = {
            "fonts/SourceSerif4.ttf",
            "fonts/CormorantGaramond.ttf",
            "fonts/Caveat.ttf",
            "fonts/RougeScript.ttf",
    };

    /** 14.10 : l'interface est en petites capitales pour les titres. */
    public static final float TITLE_TRACKING = MenuTracking.TITLE_TRACKING;

    private static final class Glyph {
        int x, y, w, h;
        float advance;
        float bearingX;
        float bearingY;
    }

    /** Petites constantes partagees avec le modele de menus. */
    public static final class MenuTracking {
        public static final float TITLE_TRACKING = 1.08f;   /* +8 % (14.10) */
    }

    private final Bitmap bitmap;
    private final Canvas canvas;
    private final Paint paint;
    private final Paint fill;
    private final Typeface[] faces = new Typeface[FONT_COUNT];
    private final Map<String, Glyph> glyphs = new HashMap<String, Glyph>(512);
    private final Rect bounds = new Rect();
    private int texId;
    /**
     * Le bloc blanc opaque en (0,0) : il sert de texture d'un pixel pour tous
     * les aplats, arcs et filets du HUD. Un seul atlas, un seul draw call.
     */
    public static final float WHITE_UV = 4f / (float) SIZE;
    public static final int WHITE_BLOCK = 8;

    private int penX = WHITE_BLOCK + PAD * 2;
    private int penY = PAD;
    private int rowHeight;
    private boolean dirty;
    private boolean loaded;

    public TextAtlas() {
        bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0);
        canvas = new Canvas(bitmap);
        canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
        android.graphics.Paint white = new android.graphics.Paint();
        white.setColor(0xFFFFFFFF);
        canvas.drawRect(0, 0, WHITE_BLOCK, WHITE_BLOCK, white);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setColor(0xFFFFFFFF);
        fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(0xFFFFFFFF);
    }

    /** Charge les polices depuis les assets. Echoue silencieusement sinon. */
    public boolean load(android.content.res.AssetManager assets) {
        boolean ok = true;
        for (int i = 0; i < FONT_COUNT; i++) {
            try {
                faces[i] = Typeface.createFromAsset(assets, FONT_PATHS[i]);
            } catch (RuntimeException e) {
                faces[i] = Typeface.DEFAULT;
                ok = false;
            }
        }
        loaded = true;
        return ok;
    }

    public boolean loaded() {
        return loaded;
    }

    public int texture() {
        return texId;
    }

    public boolean dirty() {
        return dirty;
    }

    private Glyph glyph(int font, char c, int sizePx) {
        String key = font + ":" + sizePx + ":" + (int) c;
        Glyph g = glyphs.get(key);
        if (g != null) {
            return g;
        }
        Typeface face = faces[font] != null ? faces[font] : Typeface.DEFAULT;
        paint.setTypeface(face);
        paint.setTextSize(sizePx);
        paint.getTextBounds(String.valueOf(c), 0, 1, bounds);
        float adv = paint.measureText(String.valueOf(c));
        int w = Math.max(1, bounds.width() + 2);
        int h = Math.max(1, bounds.height() + 2);
        if (penX + w + PAD > SIZE) {
            penX = PAD;
            penY += rowHeight + PAD;
            rowHeight = 0;
        }
        if (penY + h + PAD > SIZE) {
            /* atlas plein : on recycle depuis le haut (les tailles rares) */
            penX = PAD;
            penY = PAD;
            canvas.drawColor(0);
            glyphs.clear();
            dirty = true;
        }
        g = new Glyph();
        g.x = penX;
        g.y = penY;
        g.w = w;
        g.h = h;
        g.advance = adv;
        g.bearingX = bounds.left - 1;
        g.bearingY = -bounds.top + 1;
        canvas.drawText(String.valueOf(c), penX - bounds.left + 1,
                penY - bounds.top + 1, fill);
        penX += w + PAD;
        rowHeight = Math.max(rowHeight, h);
        glyphs.put(key, g);
        dirty = true;
        return g;
    }

    /** Telecharge l'atlas modifie dans le GPU. */
    public void upload() {
        if (!dirty) {
            return;
        }
        if (texId == 0) {
            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            texId = ids[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap);
        }
        dirty = false;
    }

    /** Largeur d'un texte en pixels, espacement de titre compris. */
    public float measure(String text, int font, int sizePx, float tracking) {
        if (text == null || text.length() == 0) {
            return 0f;
        }
        float w = 0f;
        for (int i = 0; i < text.length(); i++) {
            Glyph g = glyph(font, text.charAt(i), sizePx);
            w += g.advance * tracking;
        }
        return w;
    }

    public float measure(String text, int font, int sizePx) {
        return measure(text, font, sizePx, 1f);
    }

    /** Hauteur de ligne (ascend + descend) pour une taille donnee. */
    public int lineHeight(int font, int sizePx) {
        Typeface face = faces[font] != null ? faces[font] : Typeface.DEFAULT;
        paint.setTypeface(face);
        paint.setTextSize(sizePx);
        Paint.FontMetrics fm = paint.getFontMetrics();
        return (int) Math.ceil(fm.descent - fm.ascent);
    }

    public int ascent(int font, int sizePx) {
        Typeface face = faces[font] != null ? faces[font] : Typeface.DEFAULT;
        paint.setTypeface(face);
        paint.setTextSize(sizePx);
        return (int) Math.ceil(-paint.getFontMetrics().ascent);
    }

    /**
     * Ajoute un texte au lot. `x`/`y` est le coin haut-gauche de la premiere
     * ligne, comme partout ailleurs dans l'interface.
     */
    public float draw(UiBatch batch, String text, int font, int sizePx, float x,
                      float y, int argb, float tracking) {
        if (text == null || text.length() == 0 || batch == null) {
            return 0f;
        }
        float cursor = x;
        int ascent = ascent(font, sizePx);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                cursor = x;
                y += lineHeight(font, sizePx);
                continue;
            }
            Glyph g = glyph(font, c, sizePx);
            float gx = cursor + g.bearingX;
            float gy = y + ascent - g.bearingY;
            float u0 = g.x / (float) SIZE;
            float v0 = g.y / (float) SIZE;
            float u1 = (g.x + g.w) / (float) SIZE;
            float v1 = (g.y + g.h) / (float) SIZE;
            batch.quad(gx, gy, gx + g.w, gy + g.h, u0, v0, u1, v1, argb);
            cursor += g.advance * tracking;
        }
        return cursor - x;
    }

    public float draw(UiBatch batch, String text, int font, int sizePx, float x,
                      float y, int argb) {
        return draw(batch, text, font, sizePx, x, y, argb, 1f);
    }

    /**
     * Petites capitales des titres (14.10) : les minuscules sont composees en
     * capitales reduites, les capitales restent grandes.
     */
    public float drawSmallCaps(UiBatch batch, String text, int sizePx, float x,
                               float y, int argb) {
        if (text == null || text.length() == 0) {
            return 0f;
        }
        float cursor = x;
        int capSize = sizePx;
        int smallSize = Math.round(sizePx * 0.78f);
        int ascent = ascent(FONT_TITLE, capSize);
        int smallAscent = ascent(FONT_TITLE, smallSize);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean upper = Character.isUpperCase(c);
            char up = Character.toUpperCase(c);
            int size = upper ? capSize : smallSize;
            Glyph g = glyph(FONT_TITLE, up, size);
            float gy = y + ascent - (upper ? g.bearingY : (g.bearingY + (ascent - smallAscent)));
            float u0 = g.x / (float) SIZE;
            float v0 = g.y / (float) SIZE;
            float u1 = (g.x + g.w) / (float) SIZE;
            float v1 = (g.y + g.h) / (float) SIZE;
            batch.quad(cursor + g.bearingX, gy, cursor + g.bearingX + g.w, gy + g.h,
                    u0, v0, u1, v1, argb);
            cursor += g.advance * TITLE_TRACKING;
        }
        return cursor - x;
    }

    /** Decoupe un texte en lignes qui tiennent dans `maxWidthPx`. */
    public String[] wrap(String text, int font, int sizePx, float maxWidthPx) {
        if (text == null || text.length() == 0) {
            return new String[]{""};
        }
        java.util.List<String> lines = new java.util.ArrayList<String>(8);
        StringBuilder cur = new StringBuilder();
        String[] words = text.split(" ");
        for (String w : words) {
            if (w.length() == 0) {
                continue;
            }
            String test = cur.length() == 0 ? w : cur + " " + w;
            if (measure(test, font, sizePx) > maxWidthPx && cur.length() > 0) {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append(w);
            } else {
                cur.setLength(0);
                cur.append(test);
            }
        }
        if (cur.length() > 0) {
            lines.add(cur.toString());
        }
        return lines.toArray(new String[0]);
    }

    public ByteBuffer pixels() {
        ByteBuffer buf = ByteBuffer.allocateDirect(SIZE * SIZE * 4);
        bitmap.copyPixelsToBuffer(buf);
        buf.position(0);
        return buf;
    }

    public void release() {
        if (texId != 0) {
            GLES20.glDeleteTextures(1, new int[]{texId}, 0);
            texId = 0;
        }
    }
}
