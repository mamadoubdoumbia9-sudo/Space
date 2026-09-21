/*
 * LOHEN — sim/world/Npc.java
 *
 * 09.12 : 34 PNJ avec des routines SIMPLES MAIS LISIBLES (un forgeron,
 * une femme qui lave, deux enfants qui jouent a un jeu de cordes, un homme
 * qui repeint la meme porte tous les jours). 96 barks ecrits.
 *
 * 07.15 : systeme de regard a trois cibles ponderees.
 * 07.16 : clignements 1/4,2 s, +40 % en stress, -70 % en choc.
 * 07.17 : respiration additive permanente (calme, effort, panique, retenue).
 *
 * Le PNJ n'est pas une IA : c'est une routine + une presence. Personne ne
 * poursuit le joueur, personne ne le bloque. La ville continue, mal.
 */
package com.velmora.lohen.sim.world;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Rng;
import com.velmora.lohen.sim.math.Maths;

import java.util.ArrayList;
import java.util.List;

public final class Npc {

    /** Etape d'une routine : un poste, une activite, une duree. */
    public static final class Station {
        public float x, y, z;
        public float yawDeg;
        public String activity = "idle";
        public String anim = "IDLE_01";
        public float duration = 20f;
        public String bark = "";
        public boolean walkTo = true;

        public Station() { }

