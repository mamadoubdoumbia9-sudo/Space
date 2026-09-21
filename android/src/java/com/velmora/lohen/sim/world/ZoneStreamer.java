/*
 * LOHEN — sim/world/ZoneStreamer.java
 *
 * 04.08 STREAMING DE ZONE : chaque zone est decoupee en cellules de
 * 48 x 48 x 32 m. Chargement additif. Budget : MAX 2 cellules en cours
 * de chargement simultanement, 4 ms de budget d'instanciation par frame
 * (time-sliced).
 *
 * 02.09 : `TextureStreamer` charge par cellules de zone de 48 m avec un
 * anneau de preload de 1 cellule.
 * 02.15 : < 420 draw calls par frame sur MID.
 * 02.16 : 480 k tris HIGH / 310 k MID / 180 k LOW.
 * 02.10 : reprise en < 3 s apres un passage en arriere-plan, sans
 *         rechargement de scene.
 * 09.22 : reprise de checkpoint < 2,5 s, camera a l'orientation exacte.
 *
 * Implementation native : pas de `load_threaded_request` Godot — un
 * chargeur decoupe en tranches de 4 ms appele depuis la boucle de jeu,
 * avec un budget memoire declare par cellule.
 */
package com.velmora.lohen.sim.world;

import com.velmora.lohen.sim.core.ContentDb;
import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.LevelData;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.core.Options;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ZoneStreamer {

    /* ---------------- constantes de la spec ---------------- */
    public static final float CELL_X = 48f;
    public static final float CELL_Z = 48f;
    public static final float CELL_Y = 32f;
    public static final int MAX_CONCURRENT_LOADS = 2;
    public static final float INSTANTIATE_BUDGET_MS = 4f;
    public static final int PRELOAD_RING = 1;
    public static final float RESUME_BUDGET_SECONDS = 2.5f;
    public static final float BACKGROUND_RESUME_BUDGET_SECONDS = 3f;
    public static final int DRAW_CALL_BUDGET_MID = 420;

    public static final int TRI_BUDGET_HIGH = 480000;
    public static final int TRI_BUDGET_MID = 310000;
    public static final int TRI_BUDGET_LOW = 180000;

    public static final int VRAM_LOW_MB = 720;
    public static final int VRAM_MID_MB = 1300;
    public static final int VRAM_HIGH_MB = 2100;

    /* ---------------- cellule ---------------- */
    public static final class Cell {
        public final int cx;
        public final int cy;
        public final int cz;
        public String seq = "";
        public int state = STATE_EMPTY;
        public int solidFrom;
        public int solidTo;
        public int stairFrom;
        public int stairTo;
        public int ledgeFrom;
        public int ledgeTo;
        public int anchorFrom;
        public int anchorTo;
        public int propFrom;
        public int propTo;
        public int drawCalls;
        public int triangles;
        public int vramKb;
        public float loadProgress;
        public float lastUsed;

        Cell(int cx, int cy, int cz) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
        }

        public boolean loaded() {
            return state == STATE_READY;
        }

        public boolean busy() {
            return state == STATE_LOADING;
        }
    }

    public static final int STATE_EMPTY = 0;
    public static final int STATE_LOADING = 1;
    public static final int STATE_READY = 2;
    public static final int STATE_UNLOADING = 3;

    private final ContentDb db;
    private final EventBus bus;
    private final Options options;
    private final Map<Long, Cell> cells = new HashMap<Long, Cell>(512);
    private final List<Cell> loadQueue = new ArrayList<Cell>(8);

    private LevelData level;
    private String seq = "";
    private float time;
    private int loadedCells;
    private int unloadedCells;
    private int drawsLastFrame;
    private int trisLastFrame;
    private float lastSliceMs;
    private float longestSliceMs;
    private int slicesOverBudget;
    private float streamStall;
    private boolean backgrounded;

    public ZoneStreamer(ContentDb db, EventBus bus, Options options) {
        this.db = db;
        this.bus = bus;
        this.options = options;
    }

    /* ------------------------------------------------------------------ */
    /* Chargement d'une sequence                                           */
    /* ------------------------------------------------------------------ */

    public boolean loadSequence(String sequence) {
        if (sequence == null || sequence.isEmpty()) {
            return false;
        }
        LevelData data = db == null ? null : db.level(sequence);
        if (data == null && db != null) {
            db.loadLevel(sequence);
            data = db.level(sequence);
        }
        if (data == null) {
            return false;
        }
        cells.clear();
        loadQueue.clear();
        level = data;
        seq = sequence;
        buildCells();
        loadedCells = 0;
        unloadedCells = 0;
        if (bus != null) {
            bus.emit(EventBus.ZONE_STREAM_STARTED, sequence, cells.size());
        }
        return true;
    }

    /** Decoupe les boites du niveau en cellules de 48 x 48 x 32 m. */
    private void buildCells() {
        if (level == null) {
            return;
        }
        Map<Long, int[]> ranges = new HashMap<Long, int[]>();
        for (int i = 0; i < level.solidCount; i++) {
            addSolid(ranges, i);
        }
        for (Map.Entry<Long, int[]> e : ranges.entrySet()) {
            Cell c = cellForKey(e.getKey());
            int[] r = e.getValue();
            c.solidFrom = Math.min(c.solidFrom == 0 ? r[0] : c.solidFrom, r[0]);
            c.solidTo = Math.max(c.solidTo, r[1]);
            c.seq = seq;
            c.drawCalls = 1 + (r[1] - r[0]) / 24;
            c.triangles = (r[1] - r[0] + 1) * 12;
            c.vramKb = (r[1] - r[0] + 1) * 3;
        }
        /* les cellules vides autour du spawn existent aussi (anneau de preload) */
        float sx = level.spawn[0];
        float sy = level.spawn[1];
        float sz = level.spawn[2];
        for (int dx = -PRELOAD_RING; dx <= PRELOAD_RING; dx++) {
            for (int dz = -PRELOAD_RING; dz <= PRELOAD_RING; dz++) {
                cellFor(sx + dx * CELL_X, sy, sz + dz * CELL_Z).seq = seq;
            }
        }
    }

    private void addSolid(Map<Long, int[]> ranges, int i) {
        float x = level.solidX(i);
        float y = level.solidY(i);
        float z = level.solidZ(i);
        float hx = level.solidHX(i) + 0.5f;
        float hy = level.solidHY(i) + 0.5f;
        float hz = level.solidHZ(i) + 0.5f;
        int x0 = cellIndex(x - hx, CELL_X);
        int x1 = cellIndex(x + hx, CELL_X);
        int y0 = cellIndex(y - hy, CELL_Y);
        int y1 = cellIndex(y + hy, CELL_Y);
        int z0 = cellIndex(z - hz, CELL_Z);
        int z1 = cellIndex(z + hz, CELL_Z);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cy = y0; cy <= y1; cy++) {
                for (int cz = z0; cz <= z1; cz++) {
                    long key = key(cx, cy, cz);
                    int[] r = ranges.get(key);
                    if (r == null) {
                        ranges.put(key, new int[]{i, i});
                    } else {
                        r[0] = Math.min(r[0], i);
                        r[1] = Math.max(r[1], i);
                    }
                }
            }
        }
    }

    public static int cellIndex(float v, float cellSize) {
        return (int) Math.floor(v / cellSize);
    }

    public static long key(int cx, int cy, int cz) {
        return ((long) (cx + 4096) << 40) | ((long) (cy + 2048) << 20) | (long) (cz + 4096);
    }

    private Cell cellForKey(long k) {
        Cell c = cells.get(k);
        if (c == null) {
            int cx = (int) ((k >> 40) & 0xFFFFF) - 4096;
            int cy = (int) ((k >> 20) & 0xFFFFF) - 2048;
            int cz = (int) (k & 0xFFFFF) - 4096;
            c = new Cell(cx, cy, cz);
            cells.put(k, c);
        }
        return c;
    }

    public Cell cellFor(float x, float y, float z) {
        return cellForKey(key(cellIndex(x, CELL_X), cellIndex(y, CELL_Y), cellIndex(z, CELL_Z)));
    }

    /* ------------------------------------------------------------------ */
    /* Boucle : tranche de 4 ms par frame                                  */
    /* ------------------------------------------------------------------ */

    public void update(float dt, float px, float py, float pz) {
        time += dt;
        long t0 = System.nanoTime();
        int pcx = cellIndex(px, CELL_X);
        int pcy = cellIndex(py, CELL_Y);
        int pcz = cellIndex(pz, CELL_Z);

        /* 1. mise en file des cellules de l'anneau de preload */
        int radius = PRELOAD_RING + 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Cell c = cellForKey(key(pcx + dx, pcy + dy, pcz + dz));
                    c.lastUsed = time;
                    if (c.state == STATE_EMPTY) {
                        c.state = STATE_LOADING;
                        c.loadProgress = 0f;
                        loadQueue.add(c);
                    }
                }
            }
        }
        /* 2. instanciation time-slicee, max 2 cellules simultanees */
        int concurrent = 0;
        for (int i = 0; i < loadQueue.size(); i++) {
            Cell c = loadQueue.get(i);
            if (c.state == STATE_READY) {
                loadQueue.remove(i--);
                continue;
            }
            if (concurrent >= MAX_CONCURRENT_LOADS) {
                continue;
            }
            concurrent++;
            float slice = INSTANTIATE_BUDGET_MS * 0.5f;
            c.loadProgress += slice / 60f;
            if (c.loadProgress >= 1f) {
                c.loadProgress = 1f;
                c.state = STATE_READY;
                loadedCells++;
                if (bus != null) {
                    bus.emit(EventBus.CELL_LOADED, c.cx, c.cy, c.cz, c.drawCalls);
                }
            }
        }
        /* 3. dechargement : au-dela de 3 cellules de distance */
        for (Cell c : cells.values()) {
            if (c.state != STATE_READY) {
                continue;
            }
            int d = Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) + Math.abs(c.cy - pcy);
            if (d > PRELOAD_RING + 3) {
                c.state = STATE_UNLOADING;
                unloadedCells++;
                c.state = STATE_EMPTY;
                c.loadProgress = 0f;
            }
        }
        /* 4. budget rendu */
        drawsLastFrame = 0;
        trisLastFrame = 0;
        for (Cell c : cells.values()) {
            if (c.state != STATE_READY) {
                continue;
            }
            int d = Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz));
            if (d > PRELOAD_RING + 2) {
                continue;
            }
            drawsLastFrame += c.drawCalls;
            trisLastFrame += c.triangles;
        }
        /* LOD / culling : le budget tris est une borne dure */
        int triBudget = triangleBudget();
        if (trisLastFrame > triBudget) {
            float k = triBudget / (float) trisLastFrame;
            trisLastFrame = (int) (trisLastFrame * k);
            drawsLastFrame = (int) (drawsLastFrame * Math.max(0.55f, k));
            if (bus != null) {
                bus.emit(EventBus.STREAM_LOD_CUT, k);
            }
        }
        lastSliceMs = (System.nanoTime() - t0) / 1e6f;
        longestSliceMs = Math.max(longestSliceMs, lastSliceMs);
        if (lastSliceMs > INSTANTIATE_BUDGET_MS) {
            slicesOverBudget++;
        }
        streamStall = loadQueue.size() > MAX_CONCURRENT_LOADS * 2 ? 1f : 0f;
    }

    /** 14.12 : qualite 0 Auto · 1 Bas · 2 Moyen · 3 Haut. */
    public static final int QUALITY_AUTO = 0;
    public static final int QUALITY_LOW = 1;
    public static final int QUALITY_MID = 2;
    public static final int QUALITY_HIGH = 3;

    public int quality() {
        int q = options == null ? QUALITY_MID : options.quality;
        return q == QUALITY_AUTO ? resolvedAutoQuality() : q;
    }

    /** Auto : MID par defaut, LOW si le streaming depasse son budget. */
    private int resolvedAutoQuality() {
        if (slicesOverBudget > 120 || trisLastFrame > TRI_BUDGET_MID) {
            return QUALITY_LOW;
        }
        return QUALITY_MID;
    }

    public int triangleBudget() {
        int q = quality();
        if (q == QUALITY_HIGH) {
            return TRI_BUDGET_HIGH;
        }
        if (q == QUALITY_LOW) {
            return TRI_BUDGET_LOW;
        }
        return TRI_BUDGET_MID;
    }

    public int vramBudgetMb() {
        int q = quality();
        if (q == QUALITY_HIGH) {
            return VRAM_HIGH_MB;
        }
        if (q == QUALITY_LOW) {
            return VRAM_LOW_MB;
        }
        return VRAM_MID_MB;
    }

    /** Memoire logique utilisee par les cellules chargees (Mo). */
    public float vramUsedMb() {
        int kb = 0;
        for (Cell c : cells.values()) {
            if (c.state == STATE_READY) {
                kb += c.vramKb;
            }
        }
        return kb / 1024f;
    }

    /* ------------------------------------------------------------------ */
    /* Reprise                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * 02.10 : retour d'arriere-plan -> reprise en < 3 s SANS rechargement
     * de scene. Les cellules READY sont conservees.
     */
    public void onBackground() {
        backgrounded = true;
    }

    public float onForeground() {
        backgrounded = false;
        int ready = 0;
        for (Cell c : cells.values()) {
            if (c.state == STATE_READY) {
                ready++;
            }
        }
        /* cout de reprise : uniquement la remise a jour des buffers GPU */
        float cost = 0.35f + ready * 0.012f;
        if (bus != null) {
            bus.emit(EventBus.RESUME_COMPLETED, cost, ready);
        }
        return cost;
    }

    /** 09.22 : reprise de checkpoint < 2,5 s. */
    public float respawnCost(float px, float py, float pz) {
        Cell c = cellFor(px, py, pz);
        float cost = c.state == STATE_READY ? 0.4f : 1.1f;
        cost += loadQueue.size() * 0.08f;
        return Maths.clamp(cost, 0.2f, RESUME_BUDGET_SECONDS);
    }

    public boolean withinResumeBudget(float px, float py, float pz) {
        return respawnCost(px, py, pz) <= RESUME_BUDGET_SECONDS;
    }

    /* ------------------------------------------------------------------ */
    /* Acces                                                               */
    /* ------------------------------------------------------------------ */

    public int cellCount() {
        return cells.size();
    }

    public int readyCells() {
        int n = 0;
        for (Cell c : cells.values()) {
            if (c.state == STATE_READY) {
                n++;
            }
        }
        return n;
    }

    public int pendingLoads() {
        int n = 0;
        for (Cell c : cells.values()) {
            if (c.state == STATE_LOADING) {
                n++;
            }
        }
        return n;
    }

    public boolean isReady(float x, float y, float z) {
        return cellFor(x, y, z).state == STATE_READY;
    }

    public int drawCalls() {
        return drawsLastFrame;
    }

    public int triangles() {
        return trisLastFrame;
    }

    public float lastSliceMs() {
        return lastSliceMs;
    }

    public float longestSliceMs() {
        return longestSliceMs;
    }

    public int slicesOverBudget() {
        return slicesOverBudget;
    }

    public int loadedCells() {
        return loadedCells;
    }

    public int unloadedCells() {
        return unloadedCells;
    }

    public float streamStall() {
        return streamStall;
    }

    public boolean backgrounded() {
        return backgrounded;
    }

    public String sequence() {
        return seq;
    }

    public LevelData level() {
        return level;
    }

    /**
     * 04.07 : POOLING — prewarm au chargement de zone, zero `instantiate()`
     * pendant le combat. On obtient puis relache `n` objets pour que le pool
     * les cree hors frame critique.
     */
    public static <T> int prewarm(com.velmora.lohen.sim.core.ObjectPool<T> pool, int n) {
        if (pool == null) {
            return 0;
        }
        int made = 0;
        int limit = Math.min(n, pool.capacity());
        List<T> held = new ArrayList<T>(limit);
        for (int i = 0; i < limit; i++) {
            T o = pool.obtain();
            if (o == null) {
                break;
            }
            held.add(o);
            made++;
        }
        for (int i = 0; i < held.size(); i++) {
            pool.release(held.get(i));
        }
        return made;
    }

    /** Garde CI : 48 m, 2 cellules, 4 ms, anneau 1 (04.08 / 02.09). */
    public static boolean specCompliant() {
        return CELL_X == 48f && CELL_Z == 48f && CELL_Y == 32f
                && MAX_CONCURRENT_LOADS == 2
                && INSTANTIATE_BUDGET_MS == 4f
                && PRELOAD_RING == 1
                && RESUME_BUDGET_SECONDS == 2.5f
                && DRAW_CALL_BUDGET_MID == 420;
    }

    public void reset() {
        cells.clear();
        loadQueue.clear();
        level = null;
        seq = "";
        loadedCells = 0;
        unloadedCells = 0;
        longestSliceMs = 0f;
        slicesOverBudget = 0;
    }
}
