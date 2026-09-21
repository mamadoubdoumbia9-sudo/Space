/*
 * LOHEN — sim/ui/MenuModel.java
 *
 * 14.05 MENU PRINCIPAL [ART] : pas d'illustration fixe. Une scene 3D temps
 * reel : le ponton de S1, au petit matin, vide, avec la brume qui bouge et
 * le Phare qui balaye. Les options sont ecrites a plat sur le bois du
 * ponton, en perspective. Un `Continuer` ambre. Le reste en blanc casse.
 * Quand le joueur a fini le chapitre, la scene change : c'est le sommet du
 * Phare, a l'aube, avec la lampe allumee. Personne ne previent.
 *
 * 14.09 PAUSE : instantanee (< 1 frame), avec flou de fond, et la musique
 * continue attenuee de -12 dB (couper la musique en pause casse
 * l'immersion). Le jeu s'autosave a l'ouverture de la pause.
 *
 * 14.12 OPTIONS — arborescence complete : IMAGE, SON, JEU, CONTROLES,
 * ACCESSIBILITE, DONNEES.
 *
 * 14.06 TYPOGRAPHIE : interface et sous-titres en Source Serif 4 (17 sp
 * minimum, 21 sp par defaut, reglable jusqu'a 30 sp) ; titres de sequence en
 * Cormorant Garamond, petites capitales, espacement +8 %.
 */
package com.velmora.lohen.sim.ui;

