/*
 * LOHEN — sim/math/Spline.java
 *
 * Splines de camera pour les 11 cinematiques (07.21 : pistes camera splines +
 * FOV + DOF) et pour les rails de Echo (11.06).
 * Catmull-Rom : lisse, sans overshoot, peu couteux, deterministe.
 *
 * Deux modes de construction :
 *  - fixe   : new Spline(float[] points, float[] fovs)
 *  - dynamique (lecture JSON) : new Spline() + add(x,y,z) + build()
 */
package com.velmora.lohen.sim.math;

import java.util.ArrayList;
import java.util.List;

public final class Spline {

    private float[] pts;         /* x,y,z par point */
    private float[] fov;         /* FOV par point (05.10) */
    private float[] time;        /* temps cumulatif normalise */
    private int count;
    private final List<Float> pending = new ArrayList<Float>(24);
    private final List<Float> pendingFov = new ArrayList<Float>(24);

    public Spline() {
        this.pts = new float[0];
        this.time = new float[0];
        this.count = 0;
    }

    public Spline(float[] points, float[] fovs) {
        this.count = points.length / 3;
        this.pts = points;
        this.fov = fovs != null && fovs.length == count ? fovs : null;
        this.time = new float[count];
        rebuildTimes();
    }

    private void rebuildTimes() {
        float total = 0f;
        for (int i = 0; i < count; i++) {
            time[i] = total;
            if (i + 1 < count) {
                total += dist(i, i + 1);
            }
        }
        if (total > 1e-6f) {
            for (int i = 0; i < count; i++) {
                time[i] /= total;
            }
        }
    }

    private float dist(int a, int b) {
        float dx = pts[b * 3] - pts[a * 3];
        float dy = pts[b * 3 + 1] - pts[a * 3 + 1];
        float dz = pts[b * 3 + 2] - pts[a * 3 + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /* ---------------- construction dynamique ---------------- */

    public Spline add(float x, float y, float z) {
        pending.add(x);
        pending.add(y);
        pending.add(z);
        return this;
    }

    public Spline add(float x, float y, float z, float fovDeg) {
        add(x, y, z);
        pendingFov.add(fovDeg);
        return this;
    }

    public void clear() {
        pending.clear();
        pendingFov.clear();
        pts = new float[0];
        fov = null;
        time = new float[0];
        count = 0;
    }

    /** Materialise les points ajoutes. */
    public Spline build() {
        count = pending.size() / 3;
        pts = new float[pending.size()];
        for (int i = 0; i < pts.length; i++) {
            pts[i] = pending.get(i);
        }
        if (pendingFov.size() == count) {
            fov = new float[count];
            for (int i = 0; i < count; i++) {
                fov[i] = pendingFov.get(i);
            }
        } else {
            fov = null;
        }
        time = new float[count];
        rebuildTimes();
        return this;
    }

    /* ---------------- evaluation ---------------- */

    public int pointCount() {
        return count;
    }

    public int count() {
        return count;
    }

    public float length() {
        return count > 1 ? time[count - 1] : 0f;
    }

    /** Position en t (0..1) dans un tableau float[3]. */
    public void at(float t, float[] out) {
        if (count == 0) {
            out[0] = out[1] = out[2] = 0f;
            return;
        }
        if (count == 1) {
            out[0] = pts[0];
            out[1] = pts[1];
            out[2] = pts[2];
            return;
        }
        float u = Maths.clamp01(t) * (count - 1);
        int i = (int) Math.floor(u);
        if (i >= count - 1) {
            i = count - 2;
        }
        float f = u - i;
        int p0 = Math.max(0, i - 1);
        int p1 = i;
        int p2 = Math.min(count - 1, i + 1);
        int p3 = Math.min(count - 1, i + 2);
        for (int k = 0; k < 3; k++) {
            out[k] = catmull(pts[p0 * 3 + k], pts[p1 * 3 + k], pts[p2 * 3 + k], pts[p3 * 3 + k], f);
        }
    }

    /** Position en t dans un Vec3 (interface du CinematicPlayer). */
    public Vec3 evaluate(float t, Vec3 out) {
        float[] tmp = TMP;
        at(t, tmp);
        return out.set(tmp[0], tmp[1], tmp[2]);
    }

    private static final float[] TMP = new float[3];

    public static float catmull(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * ((2f * p1)
                + (-p0 + p2) * t
                + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2
                + (-p0 + 3f * p1 - 3f * p2 + p3) * t3);
    }

    public float fovAt(float t) {
        if (fov == null || count < 2) {
            return 46f;
        }
        float u = Maths.clamp01(t) * (count - 1);
        int i = Maths.clamp((int) Math.floor(u), 0, count - 2);
        float f = u - i;
        return Maths.lerp(fov[i], fov[i + 1], f);
    }

    public float[] points() {
        return pts;
    }
}
