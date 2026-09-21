/*
 * LOHEN — gl/ShaderLib.java
 *
 * Les shaders du moteur, en GLSL ES 1.00 (OpenGL ES 2.0, 02.05). Trois
 * programmes seulement, parce que trois usages seulement :
 *
 *   SCENE  — la geometrie du monde, eclairage de Clein, brouillard de Velmora.
 *            Une seule source directionnelle (le soleil bas, 05.27) + une
 *            emission locale (lanterne, Phare, ambre d'Esteban).
 *   POST   — le traitement d'image : saturation par sequence (05.08),
 *            exposition, vignettage, grain 0,035 a 24 images/s (05.13),
 *            barres 2.39:1 des cinematiques (15.01), flou de pause (14.05).
 *   UI     — les quads 2D : HUD, menus, journal, lettre, atlas de glyphes.
 *
 * REGLE ABSOLUE (05.01-05.07) : aucun contour, aucune surbrillance, aucune
 * fleche, aucun jaune autre que l'ambre #FFA33C reserve a Esteban. Les objets
 * interactifs se signalent par un contraste d'humidite, pas par un outline.
 */
package com.velmora.lohen.gl;

public final class ShaderLib {

    private ShaderLib() {
    }

    /* ------------------------------------------------------------------ */
    /* SCENE                                                               */
    /* ------------------------------------------------------------------ */

    public static final String SCENE_VS = ""
            + "uniform mat4 uMvp;\n"
            + "uniform mat4 uModel;\n"
            + "uniform float uTime;\n"
            + "attribute vec3 aPos;\n"
            + "attribute vec3 aNormal;\n"
            + "attribute vec4 aColor;\n"
            + "attribute vec2 aParam;\n"     /* x = verre 0/1, y = emission */
            + "varying vec3 vWorld;\n"
            + "varying vec3 vNormal;\n"
            + "varying vec4 vColor;\n"
            + "varying vec2 vParam;\n"
            + "void main() {\n"
            + "  vec4 wp = uModel * vec4(aPos, 1.0);\n"
            + "  vWorld = wp.xyz;\n"
            + "  vNormal = mat3(uModel) * aNormal;\n"
            + "  vColor = aColor;\n"
            + "  vParam = aParam;\n"
            + "  gl_Position = uMvp * vec4(aPos, 1.0);\n"
            + "}\n";

    public static final String SCENE_FS = ""
            + "precision mediump float;\n"
            + "uniform vec3 uCamPos;\n"
            + "uniform vec3 uSunDir;\n"
            + "uniform vec3 uSunColor;\n"
            + "uniform vec3 uAmbientSky;\n"
            + "uniform vec3 uAmbientGround;\n"
            + "uniform vec3 uFogColor;\n"
            + "uniform float uFogDensity;\n"
            + "uniform float uFogVisibility;\n"
            + "uniform vec3 uLampPos;\n"        /* lanterne portee / lampe */
            + "uniform vec3 uLampColor;\n"
            + "uniform float uLampRange;\n"
            + "uniform vec3 uBeamPos;\n"        /* faisceau du Phare */
            + "uniform vec3 uBeamDir;\n"
            + "uniform float uBeamStrength;\n"
            + "uniform float uExposure;\n"      /* +0,4 EV dans le faisceau */
            + "uniform float uTime;\n"
            + "varying vec3 vWorld;\n"
            + "varying vec3 vNormal;\n"
            + "varying vec4 vColor;\n"
            + "varying vec2 vParam;\n"
            + "void main() {\n"
            + "  vec3 n = normalize(vNormal);\n"
            + "  vec3 v = normalize(uCamPos - vWorld);\n"
            + "  float ndl = max(dot(n, -uSunDir), 0.0);\n"
            + "  vec3 sun = uSunColor * ndl;\n"
            + "  vec3 amb = mix(uAmbientGround, uAmbientSky, n.y * 0.5 + 0.5);\n"
            + "  vec3 col = vColor.rgb * (sun + amb);\n"
            /* la lampe : 2700 K, portee de 4 m quand elle est posee (08.11) */
            + "  if (uLampRange > 0.01) {\n"
            + "    vec3 ld = uLampPos - vWorld;\n"
            + "    float d = length(ld);\n"
            + "    float att = max(0.0, 1.0 - d / uLampRange);\n"
            + "    att *= att;\n"
            + "    float ldl = max(dot(n, normalize(ld)), 0.0);\n"
            + "    col += vColor.rgb * uLampColor * (att * ldl * 1.6);\n"
            + "  }\n"
            /* le faisceau du Phare : 900 m, balayage de 20 s (09.31) */
            + "  if (uBeamStrength > 0.001) {\n"
            + "    vec3 bd = vWorld - uBeamPos;\n"
            + "    float along = dot(bd, uBeamDir);\n"
            + "    if (along > 0.0) {\n"
            + "      vec3 perp = bd - uBeamDir * along;\n"
            + "      float r = length(perp);\n"
            + "      float cone = max(0.0, 1.0 - r / (along * 0.055 + 1.2));\n"
            + "      col += vec3(1.0, 0.94, 0.82) * cone * uBeamStrength * 0.35;\n"
            + "    }\n"
            + "  }\n"
            /* le verre de la Maree : reflet rasant, jamais un miroir net */
            + "  if (vParam.x > 0.5) {\n"
            + "    float fres = pow(1.0 - max(dot(n, v), 0.0), 3.0);\n"
            + "    float ripple = sin(vWorld.x * 1.7 + uTime * 0.6)\n"
            + "                 * sin(vWorld.z * 1.3 - uTime * 0.45);\n"
            + "    col += vec3(0.42, 0.95, 0.85) * fres * (0.10 + 0.03 * ripple);\n"
            + "    col *= 0.86 + 0.14 * ripple;\n"
            + "  }\n"
            /* l'emission : l'ambre d'Esteban, le cyan des Figures */
            + "  col += vColor.rgb * vParam.y;\n"
            /* brouillard exponentiel, jamais un fog lineaire (05.24) */
            + "  float dist = length(uCamPos - vWorld);\n"
            + "  float f = 1.0 - exp(-uFogDensity * dist);\n"
            + "  f = clamp(f, 0.0, 1.0);\n"
            + "  col = mix(col, uFogColor, f);\n"
            + "  col *= uExposure;\n"
            + "  gl_FragColor = vec4(col, vColor.a);\n"
            + "}\n";

