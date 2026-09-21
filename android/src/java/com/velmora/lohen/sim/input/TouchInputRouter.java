/*
 * LOHEN — sim/input/TouchInputRouter.java
 *
 * 02.18 [OBL] : le jeu implemente son propre TouchInputRouter.
 * 08.02 : joystick flottant a gauche (90 dp, zone morte 8 %, courbe x^1,4),
 *         zone camera a droite + 4 boutons maximum en ARC DE CERCLE.
 * 08.03 : multi-touch reel, tap accepte pendant un swipe camera,
 *         "sticky press" 20 dp, buffer d'entree 180 ms, coyote 140 ms,
 *         auto-fire du grappin (cone 40 deg, poids 0,5/0,3/0,2).
 * 08.04 : AUCUN QTE. 08.01 : jamais cinq touches affichees.
 *
 * Java pur : les evenements tactiles sont injectes, ce qui permet de
 * tester le routeur hors device (BLOC 18.06).
 */
package com.velmora.lohen.sim.input;

import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.core.Options;

public final class TouchInputRouter {

    public static final int BTN_A = 0;   /* contextuel / sauter — 72 dp, bas droite */
    public static final int BTN_B = 1;   /* grappin — 64 dp, au-dessus de A */
    public static final int BTN_C = 2;   /* interagir / Echo — 64 dp, a gauche de A */
    public static final int BTN_D = 3;   /* garde — combat uniquement, remplace C */
    public static final int BUTTON_COUNT = 4;

    public static final int INPUT_BUFFER_MS = 180;   /* 08.03 */
    public static final int COYOTE_MS = 140;
    public static final float STICKY_DP = 20f;
    public static final float JOYSTICK_RADIUS_DP = 90f;
    public static final float JOYSTICK_DEADZONE = 0.08f;
    public static final float JOYSTICK_CURVE = 1.4f;
    /* 08.23 revise : 0,13 deg/dp LINEAIRE. La courbe x^1,25 appliquée a un
     * delta en degres clampait chaque evenement a +/-1 deg et rendait la
     * camera saccadee et trop sensible (retour joueur). */
    public static final float CAMERA_SENSITIVITY_DP = 0.13f;
    public static final float CAMERA_CURVE = 1.25f;
    public static final float EDGE_DEAD_ZONE_DP = 48f;        /* 00.06 */

    /* gestes reconnus */
    public static final int GESTURE_NONE = 0;
    public static final int GESTURE_DODGE_SWIPE = 1;    /* swipe camera + direction */
    public static final int GESTURE_DOUBLE_TAP = 2;     /* double-tap = esquive */
    public static final int GESTURE_TWO_FINGER_DOUBLE_TAP = 3;  /* HUD minimal (14.01) */
    public static final int GESTURE_LONG_PRESS = 4;     /* skip cinematique (07.22) */

    private final Options options;

    /* geometrie d'ecran (pixels) */
    private float screenW = 2400f;
    private float screenH = 1080f;
    private float dpScale = 2.75f;      /* px par dp */
    private float leftZoneWidth;

    /* boutons : positions par defaut en arc de cercle (08.02) */
    private final float[] btnX = new float[BUTTON_COUNT];
    private final float[] btnY = new float[BUTTON_COUNT];
    private final float[] btnR = new float[BUTTON_COUNT];
    private final boolean[] btnVisible = new boolean[BUTTON_COUNT];
    private final boolean[] btnDown = new boolean[BUTTON_COUNT];
    private final long[] btnPressStart = new long[BUTTON_COUNT];
    private final int[] btnPointer = new int[]{-1, -1, -1, -1};
    private final boolean[] btnConsumed = new boolean[BUTTON_COUNT];

    /* joystick */
    private int joyPointer = -1;
    private float joyOriginX, joyOriginY;
    private float joyX, joyY;         /* vecteur brut -1..1 */
    private float joyOutX, joyOutY;   /* apres zone morte + courbe */
    private boolean joyActive;

    /* camera */
    private int camPointer = -1;
    /* 07.22 : l'appui long est un ETAT, pas un evenement. Le doigt pose dans
     * la zone camera, sans bouger, est tenu ; c'est le lecteur de
     * cinematique qui compte ses 1,5 s. */
    private int lpPointer = -1;
    private float lpX;
    private float lpY;
    private float camLastX, camLastY;
    private float camDX, camDY;       /* delta consomme par frame */
    private float camSwipeStartX, camSwipeStartY;
    private long camSwipeStartMs;
    private boolean camSwipeValid;

