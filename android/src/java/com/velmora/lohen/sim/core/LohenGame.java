/*
 * LOHEN — sim/core/LohenGame.java
 *
 * L'orchestrateur. Tout ce qui existe dans le jeu passe par ici : le chargement
 * du contenu, la boucle a pas fixe, l'ordre exact de mise a jour des systemes,
 * la lecture des declencheurs de niveau, les rencontres, les Echo, les
 * cinematiques, la finale de la lettre, la pause, les sauvegardes.
 *
 * REGLES STRUCTURANTES DU CHAPITRE 1
 * 00.04 [OBL] : la simulation tourne a pas FIXE (60 Hz) et le rendu interpole.
 *        Jamais de dt variable dans la physique — c'est ce qui garantit que le
 *        meme saut reussit sur un telephone a 30 fps et sur un autre a 120.
 * 07.21 : les 11 cinematiques sont en temps reel dans le moteur.
 * 07.22 [OBL] : skippable apres 1,5 s d'appui long (20 s pour la lettre).
 * 08.01 : quatre boutons maximum a l'ecran, jamais cinq.
 * 09.04 : un evenement toutes les 45 a 90 secondes ; au-dela de 120 s, c'est
 *        un trou de rythme et le jeu le signale.
 * 10.02 : la sequence avance par le chemin, jamais par un mur invisible.
 * 11.03 [OBL] : le rituel du gant dure 2,4 s, jamais raccourci, jamais skippe.
 * 14.05 : la pause tient en moins d'une image, avec autosave.
 */
package com.velmora.lohen.sim.core;

import com.velmora.lohen.sim.ai.AttackTokenScheduler;
import com.velmora.lohen.sim.ai.Echassier;
import com.velmora.lohen.sim.ai.Figure;
import com.velmora.lohen.sim.ai.Mueur;
import com.velmora.lohen.sim.ai.Perception;
import com.velmora.lohen.sim.ai.VerrierBoss;
import com.velmora.lohen.sim.anim.CharacterRig;
import com.velmora.lohen.sim.anim.FootstepSystem;
import com.velmora.lohen.sim.audio.AmbienceDirector;
import com.velmora.lohen.sim.audio.AudioEngine;
import com.velmora.lohen.sim.audio.MusicDirector;
import com.velmora.lohen.sim.audio.SfxDirector;
import com.velmora.lohen.sim.input.GamepadRouter;
import com.velmora.lohen.sim.input.HapticDirector;
import com.velmora.lohen.sim.input.TouchInputRouter;
import com.velmora.lohen.sim.math.Vec3;
import com.velmora.lohen.sim.narrative.CinematicPlayer;
import com.velmora.lohen.sim.narrative.DialogueRuntime;
import com.velmora.lohen.sim.narrative.EchoSystem;
import com.velmora.lohen.sim.narrative.JournalModel;
import com.velmora.lohen.sim.narrative.LetterReader;
import com.velmora.lohen.sim.narrative.QuestGraph;
import com.velmora.lohen.sim.player.BreathComponent;
import com.velmora.lohen.sim.player.CameraRig;
import com.velmora.lohen.sim.player.CarryComponent;
import com.velmora.lohen.sim.player.CombatComponent;
import com.velmora.lohen.sim.player.GrappleComponent;
import com.velmora.lohen.sim.player.LedgeScanner;
import com.velmora.lohen.sim.player.Lohen;
import com.velmora.lohen.sim.player.Motor;
import com.velmora.lohen.sim.player.PlayerFsm;
import com.velmora.lohen.sim.ui.HudModel;
import com.velmora.lohen.sim.ui.MenuModel;
import com.velmora.lohen.sim.world.GlassSea;
import com.velmora.lohen.sim.world.Lighthouse;
import com.velmora.lohen.sim.world.NpcDirector;
import com.velmora.lohen.sim.world.PhysicsWorld;
import com.velmora.lohen.sim.world.SequenceAtlas;
import com.velmora.lohen.sim.world.ZoneStreamer;

import java.util.ArrayList;
import java.util.List;

public final class LohenGame implements EventBus.Listener {

    /* ------------------------------------------------------------------ */
    /* Constantes                                                          */
    /* ------------------------------------------------------------------ */

    /** 00.04 [OBL] : pas fixe de la simulation. */
    public static final float FIXED_STEP = 1f / 60f;
    /** Au-dela, on ne rattrape pas : le jeu prefere une image lente a une
     *  spirale de la mort. */
    public static final float MAX_CATCHUP = 0.25f;
    public static final int MAX_STEPS_PER_FRAME = 8;

    /** Modes du jeu. */
    public static final int MODE_MENU = 0;
    public static final int MODE_PLAY = 1;
    public static final int MODE_CINEMATIC = 2;
    public static final int MODE_LETTER = 3;
    public static final int MODE_CREDITS = 4;
    public static final int MODE_LOADING = 5;

    /** Rayons d'interaction (08.10 : l'invite doit etre visible de loin,
     *  l'interaction se fait a portee de main). */
    public static final float INTERACT_RADIUS = 2.2f;
    public static final float ECHO_RADIUS = 1.5f;      /* 06.24 */
    public static final float CARRY_RADIUS = 1.6f;
    public static final float NPC_TALK_RADIUS = 3.0f;

    /* ------------------------------------------------------------------ */
    /* Systemes                                                            */
    /* ------------------------------------------------------------------ */

    public final EventBus bus;
    public final Options options;
    public final GameState state;
    public final ContentDb db;
    public final Tuning tuning;
    public final Localization loc;
    public final Rng rng;
    public final TimeDilation timeDilation;
    public final PerfGovernor perf;
    public final ThermalGovernor thermal;
    public final SaveSystem saves;

    public final PhysicsWorld world;
    public final ZoneStreamer streamer;
    public final Lighthouse lighthouse;
    public final GlassSea glassSea;
    public final NpcDirector npcs;
    public final Perception perception;

    public final Lohen lohen;
    public final PlayerFsm fsm;
    public final Motor motor;
    public final LedgeScanner ledges;
    public final GrappleComponent grapple;
    public final CombatComponent combat;
    public final CarryComponent carry;
    public final CameraRig camera;
    public final CharacterRig rig;
    public final FootstepSystem footsteps;

    public final AttackTokenScheduler tokens;
    public final List<Figure> figures = new ArrayList<Figure>(12);

    public final AudioEngine audio;
    public final MusicDirector music;
    public final SfxDirector sfx;
    public final AmbienceDirector ambience;

    public final QuestGraph quests;
    public final DialogueRuntime dialogue;
    public final EchoSystem echo;
    public final CinematicPlayer cine;
    public final LetterReader letter;
    public final JournalModel journal;

