/*
 * LOHEN — sim/narrative/SubtitleModel.java
 *
 * 12.03 : sous-titres en bas, 2 LIGNES MAX, 42 CARACTERES PAR LIGNE,
 * fond noir a 55 %, nom du locuteur en petites capitales ambre DESATURE
 * (#B8A08A — jamais l'ambre pur, reserve a Esteban).
 * Apparition mot par mot a la vitesse de la lecture (pas de typewriter fake :
 * la progression suit le debit reel de la ligne, pas un timer arbitraire).
 *
 * 12.04 : une ligne ne depasse jamais 4,5 s ; les silences (pause_after)
 * sont ECRITS dans le JSON. Le silence est du dialogue.
 */
package com.velmora.lohen.sim.narrative;

import com.velmora.lohen.sim.math.Maths;

import java.util.ArrayList;
import java.util.List;

public final class SubtitleModel {

    public static final int CHARS_PER_LINE = 42;
    public static final int MAX_LINES = 2;
    public static final float MAX_LINE_SECONDS = 4.5f;
    public static final int SPEAKER_COLOR = 0xFFB8A08A;   /* ambre desature */
    public static final float BACKDROP_ALPHA = 0.55f;

    /** Un sous-titre affiche : jusqu'a 2 lignes de 42 caracteres. */
    public static final class Cue {
        public String speaker = "";
        public String speakerLabel = "";
        public final String[] lines = new String[MAX_LINES];
        public int lineCount;
        public float duration = 2f;
        public float pauseAfter = 0.45f;
        public int[] wordStart = new int[0];   /* index du premier caractere de chaque mot */
        public int[] wordLine = new int[0];    /* ligne du mot */
        public int wordCount;
        public int totalChars;
        public String id = "";
    }

    private Cue current;
    private float time;
    private float pauseTime;
    private boolean inPause;

    /**
     * Decoupe un texte en cues de 2 lignes max (42 caracteres par ligne).
     * Une replique trop longue devient DEUX cues consecutives : on ne
     * tronque jamais le texte ecrit (12.02 : le texte est canonique).
     */
    public static List<Cue> buildCues(String speaker, String speakerLabel, String text,
                                      float duration, float pauseAfter, String id) {
        List<Cue> out = new ArrayList<Cue>(2);
        List<String> wrapped = wrap(text, CHARS_PER_LINE);
        int perCue = MAX_LINES;
        for (int i = 0; i < wrapped.size(); i += perCue) {
            Cue c = new Cue();
            c.speaker = speaker;
            c.speakerLabel = speakerLabel;
            c.id = id;
            c.lineCount = 0;
            int chars = 0;
            for (int k = i; k < Math.min(i + perCue, wrapped.size()); k++) {
                c.lines[c.lineCount++] = wrapped.get(k);
                chars += wrapped.get(k).length();
            }
            /* la duree est repartie au prorata des caracteres (debit constant) */
            float share = duration / (float) wrapped.size() * c.lineCount;
            c.duration = Math.min(MAX_LINE_SECONDS, Math.max(0.6f, share));
            boolean last = i + perCue >= wrapped.size();
            c.pauseAfter = last ? pauseAfter : 0.05f;
            indexWords(c);
            out.add(c);
        }
        if (out.isEmpty()) {
            Cue c = new Cue();
            c.speaker = speaker;
            c.speakerLabel = speakerLabel;
            c.id = id;
            c.lines[0] = "";
            c.lineCount = 1;
            c.duration = Math.min(MAX_LINE_SECONDS, Math.max(0.4f, duration));
            c.pauseAfter = pauseAfter;
            indexWords(c);
            out.add(c);
        }
        return out;
    }