    /* buffer d'entree (08.03) */
    private final long[] bufferedAction = new long[BUTTON_COUNT];
    private final boolean[] bufferedValid = new boolean[BUTTON_COUNT];

    /* gestes */
    private long lastTapMs;
    private float lastTapX, lastTapY;
    private int recentGesture = GESTURE_NONE;
    private float gestureDirX, gestureDirY;
    private long gestureTimestamp;
    private boolean repositioning;
    private int repositionButton = -1;
    private long clockMs;
    private int twoFingerTaps;
    private long twoFingerWindowStart;

    /* etats exposes au jeu */
    private boolean combatMode;
    private boolean interactTargetValid;
    private boolean traversalEnabled = true;
    private boolean uiCapture;

    public TouchInputRouter(Options options) {
        this.options = options;
        layoutButtons();
    }

    public void setScreen(float widthPx, float heightPx, float densityDpScale) {
        this.screenW = widthPx;
        this.screenH = heightPx;
        this.dpScale = Math.max(0.5f, densityDpScale);
        this.leftZoneWidth = widthPx / 3f;
        layoutButtons();
    }

    /** Arc de cercle, pas une grille (08.02). */
    private void layoutButtons() {
        float scale = options == null ? 1f : options.buttonScale;
        float rA = 72f * dpScale * scale * 0.5f;
        float rB = 64f * dpScale * scale * 0.5f;
        float rC = 64f * dpScale * scale * 0.5f;
        float rD = 64f * dpScale * scale * 0.5f;
        /* centre de l'arc : coin bas droit, marge = zone morte 48 dp */
        float margin = EDGE_DEAD_ZONE_DP * dpScale;
        float arcCx = screenW - margin - rA * 1.15f;
        float arcCy = screenH - margin - rA * 1.15f;
        float arcRadius = rA * 2.05f;
        /* angles : A en bas a droite, B au-dessus, C a gauche de A */
        float aA = (float) Math.toRadians(0f);
        float aB = (float) Math.toRadians(58f);
        float aC = (float) Math.toRadians(112f);
        setButtonPos(BTN_A, arcCx + (float) Math.cos(aA) * arcRadius * 0.12f,
                arcCy - (float) Math.sin(aA) * arcRadius * 0.12f, rA);
        setButtonPos(BTN_B, arcCx - (float) Math.cos(aB) * arcRadius * 0.62f,
                arcCy - (float) Math.sin(aB) * arcRadius * 0.95f, rB);
        setButtonPos(BTN_C, arcCx - (float) Math.cos(aC) * arcRadius * 1.02f,
                arcCy - (float) Math.sin(aC) * arcRadius * 0.42f, rC);
        setButtonPos(BTN_D, btnX[BTN_C], btnY[BTN_C], rD);
        /* repositionnement joueur (14.08) */
        if (options != null) {
            for (int i = 0; i < BUTTON_COUNT; i++) {
                float[] p = options.buttonPositions.get(i);
                if (p != null && p.length >= 2) {
                    btnX[i] = Maths.clamp(p[0], 0f, 1f) * screenW;
                    btnY[i] = Maths.clamp(p[1], 0f, 1f) * screenH;
                }
            }
        }
        updateVisibility();
    }

    private void setButtonPos(int b, float x, float y, float r) {
        btnX[b] = Maths.clamp(x, r, screenW - r);
        btnY[b] = Maths.clamp(y, r, screenH - r);
        btnR[b] = r;
    }

    public void setCombatMode(boolean combat) {
        if (combatMode != combat) {
            combatMode = combat;
            updateVisibility();
        }
    }

    public void setInteractTargetValid(boolean valid) {
        if (interactTargetValid != valid) {
            interactTargetValid = valid;
            updateVisibility();
        }
    }

    public void setTraversalEnabled(boolean enabled) {
        traversalEnabled = enabled;
        updateVisibility();
    }

    /** 08.01 : jamais plus de quatre touches affichees ; C et D s'excluent. */
    private void updateVisibility() {
        btnVisible[BTN_A] = traversalEnabled;
        btnVisible[BTN_B] = traversalEnabled;
        btnVisible[BTN_C] = !combatMode && interactTargetValid && traversalEnabled;
        btnVisible[BTN_D] = combatMode && traversalEnabled;
        int visible = 0;
        for (int i = 0; i < BUTTON_COUNT; i++) {
            if (btnVisible[i]) {
                visible++;
            }
        }
        if (visible > 4) {
            btnVisible[BTN_C] = false;
        }
    }