import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.Localization;
import com.velmora.lohen.sim.core.Options;
import com.velmora.lohen.sim.core.SaveSystem;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.narrative.JournalModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MenuModel implements EventBus.Listener {

    /* ---------------- 14.09 : la pause ---------------- */
    public static final float PAUSE_MAX_FRAME_SECONDS = 1f / 60f;   /* < 1 frame */
    public static final float PAUSE_MUSIC_DB = -12f;
    public static final float PAUSE_BLUR = 0.85f;
    public static final boolean PAUSE_AUTOSAVES = true;

    /* ---------------- 14.05 : le menu principal ---------------- */
    public static final String SCENE_PONTON = "menu_ponton_s1";
    public static final String SCENE_PHARE = "menu_sommet_phare";
    public static final int COLOR_AMBER = 0xFFA33C;         /* le Continuer, seul */
    public static final int COLOR_OFFWHITE = 0xEDE6DA;      /* tout le reste */
    public static final boolean MENU_HAS_FIXED_ILLUSTRATION = false;

    /* ---------------- 14.06 : typographie ---------------- */
    public static final String FONT_UI = "source_serif_4";
    public static final String FONT_TITLES = "cormorant_garamond";
    public static final String FONT_LOHEN_HAND = "caveat";
    public static final String FONT_ESTEBAN_HAND = "rouge_script";
    public static final int SUBTITLE_MIN_SP = 17;
    public static final int SUBTITLE_DEFAULT_SP = 21;
    public static final int SUBTITLE_MAX_SP = 30;
    public static final float TITLE_TRACKING = 1.08f;       /* espacement +8 % */
    public static final boolean TITLE_SMALL_CAPS = true;

    /* ---------------- 14.12 : les six familles d'options ---------------- */
    public static final int PAGE_IMAGE = 0;
    public static final int PAGE_SOUND = 1;
    public static final int PAGE_GAME = 2;
    public static final int PAGE_CONTROLS = 3;
    public static final int PAGE_ACCESSIBILITY = 4;
    public static final int PAGE_DATA = 5;
    public static final int PAGE_COUNT = 6;

    public static final String[] PAGE_KEYS = {
            "options.image", "options.sound", "options.game",
            "options.controls", "options.accessibility", "options.data",
    };
    public static final String[] PAGE_NAMES = {
            "IMAGE", "SON", "JEU", "CONTROLES", "ACCESSIBILITE", "DONNEES",
    };

    /* ---------------- une ligne d'option ---------------- */
    public static final class Row {
        public static final int KIND_TOGGLE = 0;
        public static final int KIND_SLIDER = 1;
        public static final int KIND_CHOICE = 2;
        public static final int KIND_ACTION = 3;

        public final String key;          /* cle de localisation */
        public final String field;        /* champ de Options, ou action */
        public final int kind;
        public final float min;
        public final float max;
        public final float step;
        public final String[] choices;
        public String note;

        Row(String key, String field, int kind, float min, float max, float step,
            String[] choices) {
            this.key = key;
            this.field = field;
            this.kind = kind;
            this.min = min;
            this.max = max;
            this.step = step;
            this.choices = choices;
        }

        static Row toggle(String key, String field) {
            return new Row(key, field, KIND_TOGGLE, 0f, 1f, 1f, null);
        }

        static Row slider(String key, String field, float min, float max, float step) {
            return new Row(key, field, KIND_SLIDER, min, max, step, null);
        }

        static Row choice(String key, String field, String[] choices) {
            return new Row(key, field, KIND_CHOICE, 0f, choices.length - 1, 1f, choices);
        }

        static Row action(String key, String action) {
            return new Row(key, action, KIND_ACTION, 0f, 0f, 0f, null);
        }
    }

    public static final Map<Integer, List<Row>> PAGES = new LinkedHashMap<Integer, List<Row>>();

    static {
        List<Row> image = new ArrayList<Row>(14);
        image.add(Row.choice("opt.quality", "quality",
                new String[]{"Auto", "Bas", "Moyen", "Haut"}));
        image.add(Row.slider("opt.render_scale", "renderScale", 0.5f, 1.0f, 0.05f));
        image.add(Row.choice("opt.target_fps", "targetFps",
                new String[]{"30", "45", "60", "Illimite"}));
        image.add(Row.toggle("opt.shadows", "shadows"));
        image.add(Row.toggle("opt.fog", "fog"));
        image.add(Row.toggle("opt.effects", "effects"));
        image.add(Row.toggle("opt.grain", "grain"));
        image.add(Row.toggle("opt.chromatic", "chromatic"));
        image.add(Row.toggle("opt.vignette", "vignette"));
        image.add(Row.toggle("opt.bloom", "bloom"));
        image.add(Row.toggle("opt.motion_blur", "motionBlur"));
        image.add(Row.slider("opt.brightness", "brightness", 0.6f, 1.6f, 0.05f));
        image.add(Row.toggle("opt.hdr", "hdr"));
        image.add(Row.action("opt.brightness_test_pattern", "showBrightnessPattern"));
        PAGES.put(Integer.valueOf(PAGE_IMAGE), image);

        List<Row> sound = new ArrayList<Row>(10);
        sound.add(Row.slider("opt.vol_master", "volMaster", 0f, 1f, 0.05f));
        sound.add(Row.slider("opt.vol_music", "volMusic", 0f, 1f, 0.05f));
        sound.add(Row.slider("opt.vol_sfx", "volSfx", 0f, 1f, 0.05f));
        sound.add(Row.slider("opt.vol_vo", "volVo", 0f, 1f, 0.05f));
        sound.add(Row.slider("opt.vol_amb", "volAmb", 0f, 1f, 0.05f));
        sound.add(Row.slider("opt.vol_ducking", "volDucking", 0f, 1f, 0.05f));
        sound.add(Row.choice("opt.output_preset", "outputPreset",
                new String[]{"Auto", "Casque", "Haut-parleur"}));
        sound.add(Row.choice("opt.vo_language", "voLanguage", new String[]{"fr"}));
        sound.add(Row.toggle("opt.subtitles", "subtitles"));
        PAGES.put(Integer.valueOf(PAGE_SOUND), sound);

        List<Row> game = new ArrayList<Row>(8);
        game.add(Row.choice("opt.combat_difficulty", "combatDifficulty",
                new String[]{"Calme", "Soutenu", "Brutal"}));
        game.add(Row.choice("opt.traversal_assist", "traversalAssist",
                new String[]{"Normal", "Genereux", "Automatique"}));
        game.add(Row.choice("opt.prompts", "prompts",
                new String[]{"Auto", "Toujours", "Jamais"}));
        game.add(Row.choice("opt.hud_mode", "hudMode",
                new String[]{"Complet", "Minimal", "Aucun"}));
        game.add(Row.toggle("opt.auto_recenter", "autoRecenter"));
        game.add(Row.slider("opt.camera_shake", "cameraShake", 0f, 1f, 0.05f));
        game.add(Row.toggle("opt.narration_only", "narrationOnly"));
        PAGES.put(Integer.valueOf(PAGE_GAME), game);

        List<Row> controls = new ArrayList<Row>(9);
        controls.add(Row.slider("opt.button_scale", "buttonScale", 0.8f, 1.4f, 0.05f));
        controls.add(Row.toggle("opt.tap_instead_of_hold", "tapInsteadOfHold"));
        controls.add(Row.slider("opt.sensitivity_x", "sensitivityX", 0.4f, 2.0f, 0.05f));
        controls.add(Row.slider("opt.sensitivity_y", "sensitivityY", 0.4f, 2.0f, 0.05f));
        controls.add(Row.toggle("opt.invert_x", "invertX"));
        controls.add(Row.toggle("opt.invert_y", "invertY"));
        controls.add(Row.toggle("opt.gamepad", "gamepadEnabled"));
        controls.add(Row.slider("opt.vibration", "vibration", 0f, 1f, 0.05f));
        controls.add(Row.action("opt.edit_layout", "editLayout"));
        PAGES.put(Integer.valueOf(PAGE_CONTROLS), controls);

        List<Row> access = new ArrayList<Row>(11);
        access.add(Row.slider("opt.subtitle_size", "subtitleSizeSp",
                SUBTITLE_MIN_SP, SUBTITLE_MAX_SP, 1f));
        access.add(Row.toggle("opt.subtitle_opaque_bg", "subtitleOpaqueBg"));
        access.add(Row.toggle("opt.subtitle_speaker", "subtitleSpeaker"));
        access.add(Row.toggle("opt.subtitle_extended", "subtitleExtended"));
        access.add(Row.choice("opt.colorblind", "colorblindMode",
                new String[]{"Aucun", "Protanopie", "Deuteranopie", "Tritanopie"}));
        access.add(Row.action("opt.remap_amber", "remapAmber"));
        access.add(Row.action("opt.remap_cyan", "remapCyan"));
        access.add(Row.toggle("opt.reduced_flashes", "reducedFlashes"));
        access.add(Row.toggle("opt.wide_parry_window", "wideParryWindow"));
        access.add(Row.choice("opt.language", "language",
                new String[]{"fr", "en", "es", "de", "ja"}));
        PAGES.put(Integer.valueOf(PAGE_ACCESSIBILITY), access);

        List<Row> data = new ArrayList<Row>(6);
        data.add(Row.action("opt.slot_1", "selectSlot1"));
        data.add(Row.action("opt.slot_2", "selectSlot2"));
        data.add(Row.action("opt.slot_3", "selectSlot3"));
        data.add(Row.action("opt.export_save", "exportSave"));
        data.add(Row.action("opt.import_save", "importSave"));
        data.add(Row.action("opt.erase_save", "eraseSave"));
        PAGES.put(Integer.valueOf(PAGE_DATA), data);
    }

    /* ---------------- ecrans ---------------- */
    public static final int SCREEN_NONE = -1;
    public static final int SCREEN_MAIN = 0;
    public static final int SCREEN_PAUSE = 1;
    public static final int SCREEN_OPTIONS = 2;
    public static final int SCREEN_JOURNAL = 3;
    public static final int SCREEN_SAVES = 4;

    private final Options options;
    private final EventBus bus;
    private final Localization loc;
    private final JournalModel journal;
    private final SaveSystem saves;

    private int screen = SCREEN_NONE;
    private int page = PAGE_IMAGE;
    private int row;
    private boolean paused;
    private float pauseFrameMs;
    private float musicDb;
    private float blur;
    private float openAnim;
    private boolean chapterComplete;
    private String menuScene = SCENE_PONTON;
    private int slot = 1;
    private int changesApplied;
    private int autosavesOnPause;
    private boolean brightnessPattern;
    private boolean editingLayout;

    public MenuModel(Options options, EventBus bus, Localization loc, JournalModel journal,
                     SaveSystem saves) {
        this.options = options;
        this.bus = bus;
        this.loc = loc;
        this.journal = journal;
        this.saves = saves;
        if (bus != null) {
            bus.connect(EventBus.PAUSE_TOGGLED, this);
            bus.connect(EventBus.MENU_OPENED, this);
            bus.connect(EventBus.MENU_CLOSED, this);
            bus.connect(EventBus.CHAPTER_COMPLETE, this);
            bus.connect(EventBus.OPTIONS_CHANGED, this);
        }
    }

    /* ------------------------------------------------------------------ */
    /* EventBus                                                            */
    /* ------------------------------------------------------------------ */

    @Override
    public void onEvent(String signal, Object[] args) {
        /* MENU_OPENED / MENU_CHANGED / MENU_CLOSED sont des ANNONCES : c'est
         * ce modele qui les emet pour prevenir l'audio et le HUD. Y reagir
         * ici bouclerait (open -> emit -> open -> ...). On ne consomme donc
         * que les signaux venus d'ailleurs. */
        if (EventBus.PAUSE_TOGGLED.equals(signal)) {
            togglePause();
        } else if (EventBus.MENU_OPENED.equals(signal)
                || EventBus.MENU_CLOSED.equals(signal)
                || EventBus.MENU_CHANGED.equals(signal)) {
            return;
        } else if (EventBus.CHAPTER_COMPLETE.equals(signal)) {
            chapterComplete = true;
            /* 14.05 : la scene change. Personne ne previent. */
            menuScene = SCENE_PHARE;
        }
    }

    /* ------------------------------------------------------------------ */
    /* 14.09 : la pause                                                    */
    /* ------------------------------------------------------------------ */

    /**
     * Pause instantanee (< 1 frame) : le modele ne fait que poser des
     * drapeaux, aucun travail lourd n'est declenche ici.
     */
    public boolean togglePause() {
        long t0 = System.nanoTime();
        paused = !paused;
        screen = paused ? SCREEN_PAUSE : SCREEN_NONE;
        row = 0;
        if (paused) {
            musicDb = PAUSE_MUSIC_DB;
            blur = PAUSE_BLUR;
            /* le jeu s'autosave a l'ouverture de la pause */
            if (PAUSE_AUTOSAVES && saves != null) {
                autosavesOnPause++;
            }
            if (bus != null) {
                bus.emit(EventBus.MENU_OPENED, "pause");
                bus.emit(EventBus.MUSIC_DUCK_FOR_PAUSE, PAUSE_MUSIC_DB);
                bus.emit(EventBus.SAVE_WRITTEN, "pause");
            }
        } else {
            musicDb = 0f;
            blur = 0f;
            if (bus != null) {
                bus.emit(EventBus.MENU_CLOSED, "pause");
            }
        }
        pauseFrameMs = (System.nanoTime() - t0) / 1e6f;
        return paused;
    }

    public boolean autosaveOnPause() {
        if (!PAUSE_AUTOSAVES || saves == null) {
            return false;
        }
        return true;
    }

    public boolean paused() {
        return paused;
    }

    public float pauseFrameMs() {
        return pauseFrameMs;
    }

    public boolean pauseIsInstant() {
        return pauseFrameMs < PAUSE_MAX_FRAME_SECONDS * 1000f;
    }

    /** La musique continue, attenuee de -12 dB (14.09). */
    public float musicDb() {
        return musicDb;
    }

    public float blur() {
        return blur;
    }

    /* ------------------------------------------------------------------ */
    /* Navigation                                                          */
    /* ------------------------------------------------------------------ */

    public void openMain() {
        screen = SCREEN_MAIN;
        paused = false;
        row = 0;
        if (bus != null) {
            bus.emit(EventBus.MENU_OPENED, "main");
        }
    }

    public void open(String what) {
        if ("main".equals(what)) {
            openMain();
        } else if ("pause".equals(what)) {
            if (!paused) {
                togglePause();
            }
        } else if ("options".equals(what)) {
            screen = SCREEN_OPTIONS;
            page = PAGE_IMAGE;
            row = 0;
        } else if ("journal".equals(what)) {
            screen = SCREEN_JOURNAL;
            if (journal != null) {
                journal.open(JournalModel.TAB_LETTERS);
            }
        } else if ("saves".equals(what)) {
            screen = SCREEN_SAVES;
            row = 0;
        }
        if (bus != null) {
            bus.emit(EventBus.MENU_CHANGED, what, screen);
        }
    }

    public void close() {
        screen = SCREEN_NONE;
        paused = false;
        musicDb = 0f;
        blur = 0f;
        brightnessPattern = false;
        editingLayout = false;
        if (journal != null && journal.open()) {
            journal.close();
        }
        if (bus != null) {
            bus.emit(EventBus.MENU_CLOSED, "all");
        }
    }

    public int screen() {
        return screen;
    }

    public boolean open() {
        return screen != SCREEN_NONE;
    }

    public void moveRow(int delta) {
        int max = rowsOnPage() - 1;
        row = Maths.clamp(row + delta, 0, Math.max(0, max));
        if (screen == SCREEN_JOURNAL && journal != null) {
            journal.moveEntry(delta);
        }
    }

    public void movePage(int delta) {
        if (screen == SCREEN_JOURNAL && journal != null) {
            journal.setTab(journal.tab() + delta);
            return;
        }
        page = (page + delta + PAGE_COUNT) % PAGE_COUNT;
        row = 0;
        if (bus != null) {
            bus.emit(EventBus.MENU_CHANGED, "page", PAGE_NAMES[page]);
        }
    }

    public int page() {
        return page;
    }

    public int row() {
        return row;
    }

    public int rowsOnPage() {
        List<Row> l = PAGES.get(Integer.valueOf(page));
        return l == null ? 0 : l.size();
    }

    public Row currentRow() {
        List<Row> l = PAGES.get(Integer.valueOf(page));
        if (l == null || row < 0 || row >= l.size()) {
            return null;
        }
        return l.get(row);
    }

    public String pageLabel() {
        return loc == null ? PAGE_NAMES[page] : loc.t(PAGE_KEYS[page]);
    }

    public String rowLabel(Row r) {
        return r == null ? "" : (loc == null ? r.key : loc.t(r.key));
    }

    /* ------------------------------------------------------------------ */
    /* Application d'une valeur                                            */
    /* ------------------------------------------------------------------ */

    /** Modifie la ligne courante (slider / choix / bascule) de `delta` pas. */
    public boolean adjust(float delta) {
        Row r = currentRow();
        if (r == null || options == null) {
            return false;
        }
        if (r.kind == Row.KIND_ACTION) {
            return runAction(r.field);
        }
        float current = getNumber(r.field);
        float next = r.kind == Row.KIND_TOGGLE
                ? (current > 0.5f ? 0f : 1f)
                : Maths.clamp(current + delta * r.step, r.min, r.max);
        setNumber(r.field, next);
        changesApplied++;
        options.clampToSpec();
        if (bus != null) {
            bus.emit(EventBus.OPTIONS_CHANGED, r.field, next);
        }
        return true;
    }

    public boolean set(String field, float value) {
        if (options == null) {
            return false;
        }
        setNumber(field, value);
        changesApplied++;
        options.clampToSpec();
        if (bus != null) {
            bus.emit(EventBus.OPTIONS_CHANGED, field, value);
        }
        return true;
    }

    private boolean runAction(String action) {
        if ("showBrightnessPattern".equals(action)) {
            brightnessPattern = !brightnessPattern;
        } else if ("editLayout".equals(action)) {
            editingLayout = !editingLayout;
        } else if ("selectSlot1".equals(action) || "selectSlot2".equals(action)
                || "selectSlot3".equals(action)) {
            slot = Integer.parseInt(action.substring(action.length() - 1));
        } else if ("eraseSave".equals(action)) {
            if (saves != null) {
                saves.deleteSlot(slot);
            }
        } else if ("exportSave".equals(action) || "importSave".equals(action)) {
            /* le transfert de fichier est realise par la couche Android */
            if (bus != null) {
                bus.emit(EventBus.SAVE_TRANSFER_REQUESTED, action, slot);
            }
        } else if ("remapAmber".equals(action) || "remapCyan".equals(action)) {
            if (bus != null) {
                bus.emit(EventBus.COLOR_REMAP_REQUESTED, action);
            }
        } else {
            return false;
        }
        if (bus != null) {
            bus.emit(EventBus.MENU_ACTION, action);
        }
        return true;
    }

    private float getNumber(String field) {
        return ReadWrite.number(options, field);
    }

    private void setNumber(String field, float value) {
        ReadWrite.apply(options, field, value);
    }

    /* ------------------------------------------------------------------ */
    /* 14.05 : la scene du menu principal                                  */
    /* ------------------------------------------------------------------ */

    public String menuScene() {
        return chapterComplete ? SCENE_PHARE : SCENE_PONTON;
    }

    public boolean chapterComplete() {
        return chapterComplete;
    }

    public void setChapterComplete(boolean v) {
        chapterComplete = v;
        menuScene = v ? SCENE_PHARE : SCENE_PONTON;
    }

    /** Le `Continuer` est ambre. Tout le reste est blanc casse (14.05). */
    public int colorFor(String item) {
        return "continuer".equals(item) ? COLOR_AMBER : COLOR_OFFWHITE;
    }

    public boolean hasFixedIllustration() {
        return MENU_HAS_FIXED_ILLUSTRATION;
    }

    /* ------------------------------------------------------------------ */
    /* Journal (14.04)                                                     */
    /* ------------------------------------------------------------------ */

    public JournalModel journal() {
        return journal;
    }

    public void openJournalTab(int tab) {
        screen = SCREEN_JOURNAL;
        if (journal != null) {
            journal.open(tab);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Boucle                                                              */
    /* ------------------------------------------------------------------ */

    public void update(float dt) {
        float target = open() ? 1f : 0f;
        openAnim = Maths.moveTowards(openAnim, target, dt / HudModel.UI_ANIM_SECONDS);
    }

    /** 14.07 : ease_out_quint, 180 ms. */
    public float openProgress() {
        return HudModel.ease(openAnim);
    }

    /* ------------------------------------------------------------------ */
    /* Acces                                                               */
    /* ------------------------------------------------------------------ */

    public int slot() {
        return slot;
    }

    public void setSlot(int s) {
        slot = Maths.clamp(s, 1, SaveSystem.SLOTS);
    }

    public boolean brightnessPattern() {
        return brightnessPattern;
    }

    public boolean editingLayout() {
        return editingLayout;
    }

    public int changesApplied() {
        return changesApplied;
    }

    public int autosavesOnPause() {
        return autosavesOnPause;
    }

    /** 14.12 : l'arborescence est complete — 6 familles, toutes les lignes. */
    public static boolean optionsTreeComplete() {
        int rows = 0;
        for (int p = 0; p < PAGE_COUNT; p++) {
            List<Row> l = PAGES.get(Integer.valueOf(p));
            if (l == null || l.isEmpty()) {
                return false;
            }
            rows += l.size();
        }
        return PAGES.size() == PAGE_COUNT && rows >= 50;
    }

    public static boolean specCompliant() {
        return PAUSE_MUSIC_DB == -12f
                && PAGE_COUNT == 6
                && SUBTITLE_MIN_SP == 17 && SUBTITLE_DEFAULT_SP == 21 && SUBTITLE_MAX_SP == 30
                && !MENU_HAS_FIXED_ILLUSTRATION
                && optionsTreeComplete();
    }

    /* ------------------------------------------------------------------ */
    /* Lecture / ecriture reflexive-free des champs de Options             */
    /* ------------------------------------------------------------------ */

    /**
     * Options est une structure de donnees plate ; on evite la reflexion en
     * boucle chaude (04.12). Cette table fait le lien entre le nom de champ
     * affiche dans le menu et le champ reel.
     */
    /**
     * Valeur numerique d'un champ d'Options, exposee au rendu pour tracer un
     * curseur (14.12). Le rendu n'a pas le droit de reflechir sur Options :
     * la table ReadWrite est la seule passerelle.
     */
    public static float valueOf(Options o, String field) {
        return ReadWrite.number(o, field);
    }

    /** Valeur minimale d'un champ, pour normaliser un curseur. */
    public static float minOf(String field) {
        List<Row> rows = allRows();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.field.equals(field)) {
                return r.min;
            }
        }
        return 0f;
    }

    /** Valeur maximale d'un champ, pour normaliser un curseur. */
    public static float maxOf(String field) {
        List<Row> rows = allRows();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.field.equals(field)) {
                return r.max;
            }
        }
        return 1f;
    }

    private static List<Row> allRows() {
        List<Row> all = new ArrayList<Row>(64);
        for (List<Row> page : PAGES.values()) {
            all.addAll(page);
        }
        return all;
    }

    static final class ReadWrite {

        static float number(Options o, String f) {
            if (o == null) {
                return 0f;
            }
            if ("quality".equals(f)) {
                return o.quality;
            }
            if ("renderScale".equals(f)) {
                return o.renderScale;
            }
            if ("targetFps".equals(f)) {
                return o.targetFps == 0 ? 3f : o.targetFps == 30 ? 0f
                        : o.targetFps == 45 ? 1f : o.targetFps == 60 ? 2f : 3f;
            }
            if ("shadows".equals(f)) {
                return o.shadows ? 1f : 0f;
            }
            if ("fog".equals(f)) {
                return o.fog ? 1f : 0f;
            }
            if ("effects".equals(f)) {
                return o.effects ? 1f : 0f;
            }
            if ("grain".equals(f)) {
                return o.grain ? 1f : 0f;
            }
            if ("chromatic".equals(f)) {
                return o.chromatic ? 1f : 0f;
            }
            if ("vignette".equals(f)) {
                return o.vignette ? 1f : 0f;
            }
            if ("bloom".equals(f)) {
                return o.bloom ? 1f : 0f;
            }
            if ("motionBlur".equals(f)) {
                return o.motionBlur ? 1f : 0f;
            }
            if ("brightness".equals(f)) {
                return o.brightness;
            }
            if ("hdr".equals(f)) {
                return o.hdr ? 1f : 0f;
            }
            if ("volMaster".equals(f)) {
                return o.volMaster;
            }
            if ("volMusic".equals(f)) {
                return o.volMusic;
            }
            if ("volSfx".equals(f)) {
                return o.volSfx;
            }
            if ("volVo".equals(f)) {
                return o.volVo;
            }
            if ("volAmb".equals(f)) {
                return o.volAmb;
            }
            if ("volDucking".equals(f)) {
                return o.volDucking;
            }
            if ("outputPreset".equals(f)) {
                return o.outputPreset;
            }
            if ("subtitles".equals(f)) {
                return o.subtitles ? 1f : 0f;
            }
            if ("combatDifficulty".equals(f)) {
                return o.combatDifficulty;
            }
            if ("traversalAssist".equals(f)) {
                return o.traversalAssist;
            }
            if ("prompts".equals(f)) {
                return o.prompts;
            }
            if ("hudMode".equals(f)) {
                return o.hudMode;
            }
            if ("autoRecenter".equals(f)) {
                return o.autoRecenter ? 1f : 0f;
            }
            if ("cameraShake".equals(f)) {
                return o.cameraShake;
            }
            if ("narrationOnly".equals(f)) {
                return o.narrationOnly ? 1f : 0f;
            }
            if ("buttonScale".equals(f)) {
                return o.buttonScale;
            }
            if ("tapInsteadOfHold".equals(f)) {
                return o.tapInsteadOfHold ? 1f : 0f;
            }
            if ("sensitivityX".equals(f)) {
                return o.sensitivityX;
            }
            if ("sensitivityY".equals(f)) {
                return o.sensitivityY;
            }
            if ("invertX".equals(f)) {
                return o.invertX ? 1f : 0f;
            }
            if ("invertY".equals(f)) {
                return o.invertY ? 1f : 0f;
            }
            if ("gamepadEnabled".equals(f)) {
                return o.gamepadEnabled ? 1f : 0f;
            }
            if ("vibration".equals(f)) {
                return o.vibration;
            }
            if ("subtitleSizeSp".equals(f)) {
                return o.subtitleSizeSp;
            }
            if ("subtitleOpaqueBg".equals(f)) {
                return o.subtitleOpaqueBg ? 1f : 0f;
            }
            if ("subtitleSpeaker".equals(f)) {
                return o.subtitleSpeaker ? 1f : 0f;
            }
            if ("subtitleExtended".equals(f)) {
                return o.subtitleExtended ? 1f : 0f;
            }
            if ("colorblindMode".equals(f)) {
                return o.colorblindMode;
            }
            if ("reducedFlashes".equals(f)) {
                return o.reducedFlashes ? 1f : 0f;
            }
            if ("wideParryWindow".equals(f)) {
                return o.wideParryWindow ? 1f : 0f;
            }
            return 0f;
        }

        static void apply(Options o, String f, float v) {
            if (o == null) {
                return;
            }
            boolean b = v > 0.5f;
            if ("quality".equals(f)) {
                o.quality = (int) v;
            } else if ("renderScale".equals(f)) {
                o.renderScale = v;
            } else if ("targetFps".equals(f)) {
                o.targetFps = (int) v == 0 ? 30 : (int) v == 1 ? 45
                        : (int) v == 2 ? 60 : 0;
            } else if ("shadows".equals(f)) {
                o.shadows = b;
            } else if ("fog".equals(f)) {
                o.fog = b;
            } else if ("effects".equals(f)) {
                o.effects = b;
            } else if ("grain".equals(f)) {
                o.grain = b;
            } else if ("chromatic".equals(f)) {
                o.chromatic = b;
            } else if ("vignette".equals(f)) {
                o.vignette = b;
            } else if ("bloom".equals(f)) {
                o.bloom = b;
            } else if ("motionBlur".equals(f)) {
                o.motionBlur = b;
            } else if ("brightness".equals(f)) {
                o.brightness = v;
            } else if ("hdr".equals(f)) {
                o.hdr = b;
            } else if ("volMaster".equals(f)) {
                o.volMaster = v;
            } else if ("volMusic".equals(f)) {
                o.volMusic = v;
            } else if ("volSfx".equals(f)) {
                o.volSfx = v;
            } else if ("volVo".equals(f)) {
                o.volVo = v;
            } else if ("volAmb".equals(f)) {
                o.volAmb = v;
            } else if ("volDucking".equals(f)) {
                o.volDucking = v;
            } else if ("outputPreset".equals(f)) {
                o.outputPreset = (int) v;
            } else if ("subtitles".equals(f)) {
                o.subtitles = b;
            } else if ("combatDifficulty".equals(f)) {
                o.combatDifficulty = (int) v;
            } else if ("traversalAssist".equals(f)) {
                o.traversalAssist = (int) v;
            } else if ("prompts".equals(f)) {
                o.prompts = (int) v;
            } else if ("hudMode".equals(f)) {
                o.hudMode = (int) v;
            } else if ("autoRecenter".equals(f)) {
                o.autoRecenter = b;
            } else if ("cameraShake".equals(f)) {
                o.cameraShake = v;
            } else if ("narrationOnly".equals(f)) {
                o.narrationOnly = b;
            } else if ("buttonScale".equals(f)) {
                o.buttonScale = v;
            } else if ("tapInsteadOfHold".equals(f)) {
                o.tapInsteadOfHold = b;
            } else if ("sensitivityX".equals(f)) {
                o.sensitivityX = v;
            } else if ("sensitivityY".equals(f)) {
                o.sensitivityY = v;
            } else if ("invertX".equals(f)) {
                o.invertX = b;
            } else if ("invertY".equals(f)) {
                o.invertY = b;
            } else if ("gamepadEnabled".equals(f)) {
                o.gamepadEnabled = b;
            } else if ("vibration".equals(f)) {
                o.vibration = v;
            } else if ("subtitleSizeSp".equals(f)) {
                o.subtitleSizeSp = (int) v;
            } else if ("subtitleOpaqueBg".equals(f)) {
                o.subtitleOpaqueBg = b;
            } else if ("subtitleSpeaker".equals(f)) {
                o.subtitleSpeaker = b;
            } else if ("subtitleExtended".equals(f)) {
                o.subtitleExtended = b;
            } else if ("colorblindMode".equals(f)) {
                o.colorblindMode = (int) v;
            } else if ("reducedFlashes".equals(f)) {
                o.reducedFlashes = b;
            } else if ("wideParryWindow".equals(f)) {
                o.wideParryWindow = b;
            }
        }

        private ReadWrite() {
        }
    }
}
