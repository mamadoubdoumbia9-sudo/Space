/*
 * LOHEN — AssetSourceAndroid.java
 *
 * Le contenu (2 832 lignes de dialogue, 147 objets, 744 animations, 31 Echos,
 * 8 niveaux, 11 cinematiques) vit dans `assets/` de l'APK. ContentDb ne connait
 * que l'interface AssetSource : deux methodes, `read` et `exists`. Rien d'autre
 * ne depend d'Android dans la simulation, ce qui permet de la tester sur JVM.
 *
 * 02.10 : deux cellules actives en memoire, budget de streaming par tranche de
 * 4 ms. Les fichiers JSON sont lus a la demande et mis en cache par ContentDb.
 */
package com.velmora.lohen;

import android.content.res.AssetManager;

import com.velmora.lohen.sim.core.ContentDb;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class AssetSourceAndroid implements ContentDb.AssetSource {

    private final AssetManager assets;
    private long bytesRead;
    private int filesRead;
    private int misses;

    public AssetSourceAndroid(AssetManager assets) {
        this.assets = assets;
    }

    @Override
    public byte[] read(String path) throws IOException {
        InputStream in = null;
        try {
            in = assets.open(path);
            ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 14);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            byte[] data = out.toByteArray();
            bytesRead += data.length;
            filesRead++;
            return data;
        } catch (IOException e) {
            misses++;
            throw e;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    /* rien a faire */
                }
            }
        }
    }

    @Override
    public boolean exists(String path) {
        InputStream in = null;
        try {
            in = assets.open(path);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    /* rien a faire */
                }
            }
        }
    }

    public long bytesRead() {
        return bytesRead;
    }

    public int filesRead() {
        return filesRead;
    }

    public int misses() {
        return misses;
    }
}
