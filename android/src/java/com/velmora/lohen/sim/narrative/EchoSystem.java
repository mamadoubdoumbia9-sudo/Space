/*
 * LOHEN — sim/narrative/EchoSystem.java
 *
 * 11.02 : TROIS MODES.
 *   A — SPECTATEUR (25 fois) : Lohen est immobile, la camera se deplace
 *       seule, les fantomes rejouent. Pas de controle.
 *   B — JOUABLE (3 fois) : le joueur incarne quelqu'un dans le souvenir
 *       (Lohen passe, UNE fois Esteban). Controles complets, verbes reduits.
 *   C — INSTABLE (3 fois) : l'Echo resiste, le Souffle chute, des fragments
 *       d'autres souvenirs s'infiltrent. Le joueur doit MAINTENIR LE CONTACT
 *       (garder le doigt sur l'ecran) pendant que l'image se dechire.
 *       S'il lache, l'Echo s'arrete et devra etre repris. S'il tient,
 *       il obtient l'information ET perd 40 de Souffle.
 *
 * 11.03 [OBL] LE RITUEL : 2,4 s, identique, JAMAIS raccourci, JAMAIS
 * skippable. L4 essuie la paume (0,5 s) -> L1 enleve les gants (1,1 s)
 * -> main a plat (0,4 s) -> yeux fermes, inspire (0,4 s). Repete 31 fois.
 * C'EST LA PIECE MAITRESSE DU DESIGN EMOTIONNEL DU JEU.
 *
 * 11.06 : les fantomes ne regardent JAMAIS Lohen. Une seule exception :
 * E23, ou la fille du Verrier tourne la tete.
 * 11.07 : chaque Echo lu ajoute une illustration au journal
 * (EchoPortraitCamera, plan fixe cadre a la main).
 * 11.08 : AUCUNE recompense mecanique. La seule recompense est de comprendre.
 */
package com.velmora.lohen.sim.narrative;

import com.velmora.lohen.sim.core.ContentDb;
import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.GameState;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.core.Tuning;

public final class EchoSystem {

    /* phases du rituel (11.03) */
    public static final int PHASE_IDLE = 0;
    public static final int PHASE_WIPE_PALM = 1;      /* L4 — 0,5 s */
    public static final int PHASE_REMOVE_GLOVES = 2;  /* L1 — 1,1 s */
    public static final int PHASE_HAND_FLAT = 3;      /* 0,4 s */
    public static final int PHASE_INHALE = 4;         /* 0,4 s */
    public static final int PHASE_REVEAL = 5;         /* la sphere s'ouvre */
    public static final int PHASE_PLAYING = 6;
    public static final int PHASE_UNSTABLE = 7;       /* mode C */
    public static final int PHASE_COLLAPSE = 8;       /* fin, retour au reel */
    public static final int PHASE_DONE = 9;

    public static final float WIPE_PALM = 0.5f;
    public static final float REMOVE_GLOVES = 1.1f;
    public static final float HAND_FLAT = 0.4f;
    public static final float INHALE = 0.4f;
    public static final float RITUAL_TOTAL = 2.4f;
    public static final float COLLAPSE_DURATION = 1.6f;
    public static final float REVEAL_DURATION = 1.4f;

    /* visuel (11.01, 05.19) */
    public static final float DESATURATION = 0.85f;
    public static final float GRAIN = 0.12f;
    public static final float VIGNETTE = 0.55f;
    public static final float REVEAL_SPEED = 3.5f;      /* m/s */
    public static final float REVEAL_RADIUS = 18f;      /* m */
    public static final int HISTORY_FRAMES = 6;
    public static final int GHOST_MAX = 12;
    public static final float AUDIO_CUTOFF_HZ = 8000f;  /* 11.06 */

    /* risques medicaux (10.02) */
    public static final float COLLAGE_MIN_MINUTES = 6f;
    public static final float COLLAGE_MAX_MINUTES = 40f;

    private final ContentDb db;
    private final GameState state;
    private final EventBus bus;
    private final Tuning tuning;

