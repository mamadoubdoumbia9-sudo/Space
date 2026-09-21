/*
 * LOHEN — sim/math/Quat.java
 *
 * Quaternion pour le rig procedural et le lissage de camera.
 * Conventions : -Z avant, +Y haut (06.02), personnage au repos face -Z.
 */
package com.velmora.lohen.sim.math;

public final class Quat {
    public float x, y, z, w;

    public Quat() {
        w = 1f;
    }

    public Quat(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    public Quat set(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        return this;
    }

    public Quat set(Quat o) {
        return set(o.x, o.y, o.z, o.w);
    }

    public static Quat fromAxisAngle(float ax, float ay, float az, float radians, Quat out) {
        float half = radians * 0.5f;
        float s = (float) Math.sin(half);
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-6f) {
            return out.set(0f, 0f, 0f, 1f);
        }
        float inv = s / len;
        return out.set(ax * inv, ay * inv, az * inv, (float) Math.cos(half));
    }

    public Quat multiply(Quat o, Quat out) {
        float nx = w * o.x + x * o.w + y * o.z - z * o.y;
        float ny = w * o.y - x * o.z + y * o.w + z * o.x;
        float nz = w * o.z + x * o.y - y * o.x + z * o.w;
        float nw = w * o.w - x * o.x - y * o.y - z * o.z;
        return out.set(nx, ny, nz, nw);
    }

    public Quat normalize() {
        float len = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (len < 1e-8f) {
            return set(0f, 0f, 0f, 1f);
        }
        float inv = 1f / len;
        x *= inv;
        y *= inv;
        z *= inv;
        w *= inv;
        return this;
    }

    public static Quat slerp(Quat a, Quat b, float t, Quat out) {
        float cosom = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        float bx = b.x, by = b.y, bz = b.z, bw = b.w;
        if (cosom < 0f) {
            cosom = -cosom;
            bx = -bx;
            by = -by;
            bz = -bz;
            bw = -bw;
        }
        float s0, s1;
        if (1f - cosom > 1e-6f) {
            float omega = (float) Math.acos(cosom);
            float sinom = (float) Math.sin(omega);
            s0 = (float) Math.sin((1f - t) * omega) / sinom;
            s1 = (float) Math.sin(t * omega) / sinom;
        } else {
            s0 = 1f - t;
            s1 = t;
        }
        return out.set(s0 * a.x + s1 * bx, s0 * a.y + s1 * by,
                s0 * a.z + s1 * bz, s0 * a.w + s1 * bw);
    }

    public void toMat4(float[] out) {
        float xx = x * x, yy = y * y, zz = z * z;
        float xy = x * y, xz = x * z, yz = y * z;
        float wx = w * x, wy = w * y, wz = w * z;
        out[0] = 1f - 2f * (yy + zz);
        out[1] = 2f * (xy + wz);
        out[2] = 2f * (xz - wy);
        out[3] = 0f;
        out[4] = 2f * (xy - wz);
        out[5] = 1f - 2f * (xx + zz);
        out[6] = 2f * (yz + wx);
        out[7] = 0f;
        out[8] = 2f * (xz + wy);
        out[9] = 2f * (yz - wx);
        out[10] = 1f - 2f * (xx + yy);
        out[11] = 0f;
        out[12] = 0f;
        out[13] = 0f;
        out[14] = 0f;
        out[15] = 1f;
    }

    public Vec3 rotate(Vec3 v, Vec3 out) {
        float ix = w * v.x + y * v.z - z * v.y;
        float iy = w * v.y + z * v.x - x * v.z;
        float iz = w * v.z + x * v.y - y * v.x;
        float iw = -x * v.x - y * v.y - z * v.z;
        return out.set(ix * w + iw * -x + iy * -z - iz * -y,
                iy * w + iw * -y + iz * -x - ix * -z,
                iz * w + iw * -z + ix * -y - iy * -x);
    }

    /** Yaw -> pitch (ordre utilise par la camera et le personnage). */
    public static Quat fromYawPitch(float yaw, float pitch, Quat out) {
        Quat qy = TMP_A;
        Quat qp = TMP_B;
        fromAxisAngle(0f, 1f, 0f, yaw, qy);
        fromAxisAngle(1f, 0f, 0f, pitch, qp);
        qy.multiply(qp, out);
        return out;
    }

    /* Scratch mutualise : jamais utilise de facon reentrante. */
    private static final Quat TMP_A = new Quat();
    private static final Quat TMP_B = new Quat();
}
