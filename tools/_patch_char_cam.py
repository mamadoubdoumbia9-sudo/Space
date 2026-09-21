# -*- coding: utf-8 -*-
"""Patch personnage + camera : capsules/ellipsoides pour les corps (fini les
boites et prismes a 4 faces), camera tactile lineaire et lissee."""
import sys

ROOT = "/home/user/Space/android/src/java/com/velmora/lohen"
miss = []


def load(p):
    return [open(ROOT + p, encoding="utf-8").read()]


def rep(store, old, new, all_occ=False):
    if old not in store[0]:
        miss.append(old.splitlines()[0][:70])
        return
    if all_occ:
        store[0] = store[0].replace(old, new)
    else:
        store[0] = store[0].replace(old, new, 1)


# ---------------------------------------------------------------- MeshBuilder
mb = load("/gl/MeshBuilder.java")
rep(mb, """    public void upload() {""",
    """    /**
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

    public void upload() {""")

# ---------------------------------------------------------------- WorldRenderer : Lohen
wr = load("/render/WorldRenderer.java")
rep(wr, """    /** Lohen : le gréement de 19 os donne les positions, on ne les reinvente pas. */
    private void drawRig(CharacterRig rig, int coat, int trousers, int skin,
                         float crouchScale) {
        float[] b = rig.bones();
        float k = crouchScale;
        int pelvis = CharacterRig.PELVIS * 3;
        int spine2 = CharacterRig.SPINE2 * 3;
        int neck = CharacterRig.NECK * 3;
        int head = CharacterRig.HEAD * 3;
        /* torse : deux segments epais */
        segment(b, pelvis, spine2, 0.155f, coat);
        segment(b, spine2, neck, 0.145f, coat);
        /* tete */
        dynamic.box(b[head], b[head + 1] * k, b[head + 2], 0.085f, 0.105f, 0.09f,
                0f, skin, 0f, 0f);
        /* bras et jambes */
        segment(b, CharacterRig.SHOULDER_L * 3, CharacterRig.UPPER_ARM_L * 3, 0.052f, coat);
        segment(b, CharacterRig.UPPER_ARM_L * 3, CharacterRig.FOREARM_L * 3, 0.046f, coat);
        segment(b, CharacterRig.FOREARM_L * 3, CharacterRig.HAND_L * 3, 0.040f, skin);
        segment(b, CharacterRig.SHOULDER_R * 3, CharacterRig.UPPER_ARM_R * 3, 0.052f, coat);
        segment(b, CharacterRig.UPPER_ARM_R * 3, CharacterRig.FOREARM_R * 3, 0.046f, coat);
        segment(b, CharacterRig.FOREARM_R * 3, CharacterRig.HAND_R * 3, 0.040f, skin);
        segment(b, CharacterRig.THIGH_L * 3, CharacterRig.SHIN_L * 3, 0.068f, trousers);
        segment(b, CharacterRig.SHIN_L * 3, CharacterRig.FOOT_L * 3, 0.055f, trousers);
        segment(b, CharacterRig.THIGH_R * 3, CharacterRig.SHIN_R * 3, 0.068f, trousers);
        segment(b, CharacterRig.SHIN_R * 3, CharacterRig.FOOT_R * 3, 0.055f, trousers);
        /* la sacoche du courrier : elle ne le quitte jamais (10.03) */
        dynamic.box(b[pelvis] - 0.16f, b[pelvis + 1] - 0.10f, b[pelvis + 2] + 0.06f,
                0.13f, 0.15f, 0.07f, 0f, Palette.LOHEN_SATCHEL, 0f, 0f);
    }""",
    """    /** Lohen : le gréement de 19 os donne les positions, on ne les reinvente pas. */
    private void drawRig(CharacterRig rig, int coat, int trousers, int skin,
                         float crouchScale) {
        float[] b = rig.bones();
        float k = crouchScale;
        float pelvisY = b[CharacterRig.PELVIS * 3 + 1];
        float[] p = new float[b.length];
        for (int bone = 0; bone < CharacterRig.BONE_COUNT; bone++) {
            p[bone * 3] = b[bone * 3];
            p[bone * 3 + 1] = pelvisY + (b[bone * 3 + 1] - pelvisY) * k;
            p[bone * 3 + 2] = b[bone * 3 + 2];
        }
        int coatDark = shade(coat, 0.8f);
        int boots = Palette.LOHEN_GLOVES;
        int hair = 0xFF3A2C20;
        int pel = CharacterRig.PELVIS * 3;
        /* jambes de pantalon, fuselees */
        capsuleB(p, CharacterRig.THIGH_L, CharacterRig.SHIN_L, 0.075f, 0.058f, trousers);
        capsuleB(p, CharacterRig.SHIN_L, CharacterRig.FOOT_L, 0.058f, 0.047f, trousers);
        capsuleB(p, CharacterRig.THIGH_R, CharacterRig.SHIN_R, 0.075f, 0.058f, trousers);
        capsuleB(p, CharacterRig.SHIN_R, CharacterRig.FOOT_R, 0.058f, 0.047f, trousers);
        sphereB(p, CharacterRig.FOOT_L, 0.055f, 0.045f, 0.10f, boots);
        sphereB(p, CharacterRig.FOOT_R, 0.055f, 0.045f, 0.10f, boots);
        /* pan du manteau : il s'evase du bassin a mi-cuisse */
        dynamic.capsule(p[pel], p[pel + 1], p[pel + 2],
                p[pel], p[pel + 1] - 0.34f * k, p[pel + 2],
                0.145f, 0.20f, coatDark, 0f, 8);
        /* torse : deux segments de manteau, epaules aux hanches */
        capsuleB(p, CharacterRig.PELVIS, CharacterRig.SPINE2, 0.150f, 0.135f, coat);
        capsuleB(p, CharacterRig.SPINE2, CharacterRig.NECK, 0.135f, 0.100f, coat);
        sphereB(p, CharacterRig.SHOULDER_L, 0.065f, 0.060f, 0.065f, coat);
        sphereB(p, CharacterRig.SHOULDER_R, 0.065f, 0.060f, 0.065f, coat);
        /* bras : manche de manteau puis main nue */
        capsuleB(p, CharacterRig.SHOULDER_L, CharacterRig.UPPER_ARM_L, 0.055f, 0.050f, coat);
        capsuleB(p, CharacterRig.UPPER_ARM_L, CharacterRig.FOREARM_L, 0.050f, 0.043f, coat);
        capsuleB(p, CharacterRig.FOREARM_L, CharacterRig.HAND_L, 0.042f, 0.036f, skin);
        capsuleB(p, CharacterRig.SHOULDER_R, CharacterRig.UPPER_ARM_R, 0.055f, 0.050f, coat);
        capsuleB(p, CharacterRig.UPPER_ARM_R, CharacterRig.FOREARM_R, 0.050f, 0.043f, coat);
        capsuleB(p, CharacterRig.FOREARM_R, CharacterRig.HAND_R, 0.042f, 0.036f, skin);
        sphereB(p, CharacterRig.HAND_L, 0.045f, 0.050f, 0.035f, skin);
        sphereB(p, CharacterRig.HAND_R, 0.045f, 0.050f, 0.035f, skin);
        /* cou, tete ronde, calotte de cheveux */
        capsuleB(p, CharacterRig.NECK, CharacterRig.HEAD, 0.050f, 0.045f, skin);
        int hd = CharacterRig.HEAD * 3;
        dynamic.ellipsoid(p[hd], p[hd + 1] + 0.05f, p[hd + 2],
                0.088f, 0.108f, 0.092f, skin, 0f, 8, 5, -1.5708f, 1.5708f);
        dynamic.ellipsoid(p[hd], p[hd + 1] + 0.065f, p[hd + 2],
                0.093f, 0.105f, 0.097f, hair, 0f, 8, 3, 0.28f, 1.5708f);
        /* la sacoche du courrier : elle ne le quitte jamais (10.03) */
        dynamic.box(p[pel] - 0.16f, p[pel + 1] - 0.10f, p[pel + 2] + 0.06f,
                0.13f, 0.15f, 0.07f, 0f, Palette.LOHEN_SATCHEL, 0f, 0f);
        int sp2 = CharacterRig.SPINE2 * 3;
        dynamic.beam(p[sp2] + 0.10f, p[sp2 + 1], p[sp2 + 2],
                p[pel] - 0.16f, p[pel + 1] - 0.02f, p[pel + 2] + 0.06f,
                0.018f, Palette.LOHEN_SATCHEL, 0f);
    }

    private void capsuleB(float[] p, int bone0, int bone1, float r0, float r1,
                          int color) {
        dynamic.capsule(p[bone0 * 3], p[bone0 * 3 + 1], p[bone0 * 3 + 2],
                p[bone1 * 3], p[bone1 * 3 + 1], p[bone1 * 3 + 2],
                r0, r1, color, 0f, 8);
    }

    private void sphereB(float[] p, int bone, float rx, float ry, float rz,
                         int color) {
        dynamic.ellipsoid(p[bone * 3], p[bone * 3 + 1], p[bone * 3 + 2],
                rx, ry, rz, color, 0f, 8, 5, -1.5708f, 1.5708f);
    }

    private static int shade(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int g = (int) (((argb >> 8) & 0xFF) * f);
        int bl = (int) ((argb & 0xFF) * f);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }""")

