/*
 * LOHEN — sim/world/NpcDirector.java
 *
 * Dirige les 34 PNJ de S3 (et ceux des autres sequences) : routines lisibles,
 * barks ecrits (96 en S3), cooldowns de bark par sequence, poursuite de Sol
 * (09.12 : 90 s sur les toits, le joueur NE PEUT PAS gagner, Sol s'arrete et
 * rend la sacoche parce qu'iel a vu le matricule 0114).
 *
 * Cooldowns de bark (BLOC 12) : S1 = 11 min, S3/S4/S5/S8 = 8 min,
 * S6 = 20 min, S7 = 4,5 min.
 */
package com.velmora.lohen.sim.world;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.LevelData;
import com.velmora.lohen.sim.core.Rng;
import com.velmora.lohen.sim.math.Maths;

import java.util.ArrayList;
import java.util.List;

public final class NpcDirector {

    /** Cooldowns de bark par sequence, en secondes (BLOC 12). */
    public static float barkCooldownFor(String seq) {
        if (seq == null) {
            return 480f;
        }
        switch (seq) {
            case "S1":
                return 660f;     /* 11 min */
            case "S3":
            case "S4":
            case "S5":
            case "S8":
                return 480f;     /* 8 min */
            case "S6":
                return 1200f;    /* 20 min */
            case "S7":
                return 270f;     /* 4,5 min */
            default:
                return 480f;
        }
    }

    /** Gain de souffle par sequence (BLOC 12) : S1 0,75 / S5 0,55 / autres 1,0. */
    public static float breathGainFactor(String seq) {
        if ("S1".equals(seq)) {
            return 0.75f;
        }
        if ("S5".equals(seq)) {
            return 0.55f;
        }
        return 1.0f;
    }

    private final EventBus bus;
    private final Rng rng;
    private final List<Npc> npcs = new ArrayList<Npc>(48);
    private String sequence = "S1";
    private float globalBarkTimer;
    private int barksPlayed;

    /* poursuite de Sol (09.12) */
    public static final float SOL_STEAL_DELAY = 30f;
    public static final float SOL_CHASE_DURATION = 90f;
    private Npc sol;
    private boolean solStole;
    private boolean solChasing;
    private float solTimer;
    private float solStealTimer;
    private float[] solPath;
    private int solPathIndex;
    private boolean solReturned;

    public NpcDirector(EventBus bus, Rng rng) {
        this.bus = bus;
        this.rng = rng;
    }

    public void setSequence(String seq) {
        sequence = seq;
        globalBarkTimer = barkCooldownFor(seq) * 0.25f;
    }

    public String sequence() {
        return sequence;
    }

    public void clear() {
        npcs.clear();
        sol = null;
        solStole = false;
        solChasing = false;
        solReturned = false;
    }

    /** Construit les PNJ declares dans le niveau (content/levels/*.json). */
    public void loadFromLevel(LevelData level) {
        clear();
        if (level == null) {
            return;
        }
        setSequence(level.seq);
        for (int i = 0; i < level.npcs.size(); i++) {
            LevelData.NpcSpawn sp = level.npcs.get(i);
            String role = sp.role == null ? "" : sp.role.toLowerCase();
            Npc n = new Npc(bus, rng);
            n.configure(i, sp.id, displayName(role), role.toUpperCase(), level.seq,
                    sp.x, sp.y, sp.z, sp.yaw);
            n.dialogueScene = sp.dialogue;
            n.speed = speedFor(role);
            n.interactable = sp.major || role.length() > 0;
            buildRoutine(n, sp, i, role);
            npcs.add(n);
            if ("SOL".equals(n.archetype)) {
                sol = n;
            }
        }
    }

    private static float speedFor(String role) {
        if ("sol".equals(role)) {
            return 4.6f;
        }
        if ("enfant".equals(role)) {
            return 2.4f;
        }
        if ("tallec".equals(role) || "mireille".equals(role)) {
            return 1.35f;
        }
        return 1.1f;
    }