    public final HudModel hud;
    public final MenuModel menu;
    public final TouchInputRouter touch;
    public final GamepadRouter gamepad;
    public final HapticDirector haptics;

    /* ------------------------------------------------------------------ */
    /* Etat d'orchestration                                                */
    /* ------------------------------------------------------------------ */

    private int mode = MODE_MENU;
    private float accumulator;
    private float gameTime;
    private float frameSeconds;
    private float sinceEvent;
    private float lastFrameMs;
    private String sequence = "";
    private LevelData level;
    private boolean bootOk;
    private int stepsThisFrame;
    private float deathTimer;
    private String pendingCinematic = "";
    private boolean skipHeld;
    private boolean letterConfirmQueued;
    private String pendingSequence = "";
    private String currentTrigger = "";
    private String interactTarget = "";
    private String interactVerb = "";
    private float interactDistance = Float.MAX_VALUE;
    private int pacingHoles;
    private int frames;
    private final boolean[] triggerFired = new boolean[512];
    private final float[] pos = new float[3];
    private final float[] normal = new float[3];
    private final float[] aim = new float[3];

    /* ------------------------------------------------------------------ */
    /* Construction                                                        */
    /* ------------------------------------------------------------------ */

    public LohenGame(ContentDb.AssetSource assets, SaveSystem.Storage storage,
                     Options options, long seed) {
        this.bus = new EventBus();
        this.options = options != null ? options : new Options();
        this.options.clampToSpec();
        this.db = new ContentDb(assets);
        this.state = new GameState();
        this.rng = new Rng(seed);
        this.tuning = new Tuning(db);
        this.loc = new Localization(db);
        this.timeDilation = new TimeDilation();
        this.perf = new PerfGovernor(bus, this.options);
        this.thermal = new ThermalGovernor(perf, bus);
        this.saves = new SaveSystem(storage, bus);

        this.world = new PhysicsWorld();
        this.streamer = new ZoneStreamer(db, bus, this.options);
        this.lighthouse = new Lighthouse(bus);
        this.glassSea = new GlassSea(bus, rng);
        this.npcs = new NpcDirector(bus, rng);
        this.perception = new Perception(bus, rng);

        this.lohen = new Lohen(tuning, bus);
        this.fsm = new PlayerFsm(lohen, tuning, bus);
        this.motor = new Motor(lohen, tuning, bus, fsm);
        this.ledges = new LedgeScanner(lohen, tuning, bus);
        this.grapple = new GrappleComponent(lohen, tuning, bus, fsm);
        this.combat = new CombatComponent(lohen, tuning, bus, fsm);
        this.carry = new CarryComponent(tuning, bus);
        this.camera = new CameraRig(lohen, tuning, this.options, bus);
        this.rig = new CharacterRig();
        this.footsteps = new FootstepSystem(lohen, bus, rng);

        this.tokens = new AttackTokenScheduler(tuning);
        this.audio = new AudioEngine(this.options, bus, rng);
        this.music = new MusicDirector(audio, bus, rng);
        this.sfx = new SfxDirector(audio, bus, rng);
        this.ambience = new AmbienceDirector(audio, bus, rng);

        this.quests = new QuestGraph(db, bus, state, loc);
        this.dialogue = new DialogueRuntime(db, state, bus, loc);
        this.echo = new EchoSystem(db, state, bus, tuning);
        this.cine = new CinematicPlayer(db, bus);
        this.letter = new LetterReader(db, state, bus);
        this.journal = new JournalModel(bus, db, state, loc);

        this.hud = new HudModel(this.options, bus);
        this.menu = new MenuModel(this.options, bus, loc, journal, saves);
        this.touch = new TouchInputRouter(this.options);
        this.gamepad = new GamepadRouter();
        this.haptics = new HapticDirector(this.options, bus);

        /* tout ce qui ecoute le bus s'y inscrit ici, une seule fois */
        bus.connect(EventBus.CHECKPOINT_REACHED, this);
        bus.connect(EventBus.PLAYER_DIED, this);
        bus.connect(EventBus.SEQUENCE_CHANGED, this);
        bus.connect(EventBus.CINEMATIC_STARTED, this);
        bus.connect(EventBus.CINEMATIC_ENDED, this);
        bus.connect(EventBus.CINEMATIC_FINAL_STATE, this);
        bus.connect(EventBus.ECHO_STARTED, this);
        bus.connect(EventBus.ECHO_FINISHED, this);
        bus.connect(EventBus.DIALOGUE_STARTED, this);
        bus.connect(EventBus.DIALOGUE_FINISHED, this);
        bus.connect(EventBus.LETTER_STEP, this);
        bus.connect(EventBus.CREDITS_BEGIN, this);
        bus.connect(EventBus.CREDITS_END, this);
        bus.connect(EventBus.PAUSE_TOGGLED, this);
        bus.connect(EventBus.MENU_ACTION, this);
        bus.connect(EventBus.CONTROL_RESTORED, this);
        bus.connect(EventBus.OPTIONS_CHANGED, this);
        bus.connect(EventBus.PACING_HOLE, this);
        bus.connect(EventBus.BOSS_DEFEATED, this);
        bus.connect(EventBus.CHAPTER_COMPLETE, this);
    }

    /* ------------------------------------------------------------------ */
    /* Demarrage                                                           */
    /* ------------------------------------------------------------------ */

    /** Charge le contenu (2 832 lignes, 147 props, 744 anims, 31 Echos). */
    /**
     * Ecoute du demarrage : chaque etape correspond a un travail reel, donc la
     * barre de progression ne ment jamais (02.13).
     */
    public interface BootListener {
        void stage(String name, float progress);
    }

    public boolean boot() {
        return boot(null);
    }

    public boolean boot(BootListener listener) {
        stage(listener, "contenu", 0.04f);
        bootOk = db.loadAll();
        if (!bootOk) {
            stage(listener, "erreur", 1f);
            bus.emit(EventBus.LOADING_ENDED, 0f);
            return false;
        }
        stage(listener, "reglages", 0.58f);
        tuning.load();
        stage(listener, "audio", 0.68f);
        audio.applyOptions();
        hud.applyOptions();
        loc.setLanguage(loc.language());
        stage(listener, "journal", 0.78f);
        quests.begin();
        stage(listener, "lettre", 0.88f);
        letter.loadLetter();
        mode = MODE_MENU;
        menu.openMain();
        stage(listener, "pret", 1f);
        bus.emit(EventBus.LOADING_ENDED, 1f);
        return bootOk;
    }

    private static void stage(BootListener l, String name, float p) {
        if (l != null) {
            l.stage(name, p);
        }
    }

    public boolean booted() {
        return bootOk;
    }