# ---- PNJ : torse capsule, tete ellipsoide, membres fuseles ----------------
rep(wr, """        /* jambes */
        limb(x, y, z, cs, sn, -0.09f, swing, hip, 0.055f, Palette.LOHEN_TROUSER);
        limb(x, y, z, cs, sn, 0.09f, -swing, hip, 0.055f, Palette.LOHEN_TROUSER);
        /* torse */
        dynamic.box(x, y + hip + torso * 0.5f, z, 0.17f, torso * 0.5f, 0.11f,
                (float) Math.toDegrees(yawR), cloth, 0f, 0f);
        /* bras */
        limb(x, y + hip + torso * 0.9f, z, cs, sn, -0.22f, -swing * 0.8f,
                height * 0.26f, 0.045f, cloth);
        limb(x, y + hip + torso * 0.9f, z, cs, sn, 0.22f, swing * 0.8f,
                height * 0.26f, 0.045f, cloth);
        /* tete */
        dynamic.box(x, y + hip + torso + head * 0.6f, z, 0.085f, head * 0.5f,
                0.09f, (float) Math.toDegrees(yawR), skin, 0f, 0f);
    }""",
    """        /* jambes */
        limb(x, y, z, cs, sn, -0.09f, swing, hip, 0.062f, 0.048f,
                Palette.LOHEN_TROUSER);
        limb(x, y, z, cs, sn, 0.09f, -swing, hip, 0.062f, 0.048f,
                Palette.LOHEN_TROUSER);
        /* torse : capsule, plus de boite */
        dynamic.capsule(x, y + hip, z, x, y + hip + torso, z, 0.155f, 0.115f,
                cloth, 0f, 7);
        /* bras */
        limb(x, y + hip + torso * 0.9f, z, cs, sn, -0.22f, -swing * 0.8f,
                height * 0.26f, 0.050f, 0.038f, cloth);
        limb(x, y + hip + torso * 0.9f, z, cs, sn, 0.22f, swing * 0.8f,
                height * 0.26f, 0.050f, 0.038f, cloth);
        /* tete : ellipsoide */
        dynamic.ellipsoid(x, y + hip + torso + head * 0.6f, z, 0.085f,
                head * 0.55f, 0.09f, skin, 0f, 8, 5, -1.5708f, 1.5708f);
    }""")
