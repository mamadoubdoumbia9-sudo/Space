/*
 * LOHEN — sim/narrative/QuestGraph.java
 *
 * Le graphe des 57 noeuds du Chapitre 1 (content/quests/quest_graph.json).
 * Il ne raconte rien : il ordonne. Chaque noeud declare une nature
 * (cinematique, Echo, dialogue, couloir, beat de traversee, combat, boss,
 * lettre, generique) et la liste de ses successeurs. La progression est
 * pilotee par les evenements du jeu, jamais par un compteur de quetes.
 *
 * 09.24 [OBL] : AUCUN compteur de collectibles n'est affiche en jeu.
 * 09.40 [OBL] : un evenement toutes les 45 a 90 s sur le chemin principal.
 *               Un trou de plus de 2 minutes est un BUG de design.
 * 09.23 [OBL] : AUCUN backtracking force.
 * 12.04 : jamais de marqueur « important ».
 */
package com.velmora.lohen.sim.narrative;

import com.velmora.lohen.sim.core.ContentDb;
import com.velmora.lohen.sim.core.EventBus;
import com.velmora.lohen.sim.core.GameState;
import com.velmora.lohen.sim.core.Localization;
import com.velmora.lohen.sim.core.MiniJson;
import com.velmora.lohen.sim.math.Maths;
import com.velmora.lohen.sim.world.SequenceAtlas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuestGraph implements EventBus.Listener {

    /** 09.40 : densite d'evenements sur le chemin principal. */
    public static final float EVENT_MIN_SECONDS = SequenceAtlas.EVENT_MIN_SECONDS;
    public static final float EVENT_MAX_SECONDS = SequenceAtlas.EVENT_MAX_SECONDS;
    public static final float PACING_BUG_SECONDS = SequenceAtlas.EVENT_BUG_SECONDS;

    public static final String START_NODE = "S1_arrival";
    public static final String END_NODE = "CREDITS";

    private final ContentDb db;
    private final EventBus bus;
    private final GameState state;
    private final Localization loc;

    private final Map<String, ContentDb.QuestNode> nodes =
            new LinkedHashMap<String, ContentDb.QuestNode>();
    private final List<String> visited = new ArrayList<String>(64);

    private String current = "";
    private String previous = "";
    private String sequence = "S1";
    private String objectiveKey = "";
    private float nodeTime;
    private float sinceEvent;
    private int eventsCounted;
    private int advances;
    private int stalls;
    private boolean chapterComplete;
    private float completionTime;
    private int pacingHoles;

    public QuestGraph(ContentDb db, EventBus bus, GameState state, Localization loc) {
        this.db = db;
        this.bus = bus;
        this.state = state;
        this.loc = loc;
        List<ContentDb.QuestNode> list = db == null ? null : db.questNodes();
        if (list != null) {
            for (ContentDb.QuestNode n : list) {
                nodes.put(n.id, n);
            }
        }
        if (bus != null) {
            bus.connect(EventBus.CINEMATIC_ENDED, this);
            bus.connect(EventBus.ECHO_FINISHED, this);
            bus.connect(EventBus.DIALOGUE_FINISHED, this);
            bus.connect(EventBus.SCENE_STARTED, this);
            bus.connect(EventBus.ZONE_ENTERED, this);
            bus.connect(EventBus.CHECKPOINT_REACHED, this);
            bus.connect(EventBus.COMBAT_CLEARED, this);
            bus.connect(EventBus.BOSS_DEFEATED, this);
            bus.connect(EventBus.VERB_USED, this);
            bus.connect(EventBus.LETTER_DELIVERY_SCENE, this);
            bus.connect(EventBus.LETTER_CLOSED, this);
            bus.connect(EventBus.CREDITS_END, this);
            bus.connect(EventBus.FINAL_BEAT, this);
            bus.connect(EventBus.SAVE_LOADED, this);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Demarrage                                                           */
    /* ------------------------------------------------------------------ */

    public boolean begin() {
        if (nodes.isEmpty()) {
            return false;
        }
        String start = nodes.containsKey(START_NODE) ? START_NODE : nodes.keySet().iterator().next();
        enter(start);
        return true;
    }

    public boolean restore(String nodeId) {
        if (nodeId == null || !nodes.containsKey(nodeId)) {
            return false;
        }
        enter(nodeId);
        return true;
    }

    private void enter(String nodeId) {
        ContentDb.QuestNode n = nodes.get(nodeId);
        if (n == null) {
            return;
        }
        previous = current;
        current = nodeId;
        nodeTime = 0f;
        if (!visited.contains(nodeId)) {
            visited.add(nodeId);
        }
        if (n.seq != null && !n.seq.isEmpty()) {
            sequence = n.seq;
        }
        objectiveKey = n.objective == null ? "" : n.objective;
        if (state != null) {
            state.setQuestNode(nodeId);
            state.setSequence(sequence);
        }
        noteEvent("node:" + nodeId);
        if (bus != null) {
            bus.emit(EventBus.OBJECTIVE_CHANGED, objectiveText(), sequence, nodeId, n.kind);
            if (n.seq != null && !n.seq.isEmpty() && !n.seq.equals(previous == null ? "" : seqOf(previous))) {
                bus.emit(EventBus.SEQUENCE_CHANGED, n.seq, previous);
            }
        }
        /* les noeuds "auto" se valident immediatement : ils ne font que poser
         * un titre de sequence ou un drapeau. */
        if ("title".equals(n.kind) || "flag".equals(n.kind)) {
            advance();
        }
    }

    private String seqOf(String nodeId) {
        ContentDb.QuestNode n = nodes.get(nodeId);
        return n == null ? "" : n.seq;
    }

    /** Texte d'objectif localise — jamais un marqueur « important » (12.04). */
    public String objectiveText() {
        if (objectiveKey == null || objectiveKey.isEmpty()) {
            return "";
        }
        return loc == null ? objectiveKey : loc.t(objectiveKey);
    }

    /* ------------------------------------------------------------------ */
    /* Progression                                                         */
    /* ------------------------------------------------------------------ */

    /** Passe au successeur par defaut (le premier de la liste). */
    public boolean advance() {
        return advance(0);
    }

    public boolean advance(int branch) {
        ContentDb.QuestNode n = nodes.get(current);
        if (n == null || n.next.isEmpty()) {
            if (END_NODE.equals(current)) {
                finishChapter();
            }
            return false;
        }
        int i = Maths.clamp(branch, 0, n.next.size() - 1);
        String nextId = n.next.get(i);
        if (!nodes.containsKey(nextId)) {
            return false;
        }
        advances++;
        enter(nextId);
        return true;
    }

    /** Successeur choisi par identifiant (choix de dialogue, embranchements). */
    public boolean advanceTo(String nodeId) {
        ContentDb.QuestNode n = nodes.get(current);
        if (n == null || !n.next.contains(nodeId) || !nodes.containsKey(nodeId)) {
            return false;
        }
        advances++;
        enter(nodeId);
        return true;
    }

    private void finishChapter() {
        if (chapterComplete) {
            return;
        }
        chapterComplete = true;
        completionTime = nodeTime;
        if (state != null) {
            state.setFlag("chapter1.complete");
        }
        if (bus != null) {
            bus.emit(EventBus.CHAPTER_COMPLETE, visited.size(), advances);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Conditions de validation par nature de noeud                        */
    /* ------------------------------------------------------------------ */

    @Override
    public void onEvent(String signal, Object[] args) {
        ContentDb.QuestNode n = nodes.get(current);
        if (n == null) {
            return;
        }
        if (EventBus.SAVE_LOADED.equals(signal)) {
            if (state != null) {
                restore(state.questNode());
            }
            return;
        }
        String kind = n.kind;
        boolean done = false;
        if (EventBus.CINEMATIC_ENDED.equals(signal) && "cinematic".equals(kind)) {
            done = true;
        } else if (EventBus.ECHO_FINISHED.equals(signal)
                && ("echo".equals(kind) || "echo_playable".equals(kind))) {
            done = true;
        } else if (EventBus.DIALOGUE_FINISHED.equals(signal) && "dialogue".equals(kind)) {
            done = true;
        } else if (EventBus.LETTER_DELIVERY_SCENE.equals(signal) && "delivery".equals(kind)) {
            done = true;
        } else if (EventBus.COMBAT_CLEARED.equals(signal) && "combat_chain".equals(kind)) {
            done = true;
        } else if (EventBus.BOSS_DEFEATED.equals(signal) && "boss".equals(kind)) {
            done = true;
        } else if (EventBus.ZONE_ENTERED.equals(signal) && "corridor".equals(kind)) {
            done = true;
        } else if (EventBus.LETTER_CLOSED.equals(signal) && "letter".equals(kind)) {
            done = true;
        } else if (EventBus.CREDITS_END.equals(signal) && "credits".equals(kind)) {
            done = true;
            finishChapter();
        } else if (EventBus.FINAL_BEAT.equals(signal) && "ending".equals(kind)) {
            done = true;
        } else if (EventBus.CHECKPOINT_REACHED.equals(signal)) {
            String cp = str(args, 0);
            if (cp != null && cp.equals(n.checkpoint)) {
                done = true;
            }
        } else if (EventBus.SCENE_STARTED.equals(signal)) {
            String scene = str(args, 0);
            if (scene != null && scene.equals(n.dialogue)) {
                /* la scene est lancee : le noeud attend sa fin */
                noteEvent("scene:" + scene);
            }
        } else if (EventBus.VERB_USED.equals(signal) && "beat".equals(kind)) {
            String verb = str(args, 0);
            String want = rawStr(n, "verb");
            if (want == null || want.isEmpty() || want.equals(verb)) {
                done = true;
            }
        }
        if (done) {
            noteEvent(kind);
            advance();
        }
    }

    private static String str(Object[] args, int i) {
        return args != null && i < args.length && args[i] != null ? String.valueOf(args[i]) : "";
    }

    @SuppressWarnings("unchecked")
    private static String rawStr(ContentDb.QuestNode n, String key) {
        if (n.raw == null) {
            return "";
        }
        Object o = n.raw.get(key);
        if (o == null) {
            return "";
        }
        if (o instanceof Map) {
            return MiniJson.str((Map<String, Object>) o, "id", "");
        }
        return String.valueOf(o);
    }

    /* ------------------------------------------------------------------ */
    /* Boucle : pacing (09.40) et noeuds a duree                           */
    /* ------------------------------------------------------------------ */

    public void update(float dt) {
        nodeTime += dt;
        sinceEvent += dt;
        ContentDb.QuestNode n = nodes.get(current);
        if (n == null) {
            return;
        }
        /* noeuds a duree declaree (ex. S8_descent : 90 s) */
        float want = rawNum(n, "duration_s");
        if (want > 0f && nodeTime >= want) {
            noteEvent("duration");
            advance();
            return;
        }
        /* 09.40 : un trou de plus de 2 minutes est un BUG de design */
        if (sinceEvent > PACING_BUG_SECONDS) {
            pacingHoles++;
            sinceEvent = 0f;
            if (bus != null) {
                bus.emit(EventBus.PACING_HOLE, current, PACING_BUG_SECONDS);
            }
        }
        /* un noeud qui traine sans evenement est signale au gouverneur */
        if (nodeTime > EVENT_MAX_SECONDS * 6f) {
            stalls++;
            if (bus != null) {
                bus.emit(EventBus.NAVIGATION_STALL, current, nodeTime);
            }
            nodeTime = EVENT_MAX_SECONDS;
        }
    }

    @SuppressWarnings("unchecked")
    private static float rawNum(ContentDb.QuestNode n, String key) {
        if (n.raw == null) {
            return 0f;
        }
        Object o = n.raw.get(key);
        if (o instanceof Number) {
            return ((Number) o).floatValue();
        }
        try {
            return o == null ? 0f : Float.parseFloat(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    /**
     * Tout ce qui compte comme "evenement" au sens de 09.40 : dialogue,
     * Echo, combat, changement de regle, spectacle scripte, decouverte.
     */
    public void noteEvent(String what) {
        sinceEvent = 0f;
        eventsCounted++;
        if (bus != null) {
            bus.emit(EventBus.PACING_EVENT, what, eventsCounted);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Acces                                                               */
    /* ------------------------------------------------------------------ */

    public String current() {
        return current;
    }

    public ContentDb.QuestNode currentNode() {
        return nodes.get(current);
    }

    public String kind() {
        ContentDb.QuestNode n = nodes.get(current);
        return n == null ? "" : n.kind;
    }

    public String sequence() {
        return sequence;
    }

    public String music() {
        ContentDb.QuestNode n = nodes.get(current);
        return n == null || n.music == null ? "" : n.music;
    }

    public String rule() {
        ContentDb.QuestNode n = nodes.get(current);
        return n == null || n.rule == null ? "" : n.rule;
    }

    public List<String> visited() {
        return new ArrayList<String>(visited);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int advances() {
        return advances;
    }

    public int eventsCounted() {
        return eventsCounted;
    }

    public float sinceEvent() {
        return sinceEvent;
    }

    public int pacingHoles() {
        return pacingHoles;
    }

    public int stalls() {
        return stalls;
    }

    public boolean chapterComplete() {
        return chapterComplete;
    }

    public float completionTime() {
        return completionTime;
    }

    public float progress() {
        return nodes.isEmpty() ? 0f : visited.size() / (float) nodes.size();
    }

    /** Altitude visee par la sequence courante — la boussole verticale (14.02). */
    public float objectiveAltitude() {
        SequenceAtlas.Sequence s = SequenceAtlas.get(sequence);
        return s == null ? 0f : s.altitudeTo;
    }

    public boolean isTerminal() {
        ContentDb.QuestNode n = nodes.get(current);
        return n == null || n.next.isEmpty();
    }

    /** 09.23 [OBL] : le graphe ne remonte jamais vers une sequence precedente. */
    public boolean noForcedBacktracking() {
        int lastSeq = 0;
        for (String id : visited) {
            ContentDb.QuestNode n = nodes.get(id);
            if (n == null || n.seq == null || n.seq.length() < 2) {
                continue;
            }
            int s = 0;
            try {
                s = Integer.parseInt(n.seq.substring(1));
            } catch (NumberFormatException e) {
                continue;
            }
            if (s < lastSeq) {
                return false;
            }
            lastSeq = s;
        }
        return true;
    }

    /** Drapeaux reportes au Chapitre 2 (quest_graph.json → flags_chapter2). */
    public List<String> chapter2Flags() {
        List<String> out = new ArrayList<String>(8);
        if (db == null) {
            return out;
        }
        Map<String, Object> root = db.json("content/quests/quest_graph.json");
        if (root == null) {
            return out;
        }
        List<Object> list = MiniJson.childList(root, "flags_chapter2");
        if (list != null) {
            for (Object o : list) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    public void reset() {
        current = "";
        previous = "";
        sequence = "S1";
        objectiveKey = "";
        visited.clear();
        nodeTime = 0f;
        sinceEvent = 0f;
        eventsCounted = 0;
        advances = 0;
        stalls = 0;
        pacingHoles = 0;
        chapterComplete = false;
        completionTime = 0f;
    }
}
