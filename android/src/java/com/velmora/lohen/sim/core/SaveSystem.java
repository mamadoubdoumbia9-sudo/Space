/*
 * LOHEN — sim/core/SaveSystem.java
 *
 * Autoload n°3 (04.03). Spec 02.20 :
 *   user:// avec 3 slots + 1 autosave rotatif x3
 *   format binaire custom .vlm = ConfigFile chiffre XOR + CRC32
 *   autosave a chaque checkpoint et a chaque passage en arriere-plan
 * Regle 1.05 de l'annexe C : aucune sauvegarde de scene, aucun chapitrage,
 * une seule sauvegarde automatique ecrasee (le rotatif garde 3 generations
 * pour se proteger de la corruption, pas pour rembobiner).
 *
 * 100 % Java pur : testable hors device (BLOC 18.06).
 */
package com.velmora.lohen.sim.core;

import com.velmora.lohen.sim.math.Maths;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SaveSystem {

    public static final int SLOTS = 3;
    public static final int AUTOSAVE_ROTATION = 3;
    public static final String MAGIC = "VLM1";
    public static final int VERSION = 3;

    /** Cle XOR — pas une protection, une detection de corruption (02.20). */
    private static final byte[] XOR_KEY = {
            (byte) 0x56, (byte) 0x45, (byte) 0x4C, (byte) 0x4D,
            (byte) 0x4F, (byte) 0x52, (byte) 0x41, (byte) 0x30,
            (byte) 0x31, (byte) 0x31, (byte) 0x34, (byte) 0x4C,
    };

    /** Couche d'acces aux fichiers : injectee par Android ou par les tests. */
    public interface Storage {
        byte[] read(String name);

        boolean write(String name, byte[] data);

        boolean exists(String name);

        boolean delete(String name);

        List<String> list();
    }

    /** Stockage memoire — utilise par les 180 tests unitaires. */
    public static final class MemoryStorage implements Storage {
        private final Map<String, byte[]> files = new LinkedHashMap<String, byte[]>();

        @Override
        public byte[] read(String name) {
            return files.get(name);
        }

        @Override
        public boolean write(String name, byte[] data) {
            files.put(name, data);
            return true;
        }

        @Override
        public boolean exists(String name) {
            return files.containsKey(name);
        }

        @Override
        public boolean delete(String name) {
            return files.remove(name) != null;
        }

        @Override
        public List<String> list() {
            return new ArrayList<String>(files.keySet());
        }

        public int count() {
            return files.size();
        }
    }

    private final Storage storage;
    private final EventBus bus;
    private int autosaveIndex = 0;
    private long lastWriteMs = 0L;
    private final List<String> corruptionLog = new ArrayList<String>();

    public SaveSystem(Storage storage, EventBus bus) {
        this.storage = storage;
        this.bus = bus;
    }

    public static String slotName(int slot) {
        return "slot" + (slot + 1) + ".vlm";
    }

    public static String autosaveName(int generation) {
        return "autosave" + (generation + 1) + ".vlm";
    }

    /* ------------------------------------------------------------------ */
    /* Serialisation                                                       */
    /* ------------------------------------------------------------------ */

    /** Ecrit une sauvegarde dans un slot (0..2). */
    public boolean saveToSlot(int slot, GameState state) {
        if (slot < 0 || slot >= SLOTS || state == null) {
            return false;
        }
        byte[] raw = encode(state);
        boolean ok = storage.write(slotName(slot), raw);
        if (ok) {
            lastWriteMs = System.currentTimeMillis();
            state.setSlotOfLastSave(slot);
            bus.emit(EventBus.SAVE_WRITTEN, slotName(slot));
        }
        return ok;
    }

    /** Autosave rotatif : checkpoint (09.22) et arriere-plan (02.10/02.20). */
    public boolean autosave(GameState state, String reason) {
        if (state == null) {
            return false;
        }
        String name = autosaveName(autosaveIndex % AUTOSAVE_ROTATION);
        autosaveIndex = (autosaveIndex + 1) % AUTOSAVE_ROTATION;
        state.setLastAutosaveReason(reason);
        boolean ok = storage.write(name, encode(state));
        if (ok) {
            lastWriteMs = System.currentTimeMillis();
            bus.emit(EventBus.SAVE_WRITTEN, name);
        }
        return ok;
    }

    public GameState loadSlot(int slot) {
        if (slot < 0 || slot >= SLOTS) {
            return null;
        }
        return decode(storage.read(slotName(slot)));
    }

    public GameState loadLatestAutosave() {
        for (int i = AUTOSAVE_ROTATION - 1; i >= 0; i--) {
            GameState s = decode(storage.read(autosaveName(i)));
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    public GameState loadMostRecent() {
        GameState best = null;
        for (int i = 0; i < SLOTS; i++) {
            GameState s = decode(storage.read(slotName(i)));
            if (s != null && (best == null || s.playTimeSeconds() > best.playTimeSeconds())) {
                best = s;
            }
        }
        GameState auto = loadLatestAutosave();
        if (auto != null && (best == null || auto.playTimeSeconds() > best.playTimeSeconds())) {
            best = auto;
        }
        return best;
    }

    public boolean deleteSlot(int slot) {
        return storage.delete(slotName(slot));
    }

    public boolean hasSlot(int slot) {
        return storage.exists(slotName(slot));
    }

    /** Resume lisible pour le menu (14.12 DONNEES). */
    public Map<String, Object> describeSlot(int slot) {
        GameState s = loadSlot(slot);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("exists", s != null);
        if (s != null) {
            out.put("sequence", s.sequence());
            out.put("altitude", s.altitude());
            out.put("playtime", s.playTimeSeconds());
            out.put("breath_max", s.breathMax());
            out.put("echos_read", s.echosRead().size());
            out.put("letters_delivered", s.lettersDelivered().size());
        }
        return out;
    }

    /** Export/import en fichier (14.12 DONNEES). */
    public byte[] exportSlot(int slot) {
        return storage.read(slotName(slot));
    }

    public boolean importSlot(int slot, byte[] data) {
        if (decode(data) == null) {
            return false;
        }
        return storage.write(slotName(slot), data);
    }

    public List<String> corruptionLog() {
        return corruptionLog;
    }

    public long lastWriteMs() {
        return lastWriteMs;
    }

    /* ------------------------------------------------------------------ */
    /* Format .vlm                                                         */
    /* ------------------------------------------------------------------ */

    private byte[] encode(GameState s) {
        StringBuilder body = new StringBuilder(1024);
        body.append("seq=").append(s.sequence()).append('\n');
        body.append("node=").append(s.questNode()).append('\n');
        body.append("alt=").append(fmt(s.altitude())).append('\n');
        body.append("px=").append(fmt(s.positionX())).append('\n');
        body.append("py=").append(fmt(s.positionY())).append('\n');
        body.append("pz=").append(fmt(s.positionZ())).append('\n');
        body.append("yaw=").append(fmt(s.yaw())).append('\n');
        body.append("pitch=").append(fmt(s.pitch())).append('\n');
        body.append("time=").append(fmt(s.playTimeSeconds())).append('\n');
        body.append("breath_max=").append(fmt(s.breathMax())).append('\n');
        body.append("combat_difficulty=").append(s.combatDifficulty()).append('\n');
        body.append("traversal_assist=").append(s.traversalAssist()).append('\n');
        body.append("narration_only=").append(s.narrationOnly() ? 1 : 0).append('\n');
        for (String flag : s.flagsSorted()) {
            body.append("flag=").append(flag).append('=').append(s.flagValue(flag)).append('\n');
        }
        for (String e : s.echosRead()) {
            body.append("echo=").append(e).append('\n');
        }
        for (String l : s.lettersDelivered()) {
            body.append("letter=").append(l).append('\n');
        }
        for (String o : s.objectsFromFigures()) {
            body.append("object=").append(o).append('\n');
        }
        for (String c : s.checkpoints()) {
            body.append("cp=").append(c).append('\n');
        }
        for (String sh : s.shortcutsOpened()) {
            body.append("shortcut=").append(sh).append('\n');
        }
        for (String se : s.secretsFound()) {
            body.append("secret=").append(se).append('\n');
        }
        for (Map.Entry<String, Integer> e : s.choicesMade().entrySet()) {
            body.append("choice=").append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        for (Map.Entry<String, Float> e : s.numericFacts().entrySet()) {
            body.append("fact=").append(e.getKey()).append('=').append(fmt(e.getValue())).append('\n');
        }
        for (String note : s.journalNotes()) {
            body.append("note=").append(note.replace("\n", "\\n")).append('\n');
        }
        body.append("rituals=").append(s.ritualCount()).append('\n');
        body.append("deaths=").append(s.deaths()).append('\n');
        body.append("options=").append(s.optionsSignature()).append('\n');

        byte[] payload;
        try {
            payload = body.toString().getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            payload = body.toString().getBytes();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 32);
        writeStr(out, MAGIC);
        out.write(VERSION);
        int crc = Maths.crc32(payload, payload.length);
        out.write((crc >>> 24) & 0xFF);
        out.write((crc >>> 16) & 0xFF);
        out.write((crc >>> 8) & 0xFF);
        out.write(crc & 0xFF);
        writeInt(out, payload.length);
        for (int i = 0; i < payload.length; i++) {
            out.write((payload[i] ^ XOR_KEY[i % XOR_KEY.length]) & 0xFF);
        }
        return out.toByteArray();
    }

    private GameState decode(byte[] raw) {
        if (raw == null || raw.length < 16) {
            return null;
        }
        try {
            String magic = new String(raw, 0, 4, "UTF-8");
            if (!MAGIC.equals(magic)) {
                corruptionLog.add("magic invalide");
                return null;
            }
            if (raw[4] != VERSION) {
                corruptionLog.add("version " + raw[4] + " non supportee");
                return null;
            }
            int crc = ((raw[5] & 0xFF) << 24) | ((raw[6] & 0xFF) << 16)
                    | ((raw[7] & 0xFF) << 8) | (raw[8] & 0xFF);
            int len = ((raw[9] & 0xFF) << 24) | ((raw[10] & 0xFF) << 16)
                    | ((raw[11] & 0xFF) << 8) | (raw[12] & 0xFF);
            if (len <= 0 || 13 + len > raw.length) {
                corruptionLog.add("longueur incoherente");
                return null;
            }
            byte[] payload = new byte[len];
            for (int i = 0; i < len; i++) {
                payload[i] = (byte) ((raw[13 + i] & 0xFF) ^ XOR_KEY[i % XOR_KEY.length]);
            }
            if (Maths.crc32(payload, len) != crc) {
                corruptionLog.add("CRC32 invalide");
                return null;
            }
            return parseState(new String(payload, "UTF-8"));
        } catch (Exception e) {
            corruptionLog.add("exception : " + e.getClass().getSimpleName());
            return null;
        }
    }

    private GameState parseState(String text) {
        GameState s = new GameState();
        String[] lines = text.split("\n");
        for (String line : lines) {
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq);
            String val = line.substring(eq + 1);
            if ("seq".equals(key)) {
                s.setSequence(val);
            } else if ("node".equals(key)) {
                s.setQuestNode(val);
            } else if ("alt".equals(key)) {
                s.setAltitude(parseFloat(val));
            } else if ("px".equals(key)) {
                s.setPositionX(parseFloat(val));
            } else if ("py".equals(key)) {
                s.setPositionY(parseFloat(val));
            } else if ("pz".equals(key)) {
                s.setPositionZ(parseFloat(val));
            } else if ("yaw".equals(key)) {
                s.setYaw(parseFloat(val));
            } else if ("pitch".equals(key)) {
                s.setPitch(parseFloat(val));
            } else if ("time".equals(key)) {
                s.setPlayTimeSeconds(parseFloat(val));
            } else if ("breath_max".equals(key)) {
                s.setBreathMax(parseFloat(val));
            } else if ("combat_difficulty".equals(key)) {
                s.setCombatDifficulty(parseInt(val));
            } else if ("traversal_assist".equals(key)) {
                s.setTraversalAssist(parseInt(val));
            } else if ("narration_only".equals(key)) {
                s.setNarrationOnly(parseInt(val) != 0);
            } else if ("flag".equals(key)) {
                int e2 = val.indexOf('=');
                if (e2 > 0) {
                    s.setFlag(val.substring(0, e2), val.substring(e2 + 1));
                }
            } else if ("echo".equals(key)) {
                s.markEchoRead(val);
            } else if ("letter".equals(key)) {
                s.markLetterDelivered(val);
            } else if ("object".equals(key)) {
                s.addObjectFromFigure(val);
            } else if ("cp".equals(key)) {
                s.markCheckpoint(val);
            } else if ("shortcut".equals(key)) {
                s.markShortcut(val);
            } else if ("secret".equals(key)) {
                s.markSecret(val);
            } else if ("choice".equals(key)) {
                int e3 = val.indexOf('=');
                if (e3 > 0) {
                    s.recordChoice(val.substring(0, e3), parseInt(val.substring(e3 + 1)));
                }
            } else if ("fact".equals(key)) {
                int e4 = val.indexOf('=');
                if (e4 > 0) {
                    s.setNumericFact(val.substring(0, e4), parseFloat(val.substring(e4 + 1)));
                }
            } else if ("note".equals(key)) {
                s.addJournalNote(val.replace("\\n", "\n"));
            } else if ("rituals".equals(key)) {
                s.setRitualCount(parseInt(val));
            } else if ("deaths".equals(key)) {
                s.setDeaths(parseInt(val));
            } else if ("options".equals(key)) {
                s.setOptionsSignature(val);
            }
        }
        return s;
    }

    private static float parseFloat(String v) {
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private static int parseInt(String v) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String fmt(float v) {
        return String.valueOf(Math.round(v * 1000f) / 1000f);
    }

    private static void writeStr(ByteArrayOutputStream out, String s) {
        for (int i = 0; i < s.length(); i++) {
            out.write(s.charAt(i));
        }
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }
}
