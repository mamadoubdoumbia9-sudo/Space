#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Aligne le rendu sur les API reelles du moteur."""
import os, re

APP = "/home/user/Space/android/src/java/com/velmora/lohen"


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def write(p, s):
    with open(p, "w", encoding="utf-8") as f:
        f.write(s)


# ------------------------------------------------------------ WorldRenderer
p = os.path.join(APP, "render/WorldRenderer.java")
s = read(p)
s = s.replace("import com.velmora.lohen.sim.math.Maths;",
              "import com.velmora.lohen.sim.math.Mat4;\n"
              "import com.velmora.lohen.sim.math.Maths;")
s = s.replace("Maths.multiply(proj, view, viewProj);",
              "Mat4.multiply(proj, view, viewProj);")
s = s.replace("Maths.identity(model);", "Mat4.identity(model);")
s = s.replace("Maths.multiply(viewProj, model, mvp);",
              "Mat4.multiply(viewProj, model, mvp);")
s = s.replace("phare.sweepAngleDeg()", "phare.angleDeg()")
s = s.replace("return ShaderLib.pack(r, g, b, 1f);",
              "return MeshBuilder.pack(r, g, b, 1f);")
# la variable `up` du cone ne sert a rien : on la retire proprement
s = s.replace("""        int seg = 12;
        float[] up = {0f, 1f, 0f};
        float[] side = {dz, 0f, -dx};""",
              """        int seg = 12;
        float[] side = {dz, 0f, -dx};""")
s = s.replace("""        if (up[0] == 0f) {
            /* rien : l'anti-souffle est explicite pour eviter un avertissement */
        }
""", "")
write(p, s)
print("WorldRenderer ok")

# ------------------------------------------------------------- HudRenderer
p = os.path.join(APP, "render/HudRenderer.java")
s = read(p)

s = s.replace("loc.t(", "loc.text(")
s = s.replace("game.options.sliderValue(r.field)",
              "MenuModel.valueOf(game.options, r.field)")
s = s.replace("""                    float frac = (MenuModel.valueOf(game.options, r.field) - r.min)
                            / Math.max(1e-4f, r.max - r.min);""",
              """                    float frac = (MenuModel.valueOf(game.options, r.field) - r.min)
                            / Math.max(1e-4f, r.max - r.min);""")

# un seul atlas, un seul draw call : les aplats lisent le bloc blanc
s = s.replace("""    private TextAtlas atlas;
    private float density = 2.75f;
    private int whitePixel = -1;""",
              """    private TextAtlas atlas;
    private float density = 2.75f;""")
s = s.replace("""        uResolution = GlUtil.uniform(program, "uResolution");
        /* un pixel blanc, pour les filets, les arcs et les aplats */
        java.nio.ByteBuffer px = java.nio.ByteBuffer.allocateDirect(4);
        px.put(new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255});
        px.position(0);
        whitePixel = GlUtil.texture(px, 1, 1);
    }""",
              """        uResolution = GlUtil.uniform(program, "uResolution");
    }""")
s = s.replace("0.5f, 0.5f, 0.5f, 0.5f,", "TextAtlas.WHITE_UV, TextAtlas.WHITE_UV, "
                                          "TextAtlas.WHITE_UV, TextAtlas.WHITE_UV,")
s = s.replace("""            batch.quad(cx + c0 * ri, cy + s0 * ri, cx + c1 * ri, cy + s1 * ri,
                    cx + c1 * r, cy + s1 * r, cx + c0 * r, cy + s0 * r,
                    TextAtlas.WHITE_UV, TextAtlas.WHITE_UV, TextAtlas.WHITE_UV, TextAtlas.WHITE_UV, withAlpha(argb, alpha));""",
              """            batch.quadCorners(cx + c0 * ri, cy + s0 * ri, cx + c1 * ri,
                    cy + s1 * ri, cx + c1 * r, cy + s1 * r, cx + c0 * r,
                    cy + s0 * r, TextAtlas.WHITE_UV, TextAtlas.WHITE_UV,
                    withAlpha(argb, alpha));""")

s = s.replace("""    private void flush(int w, int h, boolean textured) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uResolution, w, h);
        GLES20.glUniform1f(uOpacity, 1f);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glUniform1i(uTex, 0);
        /* les aplats d'abord (pixel blanc), puis le texte (atlas) */
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, whitePixel);
        GLES20.glUniform1f(uTextured, 1f);
        batch.draw(program, aPos, aUv, aColor, w, h);
        if (atlas != null) {
            atlas.upload();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlas.texture());
            GLES20.glUniform1f(uTextured, 1f);
            batch.draw(program, aPos, aUv, aColor, w, h);
        }
        GLES20.glDisable(GLES20.GL_BLEND);
    }""",
              """    /**
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
    }""")

s = s.replace("""    public void release() {
        batch.release();
        if (whitePixel >= 0) {
            GLES20.glDeleteTextures(1, new int[]{whitePixel}, 0);
            whitePixel = -1;
        }
        if (program != 0) {""",
              """    public void release() {
        batch.release();
        if (program != 0) {""")
write(p, s)
print("HudRenderer ok")
