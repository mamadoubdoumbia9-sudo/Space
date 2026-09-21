/*
 * LOHEN — sim/core/MiniJson.java
 *
 * Analyseur JSON minimal, sans dependance (le runtime desktop de test n'a pas
 * org.json). Tolere les fichiers gzippes decompresses en amont.
 * Produit : Map<String,Object>, List<Object>, String, Double, Boolean, null.
 */
package com.velmora.lohen.sim.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MiniJson {

    private final String src;
    private int pos;

    private MiniJson(String src) {
        this.src = src;
    }

    public static Object parse(String text) {
        MiniJson p = new MiniJson(text);
        p.skipWs();
        Object v = p.readValue();
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object o = parse(text);
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<String, Object>();
    }

    /* -- acces pratiques ------------------------------------------------- */

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }

    public static Map<String, Object> child(Map<String, Object> m, String key) {
        Object o = m == null ? null : m.get(key);
        return obj(o);
    }

    public static List<Object> childList(Map<String, Object> m, String key) {
        Object o = m == null ? null : m.get(key);
        return list(o);
    }

    public static String str(Map<String, Object> m, String key, String dflt) {
        Object o = m == null ? null : m.get(key);
        if (o == null) {
            return dflt;
        }
        return o instanceof String ? (String) o : String.valueOf(o);
    }

    public static float num(Map<String, Object> m, String key, float dflt) {
        Object o = m == null ? null : m.get(key);
        if (o instanceof Number) {
            return ((Number) o).floatValue();
        }
        if (o instanceof String) {
            try {
                return Float.parseFloat(((String) o).replace(",", "."));
            } catch (NumberFormatException e) {
                return dflt;
            }
        }
        return dflt;
    }

    public static int intNum(Map<String, Object> m, String key, int dflt) {
        return (int) Math.round(num(m, key, dflt));
    }

    public static boolean bool(Map<String, Object> m, String key, boolean dflt) {
        Object o = m == null ? null : m.get(key);
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue() != 0;
        }
        if (o instanceof String) {
            return "true".equalsIgnoreCase((String) o) || "1".equals(o);
        }
        return dflt;
    }

    /* -- analyse --------------------------------------------------------- */

    private void skipWs() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                pos++;
            } else {
                break;
            }
        }
    }

    private Object readValue() {
        skipWs();
        if (pos >= src.length()) {
            return null;
        }
        char c = src.charAt(pos);
        switch (c) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            case 't':
                pos += 4;
                return Boolean.TRUE;
            case 'f':
                pos += 5;
                return Boolean.FALSE;
            case 'n':
                pos += 4;
                return null;
            default:
                return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        pos++; /* { */
        skipWs();
        if (pos < src.length() && src.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (pos < src.length()) {
            skipWs();
            String key = readString();
            skipWs();
            if (pos < src.length() && src.charAt(pos) == ':') {
                pos++;
            }
            Object value = readValue();
            map.put(key, value);
            skipWs();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
                continue;
            }
            if (pos < src.length() && src.charAt(pos) == '}') {
                pos++;
            }
            break;
        }
        return map;
    }

    private List<Object> readArray() {
        List<Object> arr = new ArrayList<Object>();
        pos++; /* [ */
        skipWs();
        if (pos < src.length() && src.charAt(pos) == ']') {
            pos++;
            return arr;
        }
        while (pos < src.length()) {
            arr.add(readValue());
            skipWs();
            if (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
                continue;
            }
            if (pos < src.length() && src.charAt(pos) == ']') {
                pos++;
            }
            break;
        }
        return arr;
    }

    private String readString() {
        StringBuilder sb = new StringBuilder();
        if (pos < src.length() && src.charAt(pos) == '"') {
            pos++;
        }
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') {
                break;
            }
            if (c == '\\' && pos < src.length()) {
                char e = src.charAt(pos++);
                switch (e) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'u':
                        if (pos + 4 <= src.length()) {
                            sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        break;
                    default:
                        sb.append(e);
                }
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private Double readNumber() {
        int start = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                pos++;
            } else {
                break;
            }
        }
        try {
            return Double.parseDouble(src.substring(start, pos));
        } catch (NumberFormatException e) {
            return 0d;
        }
    }
}
