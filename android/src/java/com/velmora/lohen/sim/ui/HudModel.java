/*
 * LOHEN — sim/ui/HudModel.java
 *
 * 14.02 ELEMENTS DU HUD (et rien d'autre)
 *  - Jauge de Souffle : un arc de 90 deg en bas a gauche, autour du
 *    joystick. Invisible quand pleine (opacite 0). Apparait en 0,2 s des
 *    que la valeur descend sous 97 %. Couleur : blanc casse -> rouge sourd
 *    (#A8442F) sous 30 %. Jamais de chiffres.
 *  - Boussole verticale : une fine ligne a droite indiquant l'altitude
 *    actuelle et l'altitude de l'objectif. C'est la SEULE aide de
 *    navigation. Pas de mini-carte. Pas de marqueur 3D.
 *  - Prompts contextuels : glyphe seul, 44 dp, fade 0,15 s.
 *  - Sous-titres.
 *  C'est tout. Quatre elements.
 *
 * 14.01 : le HUD doit pouvoir DISPARAITRE. Mode « HUD minimal » accessible
 * en un geste (double-tap a deux doigts). Dans ce mode, seuls les prompts
 * contextuels restent. 30 % des joueurs y passeront.
 * 14.03 [OBL] PAS DE MINI-CARTE. Jamais une fleche.
 * 14.07 : tout est en ease_out_quint, 180 ms. Rien n'apparait
 * instantanement, rien ne dure plus de 250 ms. Pas de rebond. Pas de
 * slide-in depuis l'exterieur de l'ecran (sauf le journal).
 * 14.11 ECRAN DE MORT : pas de « GAME OVER ». Un fondu au noir de 1,2 s,
 * puis la reprise directe. Aucun texte. Aucun compteur de morts.
 * 14.10 CHARGEMENT : pas de barre de progression. Un dessin au trait de la
 * ville qui se complete. Plus le chargement est long, plus il y a de
 * details.
 * 08.20 [OBL] AUCUN TUTORIEL TEXTUEL. Le prompt de bouton apparait UNE fois
 * par verbe, en glyphe seul, sans phrase, et ne revient jamais (sauf si le
 * joueur echoue 3 fois).
 */
