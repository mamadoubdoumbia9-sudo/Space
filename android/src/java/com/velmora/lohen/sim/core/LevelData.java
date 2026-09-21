/*
 * LOHEN — sim/core/LevelData.java
 *
 * Format de niveau : une zone = un ensemble de boites orientees (Y),
 * d'escaliers, de prises, d'ancres de grappin, de props narratifs, de
 * PNJ, d'Echos, de lumieres, de rencontres et de declencheurs.
 *
 * Le monde est 100 % procedural/data-driven : aucun asset binaire,
 * aucun fichier mort (00.04, 16.04). Les cellules de streaming font
 * 48 x 48 x 32 m (02.09, 04.08).
 *
 * Convention : 1 unite = 1 metre, -Z avant, +Y haut (06.02).
 * L'altitude est absolue et partagee entre les 8 sequences : la regle
 * des 212 metres (09.02) est verifiable dans les donnees.
 */
package com.velmora.lohen.sim.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LevelData {

    /* ------------------------------------------------------------------ */
    /* Types composes                                                      */
    /* ------------------------------------------------------------------ */

    public static final class Trigger {
        public String id = "";
        public String type = "";       /* checkpoint, corridor, echo, dialogue, combat,
                                          shortcut, secret, destroy, script, viewpoint,
                                          lamp_room, heat_well, delivery, silence */
        public float x, y, z;
        public float rx = 2f, ry = 2f, rz = 2f;
        public String target = "";     /* id de scene, d'Echo, de rencontre... */
        public String param = "";
        public float value = 0f;
        public boolean oneShot = true;
    }

    public static final class NpcSpawn {
        public String id = "";
        public String role = "";       /* tallec, mireille, sol, forgeron, laveuse, enfant... */
        public float x, y, z;
        public float yaw;
        public String routine = "idle";
        public float routineRadius = 2f;
        public String dialogue = "";
        public String barkSet = "";
        public boolean major;
    }

    public static final class EchoPoint {
        public String echoId = "";
        public String propId = "";
        public float x, y, z;
        public float radius = 1.5f;    /* 06.24 : approchable a moins de 1,5 m */
    }

    public static final class LightDef {
        public String type = "omni";   /* omni · spot · lantern · lighthouse */
        public float x, y, z;
        public int color = 0xFFD6A0;   /* 2700 K par defaut : lanterne (05.28) */
        public float range = 6f;
        public float intensity = 1f;
        public boolean flicker;
        public String roomId = "";     /* 09.44 : salles a lampe */
    }

    public static final class FogZone {
        public float x, y, z;
        public float radius = 20f;
        public float density = 0.02f;
        public int color = 0x16263A;
    }

    public static final class Encounter {
        public String id = "";
        public float x, y, z;
        public float radius = 12f;
        public final List<String> enemies = new ArrayList<String>(4);
        public String condition = "";   /* 17.02 cause 4 : condition unique par combat */
        public boolean avoidable = true;
        public boolean boss;
        public int phase = 1;
    }

    public static final class SecretDef {
        public String id = "";
        public String kind = "";        /* letter_fragment, journal_object, breath_upgrade,
                                          viewpoint, optional_echo */
        public float x, y, z;
        public float radius = 3f;
        public String payload = "";
    }

    public static final class ShortcutDef {
        public String id = "";
        public String kind = "";        /* ladder_dropped, door_unlocked, rope_descended */
        public float x, y, z;
        public float radius = 2f;
        public String opensTo = "";
    }

    public static final class SkySettings {
        public float sunYawDeg = -35f;      /* derriere-gauche du chemin (05.27) */
        public float sunPitchDeg = 12f;     /* 12 deg au-dessus de l'horizon */
        public int sunColor = 0xFFF0D2;     /* 5200 K filtre par la brume */
        public float sunIntensity = 1.0f;
        public int ambientSky = 0xC8E4FF;   /* 7800 K dans l'ombre */
        public int ambientGround = 0x4A423A;
        public float ambientIntensity = 0.35f;
        public int fogColor = 0x16263A;
        public float fogDensity = 0.012f;
        public float fogVisibilityM = 70f;
        public float saturation = 0.35f;    /* 05.08 par sequence */
        public float exposure = 1.0f;
        public String lut = "lut_gare";
        public boolean night;
        public boolean rain;
        public boolean indoor;
        public float windLevel = 1f;        /* 13.30 : 6 intensites */
        public float cloudSpeed = 1f;
        public boolean lighthouseVisible = true;
        public boolean glassSeaVisible = true;
    }

    /* ------------------------------------------------------------------ */
    /* Donnees de niveau                                                   */
    /* ------------------------------------------------------------------ */

    public String seq = "S1";
    public String name = "";
    public String subtitle = "";
    public float altitudeMin = 0f;
    public float altitudeMax = 14f;
    public float objectiveAltitude = 14f;
    public String objectiveKey = "";
    public String rule = "";
    public String music = "";
    public String ambience = "";
    public String reverb = "falaise";
    public String transitionIn = "";
    public String transitionOut = "";
    public float targetMinutes = 26f;
    public float[] spawn = {0f, 1f, 0f, 0f};
    public float[] bounds = {-100f, -10f, -100f, 100f, 240f, 100f};
    public SkySettings sky = new SkySettings();

    /* solides : 7 floats par boite (cx,cy,cz,hx,hy,hz,yaw) */
    public int solidCount;
    public float[] solidData = new float[0];
    public int[] solidFlags = new int[0];
    public int[] solidMaterial = new int[0];
    public int[] solidKind = new int[0];

    /* escaliers : 8 floats (x,y,z,w,h,d,yaw,steps) */
    public int stairCount;
    public float[] stairData = new float[0];
    public int[] stairMaterial = new int[0];

    /* prises grimpables : 6 floats (x,y,z,len,yaw,kind) */
    public int ledgeCount;
    public float[] ledgeData = new float[0];

    /* ancres de grappin : 4 floats (x,y,z,type) */
    public int anchorCount;
    public float[] anchorData = new float[0];

    /* props narratifs : 5 floats (x,y,z,yaw,scale) + id */
    public int propCount;
    public float[] propData = new float[0];
    public String[] propIds = new String[0];

    /* checkpoints : 4 floats (x,y,z,yaw) + id (47 au total, 09.22) */
    public int checkpointCount;
    public float[] checkpointData = new float[0];
    public String[] checkpointIds = new String[0];

    public final List<Trigger> triggers = new ArrayList<Trigger>();
    public final List<NpcSpawn> npcs = new ArrayList<NpcSpawn>();
    public final List<EchoPoint> echoPoints = new ArrayList<EchoPoint>();
    public final List<LightDef> lights = new ArrayList<LightDef>();
    public final List<FogZone> fogZones = new ArrayList<FogZone>();
    public final List<Encounter> encounters = new ArrayList<Encounter>();
    public final List<SecretDef> secrets = new ArrayList<SecretDef>();
    public final List<ShortcutDef> shortcuts = new ArrayList<ShortcutDef>();

    /* ------------------------------------------------------------------ */
    /* Analyse                                                             */
    /* ------------------------------------------------------------------ */

    public static LevelData parse(Map<String, Object> root) {
        if (root == null) {
            return null;
        }
        LevelData d = new LevelData();
        d.seq = MiniJson.str(root, "seq", "S1");
        d.name = MiniJson.str(root, "name", "");
        d.subtitle = MiniJson.str(root, "subtitle", "");
        d.rule = MiniJson.str(root, "rule", "");
        d.music = MiniJson.str(root, "music", "");
        d.ambience = MiniJson.str(root, "ambience", "");
        d.reverb = MiniJson.str(root, "reverb", "falaise");
        d.transitionIn = MiniJson.str(root, "transition_in", "");
        d.transitionOut = MiniJson.str(root, "transition_out", "");
        d.objectiveKey = MiniJson.str(root, "objective_key", "");
        d.targetMinutes = MiniJson.num(root, "target_minutes", 26f);
        d.altitudeMin = MiniJson.num(root, "altitude_min", 0f);
        d.altitudeMax = MiniJson.num(root, "altitude_max", 14f);
        d.objectiveAltitude = MiniJson.num(root, "objective_altitude", d.altitudeMax);

        d.spawn = floatArray(MiniJson.childList(root, "spawn"), 4, d.spawn);
        d.bounds = floatArray(MiniJson.childList(root, "bounds"), 6, d.bounds);

        Map<String, Object> skyMap = MiniJson.child(root, "sky");
        if (skyMap != null) {
            SkySettings s = d.sky;
            s.sunYawDeg = MiniJson.num(skyMap, "sun_yaw_deg", s.sunYawDeg);
            s.sunPitchDeg = MiniJson.num(skyMap, "sun_pitch_deg", s.sunPitchDeg);
            s.sunColor = color(MiniJson.str(skyMap, "sun_color", "#FFF0D2"), s.sunColor);
            s.sunIntensity = MiniJson.num(skyMap, "sun_intensity", s.sunIntensity);
            s.ambientSky = color(MiniJson.str(skyMap, "ambient_sky", "#C8E4FF"), s.ambientSky);
            s.ambientGround = color(MiniJson.str(skyMap, "ambient_ground", "#4A423A"), s.ambientGround);
            s.ambientIntensity = MiniJson.num(skyMap, "ambient_intensity", s.ambientIntensity);
            s.fogColor = color(MiniJson.str(skyMap, "fog_color", "#16263A"), s.fogColor);
            s.fogDensity = MiniJson.num(skyMap, "fog_density", s.fogDensity);
            s.fogVisibilityM = MiniJson.num(skyMap, "fog_visibility_m", s.fogVisibilityM);
            s.saturation = MiniJson.num(skyMap, "saturation", s.saturation);
            s.exposure = MiniJson.num(skyMap, "exposure", s.exposure);
            s.lut = MiniJson.str(skyMap, "lut", s.lut);
            s.night = MiniJson.bool(skyMap, "night", false);
            s.rain = MiniJson.bool(skyMap, "rain", false);
            s.indoor = MiniJson.bool(skyMap, "indoor", false);
            s.windLevel = MiniJson.num(skyMap, "wind_level", s.windLevel);
            s.cloudSpeed = MiniJson.num(skyMap, "cloud_speed", s.cloudSpeed);
            s.lighthouseVisible = MiniJson.bool(skyMap, "lighthouse_visible", true);
            s.glassSeaVisible = MiniJson.bool(skyMap, "glass_sea_visible", true);
        }

        d.solidData = pack(root, "solids", 7);
        d.solidCount = d.solidData.length / 7;
        d.solidFlags = intArray(MiniJson.childList(root, "solid_flags"), d.solidCount, 1);
        d.solidMaterial = intArray(MiniJson.childList(root, "solid_materials"), d.solidCount, 0);
        d.solidKind = intArray(MiniJson.childList(root, "solid_kinds"), d.solidCount, 0);

        d.stairData = pack(root, "stairs", 8);
        d.stairCount = d.stairData.length / 8;
        d.stairMaterial = intArray(MiniJson.childList(root, "stair_materials"), d.stairCount, 1);

        d.ledgeData = pack(root, "ledges", 6);
        d.ledgeCount = d.ledgeData.length / 6;

        d.anchorData = pack(root, "anchors", 4);
        d.anchorCount = d.anchorData.length / 4;

        d.propData = pack(root, "props", 5);
        d.propCount = d.propData.length / 5;
        List<Object> ids = MiniJson.childList(root, "prop_ids");
        d.propIds = new String[d.propCount];
        for (int i = 0; i < d.propCount; i++) {
            d.propIds[i] = ids != null && i < ids.size() ? String.valueOf(ids.get(i)) : "";
        }

        d.checkpointData = pack(root, "checkpoints", 4);
        d.checkpointCount = d.checkpointData.length / 4;
        List<Object> cpIds = MiniJson.childList(root, "checkpoint_ids");
        d.checkpointIds = new String[d.checkpointCount];
        for (int i = 0; i < d.checkpointCount; i++) {
            d.checkpointIds[i] = cpIds != null && i < cpIds.size() ? String.valueOf(cpIds.get(i)) : "";
        }

        parseList(root, "triggers", new RowParser() {
            @Override
            public void row(Map<String, Object> m) {
                Trigger t = new Trigger();
                t.id = MiniJson.str(m, "id", "");
                t.type = MiniJson.str(m, "type", "");
                t.x = MiniJson.num(m, "x", 0f);
                t.y = MiniJson.num(m, "y", 0f);
                t.z = MiniJson.num(m, "z", 0f);
                t.rx = MiniJson.num(m, "rx", 2f);
                t.ry = MiniJson.num(m, "ry", 2f);
                t.rz = MiniJson.num(m, "rz", 2f);
                t.target = MiniJson.str(m, "target", "");
                t.param = MiniJson.str(m, "param", "");
                t.value = MiniJson.num(m, "value", 0f);
                t.oneShot = MiniJson.bool(m, "one_shot", true);
                d.triggers.add(t);
            }
        });

        parseList(root, "npcs", new RowParser() {
            @Override
            public void row(Map<String, Object> m) {
                NpcSpawn n = new NpcSpawn();
                n.id = MiniJson.str(m, "id", "");
                n.role = MiniJson.str(m, "role", "");
                n.x = MiniJson.num(m, "x", 0f);
                n.y = MiniJson.num(m, "y", 0f);
                n.z = MiniJson.num(m, "z", 0f);
                n.yaw = MiniJson.num(m, "yaw", 0f);
                n.routine = MiniJson.str(m, "routine", "idle");
                n.routineRadius = MiniJson.num(m, "routine_radius", 2f);
                n.dialogue = MiniJson.str(m, "dialogue", "");
                n.barkSet = MiniJson.str(m, "barks", "");
                n.major = MiniJson.bool(m, "major", false);
                d.npcs.add(n);
            }
        });

        parseList(root, "echo_points", new RowParser() {
            @Override
            public void row(Map<String, Object> m) {
                EchoPoint e = new EchoPoint();
                e.echoId = MiniJson.str(m, "echo", "");
                e.propId = MiniJson.str(m, "prop", "");
                e.x = MiniJson.num(m, "x", 0f);
                e.y = MiniJson.num(m, "y", 0f);
                e.z = MiniJson.num(m, "z", 0f);
                e.radius = MiniJson.num(m, "radius", 1.5f);
                d.echoPoints.add(e);
            }
        });

        parseList(root, "lights", new RowParser() {
            @Override
            public void row(Map<String, Object> m) {
                LightDef l = new LightDef();
                l.type = MiniJson.str(m, "type", "omni");
                l.x = MiniJson.num(m, "x", 0f);
                l.y = MiniJson.num(m, "y", 0f);
                l.z = MiniJson.num(m, "z", 0f);
                l.color = color(MiniJson.str(m, "color", "#FFD6A0"), 0xFFD6A0);
                l.range = MiniJson.num(m, "range", 6f);
                l.intensity = MiniJson.num(m, "intensity", 1f);
                l.flicker = MiniJson.bool(m, "flicker", false);
                l.roomId = MiniJson.str(m, "room", "");
                d.lights.add(l);
            }
        });

        parseList(root, "fog_zones", new RowParser() {
            @Override
            public void row(Map<String, Object> m) {
                FogZone f = new FogZone();
                f.x = MiniJson.num(m, "x", 0f);
                f.y = MiniJson.num(m, "y", 0f);
                f.z = MiniJson.num(m, "z", 0f);
                f.radius = MiniJson.num(m, "radius", 20f);
                f.density = MiniJson.num(m, "density", 0.02f);
                f.color = color(MiniJson.str(m, "color", "#16263A"), 0x16263A);
                d.fogZones.add(f);
            }
        });

        parseList(root, "encounters", new RowParser() {
            @Override
            public void row(Map<String, Object> m) {
                Encounter e = new Encounter();
                e.id = MiniJson.str(m, "id", "");
                e.x = MiniJson.num(m, "x", 0f);
                e.y = MiniJson.num(m, "y", 0f);
                e.z = MiniJson.num(m, "z", 0f);
                e.radius = MiniJson.num(m, "radius", 12f);
                e.condition = MiniJson.str(m, "condition", "");
                e.avoidable = MiniJson.bool(m, "avoidable", true);
                e.boss = MiniJson.bool(m, "boss", false);
                e.phase = MiniJson.intNum(m, "phase", 1);
                List<Object> en = MiniJson.childList(m, "enemies");
                if (en != null) {
                    for (Object o : en) {
                        e.enemies.add(String.valueOf(o));
                    }
                }
                d.encounters.add(e);
            }
        });

        parseList(root, "secrets", new RowParser() {
            @Override
            public void row(Map<String, Object> m) {
                SecretDef s = new SecretDef();
                s.id = MiniJson.str(m, "id", "");
                s.kind = MiniJson.str(m, "kind", "");
                s.x = MiniJson.num(m, "x", 0f);
                s.y = MiniJson.num(m, "y", 0f);
                s.z = MiniJson.num(m, "z", 0f);
                s.radius = MiniJson.num(m, "radius", 3f);
                s.payload = MiniJson.str(m, "payload", "");
                d.secrets.add(s);
            }
        });

        parseList(root, "shortcuts", new RowParser() {
            @Override
            public void row(Map<String, Object> m) {
                ShortcutDef s = new ShortcutDef();
                s.id = MiniJson.str(m, "id", "");
                s.kind = MiniJson.str(m, "kind", "");
                s.x = MiniJson.num(m, "x", 0f);
                s.y = MiniJson.num(m, "y", 0f);
                s.z = MiniJson.num(m, "z", 0f);
                s.radius = MiniJson.num(m, "radius", 2f);
                s.opensTo = MiniJson.str(m, "opens_to", "");
                d.shortcuts.add(s);
            }
        });

        return d;
    }

    private interface RowParser {
        void row(Map<String, Object> m);
    }

    private static void parseList(Map<String, Object> root, String key, RowParser p) {
        List<Object> list = MiniJson.childList(root, key);
        if (list == null) {
            return;
        }
        for (Object o : list) {
            Map<String, Object> m = MiniJson.obj(o);
            if (m != null) {
                p.row(m);
            }
        }
    }

    /**
     * Les tableaux geometriques sont stockes a plat : soit une liste de listes
     * [[cx,cy,...], ...], soit une liste plate [cx,cy,...]. Les deux sont
     * acceptes pour rester lisibles a la main dans content/levels/*.json.
     */
    private static float[] pack(Map<String, Object> root, String key, int stride) {
        List<Object> list = MiniJson.childList(root, key);
        if (list == null || list.isEmpty()) {
            return new float[0];
        }
        Object first = list.get(0);
        if (first instanceof List) {
            float[] out = new float[list.size() * stride];
            for (int i = 0; i < list.size(); i++) {
                List<Object> row = MiniJson.list(list.get(i));
                if (row == null) {
                    continue;
                }
                for (int k = 0; k < stride && k < row.size(); k++) {
                    out[i * stride + k] = toFloat(row.get(k));
                }
            }
            return out;
        }
        float[] out = new float[(list.size() / stride) * stride];
        for (int i = 0; i < out.length; i++) {
            out[i] = toFloat(list.get(i));
        }
        return out;
    }

    private static float[] floatArray(List<Object> list, int expected, float[] dflt) {
        if (list == null || list.size() < expected) {
            return dflt;
        }
        float[] out = new float[expected];
        for (int i = 0; i < expected; i++) {
            out[i] = toFloat(list.get(i));
        }
        return out;
    }

    private static int[] intArray(List<Object> list, int count, int dflt) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = list != null && i < list.size() ? (int) toFloat(list.get(i)) : dflt;
        }
        return out;
    }

    private static float toFloat(Object o) {
        if (o instanceof Number) {
            return ((Number) o).floatValue();
        }
        if (o instanceof String) {
            try {
                return Float.parseFloat((String) o);
            } catch (NumberFormatException e) {
                return 0f;
            }
        }
        return 0f;
    }

    public static int color(String hex, int dflt) {
        if (hex == null || hex.length() < 7 || hex.charAt(0) != '#') {
            return dflt;
        }
        try {
            return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Requêtes                                                            */
    /* ------------------------------------------------------------------ */

    public float solidX(int i) {
        return solidData[i * 7];
    }

    public float solidY(int i) {
        return solidData[i * 7 + 1];
    }

    public float solidZ(int i) {
        return solidData[i * 7 + 2];
    }

    public float solidHX(int i) {
        return solidData[i * 7 + 3];
    }

    public float solidHY(int i) {
        return solidData[i * 7 + 4];
    }

    public float solidHZ(int i) {
        return solidData[i * 7 + 5];
    }

    public float solidYaw(int i) {
        return solidData[i * 7 + 6];
    }

    public int solidFlags(int i) {
        return i < solidFlags.length ? solidFlags[i] : 1;
    }

    public int solidMaterial(int i) {
        return i < solidMaterial.length ? solidMaterial[i] : 0;
    }

    public int solidKind(int i) {
        return i < solidKind.length ? solidKind[i] : 0;
    }

    /* ------------------------------------------------------------------ */
    /* Primitives de collision sans allocation (utilisees par PhysicsWorld) */
    /* ------------------------------------------------------------------ */

    public boolean hasFlag(int i, int flag) {
        return (solidFlags(i) & flag) != 0;
    }

    public boolean overlapsY(int i, float lo, float hi) {
        return lo <= solidY(i) + solidHY(i) && hi >= solidY(i) - solidHY(i);
    }

    /** Test XZ en espace local (le lacet est applique autour de Y). */
    public boolean containsXZ(int i, float x, float z, float pad) {
        float dx = x - solidX(i);
        float dz = z - solidZ(i);
        float yaw = (float) Math.toRadians(solidYaw(i));
        if (yaw != 0f) {
            float c = (float) Math.cos(-yaw);
            float s = (float) Math.sin(-yaw);
            float lx = dx * c - dz * s;
            float lz = dx * s + dz * c;
            dx = lx;
            dz = lz;
        }
        return Math.abs(dx) <= solidHX(i) + pad && Math.abs(dz) <= solidHZ(i) + pad;
    }

    /** Repousse un point hors de la boite en XZ ; renvoie la penetration. */
    public float pushOut(int i, float[] pos, float radius) {
        float dx = pos[0] - solidX(i);
        float dz = pos[2] - solidZ(i);
        float yaw = (float) Math.toRadians(solidYaw(i));
        float c = (float) Math.cos(-yaw);
        float s = (float) Math.sin(-yaw);
        float lx = dx * c - dz * s;
        float lz = dx * s + dz * c;
        float px = solidHX(i) + radius - Math.abs(lx);
        float pz = solidHZ(i) + radius - Math.abs(lz);
        if (px <= 0f || pz <= 0f) {
            return 0f;
        }
        float depth = Math.min(px, pz);
        if (px < pz) {
            lx += (lx >= 0f ? px : -px);
        } else {
            lz += (lz >= 0f ? pz : -pz);
        }
        c = (float) Math.cos(yaw);
        s = (float) Math.sin(yaw);
        pos[0] = solidX(i) + lx * c - lz * s;
        pos[2] = solidZ(i) + lx * s + lz * c;
        return depth;
    }

    public float stairX(int i) {
        return stairData[i * 8];
    }

    public float stairY(int i) {
        return stairData[i * 8 + 1];
    }

    public float stairZ(int i) {
        return stairData[i * 8 + 2];
    }

    public float stairW(int i) {
        return stairData[i * 8 + 3];
    }

    public float stairH(int i) {
        return stairData[i * 8 + 4];
    }

    public float stairD(int i) {
        return stairData[i * 8 + 5];
    }

    public float stairYaw(int i) {
        return stairData[i * 8 + 6];
    }

    public float stairSteps(int i) {
        return stairData[i * 8 + 7];
    }

    public int stairMaterial(int i) {
        return i < stairMaterial.length ? stairMaterial[i] : 1;
    }

    public Trigger triggerById(String id) {
        for (Trigger t : triggers) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    public Encounter encounterById(String id) {
        for (Encounter e : encounters) {
            if (e.id.equals(id)) {
                return e;
            }
        }
        return null;
    }

    public EchoPoint echoPointById(String echoId) {
        for (EchoPoint e : echoPoints) {
            if (e.echoId.equals(echoId)) {
                return e;
            }
        }
        return null;
    }

    public int propIndex(String propId) {
        for (int i = 0; i < propCount; i++) {
            if (propId.equals(propIds[i])) {
                return i;
            }
        }
        return -1;
    }

    public int checkpointIndex(String cpId) {
        for (int i = 0; i < checkpointCount; i++) {
            if (cpId.equals(checkpointIds[i])) {
                return i;
            }
        }
        return -1;
    }

    /** Cellule de streaming (48 x 48 x 32 m — 02.09). */
    public static int cellX(float x) {
        return (int) Math.floor(x / 48f);
    }

    public static int cellY(float y) {
        return (int) Math.floor(y / 32f);
    }

    public static int cellZ(float z) {
        return (int) Math.floor(z / 48f);
    }

    public long cellKey(float x, float y, float z) {
        int cx = cellX(x) + 512;
        int cy = cellY(y) + 512;
        int cz = cellZ(z) + 512;
        return ((long) cx << 40) | ((long) cy << 20) | cz;
    }

    public int totalPrimitives() {
        return solidCount + stairCount + ledgeCount + anchorCount + propCount
                + checkpointCount + triggers.size() + npcs.size() + echoPoints.size()
                + lights.size() + fogZones.size() + encounters.size()
                + secrets.size() + shortcuts.size();
    }
}