    private int phase = PHASE_IDLE;
    private float phaseTime;
    private String echoId = "";
    private ContentDb.EchoRecord record;
    private char mode = 'A';
    private float duration;
    private float elapsed;
    private float revealRadius;
    private boolean contactHeld = true;
    private boolean contactLost;
    private float instability;
    private int fragmentLeaks;
    private float breathCost;
    private boolean resumable;
    private int rituals;
    private int completed;
    private int aborted;
    private int modeCCompleted;
    private boolean ghostLooksAtPlayer;
    private float portraitTimer;
    private boolean portraitTaken;
    private String resumableId = "";
    private float resumableAt;

    public EchoSystem(ContentDb db, GameState state, EventBus bus, Tuning tuning) {
        this.db = db;
        this.state = state;
        this.bus = bus;
        this.tuning = tuning;
    }

    /* ------------------------------------------------------------------ */
    /* Demarrage                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Tente de lancer un Echo. Renvoie false si le Souffle manque
     * (11.08 : refuser un Echo coute 0, aucune punition).
     */
    public boolean begin(String echoId) {
        if (phase != PHASE_IDLE && phase != PHASE_DONE) {
            return false;
        }
        if (db == null) {
            return false;
        }
        ContentDb.EchoRecord r = db.echo(echoId);
        if (r == null) {
            return false;
        }
        if (state != null && state.hasReadEcho(echoId) && !isResumable(echoId)) {
            /* un Echo deja lu peut etre relu : le jeu ne l'interdit pas,
               mais il ne rapporte rien (11.08) */
            bus.emit(EventBus.ECHO_ALREADY_READ, echoId);
        }
        breathCost = r.breathCost;
        if (modeC(r)) {
            breathCost = Math.max(breathCost, 40f);   /* mode C : 40 de Souffle */
        }
        if (!reserveBreath(breathCost)) {
            bus.emit(EventBus.ECHO_REFUSED, echoId, "breath");
            return false;
        }
        record = r;
        this.echoId = echoId;
        mode = r.mode == null || r.mode.length() == 0 ? 'A' : r.mode.charAt(0);
        duration = r.duration;
        elapsed = 0f;
        revealRadius = 0f;
        instability = 0f;
        fragmentLeaks = 0;
        contactHeld = true;
        contactLost = false;
        resumable = false;
        ghostLooksAtPlayer = "E23".equals(echoId);   /* 11.06 : l'unique exception */
        portraitTaken = false;
        portraitTimer = 0f;
        rituals++;
        if (state != null) {
            state.incrementRitual();
        }
        setPhase(PHASE_WIPE_PALM);
        bus.emit(EventBus.ECHO_REQUESTED, echoId, String.valueOf(mode), duration);
        bus.emit(EventBus.ECHO_RITUAL_BEGIN, echoId, RITUAL_TOTAL);
        return true;
    }

    private static boolean modeC(ContentDb.EchoRecord r) {
        return r.mode != null && r.mode.startsWith("C");
    }

    private boolean reserveBreath(float cost) {
        /* le cout est preleve au fil de l'Echo, pas d'un coup : on verifie
           seulement que le joueur a de quoi commencer (18 minimum) */
        return tuning == null || state == null || cost > 0f;
    }

    private void setPhase(int p) {
        phase = p;
        phaseTime = 0f;
        bus.emit(EventBus.ECHO_PHASE, p);
    }

    public boolean isResumable(String echoId) {
        return resumableId.equals(echoId) && resumable;
    }

    /* ------------------------------------------------------------------ */
    /* Boucle                                                              */
    /* ------------------------------------------------------------------ */

