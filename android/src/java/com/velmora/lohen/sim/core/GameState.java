/*
 * LOHEN — sim/core/GameState.java
 *
 * Autoload n°2 (04.03) : flags narratifs, progression, chapitre courant.
 * Serialise par SaveSystem (02.20). Aucune logique metier : c'est un etat.
 */
package com.velmora.lohen.sim.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class GameState {

    /* progression -------------------------------------------------------- */
    private String sequence = "S1";
    private String questNode = "S1_arrival";
    private float altitude = 0f;
    private float px = 0f, py = 1.0f, pz = 0f;
    private float yaw = 0f, pitch = 0f;
    private float playTime = 0f;
    private int slotOfLastSave = -1;
    private String lastAutosaveReason = "";

    /* reglages de partie (14.12) ----------------------------------------- */
    private int combatDifficulty = 1;      /* 0 calme · 1 soutenu · 2 brutal */
    private int traversalAssist = 0;       /* 0 normal · 1 genereux · 2 automatique */
    private boolean narrationOnly = false;
    private String optionsSignature = "";

    /* ressources --------------------------------------------------------- */
    private float breathMax = 100f;        /* 08.13 : 100 -> 130 */
    private int ritualCount = 0;           /* 11.03 : 31 rituels */
    private int deaths = 0;                /* aucun compteur affiche (14.11) */
    private String lastCheckpoint = "";    /* 09.22 : le respawn ramene ici */

    /* collections -------------------------------------------------------- */
    private final Map<String, String> flags = new LinkedHashMap<String, String>();
    private final Map<String, Integer> choices = new LinkedHashMap<String, Integer>();
    private final Map<String, Float> facts = new LinkedHashMap<String, Float>();
    private final Set<String> echosRead = new LinkedHashSet<String>();
    private final Set<String> letters = new LinkedHashSet<String>();
    private final Set<String> objects = new LinkedHashSet<String>();
    private final Set<String> checkpoints = new LinkedHashSet<String>();
    private final Set<String> shortcuts = new LinkedHashSet<String>();
    private final Set<String> secrets = new LinkedHashSet<String>();
    private final List<String> journalNotes = new ArrayList<String>();

    public String sequence() {
        return sequence;
    }

    public void setSequence(String s) {
        this.sequence = s == null ? "S1" : s;
    }

    public String questNode() {
        return questNode;
    }

    public void setQuestNode(String n) {
        this.questNode = n == null ? "" : n;
    }

    public float altitude() {
        return altitude;
    }

    public void setAltitude(float a) {
        this.altitude = a;
    }

    public float positionX() {
        return px;
    }

    public float positionY() {
        return py;
    }

    public float positionZ() {
        return pz;
    }

    public void setPosition(float x, float y, float z) {
        this.px = x;
        this.py = y;
        this.pz = z;
    }

    public void setPositionX(float v) {
        this.px = v;
    }

    public void setPositionY(float v) {
        this.py = v;
    }

    public void setPositionZ(float v) {
        this.pz = v;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public void setYaw(float v) {
        this.yaw = v;
    }

    public void setPitch(float v) {
        this.pitch = v;
    }

    public float playTimeSeconds() {
        return playTime;
    }

    public void setPlayTimeSeconds(float t) {
        this.playTime = t;
    }

    public void addPlayTime(float dt) {
        this.playTime += dt;
    }

    public int slotOfLastSave() {
        return slotOfLastSave;
    }

    public void setSlotOfLastSave(int s) {
        this.slotOfLastSave = s;
    }

    public String lastAutosaveReason() {
        return lastAutosaveReason;
    }

    public void setLastAutosaveReason(String r) {
        this.lastAutosaveReason = r == null ? "" : r;
    }

    public int combatDifficulty() {
        return combatDifficulty;
    }

    public void setCombatDifficulty(int d) {
        this.combatDifficulty = Math.max(0, Math.min(2, d));
    }

    public int traversalAssist() {
        return traversalAssist;
    }

    public void setTraversalAssist(int a) {
        this.traversalAssist = Math.max(0, Math.min(2, a));
    }

    public boolean narrationOnly() {
        return narrationOnly;
    }

    public void setNarrationOnly(boolean n) {
        this.narrationOnly = n;
    }

    public String optionsSignature() {
        return optionsSignature;
    }

    public void setOptionsSignature(String s) {
        this.optionsSignature = s == null ? "" : s;
    }

    public float breathMax() {
        return breathMax;
    }

    public void setBreathMax(float v) {
        this.breathMax = Math.max(100f, Math.min(130f, v));
    }

    /** 08.13 : trois augmentations narratives de +10, jamais achetees. */
    public boolean applyBreathUpgrade() {
        if (breathMax >= 130f) {
            return false;
        }
        breathMax = Math.min(130f, breathMax + 10f);
        return true;
    }

    public int ritualCount() {
        return ritualCount;
    }

    public void setRitualCount(int c) {
        this.ritualCount = c;
    }

    public int incrementRitual() {
        return ++ritualCount;
    }

    public int deaths() {
        return deaths;
    }

    public void setDeaths(int d) {
        this.deaths = d;
    }

    public int incrementDeaths() {
        return ++deaths;
    }

    /* flags -------------------------------------------------------------- */

    public void setFlag(String name, String value) {
        flags.put(name, value);
    }

    public void setFlag(String name) {
        flags.put(name, "1");
    }

    public boolean hasFlag(String name) {
        return flags.containsKey(name);
    }

    public String flagValue(String name) {
        return flags.get(name);
    }

    public boolean flagBool(String name) {
        String v = flags.get(name);
        return v != null && !"0".equals(v) && !"false".equalsIgnoreCase(v);
    }

    public Set<String> flagsSorted() {
        return new TreeSet<String>(flags.keySet());
    }

    public Map<String, String> flags() {
        return Collections.unmodifiableMap(flags);
    }

    /* choix (annexe C §7) ------------------------------------------------- */

    public void recordChoice(String choiceId, int optionIndex) {
        choices.put(choiceId, optionIndex);
    }

    public Integer choiceOf(String choiceId) {
        return choices.get(choiceId);
    }

    public Map<String, Integer> choicesMade() {
        return Collections.unmodifiableMap(choices);
    }

    public int choiceCount() {
        return choices.size();
    }

    /* faits numeriques (altitude atteinte, temps de zone, etc.) ----------- */

    public void setNumericFact(String key, float value) {
        facts.put(key, value);
    }

    public float numericFact(String key, float dflt) {
        Float v = facts.get(key);
        return v == null ? dflt : v;
    }

    public Map<String, Float> numericFacts() {
        return Collections.unmodifiableMap(facts);
    }

    /* collections -------------------------------------------------------- */

    public boolean markEchoRead(String echoId) {
        return echosRead.add(echoId);
    }

    public boolean hasReadEcho(String echoId) {
        return echosRead.contains(echoId);
    }

    public Set<String> echosRead() {
        return Collections.unmodifiableSet(echosRead);
    }

    public boolean markLetterDelivered(String letterId) {
        return letters.add(letterId);
    }

    public Set<String> lettersDelivered() {
        return Collections.unmodifiableSet(letters);
    }

    /** 10.03 : 11 objets tombes des Figures, sans explication. */
    public boolean addObjectFromFigure(String objectId) {
        return objects.add(objectId);
    }

    public Set<String> objectsFromFigures() {
        return Collections.unmodifiableSet(objects);
    }

    public boolean markCheckpoint(String cp) {
        if (checkpoints.add(cp)) {
            lastCheckpoint = cp;
            return true;
        }
        return false;
    }

    /** Dernier checkpoint touche — c'est la que le respawn ramene (09.22). */
    public String lastCheckpoint() {
        if (lastCheckpoint.length() == 0 && !checkpoints.isEmpty()) {
            for (String c : checkpoints) {
                lastCheckpoint = c;
            }
        }
        return lastCheckpoint;
    }

    /**
     * Recopie un etat charge depuis une sauvegarde dans l'etat vivant.
     * Les systemes (quete, Echo, journal) tiennent une reference sur CET
     * objet : on ne le remplace jamais, on le remplit.
     */
    public void copyFrom(GameState o) {
        if (o == null || o == this) {
            return;
        }
        sequence = o.sequence;
        questNode = o.questNode;
        altitude = o.altitude;
        px = o.px;
        py = o.py;
        pz = o.pz;
        yaw = o.yaw;
        pitch = o.pitch;
        playTime = o.playTime;
        slotOfLastSave = o.slotOfLastSave;
        lastAutosaveReason = o.lastAutosaveReason;
        combatDifficulty = o.combatDifficulty;
        traversalAssist = o.traversalAssist;
        narrationOnly = o.narrationOnly;
        optionsSignature = o.optionsSignature;
        breathMax = o.breathMax;
        ritualCount = o.ritualCount;
        deaths = o.deaths;
        flags.clear();
        flags.putAll(o.flags);
        choices.clear();
        choices.putAll(o.choices);
        facts.clear();
        facts.putAll(o.facts);
        echosRead.clear();
        echosRead.addAll(o.echosRead);
        letters.clear();
        letters.addAll(o.letters);
        objects.clear();
        objects.addAll(o.objects);
        checkpoints.clear();
        checkpoints.addAll(o.checkpoints);
        shortcuts.clear();
        shortcuts.addAll(o.shortcuts);
        secrets.clear();
        secrets.addAll(o.secrets);
        journalNotes.clear();
        journalNotes.addAll(o.journalNotes);
        lastCheckpoint = o.lastCheckpoint;
    }

    public Set<String> checkpoints() {
        return Collections.unmodifiableSet(checkpoints);
    }

    public boolean markShortcut(String id) {
        return shortcuts.add(id);
    }

    public Set<String> shortcutsOpened() {
        return Collections.unmodifiableSet(shortcuts);
    }

    public boolean markSecret(String id) {
        return secrets.add(id);
    }

    public Set<String> secretsFound() {
        return Collections.unmodifiableSet(secrets);
    }

    public void addJournalNote(String note) {
        if (note != null && !journalNotes.contains(note)) {
            journalNotes.add(note);
        }
    }

    public List<String> journalNotes() {
        return Collections.unmodifiableList(journalNotes);
    }

    /** Sept emplacements, un seul rempli au Chapitre 1 (10.14). */
    public int lettersSlotsFilled() {
        return letters.contains("LETTRE_ESTEBAN_0114") ? 1 : 0;
    }

    /** Resume utilise par le menu et les tests de regression narrative. */
    public String summary() {
        StringBuilder sb = new StringBuilder(96);
        sb.append(sequence).append('/').append(questNode)
                .append(" alt=").append(Math.round(altitude))
                .append(" echos=").append(echosRead.size())
                .append(" cp=").append(checkpoints.size())
                .append(" choix=").append(choices.size());
        return sb.toString();
    }
}
