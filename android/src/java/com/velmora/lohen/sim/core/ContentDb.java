/*
 * LOHEN — sim/core/ContentDb.java
 *
 * Base de donnees de contenu : charge les JSON generes par
 * tools/content_pipeline.py (2 832 lignes de dialogue, 147 props, 744 clips,
 * 31 Echos, 38 choix, la lettre canonique, tout le tuning du BLOC 08).
 *
 * Java pur : l'AssetSource est injecte (AssetManager sur device,
 * systeme de fichiers dans les tests — BLOC 18.06).
 */
package com.velmora.lohen.sim.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class ContentDb {

    /** Fournisseur de fichiers (assets Android ou disque pour les tests). */
    public interface AssetSource {
        byte[] read(String path) throws IOException;

        boolean exists(String path);
    }

    private final AssetSource assets;

    /* contenu ------------------------------------------------------------ */
    private Map<String, Object> tuning = new LinkedHashMap<String, Object>();
    private final Map<String, SequenceDialogues> dialogues = new HashMap<String, SequenceDialogues>();
    private final List<PropRecord> props = new ArrayList<PropRecord>();
    private final Map<String, PropRecord> propsById = new HashMap<String, PropRecord>();
    private final List<AnimClip> anims = new ArrayList<AnimClip>();
    private final Map<String, List<AnimClip>> animsByChar = new HashMap<String, List<AnimClip>>();
    private final List<EchoRecord> echos = new ArrayList<EchoRecord>();
    private final Map<String, EchoRecord> echosById = new HashMap<String, EchoRecord>();
    private final List<QuestNode> questNodes = new ArrayList<QuestNode>();
    private final Map<String, QuestNode> questById = new HashMap<String, QuestNode>();
    private final List<ChoiceRecord> choices = new ArrayList<ChoiceRecord>();
    private final Map<String, LetterBlock> letterBlocks = new LinkedHashMap<String, LetterBlock>();
    private Map<String, Object> letterMeta = new LinkedHashMap<String, Object>();
    private final Map<String, Map<String, String>> locales = new LinkedHashMap<String, Map<String, String>>();
    private String localeReference = "fr";
    private final Map<String, LevelData> levels = new HashMap<String, LevelData>();
    private final List<String> loadErrors = new ArrayList<String>();
    private int loadedBytes = 0;

    public ContentDb(AssetSource assets) {
        this.assets = assets;
    }

    /* ------------------------------------------------------------------ */
    /* Modeles                                                             */
    /* ------------------------------------------------------------------ */

    public static final class DialogueLine {
        public String id = "";
        public String speaker = "";
        public String speakerLabel = "";
        public float duration = 2.0f;
        public String emotion = "";
        public float priority = 0.5f;
        public String fr = "";
        public String direction = "";
        public boolean bark = false;
        public float pauseAfter = 0.45f;
    }

    public static final class ChoiceOption {
        public int index;
        public String text = "";
        public boolean silence;
    }

    public static final class DialogueChoice {
        public String id = "";
        public int no = -1;
        public float timer = 0f;
        public String note = "";
        public final List<ChoiceOption> options = new ArrayList<ChoiceOption>(3);
        public final Map<String, List<String>> branches = new LinkedHashMap<String, List<String>>();
    }

    public static final class DialogueScene {
        public String id = "";
        public String seq = "";
        public String title = "";
        public String context = "";
        public final List<DialogueLine> lines = new ArrayList<DialogueLine>();
        public final List<DialogueChoice> choices = new ArrayList<DialogueChoice>();
        public final Map<String, DialogueLine> linesById = new LinkedHashMap<String, DialogueLine>();
    }

    public static final class SequenceDialogues {
        public String seq = "";
        public float barkCooldown = 480f;
        public float barkGain = 1f;
        public final List<DialogueScene> scenes = new ArrayList<DialogueScene>();
        public final Map<String, DialogueScene> scenesById = new LinkedHashMap<String, DialogueScene>();
    }

    public static final class PropRecord {
        public String id = "";
        public String slug = "";
        public String name = "";
        public String seq = "";
        public String asset = "";
        public String positionNote = "";
        public String timing = "";
        public String statut = "";
        public int trisLod0 = 0;
        public String echoId = "";
        public boolean climbable;
        public boolean pickup;
        public boolean readable;
        public String collision = "";
        public String seen = "";
    }

    public static final class AnimClip {
        public String id = "";
        public String clip = "";
        public String character = "";
        public String category = "";
        public float duration = 1f;
        public boolean loop;
        public String rootMotion = "RM-NONE";
        public float blendIn = 0.2f;
        public float blendOut = 0.2f;
        public String layer = "L0";
        public int priority = 0;
        public String family = "ACTING";
        public String acting = "";
        public String events = "";
        public String transitions = "";
    }

    public static final class EchoRecord {
        public String id = "";
        public String seq = "";
        public String object = "";
        public String mode = "A";
        public float duration = 60f;
        public boolean mandatory;
        public String prop = "";
        public float breathCost = 18f;
        public float ritual = 2.4f;
        public int ghostMax = 12;
        public boolean amberRim;
        public boolean looksAtPlayer;
    }

    public static final class QuestNode {
        public String id = "";
        public String seq = "";
        public String kind = "beat";
        public String objective = "";
        public String echo = "";
        public String cinematic = "";
        public String dialogue = "";
        public String corridor = "";
        public String music = "";
        public String rule = "";
        public String checkpoint = "";
        public String combat = "";
        public final List<String> next = new ArrayList<String>(2);
        public final Map<String, Object> raw = new LinkedHashMap<String, Object>();
    }

    public static final class ChoiceRecord {
        public int no;
        public String seq = "";
        public String doc = "";
        public String title = "";
        public float timer;
        public String effect = "";
        public boolean displaysOptions = true;
        public String scene = "";
        public String choiceId = "";
        public final List<ChoiceOption> options = new ArrayList<ChoiceOption>(3);
    }

    public static final class LetterBlock {
        public String text = "";
        public boolean heading;
        public boolean signature;
        public boolean postScriptum;
        public String staging = "";
    }

    /* ------------------------------------------------------------------ */
    /* Chargement                                                          */
    /* ------------------------------------------------------------------ */

    private String readText(String path) {
        try {
            byte[] raw = assets.read(path);
            if (raw == null) {
                return null;
            }
            loadedBytes += raw.length;
            byte[] data = isGzip(raw) ? gunzip(raw) : raw;
            return new String(data, "UTF-8");
        } catch (IOException e) {
            loadErrors.add(path + " : " + e.getClass().getSimpleName());
            return null;
        }
    }

    private static boolean isGzip(byte[] b) {
        return b.length > 2 && (b[0] & 0xFF) == 0x1F && (b[1] & 0xFF) == 0x8B;
    }

    private static byte[] gunzip(byte[] b) throws IOException {
        GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(b));
        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length * 4);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toByteArray();
    }

    /** Charge tout le contenu sauf les niveaux (charges par le ZoneStreamer). */
    public boolean loadAll() {
        loadTuning();
        loadLocales();
        loadDialogues();
        loadProps();
        loadAnims();
        loadEchos();
        loadQuests();
        loadChoices();
        loadLetter();
        return loadErrors.isEmpty();
    }

    private void loadTuning() {
        String text = readText("content/tuning/tuning.json");
        if (text == null) {
            loadErrors.add("tuning.json manquant");
            return;
        }
        tuning = MiniJson.parseObject(text);
    }

    private void loadLocales() {
        String text = readText("content/locales/locales.json");
        if (text == null) {
            return;
        }
        Map<String, Object> root = MiniJson.parseObject(text);
        localeReference = MiniJson.str(root, "reference", "fr");
        Map<String, Object> langs = MiniJson.child(root, "locales");
        if (langs != null) {
            for (Map.Entry<String, Object> e : langs.entrySet()) {
                Map<String, Object> table = MiniJson.obj(e.getValue());
                if (table == null) {
                    continue;
                }
                Map<String, String> flat = new LinkedHashMap<String, String>(table.size());
                for (Map.Entry<String, Object> k : table.entrySet()) {
                    flat.put(k.getKey(), String.valueOf(k.getValue()));
                }
                locales.put(e.getKey(), flat);
            }
        }
    }

    private void loadDialogues() {
        for (int i = 1; i <= 8; i++) {
            String path = "content/dialogues/dialogues_s" + i + ".json";
            String text = readText(path);
            if (text == null) {
                continue;
            }
            Map<String, Object> root = MiniJson.parseObject(text);
            SequenceDialogues sd = new SequenceDialogues();
            sd.seq = MiniJson.str(root, "seq", "S" + i);
            sd.barkCooldown = MiniJson.num(root, "bark_cooldown_s", 480f);
            sd.barkGain = MiniJson.num(root, "bark_gain", 1f);
            List<Object> scenes = MiniJson.childList(root, "scenes");
            if (scenes != null) {
                for (Object so : scenes) {
                    Map<String, Object> sm = MiniJson.obj(so);
                    if (sm == null) {
                        continue;
                    }
                    DialogueScene scene = new DialogueScene();
                    scene.id = MiniJson.str(sm, "id", "");
                    scene.seq = MiniJson.str(sm, "seq", sd.seq);
                    scene.title = MiniJson.str(sm, "title", "");
                    scene.context = MiniJson.str(sm, "context", "");
                    List<Object> lines = MiniJson.childList(sm, "lines");
                    if (lines != null) {
                        for (Object lo : lines) {
                            Map<String, Object> lm = MiniJson.obj(lo);
                            if (lm == null) {
                                continue;
                            }
                            DialogueLine dl = new DialogueLine();
                            dl.id = MiniJson.str(lm, "id", "");
                            dl.speaker = MiniJson.str(lm, "spk", "");
                            dl.speakerLabel = MiniJson.str(lm, "spk_label", dl.speaker);
                            dl.duration = MiniJson.num(lm, "dur", 2f);
                            dl.emotion = MiniJson.str(lm, "emo", "");
                            dl.priority = MiniJson.num(lm, "prio", 0.5f);
                            dl.fr = MiniJson.str(lm, "fr", "");
                            dl.direction = MiniJson.str(lm, "dir", "");
                            dl.bark = MiniJson.bool(lm, "bark", false);
                            dl.pauseAfter = MiniJson.num(lm, "pause_after", 0.45f);
                            scene.lines.add(dl);
                            if (!dl.id.isEmpty()) {
                                scene.linesById.put(dl.id, dl);
                            }
                        }
                    }
                    List<Object> chs = MiniJson.childList(sm, "choices");
                    if (chs != null) {
                        for (Object co : chs) {
                            Map<String, Object> cm = MiniJson.obj(co);
                            if (cm == null) {
                                continue;
                            }
                            DialogueChoice ch = new DialogueChoice();
                            ch.id = MiniJson.str(cm, "id", "");
                            ch.no = MiniJson.intNum(cm, "no", -1);
                            ch.timer = MiniJson.num(cm, "timer", 0f);
                            ch.note = MiniJson.str(cm, "note", "");
                            List<Object> opts = MiniJson.childList(cm, "options");
                            if (opts != null) {
                                for (Object oo : opts) {
                                    Map<String, Object> om = MiniJson.obj(oo);
                                    if (om == null) {
                                        continue;
                                    }
                                    ChoiceOption opt = new ChoiceOption();
                                    opt.index = MiniJson.intNum(om, "index", ch.options.size() + 1);
                                    opt.text = MiniJson.str(om, "text", "");
                                    opt.silence = MiniJson.bool(om, "silence", false);
                                    ch.options.add(opt);
                                }
                            }
                            Map<String, Object> branches = MiniJson.child(cm, "branches");
                            if (branches != null) {
                                for (Map.Entry<String, Object> be : branches.entrySet()) {
                                    List<Object> ids = MiniJson.list(be.getValue());
                                    List<String> out = new ArrayList<String>(ids == null ? 0 : ids.size());
                                    if (ids != null) {
                                        for (Object id : ids) {
                                            out.add(String.valueOf(id));
                                        }
                                    }
                                    ch.branches.put(be.getKey(), out);
                                }
                            }
                            scene.choices.add(ch);
                        }
                    }
                    sd.scenes.add(scene);
                    sd.scenesById.put(scene.id, scene);
                }
            }
            dialogues.put(sd.seq, sd);
        }
    }

    private void loadProps() {
        String text = readText("content/props/props.json");
        if (text == null) {
            return;
        }
        Map<String, Object> root = MiniJson.parseObject(text);
        List<Object> list = MiniJson.childList(root, "props");
        if (list == null) {
            return;
        }
        for (Object o : list) {
            Map<String, Object> m = MiniJson.obj(o);
            if (m == null) {
                continue;
            }
            PropRecord p = new PropRecord();
            p.id = MiniJson.str(m, "id", "");
            p.slug = MiniJson.str(m, "slug", "");
            p.name = MiniJson.str(m, "name", "");
            p.seq = MiniJson.str(m, "seq", "");
            p.asset = MiniJson.str(m, "asset", "");
            p.positionNote = MiniJson.str(m, "position_note", "");
            p.timing = MiniJson.str(m, "timing", "");
            p.statut = MiniJson.str(m, "statut", "");
            p.trisLod0 = MiniJson.intNum(m, "tris_lod0", 0);
            p.echoId = MiniJson.str(m, "echo", "");
            p.climbable = MiniJson.bool(m, "climbable", false);
            p.pickup = MiniJson.bool(m, "pickup", false);
            p.readable = MiniJson.bool(m, "readable", false);
            p.collision = MiniJson.str(m, "collision", "");
            p.seen = MiniJson.str(m, "seen", "");
            props.add(p);
            propsById.put(p.id, p);
        }
    }

    private void loadAnims() {
        String text = readText("content/anims/anims.json");
        if (text == null) {
            return;
        }
        Map<String, Object> root = MiniJson.parseObject(text);
        List<Object> list = MiniJson.childList(root, "clips");
        if (list == null) {
            return;
        }
        for (Object o : list) {
            Map<String, Object> m = MiniJson.obj(o);
            if (m == null) {
                continue;
            }
            AnimClip c = new AnimClip();
            c.id = MiniJson.str(m, "id", "");
            c.clip = MiniJson.str(m, "clip", "");
            c.character = MiniJson.str(m, "char", "");
            c.category = MiniJson.str(m, "cat", "");
            c.duration = MiniJson.num(m, "dur", 1f);
            c.loop = MiniJson.bool(m, "loop", false);
            c.rootMotion = MiniJson.str(m, "root", "RM-NONE");
            c.blendIn = MiniJson.num(m, "blend_in", 0.2f);
            c.blendOut = MiniJson.num(m, "blend_out", 0.2f);
            c.layer = MiniJson.str(m, "layer", "L0");
            c.priority = MiniJson.intNum(m, "prio", 0);
            c.family = MiniJson.str(m, "family", "ACTING");
            c.acting = MiniJson.str(m, "jeu", "");
            c.events = MiniJson.str(m, "evt", "");
            c.transitions = MiniJson.str(m, "trans", "");
            anims.add(c);
            List<AnimClip> byChar = animsByChar.get(c.character);
            if (byChar == null) {
                byChar = new ArrayList<AnimClip>();
                animsByChar.put(c.character, byChar);
            }
            byChar.add(c);
        }
    }

    private void loadEchos() {
        String text = readText("content/echos/echos.json");
        if (text == null) {
            return;
        }
        Map<String, Object> root = MiniJson.parseObject(text);
        List<Object> list = MiniJson.childList(root, "echos");
        if (list == null) {
            return;
        }
        for (Object o : list) {
            Map<String, Object> m = MiniJson.obj(o);
            if (m == null) {
                continue;
            }
            EchoRecord e = new EchoRecord();
            e.id = MiniJson.str(m, "id", "");
            e.seq = MiniJson.str(m, "seq", "");
            e.object = MiniJson.str(m, "object", "");
            e.mode = MiniJson.str(m, "mode", "A");
            e.duration = MiniJson.num(m, "duration_s", 60f);
            e.mandatory = MiniJson.bool(m, "mandatory", false);
            e.prop = MiniJson.str(m, "prop", "");
            e.breathCost = MiniJson.num(m, "breath_cost", 18f);
            e.ritual = MiniJson.num(m, "ritual_s", 2.4f);
            e.ghostMax = MiniJson.intNum(m, "ghost_max", 12);
            e.amberRim = MiniJson.bool(m, "amber_rim", false);
            e.looksAtPlayer = MiniJson.bool(m, "looks_at_player", false);
            echos.add(e);
            echosById.put(e.id, e);
        }
    }

    private void loadQuests() {
        String text = readText("content/quests/quest_graph.json");
        if (text == null) {
            return;
        }
        Map<String, Object> root = MiniJson.parseObject(text);
        List<Object> list = MiniJson.childList(root, "nodes");
        if (list == null) {
            return;
        }
        for (Object o : list) {
            Map<String, Object> m = MiniJson.obj(o);
            if (m == null) {
                continue;
            }
            QuestNode n = new QuestNode();
            n.raw.putAll(m);
            n.id = MiniJson.str(m, "id", "");
            n.seq = MiniJson.str(m, "seq", "");
            n.kind = MiniJson.str(m, "kind", "beat");
            n.objective = MiniJson.str(m, "objective", "");
            n.echo = MiniJson.str(m, "echo", "");
            n.cinematic = MiniJson.str(m, "cine", MiniJson.str(m, "cinematic", ""));
            n.dialogue = MiniJson.str(m, "scene", MiniJson.str(m, "dialogue", ""));
            n.corridor = MiniJson.str(m, "corridor", "");
            n.music = MiniJson.str(m, "music", "");
            n.rule = MiniJson.str(m, "rule", "");
            n.checkpoint = MiniJson.str(m, "checkpoint", "");
            n.combat = MiniJson.str(m, "combat", "");
            List<Object> next = MiniJson.childList(m, "next");
            if (next != null) {
                for (Object nx : next) {
                    n.next.add(String.valueOf(nx));
                }
            }
            questNodes.add(n);
            questById.put(n.id, n);
        }
    }

    private void loadChoices() {
        String text = readText("content/quests/choices.json");
        if (text == null) {
            return;
        }
        Map<String, Object> root = MiniJson.parseObject(text);
        List<Object> list = MiniJson.childList(root, "choices");
        if (list == null) {
            return;
        }
        for (Object o : list) {
            Map<String, Object> m = MiniJson.obj(o);
            if (m == null) {
                continue;
            }
            ChoiceRecord c = new ChoiceRecord();
            c.no = MiniJson.intNum(m, "no", -1);
            c.seq = MiniJson.str(m, "seq", "");
            c.doc = MiniJson.str(m, "doc", "");
            c.title = MiniJson.str(m, "title", "");
            c.timer = MiniJson.num(m, "timer_s", 0f);
            c.effect = MiniJson.str(m, "effect", "");
            c.displaysOptions = MiniJson.bool(m, "displays_options", true);
            c.scene = MiniJson.str(m, "scene", "");
            c.choiceId = MiniJson.str(m, "choice_id", "");
            List<Object> opts = MiniJson.childList(m, "options");
            if (opts != null) {
                for (Object oo : opts) {
                    Map<String, Object> om = MiniJson.obj(oo);
                    if (om == null) {
                        continue;
                    }
                    ChoiceOption opt = new ChoiceOption();
                    opt.index = MiniJson.intNum(om, "index", 0);
                    opt.text = MiniJson.str(om, "text", "");
                    opt.silence = MiniJson.bool(om, "silence", false);
                    c.options.add(opt);
                }
            }
            choices.add(c);
        }
    }

    private void loadLetter() {
        String text = readText("content/letter/lettre_esteban.json");
        if (text == null) {
            loadErrors.add("lettre_esteban.json manquante — BLOC 20 [OBL]");
            return;
        }
        Map<String, Object> root = MiniJson.parseObject(text);
        letterMeta = root;
        List<Object> blocks = MiniJson.childList(root, "blocks");
        List<Object> notes = MiniJson.childList(root, "staging_notes");
        List<String> noteList = new ArrayList<String>();
        if (notes != null) {
            for (Object n : notes) {
                noteList.add(String.valueOf(n));
            }
        }
        if (blocks != null) {
            int i = 0;
            for (Object o : blocks) {
                String t = String.valueOf(o);
                LetterBlock b = new LetterBlock();
                b.text = t;
                b.heading = i == 0 && t.trim().equals("0114");
                b.signature = t.trim().startsWith("Esteban");
                b.postScriptum = t.trim().startsWith("P.-S.");
                /* rattachement des notes de mise en scene par citation */
                for (String note : noteList) {
                    int q1 = note.indexOf('\u00ab');
                    int q2 = note.indexOf('\u00bb');
                    if (q1 >= 0 && q2 > q1) {
                        String quoted = note.substring(q1 + 1, q2).trim();
                        if (quoted.length() > 8 && t.contains(quoted.substring(0, Math.min(24, quoted.length())))) {
                            b.staging = note;
                            break;
                        }
                    }
                }
                letterBlocks.put("B" + (i++), b);
            }
        }
    }

    public boolean loadLevel(String seq) {
        if (levels.containsKey(seq)) {
            return true;
        }
        String text = readText("content/levels/level_" + seq.toLowerCase() + ".json");
        if (text == null) {
            return false;
        }
        LevelData data = LevelData.parse(MiniJson.parseObject(text));
        if (data == null) {
            return false;
        }
        levels.put(seq, data);
        return true;
    }

    public void registerLevel(String seq, LevelData data) {
        levels.put(seq, data);
    }

    /* ------------------------------------------------------------------ */
    /* Accesseurs                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Charge un JSON arbitraire du contenu (utilisé par les systemes qui
     * ajoutent des donnees auteurs : lignes d'abandon 12.06, tables de
     * cinematiques 07.21, LUT, VFX). Retourne null si absent.
     */
    public Map<String, Object> json(String path) {
        String text = readText(path);
        return text == null ? null : MiniJson.parseObject(text);
    }

    public Map<String, Object> tuning() {
        return tuning;
    }

    public Map<String, Object> tuningGroup(String name) {
        Map<String, Object> g = MiniJson.child(tuning, name);
        return g == null ? Collections.<String, Object>emptyMap() : g;
    }

    public float tuningNum(String group, String key, float dflt) {
        return MiniJson.num(tuningGroup(group), key, dflt);
    }

    public SequenceDialogues dialogues(String seq) {
        return dialogues.get(seq);
    }

    public Map<String, SequenceDialogues> allDialogues() {
        return Collections.unmodifiableMap(dialogues);
    }

    public DialogueScene scene(String sceneId) {
        for (SequenceDialogues sd : dialogues.values()) {
            DialogueScene s = sd.scenesById.get(sceneId);
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    public DialogueLine line(String lineId) {
        for (SequenceDialogues sd : dialogues.values()) {
            for (DialogueScene s : sd.scenes) {
                DialogueLine l = s.linesById.get(lineId);
                if (l != null) {
                    return l;
                }
            }
        }
        return null;
    }

    public List<PropRecord> props() {
        return Collections.unmodifiableList(props);
    }

    public PropRecord prop(String id) {
        return propsById.get(id);
    }

    public List<AnimClip> anims() {
        return Collections.unmodifiableList(anims);
    }

    public List<AnimClip> animsFor(String character) {
        List<AnimClip> l = animsByChar.get(character);
        return l == null ? Collections.<AnimClip>emptyList() : l;
    }

    public AnimClip animById(String id) {
        for (AnimClip c : anims) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }

    public List<EchoRecord> echos() {
        return Collections.unmodifiableList(echos);
    }

    public EchoRecord echo(String id) {
        return echosById.get(id);
    }

    public List<QuestNode> questNodes() {
        return Collections.unmodifiableList(questNodes);
    }

    public QuestNode questNode(String id) {
        return questById.get(id);
    }

    public List<ChoiceRecord> choices() {
        return Collections.unmodifiableList(choices);
    }

    public Map<String, LetterBlock> letterBlocks() {
        return Collections.unmodifiableMap(letterBlocks);
    }

    public Map<String, Object> letterMeta() {
        return letterMeta;
    }

    public LevelData level(String seq) {
        return levels.get(seq);
    }

    public Map<String, LevelData> levels() {
        return Collections.unmodifiableMap(levels);
    }

    public String localeReference() {
        return localeReference;
    }

    public Map<String, String> locale(String lang) {
        Map<String, String> t = locales.get(lang);
        return t == null ? Collections.<String, String>emptyMap() : t;
    }

    public List<String> localeNames() {
        return new ArrayList<String>(locales.keySet());
    }

    public List<String> loadErrors() {
        return Collections.unmodifiableList(loadErrors);
    }

    public int loadedBytes() {
        return loadedBytes;
    }

    /* ------------------------------------------------------------------ */
    /* Statistiques (utilisées par les gardes CI et les tests)              */
    /* ------------------------------------------------------------------ */

    public int dialogueLineCount() {
        int n = 0;
        for (SequenceDialogues sd : dialogues.values()) {
            for (DialogueScene s : sd.scenes) {
                n += s.lines.size();
            }
        }
        return n;
    }

    public int barkCount() {
        int n = 0;
        for (SequenceDialogues sd : dialogues.values()) {
            for (DialogueScene s : sd.scenes) {
                for (DialogueLine l : s.lines) {
                    if (l.bark) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** 02.03 / 03.08 : garde no-webview appliquee au contenu lui-meme. */
    public boolean contentContainsBannedKeyword() {
        String[] banned = {"webview", "webkit", "cordova", "capacitor", "iframe"};
        for (SequenceDialogues sd : dialogues.values()) {
            for (DialogueScene s : sd.scenes) {
                for (DialogueLine l : s.lines) {
                    String low = (l.fr + " " + l.direction).toLowerCase();
                    for (String b : banned) {
                        if (low.contains(b)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** 10.15 R5 : le mot « amour » exactement deux fois dans le chapitre. */
    public int countWordAmour() {
        int n = 0;
        for (SequenceDialogues sd : dialogues.values()) {
            for (DialogueScene s : sd.scenes) {
                for (DialogueLine l : s.lines) {
                    String low = l.fr.toLowerCase();
                    int idx = 0;
                    while ((idx = low.indexOf("amour", idx)) >= 0) {
                        n++;
                        idx += 5;
                    }
                }
            }
        }
        for (LetterBlock b : letterBlocks.values()) {
            String low = b.text.toLowerCase();
            int idx = 0;
            while ((idx = low.indexOf("amour", idx)) >= 0) {
                n++;
                idx += 5;
            }
        }
        return n;
    }

    /** Charge un InputStream (utilitaire partagé Android / tests). */
    public static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