    /** Nouvelle partie : le ponton, 00:00, la barque. */
    public boolean newGame() {
        if (!bootOk) {
            return false;
        }
        state.setSequence(SequenceAtlas.ORDER.get(0));
        quests.restore(QuestGraph.START_NODE);
        if (!enterSequence(state.sequence(), true)) {
            return false;
        }
        mode = MODE_CINEMATIC;
        playCinematic("C01");
        return true;
    }

    /** Reprend la sauvegarde la plus recente (slot ou autosave). */
    public boolean continueGame() {
        if (!bootOk) {
            return false;
        }
        GameState loaded = saves.loadMostRecent();
        if (loaded == null) {
            return false;
        }
        state.copyFrom(loaded);
        audio.applyOptions();
        hud.applyOptions();
        quests.restore(state.questNode());
        if (!enterSequence(state.sequence(), false)) {
            return false;
        }
        lohen.reset(state.positionX(), state.positionY(), state.positionZ(), state.yaw());
        mode = MODE_PLAY;
        menu.close();
        bus.emit(EventBus.SAVE_LOADED, state.summary());
        return true;
    }

    /** Charge un slot precis (0..2). */
    public boolean loadSlot(int slot) {
        GameState loaded = saves.loadSlot(slot);
        if (loaded == null) {
            return false;
        }
        state.copyFrom(loaded);
        quests.restore(state.questNode());
        if (!enterSequence(state.sequence(), false)) {
            return false;
        }
        lohen.reset(state.positionX(), state.positionY(), state.positionZ(), state.yaw());
        mode = MODE_PLAY;
        menu.close();
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* Chargement d'une sequence                                           */
    /* ------------------------------------------------------------------ */

    /**
     * 10.02 : huit sequences, un seul niveau a la fois en memoire. Le
     * ZoneStreamer decoupe en cellules de 48 x 48 x 32 m et n'en garde que
     * deux actives (02.10).
     */
    public boolean enterSequence(String seq, boolean fromStart) {
        if (seq == null || seq.length() == 0) {
            return false;
        }
        mode = MODE_LOADING;
        if (!streamer.loadSequence(seq)) {
            mode = MODE_PLAY;
            return false;
        }
        level = streamer.level();
        if (level == null) {
            mode = MODE_PLAY;
            return false;
        }
        world.build(level);
        sequence = seq;
        state.setSequence(seq);
        npcs.setSequence(seq);
        npcs.loadFromLevel(level);
        clearFigures();
        for (int i = 0; i < triggerFired.length; i++) {
            triggerFired[i] = false;
        }
        currentTrigger = "";
        sinceEvent = 0f;

        if (fromStart) {
            lohen.reset(level.spawn[0], level.spawn[1], level.spawn[2], level.spawn[3]);
        }
        lighthouse.place(level.spawn[0], level.spawn[2]);
        ambience.setBed(bedForSequence(seq));
        ambience.setRain("S7".equals(seq));
        camera.setIndoor("S4".equals(seq) || "S5".equals(seq) || "S6".equals(seq));
        streamer.update(0f, lohen.x, lohen.y, lohen.z);
        state.setPosition(lohen.x, lohen.y, lohen.z);
        state.setAltitude(lohen.y);
        mode = MODE_PLAY;
        bus.emit(EventBus.SEQUENCE_CHANGED, seq, level.totalPrimitives());
        bus.emit(EventBus.LEVEL_LOADED, seq);
        return true;
    }

    private static String bedForSequence(String seq) {
        if ("S1".equals(seq)) {
            return "port_mort";
        }
        if ("S2".equals(seq)) {
            return "maree_verre";
        }
        if ("S3".equals(seq)) {
            return "marche_suspendu";
        }
        if ("S4".equals(seq)) {
            return "bibliotheque_eaux";
        }
        if ("S5".equals(seq)) {
            return "conduits";
        }
        if ("S6".equals(seq)) {
            return "salle_de_bal";
        }
        if ("S7".equals(seq)) {
            return "pluie_battante";
        }
        return "aube_verre";
    }

    public String sequence() {
        return sequence;
    }

    public LevelData level() {
        return level;
    }

    public int mode() {
        return mode;
    }

    public float gameTime() {
        return gameTime;
    }

    public float frameSeconds() {
        return frameSeconds;
    }

    public float lastFrameMs() {
        return lastFrameMs;
    }

    public int stepsThisFrame() {
        return stepsThisFrame;
    }

    /* ------------------------------------------------------------------ */
    /* Boucle principale                                                   */
    /* ------------------------------------------------------------------ */

    /**
     * Une image. `realDt` vient de l'horloge du systeme (Choreographer sur
     * Android). La simulation, elle, ne voit que des pas de 1/60 s.
     */
    public void frame(float realDt) {
        long t0 = System.nanoTime();
        float dt = realDt <= 0f ? FIXED_STEP : Math.min(realDt, MAX_CATCHUP);
        frameSeconds = dt;
        frames++;

        perf.submitFrame(dt * 1000f);
        thermal.update(dt);
        haptics.update(dt);
        touch.setClock(System.currentTimeMillis());

        if (mode == MODE_MENU) {
            menu.update(dt);
            music.update(dt);
            finishFrame(t0);
            return;
        }
        if (mode == MODE_LOADING) {
            streamer.update(dt, lohen.x, lohen.y, lohen.z);
            finishFrame(t0);
            return;
        }

        /* 14.05 : la pause tient en moins d'une image, flou, musique -12 dB,
         * autosave. La simulation ne tourne pas pendant ce temps. */
        if (menu.paused()) {
            menu.update(dt);
            audio.duck(MenuModel.PAUSE_MUSIC_DB, 0.08f);
            music.update(dt);
            finishFrame(t0);
            return;
        }
        audio.unduck();

        /* 00.04 : pas fixe, rendu interpole */
        float scaled = timeDilation.update(dt);
        accumulator += scaled;
        stepsThisFrame = 0;
        while (accumulator >= FIXED_STEP && stepsThisFrame < MAX_STEPS_PER_FRAME) {
            step(FIXED_STEP);
            accumulator -= FIXED_STEP;
            stepsThisFrame++;
        }
        if (accumulator > FIXED_STEP * MAX_STEPS_PER_FRAME) {
            accumulator = 0f;
        }
        timeDilation.accumulate(scaled);

        /* par image, pas par pas : camera, HUD, audio, journal */
        camera.update(dt, world, gameTime);
        hud.update(dt);
        journal.update(dt);
        menu.update(dt);
        music.setContext(state.altitude(), tension(), bossPhase(), lohen.x, lohen.z);
        music.update(dt);
        sfx.setListener(camera.yaw(), 0f);
        ambience.setAltitude(state.altitude());
        ambience.update(dt, world, lohen.x, lohen.y, lohen.z);
        rig.update(dt, lohen);
        footsteps.update(dt);
        finishFrame(t0);
    }

    private void finishFrame(long t0) {
        lastFrameMs = (System.nanoTime() - t0) * 1e-6f;
        perf.submitFrame(lastFrameMs);
    }

    /** Un pas de simulation (1/60 s). Ordre fixe, jamais remanie. */
    private void step(float dt) {
        gameTime += dt;
        state.addPlayTime(dt);
        sinceEvent += dt;

        /* 14.01 : le HUD ne montre que ce qui sert. Les boutons de
         * traversal n'existent qu'en jeu, hors menu : une cinematique, une
         * lettre ou une pause ne laissent flotter aucun bouton. */
        touch.setTraversalEnabled(mode == MODE_PLAY && !menu.open());

        readInput();

        if (mode == MODE_CINEMATIC) {
            cine.setSkipHeld(skipHeld || touch.longPressHeld(), dt);
            cine.update(dt);
            updateWorldSystems(dt);
            return;
        }
        if (mode == MODE_LETTER) {
            letter.update(dt);
            /* 19.03 / 19.05 / 19.06 : un seul bouton mene toute la scene.
             * Il ouvre le carnet (six extraits, E30), en tourne les pages,
             * puis prend la lettre et lance le rituel de 2,4 s. */
            if (letterConfirmQueued || lohen.interactBuffered
                    || lohen.jumpBuffered) {
                letterConfirmQueued = false;
                int ls = letter.step();
                if (ls == LetterReader.STEP_NOTEBOOK) {
                    letter.turnPage();
                } else if (ls == LetterReader.STEP_ROOM_FREE
                        && letter.notebookPage() < LetterReader.NOTEBOOK_EXTRACTS) {
                    letter.openNotebook();
                } else if (ls != LetterReader.STEP_READING) {
                    letter.interactWithLetter();
                }
            }
            updateWorldSystems(dt);
            return;
        }
        if (mode == MODE_CREDITS) {
            updateWorldSystems(dt);
            return;
        }

        /* 1. le joueur : FSM -> moteur -> traversee -> combat -> portage */
        fsm.update(dt);
        motor.update(dt, world);
        lohen.breath.update(dt);
        ledges.update(dt, world);
        grapple.update(dt, world);
        combat.update(dt, figures);
        carry.update(dt, lohen, motor);
        lohen.integrate(dt);
        pos[0] = lohen.x;
        pos[1] = lohen.y;
        pos[2] = lohen.z;
        if (world.resolveWalls(pos, Lohen.RADIUS, Lohen.HEIGHT, normal)) {
            lohen.x = pos[0];
            lohen.y = pos[1];
            lohen.z = pos[2];
        }

        /* 2. les Figures et leur arbitre d'attaque (12.04) */
        tokens.update(dt);
        for (int i = 0; i < figures.size(); i++) {
            Figure f = figures.get(i);
            if (!f.alive()) {
                continue;
            }
            f.update(dt, lohen, world, tokens);
        }
        perception.update(dt);

        /* 3. le monde */
        updateWorldSystems(dt);

        /* 4. la narration */
        quests.update(dt);
        if (dialogue.active()) {
            dialogue.update(dt, speakerDistance());
        }
        echo.update(dt);
        pacingWatch(dt);

        /* 5. la mort, puis les declencheurs */
        if (lohen.dead) {
            deathTimer += dt;
            if (deathTimer > HudModel.DEATH_FADE_SECONDS + 0.6f) {
                respawn();
            }
        } else {
            deathTimer = 0f;
            scanTriggers(dt);
        }

        state.setPosition(lohen.x, lohen.y, lohen.z);
        state.setAltitude(lohen.y);
        state.setYaw(camera.yaw());
        state.setPitch(camera.pitch());
    }

    private void updateWorldSystems(float dt) {
        npcs.update(dt, lohen.x, lohen.y, lohen.z);
        lighthouse.update(dt, lohen.x, lohen.y, lohen.z);
        glassSea.update(dt, world, lohen.breath, lohen.x, lohen.y, lohen.z,
                grapple.phase() == GrappleComponent.FLYING
                        || grapple.phase() == GrappleComponent.PULLING);
        streamer.update(dt, lohen.x, lohen.y, lohen.z);
    }

    /* ------------------------------------------------------------------ */
    /* Entrees (08.01 : quatre boutons, jamais cinq)                        */
    /* ------------------------------------------------------------------ */

    private void readInput() {
        boolean gamepadActive = gamepad.connected();
        float mx = gamepadActive ? gamepad.moveX() : touch.moveX();
        float my = gamepadActive ? gamepad.moveY() : touch.moveY();
        lohen.inputX = mx;
        lohen.inputY = my;

        float camX = gamepadActive ? gamepad.cameraX() : touch.consumeCameraX();
        float camY = gamepadActive ? gamepad.cameraY() : touch.consumeCameraY();
        if (camX != 0f || camY != 0f) {
            camera.applyLook(camX, camY);
        }
        lohen.cameraYaw = camera.inputYaw();
        lohen.cameraPitch = camera.inputPitch();

        /* boutons : A contextuel/saut, B grappin, C interagir/Echo, D garde */
        boolean a = gamepadActive ? gamepad.action(GamepadRouter.ACT_JUMP)
                : touch.consumeBuffered(TouchInputRouter.BTN_A);
        boolean b = gamepadActive ? gamepad.action(GamepadRouter.ACT_GRAPPLE)
                : touch.consumeBuffered(TouchInputRouter.BTN_B);
        boolean c = gamepadActive ? gamepad.action(GamepadRouter.ACT_INTERACT_ECHO)
                : touch.consumeBuffered(TouchInputRouter.BTN_C);
        boolean d = gamepadActive ? gamepad.action(GamepadRouter.ACT_GUARD_PARRY)
                : touch.isDown(TouchInputRouter.BTN_D);

        lohen.jumpBuffered = a;
        lohen.grappleBuffered = b;
        lohen.interactBuffered = c;
        lohen.guardHeld = d;
        lohen.sprintHeld = gamepadActive && gamepad.sprintToggled();
        lohen.attackBuffered = c && lohen.inCombat;

        int gesture = touch.consumeGesture();
        if (gesture == TouchInputRouter.GESTURE_DODGE_SWIPE
                || gesture == TouchInputRouter.GESTURE_DOUBLE_TAP) {
            lohen.dodgeBuffered = true;
            lohen.dodgeX = touch.gestureDirX();
            lohen.dodgeZ = touch.gestureDirY();
        } else if (gesture == TouchInputRouter.GESTURE_TWO_FINGER_DOUBLE_TAP) {
            hud.toggleByGesture();                    /* 14.01 : HUD minimal */
        } else if (gesture == TouchInputRouter.GESTURE_LONG_PRESS) {
            requestSkip();                            /* 07.22 */
        }

        if (b && grapple.phase() == GrappleComponent.IDLE) {
            lohen.forward(aim);
            Vec3 look = camera.target();
            float dx = look.x - lohen.eyeX();
            float dy = look.y - lohen.eyeY();
            float dz = look.z - lohen.eyeZ();
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len > 0.001f) {
                grapple.fire(world, dx / len, dy / len, dz / len);
            }
        }
        if (c && !lohen.inCombat) {
            interact();
        }
        if (d && !carry.allowsGuard()) {
            lohen.guardHeld = false;
        }
        fsm.clearPerFrame();
    }

