/*
 * LOHEN — sim/math/Geom.java
 *
 * Geometrie de collision : OBB a lacet seul (le monde est bati sur une grille
 * 0,5 m en XY et 0,25 m en Z — 06.22), AABB, capsule du joueur, rayons.
 * Un seul type de solide : la boite orientee en Y. C'est ce qui permet
 * un monde entierement procedural, sans asset binaire, et une physique
 * deterministe testable hors device (BLOC 18.06).
 */
package com.velmora.lohen.sim.math;

public final class Geom {

    /** Drapeaux de surface — langage visuel de traversee (09.21). */
    public static final int FLAG_WALKABLE = 1;
    public static final int FLAG_CLIMBABLE = 1 << 1;
    public static final int FLAG_LEDGE = 1 << 2;
    public static final int FLAG_ANCHOR = 1 << 3;
    public static final int FLAG_GLASS = 1 << 4;         /* le grappin refuse (08.08c) */
    public static final int FLAG_FRAGILE = 1 << 5;       /* cede sous le poids (06.26) */
    public static final int FLAG_DEADLY = 1 << 6;
    public static final int FLAG_INTERACTIVE = 1 << 7;   /* contraste d'humidite, pas d'outline */
    public static final int FLAG_SLIDE = 1 << 8;         /* glissade sur verre */
    public static final int FLAG_HEAT_WELL = 1 << 9;     /* S2 : le verre ramollit (09.11) */
    public static final int FLAG_INDOOR = 1 << 10;
    public static final int FLAG_LAMP_ROOM = 1 << 11;    /* 09.44, 6 pieces */
    public static final int FLAG_TRANSITION = 1 << 12;   /* couloir de transition (09.51) */
    public static final int FLAG_WATER_SHALLOW = 1 << 13;

    /* ------------------------------------------------------------------ */
    /* Matieres (13.28 : 9 surfaces sonores, 8 variations, 3 vitesses)     */
    /* ------------------------------------------------------------------ */
    public static final int MAT_WOOD = 0;          /* bois sec */
    public static final int MAT_WOOD_WET = 1;      /* bois mouille */
    public static final int MAT_STONE = 2;         /* pierre */
    public static final int MAT_STONE_WET = 3;     /* pierre mouillee */
    public static final int MAT_GRAVEL = 4;        /* gravier */
    public static final int MAT_GLASS = 5;         /* verre de la Maree (13.29) */
    public static final int MAT_METAL = 6;         /* metal, passerelles, treuils */
    public static final int MAT_CARPET = 7;        /* tapis / parquet (interieurs) */
    public static final int MAT_WATER = 8;         /* eau peu profonde */
    public static final int MAT_COUNT = 9;

    public static final String[] MAT_NAMES = {
            "bois_sec", "bois_mouille", "pierre", "pierre_mouillee", "gravier",
            "verre_maree", "metal", "tapis_parquet", "eau_peu_profonde"};

    public static String matName(int m) {
        return m >= 0 && m < MAT_COUNT ? MAT_NAMES[m] : MAT_NAMES[MAT_STONE];
    }

    private Geom() { }

    /** Boite orientee autour de Y : centre, demi-extents, lacet. */
    public static final class Box {
        public float cx, cy, cz;
        public float hx, hy, hz;
        public float yaw;
        public int flags;
        public int material;
        public int id;

        public Box() { }

        public Box(float cx, float cy, float cz, float hx, float hy, float hz,
                   float yaw, int flags, int material) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.hx = hx;
            this.hy = hy;
            this.hz = hz;
            this.yaw = yaw;
            this.flags = flags;
            this.material = material;
        }

        public boolean has(int flag) {
            return (flags & flag) != 0;
        }

        public float minY() {
            return cy - hy;
        }

        public float maxY() {
            return cy + hy;
        }