    private static String displayName(String role) {
        if ("tallec".equals(role)) {
            return "Capitaine Orval Tallec";
        }
        if ("mireille".equals(role)) {
            return "Mireille Vasseur";
        }
        if ("sol".equals(role)) {
            return "Sol";
        }
        if ("forgeron".equals(role)) {
            return "le forgeron";
        }
        if ("laveuse".equals(role)) {
            return "la femme qui lave";
        }
        if ("peintre".equals(role)) {
            return "l'homme qui repeint la meme porte";
        }
        if ("enfant".equals(role)) {
            return "un enfant";
        }
        if ("verrier".equals(role)) {
            return "Anselme Roux";
        }
        return role == null || role.length() == 0 ? "habitant" : role;
    }

    /**
     * Routines lisibles (09.12) : le forgeron frappe, la femme lave, les deux
     * enfants jouent a un jeu de cordes, l'homme repeint la meme porte.
     */
    private void buildRoutine(Npc n, LevelData.NpcSpawn sp, int index, String role) {
        String a = n.archetype == null ? "" : n.archetype;
        float x = n.x, y = n.y, z = n.z;
        if ("forgeron".equals(a)) {
            n.addStation(new Npc.Station(x, y, z, 200f, "frappe", "NPC_FORGE_STRIKE", 26f, ""));
            n.addStation(new Npc.Station(x + 0.6f, y, z + 0.3f, 20f, "trempe", "NPC_FORGE_QUENCH", 12f, ""));
            n.addStation(new Npc.Station(x, y, z, 200f, "frappe", "NPC_FORGE_STRIKE", 30f, ""));
            n.addGesture("NPC_FORGE_WIPE");
        } else if ("laveuse".equals(a)) {
            n.addStation(new Npc.Station(x, y, z, 180f, "lave", "NPC_WASH_SCRUB", 48f, ""));
            n.addStation(new Npc.Station(x, y + 0.02f, z, 190f, "essore", "NPC_WASH_WRING", 16f, ""));
            n.addStation(new Npc.Station(x, y, z, 180f, "lave", "NPC_WASH_SCRUB", 44f, ""));
            n.addGesture("NPC_WASH_HANDS");
        } else if ("enfant".equals(a)) {
            /* deux enfants, un jeu de cordes : ils sautent en rythme */
            n.addStation(new Npc.Station(x, y, z, index % 2 == 0 ? 90f : 270f, "corde",
                    "NPC_CHILD_ROPE_JUMP", 14f, ""));
            n.addStation(new Npc.Station(x, y, z, index % 2 == 0 ? 90f : 270f, "repos",
                    "NPC_CHILD_ROPE_REST", 5f, ""));
            n.speed = 2.4f;
            n.addGesture("NPC_CHILD_LAUGH");
        } else if ("peintre".equals(a)) {
            /* l'homme qui repeint la meme porte tous les jours */
            n.addStation(new Npc.Station(x, y, z, 0f, "peint", "NPC_PAINT_STROKE", 60f, ""));
            n.addStation(new Npc.Station(x, y, z, 0f, "regarde", "NPC_PAINT_LOOK", 14f, ""));
            n.addStation(new Npc.Station(x, y, z, 0f, "peint", "NPC_PAINT_STROKE", 60f, ""));
            n.addGesture("NPC_PAINT_STIR");
        } else if ("SOL".equals(a)) {
            n.addStation(new Npc.Station(x, y, z, 0f, "guette", "NPC_SOL_WATCH", 22f, ""));
            n.addStation(new Npc.Station(x + 3f, y, z + 2f, 40f, "rode", "NPC_SOL_PROWL", 18f, ""));
            n.speed = 4.6f;
            n.addGesture("S1");   /* Sol se balance d'un pied sur l'autre (07.18) */
        } else if ("TALLEC".equals(a)) {
            n.addStation(new Npc.Station(x, y, z, 180f, "poste", "NPC_TALLEC_STAND", 40f, ""));
            n.addStation(new Npc.Station(x, y, z, 180f, "prothese", "NPC_TALLEC_TAP", 8f, ""));
            n.addGesture("T1");   /* Tallec tape sa prothese, deux coups secs */
            n.addGesture("T2");   /* Tallec s'assoit toujours de travers */
            n.setGazeWeights(0.7f, 0.2f, 0.1f);
        } else if ("MIREILLE".equals(a)) {
            n.addStation(new Npc.Station(x, y, z, 160f, "registre", "NPC_MIREILLE_WRITE", 36f, ""));
            n.addStation(new Npc.Station(x, y, z, 160f, "tasse", "NPC_MIREILLE_TEA", 18f, ""));
            n.addGesture("M1");   /* tourne sa tasse de 90 deg avant de boire */
            n.addGesture("M2");   /* cache sa main brulee quand on la regarde */
            /* S4 : Mireille ment — elle regarde le point de fuite 62 % du temps */
            n.setGazeWeights(0.24f, 0.14f, 0.62f);
            n.setStress(0.45f);
        } else {
            /* PNJ generique : trois postes, une marche, un arret */
            float r = Math.max(1.5f, sp.routineRadius);
            n.addStation(new Npc.Station(x, y, z, rng.range(0f, 360f), "attend",
                    "NPC_IDLE_A", 20f + rng.nextFloat() * 30f, ""));
            n.addStation(new Npc.Station(x + (float) Math.cos(index) * r, y,
                    z + (float) Math.sin(index) * r, rng.range(0f, 360f), "travaille",
                    "NPC_WORK_A", 24f + rng.nextFloat() * 26f, ""));
            n.addStation(new Npc.Station(x, y, z, rng.range(0f, 360f), "regarde",
                    "NPC_LOOK_FAR", 18f + rng.nextFloat() * 20f, ""));
        }
        /* barks declares dans le niveau (champ barkSet, separes par des virgules) */
        String[] barks = sp.barkSet == null || sp.barkSet.length() == 0
                ? null : sp.barkSet.split(",");
        if (barks != null) {
            for (int i = 0; i < barks.length && i < n.stationCount(); i++) {
                Npc.Station s = n.station(i);
                if (s != null && barks[i] != null) {
                    s.bark = barks[i].trim();
                }
            }
        }
    }