    /** Coupe aux espaces, jamais au milieu d'un mot ; césure toleree a 42. */
    public static List<String> wrap(String text, int maxChars) {
        List<String> lines = new ArrayList<String>(3);
        if (text == null || text.length() == 0) {
            lines.add("");
            return lines;
        }
        String[] words = text.split("\\s+");
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            while (w.length() > maxChars) {
                /* mot plus long que la ligne : on le coupe */
                if (cur.length() > 0) {
                    lines.add(cur.toString());
                    cur.setLength(0);
                }
                lines.add(w.substring(0, maxChars));
                w = w.substring(maxChars);
            }
            if (cur.length() == 0) {
                cur.append(w);
            } else if (cur.length() + 1 + w.length() <= maxChars) {
                cur.append(' ').append(w);
            } else {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append(w);
            }
        }
        if (cur.length() > 0) {
            lines.add(cur.toString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    /**
     * Indexe les mots pour l'apparition mot par mot : chaque mot porte
     * l'instant (0..1) de son apparition, proportionnel a sa position
     * en caracteres dans la ligne — pas un typewriter a vitesse fixe.
     */
    private static void indexWords(Cue c) {
        int totalChars = 0;
        for (int i = 0; i < c.lineCount; i++) {
            totalChars += c.lines[i].length();
        }
        List<int[]> words = new ArrayList<int[]>(16);
        int charPos = 0;
        for (int i = 0; i < c.lineCount; i++) {
            String line = c.lines[i];
            int j = 0;
            while (j < line.length()) {
                while (j < line.length() && line.charAt(j) == ' ') {
                    j++;
                    charPos++;
                }
                if (j >= line.length()) {
                    break;
                }
                int start = charPos;
                while (j < line.length() && line.charAt(j) != ' ') {
                    j++;
                    charPos++;
                }
                words.add(new int[]{start, i});
            }
        }
        c.wordCount = words.size();
        c.wordStart = new int[c.wordCount];
        c.wordLine = new int[c.wordCount];
        for (int i = 0; i < c.wordCount; i++) {
            c.wordStart[i] = words.get(i)[0];
            c.wordLine[i] = words.get(i)[1];
        }
        c.totalChars = totalChars;
    }

    public void show(Cue cue) {
        current = cue;
        time = 0f;
        inPause = false;
        pauseTime = 0f;
    }

    public void clear() {
        current = null;
        time = 0f;
        inPause = false;
    }

    /** Avance ; renvoie vrai si le cue est termine (texte + silence). */
    public boolean update(float dt) {
        if (current == null) {
            return true;
        }
        if (!inPause) {
            time += dt;
            if (time >= current.duration) {
                inPause = true;
                pauseTime = 0f;
            }
            return false;
        }
        pauseTime += dt;
        return pauseTime >= current.pauseAfter;
    }

    public Cue current() {
        return current;
    }

    public float time() {
        return time;
    }

    public boolean inPause() {
        return inPause;
    }

    /** Nombre de mots deja apparus a l'instant courant. */
    public int visibleWords() {
        if (current == null) {
            return 0;
        }
        float t = inPause ? 1f : Maths.clamp01(time / Math.max(0.001f, current.duration));
        int total = Math.max(1, current.totalChars);
        int shown = (int) (t * total);
        int n = 0;
        for (int i = 0; i < current.wordCount; i++) {
            if (current.wordStart[i] <= shown) {
                n++;
            } else {
                break;
            }
        }
        return n;
    }

    /** Texte partiellement revele pour la ligne donnee (affichage mot a mot). */
    public String visibleText(int lineIndex) {
        if (current == null || lineIndex >= current.lineCount) {
            return "";
        }
        String line = current.lines[lineIndex];
        int off = lineCharOffset(lineIndex);
        int shown = visibleChars() - off;
        if (shown <= 0) {
            return "";
        }
        if (shown >= line.length()) {
            return line;
        }
        /* on coupe a la fin du dernier mot entierement revele */
        int cut = 0;
        for (int i = 0; i < current.wordCount; i++) {
            if (current.wordLine[i] != lineIndex) {
                continue;
            }
            int start = current.wordStart[i] - off;
            int end = start;
            while (end < line.length() && line.charAt(end) != ' ') {
                end++;
            }
            if (end <= shown) {
                cut = end;
            } else {
                break;
            }
        }
        return line.substring(0, cut);
    }

    private int lineCharOffset(int lineIndex) {
        int off = 0;
        for (int i = 0; i < lineIndex && i < current.lineCount; i++) {
            off += current.lines[i].length();
        }
        return off;
    }

    private int visibleChars() {
        float t = inPause ? 1f : Maths.clamp01(time / Math.max(0.001f, current.duration));
        return (int) (t * Math.max(1, current.totalChars));
    }
}
