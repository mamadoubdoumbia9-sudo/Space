/*
 * LOHEN — SaveStorageAndroid.java
 *
 * Trois slots + trois autosaves rotatifs (09.22). Les fichiers .vlm sont
 * ecrits dans le stockage prive de l'application : jamais sur le stockage
 * externe, jamais dans le nuage. Le format est celui de SaveSystem :
 * magique VLM1, version 3, CRC-32, corps XOR — une sauvegarde corrompue est
 * refusee, pas chargée a moitie.
 *
 * 02.20 : une sauvegarde est ecrite au passage en arriere-plan. L'ecriture est
 * atomique (fichier temporaire puis renommage) pour qu'une coupure batterie
 * pendant l'autosave ne detruise pas le slot.
 */
package com.velmora.lohen;

import android.content.Context;

import com.velmora.lohen.sim.core.SaveSystem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class SaveStorageAndroid implements SaveSystem.Storage {

    private final File dir;
    private final List<String> errors = new ArrayList<String>(4);

    public SaveStorageAndroid(Context context) {
        File base = context.getFilesDir();
        dir = new File(base, "saves");
        if (!dir.exists() && !dir.mkdirs()) {
            errors.add("impossible de creer " + dir.getAbsolutePath());
        }
    }

    public File directory() {
        return dir;
    }

    @Override
    public byte[] read(String name) {
        File f = new File(dir, safe(name));
        if (!f.exists() || f.length() <= 0 || f.length() > 4L * 1024 * 1024) {
            return null;
        }
        byte[] data = new byte[(int) f.length()];
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n <= 0) {
                    break;
                }
                off += n;
            }
            if (off != data.length) {
                errors.add(name + " : lecture incomplete");
                return null;
            }
            return data;
        } catch (IOException e) {
            errors.add(name + " : " + e.getMessage());
            return null;
        } finally {
            close(in);
        }
    }

    @Override
    public boolean write(String name, byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }
        File target = new File(dir, safe(name));
        File tmp = new File(dir, safe(name) + ".tmp");
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tmp);
            out.write(data);
            out.flush();
            try {
                out.getFD().sync();
            } catch (IOException ignored) {
                /* certains systemes de fichiers refusent fsync */
            }
            close(out);
            out = null;
            if (target.exists() && !target.delete()) {
                errors.add(name + " : ancien fichier non supprime");
            }
            if (!tmp.renameTo(target)) {
                errors.add(name + " : renommage impossible");
                return false;
            }
            return true;
        } catch (IOException e) {
            errors.add(name + " : " + e.getMessage());
            return false;
        } finally {
            close(out);
            if (tmp.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
    }

    @Override
    public boolean exists(String name) {
        File f = new File(dir, safe(name));
        return f.exists() && f.length() > 0;
    }

    @Override
    public boolean delete(String name) {
        File f = new File(dir, safe(name));
        return !f.exists() || f.delete();
    }

    @Override
    public List<String> list() {
        List<String> out = new ArrayList<String>(8);
        File[] files = dir.listFiles();
        if (files == null) {
            return out;
        }
        for (File f : files) {
            String n = f.getName();
            if (n.endsWith(".vlm") && f.length() > 0) {
                out.add(n.substring(0, n.length() - 4));
            }
        }
        return out;
    }

    public List<String> errors() {
        return errors;
    }

    /** Aucune echappade hors du repertoire : les noms sont sanitises. */
    private static String safe(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.append(".vlm").toString();
    }

    private static void close(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
                /* rien a faire */
            }
        }
    }
}
