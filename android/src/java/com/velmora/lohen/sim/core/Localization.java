/*
 * LOHEN — sim/core/Localization.java
 *
 * Autoload n°6 (04.03) : chargement, changement a chaud, fonte fallback.
 * 00.10 : FR est la langue de reference du recit ; EN/ES/PT-BR/JA portent
 * l'interface. Le recit reste en FR (voir docs/decisions/ADR-003).
 */
package com.velmora.lohen.sim.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Localization {

    public interface Listener {
        void onLanguageChanged(String oldLang, String newLang);
    }

    private final ContentDb db;
    private String language = "fr";
    private final List<Listener> listeners = new ArrayList<Listener>(2);
    private final Set<String> missingKeys = new LinkedHashSet<String>();

    public Localization(ContentDb db) {
        this.db = db;
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public String language() {
        return language;
    }

    /** Changement a chaud (04.03) : aucune scene n'est rechargee. */
    public boolean setLanguage(String lang) {
        if (lang == null || lang.equals(language)) {
            return false;
        }
        if (!db.localeNames().contains(lang)) {
            return false;
        }
        String old = language;
        language = lang;
        for (Listener l : listeners) {
            l.onLanguageChanged(old, lang);
        }
        return true;
    }

    public List<String> available() {
        return db.localeNames();
    }

    /**
     * Traduction avec repli explicite : si la cle est absente de la table,
     * c'est le texte de repli qui s'affiche, jamais la cle brute. L'interface
     * ne doit jamais laisser voir une cle (14.10).
     */
    public String text(String key, String fallback) {
        Map<String, String> table = db.locale(language);
        String value = table.get(key);
        if (value == null && !"fr".equals(language)) {
            value = db.locale("fr").get(key);
        }
        return value == null ? (fallback == null ? key : fallback) : value;
    }

    public String t(String key) {
        return t(key, (Object[]) null);
    }

    public String t(String key, Object... args) {
        Map<String, String> table = db.locale(language);
        String value = table.get(key);
        if (value == null && !"fr".equals(language)) {
            value = db.locale("fr").get(key);
        }
        if (value == null) {
            missingKeys.add(key);
            return key;
        }
        if (args == null || args.length == 0) {
            return value;
        }
        /* substitution %d / %s simple, sans String.format pour rester
           deterministe sur toutes les locales (pas de regroupement de chiffres). */
        StringBuilder sb = new StringBuilder(value.length() + 16);
        int ai = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%' && i + 1 < value.length() && ai < args.length) {
                char n = value.charAt(i + 1);
                if (n == 'd' || n == 's' || n == 'f') {
                    sb.append(String.valueOf(args[ai++]));
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Le recit (dialogues, Echos, lettre) n'existe qu'en FR : c'est la langue
     * de reference (00.10). On renvoie le texte canonique tel quel.
     */
    /**
     * Traduction d'une ligne de dialogue : le texte canonique est toujours le francais ;
     * les autres langues passent par la table des cles quand la ligne porte
     * un identifiant connu. 12.02 : aucune ligne n'est generee par machine.
     */
    public String translateLine(String lineId, String canonicalFrench) {
        if (language == null || "fr".equals(language)) {
            return canonicalFrench;
        }
        if (lineId != null && !lineId.isEmpty()) {
            String keyed = t("line." + lineId);
            if (keyed != null && !keyed.isEmpty() && !keyed.equals("line." + lineId)) {
                return keyed;
            }
        }
        return narrative(canonicalFrench);
    }

    public String narrative(String canonicalFrench) {
        return canonicalFrench;
    }

    /**
     * Fonte fallback (14.06) : JA/CJK utilisent la fonte systeme, les langues
     * latines utilisent Source Serif 4 / Cormorant / Caveat / Rouge Script.
     */
    public String fontForUi() {
        return "cjk".equals(scriptOf(language)) ? "system" : "SourceSerif4";
    }

    public String fontForTitles() {
        return "cjk".equals(scriptOf(language)) ? "system" : "CormorantGaramond";
    }

    public String fontForLohenHand() {
        return "cjk".equals(scriptOf(language)) ? "system" : "Caveat";
    }

    public String fontForEstebanHand() {
        return "cjk".equals(scriptOf(language)) ? "system" : "RougeScript";
    }

    private static String scriptOf(String lang) {
        return lang != null && lang.toLowerCase().startsWith("ja") ? "cjk" : "latin";
    }

    public Set<String> missingKeys() {
        return missingKeys;
    }

    public void clearMissing() {
        missingKeys.clear();
    }

    /** Sous-titres : 2 lignes max, 42 caracteres par ligne (12.03). */
    public static String[] wrapSubtitle(String text, int maxCharsPerLine, int maxLines) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        List<String> out = new ArrayList<String>(maxLines);
        StringBuilder cur = new StringBuilder();
        String[] words = text.split(" ");
        for (String w : words) {
            if (cur.length() == 0) {
                cur.append(w);
            } else if (cur.length() + 1 + w.length() <= maxCharsPerLine) {
                cur.append(' ').append(w);
            } else {
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(w);
                if (out.size() == maxLines - 1) {
                    /* derniere ligne autorisee : on y verse le reste */
                    break;
                }
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        /* si des mots restent, on les rattache a la derniere ligne */
        int consumed = 0;
        for (String l : out) {
            consumed += l.split(" ").length;
        }
        String[] all = text.split(" ");
        if (consumed < all.length && !out.isEmpty()) {
            StringBuilder last = new StringBuilder(out.get(out.size() - 1));
            for (int i = consumed; i < all.length; i++) {
                last.append(' ').append(all[i]);
            }
            out.set(out.size() - 1, last.toString());
        }
        while (out.size() > maxLines) {
            String tail = out.remove(out.size() - 1);
            out.set(out.size() - 1, out.get(out.size() - 1) + " " + tail);
        }
        return out.toArray(new String[0]);
    }
}