    /** 07.22 : appui long = skip, avec fondu de 0,4 s et etat final applique. */
    private void requestSkip() {
        /* 07.22 : l'appui long est tenu, jamais un tap simple. Le compteur
         * de 1,5 s (20 s pour la lettre) est tenu par le lecteur lui-meme. */
        skipHeld = true;
    }

    /**
     * Le joueur confirme dans la lettre : un tap au centre du papier, le
     * bouton A d'une manette, ou Entree au clavier. La meme porte, la meme
     * page, le meme pli — selon l'etape ou la scene en est (19.02 a 19.10).
     */
    public void letterConfirm() {
        letterConfirmQueued = true;
    }

    /** Relache l'appui long (lever de doigt) : le skip est rearme. */
    public void releaseSkip() {
        skipHeld = false;
    }

    /* ------------------------------------------------------------------ */
    /* Interaction contextuelle                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Le verbe est decide par le monde, jamais par un bouton dedie :
     * Echo, objet a porter, PNJ, porte, levier, checkpoint.
     */
    public void interact() {
        if (interactTarget.length() == 0) {
            hud.noteVerbFailure(interactVerb);
            bus.emit(EventBus.VERB_USED, interactVerb, 0);
            return;
        }
        String verb = interactVerb;
        String target = interactTarget;
        bus.emit(EventBus.VERB_USED, verb, 1);
        hud.onVerbUsed(verb);
        if ("echo".equals(verb)) {
            /* 11.03 : les 2,4 s du gant, jamais raccourcies */
            if (echo.begin(target)) {
                fsm.beginEcho();
            }
        } else if ("talk".equals(verb)) {
            dialogue.start(target);
            fsm.beginDialogue();
        } else if ("carry".equals(verb)) {
            carry.pickUp(target);
        } else if ("cine".equals(verb)) {
            playCinematic(target);
        } else if ("sequence".equals(verb)) {
            enterSequence(target, true);
        } else if ("letter".equals(verb)) {
            beginLetter();
        } else if ("setdown".equals(verb)) {
            carry.setDown();
        } else if ("swap".equals(verb)) {
            carry.swapHand();
        }
    }

