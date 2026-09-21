// =============================================================================
// water_shallow.gdshader — SHADER 10 / 10
// Reference maitre : BLOC 05.26.
// -----------------------------------------------------------------------------
// FLAQUES ET EAU RESIDUELLE SUR LES TOITS.
//   Depth-fade, distortion legere, reflet de la skybox uniquement.
//   Cout minime. Presente PARTOUT parce que les flaques doublent la quantite
//   de lumiere percue : c'est le poste d'eclairage le moins cher du projet.
//
// LES DEUX FLAQUES QUI COMPTENT
//   [OBL] Dans la salle de bal (S6), il y a DEUX flaques nettes sur le
//   parquet, sous les deux fenetres cassees. Elles s'effacent en dernier
//   pendant la bascule de E21 (t+6,2 s) et se reforment en premier pendant
//   la dissolution (t+20,0 s). Esteban a ferme toutes les fenetres SAUF
//   ces deux-la, le 14 octobre, et personne ne le dira jamais.
//   Job CI `windows-two` : les deux flaques portent les index des deux
//   dernieres fenetres de sa tournee (S6_EST_L192).
//
// [OBL] AUCUNE FLAQUE N'EST INTERACTIVE. Pas d'eclaboussure au pas, pas de
//   son dedie, pas de ride au contact. Le seul liquide du jeu qui garde une
//   trace est le verre de la Maree (SHADER 01), et c'est justement parce
//   qu'il n'est plus liquide.
//
// BUDGET : 29 instructions, 3 samples.
// =============================================================================

shader_type spatial;
render_mode blend_mix, cull_back, depth_draw_never, diffuse_lambert,
            specular_schlick_ggx;

uniform sampler2D depth_tex  : hint_depth_texture, filter_nearest;
uniform sampler2D screen_tex : hint_screen_texture, filter_linear_mipmap;
uniform sampler2D ripple_normal : hint_normal, filter_linear_mipmap, repeat_enable;
uniform samplerCube sky_cube : source_color, filter_linear_mipmap;

uniform vec3  water_tint : source_color = vec3(0.106, 0.133, 0.149);
uniform float water_alpha : hint_range(0.0, 1.0) = 0.55;
uniform float roughness_const : hint_range(0.0, 0.3) = 0.035;

// Distortion legere : une flaque de 2 cm ne fait pas de vagues.
uniform vec2  flow = vec2(0.006, 0.004);
uniform float distort_amount : hint_range(0.0, 0.08) = 0.014;
uniform float normal_scale : hint_range(0.5, 12.0) = 3.2;

// Depth-fade : le bord de la flaque disparait au lieu de couper net.
uniform float edge_fade_m : hint_range(0.005, 0.5) = 0.055;

uniform float sky_gain : hint_range(0.0, 3.0) = 1.15;
uniform float fresnel_power : hint_range(1.0, 8.0) = 3.4;

void fragment() {
	// 1. NORMALE ----------------------------------------------------------------
	vec2 uv_n = UV * normal_scale + flow * TIME;
	vec3 n = texture(ripple_normal, uv_n).xyz * 2.0 - 1.0;
	NORMAL_MAP = normalize(vec3(n.xy * 0.45, 1.0)) * 0.5 + 0.5;

	// 2. DEPTH FADE -------------------------------------------------------------
	float scene_depth = texture(depth_tex, SCREEN_UV).r;
	vec4 vp = INV_PROJECTION_MATRIX * vec4(SCREEN_UV * 2.0 - 1.0, scene_depth, 1.0);
	vp.xyz /= vp.w;
	float scene_dist = -vp.z;
	float frag_dist  = -(VIEW_MATRIX * vec4(
		(MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz, 1.0)).z;
	float edge = clamp((scene_dist - frag_dist) / edge_fade_m, 0.0, 1.0);

	// 3. REFLET DE SKYBOX UNIQUEMENT (05.26) ------------------------------------
	vec3 refl_dir = reflect(-normalize(VIEW), normalize(NORMAL + n * 0.3));
	vec3 sky = texture(sky_cube, refl_dir).rgb * sky_gain;

	float ndv  = clamp(dot(normalize(NORMAL), normalize(VIEW)), 0.0, 1.0);
	float fres = pow(1.0 - ndv, fresnel_power);

	// 4. SORTIE -----------------------------------------------------------------
	ALBEDO    = water_tint;
	ROUGHNESS = roughness_const;
	METALLIC  = 0.0;
	SPECULAR  = 0.9;
	EMISSION  = sky * fres * 0.85;
	ALPHA     = clamp(mix(water_alpha, 1.0, fres) * edge, 0.0, 1.0);
}
