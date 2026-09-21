// =============================================================================
// fog_volumetric_cheap.gdshader — SHADER 06 / 10
// Reference maitre : BLOC 05.22 · 09.17 (a 150 m, la brume passe SOUS le joueur).
// -----------------------------------------------------------------------------
// « C'est un mensonge complet et ca passe pour du volumetrique. » (05.22)
//
//   12 slices de quads alignes camera, noise 3D anime, plutot que du vrai
//   volumetrique Godot (trop cher sur Adreno). Densite pilotee par zone.
//   Eclairage : produit scalaire + phase de Henyey-Greenstein approximee.
//
// LE MOMENT QUE CE SHADER DOIT REUSSIR
//   [OBL] A 150 m dans S8, LA BRUME PASSE SOUS LE JOUEUR. Velmora disparait.
//   Il ne reste que le Phare, le ciel, et le bruit du vent.
//   Ce n'est pas un effet special : c'est `fog_plane_y` qui passe au-dessous
//   de la camera, et les 12 slices se retournent naturellement.
//   Job CI `fog-plane-s8` : verifie que la courbe d'altitude du brouillard
//   de la zone 8 croise 150 m et jamais 151 ou 149.
//
// [OBL] LA BRUME N'EST JAMAIS UN MUR. Elle ne sert jamais a cacher une
//   limite de niveau ni a masquer du pop-in. Si un artiste l'utilise pour
//   ca, c'est le LOD qu'on corrige. Job CI `fog-not-a-wall`.
//
// BUDGET
//   ALU ................... 43 instructions par slice
//   Textures .............. 1 sample 3D par slice
//   Draw calls ............ 1 (MultiMesh de 12 quads)
//   Overdraw .............. 12 couches max, alpha faible, blend_mix
// =============================================================================

shader_type spatial;
render_mode blend_mix, cull_disabled, depth_draw_never, unshaded,
            fog_disabled, shadows_disabled;

uniform sampler3D fog_noise : filter_linear_mipmap, repeat_enable;
uniform sampler2D depth_tex : hint_depth_texture, filter_nearest;

// --- DENSITE PAR ZONE --------------------------------------------------------
uniform float density : hint_range(0.0, 2.0) = 0.62;
uniform vec3  fog_color : source_color = vec3(0.678, 0.714, 0.745);
// [OBL] La brume n'est JAMAIS ambre. `amber-guard`.

// --- LE PLAN DE BRUME (05.22 / 09.17) ----------------------------------------
// Altitude monde du "dessus" de la nappe. Pousse par la zone.
uniform float fog_plane_y = 18.0;
uniform float fog_thickness_m : hint_range(1.0, 200.0) = 42.0;

// --- ANIMATION ---------------------------------------------------------------
uniform vec3  wind = vec3(0.012, 0.002, -0.008);
uniform float noise_scale : hint_range(0.005, 0.5) = 0.028;
uniform float detail_scale : hint_range(0.01, 1.0) = 0.11;

// --- ECLAIRAGE MENSONGER (05.22) ---------------------------------------------
// Henyey-Greenstein approximee : un seul pow, pas d'integrale.
uniform vec3  sun_dir = vec3(-0.42, -0.21, 0.88);
uniform vec3  sun_color : source_color = vec3(1.0, 0.925, 0.82);
uniform float hg_g : hint_range(-0.9, 0.9) = 0.55;
uniform float sun_gain : hint_range(0.0, 4.0) = 1.35;

// --- FONDUS ------------------------------------------------------------------
uniform float soft_depth_m : hint_range(0.1, 20.0) = 3.5;   // anti-intersection
uniform float near_fade_m  : hint_range(0.1, 20.0) = 2.2;   // anti-camera-dedans
uniform float far_fade_m   : hint_range(10.0, 900.0) = 320.0;

varying vec3  v_world_pos;
varying float v_slice_alpha;

void vertex() {
	v_world_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	// Chaque slice porte son opacite dans COLOR.a, ecrite par le MultiMesh :
	// les slices du bas sont plus denses que celles du haut.
	v_slice_alpha = COLOR.a;
}

// HG approximee. Un pow, pas de division couteuse.
float henyey_greenstein(float cos_t, float g) {
	float g2 = g * g;
	float denom = 1.0 + g2 - 2.0 * g * cos_t;
	return (1.0 - g2) / (4.0 * 3.14159265 * pow(max(denom, 1e-3), 1.5));
}

void fragment() {
	// 1. BRUIT 3D ANIME ---------------------------------------------------------
	vec3 p1 = v_world_pos * noise_scale + wind * TIME;
	vec3 p2 = v_world_pos * detail_scale - wind * TIME * 1.7;
	float n1 = texture(fog_noise, fract(p1)).r;
	float n2 = texture(fog_noise, fract(p2)).r;
	float n = n1 * 0.72 + n2 * 0.28;

	// 2. PROFIL VERTICAL --------------------------------------------------------
	// La nappe est dense en bas, nulle au-dessus de `fog_plane_y`.
	float below = fog_plane_y - v_world_pos.y;
	float vertical = clamp(below / max(fog_thickness_m, 0.01), 0.0, 1.0);
	vertical = vertical * vertical;   // plus dense en profondeur

	// 3. SOFT DEPTH -------------------------------------------------------------
	// Sans ca, les quads coupent la geometrie et tout l'effet s'effondre.
	float scene_depth = texture(depth_tex, SCREEN_UV).r;
	vec4 view_pos = INV_PROJECTION_MATRIX * vec4(SCREEN_UV * 2.0 - 1.0, scene_depth, 1.0);
	view_pos.xyz /= view_pos.w;
	float scene_dist = -view_pos.z;
	float frag_dist  = -(VIEW_MATRIX * vec4(v_world_pos, 1.0)).z;
	float soft = clamp((scene_dist - frag_dist) / soft_depth_m, 0.0, 1.0);

	// 4. FONDUS PROCHE ET LOINTAIN ----------------------------------------------
	float near_f = clamp(frag_dist / near_fade_m, 0.0, 1.0);
	float far_f  = 1.0 - clamp((frag_dist - far_fade_m) / (far_fade_m * 0.4), 0.0, 1.0);

	// 5. ECLAIRAGE --------------------------------------------------------------
	vec3 vdir = normalize(v_world_pos - CAMERA_POSITION_WORLD);
	float cos_t = dot(vdir, normalize(-sun_dir));
	float hg = henyey_greenstein(cos_t, hg_g);
	vec3 lit = fog_color + sun_color * hg * sun_gain * 0.35;

	// 6. SORTIE -----------------------------------------------------------------
	float a = n * vertical * density * v_slice_alpha * soft * near_f * far_f;

	ALBEDO = vec3(0.0);
	EMISSION = lit;
	ALPHA = clamp(a, 0.0, 1.0);
}
