/*
 * LOHEN — sim/narrative/CinematicPlayer.java
 *
 * 07.21 : les 11 cinematiques (23 min 40 s cumulees) sont en TEMPS REEL dans
 * le moteur, jamais en video prerendue (sauf le logo). Le CinematicPlayer lit
 * une timeline JSON a pistes : camera (splines + FOV + DOF), acteur
 * (anim + cibles IK), audio, post-process, sous-titres.
 *
 * 15.01 : barres noires 2.39:1 qui arrivent en 0,5 s ; le personnage garde
 * son etat visuel (salissure, blessures, objets) ; aucune cinematique ne
 * depasse 3 min sauf C11.
 *
 * 07.22 [OBL] : TOUTE cinematique est skippable apres 1,5 s d'APPUI LONG
 * (jamais sur tap simple). Le skip fait un fondu de 0,4 s et applique l'ETAT
 * FINAL. Exception : la cinematique de la lettre (S8) n'est skippable
 * qu'apres 20 s. Assume.
 */
package com.velmora.lohen.sim.narrative;

import com.velmora.lohen.sim.core.ContentDb;
import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.core.MiniJson;
import com.velmora.lohen.sim.math.Spline;
import com.velmora.lohen.sim.math.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CinematicPlayer {

    public static final float LETTERBOX_IN = 0.5f;
    public static final float LETTERBOX_RATIO = 2.39f;
    public static final float SKIP_HOLD = 1.5f;
    public static final float SKIP_HOLD_LETTER = 20f;
    public static final float SKIP_FADE = 0.4f;

    /** Un plan : camera + acteur + audio + post + sous-titres. */
    public static final class Shot {
        public String id = "";
        public float t0, t1;
        /* camera */
        public final Spline path = new Spline();
        public final Spline look = new Spline();
        public float fovStart = 46f, fovEnd = 46f;
        public float dofStart = 45f, dofEnd = 45f;
        public float shake = 0f;
        /* acteur */
        public String actor = "";
        public String anim = "";
        /**
         * 07.23 REGLE ANTI-MOLLESSE : un geste court (2 a 6 s) ne doit jamais
         * laisser l'acteur fige dans un plan de 20 s. `animHold` est un clip
         * BOUCLE du manifeste des 744 (respiration, poids du corps) qui prend
         * le relais des que `anim` est epuise.
         */
        public String animHold = "";
        public float animSpeed = 1f;
        public String ikTarget = "";
        /* audio */
        public String music = "";
        public String ambience = "";
        public final List<String> sfx = new ArrayList<String>(4);
        public float musicFade = 1f;
        /* post */
        public String lut = "";
        public float saturation = 0.35f;
        public float exposure = 1f;
        public float vignette = 0.2f;
        /* sous-titres */
        public final List<String> subtitleLines = new ArrayList<String>(6);
        public final List<Float> subtitleAt = new ArrayList<Float>(6);
        public boolean handsControl;
    }

    /** Timeline complete d'une cinematique. */
    public static final class Timeline {
        public String id = "";
        public String title = "";
        public String seq = "";
        public float duration = 10f;
        public float skipHold = SKIP_HOLD;
        public float handsControlAt = -1f;
        public boolean letterbox = true;
        public boolean playerKeepsControl;
        public final List<Shot> shots = new ArrayList<Shot>(12);

        public Shot shotAt(float t) {
            for (int i = 0; i < shots.size(); i++) {
                Shot s = shots.get(i);
                if (t >= s.t0 && t < s.t1) {
                    return s;
                }
            }
            return shots.isEmpty() ? null : shots.get(shots.size() - 1);
        }
    }

    private final ContentDb db;
    private final EventBus bus;

    private Timeline timeline;
    private float time;
    private boolean active;
    private boolean skipping;
    private float skipFade;
    private float holdTime;
    private boolean controlGiven;
    private int played;
    private int skipped;
    private final Vec3 camPos = new Vec3();
    private final Vec3 camLook = new Vec3();
    private float camFov = 46f;
    private float camDof = 45f;
    private float letterbox = 0f;
    private String lastMusic = "";
    private String lastAmbience = "";
    private int subtitleCursor;

    public CinematicPlayer(ContentDb db, EventBus bus) {
        this.db = db;
        this.bus = bus;
    }

    /* ------------------------------------------------------------------ */
    /* Chargement                                                          */
    /* ------------------------------------------------------------------ */

    public Timeline load(String cineId) {
        if (db == null) {
            return null;
        }
        Map<String, Object> root = db.json("content/cinematics/" + cineId.toLowerCase() + ".json");
        if (root == null) {
            return null;
        }
        return parse(root);
    }

    /** Duree d'un clip d'apres le manifeste (07.20), corrigee de sa vitesse. */
    private float clipDuration(String clipId, float speed) {
        if (db == null || clipId == null || clipId.length() == 0) {
            return 1f;
        }
        ContentDb.AnimClip c = db.animById(clipId);
        float d = c == null || c.duration <= 0f ? 1f : c.duration;
        float sp = speed <= 0.01f ? 1f : speed;
        return d / sp;
    }

    @SuppressWarnings("unchecked")
    public static Timeline parse(Map<String, Object> root) {
        Timeline t = new Timeline();
        t.id = MiniJson.str(root, "id", "");
        t.title = MiniJson.str(root, "title", "");
        t.seq = MiniJson.str(root, "seq", "");
        t.duration = MiniJson.num(root, "duration", 10f);
        t.skipHold = MiniJson.num(root, "skippable_after", SKIP_HOLD);
        t.handsControlAt = MiniJson.num(root, "hands_control_at", -1f);
        t.letterbox = MiniJson.bool(root, "letterbox", true);
        t.playerKeepsControl = MiniJson.bool(root, "player_keeps_control", false);
        List<Object> shots = MiniJson.childList(root, "shots");
        if (shots != null) {
            for (Object o : shots) {
                Map<String, Object> m = (Map<String, Object>) o;
                Shot s = new Shot();
                s.id = MiniJson.str(m, "id", "");
                s.t0 = MiniJson.num(m, "t0", 0f);
                s.t1 = MiniJson.num(m, "t1", s.t0 + 5f);
                Map<String, Object> cam = MiniJson.child(m, "camera");
                if (cam != null) {
                    fillSpline(s.path, MiniJson.childList(cam, "path"));
                    fillSpline(s.look, MiniJson.childList(cam, "look"));
                    s.fovStart = MiniJson.num(cam, "fov", 46f);
                    s.fovEnd = MiniJson.num(cam, "fov_end", s.fovStart);
                    s.dofStart = MiniJson.num(cam, "dof", 45f);
                    s.dofEnd = MiniJson.num(cam, "dof_end", s.dofStart);
                    s.shake = MiniJson.num(cam, "shake", 0f);
                }
                Map<String, Object> act = MiniJson.child(m, "actor");
                if (act != null) {
                    s.actor = MiniJson.str(act, "who", "");
                    s.anim = MiniJson.str(act, "anim", "");
                    s.animSpeed = MiniJson.num(act, "speed", 1f);
                    s.animHold = MiniJson.str(act, "hold", "");
                    s.ikTarget = MiniJson.str(act, "ik", "");
                }
                Map<String, Object> aud = MiniJson.child(m, "audio");
                if (aud != null) {
                    s.music = MiniJson.str(aud, "music", "");
                    s.ambience = MiniJson.str(aud, "ambience", "");
                    s.musicFade = MiniJson.num(aud, "fade", 1f);
                    List<Object> sfx = MiniJson.childList(aud, "sfx");
                    if (sfx != null) {
                        for (Object x : sfx) {
                            s.sfx.add(String.valueOf(x));
                        }
                    }
                }
                Map<String, Object> post = MiniJson.child(m, "post");
                if (post != null) {
                    s.lut = MiniJson.str(post, "lut", "");
                    s.saturation = MiniJson.num(post, "saturation", 0.35f);
                    s.exposure = MiniJson.num(post, "exposure", 1f);
                    s.vignette = MiniJson.num(post, "vignette", 0.2f);
                }
                List<Object> subs = MiniJson.childList(m, "subtitles");
                if (subs != null) {
                    for (Object x : subs) {
                        if (x instanceof Map) {
                            Map<String, Object> sm = (Map<String, Object>) x;
                            s.subtitleLines.add(MiniJson.str(sm, "line", ""));
                            s.subtitleAt.add(MiniJson.num(sm, "t", s.t0));
                        } else {
                            s.subtitleLines.add(String.valueOf(x));
                            s.subtitleAt.add(s.t0);
                        }
                    }
                }
                s.handsControl = MiniJson.bool(m, "hands_control", false);
                t.shots.add(s);
            }
        }
        return t;
    }

    private static void fillSpline(Spline sp, List<Object> pts) {
        sp.clear();
        if (pts == null) {
            return;
        }
        for (Object o : pts) {
            if (o instanceof List) {
                List<?> l = (List<?>) o;
                if (l.size() >= 3) {
                    sp.add(((Number) l.get(0)).floatValue(),
                            ((Number) l.get(1)).floatValue(),
                            ((Number) l.get(2)).floatValue());
                }
            } else if (o instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                sp.add(MiniJson.num(m, "x", 0f), MiniJson.num(m, "y", 0f), MiniJson.num(m, "z", 0f));
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Lecture                                                             */
    /* ------------------------------------------------------------------ */

    public boolean play(String cineId) {
        Timeline t = load(cineId);
        if (t == null || t.shots.isEmpty()) {
            return false;
        }
        timeline = t;
        time = 0f;
        active = true;
        skipping = false;
        skipFade = 0f;
        holdTime = 0f;
        controlGiven = t.playerKeepsControl;
        subtitleCursor = 0;
        letterbox = 0f;
        played++;
        bus.emit(EventBus.CINEMATIC_STARTED, t.id, t.duration);
        return true;
    }

    public boolean playTimeline(Timeline t) {
        if (t == null || t.shots.isEmpty()) {
            return false;
        }
        timeline = t;
        time = 0f;
        active = true;
        skipping = false;
        skipFade = 0f;
        holdTime = 0f;
        controlGiven = t.playerKeepsControl;
        subtitleCursor = 0;
        letterbox = 0f;
        played++;
        bus.emit(EventBus.CINEMATIC_STARTED, t.id, t.duration);
        return true;
    }

    /** 07.22 : appui long. Jamais un tap simple. */
    public void setSkipHeld(boolean held, float dt) {
        if (!active || timeline == null) {
            holdTime = 0f;
            return;
        }
        if (held) {
            holdTime += dt;
            if (holdTime >= timeline.skipHold && !skipping) {
                skipping = true;
                skipFade = 0f;
                bus.emit(EventBus.CINEMATIC_SKIPPED, timeline.id);
            }
        } else {
            holdTime = 0f;
        }
    }

    public void update(float dt) {
        if (!active || timeline == null) {
            return;
        }
        /* barres noires : 0,5 s (15.01) */
        float targetBars = timeline.letterbox ? 1f : 0f;
        if (skipping) {
            skipFade += dt / SKIP_FADE;
            targetBars = 0f;
            if (skipFade >= 1f) {
                applyFinalState();
                stop();
                return;
            }
        } else {
            time += dt;
        }
        letterbox = Maths.damp(letterbox, targetBars, 1f / LETTERBOX_IN, dt);

        Shot s = timeline.shotAt(time);
        if (s == null) {
            applyFinalState();
            stop();
            return;
        }
        float local = s.t1 > s.t0 ? Maths.clamp01((time - s.t0) / (s.t1 - s.t0)) : 1f;
        /* camera : spline + FOV + DOF interpole */
        if (s.path.count() > 0) {
            s.path.evaluate(local, camPos);
        }
        if (s.look.count() > 0) {
            s.look.evaluate(local, camLook);
        }
        camFov = Maths.lerp(s.fovStart, s.fovEnd, local);
        camDof = Maths.lerp(s.dofStart, s.dofEnd, local);
        bus.emit(EventBus.CINEMATIC_CAMERA, camPos.x, camPos.y, camPos.z,
                camLook.x, camLook.y, camLook.z, camFov, camDof, s.shake, letterbox);
        /* acteur : geste, puis respiration bouclee si le plan est plus long */
        if (s.anim.length() > 0) {
            String clip = s.anim;
            float clipLocal = local;
            if (s.animHold.length() > 0) {
                float dur = clipDuration(s.anim, s.animSpeed);
                float elapsed = time - s.t0;
                if (elapsed > dur) {
                    clip = s.animHold;
                    float holdDur = clipDuration(s.animHold, s.animSpeed);
                    float over = elapsed - dur;
                    clipLocal = holdDur > 0.01f ? (over / holdDur) % 1f : 0f;
                }
            }
            bus.emit(EventBus.CINEMATIC_ACTOR, s.actor, clip, s.animSpeed,
                    s.ikTarget, clipLocal);
        }
        /* audio */
        if (!s.music.equals(lastMusic)) {
            lastMusic = s.music;
            if (s.music.length() > 0) {
                bus.emit(EventBus.MUSIC_CUE, s.music, s.musicFade);
            }
        }
        if (!s.ambience.equals(lastAmbience)) {
            lastAmbience = s.ambience;
            if (s.ambience.length() > 0) {
                bus.emit(EventBus.AMBIENCE_CUE, s.ambience);
            }
        }
        for (int i = 0; i < s.sfx.size(); i++) {
            /* les sfx sont tires au debut du plan */
            if (local < 0.02f || (time - s.t0) < dt * 1.5f) {
                bus.emit(EventBus.SFX, s.sfx.get(i), 1f);
            }
        }
        /* post-process */
        bus.emit(EventBus.CINEMATIC_POST, s.lut, s.saturation, s.exposure, s.vignette);
        /* sous-titres */
        for (int i = 0; i < s.subtitleLines.size(); i++) {
            float at = s.subtitleAt.get(i);
            if (time >= at && time - at < dt * 1.5f) {
                bus.emit(EventBus.CINEMATIC_SUBTITLE, s.subtitleLines.get(i));
            }
        }
        /* rendu de controle : C01 plan 7, C10 (15.02, 15.11) */
        if (!controlGiven) {
            boolean byTime = timeline.handsControlAt >= 0f && time >= timeline.handsControlAt;
            if (byTime || s.handsControl) {
                controlGiven = true;
                bus.emit(EventBus.CONTROL_RESTORED, timeline.id);
            }
        }
        if (time >= timeline.duration) {
            applyFinalState();
            stop();
        }
    }

    /** Le skip applique l'ETAT FINAL (07.22). */
    private void applyFinalState() {
        if (timeline == null || timeline.shots.isEmpty()) {
            return;
        }
        Shot last = timeline.shots.get(timeline.shots.size() - 1);
        if (last.path.count() > 0) {
            last.path.evaluate(1f, camPos);
        }
        if (last.look.count() > 0) {
            last.look.evaluate(1f, camLook);
        }
        camFov = last.fovEnd;
        camDof = last.dofEnd;
        letterbox = 0f;
        bus.emit(EventBus.CINEMATIC_FINAL_STATE, camPos.x, camPos.y, camPos.z,
                camLook.x, camLook.y, camLook.z, camFov, last.lut, last.saturation,
                last.exposure, last.vignette);
        if (last.music.length() > 0) {
            bus.emit(EventBus.MUSIC_CUE, last.music, 1f);
        }
    }

    public void stop() {
        if (!active) {
            return;
        }
        String id = timeline == null ? "" : timeline.id;
        active = false;
        if (skipping) {
            skipped++;
        }
        controlGiven = true;
        bus.emit(EventBus.CINEMATIC_ENDED, id, skipped > 0);
        bus.emit(EventBus.CONTROL_RESTORED, id);
    }

    public boolean active() {
        return active;
    }

    public boolean playerHasControl() {
        return controlGiven || !active;
    }

    public float time() {
        return time;
    }

    public float progress() {
        return timeline == null || timeline.duration <= 0f ? 0f
                : Maths.clamp01(time / timeline.duration);
    }

    public float letterbox() {
        return letterbox;
    }

    public float skipHoldProgress() {
        return timeline == null ? 0f : Maths.clamp01(holdTime / timeline.skipHold);
    }

    public Vec3 cameraPosition() {
        return camPos;
    }

    public Vec3 cameraLookAt() {
        return camLook;
    }

    public float cameraFov() {
        return camFov;
    }

    public float cameraDof() {
        return camDof;
    }

    public Timeline timeline() {
        return timeline;
    }

    public String id() {
        return timeline == null ? "" : timeline.id;
    }

    public int played() {
        return played;
    }

    public int skipped() {
        return skipped;
    }

    public void reset() {
        active = false;
        timeline = null;
        time = 0f;
        letterbox = 0f;
        holdTime = 0f;
        skipping = false;
    }
}