    /** Cherche la cible la plus proche et met a jour l'invite du HUD. */
    private void scanInteractions() {
        interactTarget = "";
        interactVerb = "";
        interactDistance = Float.MAX_VALUE;
        if (level == null || lohen.dead) {
            hud.showPrompt("");
            return;
        }
        /* les points d'Echo (11.02 : approchables a moins de 1,5 m) */
        for (int i = 0; i < level.echoPoints.size(); i++) {
            LevelData.EchoPoint ep = level.echoPoints.get(i);
            float d = distTo(ep.x, ep.y, ep.z);
            if (d < Math.max(ECHO_RADIUS, ep.radius) && d < interactDistance) {
                interactDistance = d;
                interactTarget = ep.echoId;
                interactVerb = state.hasReadEcho(ep.echoId) ? "echo_reread" : "echo";
            }
        }
        /* les objets portables (08.11 V6) */
        for (int i = 0; i < level.propCount; i++) {
            ContentDb.PropRecord p = db.prop(level.propIds[i]);
            if (p == null || !p.pickup) {
                continue;
            }
            float px = level.propData[i * 5];
            float py = level.propData[i * 5 + 1];
            float pz = level.propData[i * 5 + 2];
            float d = distTo(px, py, pz);
            if (d < CARRY_RADIUS && d < interactDistance) {
                interactDistance = d;
                interactTarget = p.slug;
                interactVerb = "carry";
            }
        }
        /* les PNJ majeurs */
        for (int i = 0; i < level.npcs.size(); i++) {
            LevelData.NpcSpawn n = level.npcs.get(i);
            if (n.dialogue.length() == 0) {
                continue;
            }
            float d = distTo(n.x, n.y, n.z);
            if (d < NPC_TALK_RADIUS && d < interactDistance) {
                interactDistance = d;
                interactTarget = n.dialogue;
                interactVerb = "talk";
            }
        }
        if (carry.carrying() && interactTarget.length() == 0) {
            interactVerb = "setdown";
            interactTarget = carry.carriedId();
            interactDistance = 0f;
        }
        hud.showPrompt(interactVerb);
    }