rep(wr, """    private void limb(float x, float y, float z, float cs, float sn, float side,
                      float swing, float len, float thick, int color) {
        float ox = x + side * cs;
        float oz = z - side * sn;
        float ex = ox + swing * cs * len * 0.6f;
        float ez = oz - swing * sn * len * 0.6f;
        float ey = y - len * (1f - Math.abs(swing) * 0.18f);
        dynamic.beam(ox, y, oz, ex, Math.max(ey, y - len), ez, thick, color, 0f);
    }""",
    """    private void limb(float x, float y, float z, float cs, float sn, float side,
                      float swing, float len, float thick, float thick2,
                      int color) {
        float ox = x + side * cs;
        float oz = z - side * sn;
        float ex = ox + swing * cs * len * 0.6f;
        float ez = oz - swing * sn * len * 0.6f;
        float ey = y - len * (1f - Math.abs(swing) * 0.18f);
        dynamic.capsule(ox, y, oz, ex, Math.max(ey, y - len), ez, thick, thick2,
                color, 0f, 6);
    }""")

# ---- l'Echassier : jambes de capsule, plus de prismes ----------------------
rep(wr, """        dynamic.beam(x - 0.34f, y, z, x - 0.16f + lean, y + 1.85f, z, 0.055f, cyan, 0.35f);
        dynamic.beam(x + 0.34f, y, z, x + 0.16f + lean, y + 1.85f, z, 0.055f, cyan, 0.35f);""",
    """        dynamic.capsule(x - 0.34f, y, z, x - 0.16f + lean, y + 1.85f, z,
                0.035f, 0.075f, cyan, 0.35f, 7);
        dynamic.capsule(x + 0.34f, y, z, x + 0.16f + lean, y + 1.85f, z,
                0.035f, 0.075f, cyan, 0.35f, 7);""")