    public List<Npc> npcs() {
        return npcs;
    }

    public int count() {
        return npcs.size();
    }

    public Npc byId(String instanceId) {
        for (int i = 0; i < npcs.size(); i++) {
            if (npcs.get(i).instanceId.equals(instanceId)) {
                return npcs.get(i);
            }
        }
        return null;
    }

    public Npc nearest(float x, float y, float z, float maxDist) {
        Npc best = null;
        float bestD = maxDist;
        for (int i = 0; i < npcs.size(); i++) {
            Npc n = npcs.get(i);
            if (!n.visible || !n.interactable) {
                continue;
            }
            float d = (float) Math.sqrt((n.x - x) * (n.x - x) + (n.z - z) * (n.z - z));
            if (d < bestD && Math.abs(n.y - y) < 3f) {
                bestD = d;
                best = n;
            }
        }
        return best;
    }

    public void update(float dt, float px, float py, float pz) {
        for (int i = 0; i < npcs.size(); i++) {
            Npc n = npcs.get(i);
            float d = (float) Math.sqrt((n.x - px) * (n.x - px) + (n.z - pz) * (n.z - pz));
            n.update(dt, px, py, pz, d, false);
        }
        updateSol(dt, px, py, pz);
        /* barks d'ambiance globaux : jamais plus d'un a la fois (13.28) */
        globalBarkTimer -= dt;
        if (globalBarkTimer <= 0f) {
            globalBarkTimer = barkCooldownFor(sequence) * (0.5f + rng.nextFloat());
            emitAmbientBark(px, py, pz);
        }
    }

