/*
 * LOHEN — gl/UiBatch.java
 *
 * Le lot de quads 2D : HUD, menus, journal, lettre, sous-titres. Un seul
 * VBO dynamique, un seul draw call par atlas de glyphes.
 *
 * 14.06 : toute animation d'interface dure 180 ms, courbe ease_out_quint,
 * jamais plus de 250 ms, jamais de rebond. Les modeles (HudModel, MenuModel)
 * calculent ces progressions ; ce lot ne fait que tracer.
 */
package com.velmora.lohen.gl;

import android.opengl.GLES20;

import java.nio.FloatBuffer;

public final class UiBatch {

    /** position(2) + uv(2) + couleur(4) = 8 flottants par sommet. */
    public static final int STRIDE = 8;

    private final float[] data;
    private int verts;
    private final int capacity;
    private int vbo;
    private final FloatBuffer buffer;

    public UiBatch(int maxQuads) {
        capacity = maxQuads * 4;
        data = new float[capacity * STRIDE];
        buffer = GlUtil.allocate(capacity * STRIDE);
    }

    public void clear() {
        verts = 0;
    }

    public int quads() {
        return verts / 4;
    }

    public boolean full() {
        return verts + 4 > capacity;
    }

    /** Un quad en coordonnees d'ecran (origine en haut a gauche, y vers le bas). */
    public void quad(float x0, float y0, float x1, float y1,
                     float u0, float v0, float u1, float v1, int argb) {
        if (verts + 4 > capacity) {
            return;
        }
        float r = ShaderLib.r(argb);
        float g = ShaderLib.g(argb);
        float b = ShaderLib.b(argb);
        float a = ShaderLib.a(argb);
        put(x0, y0, u0, v0, r, g, b, a);
        put(x1, y0, u1, v0, r, g, b, a);
        put(x1, y1, u1, v1, r, g, b, a);
        put(x0, y1, u0, v1, r, g, b, a);
    }

    /** Un quad dont les coins ont chacun leur alpha (degrades, arcs de souffle). */
    public void quadGradient(float x0, float y0, float x1, float y1,
                             float u0, float v0, float u1, float v1,
                             int argb, float a00, float a10, float a11, float a01) {
        if (verts + 4 > capacity) {
            return;
        }
        float r = ShaderLib.r(argb);
        float g = ShaderLib.g(argb);
        float b = ShaderLib.b(argb);
        float base = ShaderLib.a(argb);
        put(x0, y0, u0, v0, r, g, b, base * a00);
        put(x1, y0, u1, v0, r, g, b, base * a10);
        put(x1, y1, u1, v1, r, g, b, base * a11);
        put(x0, y1, u0, v1, r, g, b, base * a01);
    }

    /** Un quad dont les quatre coins sont libres : anneaux, arcs, filets. */
    public void quadCorners(float x0, float y0, float x1, float y1,
                            float x2, float y2, float x3, float y3,
                            float u, float v, int argb) {
        if (verts + 4 > capacity) {
            return;
        }
        float r = ShaderLib.r(argb);
        float g = ShaderLib.g(argb);
        float b = ShaderLib.b(argb);
        float a = ShaderLib.a(argb);
        put(x0, y0, u, v, r, g, b, a);
        put(x1, y1, u, v, r, g, b, a);
        put(x2, y2, u, v, r, g, b, a);
        put(x3, y3, u, v, r, g, b, a);
    }

    /**
     * Un triangle : les fleches sont interdites (14.03), mais les arcs du
     * souffle et les pans du compas d'altitude sont des fan de triangles.
     */
    public void tri(float x0, float y0, float x1, float y1, float x2, float y2,
                    int argb) {
        if (verts + 4 > capacity) {
            return;
        }
        float r = ShaderLib.r(argb);
        float g = ShaderLib.g(argb);
        float b = ShaderLib.b(argb);
        float a = ShaderLib.a(argb);
        put(x0, y0, 0.5f, 0.5f, r, g, b, a);
        put(x1, y1, 0.5f, 0.5f, r, g, b, a);
        put(x2, y2, 0.5f, 0.5f, r, g, b, a);
        put(x2, y2, 0.5f, 0.5f, r, g, b, a);
    }

    private void put(float x, float y, float u, float v, float r, float g,
                     float b, float a) {
        int o = verts * STRIDE;
        data[o] = x;
        data[o + 1] = y;
        data[o + 2] = u;
        data[o + 3] = v;
        data[o + 4] = r;
        data[o + 5] = g;
        data[o + 6] = b;
        data[o + 7] = a;
        verts++;
    }

    public void beginFrame() {
        if (vbo == 0) {
            int[] ids = new int[1];
            GLES20.glGenBuffers(1, ids, 0);
            vbo = ids[0];
        }
    }

    /** Televerse le lot et le trace en un seul appel. */
    public void draw(int program, int aPos, int aUv, int aColor, float w, float h) {
        if (verts == 0) {
            return;
        }
        buffer.clear();
        buffer.put(data, 0, verts * STRIDE);
        buffer.position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts * STRIDE * 4, buffer,
                GLES20.GL_DYNAMIC_DRAW);
        GLES20.glUniform2f(GlUtil.uniform(program, "uResolution"), w, h);
        int stride = STRIDE * 4;
        if (aPos >= 0) {
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, stride, 0);
        }
        if (aUv >= 0) {
            GLES20.glEnableVertexAttribArray(aUv);
            GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, stride, 8);
        }
        if (aColor >= 0) {
            GLES20.glEnableVertexAttribArray(aColor);
            GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, stride, 16);
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4);
        /* les quads sont traces par groupes de 4 en TRIANGLE_FAN : un seul
         * draw ne suffit pas, on decoupe en primitives de 4 sommets. */
        for (int base = 4; base + 4 <= verts; base += 4) {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, base, 4);
        }
        if (aPos >= 0) {
            GLES20.glDisableVertexAttribArray(aPos);
        }
        if (aUv >= 0) {
            GLES20.glDisableVertexAttribArray(aUv);
        }
        if (aColor >= 0) {
            GLES20.glDisableVertexAttribArray(aColor);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    public void release() {
        if (vbo != 0) {
            GLES20.glDeleteBuffers(1, new int[]{vbo}, 0);
            vbo = 0;
        }
    }
}