        public Station(float x, float y, float z, float yawDeg, String activity,
                       String anim, float duration, String bark) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yawDeg = yawDeg;
            this.activity = activity;
            this.anim = anim;
            this.duration = duration;
            this.bark = bark == null ? "" : bark;
        }
    }

    public static final int BREATH_CALM = 0;
    public static final int BREATH_EFFORT = 1;
    public static final int BREATH_PANIC = 2;
    public static final int BREATH_HELD = 3;

    public int id;
    public String instanceId = "";
    public String name = "";
    public String archetype = "citizen";
    public String sequence = "";
    public float x, y, z;
    public float yaw;
    public float speed = 1.1f;
    public boolean visible = true;
    public boolean interactable = true;
    public String dialogueScene = "";

    private final List<Station> routine = new ArrayList<Station>(8);
    private int stationIndex;
    private float stationTimer;
    private float walkProgress;
    private float fromX, fromY, fromZ;
    private boolean walking;

    /* regard (07.15) */
    private float gazeInterlocutor = 0.55f;
    private float gazeObject = 0.25f;
    private float gazeEscape = 0.20f;
    private float gazeTimer;
    private int gazeMode;
    private float gazeTargetX, gazeTargetY, gazeTargetZ;

    /* clignements (07.16) */
    private float blinkTimer;
    private float blinkInterval = 4.2f;
    private float stress;
    private boolean blinking;
    private float blinkPhase;
    private int doubleBlinkPending;

    /* respiration (07.17) */
    private int breathProfile = BREATH_CALM;
    private float breathPhase;

    /* micro-gestes (07.18) */
    private final List<String> gestures = new ArrayList<String>(3);
    private float gestureTimer;
    private String currentGesture = "";
    private float gestureTime;

    /* barks */
    private float barkCooldown;
    private int barksPlayed;
    private String lastBark = "";

    private final EventBus bus;
    private final Rng rng;

    public Npc(EventBus bus, Rng rng) {
        this.bus = bus;
        this.rng = rng;
        blinkTimer = rng.range(0.5f, 4.2f);
        gestureTimer = rng.range(2f, 9f);
    }

    public void configure(int id, String instanceId, String name, String archetype,
                          String sequence, float x, float y, float z, float yawDeg) {
        this.id = id;
        this.instanceId = instanceId;
        this.name = name;
        this.archetype = archetype;
        this.sequence = sequence;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = (float) Math.toRadians(yawDeg);
        this.routine.clear();
        this.stationIndex = 0;
        this.stationTimer = 0f;
        this.walking = false;
    }

    public void addStation(Station s) {
        routine.add(s);
    }

    public void addGesture(String gesture) {
        gestures.add(gesture);
    }

    public int stationCount() {
        return routine.size();
    }

    public Station station(int i) {
        return i >= 0 && i < routine.size() ? routine.get(i) : null;
    }

    /** Poids du regard (07.15) : un menteur regarde le point de fuite. */
    public void setGazeWeights(float interlocutor, float object, float escape) {
        float sum = Math.max(0.001f, interlocutor + object + escape);
        gazeInterlocutor = interlocutor / sum;
        gazeObject = object / sum;
        gazeEscape = escape / sum;
    }

    public float gazeEscapeWeight() {
        return gazeEscape;
    }

    public void setStress(float s) {
        stress = Maths.clamp01(s);
    }

    public void setBreathProfile(int profile) {
        breathProfile = profile;
    }

    public int breathProfile() {
        return breathProfile;
    }

    public void update(float dt, float playerX, float playerY, float playerZ,
                       float playerDist, boolean playerTalkingToMe) {
        if (!visible) {
            return;
        }
        /* routine */
        if (!routine.isEmpty()) {
            if (walking) {
                Station target = routine.get(stationIndex);
                float dx = target.x - x;
                float dz = target.z - z;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                if (dist < 0.15f) {
                    walking = false;
                    stationTimer = 0f;
                    x = target.x;
                    z = target.z;
                    y = target.y;
                } else {
                    float step = speed * dt;
                    x += dx / dist * Math.min(step, dist);
                    z += dz / dist * Math.min(step, dist);
                    y = Maths.damp(y, target.y, 4f, dt);
                    yaw = Maths.dampAngle(yaw, (float) Math.atan2(dx, dz), 4f, dt);
                }
            } else {
                Station s = routine.get(stationIndex);
                stationTimer += dt;
                yaw = Maths.dampAngle(yaw, (float) Math.toRadians(s.yawDeg), 2f, dt);
                /* travail repetitif : le forgeron frappe, la laveuse frotte */
                if (s.bark.length() > 0 && barkCooldown <= 0f && playerDist < 22f) {
                    sayBark(s.bark);
                }
                if (stationTimer >= s.duration) {
                    advance();
                }
            }
        }
        if (barkCooldown > 0f) {
            barkCooldown -= dt;
        }
        /* regard */
        updateGaze(dt, playerX, playerY, playerZ, playerTalkingToMe);
        /* clignements */
        updateBlink(dt);
        /* respiration */
        updateBreath(dt, playerDist);
        /* micro-gestes */
        updateGestures(dt);
    }

    private void advance() {
        stationIndex = (stationIndex + 1) % routine.size();
        Station s = routine.get(stationIndex);
        if (s.walkTo) {
            walking = true;
            fromX = x;
            fromY = y;
            fromZ = z;
            walkProgress = 0f;
        } else {
            x = s.x;
            y = s.y;
            z = s.z;
            stationTimer = 0f;
        }
    }

    private void updateGaze(float dt, float px, float py, float pz, boolean talking) {
        gazeTimer -= dt;
        if (gazeTimer <= 0f) {
            gazeTimer = 1.4f + rng.nextFloat() * 2.6f;
            float r = rng.nextFloat();
            if (talking && r < gazeInterlocutor + 0.25f) {
                gazeMode = 0;
            } else if (r < gazeInterlocutor) {
                gazeMode = 0;
            } else if (r < gazeInterlocutor + gazeObject) {
                gazeMode = 1;
            } else {
                gazeMode = 2;      /* point de fuite : le vide, le ciel */
            }
        }
        switch (gazeMode) {
            case 0:
                gazeTargetX = px;
                gazeTargetY = py + 1.6f;
                gazeTargetZ = pz;
                break;
            case 1:
                gazeTargetX = x + (float) Math.sin(yaw) * 1.6f;
                gazeTargetY = y + 1.0f;
                gazeTargetZ = z + (float) Math.cos(yaw) * 1.6f;
                break;
            default:
                gazeTargetX = x + (float) Math.sin(yaw + 0.8f) * 40f;
                gazeTargetY = y + 26f;
                gazeTargetZ = z + (float) Math.cos(yaw + 0.8f) * 40f;
                break;
        }
    }

    private void updateBlink(float dt) {
        /* 07.16 : 1/4,2 s de base, +40 % en stress, -70 % en choc */
        float interval = blinkInterval / (1f + stress * 0.4f);
        if (shock) {
            interval /= 0.3f;
        }
        blinkTimer -= dt;
        if (blinking) {
            blinkPhase += dt / 0.14f;
            if (blinkPhase >= 1f) {
                blinking = false;
                blinkPhase = 0f;
                if (doubleBlinkPending > 0) {
                    doubleBlinkPending--;
                    blinking = true;
                }
            }
        } else if (blinkTimer <= 0f) {
            blinking = true;
            blinkPhase = 0f;
            blinkTimer = interval * (0.7f + rng.nextFloat() * 0.6f);
        }
    }

    private boolean shock;

    public void setShock(boolean s) {
        shock = s;
    }

    /** Une ligne difficile declenche un double-clignement (07.16). */
    public void onDifficultLine() {
        doubleBlinkPending = 1;
    }

    private void updateBreath(float dt, float playerDist) {
        float rate = breathProfile == BREATH_CALM ? 0.25f
                : breathProfile == BREATH_EFFORT ? 0.5f
                        : breathProfile == BREATH_PANIC ? 0.95f : 0.06f;
        breathPhase += dt * rate;
        if (breathPhase > 1f) {
            breathPhase -= 1f;
        }
    }

    private void updateGestures(float dt) {
        if (gestureTime > 0f) {
            gestureTime -= dt;
            if (gestureTime <= 0f) {
                currentGesture = "";
            }
            return;
        }
        gestureTimer -= dt;
        if (gestureTimer <= 0f && !gestures.isEmpty()) {
            gestureTimer = 12f + rng.nextFloat() * 26f;
            currentGesture = gestures.get(rng.nextInt(gestures.size()));
            gestureTime = 1.6f;
            bus.emit(EventBus.NPC_GESTURE, instanceId, currentGesture);
        }
    }

    public void sayBark(String barkId) {
        if (barkId == null || barkId.length() == 0) {
            return;
        }
        lastBark = barkId;
        barksPlayed++;
        barkCooldown = 24f + rng.nextFloat() * 40f;
        bus.emit(EventBus.NPC_BARK, instanceId, barkId);
    }

    public void playGesture(String gesture) {
        currentGesture = gesture;
        gestureTime = 1.8f;
        bus.emit(EventBus.NPC_GESTURE, instanceId, gesture);
    }

    public String currentAnim() {
        if (walking) {
            return "WALK_01";
        }
        if (!routine.isEmpty()) {
            return routine.get(stationIndex).anim;
        }
        return "IDLE_01";
    }

    public String currentActivity() {
        if (routine.isEmpty()) {
            return "idle";
        }
        return routine.get(stationIndex).activity;
    }

    public float gazeTargetX() {
        return gazeTargetX;
    }

    public float gazeTargetY() {
        return gazeTargetY;
    }

    public float gazeTargetZ() {
        return gazeTargetZ;
    }

    public int gazeMode() {
        return gazeMode;
    }

    public float blinkValue() {
        if (!blinking) {
            return 1f;
        }
        return (float) Math.abs(Math.cos(blinkPhase * Math.PI));
    }

    public float breathValue() {
        if (breathProfile == BREATH_HELD) {
            return 0.05f;
        }
        return (float) (Math.sin(breathPhase * Maths.TWO_PI) * 0.5 + 0.5);
    }

    public String currentGesture() {
        return currentGesture;
    }

    public float gestureTime() {
        return gestureTime;
    }

    public int barksPlayed() {
        return barksPlayed;
    }

    public String lastBark() {
        return lastBark;
    }

    public boolean walking() {
        return walking;
    }

    public void setVisible(boolean v) {
        visible = v;
    }

    public void teleport(float nx, float ny, float nz) {
        x = nx;
        y = ny;
        z = nz;
        walking = false;
    }
}
