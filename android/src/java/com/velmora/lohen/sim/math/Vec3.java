/*
 * LOHEN — Les Sept Lettres de Velmora · Chapitre 1
 * sim/math/Vec3.java
 *
 * Vecteur 3D mutable. Les boucles chaudes (physique, IK, cable, foule)
 * n'allouent jamais : on reutilise des instances (BLOC 04.12).
 */
package com.velmora.lohen.sim.math;

public final class Vec3 {
    public float x, y, z;

    public Vec3() { }

    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3 set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vec3 set(Vec3 o) {
        return set(o.x, o.y, o.z);
    }

    public Vec3 copy() {
        return new Vec3(x, y, z);
    }

    public Vec3 add(Vec3 o) {
        x += o.x;
        y += o.y;
        z += o.z;
        return this;
    }

    public Vec3 addScaled(Vec3 o, float s) {
        x += o.x * s;
        y += o.y * s;
        z += o.z * s;
        return this;
    }

    public Vec3 sub(Vec3 o) {
        x -= o.x;
        y -= o.y;
        z -= o.z;
        return this;
    }

    public Vec3 scale(float s) {
        x *= s;
        y *= s;
        z *= s;
        return this;
    }

    public float dot(Vec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vec3 cross(Vec3 o, Vec3 out) {
        float cx = y * o.z - z * o.y;
        float cy = z * o.x - x * o.z;
        float cz = x * o.y - y * o.x;
        return out.set(cx, cy, cz);
    }

    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public float lengthSq() {
        return x * x + y * y + z * z;
    }

    public float distance(Vec3 o) {
        float dx = x - o.x;
        float dy = y - o.y;
        float dz = z - o.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public float distanceXZ(Vec3 o) {
        float dx = x - o.x;
        float dz = z - o.z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    /** Normalise et renvoie la longueur precedente (0 si vecteur nul). */
    public float normalize() {
        float len = length();
        if (len < 1e-8f) {
            set(0f, 0f, 0f);
            return 0f;
        }
        float inv = 1f / len;
        x *= inv;
        y *= inv;
        z *= inv;
        return len;
    }

    public Vec3 lerp(Vec3 to, float t) {
        x += (to.x - x) * t;
        y += (to.y - y) * t;
        z += (to.z - z) * t;
        return this;
    }

    /** Lissage exponentiel independant du framerate (04.12, camera 08.23). */
    public Vec3 damp(Vec3 to, float lambda, float dt) {
        float t = 1f - (float) Math.exp(-lambda * dt);
        return lerp(to, t);
    }

    public boolean isFinite() {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
    }

    /** Distance d'un point a un segment [a,b] — sert au cable et aux rambardes. */
    public static float distancePointSegment(Vec3 p, Vec3 a, Vec3 b) {
        float abx = b.x - a.x;
        float aby = b.y - a.y;
        float abz = b.z - a.z;
        float apx = p.x - a.x;
        float apy = p.y - a.y;
        float apz = p.z - a.z;
        float ab2 = abx * abx + aby * aby + abz * abz;
        float t = ab2 < 1e-8f ? 0f : (apx * abx + apy * aby + apz * abz) / ab2;
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        float dx = apx - abx * t;
        float dy = apy - aby * t;
        float dz = apz - abz * t;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