    /* ------------------------------------------------------------------ */
    /* POST                                                                */
    /* ------------------------------------------------------------------ */

    public static final String POST_VS = ""
            + "attribute vec2 aPos;\n"
            + "attribute vec2 aUv;\n"
            + "varying vec2 vUv;\n"
            + "void main() {\n"
            + "  vUv = aUv;\n"
            + "  gl_Position = vec4(aPos, 0.0, 1.0);\n"
            + "}\n";

    public static final String POST_FS = ""
            + "precision mediump float;\n"
            + "uniform sampler2D uTex;\n"
            + "uniform vec2 uTexel;\n"
            + "uniform float uSaturation;\n"     /* 0,24 a 0,70 selon la sequence */
            + "uniform float uExposure;\n"
            + "uniform float uVignette;\n"
            + "uniform float uGrain;\n"          /* 0,035 (05.13) */
            + "uniform float uTime;\n"
            + "uniform float uLetterbox;\n"      /* 0..1 : barres 2.39:1 */
            + "uniform float uBlur;\n"           /* flou de pause (14.05) */
            + "uniform float uFade;\n"           /* fondu de mort / de skip */
            + "uniform vec3 uFadeColor;\n"
            + "varying vec2 vUv;\n"
            + "float rand(vec2 co) {\n"
            + "  return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);\n"
            + "}\n"
            + "void main() {\n"
            + "  vec2 uv = vUv;\n"
            /* les barres noires arrivent en 0,5 s (15.01) */
            + "  if (uLetterbox > 0.001) {\n"
            + "    float half_h = (1.0 - 1.0 / 2.39 * (1.0 / max(uv.y, 0.0001))) * 0.0;\n"
            + "    float bar = (1.0 - (1.0 / 2.39)) * 0.5 * uLetterbox;\n"
            + "    float scaled = (1.0 - 2.0 * bar);\n"
            + "    if (uv.y < bar || uv.y > 1.0 - bar) {\n"
            + "      gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);\n"
            + "      return;\n"
            + "    }\n"
            + "    uv.y = (uv.y - bar) / max(scaled, 0.0001);\n"
            + "    half_h = 0.0;\n"
            + "  }\n"
            + "  vec3 col;\n"
            + "  if (uBlur > 0.001) {\n"
            /* flou de pause : 9 prelevements, jamais un flou de galerie */
            + "    col = texture2D(uTex, uv).rgb * 0.28;\n"
            + "    vec2 o = uTexel * (2.0 + uBlur * 4.0);\n"
            + "    col += texture2D(uTex, uv + vec2(o.x, 0.0)).rgb * 0.09;\n"
            + "    col += texture2D(uTex, uv - vec2(o.x, 0.0)).rgb * 0.09;\n"
            + "    col += texture2D(uTex, uv + vec2(0.0, o.y)).rgb * 0.09;\n"
            + "    col += texture2D(uTex, uv - vec2(0.0, o.y)).rgb * 0.09;\n"
            + "    col += texture2D(uTex, uv + o).rgb * 0.09;\n"
            + "    col += texture2D(uTex, uv - o).rgb * 0.09;\n"
            + "    col += texture2D(uTex, uv + vec2(o.x, -o.y)).rgb * 0.09;\n"
            + "    col += texture2D(uTex, uv + vec2(-o.x, o.y)).rgb * 0.09;\n"
            + "  } else {\n"
            + "    col = texture2D(uTex, uv).rgb;\n"
            + "  }\n"
            /* saturation : le monde est pauvre en couleur, pas gris plat */
            + "  float l = dot(col, vec3(0.2126, 0.7152, 0.0722));\n"
            + "  col = mix(vec3(l), col, uSaturation);\n"
            + "  col *= uExposure;\n"
            /* vignettage : les bords tombent, le centre reste lisible */
            + "  vec2 d = uv - 0.5;\n"
            + "  float vig = 1.0 - uVignette * dot(d, d) * 1.9;\n"
            + "  col *= clamp(vig, 0.0, 1.0);\n"
            /* grain : 0,035 a 24 images par seconde, pas a 60 (05.13) */
            + "  float g = rand(uv * vec2(1024.0, 512.0) + fract(uTime) * 91.7);\n"
            + "  col += (g - 0.5) * uGrain;\n"
            + "  col = mix(col, uFadeColor, clamp(uFade, 0.0, 1.0));\n"
            + "  gl_FragColor = vec4(col, 1.0);\n"
            + "}\n";