rep(wr, """        dynamic.beam(x - 0.18f, y + 0.30f, z, x - 0.26f, y, z, 0.045f, cyan, 0.25f);
        dynamic.beam(x + 0.18f, y + 0.30f, z, x + 0.26f, y, z, 0.045f, cyan, 0.25f);""",
    """        dynamic.capsule(x - 0.18f, y + 0.30f, z, x - 0.26f, y, z, 0.03f, 0.05f,
                cyan, 0.25f, 6);
        dynamic.capsule(x + 0.18f, y + 0.30f, z, x + 0.26f, y, z, 0.03f, 0.05f,
                cyan, 0.25f, 6);""")

# ---------------------------------------------------------------- camera tactile
tir = load("/sim/input/TouchInputRouter.java")
rep(tir, """    public static final float CAMERA_SENSITIVITY_DP = 0.22f;  /* deg/dp (08.23) */""",
    """    /* 08.23 revise : 0,13 deg/dp LINEAIRE. La courbe x^1,25 appliquée a un
     * delta en degres clampait chaque evenement a +/-1 deg et rendait la
     * camera saccadee et trop sensible (retour joueur). */
    public static final float CAMERA_SENSITIVITY_DP = 0.13f;""")
rep(tir, """        if (pointerId == camPointer) {
            float dx = x - camLastX;
            float dy = y - camLastY;
            float sens = CAMERA_SENSITIVITY_DP * dpScale;
            float sx = options == null ? 1f : options.sensitivityX;
            float sy = options == null ? 1f : options.sensitivityY;
            boolean ix = options != null && options.invertX;
            boolean iy = options != null && options.invertY;
            camDX += (ix ? 1f : -1f) * Maths.responseCurve(dx / sens, CAMERA_CURVE) * sx;
            camDY += (iy ? -1f : 1f) * Maths.responseCurve(dy / sens, CAMERA_CURVE) * sy;
            camLastX = x;
            camLastY = y;
        }""",
    """        if (pointerId == camPointer) {
            float dx = x - camLastX;
            float dy = y - camLastY;
            float sx = options == null ? 1f : options.sensitivityX;
            float sy = options == null ? 1f : options.sensitivityY;
            boolean ix = options != null && options.invertX;
            boolean iy = options != null && options.invertY;
            /* lineaire, en degres par dp ; un garde-fou a +/-3 deg par
             * evenement absorbe les sauts du tactile sans tout quantifier */
            float degX = (dx / dpScale) * CAMERA_SENSITIVITY_DP;
            float degY = (dy / dpScale) * CAMERA_SENSITIVITY_DP;
            camDX += (ix ? 1f : -1f) * Maths.clamp(degX * sx, -3f, 3f);
            camDY += (iy ? -1f : 1f) * Maths.clamp(degY * sy, -3f, 3f);
            camLastX = x;
            camLastY = y;
        }""")

cr = load("/sim/player/CameraRig.java")
rep(cr, """    public static final float SMOOTH_POS = 0.12f;""",
    """    public static final float SMOOTH_POS = 0.13f;""")
rep(cr, """    public static final float SMOOTH_ROT = 0.08f;""",
    """    public static final float SMOOTH_ROT = 0.11f;   /* lissee : retour joueur */""")

gp = load("/sim/input/GamepadRouter.java")
rep(gp, """    private float sensitivity = 2.2f;""",
    """    private float sensitivity = 1.4f;   /*adoucie : retour joueur */""")

for p, store in (("/gl/MeshBuilder.java", mb), ("/render/WorldRenderer.java", wr),
                 ("/sim/input/TouchInputRouter.java", tir),
                 ("/sim/player/CameraRig.java", cr),
                 ("/sim/input/GamepadRouter.java", gp)):
    open(ROOT + p, "w", encoding="utf-8").write(store[0])

if miss:
    print("MANQUES:")
    for m in miss:
        print("  - " + m)
    sys.exit(1)
print("patch personnage + camera ok")