    public void setUiCapture(boolean capture) {
        this.uiCapture = capture;
        if (capture) {
            resetPointers();
        }
    }

    public boolean uiCapture() {
        return uiCapture;
    }

    /* ------------------------------------------------------------------ */
    /* Evenements                                                          */
    /* ------------------------------------------------------------------ */

    public void onPointerDown(int pointerId, float x, float y) {
        if (uiCapture) {
            /* 07.22 : meme pendant un dialogue ou un menu, l'appui long de
             * skip reste accessible — c'est un etat du doigt, pas une action
             * de jeu. Rien d'autre ne passe tant que l'interface capture. */
            lpPointer = pointerId;
            lpX = x;
            lpY = y;
            return;
        }
        clockMs = System.nanoTime() / 1000000L;
        /* deux doigts : double-tap -> HUD minimal (14.01) */
        if (joyPointer >= 0 && camPointer >= 0 && pointerId != joyPointer && pointerId != camPointer) {
            if (clockMs - twoFingerWindowStart < 400) {
                twoFingerTaps++;
                if (twoFingerTaps >= 2) {
                    recentGesture = GESTURE_TWO_FINGER_DOUBLE_TAP;
                    gestureTimestamp = clockMs;
                    twoFingerTaps = 0;
                }
            } else {
                twoFingerTaps = 1;
            }
            twoFingerWindowStart = clockMs;
        }
        /* mode repositionnement des boutons (14.08) */
        if (repositioning) {
            for (int i = 0; i < BUTTON_COUNT; i++) {
                if (dist(x, y, btnX[i], btnY[i]) <= btnR[i] * 1.6f) {
                    repositionButton = i;
                    return;
                }
            }
        }
        /* boutons d'abord : un tap sur un bouton pendant un swipe camera
           est accepte, pas de vol de focus (08.03) */
        for (int i = 0; i < BUTTON_COUNT; i++) {
            if (!btnVisible[i] || btnPointer[i] >= 0) {
                continue;
            }
            if (dist(x, y, btnX[i], btnY[i]) <= btnR[i]) {
                btnPointer[i] = pointerId;
                btnDown[i] = true;
                btnPressStart[i] = clockMs;
                btnConsumed[i] = false;
                if (repositioning) {
                    repositionButton = i;
                }
                return;
            }
        }
        /* joystick flottant : tout le tiers gauche (08.02) */
        if (x < leftZoneWidth && joyPointer < 0) {
            joyPointer = pointerId;
            joyOriginX = x;
            joyOriginY = y;
            joyActive = true;
            joyX = joyY = 0f;
            joyOutX = joyOutY = 0f;
            return;
        }
        /* sinon : camera */
        if (camPointer < 0) {
            camPointer = pointerId;
            lpPointer = pointerId;
            lpX = x;
            lpY = y;
            camLastX = camSwipeStartX = x;
            camLastY = camSwipeStartY = y;
            camSwipeStartMs = clockMs;
            camSwipeValid = true;
            /* double-tap -> esquive (08.14) */
            if (clockMs - lastTapMs < 280 && dist(x, y, lastTapX, lastTapY) < 60f * dpScale) {
                recentGesture = GESTURE_DOUBLE_TAP;
                gestureTimestamp = clockMs;
                gestureDirX = 0f;
                gestureDirY = 1f;
                lastTapMs = 0;
            } else {
                lastTapMs = clockMs;
                lastTapX = x;
                lastTapY = y;
            }
        }
    }

