/*
 * LOHEN — sim/core/EventBus.java
 *
 * Autoload n°1 (04.03) : signaux globaux, zero etat.
 * Liste exhaustive des signaux du Chapitre 1, recopiee de 04.04.
 * Aucun "get_node('../../../')" : la communication inter-systemes passe ici.
 */
package com.velmora.lohen.sim.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EventBus {

    /** Identifiants des signaux du Chapitre 1 (04.04). */
    public static final String ECHO_STARTED = "echo_started";
    public static final String ECHO_FINISHED = "echo_finished";
    public static final String DIALOGUE_STARTED = "dialogue_started";
    public static final String DIALOGUE_LINE = "dialogue_line";
    public static final String DIALOGUE_CHOICE_MADE = "dialogue_choice_made";
    public static final String DIALOGUE_FINISHED = "dialogue_finished";
    public static final String ZONE_ENTERED = "zone_entered";
    public static final String ZONE_EXITED = "zone_exited";
    public static final String CHECKPOINT_REACHED = "checkpoint_reached";
    public static final String BREATH_CHANGED = "breath_changed";
    public static final String BREATH_BROKEN = "breath_broken";
    public static final String BREATH_RESTORED = "breath_restored";
    public static final String GRAPPLE_FIRED = "grapple_fired";
    public static final String GRAPPLE_ATTACHED = "grapple_attached";
    public static final String GRAPPLE_RELEASED = "grapple_released";
    public static final String COMBAT_ENTERED = "combat_entered";
    public static final String COMBAT_CLEARED = "combat_cleared";
    public static final String FIGURE_STAGGERED = "figure_staggered";
    public static final String FIGURE_SHATTERED = "figure_shattered";
    public static final String LETTER_FOUND = "letter_found";
    public static final String JOURNAL_UPDATED = "journal_updated";
    public static final String CINEMATIC_STARTED = "cinematic_started";
    public static final String CINEMATIC_SKIPPED = "cinematic_skipped";
    public static final String CINEMATIC_ENDED = "cinematic_ended";
    public static final String CINEMATIC_CAMERA = "cinematic_camera";
    public static final String CINEMATIC_ACTOR = "cinematic_actor";
    public static final String CINEMATIC_POST = "cinematic_post";
    public static final String CINEMATIC_SUBTITLE = "cinematic_subtitle";
    public static final String CINEMATIC_FINAL_STATE = "cinematic_final_state";
    public static final String AMBIENCE_CUE = "ambience_cue";
    public static final String CONTROL_RESTORED = "control_restored";
    public static final String LETTER_UNFOLD = "letter_unfold";
    public static final String LETTER_SCROLL = "letter_scroll";
    public static final String LETTER_VOICE = "letter_voice";
    public static final String LETTER_BELL = "letter_bell";
    public static final String LETTER_CAMERA_CUT = "letter_camera_cut";
    public static final String LETTER_FOLD_STORE = "letter_fold_store";
    public static final String DOOR_PUSH = "door_push";
    public static final String DESCENT_BEGIN = "descent_begin";
    public static final String FINAL_SHOT = "final_shot";
    public static final String CREDITS_BEGIN = "credits_begin";
    public static final String CREDITS_END = "credits_end";
    public static final String CHAPTER_CARD = "chapter_card";
    public static final String LETTER_STEP = "letter_step";
    public static final String LETTER_DAWN = "letter_dawn";
    public static final String LETTER_DISCOVERY = "letter_discovery";
    public static final String LETTER_PROMPT = "letter_prompt";
    public static final String LETTER_NOTEBOOK_PAGE = "letter_notebook_page";
    public static final String LETTER_RITUAL = "letter_ritual";
    public static final String LETTER_NO_ECHO = "letter_no_echo";
    public static final String LETTER_UNFOLD_PROGRESS = "letter_unfold_progress";
    public static final String LETTER_VOICE_HOLD = "letter_voice_hold";
    public static final String LETTER_CAMERA_LEAVING = "letter_camera_leaving";
    public static final String LETTER_DESCENT_PROGRESS = "letter_descent_progress";
    public static final String LETTER_FOLD_PROGRESS = "letter_fold_progress";
    public static final String LETTER_REREAD = "letter_reread";
    public static final String FINAL_SHOT_PROGRESS = "final_shot_progress";
    public static final String FINAL_BEAT = "final_beat";
    public static final String CREDITS_SKIPPABLE = "credits_skippable";
    public static final String CREDITS_PROGRESS = "credits_progress";
    public static final String MENU_CHANGED = "menu_changed";
    public static final String FOG_CLEARED = "fog_cleared";
    public static final String CAMERA_MODE = "camera_mode";
    public static final String CAMERA_UPDATED = "camera_updated";
    public static final String HORIZON_RULE = "horizon_rule";
    public static final String IK_UPDATED = "ik_updated";
    public static final String ANIM_STATE = "anim_state";
    public static final String FOOTSTEP_SOUND = "footstep_sound";
    public static final String CLOTH_SOUND = "cloth_sound";
    public static final String HARNESS_JINGLE = "harness_jingle";
    public static final String BREATH_SOUND = "breath_sound";
    public static final String WATER_SPLASH = "water_splash";
    public static final String BONE_POSE = "bone_pose";
    public static final String QUALITY_CHANGED = "quality_changed";
    public static final String THERMAL_WARNING = "device_thermal_warning";
    /* signaux ajoutés par l'implementation native (documentes dans l'ADR-002) */
    public static final String VERB_USED = "verb_used";
    public static final String PLAYER_DIED = "player_died";
    public static final String HAPTIC = "haptic";
    public static final String SFX = "sfx";
    public static final String MUSIC_CUE = "music_cue";
    public static final String SEQUENCE_CHANGED = "sequence_changed";
    public static final String NAVIGATION_STALL = "navigation_stall";
    public static final String SAVE_WRITTEN = "save_written";
    public static final String OBJECTIVE_CHANGED = "objective_changed";
    /* --- locomotion / traversee (BLOC 07, 08) --- */
    public static final String PLAYER_STATE = "player_state";
    public static final String PLAYER_JUMPED = "player_jumped";
    public static final String PLAYER_LANDED = "player_landed";
    public static final String LANDING = "landing";
    public static final String FOOTSTEP = "footstep";
    public static final String FALL_FLAIL = "fall_flail";
    public static final String JUMP_REFUSED_EDGE_STOP = "jump_refused_edge_stop";
    public static final String SLIDE_STARTED = "slide_started";
    public static final String SLIDE_ENDED = "slide_ended";
    public static final String WALLRUN_START = "wallrun_start";
    public static final String WALLRUN_END = "wallrun_end";
    public static final String LEDGE_FOUND = "ledge_found";
    public static final String LEDGE_GRABBED = "ledge_grabbed";
    public static final String LEDGE_SHIMMY = "ledge_shimmy";
    public static final String LEDGE_RELEASED = "ledge_released";
    public static final String LEDGE_CLIMB_BEGIN = "ledge_climb_begin";
    public static final String LEDGE_MOUNTED = "ledge_mounted";
    /* --- grappin (08.08, 08.09) --- */
    public static final String GRAPPLE_REFUSED = "grapple_refused";
    public static final String GRAPPLE_MISSED = "grapple_missed";
    public static final String GRAPPLE_SWING_START = "grapple_swing_start";
    public static final String GRAPPLE_TIMEOUT = "grapple_timeout";
    public static final String CABLE_STRAIN = "cable_strain";
    public static final String ZIP_STARTED = "zip_started";
    public static final String ZIP_ENDED = "zip_ended";
    /* --- combat (07.12, 07.13, 08.14) --- */
    public static final String GUARD_BROKEN = "guard_broken";
    public static final String PARRY_WINDOW_OPEN = "parry_window_open";
    public static final String PARRY_SUCCESS = "parry_success";
    public static final String BLOCKED = "blocked";
    public static final String STRIKE_STARTED = "strike_started";
    public static final String STRIKE_WHIFFED = "strike_whiffed";
    public static final String DODGE = "dodge";
    public static final String IMPACT_SOUND = "impact_sound";
    public static final String PLAYER_HIT_TAKEN = "player_hit_taken";
    public static final String ATTACK_TELL = "attack_tell";
    public static final String ATTACK_LANDED = "attack_landed";
    public static final String ATTACK_MISSED = "attack_missed";
    public static final String ATTACK_DODGED = "attack_dodged";
    public static final String FIGURE_HIT = "figure_hit";
    public static final String FIGURE_STATE = "figure_state";
    public static final String FIGURE_MOVED = "figure_moved";
    public static final String FIGURE_PEACEFUL = "figure_peaceful";
    public static final String GLASS_SHATTER_BURST = "glass_shatter_burst";
    public static final String GLASS_VIBRATION = "glass_vibration";
    public static final String ECHASSIER_TELL = "echassier_tell";
    public static final String ECHASSIER_SWEEP = "echassier_sweep";
    public static final String ECHASSIER_STILT_BROKEN = "echassier_stilt_broken";
    public static final String ECHASSIER_STOMP = "echassier_stomp";
    public static final String MUEUR_TELL = "mueur_tell";
    public static final String MUEUR_HOP = "mueur_hop";
    public static final String MUEUR_LANDED = "mueur_landed";
    public static final String MUEUR_CHIRP = "mueur_chirp";
    public static final String BOSS_PHASE = "boss_phase";
    public static final String BOSS_BEAM = "boss_beam";
    public static final String BOSS_SUMMON = "boss_summon";
    public static final String BOSS_PEACE = "boss_peace";
    public static final String BOSS_DEFEATED = "boss_defeated";
    public static final String BOSS_DOSSIER_OFFERED = "boss_dossier_offered";
    public static final String BOSS_SWEEP = "boss_sweep";
    public static final String BOSS_SHARDS = "boss_shards";
    public static final String BOSS_SHARD_HIT = "boss_shard_hit";
    public static final String BOSS_VERTICAL = "boss_vertical";
    public static final String BOSS_SECOND_HAND = "boss_second_hand";
    /* --- narratif (BLOC 11, 12, 19, 20) --- */
    public static final String ECHO_REQUESTED = "echo_requested";
    public static final String ECHO_TRIGGERED = "echo_triggered";
    public static final String ECHO_REFUSED = "echo_refused";
    public static final String ECHO_RITUAL_BEGIN = "echo_ritual_begin";
    public static final String ECHO_PHASE = "echo_phase";
    public static final String ECHO_VISUAL = "echo_visual";
    public static final String ECHO_BREATH_DRAIN = "echo_breath_drain";
    public static final String ECHO_PLAYABLE = "echo_playable";
    public static final String ECHO_CONTACT_LOST = "echo_contact_lost";
    public static final String ECHO_FRAGMENT_LEAK = "echo_fragment_leak";
    public static final String ECHO_MODE_C_HELD = "echo_mode_c_held";
    public static final String ECHO_PORTRAIT_REQUEST = "echo_portrait_request";
    public static final String ECHO_ENDED_NO_REFUND = "echo_ended_no_refund";
    public static final String ECHO_ALREADY_READ = "echo_already_read";
    public static final String GESTURE = "gesture";
    public static final String GLOVE_RITUAL = "glove_ritual";
    public static final String PROP_EXAMINED = "prop_examined";
    public static final String PROP_PICKED = "prop_picked";
    public static final String SCENE_STARTED = "scene_started";
    public static final String SCENE_ENDED = "scene_ended";
    public static final String SUBTITLE = "subtitle";
    public static final String CHOICE_OFFERED = "choice_offered";
    public static final String CHOICE_TIMER = "choice_timer";
    public static final String CHOICE_TIMEOUT = "choice_timeout";
    public static final String LETTER_STARTED = "letter_started";
    public static final String LETTER_LINE = "letter_line";
    public static final String LETTER_ENDED = "letter_ended";
    public static final String ENDING_REACHED = "ending_reached";
    public static final String SECRET_FOUND = "secret_found";
    public static final String SHORTCUT_OPENED = "shortcut_opened";
    public static final String TRIGGER_ENTERED = "trigger_entered";
    public static final String TRIGGER_EXITED = "trigger_exited";
    public static final String NPC_BARK = "npc_bark";
    public static final String NPC_GESTURE = "npc_gesture";
    public static final String SOL_STEAL = "sol_steal";
    public static final String SOL_RETURNS_SATCHEL = "sol_returns_satchel";
    public static final String DIALOGUE_INTERRUPTED = "dialogue_interrupted";
    public static final String BARK_PLAYED = "bark_played";
    public static final String QUEST_NODE = "quest_node";
    public static final String JOURNAL_ENTRY = "journal_entry";
    /* --- monde / systeme --- */
    public static final String LEVEL_LOADED = "level_loaded";
    public static final String ZONE_STREAMED = "zone_streamed";
    public static final String ZONE_UNLOADED = "zone_unloaded";
    public static final String RESPAWN = "respawn";
    public static final String CHECKPOINT_SET = "checkpoint_set";
    public static final String PERF_REPORT = "perf_report";
    public static final String THERMAL_STATE = "thermal_state";
    public static final String MENU_OPENED = "menu_opened";
    public static final String MENU_CLOSED = "menu_closed";
    public static final String PAUSE_TOGGLED = "pause_toggled";
    public static final String HUD_TOGGLE = "hud_toggle";
    public static final String LANGUAGE_CHANGED = "language_changed";
    public static final String OPTIONS_CHANGED = "options_changed";
    public static final String SAVE_LOADED = "save_loaded";
    public static final String CHAPTER_COMPLETE = "chapter_complete";
    public static final String FRAGILE_BROKE = "fragile_broke";
    public static final String LIGHTHOUSE_BEAM = "lighthouse_beam";
    public static final String AMBIENCE_CHANGED = "ambience_changed";
    public static final String MUSIC_STATE = "music_state";
    public static final String REVERB_CHANGED = "reverb_changed";
    public static final String WIND_LEVEL = "wind_level";
    public static final String SILENCE_ACTIVE = "silence_active";
    public static final String SILENCE_ENDED = "silence_ended";
    public static final String MOTIF_PLAYED = "motif_played";
    public static final String STEM_CHANGED = "stem_changed";
    public static final String RIG_UPDATED = "rig_updated";
    public static final String IK_SOLVED = "ik_solved";
    public static final String UI_SELECT = "ui_select";
    public static final String BLINK_DOUBLE = "blink_double";
    public static final String CARRY_CHANGED = "carry_changed";
    public static final String CARRY_PICKUP = "carry_pickup";
    public static final String CARRY_SETDOWN = "carry_setdown";
    public static final String CARRY_SWAP_HAND = "carry_swap_hand";
    public static final String COLOR_REMAP_REQUESTED = "color_remap_requested";
    public static final String DARK_CROSSING_BEGIN = "dark_crossing_begin";
    public static final String DARK_CROSSING_DONE = "dark_crossing_done";
    public static final String DEATH_FADE_BEGIN = "death_fade_begin";
    public static final String DEATH_FADE_ENDED = "death_fade_ended";
    public static final String HUD_MODE_CHANGED = "hud_mode_changed";
    public static final String JOURNAL_CLOSED = "journal_closed";
    public static final String JOURNAL_OPENED = "journal_opened";
    public static final String LANTERN_PLACED = "lantern_placed";
    public static final String LETTER_CLOSED = "letter_closed";
    public static final String LETTER_DELIVERED = "letter_delivered";
    public static final String LETTER_DELIVERY_SCENE = "letter_delivery_scene";
    public static final String LOADING_ENDED = "loading_ended";
    public static final String LOADING_PROGRESS = "loading_progress";
    public static final String MENU_ACTION = "menu_action";
    public static final String MUSIC_DUCK_FOR_PAUSE = "music_duck_for_pause";
    public static final String NPC_NOTICED = "npc_noticed";
    public static final String PACING_EVENT = "pacing_event";
    public static final String PACING_HOLE = "pacing_hole";
    public static final String PROMPT_REQUESTED = "prompt_requested";
    public static final String PROMPT_SHOWN = "prompt_shown";
    public static final String SAVE_TRANSFER_REQUESTED = "save_transfer_requested";
    public static final String CELL_LOADED = "cell_loaded";
    public static final String CHAMBER_ENTERED = "chamber_entered";
    public static final String FIGURE_DROPPED_OBJECT = "figure_dropped_object";
    public static final String FIGURE_SEEN_FAR = "figure_seen_far";
    public static final String GLASS_OPENED = "glass_opened";
    public static final String GLASS_SEA_ENTERED = "glass_sea_entered";
    public static final String GLASS_SEA_LEFT = "glass_sea_left";
    public static final String GLASS_SPECTACLE = "glass_spectacle";
    public static final String HEAT_WELL_ENTERED = "heat_well_entered";
    public static final String HEAT_WELL_ESCAPED = "heat_well_escaped";
    public static final String HEAT_WELL_WARNING = "heat_well_warning";
    public static final String LIGHTHOUSE_EXPOSURE = "lighthouse_exposure";
    public static final String LIGHTHOUSE_LINGER = "lighthouse_linger";
    public static final String LIGHTHOUSE_LINGER_ENDED = "lighthouse_linger_ended";
    public static final String LIGHTHOUSE_SWEEP_IN_CHAMBER = "lighthouse_sweep_in_chamber";
    public static final String PLAYER_SANK_IN_GLASS = "player_sank_in_glass";
    public static final String RESUME_COMPLETED = "resume_completed";
    public static final String SHEETS_REVEALED = "sheets_revealed";
    public static final String SHEETS_REVEAL_REQUESTED = "sheets_reveal_requested";
    public static final String STREAM_LOD_CUT = "stream_lod_cut";
    public static final String TRAWLER_CRACKED = "trawler_cracked";
    public static final String TRAWLER_REACHED = "trawler_reached";
    public static final String ZONE_STREAM_STARTED = "zone_stream_started";

    /** Interface d'ecoute : un tableau d'arguments heterogenes. */
    public interface Listener {
        void onEvent(String signal, Object[] args);
    }

    private final Map<String, List<Listener>> listeners = new HashMap<String, List<Listener>>();
    private final List<LogEntry> log = new ArrayList<LogEntry>(512);
    private final Object[] scratch = new Object[8];
    private boolean logging = true;

    public static final class LogEntry {
        public final String signal;
        public final String arg0;
        public final float time;

        LogEntry(String signal, String arg0, float time) {
            this.signal = signal;
            this.arg0 = arg0;
            this.time = time;
        }
    }

    public void connect(String signal, Listener l) {
        List<Listener> list = listeners.get(signal);
        if (list == null) {
            list = new ArrayList<Listener>(4);
            listeners.put(signal, list);
        }
        if (!list.contains(l)) {
            list.add(l);
        }
    }

    public void disconnect(String signal, Listener l) {
        List<Listener> list = listeners.get(signal);
        if (list != null) {
            list.remove(l);
        }
    }

    public void disconnectAll(Listener l) {
        for (List<Listener> list : listeners.values()) {
            list.remove(l);
        }
    }

    public void emit(String signal) {
        emit(signal, null, 0f);
    }

    public void emit(String signal, Object a0) {
        scratch[0] = a0;
        dispatch(signal, scratch, 1, 0f);
    }

    public void emit(String signal, Object a0, Object a1) {
        scratch[0] = a0;
        scratch[1] = a1;
        dispatch(signal, scratch, 2, 0f);
    }

    public void emit(String signal, Object a0, Object a1, Object a2) {
        scratch[0] = a0;
        scratch[1] = a1;
        scratch[2] = a2;
        dispatch(signal, scratch, 3, 0f);
    }

    public void emit(String signal, Object a0, Object a1, Object a2, Object a3) {
        scratch[0] = a0;
        scratch[1] = a1;
        scratch[2] = a2;
        scratch[3] = a3;
        dispatch(signal, scratch, 4, 0f);
    }

    public void emit(String signal, Object a0, Object a1, Object a2, Object a3, Object a4) {
        scratch[0] = a0;
        scratch[1] = a1;
        scratch[2] = a2;
        scratch[3] = a3;
        scratch[4] = a4;
        dispatch(signal, scratch, 5, 0f);
    }

    public void emit(String signal, Object a0, Object a1, Object a2, Object a3, Object a4,
                     Object a5) {
        scratch[0] = a0;
        scratch[1] = a1;
        scratch[2] = a2;
        scratch[3] = a3;
        scratch[4] = a4;
        scratch[5] = a5;
        dispatch(signal, scratch, 6, 0f);
    }

    /** Emission a plus de 6 arguments (cinematiques, Echo, lettre). */
    public void emit(String signal, Object... args) {
        dispatch(signal, args, args == null ? 0 : args.length, 0f);
    }

    public void emitAt(String signal, float time, Object... args) {
        dispatch(signal, args, args == null ? 0 : args.length, time);
    }

    private void dispatch(String signal, Object[] args, int count, float time) {
        if (logging) {
            String first = count > 0 && args != null && args[0] != null ? String.valueOf(args[0]) : "";
            if (log.size() < 8192) {
                log.add(new LogEntry(signal, first, time));
            }
        }
        List<Listener> list = listeners.get(signal);
        if (list == null) {
            return;
        }
        /* copie defensive : un listener peut se (de)connecter pendant l'emission */
        Object[] payload = new Object[count];
        if (args != null) {
            System.arraycopy(args, 0, payload, 0, count);
        }
        for (int i = 0, n = list.size(); i < n; i++) {
            Listener l = list.get(i);
            if (l != null) {
                l.onEvent(signal, payload);
            }
        }
    }

    public List<LogEntry> log() {
        return log;
    }

    public void clearLog() {
        log.clear();
    }

    public void setLogging(boolean enabled) {
        this.logging = enabled;
    }

    public int listenerCount(String signal) {
        List<Listener> list = listeners.get(signal);
        return list == null ? 0 : list.size();
    }
}
