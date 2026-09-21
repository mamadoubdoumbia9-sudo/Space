/*
 * LOHEN — LohenActivity.java
 *
 * L'unique Activity du jeu. Elle ne contient aucune regle de jeu : elle donne
 * a la simulation un ecran, des doigts, une sortie audio, un vibreur et un
 * stockage, puis elle lui rend la main a chaque image.
 *
 *   00.05  natif — un GLSurfaceView, OpenGL ES 2.0. Aucune WebView, aucun
 *          moteur externe, aucune ressource telechargee au premier lancement.
 *   02.19  le retour haptique passe par le catalogue HapticDirector : c'est la
 *          simulation qui decide de la forme de l'impulsion, l'Activity ne fait
 *          que la transmettre au Vibrator, en respectant le reglage du joueur.
 *   08.06  un doigt pose sur l'interface n'atteint jamais le joystick ni la
 *          camera : le routage est exclusif, decide au moment du ACTION_DOWN.
 *   14.05  la mise en pause ouvre le menu en moins d'une image et autosauve.
 *
 * Le demarrage lit le contenu sur un fil separe : l'ecran affiche les etapes
 * reelles du chargement pendant ce temps, jamais une barre decorative.
 */
package com.velmora.lohen;

import android.app.Activity;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.velmora.lohen.audio.AudioThread;
import com.velmora.lohen.render.GameRenderer;
import com.velmora.lohen.render.HudRenderer;
import com.velmora.lohen.sim.core.LohenGame;
import com.velmora.lohen.sim.core.Options;
import com.velmora.lohen.sim.input.GamepadRouter;
import com.velmora.lohen.sim.input.HapticDirector;
import com.velmora.lohen.sim.narrative.DialogueRuntime;
import com.velmora.lohen.sim.ui.MenuModel;

import java.nio.charset.Charset;

public class LohenActivity extends Activity {

    private static final String TAG = "LOHEN";
    private static final String OPTIONS_FILE = "options.cfg";
    private static final String PREFS = "lohen";
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private GLSurfaceView glView;
    private GameRenderer renderer;
    private LohenGame game;
    private AudioThread audio;
    private SaveStorageAndroid storage;
    private Vibrator vibrator;

    private float density = 2.75f;
    private volatile boolean booted;
    private volatile boolean resumed;
    private Thread bootThread;

    /* le routage exclusif du toucher : un pointeur est soit a l'interface,
     * soit au monde, et il le reste jusqu'a ce qu'il se leve. */
    private final boolean[] uiPointer = new boolean[32];
    private final float[] lastX = new float[32];
    private final float[] lastY = new float[32];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        density = getResources().getDisplayMetrics().density;
        storage = new SaveStorageAndroid(this);
        Options options = loadOptions();

        long seed = getSharedPreferences(PREFS, MODE_PRIVATE).getLong("seed", 0L);
        if (seed == 0L) {
            seed = System.nanoTime() ^ 0x5645_4C4D_4F52_41L;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong("seed", seed).apply();
        }

        game = new LohenGame(new AssetSourceAndroid(getAssets()), storage, options, seed);

