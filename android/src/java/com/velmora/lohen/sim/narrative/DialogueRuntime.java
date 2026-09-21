/*
 * LOHEN — sim/narrative/DialogueRuntime.java
 *
 * 12.02 : chaque ligne possede un id stable, le texte, la duree, l'emotion,
 * la priorite et `pause_after`. 12.04 : jamais plus de 4,5 s par ligne,
 * silences ECRITS. 12.05 : 38 choix, jamais plus de 3 options, le texte de
 * l'option est EXACTEMENT ce que Lohen va dire, pas de timer sauf 2 fois
 * (signale visuellement), aucun choix ne change la fin, et le jeu n'affiche
 * JAMAIS « X se souviendra de cela ».
 * 12.06 : le joueur peut s'eloigner pendant un dialogue non scripte ; le PNJ
 * s'arrete au milieu de sa phrase avec une reaction ecrite (3 par PNJ majeur,
 * content/dialogues/abandons.json).
 *
 * Le runtime ne contient AUCUN texte : tout vient du contenu genere.
 */
package com.velmora.lohen.sim.narrative;

import com.velmora.lohen.sim.core.ContentDb;
import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.GameState;
import com.velmora.lohen.sim.core.Localization;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.core.MiniJson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DialogueRuntime {

    /** Distance au-dela de laquelle un dialogue non scripte est interrompu. */
    public static final float ABANDON_DISTANCE = 9f;
    public static final float ABANDON_GRACE = 1.2f;

    private final ContentDb db;
    private final GameState state;
    private final EventBus bus;
    private final Localization loc;
    private final SubtitleModel subtitles = new SubtitleModel();

    private final List<SubtitleModel.Cue> queue = new ArrayList<SubtitleModel.Cue>(24);
    private final Map<String, List<AbandonLine>> abandons =
            new HashMap<String, List<AbandonLine>>();
    private final Map<String, ContentDb.DialogueChoice> choicePoints =
            new HashMap<String, ContentDb.DialogueChoice>();

    private String sceneId = "";
    private boolean scripted = true;
    private boolean active;
    private int cursor;
    private float abandonTimer;
    private boolean abandoned;

    /* choix courant */
    private ContentDb.DialogueChoice currentChoice;
    private float choiceTimer;
    private boolean choiceOffered;
    private int choicesMade;
    private int timeouts;
    private int linesPlayed;
    private int interruptions;

    public DialogueRuntime(ContentDb db, GameState state, EventBus bus, Localization loc) {
        this.db = db;
        this.state = state;
        this.bus = bus;
        this.loc = loc;
        loadAbandons();
    }

    /** 12.06 : les 3 lignes d'abandon de chaque PNJ majeur. */
    private void loadAbandons() {
        if (db == null) {
            return;
        }
        Map<String, Object> root = db.json("content/dialogues/abandons.json");
        if (root == null) {
            return;
        }
        Map<String, Object> map = MiniJson.child(root, "abandons");
        if (map == null) {
            return;
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            List<Object> list = MiniJson.childList(map, e.getKey());
            if (list == null) {
                continue;
            }
            List<AbandonLine> cues = new ArrayList<AbandonLine>(3);
            for (Object o : list) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                String id = MiniJson.str(m, "id", "");
                String text = MiniJson.str(m, "text", "");
                String direction = MiniJson.str(m, "direction", "");
                cues.add(new AbandonLine(e.getKey(), id, text, direction));
            }
            abandons.put(e.getKey(), cues);
        }
    }

    /** Petite structure pour une ligne d'abandon. */
    static final class AbandonLine {
        final String speaker;
        final String id;
        final String text;
        final String direction;

        AbandonLine(String speaker, String id, String text, String direction) {
            this.speaker = speaker;
            this.id = id;
            this.text = text;
            this.direction = direction;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Demarrage                                                           */
    /* ------------------------------------------------------------------ */

    public boolean start(String sceneId) {
        return start(sceneId, true);
    }

    public boolean start(String sceneId, boolean scripted) {
        if (db == null || sceneId == null) {
            return false;
        }
        ContentDb.DialogueScene scene = db.scene(sceneId);
        if (scene == null || scene.lines.isEmpty()) {
            return false;
        }
        this.sceneId = sceneId;
        this.scripted = scripted;
        this.active = true;
        this.abandoned = false;
        this.abandonTimer = 0f;
        this.cursor = 0;
        this.currentChoice = null;
        this.choiceOffered = false;
        queue.clear();
        choicePoints.clear();
        for (int i = 0; i < scene.lines.size(); i++) {
            ContentDb.DialogueLine line = scene.lines.get(i);
            enqueueLine(line);
        }
        for (int i = 0; i < scene.choices.size(); i++) {
            ContentDb.DialogueChoice c = scene.choices.get(i);
            /* le choix s'insere apres la ligne dont l'id est le pivot */
            choicePoints.put(c.id, c);
        }
        bus.emit(EventBus.DIALOGUE_STARTED, sceneId, scene.title);
        bus.emit(EventBus.SCENE_STARTED, sceneId);
        advance();
        return true;
    }

    private void enqueueLine(ContentDb.DialogueLine line) {
        String text = loc == null ? line.fr : loc.translateLine(line.id, line.fr);
        float duration = Math.min(SubtitleModel.MAX_LINE_SECONDS, Math.max(0.35f, line.duration));
        List<SubtitleModel.Cue> cues = SubtitleModel.buildCues(line.speaker, line.speakerLabel,
                text, duration, line.pauseAfter, line.id);
        queue.addAll(cues);
    }

    /* ------------------------------------------------------------------ */
    /* Boucle                                                              */
    /* ------------------------------------------------------------------ */

    public void update(float dt, float distanceToSpeaker) {
        if (!active) {
            return;
        }
        /* 12.06 : interruption si le joueur s'eloigne pendant un dialogue
           non scripte. Un dialogue scripte (cinematique, Echo) ne s'arrete pas. */
        if (!scripted && distanceToSpeaker > ABANDON_DISTANCE) {
            abandonTimer += dt;
            if (abandonTimer > ABANDON_GRACE) {
                abandon();
                return;
            }
        } else {
            abandonTimer = 0f;
        }

        /* choix en cours : timer eventuel (2 fois dans le chapitre) */
        if (choiceOffered && currentChoice != null && currentChoice.timer > 0f) {
            choiceTimer -= dt;
            bus.emit(EventBus.CHOICE_TIMER, currentChoice.no, Math.max(0f, choiceTimer));
            if (choiceTimer <= 0f) {
                timeouts++;
                /* le silence par defaut : la premiere option de silence,
                   sinon la derniere — jamais un choix impose */
                int pick = silenceOption(currentChoice);
                select(pick);
                return;
            }
        }

        if (subtitles.update(dt)) {
            advance();
        }
    }

    private int silenceOption(ContentDb.DialogueChoice c) {
        for (int i = 0; i < c.options.size(); i++) {
            if (c.options.get(i).silence) {
                return i;
            }
        }
        return c.options.isEmpty() ? -1 : c.options.size() - 1;
    }

    /** Passe a la ligne suivante ; gere les points de choix. */
    private void advance() {
        if (cursor >= queue.size()) {
            finish();
            return;
        }
        SubtitleModel.Cue cue = queue.get(cursor);
        cursor++;
        linesPlayed++;
        subtitles.show(cue);
        bus.emit(EventBus.DIALOGUE_LINE, cue.id, cue.speaker, cue.lines[0]);
        bus.emit(EventBus.SUBTITLE, cue.speaker, cue.speakerLabel, cue.lines[0],
                cue.lineCount > 1 ? cue.lines[1] : "");
        /* un choix est-il attache a cette ligne ? */
        ContentDb.DialogueChoice c = findChoiceFor(cue.id);
        if (c != null && !choicePoints.isEmpty()) {
            openChoice(c);
        }
    }

    private ContentDb.DialogueChoice findChoiceFor(String lineId) {
        for (Map.Entry<String, ContentDb.DialogueChoice> e : choicePoints.entrySet()) {
            ContentDb.DialogueChoice c = e.getValue();
            if (c.id != null && lineId != null && lineId.startsWith(c.id)) {
                choicePoints.remove(c.id);
                return c;
            }
        }
        return null;
    }

    private void openChoice(ContentDb.DialogueChoice c) {
        currentChoice = c;
        choiceOffered = true;
        choiceTimer = c.timer;
        /* 12.05 : le texte de l'option est exactement ce que Lohen va dire */
        String[] texts = new String[c.options.size()];
        boolean[] silences = new boolean[c.options.size()];
        for (int i = 0; i < c.options.size(); i++) {
            texts[i] = c.options.get(i).text;
            silences[i] = c.options.get(i).silence;
        }
        bus.emit(EventBus.CHOICE_OFFERED, c.no, texts, silences, c.timer > 0f);
    }

    public boolean choiceOffered() {
        return choiceOffered;
    }

    public int choiceCount() {
        return currentChoice == null ? 0 : currentChoice.options.size();
    }

    public String choiceText(int i) {
        if (currentChoice == null || i < 0 || i >= currentChoice.options.size()) {
            return "";
        }
        return currentChoice.options.get(i).text;
    }

    public boolean choiceIsSilence(int i) {
        if (currentChoice == null || i < 0 || i >= currentChoice.options.size()) {
            return false;
        }
        return currentChoice.options.get(i).silence;
    }

    public boolean choiceHasTimer() {
        return currentChoice != null && currentChoice.timer > 0f;
    }

    public float choiceTimerRemaining() {
        return currentChoice == null ? 0f : Math.max(0f, choiceTimer);
    }

    public int choiceNumber() {
        return currentChoice == null ? -1 : currentChoice.no;
    }

    public void selectChoice(int index) {
        if (!choiceOffered || currentChoice == null) {
            return;
        }
        select(index);
    }

    private void select(int index) {
        ContentDb.DialogueChoice c = currentChoice;
        choiceOffered = false;
        currentChoice = null;
        if (c == null || index < 0) {
            return;
        }
        choicesMade++;
        String text = index < c.options.size() ? c.options.get(index).text : "";
        if (state != null) {
            state.recordChoice(c.id, index);
            state.setFlag("choice_text_" + c.id, text);
        }
        bus.emit(EventBus.DIALOGUE_CHOICE_MADE, c.no, index, text);
        /* branche : les lignes de reponse sont dans le graphe, jamais dupliquees */
        List<String> branch = c.branches.get(String.valueOf(index));
        if (branch != null && db != null) {
            for (int i = 0; i < branch.size(); i++) {
                ContentDb.DialogueLine line = db.line(branch.get(i));
                if (line != null) {
                    enqueueLine(line);
                }
            }
        }
        /* le jeu n'affiche JAMAIS « X se souviendra de cela » (12.05) */
        advance();
    }

    /** 12.06 : le PNJ s'arrete au milieu de sa phrase, avec une reaction ecrite. */
    public void abandon() {
        if (!active || abandoned) {
            return;
        }
        abandoned = true;
        interruptions++;
        SubtitleModel.Cue cue = subtitles.current();
        String speaker = cue == null ? "" : cue.speaker;
        bus.emit(EventBus.DIALOGUE_INTERRUPTED, sceneId, speaker);
        List<AbandonLine> lines = abandons.get(speaker);
        if (lines == null || lines.isEmpty()) {
            lines = abandons.get("DEFAUT");
        }
        if (lines != null && !lines.isEmpty()) {
            AbandonLine a = lines.get(interruptions % lines.size());
            String text = loc == null ? a.text : loc.translateLine(a.id, a.text);
            List<SubtitleModel.Cue> cues = SubtitleModel.buildCues(speaker, speaker,
                    text, Math.min(3.2f, Math.max(1.2f, text.length() * 0.09f)), 0.8f, a.id);
            if (!cues.isEmpty()) {
                subtitles.show(cues.get(0));
                bus.emit(EventBus.SUBTITLE, speaker, speaker, cues.get(0).lines[0], "");
                bus.emit(EventBus.DIALOGUE_LINE, a.id, speaker, a.text);
            }
        }
        /* la ligne coupee reste affichee telle quelle, inachevee */
        active = false;
        choiceOffered = false;
        currentChoice = null;
        bus.emit(EventBus.SCENE_ENDED, sceneId, "abandoned");
        bus.emit(EventBus.DIALOGUE_FINISHED, sceneId, false);
    }

    private void finish() {
        active = false;
        choiceOffered = false;
        currentChoice = null;
        subtitles.clear();
        if (state != null) {
            state.setFlag("scene_" + sceneId);
        }
        bus.emit(EventBus.SCENE_ENDED, sceneId, "complete");
        bus.emit(EventBus.DIALOGUE_FINISHED, sceneId, true);
    }

    public boolean active() {
        return active;
    }

    public String sceneId() {
        return sceneId;
    }

    public boolean scripted() {
        return scripted;
    }

    public SubtitleModel subtitles() {
        return subtitles;
    }

    public String currentSpeaker() {
        SubtitleModel.Cue c = subtitles.current();
        return c == null ? "" : c.speaker;
    }

    public String currentSpeakerLabel() {
        SubtitleModel.Cue c = subtitles.current();
        return c == null ? "" : c.speakerLabel;
    }

    public int linesPlayed() {
        return linesPlayed;
    }

    public int choicesMade() {
        return choicesMade;
    }

    public int timeouts() {
        return timeouts;
    }

    public int interruptions() {
        return interruptions;
    }

    public int remainingLines() {
        return Math.max(0, queue.size() - cursor);
    }

    public float progress() {
        return queue.isEmpty() ? 0f : Maths.clamp01(cursor / (float) queue.size());
    }

    /** Force la fin (cinematique skippée, changement de sequence). */
    public void stop() {
        if (!active) {
            return;
        }
        active = false;
        choiceOffered = false;
        currentChoice = null;
        subtitles.clear();
        bus.emit(EventBus.SCENE_ENDED, sceneId, "stopped");
        bus.emit(EventBus.DIALOGUE_FINISHED, sceneId, false);
    }

    public void reset() {
        stop();
        queue.clear();
        cursor = 0;
        sceneId = "";
        linesPlayed = 0;
        choicesMade = 0;
        timeouts = 0;
        interruptions = 0;
        abandoned = false;
    }

    public int abandonLineCount(String speaker) {
        List<AbandonLine> l = abandons.get(speaker);
        return l == null ? 0 : l.size();
    }

    public boolean hasAbandonLines(String speaker) {
        return abandonLineCount(speaker) > 0;
    }
}
