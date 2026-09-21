/*
 * LOHEN — gl/TextureLib.java
 *
 * Les IMAGES du jeu (retour joueur : « génère des images dans le jeu ») :
 * atlas de matières 4096×4096 (triplanar, voir ShaderLib.SCENE_FS), fonds de
 * menu, papier de la lettre, pages du journal, écran d'accueil. Tout est
 * chargé à la demande depuis les assets et mip-mappé pour un téléphone de
 * 2017 (02.05). Appel UNIQUEMENT sur le thread GL.
 */
package com.velmora.lohen.gl;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class TextureLib {

    private final Map<String, Integer> ids = new HashMap<String, Integer>();
    private AssetManager assets;

    public void setAssets(AssetManager assets) {
        this.assets = assets;
    }

    /** Charge (une seule fois) la texture et renvoie son id GL ; 0 si absente. */
    public int get(String path) {
        return get(path, false);
    }

    /** getHQ : 32 bits pour les images aux grands degradés (menus, papier). */
    public int getHQ(String path) {
        return get(path, true);
    }

    private int get(String path, boolean highQuality) {
        Integer known = ids.get(path + (highQuality ? "#hq" : ""));
        if (known != null) {
            return known.intValue();
        }
        if (assets == null) {
            return 0;
        }
        InputStream in = null;
        try {
            in = assets.open(path);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = highQuality
                    ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
            if (!highQuality && path.indexOf("atlas") >= 0) {
                /* les atlas sont livres en 4096² (assets reels) mais decodes
                 * en 2048² : sur un telephone de 2017, 4x43 Mo de VRAM ne
                 * tiennent pas — 512 px par tuile suffisent a l'ecran. */
                opts.inSampleSize = 2;
            }
            Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
            if (bmp == null) {
                ids.put(path + (highQuality ? "#hq" : ""), Integer.valueOf(0));
                return 0;
            }
            int[] tex = new int[1];
            GLES20.glGenTextures(1, tex, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR_MIPMAP_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
            bmp.recycle();
            ids.put(path + (highQuality ? "#hq" : ""), Integer.valueOf(tex[0]));
            return tex[0];
        } catch (Exception e) {
            ids.put(path + (highQuality ? "#hq" : ""), Integer.valueOf(0));
            return 0;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                    /* rien */
                }
            }
        }
    }

    /** Libere une image precise (l'atlas de la sequence precedente). */
    public void releasePath(String path) {
        Integer a = ids.remove(path);
        Integer b = ids.remove(path + "#hq");
        int[] del = new int[2];
        int n = 0;
        if (a != null && a.intValue() != 0) {
            del[n++] = a.intValue();
        }
        if (b != null && b.intValue() != 0) {
            del[n++] = b.intValue();
        }
        if (n > 0) {
            GLES20.glDeleteTextures(n, del, 0);
        }
    }

    public void release() {
        for (Integer id : ids.values()) {
            if (id.intValue() != 0) {
                GLES20.glDeleteTextures(1, new int[]{id.intValue()}, 0);
            }
        }
        ids.clear();
    }
}