    public void onPointerMove(int pointerId, float x, float y) {
        if (pointerId == lpPointer
                && dist(x, y, lpX, lpY) > 24f * dpScale) {
            lpPointer = -1;
        }
        if (uiCapture) {
            return;
        }
        if (repositioning && repositionButton >= 0 && pointerId == btnPointer[repositionButton]) {
            btnX[repositionButton] = Maths.clamp(x, btnR[repositionButton], screenW - btnR[repositionButton]);
            btnY[repositionButton] = Maths.clamp(y, btnR[repositionButton], screenH - btnR[repositionButton]);
            if (options != null) {
                options.buttonPositions.put(repositionButton, new float[]{
                        btnX[repositionButton] / screenW, btnY[repositionButton] / screenH});
            }
            return;
        }
        if (pointerId == joyPointer) {
            float dx = x - joyOriginX;
            float dy = y - joyOriginY;
            float radius = JOYSTICK_RADIUS_DP * dpScale;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > radius) {
                /* le joystick suit le pouce au-dela du rayon (confort) */
                joyOriginX += dx * (len - radius) / len * 0.5f;
                joyOriginY += dy * (len - radius) / len * 0.5f;
                dx = x - joyOriginX;
                dy = y - joyOriginY;
                len = (float) Math.sqrt(dx * dx + dy * dy);
            }
            float nx = len < 1e-4f ? 0f : dx / radius;
            float ny = len < 1e-4f ? 0f : dy / radius;
            joyX = Maths.clamp(nx, -1f, 1f);
            joyY = Maths.clamp(ny, -1f, 1f);
            applyJoystickCurve();
            return;
        }
        int btn = buttonOfPointer(pointerId);
        if (btn >= 0) {
            /* sticky press : 20 dp hors du bouton, l'appui reste valide */
            if (dist(x, y, btnX[btn], btnY[btn]) > btnR[btn] + STICKY_DP * dpScale) {
                releaseButton(btn, false);
            }
            return;
        }
        if (pointerId == camPointer) {
            float dx = x - camLastX;
            float dy = y - camLastY;
            float sx = options == null ? 1f : options.sensitivityX;
            float sy = options == null ? 1f : options.sensitivityY;
            boolean ix = options != null && options.invertX;
            boolean iy = options != null && options.invertY;
            /* lineaire, en degres par dp ; un garde-fou a +/-3 deg par
             * evenement absorbe les sauts du tactile sans tout quantifier */
            float degX = (dx / dpScale) * CAMERA_SENSITIVITY_DP;
            float degY = (dy / dpScale) * CAMERA_SENSITIVITY_DP;
            camDX += (ix ? 1f : -1f) * Maths.clamp(degX * sx, -3f, 3f);
            camDY += (iy ? -1f : 1f) * Maths.clamp(degY * sy, -3f, 3f);
            camLastX = x;
            camLastY = y;
        }
    }

    /** Le doigt qui tenait l'appui long vient-il de se lever ? */
    public boolean longPressHeld() {
        return lpPointer >= 0;
    }

    public void onPointerUp(int pointerId, float x, float y) {
        if (pointerId == lpPointer) {
            lpPointer = -1;
        }
        if (repositioning && pointerId == (repositionButton >= 0 ? btnPointer[repositionButton] : -2)) {
            repositionButton = -1;
        }
        int btn = buttonOfPointer(pointerId);
        if (btn >= 0) {
            releaseButton(btn, true);
        }
        if (pointerId == joyPointer) {
            joyPointer = -1;
            joyActive = false;
            joyX = joyY = joyOutX = joyOutY = 0f;
        }
        if (pointerId == camPointer) {
            /* swipe rapide = esquive directionnelle (08.14) */
            float dx = x - camSwipeStartX;
            float dy = y - camSwipeStartY;
            long elapsed = clockMs - camSwipeStartMs;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (camSwipeValid && elapsed < 320 && len > 70f * dpScale) {
                recentGesture = GESTURE_DODGE_SWIPE;
                gestureTimestamp = clockMs;
                gestureDirX = dx / len;
                gestureDirY = dy / len;
            }
            /* appui long = skip de cinematique (07.22) */
            else if (camSwipeValid && elapsed >= 1500 && len < 20f * dpScale) {
                recentGesture = GESTURE_LONG_PRESS;
                gestureTimestamp = clockMs;
            }
            camPointer = -1;
            camSwipeValid = false;
        }
    }

    public void onCancel(int pointerId) {
        if (pointerId == lpPointer) {
            lpPointer = -1;
        }
        onPointerUp(pointerId, -1f, -1f);
    }

    private int buttonOfPointer(int pointerId) {
        for (int i = 0; i < BUTTON_COUNT; i++) {
            if (btnPointer[i] == pointerId) {
                return i;
            }
        }
        return -1;
    }

    private void releaseButton(int btn, boolean inside) {
        btnDown[btn] = false;
        btnPointer[btn] = -1;
        if (inside) {
            long held = clockMs - btnPressStart[btn];
            /* mode "tap au lieu de maintien" (14.08) : un tap court declenche
               l'action de maintien (garde, Echo) au lieu d'exiger un appui long. */
            boolean tapMode = options != null && options.tapInsteadOfHold;
            if (tapMode || held < 2000) {
                bufferedAction[btn] = clockMs;
                bufferedValid[btn] = true;
            }
        }
    }

    private void applyJoystickCurve() {
        float len = (float) Math.sqrt(joyX * joyX + joyY * joyY);
        if (len < JOYSTICK_DEADZONE) {
            joyOutX = 0f;
            joyOutY = 0f;
            return;
        }
        float scaled = (len - JOYSTICK_DEADZONE) / (1f - JOYSTICK_DEADZONE);
        float curved = Maths.responseCurve(scaled, JOYSTICK_CURVE);
        float inv = len < 1e-5f ? 0f : curved / len;
        joyOutX = joyX * inv;
        joyOutY = joyY * inv;
    }

    /* ------------------------------------------------------------------ */
    /* Lecture par le jeu                                                  */
    /* ------------------------------------------------------------------ */

    /** Vecteur de deplacement (X = lateral, Y = avant, negatif = avant). */
    public float moveX() {
        return joyOutX;
    }

    public float moveY() {
        return -joyOutY;
    }

    public float moveMagnitude() {
        return (float) Math.sqrt(joyOutX * joyOutX + joyOutY * joyOutY);
    }

    public boolean joystickActive() {
        return joyActive;
    }

    public float joystickOriginX() {
        return joyOriginX;
    }

    public float joystickOriginY() {
        return joyOriginY;
    }

    /** Delta de camera consomme (degres) — remis a zero apres lecture. */
    public float consumeCameraX() {
        float v = camDX;
        camDX = 0f;
        return v;
    }

    public float consumeCameraY() {
        float v = camDY;
        camDY = 0f;
        return v;
    }

    public boolean isDown(int button) {
        return btnDown[button];
    }

    public boolean isVisible(int button) {
        return btnVisible[button];
    }

    public float buttonX(int b) {
        return btnX[b];
    }

    public float buttonY(int b) {
        return btnY[b];
    }

    public float buttonRadius(int b) {
        return btnR[b];
    }

    public long heldMs(int button) {
        return btnDown[button] ? clockMs - btnPressStart[button] : 0L;
    }

    /**
     * Buffer d'entree 180 ms (08.03) : une action pressee avant d'etre
     * possible est executee des qu'elle l'est.
     */
    public boolean consumeBuffered(int button) {
        if (!bufferedValid[button]) {
            return false;
        }
        if (clockMs - bufferedAction[button] > INPUT_BUFFER_MS) {
            bufferedValid[button] = false;
            return false;
        }
        bufferedValid[button] = false;
        return true;
    }

    public boolean hasBuffered(int button) {
        return bufferedValid[button] && clockMs - bufferedAction[button] <= INPUT_BUFFER_MS;
    }

    public void clearBuffer(int button) {
        bufferedValid[button] = false;
    }

    public void clearAllBuffers() {
        for (int i = 0; i < BUTTON_COUNT; i++) {
            bufferedValid[i] = false;
        }
    }

    public int consumeGesture() {
        int g = recentGesture;
        recentGesture = GESTURE_NONE;
        return g;
    }

    public float gestureDirX() {
        return gestureDirX;
    }

    public float gestureDirY() {
        return gestureDirY;
    }

    public long gestureTimestamp() {
        return gestureTimestamp;
    }

    public void setRepositioning(boolean on) {
        repositioning = on;
        repositionButton = -1;
    }

    public boolean repositioning() {
        return repositioning;
    }

    public void resetLayout() {
        if (options != null) {
            options.buttonPositions.clear();
        }
        layoutButtons();
    }

    public void resetPointers() {
        joyPointer = -1;
        camPointer = -1;
        joyActive = false;
        joyX = joyY = joyOutX = joyOutY = 0f;
        for (int i = 0; i < BUTTON_COUNT; i++) {
            btnDown[i] = false;
            btnPointer[i] = -1;
            bufferedValid[i] = false;
        }
    }

    /** Horloge injectable : les tests deterministes l'utilisent. */
    public void setClock(long ms) {
        this.clockMs = ms;
    }

    public long clock() {
        return clockMs;
    }

    public void refreshLayout() {
        layoutButtons();
    }

    private static float dist(float x0, float y0, float x1, float y1) {
        float dx = x0 - x1;
        float dy = y0 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public boolean combatMode() {
        return combatMode;
    }

    public boolean interactTargetValid() {
        return interactTargetValid;
    }

    public float screenW() {
        return screenW;
    }

    public float screenH() {
        return screenH;
    }

    public float dpScale() {
        return dpScale;
    }
}