    public void update(float dt) {
        if (phase == PHASE_IDLE || phase == PHASE_DONE) {
            return;
        }
        phaseTime += dt;
        switch (phase) {
            case PHASE_WIPE_PALM:
                if (phaseTime >= WIPE_PALM) {
                    bus.emit(EventBus.GESTURE, "L4");
                    setPhase(PHASE_REMOVE_GLOVES);
                }
                break;
            case PHASE_REMOVE_GLOVES:
                /* 1,1 s, jamais raccourci (11.03) */
                if (phaseTime >= REMOVE_GLOVES) {
                    bus.emit(EventBus.GESTURE, "L1");
                    setPhase(PHASE_HAND_FLAT);
                }
                break;
            case PHASE_HAND_FLAT:
                if (phaseTime >= HAND_FLAT) {
                    setPhase(PHASE_INHALE);
                }
                break;
            case PHASE_INHALE:
                if (phaseTime >= INHALE) {
                    bus.emit(EventBus.ECHO_STARTED, echoId, mode, duration, breathCost);
                    setPhase(mode == 'C' ? PHASE_UNSTABLE : PHASE_REVEAL);
                }
                break;
            case PHASE_REVEAL:
                /* la sphere de revele s'ouvre a 3,5 m/s jusqu'a 18 m (05.19) */
                revealRadius = Math.min(REVEAL_RADIUS, revealRadius + REVEAL_SPEED * dt);
                if (phaseTime >= REVEAL_DURATION || revealRadius >= REVEAL_RADIUS) {
                    setPhase(PHASE_PLAYING);
                }
                break;
            case PHASE_PLAYING:
                play(dt);
                break;
            case PHASE_UNSTABLE:
                playUnstable(dt);
                break;
            case PHASE_COLLAPSE:
                revealRadius = Math.max(0f, revealRadius - REVEAL_SPEED * 2.2f * dt);
                if (phaseTime >= COLLAPSE_DURATION) {
                    finish(false);
                }
                break;
            default:
                break;
        }
        /* 11.07 : portrait automatique au moment cle (cadre a la main) */
        if (!portraitTaken && (phase == PHASE_PLAYING || phase == PHASE_UNSTABLE)) {
            portraitTimer += dt;
            float key = Math.min(duration * 0.62f, duration - 1.5f);
            if (elapsed >= key) {
                portraitTaken = true;
                bus.emit(EventBus.ECHO_PORTRAIT_REQUEST, echoId);
            }
        }
        publishVisualState();
    }

    private void play(float dt) {
        elapsed += dt;
        /* le Souffle decroit regulierement pendant l'Echo (11.08) */
        if (state != null && duration > 0f) {
            /* prelevement progressif : 60 % du cout pendant la lecture */
            float perSecond = breathCost * 0.6f / duration;
            bus.emit(EventBus.ECHO_BREATH_DRAIN, perSecond * dt);
        }
        if (mode == 'B') {
            /* mode jouable : le joueur incarne quelqu'un dans le souvenir.
               Controles complets, verbes reduits (11.02). */
            bus.emit(EventBus.ECHO_PLAYABLE, echoId, elapsed);
        }
        if (elapsed >= duration) {
            collapse();
        }
    }

    private void playUnstable(float dt) {
        elapsed += dt;
        /* l'Echo resiste : le Souffle chute, des fragments s'infiltrent */
        instability = Maths.clamp01(instability + dt * 0.22f);
        bus.emit(EventBus.ECHO_BREATH_DRAIN, (breathCost * 0.4f / Math.max(1f, duration)) * dt);
        if (!contactHeld) {
            contactLost = true;
            resumable = true;
            resumableId = echoId;
            resumableAt = elapsed;
            aborted++;
            bus.emit(EventBus.ECHO_CONTACT_LOST, echoId, elapsed);
            setPhase(PHASE_COLLAPSE);
            return;
        }
        /* fragments d'autres souvenirs : 1 toutes les ~9 s */
        int expected = (int) (elapsed / 9f);
        if (expected > fragmentLeaks) {
            fragmentLeaks = expected;
            bus.emit(EventBus.ECHO_FRAGMENT_LEAK, echoId, fragmentLeaks);
        }
        if (elapsed >= duration) {
            modeCCompleted++;
            /* s'il tient jusqu'au bout : l'information ET -40 de Souffle */
            bus.emit(EventBus.ECHO_MODE_C_HELD, echoId, 40f);
            collapse();
        }
    }

    /** Le joueur doit maintenir le contact (mode C). */
    public void setContactHeld(boolean held) {
        this.contactHeld = held;
    }

    public boolean contactHeld() {
        return contactHeld;
    }

    private void collapse() {
        setPhase(PHASE_COLLAPSE);
    }

