/*
 * LOHEN — sim/input/GamepadRouter.java
 *
 * 08.22 : support manette Bluetooth complet, mapping Xbox, detection a chaud,
 * changement d'iconographie HUD en 1 frame, le HUD tactile disparait avec un
 * fondu de 0,3 s.
 */
package com.velmora.lohen.sim.input;

import com.velmora.lohen.sim.math.Maths;

public final class GamepadRouter {

    /* Codes Android KeyEvent (importes en dur pour rester Java pur) */
    public static final int KEY_BUTTON_A = 96;
    public static final int KEY_BUTTON_B = 97;
    public static final int KEY_BUTTON_X = 99;
    public static final int KEY_BUTTON_Y = 100;
    public static final int KEY_BUTTON_L1 = 102;
    public static final int KEY_BUTTON_R1 = 103;
    public static final int KEY_BUTTON_L2 = 104;
    public static final int KEY_BUTTON_R2 = 105;
    public static final int KEY_BUTTON_SELECT = 109;
    public static final int KEY_BUTTON_START = 108;
    public static final int KEY_BUTTON_THUMBL = 106;
    public static final int KEY_BUTTON_THUMBR = 107;
    public static final int KEY_DPAD_UP = 19;
    public static final int KEY_DPAD_DOWN = 20;
    public static final int KEY_DPAD_LEFT = 21;
    public static final int KEY_DPAD_RIGHT = 22;

    public static final int AXIS_LEFT_X = 0;
    public static final int AXIS_LEFT_Y = 1;
    public static final int AXIS_RIGHT_X = 11;
    public static final int AXIS_RIGHT_Y = 14;
    public static final int AXIS_TRIGGER_L = 23;
    public static final int AXIS_TRIGGER_R = 22;

    /** Actions logiques du jeu (mapping 08.22). */
    public static final int ACT_MOVE = 0;
    public static final int ACT_CAMERA = 1;
    public static final int ACT_JUMP = 2;
    public static final int ACT_INTERACT_ECHO = 3;
    public static final int ACT_DODGE = 4;
    public static final int ACT_HOLSTER = 5;
    public static final int ACT_GRAPPLE = 6;
    public static final int ACT_GUARD_PARRY = 7;
    public static final int ACT_SPRINT_TOGGLE = 8;
    public static final int ACT_CENTER_CAMERA = 9;
    public static final int ACT_PAUSE = 10;
    public static final int ACT_JOURNAL = 11;

    private boolean connected;
    private String deviceName = "";
    private final float[] axes = new float[16];
    private final boolean[] buttons = new boolean[128];
    private final boolean[] pressedEdge = new boolean[128];
    private final boolean[] releasedEdge = new boolean[128];
    private boolean sprintToggle;
    private float deadzone = 0.18f;
    private float sensitivity = 1.4f;   /*adoucie : retour joueur */
    private float hudFade = 0f;          /* 0,3 s de fondu du HUD tactile */
    private boolean hudTouchVisible = true;

    public void setConnected(boolean connected, String name) {
        if (this.connected != connected) {
            this.connected = connected;
            this.deviceName = name == null ? "" : name;
            /* detection a chaud : le HUD tactile s'efface en 0,3 s */
            hudTouchVisible = !connected;
        }
    }

    public boolean connected() {
        return connected;
    }

    public String deviceName() {
        return deviceName;
    }

    public boolean touchHudVisible() {
        return hudTouchVisible;
    }

    public void updateHudFade(float dt) {
        float target = connected ? 0f : 1f;
        hudFade = Maths.damp(hudFade, target, 1f / 0.3f, dt);
        hudTouchVisible = hudFade > 0.02f;
    }

    public float hudFade() {
        return hudFade;
    }

    public void setAxis(int axis, float value) {
        if (axis >= 0 && axis < axes.length) {
            axes[axis] = value;
        }
    }

    public void setButton(int keyCode, boolean down) {
        int idx = mapKeyToIndex(keyCode);
        if (idx < 0) {
            return;
        }
        if (down && !buttons[idx]) {
            pressedEdge[idx] = true;
        }
        if (!down && buttons[idx]) {
            releasedEdge[idx] = true;
        }
        buttons[idx] = down;
    }

    private static int mapKeyToIndex(int keyCode) {
        if (keyCode >= 0 && keyCode < 128) {
            return keyCode;
        }
        return -1;
    }

    public boolean isDown(int keyCode) {
        return keyCode >= 0 && keyCode < 128 && buttons[keyCode];
    }

    public boolean consumePress(int keyCode) {
        if (keyCode >= 0 && keyCode < 128 && pressedEdge[keyCode]) {
            pressedEdge[keyCode] = false;
            return true;
        }
        return false;
    }

    public boolean consumeRelease(int keyCode) {
        if (keyCode >= 0 && keyCode < 128 && releasedEdge[keyCode]) {
            releasedEdge[keyCode] = false;
            return true;
        }
        return false;
    }

    public void endFrame() {
        for (int i = 0; i < pressedEdge.length; i++) {
            pressedEdge[i] = false;
            releasedEdge[i] = false;
        }
    }

    private float axis(int a) {
        float v = a >= 0 && a < axes.length ? axes[a] : 0f;
        return Math.abs(v) < deadzone ? 0f : v;
    }

    public float moveX() {
        return axis(AXIS_LEFT_X);
    }

    public float moveY() {
        return -axis(AXIS_LEFT_Y);
    }

    public float cameraX() {
        return axis(AXIS_RIGHT_X) * sensitivity;
    }

    public float cameraY() {
        return -axis(AXIS_RIGHT_Y) * sensitivity;
    }

    /** Mapping Xbox (08.22) — chaque action renvoie son etat logique. */
    public boolean action(int act) {
        switch (act) {
            case ACT_JUMP:
                return isDown(KEY_BUTTON_A);
            case ACT_INTERACT_ECHO:
                return isDown(KEY_BUTTON_X);
            case ACT_DODGE:
                return isDown(KEY_BUTTON_B);
            case ACT_HOLSTER:
                return isDown(KEY_BUTTON_Y);
            case ACT_GRAPPLE:
                return isDown(KEY_BUTTON_R2) || axis(AXIS_TRIGGER_R) > 0.5f;
            case ACT_GUARD_PARRY:
                return isDown(KEY_BUTTON_L1) || axis(AXIS_TRIGGER_L) > 0.5f;
            case ACT_SPRINT_TOGGLE:
                return isDown(KEY_BUTTON_R1);
            case ACT_CENTER_CAMERA:
                return isDown(KEY_BUTTON_THUMBR);
            case ACT_PAUSE:
                return isDown(KEY_BUTTON_START);
            case ACT_JOURNAL:
                return isDown(KEY_BUTTON_SELECT);
            default:
                return false;
        }
    }

    public boolean toggleSprint() {
        if (consumePress(KEY_BUTTON_R1)) {
            sprintToggle = !sprintToggle;
            return true;
        }
        return false;
    }

    public boolean sprintToggled() {
        return sprintToggle;
    }

    public void setSensitivity(float s) {
        this.sensitivity = s;
    }

    public void setDeadzone(float d) {
        this.deadzone = Maths.clamp(d, 0.05f, 0.4f);
    }
}