    private void emitAmbientBark(float px, float py, float pz) {
        if (npcs.isEmpty()) {
            return;
        }
        Npc n = npcs.get(rng.nextInt(npcs.size()));
        if (!n.visible) {
            return;
        }
        float d = (float) Math.sqrt((n.x - px) * (n.x - px) + (n.z - pz) * (n.z - pz));
        if (d > 34f) {
            return;
        }
        n.sayBark(sequence + "_BK_L" + (100 + rng.nextInt(40)));
        barksPlayed++;
        bus.emit(EventBus.BARK_PLAYED, sequence, barksPlayed);
    }

    /**
     * 09.12 : Sol vole la sacoche dans les 30 s suivant l'entree dans la zone.
     * Poursuite jouable de 90 s sur les toits. Le joueur ne peut pas gagner.
     * Sol s'arrete et rend la sacoche, parce qu'iel a vu le matricule 0114.
     */
    private void updateSol(float dt, float px, float py, float pz) {
        if (sol == null || !"S3".equals(sequence)) {
            return;
        }
        if (solReturned) {
            return;
        }
        if (!solStole) {
            solStealTimer += dt;
            float dist = (float) Math.sqrt((sol.x - px) * (sol.x - px) + (sol.z - pz) * (sol.z - pz));
            if (solStealTimer >= SOL_STEAL_DELAY && dist < 14f) {
                solStole = true;
                solChasing = true;
                solTimer = 0f;
                bus.emit(EventBus.SOL_STEAL);
                bus.emit(EventBus.SCENE_STARTED, "S3_SOL_VOLSACOCHE");
            }
            return;
        }
        if (solChasing) {
            solTimer += dt;
            /* Sol reste toujours 4 a 9 m devant : impossible a rattraper */
            float dx = px - sol.x;
            float dz = pz - sol.z;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            if (dist < 4f) {
                /* Sol accelere : le joueur ne gagne jamais */
                sol.speed = 6.4f;
            } else if (dist > 9f) {
                sol.speed = 3.4f;
            } else {
                sol.speed = 4.6f;
            }
            if (solPath != null && solPath.length >= 3) {
                float tx = solPath[solPathIndex * 3];
                float ty = solPath[solPathIndex * 3 + 1];
                float tz = solPath[solPathIndex * 3 + 2];
                float ddx = tx - sol.x;
                float ddz = tz - sol.z;
                float l = (float) Math.sqrt(ddx * ddx + ddz * ddz);
                if (l < 1.2f) {
                    solPathIndex = (solPathIndex + 1) % (solPath.length / 3);
                } else {
                    sol.x += ddx / l * sol.speed * dt;
                    sol.z += ddz / l * sol.speed * dt;
                    sol.y = Maths.damp(sol.y, ty, 3f, dt);
                    sol.yaw = Maths.dampAngle(sol.yaw, (float) Math.atan2(ddx, ddz), 6f, dt);
                }
            } else {
                /* pas de chemin declare : Sol fuit en ligne brisee */
                float ang = (float) Math.atan2(-dx, -dz) + (rng.nextFloat() - 0.5f) * 0.9f;
                sol.x += (float) Math.sin(ang) * sol.speed * dt;
                sol.z += (float) Math.cos(ang) * sol.speed * dt;
            }
            if (solTimer >= SOL_CHASE_DURATION) {
                solChasing = false;
                solReturned = true;
                bus.emit(EventBus.SOL_RETURNS_SATCHEL);
                bus.emit(EventBus.SCENE_STARTED, "S3_SOL_MATRICULE");
            }
        }
    }

    public void setSolPath(float[] path) {
        this.solPath = path;
        this.solPathIndex = 0;
    }

    public boolean solChasing() {
        return solChasing;
    }

    public boolean solStole() {
        return solStole;
    }

    public boolean solReturned() {
        return solReturned;
    }

    public void markSolReturned() {
        solReturned = true;
        solChasing = false;
        solStole = true;
    }

    public int barksPlayed() {
        return barksPlayed;
    }

    public Npc sol() {
        return sol;
    }
}