    private float distTo(float x, float y, float z) {
        float dx = x - lohen.x;
        float dy = y - (lohen.y + Lohen.HIP_HEIGHT);
        float dz = z - lohen.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private float speakerDistance() {
        com.velmora.lohen.sim.world.Npc n = npcs.nearest(lohen.x, lohen.y, lohen.z, 40f);
        if (n == null) {
            return 0f;
        }
        float dx = n.x - lohen.x;
        float dy = n.y - lohen.y;
        float dz = n.z - lohen.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /* ------------------------------------------------------------------ */
    /* Declencheurs de niveau                                              */
    /* ------------------------------------------------------------------ */

    private void scanTriggers(float dt) {
        if (level == null) {
            return;
        }
        scanInteractions();
        for (int i = 0; i < level.triggers.size() && i < triggerFired.length; i++) {
            LevelData.Trigger t = level.triggers.get(i);
            boolean inside = insideBox(t);
            if (inside && !triggerFired[i]) {
                triggerFired[i] = t.oneShot;
                currentTrigger = t.id;
                fireTrigger(t);
            } else if (!inside && triggerFired[i] && !t.oneShot) {
                triggerFired[i] = false;
                bus.emit(EventBus.TRIGGER_EXITED, t.id);
            }
        }
        /* rencontres : les Figures n'existent que dans leur rayon (02.10) */
        for (int i = 0; i < level.encounters.size(); i++) {
            LevelData.Encounter e = level.encounters.get(i);
            float d = distTo(e.x, e.y, e.z);
            if (d < e.radius && !state.hasFlag("enc_" + e.id)) {
                state.setFlag("enc_" + e.id);
                spawnEncounter(e);
            } else if (d > e.radius * 2.5f) {
                despawnFar(e);
            }
        }
        /* raccourcis et secrets */
        for (int i = 0; i < level.shortcuts.size(); i++) {
            LevelData.ShortcutDef s = level.shortcuts.get(i);
            if (!state.hasFlag("sc_" + s.id) && distTo(s.x, s.y, s.z) < s.radius) {
                openShortcut(s);
            }
        }
        for (int i = 0; i < level.secrets.size(); i++) {
            LevelData.SecretDef s = level.secrets.get(i);
            if (!state.hasFlag("sec_" + s.id) && distTo(s.x, s.y, s.z) < s.radius) {
                findSecret(s);
            }
        }
        /* 10.02 : la sortie de sequence est un chemin, pas un mur */
        if (pendingSequence.length() > 0 && distTo(level.spawn[0], level.spawn[1],
                level.spawn[2]) > 0f) {
            String next = pendingSequence;
            pendingSequence = "";
            enterSequence(next, true);
        }
    }

    private boolean insideBox(LevelData.Trigger t) {
        float dx = Math.abs(lohen.x - t.x);
        float dy = Math.abs((lohen.y + Lohen.HIP_HEIGHT) - t.y);
        float dz = Math.abs(lohen.z - t.z);
        return dx <= t.rx && dy <= t.ry && dz <= t.rz;
    }

    private void fireTrigger(LevelData.Trigger t) {
        bus.emit(EventBus.TRIGGER_ENTERED, t.id, t.type);
        sinceEvent = 0f;
        String type = t.type;
        if ("checkpoint".equals(type)) {
            if (state.markCheckpoint(t.target)) {
                bus.emit(EventBus.CHECKPOINT_REACHED, t.target);
                saves.autosave(state, "checkpoint " + t.target);
            }
        } else if ("corridor".equals(type)) {
            bus.emit(EventBus.ZONE_ENTERED, t.target);
            ambience.setReverb(t.target);
        } else if ("silence".equals(type)) {
            ambience.triggerSilence(t.target);
        } else if ("cinematic".equals(type)) {
            playCinematic(t.target);
        } else if ("dialogue".equals(type)) {
            dialogue.start(t.target);
        } else if ("teach".equals(type)) {
            bus.emit(EventBus.PROMPT_REQUESTED, t.target, t.value);
        } else if ("sequence".equals(type)) {
            pendingSequence = t.target;
        } else if ("combat".equals(type)) {
            for (int i = 0; i < level.encounters.size(); i++) {
                if (level.encounters.get(i).id.equals(t.target)) {
                    spawnEncounter(level.encounters.get(i));
                }
            }
        } else if ("fog".equals(type)) {
            bus.emit(EventBus.FOG_CLEARED, t.value);
        } else if ("letter".equals(type)) {
            beginLetter();
        } else if ("lamp_room".equals(type)) {
            bus.emit(EventBus.ZONE_ENTERED, t.target);
            camera.setIndoor(true);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Rencontres (12.02 : 14 rencontres, 2 types de Figures)               */
    /* ------------------------------------------------------------------ */

    private void spawnEncounter(LevelData.Encounter e) {
        for (int i = 0; i < e.enemies.size(); i++) {
            String kind = e.enemies.get(i);
            Figure f = createFigure(kind);
            if (f == null) {
                continue;
            }
            float ox = e.x + rng.range(-2.5f, 2.5f);
            float oz = e.z + rng.range(-2.5f, 2.5f);
            float oy = world.groundHeight(ox, oz, e.y + 2f, 0.4f, null);
            if (Float.isNaN(oy)) {
                oy = e.y;
            }
            f.configure(figures.size(), e.id + "_" + i, ox, oy, oz,
                    (float) Math.toDegrees(Math.atan2(lohen.x - ox, lohen.z - oz)));
            if (f instanceof VerrierBoss) {
                VerrierBoss v = (VerrierBoss) f;
                v.setArena(e.x, e.z, e.x, e.z);
            }
            figures.add(f);
            tokens.register(f);
        }
        if (!figures.isEmpty()) {
            lohen.inCombat = true;
            touch.setCombatMode(true);
            bus.emit(EventBus.COMBAT_ENTERED, e.id, e.enemies.size());
        }
    }

    private Figure createFigure(String kind) {
        if (kind == null) {
            return null;
        }
        String k = kind.toLowerCase();
        if (k.contains("echassier") || k.contains("stilt")) {
            return new Echassier(tuning, bus, rng);
        }
        if (k.contains("mueur") || k.contains("mourner")) {
            return new Mueur(tuning, bus, rng);
        }
        if (k.contains("verrier") || k.contains("glassblower") || k.contains("boss")) {
            return new VerrierBoss(tuning, bus, rng);
        }
        return null;
    }

    private void despawnFar(LevelData.Encounter e) {
        for (int i = figures.size() - 1; i >= 0; i--) {
            Figure f = figures.get(i);
            if (f.instanceId() != null && f.instanceId().startsWith(e.id)
                    && !f.alive()) {
                tokens.unregister(f);
                figures.remove(i);
            }
        }
        if (figures.isEmpty() && lohen.inCombat) {
            lohen.inCombat = false;
            touch.setCombatMode(false);
            bus.emit(EventBus.COMBAT_CLEARED, e.id);
        }
    }

    private void clearFigures() {
        for (int i = 0; i < figures.size(); i++) {
            tokens.unregister(figures.get(i));
        }
        figures.clear();
        tokens.clear();
        lohen.inCombat = false;
        touch.setCombatMode(false);
    }

    private int bossPhase() {
        for (int i = 0; i < figures.size(); i++) {
            Figure f = figures.get(i);
            if (f instanceof VerrierBoss) {
                return ((VerrierBoss) f).phase();
            }
        }
        return 0;
    }

    private float tension() {
        if (lohen.inCombat) {
            return 1f;
        }
        if (echo.active()) {
            return 0.6f;
        }
        if (dialogue.active()) {
            return 0.35f;
        }
        return Math.min(1f, perception.darkness() * 0.6f
                + (1f - lohen.breath.fraction()) * 0.4f);
    }

    /* ------------------------------------------------------------------ */
    /* Raccourcis, secrets, checkpoints                                    */
    /* ------------------------------------------------------------------ */

    private void openShortcut(LevelData.ShortcutDef s) {
        state.setFlag("sc_" + s.id);
        state.markShortcut(s.id);
        bus.emit(EventBus.SHORTCUT_OPENED, s.id, s.kind);
        sinceEvent = 0f;
    }

    private void findSecret(LevelData.SecretDef s) {
        state.setFlag("sec_" + s.id);
        state.markSecret(s.id);
        if ("breath_upgrade".equals(s.kind)) {
            state.applyBreathUpgrade();                 /* 08.13 : 100 -> 130 */
        } else if ("letter_fragment".equals(s.kind)) {
            bus.emit(EventBus.LETTER_FOUND, s.payload);
        } else if ("journal_object".equals(s.kind)) {
            state.addObjectFromFigure(s.payload);
        }
        bus.emit(EventBus.SECRET_FOUND, s.id, s.kind);
        journal.update(0f);
        sinceEvent = 0f;
    }

    /** Respawn au dernier checkpoint, sans compteur de morts affiche (14.11). */
    public void respawn() {
        state.incrementDeaths();
        String cp = state.lastCheckpoint();
        int idx = level == null || cp == null ? -1 : level.checkpointIndex(cp);
        float x, y, z, yaw;
        if (idx >= 0) {
            x = level.checkpointData[idx * 4];
            y = level.checkpointData[idx * 4 + 1];
            z = level.checkpointData[idx * 4 + 2];
            yaw = level.checkpointData[idx * 4 + 3];
        } else if (level != null) {
            x = level.spawn[0];
            y = level.spawn[1];
            z = level.spawn[2];
            yaw = level.spawn[3];
        } else {
            x = y = z = yaw = 0f;
        }
        lohen.reset(x, y, z, yaw);
        lohen.dead = false;
        lohen.breath.gain(lohen.breath.max());
        hud.endDeath();
        bus.emit(EventBus.RESPAWN, cp == null ? "spawn" : cp);
        saves.autosave(state, "respawn");
    }

    /* ------------------------------------------------------------------ */
    /* Cinematiques, Echo, lettre                                          */
    /* ------------------------------------------------------------------ */

    public boolean playCinematic(String id) {
        if (id == null || id.length() == 0) {
            return false;
        }
        pendingCinematic = id;
        if (cine.play(id)) {
            mode = MODE_CINEMATIC;
            lohen.inCinematic = true;
            fsm.beginCinematic();
            timeDilation.setCinematicScale(1f);
            bus.emit(EventBus.CINEMATIC_STARTED, id);
            return true;
        }
        pendingCinematic = "";
        return false;
    }

    public String pendingCinematic() {
        return pendingCinematic;
    }

    /** 19.01 : la finale. Neuf minutes quarante, jamais skippable avant 20 s. */
    public boolean beginLetter() {
        if (mode == MODE_LETTER) {
            return true;
        }
        if (!letter.loadLetter()) {
            return false;
        }
        if (letter.begin()) {
            mode = MODE_LETTER;
            fsm.beginLetter();
            bus.emit(EventBus.LETTER_STARTED, letter.blockCount());
            return true;
        }
        return false;
    }

    /* ------------------------------------------------------------------ */
    /* Evenements                                                          */
    /* ------------------------------------------------------------------ */

    @Override
    public void onEvent(String signal, Object[] args) {
        if (EventBus.PLAYER_DIED.equals(signal)) {
            lohen.dead = true;
            hud.beginDeath();
            timeDilation.setDeathSlowmo(1.2f);
            bus.emit(EventBus.DEATH_FADE_BEGIN);
            for (int i = 0; i < figures.size(); i++) {
                if (figures.get(i) instanceof VerrierBoss) {
                    ((VerrierBoss) figures.get(i)).onPlayerDeath();
                }
            }
        } else if (EventBus.CINEMATIC_ENDED.equals(signal)
                || EventBus.CINEMATIC_SKIPPED.equals(signal)) {
            pendingCinematic = "";
            lohen.inCinematic = false;
            if (mode == MODE_CINEMATIC) {
                mode = MODE_PLAY;
                bus.emit(EventBus.CONTROL_RESTORED);
            }
        } else if (EventBus.CINEMATIC_FINAL_STATE.equals(signal)) {
            applyCinematicFinalState();
        } else if (EventBus.ECHO_STARTED.equals(signal)) {
            lohen.inEcho = true;
            timeDilation.setEcho(true);
            rig.setGloves(false);
        } else if (EventBus.ECHO_FINISHED.equals(signal)) {
            lohen.inEcho = false;
            timeDilation.setEcho(false);
            rig.setGloves(true);
            fsm.endEcho();
        } else if (EventBus.DIALOGUE_STARTED.equals(signal)) {
            lohen.inDialogue = true;
            touch.setUiCapture(true);
        } else if (EventBus.DIALOGUE_FINISHED.equals(signal)) {
            lohen.inDialogue = false;
            touch.setUiCapture(false);
            camera.clearDialogueSubject();
        } else if (EventBus.CHECKPOINT_REACHED.equals(signal)) {
            hud.showPrompt("");
        } else if (EventBus.SEQUENCE_CHANGED.equals(signal)) {
            hud.setMode(options.hudMode);
        } else if (EventBus.PAUSE_TOGGLED.equals(signal)) {
            audio.applyOptions();
        } else if (EventBus.MENU_ACTION.equals(signal)) {
            onMenuAction(args);
        } else if (EventBus.CREDITS_BEGIN.equals(signal)) {
            mode = MODE_CREDITS;
        } else if (EventBus.CREDITS_END.equals(signal)) {
            menu.setChapterComplete(true);
            bus.emit(EventBus.CHAPTER_COMPLETE);
        } else if (EventBus.CHAPTER_COMPLETE.equals(signal)) {
            state.setFlag("chapter1_complete");
            saves.autosave(state, "chapitre termine");
        } else if (EventBus.BOSS_DEFEATED.equals(signal)) {
            clearFigures();
        } else if (EventBus.PACING_HOLE.equals(signal)) {
            pacingHoles++;
        } else if (EventBus.OPTIONS_CHANGED.equals(signal)) {
            options.clampToSpec();
            audio.applyOptions();
            hud.applyOptions();
            touch.refreshLayout();
            perf.refreshFromOptions();
        } else if (EventBus.CONTROL_RESTORED.equals(signal)) {
            if (mode == MODE_CINEMATIC) {
                mode = MODE_PLAY;
            }
        } else if (EventBus.LETTER_STEP.equals(signal)) {
            if (args != null && args.length > 0 && "STEP_CREDITS".equals(String.valueOf(args[0]))) {
                mode = MODE_CREDITS;
            }
        }
    }

    private void onMenuAction(Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        String action = String.valueOf(args[0]);
        if ("new_game".equals(action)) {
            newGame();
        } else if ("continue".equals(action)) {
            if (!continueGame()) {
                newGame();
            }
        } else if ("resume".equals(action)) {
            menu.togglePause();
        } else if ("save".equals(action) && args.length > 1) {
            int slot = args[1] instanceof Number ? ((Number) args[1]).intValue() : menu.slot();
            saves.saveToSlot(slot, state);
        } else if ("load".equals(action) && args.length > 1) {
            int slot = args[1] instanceof Number ? ((Number) args[1]).intValue() : menu.slot();
            loadSlot(slot);
        } else if ("quit_to_menu".equals(action)) {
            saves.autosave(state, "retour au menu");
            mode = MODE_MENU;
            menu.openMain();
        }
    }

    /** 07.22 : le skip applique l'ETAT FINAL (position, drapeaux, objets). */
    private void applyCinematicFinalState() {
        String id = pendingCinematic;
        if (id.length() == 0) {
            return;
        }
        if ("C01".equals(id)) {
            state.setFlag("c01_arrived");
            quests.advance();
        } else if ("C03".equals(id)) {
            state.setFlag("c03_marche_vu");
        } else if ("C07".equals(id)) {
            state.setFlag("c07_esteban_vu");
        } else if ("C08".equals(id)) {
            state.setFlag("c08_echo_effondre");
        } else if ("C09".equals(id)) {
            state.setFlag("c09_dossier_recu");
            state.addObjectFromFigure("dossier_verrier");
        }
        mode = MODE_PLAY;
        lohen.inCinematic = false;
        pendingCinematic = "";
    }

    /* ------------------------------------------------------------------ */
    /* Rythme (09.04)                                                      */
    /* ------------------------------------------------------------------ */

    private void pacingWatch(float dt) {
        if (mode != MODE_PLAY || lohen.dead) {
            return;
        }
        if (sinceEvent > SequenceAtlas.EVENT_BUG_SECONDS) {
            sinceEvent = 0f;
            bus.emit(EventBus.PACING_HOLE, SequenceAtlas.EVENT_BUG_SECONDS);
        }
    }

    /** Le verbe actuellement propose : le HUD en tire un glyphe (14.07). */
    public String interactVerb() {
        return interactVerb;
    }

    /** La cible du verbe : un Echo, une scene, un objet, une sequence. */
    public String interactTarget() {
        return interactTarget;
    }

    /** Un slot ou un autosave existe : le « Continuer » du menu en depend. */
    public boolean hasSave() {
        for (int i = 0; i < SaveSystem.SLOTS; i++) {
            if (saves.hasSlot(i)) {
                return true;
            }
        }
        return saves.loadLatestAutosave() != null;
    }

    /** Description d'un emplacement pour l'ecran des sauvegardes (14.05). */
    public String describeSlot(int slot) {
        java.util.Map<String, Object> d = saves.describeSlot(slot);
        if (d == null || d.isEmpty()) {
            return "\u2014";
        }
        StringBuilder sb = new StringBuilder(96);
        Object seq = d.get("sequence");
        Object time = d.get("play_time");
        Object node = d.get("quest_node");
        if (seq != null) {
            sb.append(seq);
        }
        if (time instanceof Number) {
            int minutes = (int) (((Number) time).floatValue() / 60f);
            sb.append(" \u00b7 ").append(minutes).append(" min");
        }
        if (node != null) {
            sb.append(" \u00b7 ").append(node);
        }
        return sb.length() == 0 ? "\u2014" : sb.toString();
    }

    public float secondsSinceEvent() {
        return sinceEvent;
    }

    public void noteEvent() {
        sinceEvent = 0f;
    }

    public int pacingHoles() {
        return pacingHoles;
    }

    /* ------------------------------------------------------------------ */
    /* Pause, sauvegarde, rendu                                            */
    /* ------------------------------------------------------------------ */

    /** 14.05 : la pause est instantanee (< 1 image) et sauvegarde. */
    public boolean togglePause() {
        if (mode == MODE_MENU || mode == MODE_LETTER || mode == MODE_CREDITS) {
            return false;
        }
        boolean now = menu.togglePause();
        if (now) {
            saves.autosave(state, "pause");
        }
        return now;
    }

    public boolean paused() {
        return menu.paused();
    }

    public boolean saveToSlot(int slot) {
        state.setPosition(lohen.x, lohen.y, lohen.z);
        state.setYaw(camera.yaw());
        return saves.saveToSlot(slot, state);
    }

    /** Le rendu lit directement ces matrices (02.11 : 60 fps cible). */
    public void viewMatrix(float[] out16) {
        camera.viewMatrix(out16);
    }

    public void projectionMatrix(float[] out16, float aspect) {
        camera.projectionMatrix(out16, aspect);
    }

    public float fov() {
        return camera.fov();
    }

    public Vec3 cameraPosition() {
        return camera.position();
    }

    public int frames() {
        return frames;
    }

    /** Rendu audio : un bloc de 16 bits stereo (13.02 : -16 LUFS). */
    public int renderAudio(short[] out) {
        return audio.render(out);
    }

    /* ------------------------------------------------------------------ */
    /* Conformite                                                          */
    /* ------------------------------------------------------------------ */

    /** Les invariants que l'orchestrateur doit garantir, verifies au boot. */
    public static boolean specCompliant() {
        return Math.abs(FIXED_STEP - 1f / 60f) < 1e-6f
                && MAX_STEPS_PER_FRAME >= 4
                && ECHO_RADIUS <= 1.5f + 1e-6f
                && TouchInputRouter.BUTTON_COUNT <= 4
                && MenuModel.PAUSE_MAX_FRAME_SECONDS <= 1f / 60f + 1e-6f
                && CinematicPlayer.SKIP_HOLD == 1.5f
                && CinematicPlayer.SKIP_HOLD_LETTER == 20f
                && EchoSystem.RITUAL_TOTAL == 2.4f
                && SequenceAtlas.ORDER.size() == 8;
    }

    /** Etat lisible pour les tests et le journal de bord. */
    public String describe() {
        StringBuilder sb = new StringBuilder(160);
        sb.append("mode=").append(modeName(mode))
                .append(" seq=").append(sequence)
                .append(" pos=").append((int) lohen.x).append(',')
                .append((int) lohen.y).append(',')
                .append((int) lohen.z)
                .append(" figures=").append(figures.size())
                .append(" souffle=").append((int) lohen.breath.current())
                .append(" t=").append((int) gameTime);
        return sb.toString();
    }

    public static String modeName(int m) {
        switch (m) {
            case MODE_MENU: return "MENU";
            case MODE_PLAY: return "JEU";
            case MODE_CINEMATIC: return "CINEMATIQUE";
            case MODE_LETTER: return "LETTRE";
            case MODE_CREDITS: return "GENERIQUE";
            case MODE_LOADING: return "CHARGEMENT";
            default: return "?";
        }
    }
}
