/*
 * LOHEN — sim/world/PhysicsWorld.java
 *
 * Physique deterministe capsule/boites orientees (Y). Le monde est entierement
 * data-driven : aucune geometrie binaire, tout vient de content/levels/*.json.
 *
 * - hachage spatial XZ (cellules 4 m) pour rester < 0,9 ms de budget (18.04)
 * - requetes : sol, mur, prise (LedgeVolume, 08.07), ancre (08.08),
 *   surface de verre (08.08c), puits de chaleur (09.11), planche fragile (06.26)
 * - aucune allocation dans les boucles chaudes (04.12)
 */
package com.velmora.lohen.sim.world;

import com.velmora.lohen.sim.core.LevelData;
import com.velmora.lohen.sim.math.Geom;
import com.velmora.lohen.sim.math.Maths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PhysicsWorld {

    public static final float CELL = 4f;
    public static final int MAX_HITS = 24;

    private LevelData data;

    /* hachage spatial */
    private int gridW = 1, gridH = 1, gridD = 1;
    private float originX, originY, originZ;
    private int[][] cells = new int[1][0];

    /* resultats de requete pre-alloues */
    private final int[] hitIds = new int[MAX_HITS];
    private final float[] hitValues = new float[MAX_HITS];
    private int hitCount;

    /* etat dynamique : planches cedees, raccourcis ouverts (06.26, 09.23) */
    private final List<Integer> disabledSolids = new ArrayList<Integer>(32);
    private boolean[] solidDisabled = new boolean[0];

    public PhysicsWorld() { }

    public void build(LevelData data) {
        this.data = data;
        int n = data == null ? 0 : data.solidCount;
        solidDisabled = new boolean[n];
        disabledSolids.clear();
        if (n == 0) {
            gridW = gridH = gridD = 1;
            cells = new int[1][0];
            return;
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float hx = data.solidHX(i), hy = data.solidHY(i), hz = data.solidHZ(i);
            /* le lacet etant limite a Y, l'AABB englobant est majore par max(hx,hz) */
            float r = (float) Math.sqrt(hx * hx + hz * hz);
            minX = Math.min(minX, data.solidX(i) - r);
            maxX = Math.max(maxX, data.solidX(i) + r);
            minY = Math.min(minY, data.solidY(i) - hy);
            maxY = Math.max(maxY, data.solidY(i) + hy);
            minZ = Math.min(minZ, data.solidZ(i) - r);
            maxZ = Math.max(maxZ, data.solidZ(i) + r);
        }
        originX = minX - CELL;
        originY = minY - CELL;
        originZ = minZ - CELL;
        gridW = Math.max(1, (int) Math.ceil((maxX - minX + 2 * CELL) / CELL));
        gridH = Math.max(1, (int) Math.ceil((maxY - minY + 2 * CELL) / CELL));
        gridD = Math.max(1, (int) Math.ceil((maxZ - minZ + 2 * CELL) / CELL));
        /* limite memoire : si la grille est enorme, on aplatie Y */
        if ((long) gridW * gridH * gridD > 4_000_000L) {
            gridH = Math.max(1, (int) Math.ceil((maxY - minY + 2 * CELL) / 32f));
            originY = minY - 16f;
        }
        int total = gridW * gridH * gridD;
        cells = new int[total][];
        int[] counts = new int[total];
        for (int i = 0; i < n; i++) {
            forEachCell(i, counts, true);
        }
        for (int c = 0; c < total; c++) {
            cells[c] = counts[c] == 0 ? EMPTY : new int[counts[c]];
        }
        int[] fill = new int[total];
        for (int i = 0; i < n; i++) {
            forEachCell(i, fill, false);
        }
    }

    private static final int[] EMPTY = new int[0];

    private void forEachCell(int solidIndex, int[] counter, boolean countOnly) {
        float hx = data.solidHX(solidIndex), hy = data.solidHY(solidIndex), hz = data.solidHZ(solidIndex);
        float r = (float) Math.sqrt(hx * hx + hz * hz);
        float cx = data.solidX(solidIndex), cy = data.solidY(solidIndex), cz = data.solidZ(solidIndex);
        int x0 = cellCoord(cx - r - originX, gridW);
        int x1 = cellCoord(cx + r - originX, gridW);
        int y0 = cellCoordY(cy - hy - originY, gridH);
        int y1 = cellCoordY(cy + hy - originY, gridH);
        int z0 = cellCoord(cz - r - originZ, gridD);
        int z1 = cellCoord(cz + r - originZ, gridD);
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    int idx = (y * gridD + z) * gridW + x;
                    if (idx < 0 || idx >= cells.length) {
                        continue;
                    }
                    if (countOnly) {
                        counter[idx]++;
                    } else {
                        int k = counter[idx]++;
                        if (k < cells[idx].length) {
                            cells[idx][k] = solidIndex;
                        }
                    }
                }
            }
        }
    }

    private static int cellCoord(float v, int max) {
        int c = (int) Math.floor(v / CELL);
        return c < 0 ? 0 : (c >= max ? max - 1 : c);
    }

    private static int cellCoordY(float v, int max) {
        float h = max > 1 ? CELL : 32f;
        int c = (int) Math.floor(v / h);
        return c < 0 ? 0 : (c >= max ? max - 1 : c);
    }

    /* ------------------------------------------------------------------ */
    /* Etat dynamique                                                      */
    /* ------------------------------------------------------------------ */

    /** 06.26 : destruction scriptee et locale (planche, balcon, escalier). */
    public void disableSolid(int index) {
        if (index < 0 || index >= solidDisabled.length || solidDisabled[index]) {
            return;
        }
        solidDisabled[index] = true;
        disabledSolids.add(index);
    }

    public boolean isDisabled(int index) {
        return index >= 0 && index < solidDisabled.length && solidDisabled[index];
    }

    public List<Integer> disabledSolids() {
        return disabledSolids;
    }

    public void restoreAll() {
        Arrays.fill(solidDisabled, false);
        disabledSolids.clear();
    }

    /* ------------------------------------------------------------------ */
    /* Requetes                                                            */
    /* ------------------------------------------------------------------ */

    private int collect(float x, float y, float z, float radius, float yLo, float yHi) {
        hitCount = 0;
        if (data == null || data.solidCount == 0) {
            return 0;
        }
        int x0 = cellCoord(x - radius - originX, gridW);
        int x1 = cellCoord(x + radius - originX, gridW);
        int y0 = cellCoordY(yLo - originY, gridH);
        int y1 = cellCoordY(yHi - originY, gridH);
        int z0 = cellCoord(z - radius - originZ, gridD);
        int z1 = cellCoord(z + radius - originZ, gridD);
        for (int gy = y0; gy <= y1; gy++) {
            for (int gz = z0; gz <= z1; gz++) {
                for (int gx = x0; gx <= x1; gx++) {
                    int idx = (gy * gridD + gz) * gridW + gx;
                    if (idx < 0 || idx >= cells.length) {
                        continue;
                    }
                    int[] list = cells[idx];
                    for (int k = 0; k < list.length; k++) {
                        int s = list[k];
                        if (solidDisabled[s]) {
                            continue;
                        }
                        for (int d = 0; d < hitCount; d++) {
                            if (hitIds[d] == s) {
                                s = -1;
                                break;
                            }
                        }
                        if (s < 0) {
                            continue;
                        }
                        if (hitCount >= MAX_HITS) {
                            return hitCount;
                        }
                        if (!data.overlapsY(s, yLo, yHi)) {
                            continue;
                        }
                        if (!data.containsXZ(s, x, z, radius)) {
                            continue;
                        }
                        hitIds[hitCount] = s;
                        hitValues[hitCount] = 0f;
                        hitCount++;
                    }
                }
            }
        }
        return hitCount;
    }

    /**
     * Hauteur du sol sous (x,z) pour des pieds a footY.
     * Renvoie NaN si aucun sol. `outMaterial`/`outFlags` renseignent la surface
     * (matiere des pas — 13.28, langage visuel — 09.21).
     */
    public float groundHeight(float x, float z, float footY, float radius, int[] outInfo) {
        float best = Float.NaN;
        int bestId = -1;
        collect(x, footY + 0.05f, z, radius, footY - 1.2f, footY + 0.6f);
        for (int i = 0; i < hitCount; i++) {
            int s = hitIds[i];
            if (!data.hasFlag(s, Geom.FLAG_WALKABLE)) {
                continue;
            }
            float top = data.solidY(s) + data.solidHY(s);
            if (top > footY + 0.45f) {
                continue;   /* trop haut : c'est un mur, pas un sol */
            }
            if (Float.isNaN(best) || top > best) {
                best = top;
                bestId = s;
            }
        }
        /* escaliers : rampe continue (le kit K3, le plus utilise du jeu) */
        for (int i = 0; i < data.stairCount; i++) {
            float sh = stairHeight(i, x, z, footY);
            if (!Float.isNaN(sh) && (Float.isNaN(best) || sh > best)) {
                best = sh;
                bestId = -2 - i;
            }
        }
        if (outInfo != null) {
            if (bestId >= 0) {
                outInfo[0] = data.solidMaterial(bestId);
                outInfo[1] = data.solidFlags(bestId);
                outInfo[2] = bestId;
            } else if (bestId <= -2) {
                int si = -2 - bestId;
                outInfo[0] = data.stairMaterial(si);
                outInfo[1] = Geom.FLAG_WALKABLE;
                outInfo[2] = -2 - si;
            } else {
                outInfo[0] = -1;
                outInfo[1] = 0;
                outInfo[2] = -1;
            }
        }
        return best;
    }

    /** Hauteur de la rampe d'un escalier au point (x,z). */
    public float stairHeight(int i, float x, float z, float footY) {
        float sx = data.stairData[i * 8];
        float sy = data.stairData[i * 8 + 1];
        float sz = data.stairData[i * 8 + 2];
        float w = data.stairData[i * 8 + 3];
        float h = data.stairData[i * 8 + 4];
        float d = data.stairData[i * 8 + 5];
        float yaw = (float) Math.toRadians(data.stairData[i * 8 + 6]);
        float dx = x - sx;
        float dz = z - sz;
        float c = (float) Math.cos(-yaw);
        float s = (float) Math.sin(-yaw);
        float lx = dx * c - dz * s;
        float lz = dx * s + dz * c;
        if (Math.abs(lx) > w * 0.5f || Math.abs(lz) > d * 0.5f) {
            return Float.NaN;
        }
        float t = Maths.clamp01((lz + d * 0.5f) / d);
        float top = sy + h * t;
        if (top > footY + 0.6f || top < footY - 2.5f) {
            return Float.NaN;
        }
        return top;
    }

    /** Repousse la capsule hors des murs ; renvoie la normale de contact. */
    public boolean resolveWalls(float[] pos, float radius, float height, float[] outNormal) {
        boolean contact = false;
        float feet = pos[1];
        collect(pos[0], feet + height * 0.5f, pos[2], radius + 0.05f, feet + 0.12f, feet + height);
        float nx = 0f, nz = 0f;
        for (int i = 0; i < hitCount; i++) {
            int s = hitIds[i];
            float top = data.solidY(s) + data.solidHY(s);
            if (top <= feet + 0.18f) {
                continue;   /* marche basse : on monte dessus, pas de mur */
            }
            float bottom = data.solidY(s) - data.solidHY(s);
            if (bottom >= feet + height) {
                continue;   /* au-dessus de la tete */
            }
            float before0 = pos[0], before2 = pos[2];
            float depth = data.pushOut(s, pos, radius);
            if (depth > 0f) {
                contact = true;
                float ddx = pos[0] - before0;
                float ddz = pos[2] - before2;
                float l = (float) Math.sqrt(ddx * ddx + ddz * ddz);
                if (l > 1e-5f) {
                    nx += ddx / l;
                    nz += ddz / l;
                }
            }
        }
        if (outNormal != null) {
            float l = (float) Math.sqrt(nx * nx + nz * nz);
            if (l > 1e-5f) {
                outNormal[0] = nx / l;
                outNormal[1] = 0f;
                outNormal[2] = nz / l;
            } else {
                outNormal[0] = 0f;
                outNormal[1] = 0f;
                outNormal[2] = 0f;
            }
        }
        return contact;
    }

    /** Mur devant le joueur (pour wallrun 07.08 et IK de main 07.06). */
    public float wallDistance(float x, float y, float z, float dirX, float dirZ,
                              float maxDist, float[] outNormal) {
        float best = maxDist;
        boolean found = false;
        float step = 0.15f;
        for (float t = 0.2f; t <= maxDist; t += step) {
            float px = x + dirX * t;
            float pz = z + dirZ * t;
            collect(px, y + 0.9f, pz, 0.05f, y + 0.2f, y + 1.6f);
            for (int i = 0; i < hitCount; i++) {
                int s = hitIds[i];
                float top = data.solidY(s) + data.solidHY(s);
                float bottom = data.solidY(s) - data.solidHY(s);
                if (top < y + 0.5f || bottom > y + 1.9f) {
                    continue;
                }
                best = t;
                found = true;
                if (outNormal != null) {
                    float cx = data.solidX(s), cz = data.solidZ(s);
                    float nx = px - cx, nz = pz - cz;
                    float l = (float) Math.sqrt(nx * nx + nz * nz);
                    if (l > 1e-5f) {
                        outNormal[0] = nx / l;
                        outNormal[1] = 0f;
                        outNormal[2] = nz / l;
                    }
                }
                break;
            }
            if (found) {
                break;
            }
        }
        return found ? best : Float.NaN;
    }

    /**
     * Prise grimpable (LedgeVolume, 08.07) : renvoie l'index de la prise
     * atteignable depuis (x,y,z) dans la direction (dx,dz), ou -1.
     * Fenetre genéreuse : 0,35 s et capsule elargie de 20 cm (08.05).
     */
    public int findLedge(float x, float y, float z, float dx, float dz,
                         float reachMin, float reachMax, float widen, float[] outPos) {
        float best = -1f;
        int bestIdx = -1;
        for (int i = 0; i < data.ledgeCount; i++) {
            float lx = data.ledgeData[i * 6];
            float ly = data.ledgeData[i * 6 + 1];
            float lz = data.ledgeData[i * 6 + 2];
            float len = data.ledgeData[i * 6 + 3];
            float yaw = (float) Math.toRadians(data.ledgeData[i * 6 + 4]);
            /* distance au segment de prise */
            float hx = (float) Math.cos(yaw) * len * 0.5f;
            float hz = (float) Math.sin(yaw) * len * 0.5f;
            float px = x - lx, pz = z - lz;
            float t = Maths.clamp((px * hx + pz * hz) / Math.max(1e-5f, hx * hx + hz * hz), -1f, 1f);
            float cx = lx + hx * t;
            float cz = lz + hz * t;
            float distH = (float) Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
            float distV = ly - y;
            if (distH > 1.6f + widen) {
                continue;
            }
            if (distV < reachMin - widen || distV > reachMax + widen) {
                continue;
            }
            /* la prise doit etre devant le joueur */
            if (dx * (cx - x) + dz * (cz - z) < -0.2f) {
                continue;
            }
            float score = distV * 0.6f + distH;
            if (bestIdx < 0 || score < best) {
                best = score;
                bestIdx = i;
                if (outPos != null) {
                    outPos[0] = cx;
                    outPos[1] = ly;
                    outPos[2] = cz;
                    outPos[3] = yaw;
                }
            }
        }
        return bestIdx;
    }

    public float ledgeX(int i) {
        return data.ledgeData[i * 6];
    }

    public float ledgeY(int i) {
        return data.ledgeData[i * 6 + 1];
    }

    public float ledgeZ(int i) {
        return data.ledgeData[i * 6 + 2];
    }

    public float ledgeLength(int i) {
        return data.ledgeData[i * 6 + 3];
    }

    public float ledgeYaw(int i) {
        return (float) Math.toRadians(data.ledgeData[i * 6 + 4]);
    }

    public float ledgeKind(int i) {
        return data.ledgeData[i * 6 + 5];
    }

    /**
     * Meilleure ancre de grappin dans un cone de 40 deg (08.03) :
     * score = alignement camera 0,5 + distance 0,3 + avantage de progression 0,2.
     */
    public int findAnchor(float x, float y, float z, float aimX, float aimY, float aimZ,
                          float maxRange, float progressY, float[] outPos) {
        float bestScore = -1f;
        int bestIdx = -1;
        for (int i = 0; i < data.anchorCount; i++) {
            float ax = data.anchorData[i * 4];
            float ay = data.anchorData[i * 4 + 1];
            float az = data.anchorData[i * 4 + 2];
            float dx = ax - x, dy = ay - y, dz = az - z;
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > maxRange || dist < 0.6f) {
                continue;
            }
            float inv = 1f / Math.max(1e-5f, dist);
            float align = (dx * inv * aimX + dy * inv * aimY + dz * inv * aimZ);
            float angle = (float) Math.toDegrees(Math.acos(Maths.clamp(align, -1f, 1f)));
            if (angle > 20f) {   /* demi-cone de 40 deg */
                continue;
            }
            float sAlign = 1f - angle / 20f;
            float sDist = 1f - Maths.clamp01(dist / maxRange);
            float sProg = Maths.clamp01((ay - progressY) / 12f + 0.5f);
            float score = sAlign * 0.5f + sDist * 0.3f + sProg * 0.2f;
            if (score > bestScore) {
                bestScore = score;
                bestIdx = i;
                if (outPos != null) {
                    outPos[0] = ax;
                    outPos[1] = ay;
                    outPos[2] = az;
                    outPos[3] = data.anchorData[i * 4 + 3];
                }
            }
        }
        return bestIdx;
    }

    public float anchorX(int i) {
        return data.anchorData[i * 4];
    }

    public float anchorY(int i) {
        return data.anchorData[i * 4 + 1];
    }

    public float anchorZ(int i) {
        return data.anchorData[i * 4 + 2];
    }

    public float anchorType(int i) {
        return data.anchorData[i * 4 + 3];
    }

    public int anchorCount() {
        return data == null ? 0 : data.anchorCount;
    }

    public int ledgeCount() {
        return data == null ? 0 : data.ledgeCount;
    }

    /** Rayon : renvoie la distance ou NaN (grappin, regard, IK, audit). */
    public float raycast(float ox, float oy, float oz, float dx, float dy, float dz,
                         float maxDist, int[] outInfo) {
        Geom.Ray r = TMP_RAY.set(ox, oy, oz, dx, dy, dz);
        float best = Float.NaN;
        int bestId = -1;
        /* parcours par pas le long du rayon, puis test exact sur les boites touchees */
        float step = 0.5f;
        for (float t = 0f; t <= maxDist; t += step) {
            float px = ox + dx * t;
            float py = oy + dy * t;
            float pz = oz + dz * t;
            collect(px, py, pz, 0.35f, py - 0.35f, py + 0.35f);
            for (int i = 0; i < hitCount; i++) {
                int s = hitIds[i];
                Geom.Box b = TMP_BOX;
                fillBox(s, b);
                float hit = Geom.rayBox(r, b, maxDist);
                if (hit >= 0f && (Float.isNaN(best) || hit < best)) {
                    best = hit;
                    bestId = s;
                }
            }
            if (!Float.isNaN(best)) {
                break;
            }
        }
        if (outInfo != null) {
            outInfo[0] = bestId;
            outInfo[1] = bestId >= 0 ? data.solidMaterial(bestId) : -1;
            outInfo[2] = bestId >= 0 ? data.solidFlags(bestId) : 0;
        }
        return best;
    }

    private final Geom.Box TMP_BOX = new Geom.Box();
    private final Geom.Ray TMP_RAY = new Geom.Ray();

    public Geom.Box fillBox(int index, Geom.Box b) {
        b.cx = data.solidX(index);
        b.cy = data.solidY(index);
        b.cz = data.solidZ(index);
        b.hx = data.solidHX(index);
        b.hy = data.solidHY(index);
        b.hz = data.solidHZ(index);
        b.yaw = (float) Math.toRadians(data.solidYaw(index));
        b.flags = data.solidFlags(index);
        b.material = data.solidMaterial(index);
        b.id = index;
        return b;
    }

    public LevelData data() {
        return data;
    }

    /** Surface de verre : la Maree est a y = 0 (09.11). */
    public boolean isOnGlassSea(float x, float y, float z) {
        if (data == null || data.sky == null || !data.sky.glassSeaVisible) {
            return false;
        }
        collect(x, 0.1f, z, 0.4f, -0.6f, 0.6f);
        for (int i = 0; i < hitCount; i++) {
            if (data.hasFlag(hitIds[i], Geom.FLAG_GLASS)) {
                return true;
            }
        }
        return y < 0.35f && data.seq.equals("S2");
    }

    /** Puits de chaleur (S2) : le verre ramollit, on s'y enfonce en 2,4 s. */
    public boolean isHeatWell(float x, float y, float z) {
        if (data == null) {
            return false;
        }
        collect(x, y, z, 0.5f, y - 1f, y + 1f);
        for (int i = 0; i < hitCount; i++) {
            if (data.hasFlag(hitIds[i], Geom.FLAG_HEAT_WELL)) {
                return true;
            }
        }
        return false;
    }

    /** Surface fragile qui cede sous le poids (06.26, 09.21). */
    public int fragileAt(float x, float y, float z) {
        if (data == null) {
            return -1;
        }
        collect(x, y, z, 0.5f, y - 0.8f, y + 0.4f);
        for (int i = 0; i < hitCount; i++) {
            int s = hitIds[i];
            if (data.hasFlag(s, Geom.FLAG_FRAGILE)) {
                float top = data.solidY(s) + data.solidHY(s);
                if (Math.abs(top - y) < 0.5f) {
                    return s;
                }
            }
        }
        return -1;
    }

    public int solidCount() {
        return data == null ? 0 : data.solidCount;
    }

    public int gridCells() {
        return cells.length;
    }

    /** Nombre moyen de solides par cellule non vide — audit de densite. */
    public float averageSolidsPerCell() {
        int nonEmpty = 0;
        int total = 0;
        for (int[] c : cells) {
            if (c.length > 0) {
                nonEmpty++;
                total += c.length;
            }
        }
        return nonEmpty == 0 ? 0f : total / (float) nonEmpty;
    }
}