package com.velmora.lohen.sim.ui;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Options;
import com.velmora.lohen.sim.math.Maths;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HudModel implements EventBus.Listener {

    /* ---------------- 14.02 : les quatre elements ---------------- */
    public static final int ELEMENT_COUNT = 4;
    public static final int EL_BREATH = 0;
    public static final int EL_COMPASS = 1;
    public static final int EL_PROMPT = 2;
    public static final int EL_SUBTITLES = 3;

    /* ---------------- jauge de Souffle ---------------- */
    public static final float BREATH_ARC_DEG = 90f;
    public static final float BREATH_APPEAR_THRESHOLD = 0.97f;
    public static final float BREATH_APPEAR_SECONDS = 0.2f;
    public static final float BREATH_RED_THRESHOLD = 0.30f;
    public static final int COLOR_OFFWHITE = 0xEDE6DA;
    public static final int COLOR_BREATH_RED = 0xA8442F;      /* rouge sourd */
    public static final boolean BREATH_SHOWS_NUMBERS = false;

    /* ---------------- boussole verticale ---------------- */
    public static final boolean MINIMAP = false;              /* 14.03 [OBL] */
    public static final boolean MARKER_3D = false;
    public static final boolean DIRECTION_ARROW = false;      /* jamais une fleche */
    public static final float COMPASS_WIDTH_DP = 2f;

    /* ---------------- prompts ---------------- */
    public static final float PROMPT_SIZE_DP = 44f;
    public static final float PROMPT_FADE_SECONDS = 0.15f;
    public static final int PROMPT_MAX_FAILURES_BEFORE_REPEAT = 3;
    public static final boolean PROMPT_SHOWS_TEXT = false;    /* glyphe seul */

    /* ---------------- 14.07 : animation d'UI ---------------- */
    public static final float UI_ANIM_SECONDS = 0.18f;        /* 180 ms */
    public static final float UI_MAX_ANIM_SECONDS = 0.25f;
    public static final boolean UI_BOUNCE = false;
    public static final boolean UI_SLIDE_IN = false;          /* sauf le journal */

    /* ---------------- 14.11 : mort ---------------- */
    public static final float DEATH_FADE_SECONDS = 1.2f;
    public static final boolean DEATH_SHOWS_TEXT = false;
    public static final boolean DEATH_COUNTS = false;

    /* ---------------- 14.01 : modes ---------------- */
    public static final int MODE_FULL = 0;
    public static final int MODE_MINIMAL = 1;
    public static final int MODE_NONE = 2;
    public static final int TWO_FINGER_TAPS_FOR_MINIMAL = 2;

    /* ---------------- les verbes enseignables (08.21) ---------------- */
    public static final String[] VERBS = {
            "marcher", "camera", "sauter", "grimper", "interagir", "ecouter",
            "grappin", "pendule", "parade", "porter", "wallrun", "zipline",
            "donner",
    };
    /** Glyphes : jamais une phrase (08.20). */
    public static final String[] VERB_GLYPHS = {
            "g_move", "g_camera", "g_jump", "g_climb", "g_interact", "g_echo",
            "g_grapple", "g_swing", "g_parry", "g_carry", "g_wallrun", "g_zip",
            "g_give",
    };

    /* ------------------------------------------------------------------ */
    /* Etat                                                                */
    /* ------------------------------------------------------------------ */

    private final Options options;
    private final EventBus bus;

    private int mode = MODE_FULL;
    private boolean minimalByGesture;

    private float breathValue = 1f;
    private float breathOpacity;
    private int breathColor = COLOR_OFFWHITE;
    private boolean breathVisible;

    private float altitude;
    private float objectiveAltitude;
    private float compassOpacity;
    private boolean compassVisible;

    private String promptGlyph = "";
    private String promptVerb = "";
    private float promptOpacity;
    private boolean promptVisible;
    private final Map<String, Boolean> promptShownOnce = new LinkedHashMap<String, Boolean>();
    private final Map<String, Integer> verbFailures = new LinkedHashMap<String, Integer>();

    private boolean subtitlesVisible;
    private float deathFade;
    private boolean dying;
    private float loadProgress;
    private int loadDetailLevel;
    private boolean loading;
    private int elementsDrawn;
    private float time;

    public HudModel(Options options, EventBus bus) {
        this.options = options;
        this.bus = bus;
        if (options != null) {
            mode = options.hudMode;
        }
        for (String v : VERBS) {
            promptShownOnce.put(v, Boolean.FALSE);
            verbFailures.put(v, Integer.valueOf(0));
        }
        if (bus != null) {
            bus.connect(EventBus.BREATH_CHANGED, this);
            bus.connect(EventBus.PLAYER_DIED, this);
            bus.connect(EventBus.PROMPT_REQUESTED, this);
            bus.connect(EventBus.VERB_USED, this);
            bus.connect(EventBus.OBJECTIVE_CHANGED, this);
            bus.connect(EventBus.SUBTITLE, this);
            bus.connect(EventBus.HUD_TOGGLE, this);
            bus.connect(EventBus.LOADING_PROGRESS, this);
            bus.connect(EventBus.OPTIONS_CHANGED, this);
            bus.connect(EventBus.CINEMATIC_STARTED, this);
            bus.connect(EventBus.CINEMATIC_ENDED, this);
        }
    }

    /* ------------------------------------------------------------------ */
    /* EventBus                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    public void onEvent(String signal, Object[] args) {
        if (EventBus.BREATH_CHANGED.equals(signal)) {
            setBreath(num(args, 0), num(args, 1));
        } else if (EventBus.PLAYER_DIED.equals(signal)) {
            beginDeath();
        } else if (EventBus.PROMPT_REQUESTED.equals(signal)) {
            showPrompt(str(args, 0));
        } else if (EventBus.VERB_USED.equals(signal)) {
            onVerbUsed(str(args, 0));
        } else if (EventBus.OBJECTIVE_CHANGED.equals(signal)) {
            /* l'altitude de l'objectif est posee par le jeu, pas par le HUD */
            compassVisible = true;
        } else if (EventBus.SUBTITLE.equals(signal)) {
            subtitlesVisible = true;
        } else if (EventBus.HUD_TOGGLE.equals(signal)) {
            toggleByGesture();
        } else if (EventBus.LOADING_PROGRESS.equals(signal)) {
            setLoading(num(args, 0));
        } else if (EventBus.OPTIONS_CHANGED.equals(signal)) {
            applyOptions();
        } else if (EventBus.CINEMATIC_STARTED.equals(signal)) {
            /* pendant une cinematique, le HUD disparait completement */
            compassVisible = false;
            promptVisible = false;
        } else if (EventBus.CINEMATIC_ENDED.equals(signal)) {
            compassVisible = true;
        }
    }

    private static String str(Object[] args, int i) {
        return args != null && i < args.length && args[i] != null ? String.valueOf(args[i]) : "";
    }

    private static float num(Object[] args, int i) {
        if (args == null || i >= args.length || !(args[i] instanceof Number)) {
            return 0f;
        }
        return ((Number) args[i]).floatValue();
    }

    public void applyOptions() {
        if (options != null) {
            mode = Maths.clamp(options.hudMode, MODE_FULL, MODE_NONE);
            minimalByGesture = false;
        }
    }

    /* ------------------------------------------------------------------ */
    /* 14.01 : le HUD disparait en un geste                                */
    /* ------------------------------------------------------------------ */

    /** Double-tap a deux doigts : bascule complet <-> minimal. */
    public boolean toggleByGesture() {
        if (mode == MODE_NONE) {
            return false;
        }
        minimalByGesture = mode != MODE_MINIMAL;
        mode = minimalByGesture ? MODE_MINIMAL : MODE_FULL;
        if (options != null) {
            options.hudMode = mode;
        }
        if (bus != null) {
            bus.emit(EventBus.HUD_MODE_CHANGED, mode, minimalByGesture);
        }
        return true;
    }

    public void setMode(int m) {
        mode = Maths.clamp(m, MODE_FULL, MODE_NONE);
        if (options != null) {
            options.hudMode = mode;
        }
    }

    public int mode() {
        return mode;
    }

    public boolean minimal() {
        return mode == MODE_MINIMAL;
    }

    /** En mode minimal, seuls les prompts contextuels restent (14.01). */
    public boolean elementVisible(int element) {
        if (mode == MODE_NONE) {
            return false;
        }
        if (mode == MODE_MINIMAL) {
            return element == EL_PROMPT || (element == EL_SUBTITLES && subtitlesVisible);
        }
        switch (element) {
            case EL_BREATH:
                return breathVisible;
            case EL_COMPASS:
                return compassVisible;
            case EL_PROMPT:
                return promptVisible;
            case EL_SUBTITLES:
                return subtitlesVisible;
            default:
                return false;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Jauge de Souffle                                                    */
    /* ------------------------------------------------------------------ */

    public void setBreath(float current, float max) {
        breathValue = max <= 0f ? 1f : Maths.clamp01(current / max);
        /* invisible quand pleine : opacite 0 (14.02) */
        boolean shouldShow = breathValue < BREATH_APPEAR_THRESHOLD;
        breathVisible = shouldShow;
        breathColor = breathValue < BREATH_RED_THRESHOLD ? COLOR_BREATH_RED : COLOR_OFFWHITE;
    }

    /** Arc de 90 deg, plein = 1. */
    public float breathArcDegrees() {
        return BREATH_ARC_DEG * breathValue;
    }

    public float breathOpacity() {
        return breathOpacity;
    }

    public int breathColor() {
        return breathColor;
    }

    public boolean breathShowsNumbers() {
        return BREATH_SHOWS_NUMBERS;
    }

    /* ------------------------------------------------------------------ */
    /* Boussole verticale (14.02) — la seule aide de navigation            */
    /* ------------------------------------------------------------------ */

    public void setAltitudes(float current, float objective) {
        altitude = current;
        objectiveAltitude = objective;
        compassVisible = mode == MODE_FULL;
    }

    /** Position du curseur d'altitude sur la ligne, 0 (bas) a 1 (haut). */
    public float compassPlayerT() {
        return Maths.clamp01(altitude / 212f);
    }

    public float compassObjectiveT() {
        return Maths.clamp01(objectiveAltitude / 212f);
    }

    public float compassDelta() {
        return objectiveAltitude - altitude;
    }

    public boolean compassVisible() {
        return compassVisible && mode == MODE_FULL;
    }

    public boolean hasMinimap() {
        return MINIMAP;
    }

    /* ------------------------------------------------------------------ */
    /* Prompts contextuels (08.20, 14.02)                                  */
    /* ------------------------------------------------------------------ */

    /**
     * Le prompt apparait UNE fois par verbe, en glyphe seul. Il ne revient
     * jamais, sauf si le joueur echoue 3 fois sur ce verbe.
     */
    public boolean showPrompt(String verb) {
        if (verb == null || verb.isEmpty()) {
            return false;
        }
        Boolean shown = promptShownOnce.get(verb);
        Integer failures = verbFailures.get(verb);
        int fails = failures == null ? 0 : failures.intValue();
        if (shown != null && shown.booleanValue() && fails < PROMPT_MAX_FAILURES_BEFORE_REPEAT) {
            return false;
        }
        if (options != null && options.prompts == 2) {
            return false;                     /* jamais */
        }
        promptGlyph = glyphFor(verb);
        promptVerb = verb;
        promptVisible = true;
        promptShownOnce.put(verb, Boolean.TRUE);
        verbFailures.put(verb, Integer.valueOf(0));
        if (bus != null) {
            bus.emit(EventBus.PROMPT_SHOWN, verb, promptGlyph);
        }
        return true;
    }

    public static String glyphFor(String verb) {
        for (int i = 0; i < VERBS.length; i++) {
            if (VERBS[i].equals(verb)) {
                return VERB_GLYPHS[i];
            }
        }
        return "g_interact";
    }

    /** Le joueur a reussi le verbe : on retire le prompt. */
    public void onVerbUsed(String verb) {
        if (verb != null && verb.equals(promptVerb)) {
            promptVisible = false;
        }
    }

    /** Le joueur a echoue sur ce verbe : au 3e echec, le prompt revient. */
    public void noteVerbFailure(String verb) {
        Integer f = verbFailures.get(verb);
        int n = (f == null ? 0 : f.intValue()) + 1;
        verbFailures.put(verb, Integer.valueOf(n));
        if (n >= PROMPT_MAX_FAILURES_BEFORE_REPEAT) {
            promptShownOnce.put(verb, Boolean.FALSE);
            showPrompt(verb);
        }
    }

    public String promptGlyph() {
        return promptGlyph;
    }

    public float promptOpacity() {
        return promptOpacity;
    }

    public boolean promptVisible() {
        return promptVisible && mode != MODE_NONE;
    }

    /* ------------------------------------------------------------------ */
    /* Sous-titres                                                         */
    /* ------------------------------------------------------------------ */

    public boolean subtitlesVisible() {
        if (options != null && !options.subtitles) {
            return false;
        }
        return subtitlesVisible && mode != MODE_NONE;
    }

    public void setSubtitlesVisible(boolean v) {
        subtitlesVisible = v;
    }

    /* ------------------------------------------------------------------ */
    /* 14.11 : la mort                                                     */
    /* ------------------------------------------------------------------ */

    public void beginDeath() {
        dying = true;
        deathFade = 0f;
        if (bus != null) {
            bus.emit(EventBus.DEATH_FADE_BEGIN, DEATH_FADE_SECONDS);
        }
    }

    /** Fondu au noir de 1,2 s, puis reprise directe. Aucun texte. */
    public float deathFade() {
        return deathFade;
    }

    public boolean dying() {
        return dying;
    }

    public boolean deathFinished() {
        return dying && deathFade >= 1f;
    }

    public void endDeath() {
        dying = false;
        deathFade = 0f;
        if (bus != null) {
            bus.emit(EventBus.DEATH_FADE_ENDED);
        }
    }

    /* ------------------------------------------------------------------ */
    /* 14.10 : le chargement                                               */
    /* ------------------------------------------------------------------ */

    public void beginLoading() {
        loading = true;
        loadProgress = 0f;
        loadDetailLevel = 0;
    }

    public void setLoading(float progress) {
        loadProgress = Maths.clamp01(progress);
        /* plus le chargement est long, plus il y a de details : les joueurs
         * avec un vieux telephone voient le plus beau dessin (14.10). */
        loadDetailLevel = (int) (loadProgress * 6f);
    }

    public void endLoading() {
        loading = false;
        if (bus != null) {
            bus.emit(EventBus.LOADING_ENDED, loadDetailLevel);
        }
    }

    public boolean loading() {
        return loading;
    }

    public float loadProgress() {
        return loadProgress;
    }

    /** Niveau de detail du dessin au trait, 0 a 6. */
    public int loadDetailLevel() {
        return loadDetailLevel;
    }

    public boolean hasProgressBar() {
        return false;                 /* 14.10 : pas de barre */
    }

    /* ------------------------------------------------------------------ */
    /* Boucle                                                              */
    /* ------------------------------------------------------------------ */

    public void update(float dt) {
        time += dt;
        float speed = dt / UI_ANIM_SECONDS;      /* 14.07 : 180 ms */
        breathOpacity = Maths.moveTowards(breathOpacity, breathVisible ? 1f : 0f,
                dt / BREATH_APPEAR_SECONDS);
        compassOpacity = Maths.moveTowards(compassOpacity, compassVisible() ? 1f : 0f, speed);
        promptOpacity = Maths.moveTowards(promptOpacity, promptVisible ? 1f : 0f,
                dt / PROMPT_FADE_SECONDS);
        if (promptOpacity <= 0f && !promptVisible) {
            promptGlyph = "";
        }
        if (dying) {
            deathFade = Math.min(1f, deathFade + dt / DEATH_FADE_SECONDS);
        }
        elementsDrawn = 0;
        for (int i = 0; i < ELEMENT_COUNT; i++) {
            if (elementVisible(i)) {
                elementsDrawn++;
            }
        }
    }

    /** ease_out_quint (14.07) — fourni au renderer pour ses propres tweens. */
    public static float ease(float t) {
        return Maths.easeOutQuint(Maths.clamp01(t));
    }

    /* ------------------------------------------------------------------ */
    /* Acces                                                               */
    /* ------------------------------------------------------------------ */

    public float breathValue() {
        return breathValue;
    }

    public float altitude() {
        return altitude;
    }

    public float objectiveAltitude() {
        return objectiveAltitude;
    }

    public int elementsDrawn() {
        return elementsDrawn;
    }

    public float time() {
        return time;
    }

    /** Garde CI : 4 elements, pas de mini-carte, pas de fleche, 180 ms. */
    public static boolean specCompliant() {
        return ELEMENT_COUNT == 4
                && !MINIMAP && !MARKER_3D && !DIRECTION_ARROW
                && !BREATH_SHOWS_NUMBERS && !PROMPT_SHOWS_TEXT
                && !DEATH_SHOWS_TEXT && !DEATH_COUNTS
                && !UI_BOUNCE && !UI_SLIDE_IN
                && BREATH_ARC_DEG == 90f
                && BREATH_APPEAR_THRESHOLD == 0.97f
                && BREATH_RED_THRESHOLD == 0.30f
                && PROMPT_SIZE_DP == 44f
                && UI_ANIM_SECONDS == 0.18f
                && UI_MAX_ANIM_SECONDS == 0.25f
                && DEATH_FADE_SECONDS == 1.2f
                && VERBS.length == VERB_GLYPHS.length;
    }

    public void reset() {
        breathValue = 1f;
        breathOpacity = 0f;
        breathVisible = false;
        compassOpacity = 0f;
        compassVisible = true;
        promptOpacity = 0f;
        promptVisible = false;
        promptGlyph = "";
        subtitlesVisible = false;
        deathFade = 0f;
        dying = false;
        loading = false;
        loadProgress = 0f;
        loadDetailLevel = 0;
        elementsDrawn = 0;
        for (String v : VERBS) {
            promptShownOnce.put(v, Boolean.FALSE);
            verbFailures.put(v, Integer.valueOf(0));
        }
    }
}