        renderer = new GameRenderer(game);
        renderer.setDensity(density);
        renderer.setAssets(getAssets());

        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);            /* 02.05 : GLES 2.0 */
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        glView.setPreserveEGLContextOnPause(true);
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        glView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b,
                                       int ol, int ot, int or, int ob) {
                applyScreenSize(r - l, b - t);
            }
        });
        setContentView(glView);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        game.haptics.setSink(new AndroidHaptics());

        immersive();
        startBoot();
    }

    /* ------------------------------------------------------------------ */
    /* Demarrage                                                           */
    /* ------------------------------------------------------------------ */

    private void startBoot() {
        bootThread = new Thread(new Runnable() {
            @Override
            public void run() {
                long t0 = System.nanoTime();
                boolean ok;
                try {
                    ok = game.boot(new LohenGame.BootListener() {
                        @Override
                        public void stage(String name, float progress) {
                            renderer.setBootStage(name);
                            renderer.setBootProgress(progress);
                        }
                    });
                } catch (Throwable t) {
                    Log.e(TAG, "echec du chargement du contenu", t);
                    ok = false;
                }
                long ms = (System.nanoTime() - t0) / 1000000L;
                Log.i(TAG, "contenu charge en " + ms + " ms — " + game.describe());
                booted = ok;
                renderer.setBootProgress(1f);
                renderer.setStarted(ok);
                if (!ok) {
                    renderer.setBootStage("Contenu introuvable");
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        applyScreenSize(glView.getWidth(), glView.getHeight());
                        if (audio == null) {
                            audio = new AudioThread(game);
                            audio.start();
                        }
                    }
                });
            }
        }, "lohen-boot");
        bootThread.setPriority(Thread.NORM_PRIORITY + 1);
        bootThread.start();
    }

    /* ------------------------------------------------------------------ */
    /* Cycle de vie                                                        */
    /* ------------------------------------------------------------------ */

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        glView.onResume();
        immersive();
        if (audio != null) {
            audio.resumeAudio();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        /* 14.05 : quitter le jeu ne coute jamais la progression. */
        if (booted && game != null) {
            synchronized (game) {
                try {
                    game.saves.autosave(game.state, "arriere-plan");
                } catch (RuntimeException e) {
                    Log.w(TAG, "autosave de sortie refuse", e);
                }
            }
            saveOptions();
        }
        if (audio != null) {
            audio.pauseAudio();
        }
        glView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (audio != null) {
            audio.shutdown();
            audio = null;
        }
        if (renderer != null) {
            glView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    renderer.release();
                }
            });
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            immersive();
        }
    }

    /** Plein ecran stable : les barres systeme ne doivent jamais apparaitre. */
    private void immersive() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void applyScreenSize(int w, int h) {
        if (w <= 0 || h <= 0 || game == null) {
            return;
        }
        synchronized (game) {
            game.touch.setScreen(w, h, density);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Le toucher                                                          */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (game == null || !booted) {
            return true;
        }
        int action = e.getActionMasked();
        int index = e.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int id = e.getPointerId(index);
                float x = e.getX(index);
                float y = e.getY(index);
                boolean ui = false;
                synchronized (game) {
                    if (interfaceWantsInput()) {
                        ui = renderer.hud().tap(game, x, y);
                    }
                    if (!ui) {
                        game.touch.onPointerDown(id, x, y);
                    }
                }
                mark(id, ui, x, y);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                int n = e.getPointerCount();
                for (int i = 0; i < n; i++) {
                    int id = e.getPointerId(i);
                    float x = e.getX(i);
                    float y = e.getY(i);
                    synchronized (game) {
                        if (isUi(id)) {
                            dragInterface(x, y);
                        } else {
                            game.touch.onPointerMove(id, x, y);
                        }
                    }
                    remember(id, x, y);
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP: {
                int id = e.getPointerId(index);
                float x = e.getX(index);
                float y = e.getY(index);
                boolean quit = false;
                synchronized (game) {
                    if (isUi(id)) {
                        releaseInterface(x, y);
                        quit = renderer.hud().consumeQuit();
                    } else {
                        game.touch.onPointerUp(id, x, y);
                    }
                    /* 07.22 : doigt leve, l'appui long de skip est rearme,
                     * sinon la cinematique suivante sauterait toute seule */
                    game.releaseSkip();
                }
                clear(id);
                if (quit) {
                    finish();
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                int n = e.getPointerCount();
                for (int i = 0; i < n; i++) {
                    int id = e.getPointerId(i);
                    synchronized (game) {
                        if (isUi(id)) {
                            releaseInterface(e.getX(i), e.getY(i));
                        } else {
                            game.touch.onCancel(id);
                        }
                        game.releaseSkip();
                    }
                    clear(id);
                }
                return true;
            }
            default:
                return super.onTouchEvent(e);
        }
    }

    /** L'interface a-t-elle la main ? Menu, choix de dialogue, lettre. */
    private boolean interfaceWantsInput() {
        int mode = game.mode();
        if (mode == LohenGame.MODE_MENU || mode == LohenGame.MODE_LETTER
                || mode == LohenGame.MODE_CREDITS) {
            return true;
        }
        if (game.menu.open()) {
            return true;
        }
        DialogueRuntime d = game.dialogue;
        return d.active() && d.choiceOffered();
    }

    private void dragInterface(float x, float y) {
        HudRenderer hud = renderer.hud();
        if (game.letter != null && hud.isDoorHeld(x, y)) {
            /* 19.02 : la porte se pousse des deux mains, en continu */
            game.letter.setDoorHeld(true);
            return;
        }
        hud.drag(game, x, y);
    }

    private void releaseInterface(float x, float y) {
        if (game.letter != null) {
            game.letter.setDoorHeld(false);
        }
    }

    private void mark(int id, boolean ui, float x, float y) {
        if (id < 0 || id >= uiPointer.length) {
            return;
        }
        uiPointer[id] = ui;
        lastX[id] = x;
        lastY[id] = y;
    }

    private void remember(int id, float x, float y) {
        if (id < 0 || id >= uiPointer.length) {
            return;
        }
        lastX[id] = x;
        lastY[id] = y;
    }

    private boolean isUi(int id) {
        return id >= 0 && id < uiPointer.length && uiPointer[id];
    }

    private void clear(int id) {
        if (id >= 0 && id < uiPointer.length) {
            uiPointer[id] = false;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Clavier, manette, bouton retour                                     */
    /* ------------------------------------------------------------------ */

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (game == null || !booted) {
            return super.onKeyDown(keyCode, event);
        }
        if (isGamepad(event)) {
            synchronized (game) {
                String name = event.getDevice() != null ? event.getDevice().getName() : "manette";
                game.gamepad.setConnected(true, name);
                game.gamepad.setButton(keyCode, true);
            }
            return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                backPressed();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                navigate(-1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                navigate(1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                navigate(0, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                navigate(0, 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_SPACE:
                confirm();
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_P:
                synchronized (game) {
                    game.togglePause();
                }
                return true;
            case KeyEvent.KEYCODE_J:
                synchronized (game) {
                    if (game.menu.open()) {
                        game.menu.open("journal");
                    }
                }
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (game != null && booted && isGamepad(event)) {
            synchronized (game) {
                game.gamepad.setButton(keyCode, false);
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (game == null || !booted) {
            return super.onGenericMotionEvent(event);
        }
        boolean pad = (event.getSource() & android.view.InputDevice.SOURCE_GAMEPAD)
                == android.view.InputDevice.SOURCE_GAMEPAD
                || (event.getSource() & android.view.InputDevice.SOURCE_JOYSTICK)
                == android.view.InputDevice.SOURCE_JOYSTICK;
        if (!pad) {
            return super.onGenericMotionEvent(event);
        }
        synchronized (game) {
            GamepadRouter gp = game.gamepad;
            gp.setConnected(true, event.getDevice() != null
                    ? event.getDevice().getName() : "manette");
            gp.setAxis(GamepadRouter.AXIS_LEFT_X, axis(event, MotionEvent.AXIS_X));
            gp.setAxis(GamepadRouter.AXIS_LEFT_Y, axis(event, MotionEvent.AXIS_Y));
            gp.setAxis(GamepadRouter.AXIS_RIGHT_X, axis(event, MotionEvent.AXIS_Z));
            gp.setAxis(GamepadRouter.AXIS_RIGHT_Y, axis(event, MotionEvent.AXIS_RZ));
            gp.setAxis(GamepadRouter.AXIS_TRIGGER_L, axis(event, MotionEvent.AXIS_LTRIGGER));
            gp.setAxis(GamepadRouter.AXIS_TRIGGER_R, axis(event, MotionEvent.AXIS_RTRIGGER));
            gp.setAxis(GamepadRouter.AXIS_LEFT_X,
                    axis(event, MotionEvent.AXIS_HAT_X) != 0f
                            ? axis(event, MotionEvent.AXIS_HAT_X)
                            : gp.moveX());
        }
        return true;
    }

    private static float axis(MotionEvent e, int a) {
        try {
            return e.getAxisValue(a);
        } catch (RuntimeException ex) {
            return 0f;
        }
    }

    private static boolean isGamepad(KeyEvent e) {
        int src = e.getSource();
        return (src & android.view.InputDevice.SOURCE_GAMEPAD)
                == android.view.InputDevice.SOURCE_GAMEPAD
                || (src & android.view.InputDevice.SOURCE_JOYSTICK)
                == android.view.InputDevice.SOURCE_JOYSTICK;
    }

    /** Le bouton retour : remonte d'un niveau, ne quitte jamais brutalement. */
    private void backPressed() {
        synchronized (game) {
            MenuModel menu = game.menu;
            int screen = menu.screen();
            if (screen == MenuModel.SCREEN_OPTIONS || screen == MenuModel.SCREEN_JOURNAL
                    || screen == MenuModel.SCREEN_SAVES) {
                if (game.mode() == LohenGame.MODE_MENU) {
                    menu.open("main");
                } else {
                    menu.open("pause");
                }
                return;
            }
            if (screen == MenuModel.SCREEN_PAUSE) {
                menu.togglePause();
                return;
            }
            if (screen == MenuModel.SCREEN_MAIN) {
                /* au menu principal, retour = quitter, comme le bouton Quitter */
                finish();
                return;
            }
            if (game.mode() == LohenGame.MODE_LETTER
                    || game.mode() == LohenGame.MODE_CREDITS) {
                return;          /* la lettre ne s'interrompt pas : elle se lit */
            }
            game.togglePause();
        }
    }

    private void navigate(int rowDelta, int sideDelta) {
        synchronized (game) {
            MenuModel menu = game.menu;
            if (!menu.open()) {
                return;
            }
            if (menu.screen() == MenuModel.SCREEN_OPTIONS) {
                if (rowDelta != 0) {
                    menu.moveRow(rowDelta);
                } else if (sideDelta != 0) {
                    MenuModel.Row r = menu.currentRow();
                    if (r != null && r.kind == MenuModel.Row.KIND_SLIDER) {
                        menu.adjust(sideDelta);
                    } else {
                        menu.movePage(sideDelta);
                    }
                }
                return;
            }
            if (menu.screen() == MenuModel.SCREEN_JOURNAL) {
                if (sideDelta != 0) {
                    game.journal.setTab(Math.max(0, Math.min(
                            com.velmora.lohen.sim.narrative.JournalModel.TAB_COUNT - 1,
                            game.journal.tab() + sideDelta)));
                } else {
                    game.journal.moveEntry(rowDelta);
                }
                return;
            }
            if (menu.screen() == MenuModel.SCREEN_SAVES) {
                if (rowDelta != 0) {
                    menu.setSlot(Math.max(0, Math.min(2, menu.slot() + rowDelta)));
                }
                return;
            }
            menu.moveRow(rowDelta);
        }
    }

    private void confirm() {
        synchronized (game) {
            MenuModel menu = game.menu;
            HudRenderer hud = renderer.hud();
            if (!menu.open()) {
                if (game.mode() == LohenGame.MODE_LETTER) {
                    game.letterConfirm();
                } else {
                    /* hors menu : confirmer, c'est l'action contextuelle */
                    game.interact();
                }
                return;
            }
            switch (menu.screen()) {
                case MenuModel.SCREEN_MAIN:
                    hud.activateMain(game, menu.row());
                    break;
                case MenuModel.SCREEN_PAUSE:
                    hud.activatePause(game, menu.row());
                    break;
                case MenuModel.SCREEN_OPTIONS:
                    menu.adjust(1f);
                    break;
                case MenuModel.SCREEN_SAVES:
                    hud.activateSlot(game, menu.slot());
                    break;
                default:
                    break;
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Haptique et reglages                                                */
    /* ------------------------------------------------------------------ */

    /**
     * Le vibreur Android. HapticDirector decide du motif et de l'amplitude ;
     * ici on traduit seulement, en appliquant le reglage `vibration` (02.19).
     */
    private final class AndroidHaptics implements HapticDirector.Sink {
        @Override
        public void vibrate(int[] patternMs, int amplitudePct) {
            if (vibrator == null || !vibrator.hasVibrator() || patternMs == null
                    || patternMs.length == 0 || !resumed) {
                return;
            }
            float scale = game.options.vibration;
            if (scale <= 0.01f) {
                return;
            }
            int amp = Math.max(1, Math.min(255,
                    Math.round(amplitudePct * 2.55f * scale)));
            /* le motif alterne impulsion / silence ; Android commence par un
             * delai, donc on prepose un zero. */
            long[] timings = new long[patternMs.length + 1];
            int[] amps = new int[patternMs.length + 1];
            timings[0] = 0L;
            amps[0] = 0;
            for (int i = 0; i < patternMs.length; i++) {
                timings[i + 1] = Math.max(1L, patternMs[i]);
                amps[i + 1] = (i % 2 == 0) ? amp : 0;
            }
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amps, -1));
                } else {
                    vibrator.vibrate(timings, -1);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "vibration refusee", e);
            }
        }
    }

    /** Les reglages survivent au redemarrage : options.cfg, via le stockage. */
    private Options loadOptions() {
        byte[] raw = storage.read(OPTIONS_FILE);
        if (raw == null || raw.length == 0) {
            Options o = new Options();
            o.clampToSpec();
            return o;
        }
        Options o = Options.deserialize(new String(raw, UTF8));
        o.clampToSpec();
        return o;
    }

    private void saveOptions() {
        try {
            storage.write(OPTIONS_FILE, game.options.serialize().getBytes(UTF8));
        } catch (RuntimeException e) {
            Log.w(TAG, "options non enregistrees", e);
        }
    }
}
