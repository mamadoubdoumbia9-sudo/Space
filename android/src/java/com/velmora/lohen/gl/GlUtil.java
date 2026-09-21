/*
 * LOHEN — gl/GlUtil.java
 *
 * Le strict minimum OpenGL ES 2.0 : compilation de shaders, liens de
 * programmes, buffers. Aucune dependance externe (pas de libGDX, pas de
 * Filament) : le moteur est ecrit a la main, conformement a l'ADR-001.
 *
 * 02.05 : GLES 2.0 minimum, pas de GLES 3 exige — le jeu doit tourner sur un
 * telephone de 2017 sans mise a jour.
 */
package com.velmora.lohen.gl;

import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public final class GlUtil {

    public static final String TAG = "LOHEN";

    private GlUtil() {
    }

    public static FloatBuffer floats(float[] data) {
        FloatBuffer fb = ByteBuffer.allocateDirect(data.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(data).position(0);
        return fb;
    }

    public static FloatBuffer allocate(int floats) {
        return ByteBuffer.allocateDirect(floats * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    public static ShortBuffer shorts(short[] data) {
        ShortBuffer sb = ByteBuffer.allocateDirect(data.length * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        sb.put(data).position(0);
        return sb;
    }

    public static int compileShader(int type, String src) {
        int sh = GLES20.glCreateShader(type);
        GLES20.glShaderSource(sh, src);
        GLES20.glCompileShader(sh);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String info = GLES20.glGetShaderInfoLog(sh);
            GLES20.glDeleteShader(sh);
            throw new RuntimeException("shader (" + type + ") : " + info);
        }
        return sh;
    }

    public static int program(String vs, String fs) {
        int v = compileShader(GLES20.GL_VERTEX_SHADER, vs);
        int f = compileShader(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);
        if (ok[0] == 0) {
            String info = GLES20.glGetProgramInfoLog(p);
            GLES20.glDeleteProgram(p);
            throw new RuntimeException("link : " + info);
        }
        return p;
    }

    public static int uniform(int prog, String name) {
        int loc = GLES20.glGetUniformLocation(prog, name);
        if (loc < 0) {
            Log.w(TAG, "uniform absent : " + name);
        }
        return loc;
    }

    public static int attrib(int prog, String name) {
        int loc = GLES20.glGetAttribLocation(prog, name);
        if (loc < 0) {
            Log.w(TAG, "attribut absent : " + name);
        }
        return loc;
    }

    public static int texture(ByteBuffer rgba, int w, int h) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rgba);
        return ids[0];
    }

    public static void check(String where) {
        int err = GLES20.glGetError();
        if (err != GLES20.GL_NO_ERROR) {
            Log.w(TAG, "GL erreur " + err + " apres " + where);
        }
    }
}
