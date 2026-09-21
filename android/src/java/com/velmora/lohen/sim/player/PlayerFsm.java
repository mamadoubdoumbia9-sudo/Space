/*
 * LOHEN — sim/player/PlayerFsm.java
 *
 * 04.05 [OBL] : machine a etats HIERARCHIQUE et DATA-DRIVEN.
 * Racine -> {Locomotion, Traversal, Combat, Narrative}, sous-etats declares,
 * table de transitions lohen_fsm_table.
 *
 * La table est lue depuis content/tuning/lohen_fsm_table.json ; une table de
 * secours identique est fournie pour que le moteur tourne sans assets
 * (et pour que les tests JVM n'aient pas besoin du pipeline).
 */
package com.velmora.lohen.sim.player;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.MiniJson;
import com.velmora.lohen.sim.core.Tuning;
import com.velmora.lohen.sim.math.Maths;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PlayerFsm {

    /* ---------------- Etats (racine + enfants) ---------------- */
    public static final int ST_ROOT_LOCOMOTION = 0;
    public static final int ST_ROOT_TRAVERSAL = 1;
    public static final int ST_ROOT_COMBAT = 2;
    public static final int ST_ROOT_NARRATIVE = 3;

    public static final int ST_IDLE = 10;
    public static final int ST_WALK = 11;
    public static final int ST_RUN = 12;
    public static final int ST_SPRINT = 13;
    public static final int ST_STOP = 14;
    public static final int ST_JUMP = 20;
    public static final int ST_FALL = 21;
    public static final int ST_WALLRUN = 22;
    public static final int ST_LEDGE_HANG = 23;
    public static final int ST_LEDGE_SHIMMY = 24;
    public static final int ST_LEDGE_CLIMB = 25;
    public static final int ST_VAULT = 26;
    public static final int ST_SLIDE = 27;
    public static final int ST_LAND_SOFT = 28;
    public static final int ST_LAND_HEAVY = 29;
    public static final int ST_GRAPPLE_FLY = 30;
    public static final int ST_GRAPPLE_PULL = 31;
    public static final int ST_GRAPPLE_SWING = 32;
    public static final int ST_GRAPPLE_ZIP = 33;
    public static final int ST_GUARD = 40;
    public static final int ST_PARRY = 41;
    public static final int ST_STRIKE_LIGHT = 42;
    public static final int ST_STRIKE_HEAVY = 43;
    public static final int ST_DODGE = 44;
    public static final int ST_STAGGER = 45;
    public static final int ST_COLLAPSE = 46;
    public static final int ST_ECHO = 50;
    public static final int ST_DIALOGUE = 51;
    public static final int ST_CINEMATIC = 52;
    public static final int ST_CARRY = 53;
    public static final int ST_LETTER = 54;
    public static final int ST_DEATH = 60;

    private static final String[] NAMES = {
            "ROOT_LOCOMOTION", "ROOT_TRAVERSAL", "ROOT_COMBAT", "ROOT_NARRATIVE",
            "IDLE", "WALK", "RUN", "SPRINT", "STOP",
            "JUMP", "FALL", "WALLRUN", "LEDGE_HANG", "LEDGE_SHIMMY", "LEDGE_CLIMB",
            "VAULT", "SLIDE", "LAND_SOFT", "LAND_HEAVY",
            "GRAPPLE_FLY", "GRAPPLE_PULL", "GRAPPLE_SWING", "GRAPPLE_ZIP",
            "GUARD", "PARRY", "STRIKE_LIGHT", "STRIKE_HEAVY", "DODGE", "STAGGER", "COLLAPSE",
            "ECHO", "DIALOGUE", "CINEMATIC", "CARRY", "LETTER", "DEATH"};

    private static final int[] IDS = {
            ST_ROOT_LOCOMOTION, ST_ROOT_TRAVERSAL, ST_ROOT_COMBAT, ST_ROOT_NARRATIVE,
            ST_IDLE, ST_WALK, ST_RUN, ST_SPRINT, ST_STOP,
            ST_JUMP, ST_FALL, ST_WALLRUN, ST_LEDGE_HANG, ST_LEDGE_SHIMMY, ST_LEDGE_CLIMB,
            ST_VAULT, ST_SLIDE, ST_LAND_SOFT, ST_LAND_HEAVY,
            ST_GRAPPLE_FLY, ST_GRAPPLE_PULL, ST_GRAPPLE_SWING, ST_GRAPPLE_ZIP,
            ST_GUARD, ST_PARRY, ST_STRIKE_LIGHT, ST_STRIKE_HEAVY, ST_DODGE, ST_STAGGER, ST_COLLAPSE,
            ST_ECHO, ST_DIALOGUE, ST_CINEMATIC, ST_CARRY, ST_LETTER, ST_DEATH};

    public static String name(int state) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i] == state) {
                return NAMES[i];
            }
        }
        return "UNKNOWN(" + state + ")";
    }

    public static int rootOf(int state) {
        if (state >= ST_IDLE && state <= ST_STOP) {
            return ST_ROOT_LOCOMOTION;
        }
        if (state >= ST_JUMP && state <= ST_GRAPPLE_ZIP) {
            return ST_ROOT_TRAVERSAL;
        }
        if (state >= ST_GUARD && state <= ST_COLLAPSE) {
            return ST_ROOT_COMBAT;
        }
        if (state >= ST_ECHO && state <= ST_LETTER) {
            return ST_ROOT_NARRATIVE;
        }
        return ST_ROOT_LOCOMOTION;
    }

    /* ---------------- Conditions ---------------- */
    public static final int C_NONE = 0;
    public static final int C_HAS_INPUT = 1;
    public static final int C_NO_INPUT = 2;
    public static final int C_GROUNDED = 3;
    public static final int C_AIRBORNE = 4;
    public static final int C_JUMP = 5;
    public static final int C_SPEED_WALK = 6;
    public static final int C_SPEED_RUN = 7;
    public static final int C_SPEED_SPRINT = 8;
    public static final int C_LEDGE_FOUND = 9;
    public static final int C_GRAPPLE_FIRED = 10;
    public static final int C_GRAPPLE_ATTACHED = 11;
    public static final int C_GRAPPLE_RELEASED = 12;
    public static final int C_COMBAT = 13;
    public static final int C_GUARD = 14;
    public static final int C_PARRY_WINDOW = 15;
    public static final int C_ATTACK = 16;
    public static final int C_HEAVY = 17;
    public static final int C_DODGE = 18;
    public static final int C_HIT_TAKEN = 19;
    public static final int C_BREATH_BROKEN = 20;
    public static final int C_ECHO = 21;
    public static final int C_DIALOGUE = 22;
    public static final int C_CINEMATIC = 23;
    public static final int C_TIMER_DONE = 24;
    public static final int C_LANDED_SOFT = 25;
    public static final int C_LANDED_HEAVY = 26;
    public static final int C_WALL_CONTACT = 27;
    public static final int C_CARRY = 28;
    public static final int C_LETTER = 29;
    public static final int C_DEAD = 30;
    public static final int C_NOT_COMBAT = 31;
    public static final int C_NOT_CINEMATIC = 32;
    public static final int C_SLIDE = 33;
    public static final int C_VAULT = 34;
    public static final int C_STAGGER_DONE = 35;
    public static final int C_BREATH_RESTORED = 36;

    public static final class Transition {
        public int from;
        public int to;
        public int condition;
        public int priority;
        public float timer;

        public Transition(int from, int to, int condition, int priority, float timer) {
            this.from = from;
            this.to = to;
            this.condition = condition;
            this.priority = priority;
            this.timer = timer;
        }
    }

    private final List<Transition> table = new ArrayList<Transition>();
    private final Tuning tuning;
    private final EventBus bus;
    private final Lohen lohen;
    private int currentState = ST_IDLE;
    private float stateTimer;
    private float lastFallSpeed;
    private boolean ledgeFound;
    private boolean grappleFired;
    private boolean grappleReleased;
    private boolean hitTaken;
    private boolean echoRequested;
    private boolean dialogueRequested;
    private boolean cinematicRequested;
    private boolean letterRequested;
    private boolean vaultRequested;
    private boolean slideRequested;
    private int transitions;
    private String lastTransitionLog = "";

    public PlayerFsm(Lohen lohen, Tuning tuning, EventBus bus) {
        this.lohen = lohen;
        this.tuning = tuning;
        this.bus = bus;
        buildDefaultTable();
        if (bus != null) {
            bus.connect(EventBus.LEDGE_FOUND, new EventBus.Listener() {
                public void onEvent(String signal, Object[] args) {
                    ledgeFound = true;
                }
            });
            bus.connect(EventBus.GRAPPLE_ATTACHED, new EventBus.Listener() {
                public void onEvent(String signal, Object[] args) {
                    grappleFired = true;
                }
            });
            bus.connect(EventBus.GRAPPLE_RELEASED, new EventBus.Listener() {
                public void onEvent(String signal, Object[] args) {
                    grappleReleased = true;
                }
            });
            bus.connect(EventBus.PLAYER_HIT_TAKEN, new EventBus.Listener() {
                public void onEvent(String signal, Object[] args) {
                    hitTaken = true;
                }
            });
            bus.connect(EventBus.ECHO_REQUESTED, new EventBus.Listener() {
                public void onEvent(String signal, Object[] args) {
                    echoRequested = true;
                }
            });
            bus.connect(EventBus.SCENE_STARTED, new EventBus.Listener() {
                public void onEvent(String signal, Object[] args) {
                    dialogueRequested = true;
                }
            });
            bus.connect(EventBus.SCENE_ENDED, new EventBus.Listener() {
                public void onEvent(String signal, Object[] args) {
                    dialogueRequested = false;
                }
            });
            bus.connect(EventBus.CINEMATIC_STARTED, new EventBus.Listener() {
                public void onEvent(String signal, Object[] args) {
                    cinematicRequested = true;
                }
            });
            bus.connect(EventBus.CINEMATIC_ENDED, new EventBus.Listener() {
                public void onEvent(String signal, Object[] args) {
                    cinematicRequested = false;
                }
            });
            bus.connect(EventBus.LETTER_STARTED, new EventBus.Listener() {
                public void onEvent(String signal, Object[] args) {
                    letterRequested = true;
                }
            });
            bus.connect(EventBus.LETTER_ENDED, new EventBus.Listener() {
                public void onEvent(String signal, Object[] args) {
                    letterRequested = false;
                }
            });
            bus.connect(EventBus.PLAYER_DIED, new EventBus.Listener() {
                public void onEvent(String signal, Object[] args) {
                    forceState(ST_DEATH);
                }
            });
            bus.connect(EventBus.BREATH_BROKEN, new EventBus.Listener() {
                public void onEvent(String signal, Object[] args) {
                    if (lohen.inCombat) {
                        forceState(ST_COLLAPSE);
                    }
                }
            });
        }
    }

    /** Table de secours identique au JSON genere (04.05). */
    private void buildDefaultTable() {
        table.clear();
        /* Locomotion */
        add(ST_IDLE, ST_WALK, C_HAS_INPUT, 1, 0f);
        add(ST_WALK, ST_IDLE, C_NO_INPUT, 1, 0f);
        add(ST_WALK, ST_RUN, C_SPEED_RUN, 1, 0f);
        add(ST_RUN, ST_WALK, C_SPEED_WALK, 1, 0f);
        add(ST_RUN, ST_SPRINT, C_SPEED_SPRINT, 1, 0f);
        add(ST_SPRINT, ST_RUN, C_SPEED_RUN, 1, 0f);
        add(ST_SPRINT, ST_WALK, C_SPEED_WALK, 1, 0f);
        add(ST_WALK, ST_JUMP, C_JUMP, 5, 0f);
        add(ST_RUN, ST_JUMP, C_JUMP, 5, 0f);
        add(ST_SPRINT, ST_JUMP, C_JUMP, 5, 0f);
        add(ST_IDLE, ST_JUMP, C_JUMP, 5, 0f);
        add(ST_STOP, ST_IDLE, C_TIMER_DONE, 1, 0.45f);
        add(ST_RUN, ST_STOP, C_NO_INPUT, 1, 0f);
        add(ST_SPRINT, ST_STOP, C_NO_INPUT, 1, 0f);
        add(ST_WALK, ST_SLIDE, C_SLIDE, 4, 0f);
        add(ST_RUN, ST_SLIDE, C_SLIDE, 4, 0f);
        add(ST_SPRINT, ST_SLIDE, C_SLIDE, 4, 0f);
        add(ST_SLIDE, ST_WALK, C_TIMER_DONE, 1, 0.75f);
        add(ST_WALK, ST_VAULT, C_VAULT, 4, 0f);
        add(ST_RUN, ST_VAULT, C_VAULT, 4, 0f);
        add(ST_VAULT, ST_RUN, C_TIMER_DONE, 1, 0.55f);
        add(ST_CARRY, ST_WALK, C_HAS_INPUT, 1, 0f);
        add(ST_CARRY, ST_IDLE, C_NO_INPUT, 1, 0f);
        /* Traversee */
        add(ST_JUMP, ST_FALL, C_TIMER_DONE, 1, 0.25f);
        add(ST_FALL, ST_LAND_SOFT, C_LANDED_SOFT, 5, 0f);
        add(ST_FALL, ST_LAND_HEAVY, C_LANDED_HEAVY, 5, 0f);
        add(ST_FALL, ST_LEDGE_HANG, C_LEDGE_FOUND, 6, 0f);
        add(ST_FALL, ST_WALLRUN, C_WALL_CONTACT, 4, 0f);
        add(ST_LAND_SOFT, ST_IDLE, C_TIMER_DONE, 1, 0.35f);
        add(ST_LAND_SOFT, ST_RUN, C_HAS_INPUT, 2, 0.35f);
        add(ST_LAND_HEAVY, ST_IDLE, C_TIMER_DONE, 1, 0.8f);
        add(ST_WALLRUN, ST_FALL, C_TIMER_DONE, 2, 2.1f);
        add(ST_WALLRUN, ST_JUMP, C_JUMP, 5, 0f);
        add(ST_WALLRUN, ST_LAND_SOFT, C_LANDED_SOFT, 6, 0f);
        add(ST_LEDGE_HANG, ST_LEDGE_SHIMMY, C_HAS_INPUT, 1, 0f);
        add(ST_LEDGE_SHIMMY, ST_LEDGE_HANG, C_NO_INPUT, 1, 0f);
        add(ST_LEDGE_HANG, ST_LEDGE_CLIMB, C_JUMP, 5, 0f);
        add(ST_LEDGE_SHIMMY, ST_LEDGE_CLIMB, C_JUMP, 5, 0f);
        add(ST_LEDGE_CLIMB, ST_IDLE, C_TIMER_DONE, 5, 0.7f);
        add(ST_LEDGE_HANG, ST_FALL, C_JUMP, 1, 0f);
        add(ST_GRAPPLE_FLY, ST_GRAPPLE_PULL, C_TIMER_DONE, 1, 0.45f);
        add(ST_GRAPPLE_PULL, ST_GRAPPLE_SWING, C_TIMER_DONE, 1, 0.3f);
        add(ST_GRAPPLE_FLY, ST_GRAPPLE_SWING, C_GRAPPLE_ATTACHED, 2, 0f);
        add(ST_GRAPPLE_SWING, ST_FALL, C_GRAPPLE_RELEASED, 5, 0f);
        add(ST_GRAPPLE_PULL, ST_FALL, C_GRAPPLE_RELEASED, 5, 0f);
        add(ST_GRAPPLE_ZIP, ST_FALL, C_TIMER_DONE, 2, 3f);
        add(ST_GRAPPLE_ZIP, ST_LAND_SOFT, C_LANDED_SOFT, 6, 0f);
        add(ST_FALL, ST_GRAPPLE_FLY, C_GRAPPLE_FIRED, 6, 0f);
        add(ST_JUMP, ST_GRAPPLE_FLY, C_GRAPPLE_FIRED, 6, 0f);
        add(ST_IDLE, ST_GRAPPLE_FLY, C_GRAPPLE_FIRED, 6, 0f);
        add(ST_RUN, ST_GRAPPLE_FLY, C_GRAPPLE_FIRED, 6, 0f);
        add(ST_LEDGE_HANG, ST_GRAPPLE_FLY, C_GRAPPLE_FIRED, 6, 0f);
        /* Combat */
        add(ST_IDLE, ST_GUARD, C_GUARD, 7, 0f);
        add(ST_WALK, ST_GUARD, C_GUARD, 7, 0f);
        add(ST_RUN, ST_GUARD, C_GUARD, 7, 0f);
        add(ST_GUARD, ST_PARRY, C_PARRY_WINDOW, 8, 0f);
        add(ST_PARRY, ST_STRIKE_HEAVY, C_TIMER_DONE, 3, 0.22f);
        add(ST_GUARD, ST_IDLE, C_NOT_COMBAT, 1, 0f);
        add(ST_IDLE, ST_STRIKE_LIGHT, C_ATTACK, 7, 0f);
        add(ST_WALK, ST_STRIKE_LIGHT, C_ATTACK, 7, 0f);
        add(ST_RUN, ST_STRIKE_LIGHT, C_ATTACK, 7, 0f);
        add(ST_IDLE, ST_STRIKE_HEAVY, C_HEAVY, 7, 0f);
        add(ST_STRIKE_LIGHT, ST_STRIKE_LIGHT, C_ATTACK, 2, 0.42f);
        add(ST_STRIKE_LIGHT, ST_IDLE, C_TIMER_DONE, 1, 0.42f);
        add(ST_STRIKE_HEAVY, ST_IDLE, C_TIMER_DONE, 1, 1.1f);
        add(ST_IDLE, ST_DODGE, C_DODGE, 8, 0f);
        add(ST_WALK, ST_DODGE, C_DODGE, 8, 0f);
        add(ST_RUN, ST_DODGE, C_DODGE, 8, 0f);
        add(ST_SPRINT, ST_DODGE, C_DODGE, 8, 0f);
        add(ST_GUARD, ST_DODGE, C_DODGE, 8, 0f);
        add(ST_DODGE, ST_IDLE, C_TIMER_DONE, 1, 0.55f);
        add(ST_DODGE, ST_RUN, C_HAS_INPUT, 2, 0.55f);
        add(ST_IDLE, ST_STAGGER, C_HIT_TAKEN, 9, 0f);
        add(ST_WALK, ST_STAGGER, C_HIT_TAKEN, 9, 0f);
        add(ST_RUN, ST_STAGGER, C_HIT_TAKEN, 9, 0f);
        add(ST_GUARD, ST_STAGGER, C_HIT_TAKEN, 9, 0f);
        add(ST_STRIKE_LIGHT, ST_STAGGER, C_HIT_TAKEN, 9, 0f);
        add(ST_STAGGER, ST_IDLE, C_STAGGER_DONE, 1, 0.55f);
        add(ST_COLLAPSE, ST_IDLE, C_BREATH_RESTORED, 5, 3.5f);
        add(ST_COLLAPSE, ST_DEATH, C_DEAD, 10, 0f);
        add(ST_STAGGER, ST_COLLAPSE, C_BREATH_BROKEN, 10, 0f);
        /* Narratif */
        add(ST_IDLE, ST_ECHO, C_ECHO, 12, 0f);
        add(ST_WALK, ST_ECHO, C_ECHO, 12, 0f);
        add(ST_RUN, ST_ECHO, C_ECHO, 12, 0f);
        add(ST_ECHO, ST_IDLE, C_TIMER_DONE, 1, 0f);
        add(ST_IDLE, ST_DIALOGUE, C_DIALOGUE, 12, 0f);
        add(ST_WALK, ST_DIALOGUE, C_DIALOGUE, 12, 0f);
        add(ST_DIALOGUE, ST_IDLE, C_NOT_CINEMATIC, 1, 0f);
        add(ST_IDLE, ST_CINEMATIC, C_CINEMATIC, 13, 0f);
        add(ST_WALK, ST_CINEMATIC, C_CINEMATIC, 13, 0f);
        add(ST_RUN, ST_CINEMATIC, C_CINEMATIC, 13, 0f);
        add(ST_CINEMATIC, ST_IDLE, C_NOT_CINEMATIC, 1, 0f);
        add(ST_IDLE, ST_LETTER, C_LETTER, 14, 0f);
        add(ST_LETTER, ST_IDLE, C_NOT_CINEMATIC, 1, 0f);
        add(ST_IDLE, ST_CARRY, C_CARRY, 3, 0f);
    }

    private void add(int from, int to, int condition, int priority, float timer) {
        table.add(new Transition(from, to, condition, priority, timer));
    }

    /** Charge la table data-driven (04.05) ; garde les defauts si absente. */
    public void loadTable(String json) {
        List<Object> list = MiniJson.childList(MiniJson.parseObject(json), "transitions");
        if (list == null || list.isEmpty()) {
            return;
        }
        table.clear();
        for (Object o : list) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            int from = (int) MiniJson.num(m, "from", ST_IDLE);
            int to = (int) MiniJson.num(m, "to", ST_IDLE);
            int cond = (int) MiniJson.num(m, "condition", C_NONE);
            int prio = (int) MiniJson.num(m, "priority", 1);
            float timer = MiniJson.num(m, "timer", 0f);
            table.add(new Transition(from, to, cond, prio, timer));
        }
    }

    public int tableSize() {
        return table.size();
    }

    public void forceState(int state) {
        setState(state);
    }

    private void setState(int state) {
        if (currentState == state) {
            return;
        }
        int prev = currentState;
        currentState = state;
        stateTimer = 0f;
        lohen.prevState = prev;
        lohen.state = state;
        lohen.stateTime = 0f;
        transitions++;
        lastTransitionLog = name(prev) + "->" + name(state);
        if (bus != null) {
            bus.emit(EventBus.PLAYER_STATE, name(state), name(prev));
        }
        /* racine hierarchique : le contexte global change avec la racine */
        int root = rootOf(state);
        lohen.inCombat = root == ST_ROOT_COMBAT;
        if (root == ST_ROOT_NARRATIVE) {
            lohen.vx = 0f;
            lohen.vz = 0f;
        }
    }

    public int state() {
        return currentState;
    }

    public float stateTimer() {
        return stateTimer;
    }

    public int transitions() {
        return transitions;
    }

    public String lastTransition() {
        return lastTransitionLog;
    }

    /** Drapeaux d'entree poses par les composants ce tick. */
    public void requestVault() {
        vaultRequested = true;
    }

    public void requestSlide() {
        slideRequested = true;
    }

    public void requestLedge(boolean found) {
        ledgeFound = found;
    }

    public void requestGrappleRelease() {
        grappleReleased = true;
    }

    public void clearPerFrame() {
        ledgeFound = false;
        grappleFired = false;
        grappleReleased = false;
        hitTaken = false;
        echoRequested = false;
        vaultRequested = false;
        slideRequested = false;
    }

    private boolean evalCondition(int c, float dt) {
        switch (c) {
            case C_NONE:
                return true;
            case C_HAS_INPUT:
                return lohen.inputX * lohen.inputX + lohen.inputY * lohen.inputY > 0.0016f;
            case C_NO_INPUT:
                return lohen.inputX * lohen.inputX + lohen.inputY * lohen.inputY <= 0.0016f;
            case C_GROUNDED:
                return lohen.grounded;
            case C_AIRBORNE:
                return !lohen.grounded;
            case C_JUMP:
                return lohen.jumpBuffered;
            case C_SPEED_WALK:
                return lohen.speed < tuning.jogSpeed + 0.3f;
            case C_SPEED_RUN:
                return lohen.speed >= tuning.jogSpeed && lohen.speed < tuning.sprintSpeed - 0.35f;
            case C_SPEED_SPRINT:
                return lohen.speed >= tuning.sprintSpeed - 0.35f;
            case C_LEDGE_FOUND:
                return ledgeFound;
            case C_GRAPPLE_FIRED:
                return grappleFired || lohen.grappleAttached;
            case C_GRAPPLE_ATTACHED:
                return lohen.grappleAttached;
            case C_GRAPPLE_RELEASED:
                return grappleReleased || !lohen.grappleAttached;
            case C_COMBAT:
                return lohen.inCombat;
            case C_NOT_COMBAT:
                return !lohen.inCombat && !lohen.guardHeld && stateTimer > tuning.combatExitLock;
            case C_GUARD:
                return lohen.guardHeld && lohen.inCombat;
            case C_PARRY_WINDOW:
                return lohen.parryWindowOpen;
            case C_ATTACK:
                return lohen.attackBuffered;
            case C_HEAVY:
                return lohen.heavyBuffered;
            case C_DODGE:
                return lohen.dodgeBuffered;
            case C_HIT_TAKEN:
                return hitTaken;
            case C_BREATH_BROKEN:
                return lohen.breath.collapsed();
            case C_BREATH_RESTORED:
                return !lohen.breath.collapsed();
            case C_ECHO:
                return echoRequested;
            case C_DIALOGUE:
                return dialogueRequested;
            case C_CINEMATIC:
                return cinematicRequested;
            case C_NOT_CINEMATIC:
                return !cinematicRequested && !dialogueRequested && !letterRequested;
            case C_CARRY:
                return lohen.carrying;
            case C_LETTER:
                return letterRequested;
            case C_DEAD:
                return lohen.dead;
            case C_TIMER_DONE:
                return true;   /* le timer est verifie par la transition */
            case C_LANDED_SOFT:
                return lohen.grounded && lastFallSpeed < tuning.landHardM;
            case C_LANDED_HEAVY:
                return lohen.grounded && lastFallSpeed >= tuning.landHardM;
            case C_WALL_CONTACT:
                return lohen.wallContact && !lohen.grounded;
            case C_STAGGER_DONE:
                return lohen.staggerTime <= 0f;
            case C_SLIDE:
                return slideRequested && lohen.grounded && lohen.speed > tuning.jogSpeed;
            case C_VAULT:
                return vaultRequested;
            default:
                return false;
        }
    }

    public void update(float dt) {
        stateTimer += dt;
        lastFallSpeed = -lohen.vy;
        /* recherche de la meilleure transition par priorite decroissante */
        Transition best = null;
        for (int i = 0; i < table.size(); i++) {
            Transition t = table.get(i);
            if (t.from != currentState) {
                continue;
            }
            if (t.timer > 0f && stateTimer < t.timer) {
                continue;
            }
            if (!evalCondition(t.condition, dt)) {
                continue;
            }
            if (t.to == currentState && t.condition != C_ATTACK) {
                continue;   /* pas d'auto-transition sauf combo */
            }
            if (best == null || t.priority > best.priority) {
                best = t;
            }
        }
        if (best != null) {
            int target = best.to;
            setState(target);
        }
        lohen.state = currentState;
        lohen.stateTime = stateTimer;
        /* garde-fou : un etat narratif ne doit jamais laisser la physique agir */
        if (rootOf(currentState) == ST_ROOT_NARRATIVE) {
            lohen.vy = 0f;
        }
    }

    /** Fin explicite d'un Echo (11.15 : le souffle n'est PAS rendu). */
    public void endEcho() {
        if (currentState == ST_ECHO) {
            setState(ST_IDLE);
        }
    }

    public void beginEcho() {
        setState(ST_ECHO);
    }

    public void beginDialogue() {
        dialogueRequested = true;
        setState(ST_DIALOGUE);
    }

    public void beginCinematic() {
        cinematicRequested = true;
        setState(ST_CINEMATIC);
    }

    public void beginLetter() {
        letterRequested = true;
        setState(ST_LETTER);
    }

    public boolean isNarrative() {
        return rootOf(currentState) == ST_ROOT_NARRATIVE;
    }

    public boolean isTraversal() {
        return rootOf(currentState) == ST_ROOT_TRAVERSAL;
    }

    public float stateProgress(float duration) {
        return duration <= 0f ? 1f : Maths.clamp01(stateTimer / duration);
    }
}
