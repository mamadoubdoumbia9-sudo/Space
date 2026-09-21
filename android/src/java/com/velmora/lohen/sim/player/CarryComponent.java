/*
 * LOHEN — sim/player/CarryComponent.java
 *
 * 08.11 V6 — PORTER. Certains objets (une malle, un corps, une lanterne, un
 * levier arrache) se portent. Porter modifie la locomotion : plus lent, pas
 * de grappin, pas de garde, la camera se resserre, la respiration change.
 * Trois sequences du chapitre reposent la-dessus, dont la derniere montee.
 *
 * 09.14 S5 — LES CONDUITS. REGLE UNIQUE : PORTER la lanterne. Une main
 * occupee = pas de grappin, pas de garde. On peut la poser. On ne veut pas.
 * Les Mueurs vivent ici. Ils ont peur de la lumiere mais pas assez.
 * Furtivite simple : rester dans le noir les attire, la lumiere les
 * repousse a 4 m. Trois passages ou il faut poser la lanterne pour grimper,
 * et donc traverser 8 m dans le noir. Ces 8 m durent une heure.
 *
 * 08.21 : « porter un objet » est enseigne a 01:19, sans tutoriel textuel
 * (08.20) — une situation ou le verbe est la seule solution.
 */
package com.velmora.lohen.sim.player;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Tuning;
import com.velmora.lohen.sim.math.Maths;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CarryComponent {

    /* ---------------- les objets portables (08.11) ---------------- */
    public static final String LANTERNE = "lanterne";
    public static final String MALLE = "malle";
    public static final String CORPS = "corps";
    public static final String LEVIER = "levier";
    public static final String DOSSIER = "dossier";
    public static final String LETTRE = "lettre";
    public static final String COUVERTURE = "couverture";

    public static final class Item {
        public final String id;
        public final String name;
        public final boolean twoHanded;
        public final boolean emitsLight;
        public final float speedFactor;
        public final float weightKg;
        public final String pickupAnim;
        public final String carryAnim;
        public final String setdownAnim;

        Item(String id, String name, boolean twoHanded, boolean emitsLight,
             float speedFactor, float weightKg, String pickupAnim, String carryAnim,
             String setdownAnim) {
            this.id = id;
            this.name = name;
            this.twoHanded = twoHanded;
            this.emitsLight = emitsLight;
            this.speedFactor = speedFactor;
            this.weightKg = weightKg;
            this.pickupAnim = pickupAnim;
            this.carryAnim = carryAnim;
            this.setdownAnim = setdownAnim;
        }
    }

    public static final Map<String, Item> ITEMS = new LinkedHashMap<String, Item>();

    static {
        ITEMS.put(LANTERNE, new Item(LANTERNE, "la lanterne a huile", false, true,
                0.86f, 1.4f, "CARRY_PICKUP_LANTERN", "CARRY_WALK_LANTERN",
                "CARRY_SETDOWN_LANTERN"));
        ITEMS.put(MALLE, new Item(MALLE, "la malle", true, false,
                0.62f, 24f, "CARRY_PICKUP_CHEST", "CARRY_WALK_CHEST",
                "CARRY_SETDOWN_CHEST"));
        ITEMS.put(CORPS, new Item(CORPS, "le corps", true, false,
                0.55f, 62f, "CARRY_PICKUP_BODY", "CARRY_WALK_BODY",
                "CARRY_SETDOWN_BODY"));
        ITEMS.put(LEVIER, new Item(LEVIER, "le levier arrache", true, false,
                0.72f, 11f, "CARRY_PICKUP_LEVER", "CARRY_WALK_LEVER",
                "CARRY_SETDOWN_LEVER"));
        ITEMS.put(DOSSIER, new Item(DOSSIER, "le dossier du Registre", false, false,
                0.92f, 0.8f, "CARRY_PICKUP_FOLDER", "CARRY_WALK_FOLDER",
                "CARRY_SETDOWN_FOLDER"));
        ITEMS.put(LETTRE, new Item(LETTRE, "la lettre pliee en trois", false, false,
                1.0f, 0.02f, "PICKUP_LETTER", "IDLE_01", "STORE_LETTER"));
        ITEMS.put(COUVERTURE, new Item(COUVERTURE, "la couverture pliee", false, false,
                0.95f, 2.2f, "CARRY_PICKUP_BLANKET", "CARRY_WALK_BLANKET",
                "CARRY_SETDOWN_BLANKET"));
    }

    /** 09.14 : trois passages ou il faut poser la lanterne. */
    public static final int DARK_CROSSINGS_S5 = 3;
    public static final float DARK_CROSSING_M = 8f;
    /** 09.14 : la lumiere repousse les Mueurs a 4 m. */
    public static final float LIGHT_REPEL_M = 4f;
    /** 09.14 : plafond de 1,4 m — Lohen est accroupi 60 % du temps. */
    public static final float CEILING_S5 = 1.4f;
    public static final float CROUCH_TIME_FRACTION_S5 = 0.6f;
    /** 08.21 : enseigne a 01:19. */
    public static final float TAUGHT_AT_SECONDS = 79f;

    /* ------------------------------------------------------------------ */
    /* Une lanterne posee reste allumee la ou on l'a laissee               */
    /* ------------------------------------------------------------------ */

    public static final class Placed {
        public final String itemId;
        public float x, y, z;
        public boolean lit;
        public float sinceSetdown;

        Placed(String itemId, float x, float y, float z) {
            this.itemId = itemId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lit = true;
        }
    }

    private final Tuning tuning;
    private final EventBus bus;

    private Item carried;
    private boolean carrying;
    private int hand = 1;                 /* 0 = gauche, 1 = droite */
    private float transition;             /* 0 -> 1 pendant la prise / la pose */
    private boolean transitioning;
    private boolean transitionIsPickup;
    private float swapTimer;
    private float carryTime;
    private float lightRadius;
    private float lightFlicker;
    private float lightX, lightY, lightZ;
    private boolean lightOn;

    private final List<Placed> placed = new ArrayList<Placed>(6);
    private float darkDistance;
    private boolean inDarkCrossing;
    private int darkCrossings;
    private float longestDark;
    private float currentDark;
    private int pickups;
    private int setdowns;
    private int swaps;
    private float crouchTime;
    private float totalTime;

    public CarryComponent(Tuning tuning, EventBus bus) {
        this.tuning = tuning;
        this.bus = bus;
        lightRadius = tuning == null ? LIGHT_REPEL_M : tuning.lanternLightRadius;
    }

    /* ------------------------------------------------------------------ */
    /* Prise / pose                                                        */
    /* ------------------------------------------------------------------ */

    public boolean pickUp(String itemId) {
        Item it = ITEMS.get(itemId);
        if (it == null || carrying || transitioning) {
            return false;
        }
        carried = it;
        carrying = true;
        transitioning = true;
        transitionIsPickup = true;
        transition = 0f;
        pickups++;
        if (it.emitsLight) {
            lightOn = true;
        }
        if (bus != null) {
            bus.emit(EventBus.CARRY_PICKUP, itemId, it.speedFactor, it.twoHanded);
            bus.emit(EventBus.ANIM_STATE, it.pickupAnim, pickupSeconds());
            bus.emit(EventBus.VERB_USED, "porter", itemId);
        }
        return true;
    }

    /** On peut la poser. On ne veut pas. (09.14) */
    public boolean setDown() {
        if (!carrying || transitioning) {
            return false;
        }
        transitioning = true;
        transitionIsPickup = false;
        transition = 0f;
        setdowns++;
        if (bus != null) {
            bus.emit(EventBus.CARRY_SETDOWN, carried.id);
            bus.emit(EventBus.ANIM_STATE, carried.setdownAnim, setdownSeconds());
        }
        return true;
    }

    /** Changement de main : la lanterne passe de la main droite a la gauche. */
    public boolean swapHand() {
        if (!carrying || carried.twoHanded || swapTimer > 0f) {
            return false;
        }
        swapTimer = swapSeconds();
        hand = 1 - hand;
        swaps++;
        if (bus != null) {
            bus.emit(EventBus.CARRY_SWAP_HAND, hand, swapTimer);
        }
        return true;
    }

    public float pickupSeconds() {
        return tuning == null ? 0.9f : tuning.carryPickupSeconds;
    }

    public float setdownSeconds() {
        return tuning == null ? 0.7f : tuning.carrySetdownSeconds;
    }

    public float swapSeconds() {
        return tuning == null ? 0.45f : tuning.carrySwapHandSeconds;
    }

    /* ------------------------------------------------------------------ */
    /* Boucle                                                              */
    /* ------------------------------------------------------------------ */

    public void update(float dt, Lohen lohen, Motor motor) {
        totalTime += dt;
        if (swapTimer > 0f) {
            swapTimer = Math.max(0f, swapTimer - dt);
        }
        if (transitioning) {
            float dur = transitionIsPickup ? pickupSeconds() : setdownSeconds();
            transition += dt / Math.max(0.05f, dur);
            if (transition >= 1f) {
                transition = 1f;
                transitioning = false;
                if (!transitionIsPickup) {
                    /* l'objet reste au sol, la lanterne reste allumee */
                    placed.add(new Placed(carried.id, lohen.x, lohen.y, lohen.z));
                    if (carried.emitsLight) {
                        lightOn = false;
                        if (bus != null) {
                            bus.emit(EventBus.LANTERN_PLACED, lohen.x, lohen.y, lohen.z,
                                    placed.size());
                        }
                    }
                    carrying = false;
                    carried = null;
                    if (lohen != null) {
                        lohen.carrying = false;
                        lohen.carriedObjectId = "";
                    }
                    if (motor != null) {
                        motor.setSpeedScale(1f);
                    }
                    if (bus != null) {
                        bus.emit(EventBus.CARRY_CHANGED, "", 1f);
                    }
                } else if (lohen != null) {
                    lohen.carrying = true;
                    lohen.carriedObjectId = carried.id;
                    if (motor != null) {
                        motor.setSpeedScale(speedFactor());
                    }
                    if (bus != null) {
                        bus.emit(EventBus.CARRY_CHANGED, carried.id, speedFactor());
                    }
                }
            }
        }
        if (carrying && carried != null) {
            carryTime += dt;
            if (lohen != null) {
                lohen.carrying = true;
                lohen.carriedObjectId = carried.id;
                /* la main occupee : IK du bras correspondant */
                if (carried.twoHanded) {
                    lohen.leftHandIK = 1f;
                    lohen.rightHandIK = 1f;
                } else if (hand == 0) {
                    lohen.leftHandIK = 1f;
                    lohen.rightHandIK = 0f;
                } else {
                    lohen.leftHandIK = 0f;
                    lohen.rightHandIK = 1f;
                }
            }
            if (motor != null) {
                motor.setSpeedScale(speedFactor());
            }
        }
        /* lumiere de la lanterne : portee, vacillement (VFX V22, 2700 K) */
        if (lightOn && carrying && carried != null && carried.emitsLight) {
            lightFlicker += dt;
            float flick = 0.92f + 0.08f * (float) Math.sin(lightFlicker * 11.3f)
                    + 0.04f * (float) Math.sin(lightFlicker * 27.7f);
            lightRadius = (tuning == null ? LIGHT_REPEL_M : tuning.lanternLightRadius) * flick;
            if (lohen != null) {
                float side = hand == 0 ? -1f : 1f;
                float cy = (float) Math.cos(lohen.yaw);
                float sy = (float) Math.sin(lohen.yaw);
                lightX = lohen.x + cy * 0.22f * side + sy * 0.18f;
                lightY = lohen.y + 1.02f;
                lightZ = lohen.z - sy * 0.22f * side + cy * 0.18f;
            }
        } else if (!lightOn) {
            lightRadius = 0f;
        }
        /* les lanternes posees continuent d'eclairer */
        for (int i = 0; i < placed.size(); i++) {
            Placed p = placed.get(i);
            if (p.lit) {
                p.sinceSetdown += dt;
            }
        }
        /* 09.14 : traverser 8 m dans le noir. Est "dans le noir" ce qui est
         * au-dela du rayon de repulsion de la lumiere (4 m). */
        float nearestLight = nearestLightDistance(lohen);
        boolean dark = nearestLight > repelRadius();
        if (dark && lohen != null && lohen.groundedSpeed > 0.35f) {
            darkDistance += lohen.groundedSpeed * dt;
            currentDark += dt;
            longestDark = Math.max(longestDark, currentDark);
            if (!inDarkCrossing) {
                inDarkCrossing = true;
                if (bus != null) {
                    bus.emit(EventBus.DARK_CROSSING_BEGIN, DARK_CROSSING_M);
                }
            }
            if (darkDistance >= DARK_CROSSING_M) {
                darkCrossings++;
                darkDistance = 0f;
                inDarkCrossing = false;
                if (bus != null) {
                    bus.emit(EventBus.DARK_CROSSING_DONE, darkCrossings);
                }
            }
        } else {
            if (inDarkCrossing) {
                inDarkCrossing = false;
                darkDistance = 0f;
                currentDark = 0f;
            }
        }
        /* 09.14 : plafond de 1,4 m — accroupi 60 % du temps */
        if (lohen != null && lohen.crouch) {
            crouchTime += dt;
        }
    }

    /** Distance a la source de lumiere la plus proche (lanterne portee ou posee). */
    public float nearestLightDistance(Lohen lohen) {
        if (lohen == null) {
            return Float.MAX_VALUE;
        }
        float best = lightOn ? 0f : Float.MAX_VALUE;
        for (int i = 0; i < placed.size(); i++) {
            Placed p = placed.get(i);
            if (!p.lit) {
                continue;
            }
            float dx = lohen.x - p.x;
            float dy = (lohen.y + 1f) - (p.y + 0.3f);
            float dz = lohen.z - p.z;
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < best) {
                best = d;
            }
        }
        return best;
    }

    /* ------------------------------------------------------------------ */
    /* Effets sur les autres systemes (08.11)                              */
    /* ------------------------------------------------------------------ */

    /** Plus lent. Le facteur vient de content/tuning/tuning.json (04.06). */
    public float speedFactor() {
        if (carried == null) {
            return 1f;
        }
        if (tuning == null) {
            return carried.speedFactor;
        }
        switch (carried.id) {
            case LANTERNE:
                return tuning.carrySpeedLantern;
            case MALLE:
                return tuning.carrySpeedChest;
            case CORPS:
                return tuning.carrySpeedBody;
            case LEVIER:
                return tuning.carrySpeedLever;
            case DOSSIER:
                return tuning.carrySpeedDossier;
            case LETTRE:
                return tuning.carrySpeedLetter;
            default:
                return carried.speedFactor;
        }
    }

    /** Une main occupee = pas de grappin. */
    public boolean allowsGrapple() {
        return !carrying || (tuning != null && tuning.carryAllowsGrapple);
    }

    /** Une main occupee = pas de garde. */
    public boolean allowsGuard() {
        return !carrying || (tuning != null && tuning.carryAllowsGuard);
    }

    public boolean allowsParry() {
        return allowsGuard();
    }

    public boolean allowsSprint() {
        return !carrying || (tuning != null && tuning.carryAllowsSprint);
    }

    public boolean allowsWallrun() {
        return !carrying || (tuning != null && tuning.carryAllowsWallrun);
    }

    /** La camera se resserre (08.11). */
    public float cameraDistanceFactor() {
        return carrying && tuning != null ? tuning.carryCameraDistanceFactor : 1f;
    }

    public float cameraFovDelta() {
        return carrying && tuning != null ? tuning.carryCameraFovDelta : 0f;
    }

    /** La respiration change : profil EFFORT, regeneration reduite. */
    public int breathProfile() {
        return carrying ? CharacterRigBreath.EFFORT : CharacterRigBreath.CALM;
    }

    public float breathRegenFactor() {
        return carrying && tuning != null ? tuning.carryBreathRegenFactor : 1f;
    }

    /** Profil de respiration — evite une dependance circulaire vers anim/. */
    public static final class CharacterRigBreath {
        public static final int CALM = 0;
        public static final int EFFORT = 1;
        public static final int PANIC = 2;
        public static final int HELD = 3;
    }

    /* ------------------------------------------------------------------ */
    /* 09.14 : les Mueurs et la lumiere                                    */
    /* ------------------------------------------------------------------ */

    /** La lumiere les repousse a 4 m. */
    public boolean repels(float mx, float my, float mz) {
        if (!lightOn || carried == null || !carried.emitsLight) {
            return false;
        }
        float dx = mx - lightX;
        float dy = my - lightY;
        float dz = mz - lightZ;
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float radius = tuning == null ? LIGHT_REPEL_M : tuning.mueurRepelRadius;
        return d < radius;
    }

    public float repelRadius() {
        return tuning == null ? LIGHT_REPEL_M : tuning.mueurRepelRadius;
    }

    /** Rester dans le noir les attire. */
    public boolean attractsInDark(Lohen lohen) {
        return nearestLightDistance(lohen) > repelRadius();
    }

    /* ------------------------------------------------------------------ */
    /* Acces                                                               */
    /* ------------------------------------------------------------------ */

    public boolean carrying() {
        return carrying;
    }

    public String carriedId() {
        return carried == null ? "" : carried.id;
    }

    public Item carried() {
        return carried;
    }

    public int hand() {
        return hand;
    }

    public float transition() {
        return transitioning ? Maths.clamp01(transition) : 1f;
    }

    public boolean transitioning() {
        return transitioning;
    }

    public float carryTime() {
        return carryTime;
    }

    public boolean lightOn() {
        return lightOn;
    }

    public float lightRadius() {
        return lightRadius;
    }

    public float lightX() {
        return lightX;
    }

    public float lightY() {
        return lightY;
    }

    public float lightZ() {
        return lightZ;
    }

    public int lanternColorK() {
        return 2700;      /* 05.28 / 19.04 */
    }

    public List<Placed> placed() {
        return placed;
    }

    public int placedCount() {
        return placed.size();
    }

    /** Reprendre une lanterne posee (le joueur revient toujours la chercher). */
    public boolean pickUpPlaced(String itemId, float x, float y, float z, float reach) {
        for (int i = 0; i < placed.size(); i++) {
            Placed p = placed.get(i);
            if (!p.itemId.equals(itemId)) {
                continue;
            }
            float dx = p.x - x;
            float dy = p.y - y;
            float dz = p.z - z;
            if ((float) Math.sqrt(dx * dx + dy * dy + dz * dz) > reach) {
                continue;
            }
            placed.remove(i);
            return pickUp(itemId);
        }
        return false;
    }

    public int darkCrossings() {
        return darkCrossings;
    }

    public boolean inDarkCrossing() {
        return inDarkCrossing;
    }

    public float darkProgress() {
        return Maths.clamp01(darkDistance / DARK_CROSSING_M);
    }

    public float longestDark() {
        return longestDark;
    }

    public int pickups() {
        return pickups;
    }

    public int setdowns() {
        return setdowns;
    }

    public int swaps() {
        return swaps;
    }

    /** 09.14 : Lohen est accroupi 60 % du temps dans les conduits. */
    public float crouchFraction() {
        return totalTime <= 0f ? 0f : crouchTime / totalTime;
    }

    public boolean crouchFractionCompliant() {
        return Math.abs(crouchFraction() - CROUCH_TIME_FRACTION_S5) < 0.15f;
    }

    /** 08.21 : le verbe est enseigne a 01:19, sans texte (08.20). */
    public boolean taughtOnSchedule() {
        return Math.abs(TAUGHT_AT_SECONDS - 79f) < 0.001f;
    }

    /** Garde CI : pas de grappin, pas de garde, camera resserree (08.11). */
    public static boolean specCompliant() {
        return ITEMS.size() >= 4
                && DARK_CROSSINGS_S5 == 3
                && DARK_CROSSING_M == 8f
                && LIGHT_REPEL_M == 4f
                && Math.abs(CEILING_S5 - 1.4f) < 0.001f;
    }

    public void reset() {
        carried = null;
        carrying = false;
        transitioning = false;
        transition = 0f;
        swapTimer = 0f;
        carryTime = 0f;
        lightOn = false;
        lightRadius = 0f;
        placed.clear();
        darkDistance = 0f;
        inDarkCrossing = false;
        darkCrossings = 0;
        longestDark = 0f;
        currentDark = 0f;
        pickups = 0;
        setdowns = 0;
        swaps = 0;
        crouchTime = 0f;
        totalTime = 0f;
        hand = 1;
    }
}
