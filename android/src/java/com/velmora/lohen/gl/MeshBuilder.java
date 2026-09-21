/*
 * LOHEN — gl/MeshBuilder.java
 *
 * Construit les maillages du monde a partir des donnees de niveau. Aucun asset
 * binaire n'est telecharge : la geometrie est generee au chargement de la
 * sequence depuis `level_sN.json` (774 solides, 77 rampes, 38 prises, 87
 * ancres). C'est ce qui permet de tenir le budget de 2,1 Go de VRAM du palier
 * haut (02.10) sans sacrifier le contenu.
 *
 * Format de sommet : position(3) + normale(3) + couleur(4) + param(2) = 12
 * flottants. `param.x` marque le verre de la Maree, `param.y` l'emission
 * (ambre d'Esteban, cyan des Figures).
 */
package com.velmora.lohen.gl;

import android.opengl.GLES20;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public final class MeshBuilder {

    public static final int STRIDE_FLOATS = 12;

    private float[] verts;
    private short[] indices;
    private int vertCount;
    private int indexCount;

    private int vbo;
    private int ibo;
    private boolean uploaded;

    public MeshBuilder(int initialVerts, int initialIndices) {
        verts = new float[Math.max(24, initialVerts) * STRIDE_FLOATS];
        indices = new short[Math.max(36, initialIndices)];
    }

    public void clear() {
        vertCount = 0;
        indexCount = 0;
        uploaded = false;
    }

    public int vertexCount() {
        return vertCount;
    }

    public int indexCount() {
        return indexCount;
    }

    private void ensureVerts(int more) {
        int need = (vertCount + more) * STRIDE_FLOATS;
        if (need > verts.length) {
            float[] grown = new float[Math.max(need, verts.length * 2)];
            System.arraycopy(verts, 0, grown, 0, vertCount * STRIDE_FLOATS);
            verts = grown;
        }
    }

    private void ensureIndices(int more) {
        int need = indexCount + more;
        if (need > indices.length) {
            short[] grown = new short[Math.max(need, indices.length * 2)];
            System.arraycopy(indices, 0, grown, 0, indexCount);
            indices = grown;
        }
    }

    public int vertex(float x, float y, float z, float nx, float ny, float nz,
                      float r, float g, float b, float a, float glass, float emit) {
        ensureVerts(1);
        int o = vertCount * STRIDE_FLOATS;
        verts[o] = x;
        verts[o + 1] = y;
        verts[o + 2] = z;
        verts[o + 3] = nx;
        verts[o + 4] = ny;
        verts[o + 5] = nz;
        verts[o + 6] = r;
        verts[o + 7] = g;
        verts[o + 8] = b;
        verts[o + 9] = a;
        verts[o + 10] = glass;
        verts[o + 11] = emit;
        return vertCount++;
    }

    public void tri(int a, int b, int c) {
        ensureIndices(3);
        indices[indexCount++] = (short) a;
        indices[indexCount++] = (short) b;
        indices[indexCount++] = (short) c;
    }

    public void quad(int a, int b, int c, int d) {
        tri(a, b, c);
        tri(a, c, d);
    }

    /**
     * Une boite orientee autour de Y (le seul axe de rotation du niveau : les
     * quais, les passerelles et les marches sont tous axes sur la ville).
     */
    public void box(float cx, float cy, float cz, float hx, float hy, float hz,
                    float yawDeg, int argb, float glass, float emit) {
        float r = ShaderLib.r(argb);
        float g = ShaderLib.g(argb);
        float b = ShaderLib.b(argb);
        float a = ShaderLib.a(argb);
        float yaw = (float) Math.toRadians(yawDeg);
        float cs = (float) Math.cos(yaw);
        float sn = (float) Math.sin(yaw);
        float[] px = new float[8];
        float[] py = new float[8];
        float[] pz = new float[8];
        int k = 0;
        for (int iy = -1; iy <= 1; iy += 2) {
            for (int ix = -1; ix <= 1; ix += 2) {
                for (int iz = -1; iz <= 1; iz += 2) {
                    float lx = ix * hx;
                    float lz = iz * hz;
                    px[k] = cx + lx * cs + lz * sn;
                    py[k] = cy + iy * hy;
                    pz[k] = cz - lx * sn + lz * cs;
                    k++;
                }
            }
        }
        /* l'ordre des coins est fixe : 0..3 = bas, 4..7 = haut */
        int[][] faces = {
                {1, 0, 2, 3},   /* bas   (-Y) */
                {4, 5, 6, 7},   /* haut  (+Y) */
                {0, 4, 6, 2},   /* +X */
                {1, 3, 7, 5},   /* -X */
                {3, 2, 6, 7},   /* +Z */
                {0, 1, 5, 4},   /* -Z */
        };
        float[][] normals = {
                {0, -1, 0}, {0, 1, 0}, {cs, 0, sn}, {-cs, 0, -sn},
                {-sn, 0, cs}, {sn, 0, -cs},
        };
        for (int f = 0; f < 6; f++) {
            float shade = f == 1 ? 1.0f : (f == 0 ? 0.55f : (f < 4 ? 0.82f : 0.72f));
            int base = vertCount;
            for (int i = 0; i < 4; i++) {
                int vi = faces[f][i];
                vertex(px[vi], py[vi], pz[vi], normals[f][0], normals[f][1],
                        normals[f][2], r * shade, g * shade, b * shade, a,
                        glass, emit);
            }
            quad(base, base + 1, base + 2, base + 3);
        }
    }

    /** Une rampe d'escalier : le kit K3, le plus utilise du jeu (09.41). */
    public void ramp(float x, float y, float z, float w, float h, float d,
                     float yawDeg, float steps, int argb) {
        ramp(x, y, z, w, h, d, yawDeg, steps, argb, 0f);
    }

    public void ramp(float x, float y, float z, float w, float h, float d,
                     float yawDeg, float steps, int argb, float texParam) {
        float r = ShaderLib.r(argb);
        float g = ShaderLib.g(argb);
        float b = ShaderLib.b(argb);
        float a = ShaderLib.a(argb);
        float yaw = (float) Math.toRadians(yawDeg);
        float cs = (float) Math.cos(yaw);
        float sn = (float) Math.sin(yaw);
        int n = Math.max(1, (int) steps);
        float rise = h / n;
        float run = d / n;
        for (int i = 0; i < n; i++) {
            float lz = -d * 0.5f + run * (i + 0.5f);
            float ly = y - h * 0.5f + rise * (i + 0.5f);
            float lx = 0f;
            float wx = lx * cs + lz * sn;
            float wz = -lx * sn + lz * cs;
            box(x + wx, ly, z + wz, w * 0.5f, rise * 0.5f, run * 0.55f, yawDeg,
                    pack(r * 0.9f, g * 0.9f, b * 0.9f, a), texParam, 0f);
        }
    }

    /** Le disque de l'ancre de harpon : un anneau, pas une icone (05.02). */
    public void ring(float cx, float cy, float cz, float radius, int argb,
                     float emit) {
        float r = ShaderLib.r(argb);
        float g = ShaderLib.g(argb);
        float b = ShaderLib.b(argb);
        float a = ShaderLib.a(argb);
        int seg = 14;
        int base = vertCount;
        for (int i = 0; i <= seg; i++) {
            float t = (float) (i * Math.PI * 2.0 / seg);
            float ct = (float) Math.cos(t);
            float st = (float) Math.sin(t);
            vertex(cx + ct * radius, cy, cz + st * radius, 0f, 1f, 0f,
                    r, g, b, a, 0f, emit);
            vertex(cx + ct * radius * 0.62f, cy, cz + st * radius * 0.62f, 0f, 1f, 0f,
                    r * 0.7f, g * 0.7f, b * 0.7f, a, 0f, emit * 0.6f);
        }
        for (int i = 0; i < seg; i++) {
            int o = base + i * 2;
            quad(o, o + 1, o + 3, o + 2);
        }
    }

    /** Un plan horizontal : la Maree de verre, les pontons, les sols. */
    public void plane(float cx, float cy, float cz, float hx, float hz,
                      int argb, float glass, float emit) {
        float r = ShaderLib.r(argb);
        float g = ShaderLib.g(argb);
        float b = ShaderLib.b(argb);
        float a = ShaderLib.a(argb);
        int base = vertCount;
        vertex(cx - hx, cy, cz - hz, 0f, 1f, 0f, r, g, b, a, glass, emit);
        vertex(cx + hx, cy, cz - hz, 0f, 1f, 0f, r, g, b, a, glass, emit);
        vertex(cx + hx, cy, cz + hz, 0f, 1f, 0f, r, g, b, a, glass, emit);
        vertex(cx - hx, cy, cz + hz, 0f, 1f, 0f, r, g, b, a, glass, emit);
        quad(base, base + 1, base + 2, base + 3);
    }

    /**
     * Un segment epais : le cable du harpon, les rampes de fortune, les
     * membrures du Phare.
     */
    public void beam(float x0, float y0, float z0, float x1, float y1, float z1,
                     float thickness, int argb, float emit) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) {
            return;
        }
        float cx = (x0 + x1) * 0.5f;
        float cy = (y0 + y1) * 0.5f;
        float cz = (z0 + z1) * 0.5f;
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float pitch = (float) Math.toDegrees(Math.asin(dy / len));
        float r = ShaderLib.r(argb);
        float g = ShaderLib.g(argb);
        float b = ShaderLib.b(argb);
        float a = ShaderLib.a(argb);
        /* un prisme a 4 faces oriente, suffisant a 3,4 m de distance camera */
        float h = thickness;
        float yawR = (float) Math.toRadians(yaw);
        float pitR = (float) Math.toRadians(pitch);
        float cy2 = (float) Math.cos(yawR);
        float sy2 = (float) Math.sin(yawR);
        float cp = (float) Math.cos(pitR);
        float sp = (float) Math.sin(pitR);
        float[] axis = {sy2 * cp, sp, cy2 * cp};
        float[] side = {cy2, 0f, -sy2};
        float[] up = {
                -sy2 * sp, cp, -cy2 * sp};
        int base = vertCount;
        for (int s = -1; s <= 1; s += 2) {
            for (int u = -1; u <= 1; u += 2) {
                for (int e = -1; e <= 1; e += 2) {
                    float px = cx + axis[0] * (len * 0.5f * e)
                            + side[0] * (h * s) + up[0] * (h * u);
                    float py = cy + axis[1] * (len * 0.5f * e)
                            + side[1] * (h * s) + up[1] * (h * u);
                    float pz = cz + axis[2] * (len * 0.5f * e)
                            + side[2] * (h * s) + up[2] * (h * u);
                    float nx = side[0] * s + up[0] * u;
                    float ny = side[1] * s + up[1] * u;
                    float nz = side[2] * s + up[2] * u;
                    float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (nl > 1e-4f) {
                        nx /= nl;
                        ny /= nl;
                        nz /= nl;
                    }
                    vertex(px, py, pz, nx, ny, nz, r, g, b, a, 0f, emit);
                }
            }
        }
        /* 8 sommets : 0..3 = une extremité, 4..7 = l'autre */
        quad(base + 0, base + 1, base + 5, base + 4);
        quad(base + 2, base + 6, base + 7, base + 3);
        quad(base + 0, base + 4, base + 6, base + 2);
        quad(base + 1, base + 3, base + 7, base + 5);
    }

    /**
     * Un cylindre creux : le fut du Phare (212 m), les colonnes du marche,
     * les conduits de S5.
     */
    public void cylinder(float cx, float y0, float cz, float y1, float radius,
                         int segments, int argb, float emit) {
        float r = ShaderLib.r(argb);
        float g = ShaderLib.g(argb);
        float b = ShaderLib.b(argb);
        float a = ShaderLib.a(argb);
        int seg = Math.max(6, segments);
        int base = vertCount;
        for (int i = 0; i <= seg; i++) {
            float t = (float) (i * Math.PI * 2.0 / seg);
            float ct = (float) Math.cos(t);
            float st = (float) Math.sin(t);
            vertex(cx + ct * radius, y0, cz + st * radius, ct, 0f, st,
                    r * 0.75f, g * 0.75f, b * 0.75f, a, 0f, emit);
            vertex(cx + ct * radius, y1, cz + st * radius, ct, 0f, st,
                    r, g, b, a, 0f, emit);
        }
        for (int i = 0; i < seg; i++) {
            int o = base + i * 2;
            quad(o, o + 2, o + 3, o + 1);
        }
    }

    public static int pack(float r, float g, float b, float a) {
        int ir = clamp255(r);
        int ig = clamp255(g);
        int ib = clamp255(b);
        int ia = clamp255(a);
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    private static int clamp255(float v) {
        int i = (int) (Math.max(0f, Math.min(1f, v)) * 255f + 0.5f);
        return i > 255 ? 255 : i;
    }

    /* ------------------------------------------------------------------ */
    /* Telechargement GPU                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * Une capsule conique : deux hemispheres et un tube en douceur entre deux
     * rayons. Les corps (Lohen, les habitants) sont faits de ca — plus aucune
     * boite ni prisme a 4 faces pour la chair et le tissu.
     */
    public void capsule(float x0, float y0, float z0, float x1, float y1, float z1,
                        float r0, float r1, int argb, float emit, int segs) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-5f) {
            ellipsoid(x0, y0, z0, r0, r0, r0, argb, emit, segs, 4, -1.5708f, 1.5708f);
            return;
        }
        float ux = dx / len, uy = dy / len, uz = dz / len;
        float ax = Math.abs(uy) < 0.9f ? 0f : 1f;
        float ay = Math.abs(uy) < 0.9f ? 1f : 0f;
        float vx = -uz * ay;
        float vy = uz * ax;
        float vz = ux * ay - uy * ax;
        float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx /= vl; vy /= vl; vz /= vl;
        float wx = uy * vz - uz * vy;
        float wy = uz * vx - ux * vz;
        float wz = ux * vy - uy * vx;
        float cr = ShaderLib.r(argb), cg = ShaderLib.g(argb);
        float cb = ShaderLib.b(argb), ca = ShaderLib.a(argb);
        int seg = Math.max(5, segs);
        int apex0 = vertex(x0 - ux * r0, y0 - uy * r0, z0 - uz * r0,
                -ux, -uy, -uz, cr, cg, cb, ca, 0f, emit);
        int[] rb = new int[8];
        float[] th0 = {-80f, -50f, -20f, 0f};
        for (int i = 0; i < 4; i++) {
            rb[i] = ringOf(x0, y0, z0, ux, uy, uz, vx, vy, vz, wx, wy, wz,
                    r0, th0[i], cr, cg, cb, ca, emit, seg);
        }
        rb[4] = ringOf(x1, y1, z1, ux, uy, uz, vx, vy, vz, wx, wy, wz,
                r1, 0f, cr, cg, cb, ca, emit, seg);
        float[] th1 = {20f, 50f, 80f};
        for (int i = 0; i < 3; i++) {
            rb[5 + i] = ringOf(x1, y1, z1, ux, uy, uz, vx, vy, vz, wx, wy, wz,
                    r1, th1[i], cr, cg, cb, ca, emit, seg);
        }
        int apex1 = vertex(x1 + ux * r1, y1 + uy * r1, z1 + uz * r1,
                ux, uy, uz, cr, cg, cb, ca, 0f, emit);
        for (int s = 0; s < seg; s++) {
            int s2 = (s + 1) % seg;
            tri(apex0, rb[0] + s2, rb[0] + s);
            tri(apex1, rb[7] + s, rb[7] + s2);
        }
        for (int i = 0; i < 7; i++) {
            for (int s = 0; s < seg; s++) {
                int s2 = (s + 1) % seg;
                quad(rb[i] + s, rb[i] + s2, rb[i + 1] + s2, rb[i + 1] + s);
            }
        }
    }

    private int ringOf(float px, float py, float pz,
                       float ux, float uy, float uz,
                       float vx, float vy, float vz,
                       float wx, float wy, float wz,
                       float rad, float thDeg,
                       float cr, float cg, float cb, float ca, float emit, int seg) {
        float th = (float) Math.toRadians(thDeg);
        float st = (float) Math.sin(th), ct = (float) Math.cos(th);
        float cx = px + ux * rad * st;
        float cy = py + uy * rad * st;
        float cz = pz + uz * rad * st;
        int base = vertCount;
        for (int s = 0; s < seg; s++) {
            float a = (float) (s * Math.PI * 2.0 / seg);
            float cA = (float) Math.cos(a), sA = (float) Math.sin(a);
            float rx = vx * cA + wx * sA;
            float ry = vy * cA + wy * sA;
            float rz = vz * cA + wz * sA;
            vertex(cx + rx * rad * ct, cy + ry * rad * ct, cz + rz * rad * ct,
                    ux * st + rx * ct, uy * st + ry * ct, uz * st + rz * ct,
                    cr, cg, cb, ca, 0f, emit);
        }
        return base;
    }

    /**
     * Un ellipsoide : la tete, les mains, les bottes — et la calotte des
     * cheveux quand elev0 > -pi/2.
     */
    public void ellipsoid(float cx, float cy, float cz,
                          float rx, float ry, float rz,
                          int argb, float emit, int segs, int rings,
                          float elev0, float elev1) {
        float cr = ShaderLib.r(argb), cg = ShaderLib.g(argb);
        float cb = ShaderLib.b(argb), ca = ShaderLib.a(argb);
        int seg = Math.max(5, segs);
        int rg = Math.max(2, rings);
        float e0 = Math.max(-1.5708f, elev0);
        float e1 = Math.min(1.5708f, elev1);
        int[] rb = new int[rg];
        for (int j = 0; j < rg; j++) {
            float th = e0 + (e1 - e0) * (j + 0.5f) / rg;
            float ct = (float) Math.cos(th), st = (float) Math.sin(th);
            int base = vertCount;
            for (int s = 0; s < seg; s++) {
                float a = (float) (s * Math.PI * 2.0 / seg);
                float cA = (float) Math.cos(a), sA = (float) Math.sin(a);
                float nx = ct * cA / Math.max(1e-4f, rx);
                float ny = st / Math.max(1e-4f, ry);
                float nz = ct * sA / Math.max(1e-4f, rz);
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                vertex(cx + rx * ct * cA, cy + ry * st, cz + rz * ct * sA,
                        nx / nl, ny / nl, nz / nl, cr, cg, cb, ca, 0f, emit);
            }
            rb[j] = base;
        }
        for (int j = 0; j < rg - 1; j++) {
            for (int s = 0; s < seg; s++) {
                int s2 = (s + 1) % seg;
                quad(rb[j] + s, rb[j] + s2, rb[j + 1] + s2, rb[j + 1] + s);
            }
        }
        if (e0 <= -1.5f) {
            int apex = vertex(cx, cy - ry, cz, 0f, -1f, 0f, cr, cg, cb, ca, 0f, emit);
            for (int s = 0; s < seg; s++) {
                tri(apex, rb[0] + (s + 1) % seg, rb[0] + s);
            }
        }
        if (e1 >= 1.5f) {
            int apex = vertex(cx, cy + ry, cz, 0f, 1f, 0f, cr, cg, cb, ca, 0f, emit);
            for (int s = 0; s < seg; s++) {
                tri(apex, rb[rg - 1] + s, rb[rg - 1] + (s + 1) % seg);
            }
        }
    }

    public void upload() {
        if (vertCount == 0 || indexCount == 0) {
            return;
        }
        if (vbo == 0) {
            int[] ids = new int[2];
            GLES20.glGenBuffers(2, ids, 0);
            vbo = ids[0];
            ibo = ids[1];
        }
        FloatBuffer vb = GlUtil.allocate(vertCount * STRIDE_FLOATS);
        vb.put(verts, 0, vertCount * STRIDE_FLOATS).position(0);
        ShortBuffer ib = GlUtil.shorts(trimIndices());
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertCount * STRIDE_FLOATS * 4,
                vb, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexCount * 2,
                ib, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        uploaded = true;
    }

    /** Telechargement dynamique : le verre, le faisceau, les personnages. */
    public void uploadDynamic() {
        if (vertCount == 0 || indexCount == 0) {
            return;
        }
        if (vbo == 0) {
            int[] ids = new int[2];
            GLES20.glGenBuffers(2, ids, 0);
            vbo = ids[0];
            ibo = ids[1];
        }
        FloatBuffer vb = GlUtil.allocate(vertCount * STRIDE_FLOATS);
        vb.put(verts, 0, vertCount * STRIDE_FLOATS).position(0);
        ShortBuffer ib = GlUtil.shorts(trimIndices());
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertCount * STRIDE_FLOATS * 4,
                vb, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexCount * 2,
                ib, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        uploaded = true;
    }

    private short[] trimIndices() {
        if (indexCount == indices.length) {
            return indices;
        }
        short[] out = new short[indexCount];
        System.arraycopy(indices, 0, out, 0, indexCount);
        return out;
    }

    public boolean uploaded() {
        return uploaded;
    }

    public int vbo() {
        return vbo;
    }

    public int ibo() {
        return ibo;
    }

    public void bind(int aPos, int aNormal, int aColor, int aParam) {
        int stride = STRIDE_FLOATS * 4;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo);
        if (aPos >= 0) {
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, 0);
        }
        if (aNormal >= 0) {
            GLES20.glEnableVertexAttribArray(aNormal);
            GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, stride, 12);
        }
        if (aColor >= 0) {
            GLES20.glEnableVertexAttribArray(aColor);
            GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, stride, 24);
        }
        if (aParam >= 0) {
            GLES20.glEnableVertexAttribArray(aParam);
            GLES20.glVertexAttribPointer(aParam, 2, GLES20.GL_FLOAT, false, stride, 40);
        }
    }

    public static void unbind(int aPos, int aNormal, int aColor, int aParam) {
        if (aPos >= 0) {
            GLES20.glDisableVertexAttribArray(aPos);
        }
        if (aNormal >= 0) {
            GLES20.glDisableVertexAttribArray(aNormal);
        }
        if (aColor >= 0) {
            GLES20.glDisableVertexAttribArray(aColor);
        }
        if (aParam >= 0) {
            GLES20.glDisableVertexAttribArray(aParam);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public void draw() {
        if (!uploaded || indexCount == 0) {
            return;
        }
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount,
                GLES20.GL_UNSIGNED_SHORT, 0);
    }

    public void release() {
        if (vbo != 0) {
            GLES20.glDeleteBuffers(1, new int[]{vbo}, 0);
            GLES20.glDeleteBuffers(1, new int[]{ibo}, 0);
            vbo = 0;
            ibo = 0;
        }
        uploaded = false;
    }
}
