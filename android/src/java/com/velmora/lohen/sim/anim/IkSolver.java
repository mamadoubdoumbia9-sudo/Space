/*
 * LOHEN — sim/anim/IkSolver.java
 *
 * IK analytique a deux os (jambes pour le contact au sol, bras pour les
 * prises et les appuis contextuels — 07.06, 07.07, 07.09).
 *
 * Pas de solveur iteratif : un plan, une loi des cosinus, un pole vector.
 * Deterministe, sans allocation, < 0,02 ms pour les 4 membres.
 */
package com.velmora.lohen.sim.anim;

import com.velmora.lohen.sim.math.Maths;

public final class IkSolver {

    /**
     * Resout une chaine a deux os.
     *
     * @param root      origine de la chaine (epaule ou hanche), 3 floats
     * @param target    position visee (main ou pied), 3 floats
     * @param upper     longueur du premier os
     * @param lower     longueur du second os
     * @param pole      direction du coude / du genou (indice de pliage)
     * @param outJoint  position calculee du coude / genou, 3 floats
     * @return poids reellement applique (0..1) — la cible est ramenee dans
     *         la portee de la chaine si elle est trop loin (pas d'etirement)
     */
    public static float solveTwoBone(float[] root, float[] target, float upper, float lower,
                                     float[] pole, float[] outJoint) {
        float dx = target[0] - root[0];
        float dy = target[1] - root[1];
        float dz = target[2] - root[2];
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float maxReach = upper + lower;
        float weight = 1f;
        if (dist > maxReach) {
            weight = maxReach / dist;
            dx *= weight;
            dy *= weight;
            dz *= weight;
            dist = maxReach;
        }
        if (dist < 1e-4f) {
            outJoint[0] = root[0];
            outJoint[1] = root[1];
            outJoint[2] = root[2];
            return 0f;
        }
        /* loi des cosinus : angle au niveau de la racine */
        float cosA = (upper * upper + dist * dist - lower * lower) / (2f * upper * dist);
        cosA = Maths.clamp(cosA, -1f, 1f);
        float angle = (float) Math.acos(cosA);
        /* axe de rotation : perpendiculaire a la direction et au pole */
        float invDist = 1f / dist;
        float dirX = dx * invDist;
        float dirY = dy * invDist;
        float dirZ = dz * invDist;
        /* pole projete */
        float dot = pole[0] * dirX + pole[1] * dirY + pole[2] * dirZ;
        float px = pole[0] - dirX * dot;
        float py = pole[1] - dirY * dot;
        float pz = pole[2] - dirZ * dot;
        float pl = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (pl < 1e-5f) {
            /* pole degeneré : on choisit un axe arbitraire stable */
            px = 1f - dirX * dirX;
            py = -dirX * dirY;
            pz = -dirX * dirZ;
            pl = (float) Math.sqrt(px * px + py * py + pz * pz);
            if (pl < 1e-5f) {
                px = 0f;
                py = 1f - dirY * dirY;
                pz = -dirY * dirZ;
                pl = (float) Math.sqrt(px * px + py * py + pz * pz);
            }
        }
        px /= pl;
        py /= pl;
        pz /= pl;
        /* rotation de dir autour de pole de `angle` */
        float ca = (float) Math.cos(angle);
        float sa = (float) Math.sin(angle);
        /* Rodrigues */
        float rx = dirX * ca + (py * dirZ - pz * dirY) * sa + px * (px * dirX + py * dirY + pz * dirZ) * (1f - ca);
        float ry = dirY * ca + (pz * dirX - px * dirZ) * sa + py * (px * dirX + py * dirY + pz * dirZ) * (1f - ca);
        float rz = dirZ * ca + (px * dirY - py * dirX) * sa + pz * (px * dirX + py * dirY + pz * dirZ) * (1f - ca);
        outJoint[0] = root[0] + rx * upper;
        outJoint[1] = root[1] + ry * upper;
        outJoint[2] = root[2] + rz * upper;
        return weight;
    }

    /**
     * Orientation d'un os (quaternion) qui regarde depuis `from` vers `to`,
     * avec un up de reference. Ecrit (x,y,z,w) dans out4.
     */
    public static void lookQuaternion(float[] from, float[] to, float upX, float upY, float upZ,
                                      float[] out4) {
        float dx = to[0] - from[0];
        float dy = to[1] - from[1];
        float dz = to[2] - from[2];
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-5f) {
            out4[0] = 0f;
            out4[1] = 0f;
            out4[2] = 0f;
            out4[3] = 1f;
            return;
        }
        dx /= l;
        dy /= l;
        dz /= l;
        /* z = direction, x = up x z, y = z x x */
        float xx = upY * dz - upZ * dy;
        float xy = upZ * dx - upX * dz;
        float xz = upX * dy - upY * dx;
        float xl = (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
        if (xl < 1e-5f) {
            xx = 1f;
            xy = 0f;
            xz = 0f;
            xl = 1f;
        }
        xx /= xl;
        xy /= xl;
        xz /= xl;
        float yx = dy * xz - dz * xy;
        float yy = dz * xx - dx * xz;
        float yz = dx * xy - dy * xx;
        matrixToQuat(xx, xy, xz, yx, yy, yz, dx, dy, dz, out4);
    }

    private static void matrixToQuat(float m00, float m01, float m02,
                                     float m10, float m11, float m12,
                                     float m20, float m21, float m22, float[] out4) {
        float trace = m00 + m11 + m22;
        float qx, qy, qz, qw;
        if (trace > 0f) {
            float s = (float) Math.sqrt(trace + 1f) * 2f;
            qw = 0.25f * s;
            qx = (m21 - m12) / s;
            qy = (m02 - m20) / s;
            qz = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            float s = (float) Math.sqrt(1f + m00 - m11 - m22) * 2f;
            qw = (m21 - m12) / s;
            qx = 0.25f * s;
            qy = (m01 + m10) / s;
            qz = (m02 + m20) / s;
        } else if (m11 > m22) {
            float s = (float) Math.sqrt(1f + m11 - m00 - m22) * 2f;
            qw = (m02 - m20) / s;
            qx = (m01 + m10) / s;
            qy = 0.25f * s;
            qz = (m12 + m21) / s;
        } else {
            float s = (float) Math.sqrt(1f + m22 - m00 - m11) * 2f;
            qw = (m10 - m01) / s;
            qx = (m02 + m20) / s;
            qy = (m12 + m21) / s;
            qz = 0.25f * s;
        }
        float l = (float) Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        if (l < 1e-6f) {
            out4[0] = out4[1] = out4[2] = 0f;
            out4[3] = 1f;
            return;
        }
        out4[0] = qx / l;
        out4[1] = qy / l;
        out4[2] = qz / l;
        out4[3] = qw / l;
    }

    /** Angle de pliage du genou / coude, pour l'animation procedurale. */
    public static float bendAngle(float upper, float lower, float dist) {
        float cosA = (upper * upper + lower * lower - dist * dist) / (2f * upper * lower);
        return (float) Math.toDegrees((float) Math.acos(Maths.clamp(cosA, -1f, 1f)));
    }
}