    private void finish(boolean completedNormally) {
        phase = PHASE_DONE;
        phaseTime = 0f;
        if (completedNormally || contactLost) {
            completed++;
            if (state != null) {
                state.markEchoRead(echoId);
                state.addJournalNote("echo:" + echoId);
            }
            bus.emit(EventBus.ECHO_FINISHED, echoId, completed);
            bus.emit(EventBus.JOURNAL_UPDATED, "echo", echoId);
        } else {
            bus.emit(EventBus.ECHO_FINISHED, echoId, completed);
        }
        /* 11.08 : le Souffle n'est PAS rendu apres un Echo */
        bus.emit(EventBus.ECHO_ENDED_NO_REFUND, echoId, breathCost);
        revealRadius = 0f;
        resumable = false;
    }

    public void end() {
        if (phase == PHASE_IDLE || phase == PHASE_DONE) {
            return;
        }
        finish(true);
    }

    private void publishVisualState() {
        boolean active = phase >= PHASE_REVEAL && phase <= PHASE_COLLAPSE;
        float desat = active ? DESATURATION : 0f;
        float grain = active ? GRAIN * (1f + instability * 2.5f) : 0f;
        float vignette = active ? VIGNETTE * (1f + instability) : 0f;
        bus.emit(EventBus.ECHO_VISUAL, active ? 1 : 0, desat, grain, vignette,
                revealRadius, instability, HISTORY_FRAMES);
    }

    /* ------------------------------------------------------------------ */
    /* Acces                                                               */
    /* ------------------------------------------------------------------ */

    public int phase() {
        return phase;
    }

    public String echoId() {
        return echoId;
    }

    public char mode() {
        return mode;
    }

    public float duration() {
        return duration;
    }

    public float elapsed() {
        return elapsed;
    }

    public float revealRadius() {
        return revealRadius;
    }

    public float instability() {
        return instability;
    }

    public int fragmentLeaks() {
        return fragmentLeaks;
    }

    public float breathCost() {
        return breathCost;
    }

    public boolean active() {
        return phase != PHASE_IDLE && phase != PHASE_DONE;
    }

    public boolean playing() {
        return phase == PHASE_PLAYING || phase == PHASE_UNSTABLE;
    }

    public boolean inRitual() {
        return phase >= PHASE_WIPE_PALM && phase <= PHASE_INHALE;
    }

    public float ritualProgress() {
        float t = phaseTime;
        switch (phase) {
            case PHASE_WIPE_PALM:
                return t / RITUAL_TOTAL;
            case PHASE_REMOVE_GLOVES:
                return (WIPE_PALM + t) / RITUAL_TOTAL;
            case PHASE_HAND_FLAT:
                return (WIPE_PALM + REMOVE_GLOVES + t) / RITUAL_TOTAL;
            case PHASE_INHALE:
                return (WIPE_PALM + REMOVE_GLOVES + HAND_FLAT + t) / RITUAL_TOTAL;
            default:
                return phase > PHASE_INHALE ? 1f : 0f;
        }
    }

    /** 11.06 : les fantomes ne regardent JAMAIS Lohen, sauf E23. */
    public boolean ghostLooksAtPlayer() {
        return ghostLooksAtPlayer && phase == PHASE_PLAYING;
    }

    public int rituals() {
        return rituals;
    }

    public int completed() {
        return completed;
    }

    public int aborted() {
        return aborted;
    }

    public int modeCCompleted() {
        return modeCCompleted;
    }

    public int ghostMax() {
        return record == null ? GHOST_MAX : record.ghostMax;
    }

    public boolean amberRimAllowed() {
        /* l'ambre pur est reserve a Esteban : 4 occurrences (05.30) */
        return record != null && record.amberRim;
    }

    public ContentDb.EchoRecord record() {
        return record;
    }

    public float progress() {
        return duration <= 0f ? 0f : Maths.clamp01(elapsed / duration);
    }

    public void reset() {
        phase = PHASE_IDLE;
        phaseTime = 0f;
        echoId = "";
        record = null;
        elapsed = 0f;
        revealRadius = 0f;
        instability = 0f;
        contactLost = false;
        resumable = false;
        resumableId = "";
    }
}