    /* ------------------------------------------------------------------ */
    /* UI                                                                  */
    /* ------------------------------------------------------------------ */

    public static final String UI_VS = ""
            + "uniform vec2 uResolution;\n"
            + "attribute vec2 aPos;\n"
            + "attribute vec2 aUv;\n"
            + "attribute vec4 aColor;\n"
            + "varying vec2 vUv;\n"
            + "varying vec4 vColor;\n"
            + "void main() {\n"
            + "  vec2 p = aPos / uResolution * 2.0 - 1.0;\n"
            + "  gl_Position = vec4(p.x, -p.y, 0.0, 1.0);\n"
            + "  vUv = aUv;\n"
            + "  vColor = aColor;\n"
            + "}\n";

    public static final String UI_FS = ""
            + "precision mediump float;\n"
            + "uniform sampler2D uTex;\n"
            + "uniform float uTextured;\n"
            + "uniform float uOpacity;\n"
            + "varying vec2 vUv;\n"
            + "varying vec4 vColor;\n"
            + "void main() {\n"
            + "  vec4 c = vColor;\n"
            + "  if (uTextured > 0.5) {\n"
            + "    vec4 t = texture2D(uTex, vUv);\n"
            + "    c.rgb *= t.rgb;\n"
            + "    c.a *= t.a;\n"
            + "  }\n"
            + "  c.a *= uOpacity;\n"
            + "  if (c.a < 0.004) {\n"
            + "    discard;\n"
            + "  }\n"
            + "  gl_FragColor = c;\n"
            + "}\n";

    /* ------------------------------------------------------------------ */
    /* Couleurs canoniques (05.05 - 05.07)                                 */
    /* ------------------------------------------------------------------ */

    /** Ambre #FFA33C — ESTEBAN UNIQUEMENT. Jamais ailleurs. */
    public static final int AMBER = 0xFFA33C;
    /** Cyan #6BF2D8 — les Figures, jamais un PNJ vivant. */
    public static final int CYAN = 0x6BF2D8;
    /** Blanc casse du texte et du HUD — jamais un blanc pur (05.04). */
    public static final int OFFWHITE = 0xEDE6DA;
    /** Bleu-noir des reflets et du brouillard. */
    public static final int NIGHT = 0x0C1420;
    /** Rouge sourd du souffle bas — jamais un rouge vif (14.02). */
    public static final int BREATH_RED = 0xA8442F;

    public static float r(int argb) {
        return ((argb >> 16) & 0xFF) / 255f;
    }

    public static float g(int argb) {
        return ((argb >> 8) & 0xFF) / 255f;
    }

    public static float b(int argb) {
        return (argb & 0xFF) / 255f;
    }

    public static float a(int argb) {
        return ((argb >> 24) & 0xFF) / 255f;
    }
}