        /** Test point/boite en espace local (rotation inverse du lacet). */
        public boolean containsXZ(float px, float pz, float pad) {
            float dx = px - cx;
            float dz = pz - cz;
            if (yaw != 0f) {
                float c = (float) Math.cos(-yaw);
                float s = (float) Math.sin(-yaw);
                float lx = dx * c - dz * s;
                float lz = dx * s + dz * c;
                dx = lx;
                dz = lz;
            }
            return Math.abs(dx) <= hx + pad && Math.abs(dz) <= hz + pad;
        }

        public boolean overlapsY(float lo, float hi) {
            return lo <= maxY() && hi >= minY();
        }

        /** Repousse un point hors de la boite en XZ ; renvoie la penetration. */
        public float pushOutXZ(float[] p, float radius) {
            float dx = p[0] - cx;
            float dz = p[2] - cz;
            float c = (float) Math.cos(-yaw);
            float s = (float) Math.sin(-yaw);
            float lx = dx * c - dz * s;
            float lz = dx * s + dz * c;
            float px = hx + radius - Math.abs(lx);
            float pz = hz + radius - Math.abs(lz);
            if (px <= 0f || pz <= 0f) {
                return 0f;
            }
            float depth = Math.min(px, pz);
            if (px < pz) {
                lx += Math.signum(lx) * px;
            } else {
                lz += Math.signum(lz) * pz;
            }
            c = (float) Math.cos(yaw);
            s = (float) Math.sin(yaw);
            p[0] = cx + lx * c - lz * s;
            p[2] = cz + lx * s + lz * c;
            return depth;
        }

        /** Hauteur du sommet si le point (x,z) est au-dessus de la boite. */
        public float topIfAbove(float x, float z) {
            return containsXZ(x, z, 0f) ? maxY() : Float.NaN;
        }
    }

    /** Rayon en espace monde, utilise pour le grappin, le regard, les IK. */
    public static final class Ray {
        public float ox, oy, oz;
        public float dx, dy, dz;

        public Ray set(float ox, float oy, float oz, float dx, float dy, float dz) {
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-8f) {
                this.dx = 0f;
                this.dy = 0f;
                this.dz = -1f;
            } else {
                this.dx = dx / len;
                this.dy = dy / len;
                this.dz = dz / len;
            }
            return this;
        }
    }

    /** Intersection rayon/boite orientee (slab method) ; renvoie t ou -1. */
    public static float rayBox(Ray r, Box b, float maxT) {
        float c = (float) Math.cos(-b.yaw);
        float s = (float) Math.sin(-b.yaw);
        float ox = (r.ox - b.cx) * c - (r.oz - b.cz) * s;
        float oz = (r.ox - b.cx) * s + (r.oz - b.cz) * c;
        float oy = r.oy - b.cy;
        float dx = r.dx * c - r.dz * s;
        float dz = r.dx * s + r.dz * c;
        float dy = r.dy;
        float tmin = -maxT;
        float tmax = maxT;
        float[] o = {ox, oy, oz};
        float[] d = {dx, dy, dz};
        float[] h = {b.hx, b.hy, b.hz};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-8f) {
                if (Math.abs(o[i]) > h[i]) {
                    return -1f;
                }
                continue;
            }
            float inv = 1f / d[i];
            float t1 = (-h[i] - o[i]) * inv;
            float t2 = (h[i] - o[i]) * inv;
            if (t1 > t2) {
                float tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) {
                return -1f;
            }
        }
        return tmin >= 0f ? tmin : (tmax >= 0f ? 0f : -1f);
    }

    /** Distance capsule verticale (centre p, demi-hauteur hh, rayon r) / boite. */
    public static boolean capsuleBox(float px, float py, float pz, float halfHeight,
                                     float radius, Box b) {
        float top = py + halfHeight;
        float bottom = py - halfHeight;
        if (top < b.minY() || bottom > b.maxY()) {
            return false;
        }
        /* echantillonne le disque de la capsule sur 8 points + centre */
        float clampedY = Maths.clamp(b.cy, bottom, top);
        float dy = b.cy - clampedY;
        if (Math.abs(dy) > b.hy + radius) {
            return false;
        }
        return b.containsXZ(px, pz, radius);
    }
}
