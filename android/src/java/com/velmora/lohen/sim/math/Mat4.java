/*
 * LOHEN — sim/math/Mat4.java
 *
 * Matrice 4x4 colonne-majeure (compatible OpenGL ES / android.opengl.Matrix).
 * Toutes les operations critiques existent en version "sans allocation"
 * (resultat ecrit dans un tableau fourni).
 */
package com.velmora.lohen.sim.math;

public final class Mat4 {

    private Mat4() { }

    public static float[] create() {
        float[] m = new float[16];
        identity(m);
        return m;
    }

    public static void identity(float[] m) {
        for (int i = 0; i < 16; i++) {
            m[i] = 0f;
        }
        m[0] = m[5] = m[10] = m[15] = 1f;
    }

    public static void copy(float[] src, float[] dst) {
        System.arraycopy(src, 0, dst, 0, 16);
    }

    public static void multiply(float[] a, float[] b, float[] out) {
        float[] tmp = out == a || out == b ? new float[16] : out;
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + r] * b[c * 4 + k];
                }
                tmp[c * 4 + r] = sum;
            }
        }
        if (tmp != out) {
            System.arraycopy(tmp, 0, out, 0, 16);
        }
    }

    public static void translate(float[] m, float x, float y, float z) {
        m[12] += m[0] * x + m[4] * y + m[8] * z;
        m[13] += m[1] * x + m[5] * y + m[9] * z;
        m[14] += m[2] * x + m[6] * y + m[10] * z;
        m[15] += m[3] * x + m[7] * y + m[11] * z;
    }

    public static void scale(float[] m, float x, float y, float z) {
        for (int i = 0; i < 4; i++) {
            m[i] *= x;
            m[4 + i] *= y;
            m[8 + i] *= z;
        }
    }

    public static void rotateY(float[] m, float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        float a00 = m[0], a01 = m[1], a02 = m[2], a03 = m[3];
        float a20 = m[8], a21 = m[9], a22 = m[10], a23 = m[11];
        m[0] = a00 * c - a20 * s;
        m[1] = a01 * c - a21 * s;
        m[2] = a02 * c - a22 * s;
        m[3] = a03 * c - a23 * s;
        m[8] = a00 * s + a20 * c;
        m[9] = a01 * s + a21 * c;
        m[10] = a02 * s + a22 * c;
        m[11] = a03 * s + a23 * c;
    }

    public static void rotateX(float[] m, float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        float a10 = m[4], a11 = m[5], a12 = m[6], a13 = m[7];
        float a20 = m[8], a21 = m[9], a22 = m[10], a23 = m[11];
        m[4] = a10 * c + a20 * s;
        m[5] = a11 * c + a21 * s;
        m[6] = a12 * c + a22 * s;
        m[7] = a13 * c + a23 * s;
        m[8] = a20 * c - a10 * s;
        m[9] = a21 * c - a11 * s;
        m[10] = a22 * c - a12 * s;
        m[11] = a23 * c - a13 * s;
    }

    public static void rotateZ(float[] m, float radians) {
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        float a00 = m[0], a01 = m[1], a02 = m[2], a03 = m[3];
        float a10 = m[4], a11 = m[5], a12 = m[6], a13 = m[7];
        m[0] = a00 * c + a10 * s;
        m[1] = a01 * c + a11 * s;
        m[2] = a02 * c + a12 * s;
        m[3] = a03 * c + a13 * s;
        m[4] = a10 * c - a00 * s;
        m[5] = a11 * c - a01 * s;
        m[6] = a12 * c - a02 * s;
        m[7] = a13 * c - a03 * s;
    }

    public static void rotationYawPitch(float[] out, float yaw, float pitch) {
        identity(out);
        rotateY(out, yaw);
        rotateX(out, pitch);
    }

    public static void trs(float[] out, float px, float py, float pz,
                           float yaw, float pitch, float roll,
                           float sx, float sy, float sz) {
        identity(out);
        rotateY(out, yaw);
        rotateX(out, pitch);
        rotateZ(out, roll);
        scale(out, sx, sy, sz);
        out[12] = px;
        out[13] = py;
        out[14] = pz;
    }

    /** Perspective OpenGL (fov vertical en degres). FOV : 46 / 34 / 62 (05.10). */
    public static void perspective(float[] out, float fovDeg, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(Math.toRadians(fovDeg) * 0.5));
        identity(out);
        out[0] = f / aspect;
        out[5] = f;
        out[10] = (far + near) / (near - far);
        out[11] = -1f;
        out[14] = (2f * far * near) / (near - far);
        out[15] = 0f;
    }

    public static void ortho(float[] out, float l, float r, float b, float t, float n, float f) {
        identity(out);
        out[0] = 2f / (r - l);
        out[5] = 2f / (t - b);
        out[10] = -2f / (f - n);
        out[12] = -(r + l) / (r - l);
        out[13] = -(t + b) / (t - b);
        out[14] = -(f + n) / (f - n);
    }

    /**
     * Look-at. `up` est fourni pour permettre l'inclinaison de cadre
     * (regle de la ligne d'horizon, 05.11).
     */
    public static void lookAt(float[] out, float ex, float ey, float ez,
                              float cx, float cy, float cz,
                              float ux, float uy, float uz) {
        float zx = ex - cx, zy = ey - cy, zz = ez - cz;
        float len = (float) Math.sqrt(zx * zx + zy * zy + zz * zz);
        if (len < 1e-6f) {
            zz = 1f;
            len = 1f;
        }
        zx /= len;
        zy /= len;
        zz /= len;
        float xx = uy * zz - uz * zy;
        float xy = uz * zx - ux * zz;
        float xz = ux * zy - uy * zx;
        len = (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
        if (len < 1e-6f) {
            xx = 1f;
            xy = 0f;
            xz = 0f;
        } else {
            xx /= len;
            xy /= len;
            xz /= len;
        }
        float yx = zy * xz - zz * xy;
        float yy = zz * xx - zx * xz;
        float yz = zx * xy - zy * xx;
        out[0] = xx;
        out[1] = yx;
        out[2] = zx;
        out[3] = 0f;
        out[4] = xy;
        out[5] = yy;
        out[6] = zy;
        out[7] = 0f;
        out[8] = xz;
        out[9] = yz;
        out[10] = zz;
        out[11] = 0f;
        out[12] = -(xx * ex + xy * ey + xz * ez);
        out[13] = -(yx * ex + yy * ey + yz * ez);
        out[14] = -(zx * ex + zy * ey + zz * ez);
        out[15] = 1f;
    }

    /** Inversion generale (utilisee pour la vue->monde et le picking). */
    public static boolean invert(float[] m, float[] out) {
        float a00 = m[0], a01 = m[1], a02 = m[2], a03 = m[3];
        float a10 = m[4], a11 = m[5], a12 = m[6], a13 = m[7];
        float a20 = m[8], a21 = m[9], a22 = m[10], a23 = m[11];
        float a30 = m[12], a31 = m[13], a32 = m[14], a33 = m[15];
        float b00 = a00 * a11 - a01 * a10;
        float b01 = a00 * a12 - a02 * a10;
        float b02 = a00 * a13 - a03 * a10;
        float b03 = a01 * a12 - a02 * a11;
        float b04 = a01 * a13 - a03 * a11;
        float b05 = a02 * a13 - a03 * a12;
        float b06 = a20 * a31 - a21 * a30;
        float b07 = a20 * a32 - a22 * a30;
        float b08 = a20 * a33 - a23 * a30;
        float b09 = a21 * a32 - a22 * a31;
        float b10 = a21 * a33 - a23 * a31;
        float b11 = a22 * a33 - a23 * a32;
        float det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;
        if (Math.abs(det) < 1e-12f) {
            return false;
        }
        det = 1f / det;
        out[0] = (a11 * b11 - a12 * b10 + a13 * b09) * det;
        out[1] = (a02 * b10 - a01 * b11 - a03 * b09) * det;
        out[2] = (a31 * b05 - a32 * b04 + a33 * b03) * det;
        out[3] = (a22 * b04 - a21 * b05 - a23 * b03) * det;
        out[4] = (a12 * b08 - a10 * b11 - a13 * b07) * det;
        out[5] = (a00 * b11 - a02 * b08 + a03 * b07) * det;
        out[6] = (a32 * b02 - a30 * b05 - a33 * b01) * det;
        out[7] = (a20 * b05 - a22 * b02 + a23 * b01) * det;
        out[8] = (a10 * b10 - a11 * b08 + a13 * b06) * det;
        out[9] = (a01 * b08 - a00 * b10 - a03 * b06) * det;
        out[10] = (a30 * b04 - a31 * b02 + a33 * b00) * det;
        out[11] = (a21 * b02 - a20 * b04 - a23 * b00) * det;
        out[12] = (a11 * b07 - a10 * b09 - a12 * b06) * det;
        out[13] = (a00 * b09 - a01 * b07 + a02 * b06) * det;
        out[14] = (a31 * b01 - a30 * b03 - a32 * b00) * det;
        out[15] = (a20 * b03 - a21 * b01 + a22 * b00) * det;
        return true;
    }

    public static void transformPoint(float[] m, float x, float y, float z, float[] out3) {
        float w = m[3] * x + m[7] * y + m[11] * z + m[15];
        if (Math.abs(w) < 1e-9f) {
            w = 1f;
        }
        out3[0] = (m[0] * x + m[4] * y + m[8] * z + m[12]) / w;
        out3[1] = (m[1] * x + m[5] * y + m[9] * z + m[13]) / w;
        out3[2] = (m[2] * x + m[6] * y + m[10] * z + m[14]) / w;
    }

    public static void transformDir(float[] m, float x, float y, float z, float[] out3) {
        out3[0] = m[0] * x + m[4] * y + m[8] * z;
        out3[1] = m[1] * x + m[5] * y + m[9] * z;
        out3[2] = m[2] * x + m[6] * y + m[10] * z;
    }

    /** Extrait la base (sans translation) — sert aux IK et au rig. */
    public static void basis(float[] m, float[] out) {
        System.arraycopy(m, 0, out, 0, 12);
        out[3] = out[7] = out[11] = 0f;
        out[12] = out[13] = out[14] = 0f;
        out[15] = 1f;
    }
}
