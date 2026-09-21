// =============================================================================
// stone_wet.gdshader — SHADER 04 / 10
// Reference maitre : BLOC 05.20 · 09.21 (langage visuel de la traversee).
// -----------------------------------------------------------------------------
// ROLE
//   Triplanaire sur les grosses surfaces de pierre (economise 40 % de travail
//   d'UV). Le masque d'humidite est pilote par la HAUTEUR AU-DESSUS DE LA
//   MAREE, pas par une texture peinte :
//       sous 12 m ............. trempe, tres reflechissant
//       entre 12 et 30 m ...... humide
//       au-dessus de 30 m ..... sec et poussiereux
//
//   [OBL] CELA RACONTE LE NIVEAU D'EAU D'AVANT. Aucun dialogue ne l'explique.
//   Aucun document du jeu ne le mentionne. C'est un fait de monde encode dans
//   un materiau, et c'est tout ce qu'il en sera jamais dit.
//
// ROLE SECONDAIRE, CRITIQUE : LE LANGAGE DE L'INTERACTION (09.21)
//   « Interactif narratif : un leger contraste d'humidite — le shader
//     `stone_wet` baisse localement. Subtil, pas un contour brillant. »
//   [OBL] INTERDICTION ABSOLUE de la peinture jaune, du contour blanc, de
//   l'outline. Si le joueur ne voit pas, c'est le level art qui a echoue.
//   Job CI `no-outline` : aucun materiau du jeu ne porte de shader d'outline.
//
// BUDGET
//   ALU ................... 77 instructions
//   Textures .............. 9 samples (3 axes x [albedo, normal, rough])
//                           + 1 detail normal = 10 au pire cas triplanaire
//   Optimisation .......... les poids triplanaires sont seuillés : sur une
//                           face franche, 2 des 3 axes tombent a 0 et le
//                           compilateur ne peut pas les eliminer, mais le
//                           cache texture les rend quasi gratuits.
// =============================================================================

shader_type spatial;
render_mode cull_back, diffuse_burley, specular_schlick_ggx;

// --- TRIPLANAIRE -------------------------------------------------------------
uniform sampler2D stone_albedo : source_color, filter_linear_mipmap, repeat_enable;
uniform sampler2D stone_normal : hint_normal,  filter_linear_mipmap, repeat_enable;
uniform sampler2D stone_orm    : hint_default_white, filter_linear_mipmap, repeat_enable;
uniform float tri_scale  : hint_range(0.05, 4.0) = 0.42;
uniform float tri_sharp  : hint_range(1.0, 16.0) = 5.5;

// --- DETAIL A 8x (05.20) -----------------------------------------------------
uniform sampler2D detail_normal : hint_normal, filter_linear_mipmap, repeat_enable;
uniform float detail_scale : hint_range(1.0, 32.0) = 8.0;
uniform float detail_gain  : hint_range(0.0, 2.0)  = 0.55;

// --- HUMIDITE PAR ALTITUDE (05.20) -------------------------------------------
// `maree_level_y` est pousse par la zone, pas code en dur : la Maree n'est
// pas au meme niveau partout dans Velmora.
uniform float maree_level_y   = 0.0;
uniform float soaked_below_m  : hint_range(0.0, 40.0) = 12.0;
uniform float damp_below_m    : hint_range(0.0, 80.0) = 30.0;
uniform float wet_roughness   : hint_range(0.0, 1.0) = 0.11;
uniform float dry_roughness   : hint_range(0.0, 1.0) = 0.86;
uniform vec3  wet_darkening   : source_color = vec3(0.42, 0.45, 0.49);

// --- POUSSIERE (au-dessus de 30 m) -------------------------------------------
uniform vec3  dust_color : source_color = vec3(0.616, 0.596, 0.557);
uniform float dust_gain  : hint_range(0.0, 1.0) = 0.34;
// La poussiere ne se depose que sur les faces vers le haut. Evident, et
// c'est ce qui fait que les rebords lisent bien en silhouette.
uniform float dust_up_bias : hint_range(0.0, 1.0) = 0.62;

// --- CONTRASTE D'INTERACTION (09.21) -----------------------------------------
// Pousse par `InteractableSurface.gd` sur un rayon monde. Baisse LOCALEMENT
// l'humidite : la pierre parait un peu plus seche, donc un peu plus claire.
// [OBL] Amplitude maximale 0.22. Au-dela, ca devient un contour, et c'est
// interdit. Le clamp est en dur dans le shader, pas seulement dans le hint.
uniform vec3  interact_center_ws = vec3(0.0, -9999.0, 0.0);
uniform float interact_radius_m : hint_range(0.1, 4.0) = 1.15;
uniform float interact_amount   : hint_range(0.0, 0.22) = 0.18;

varying vec3 v_world_pos;
varying vec3 v_world_nrm;

void vertex() {
	v_world_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	v_world_nrm = normalize((MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz);
}

vec3 tri_weights(vec3 n, float sharpness) {
	vec3 w = pow(abs(n), vec3(sharpness));
	return w / max(w.x + w.y + w.z, 1e-4);
}

void fragment() {
	vec3 wp = v_world_pos * tri_scale;
	vec3 w  = tri_weights(v_world_nrm, tri_sharp);

	// 1. ALBEDO TRIPLANAIRE ----------------------------------------------------
	vec3 ax = texture(stone_albedo, wp.zy).rgb;
	vec3 ay = texture(stone_albedo, wp.xz).rgb;
	vec3 az = texture(stone_albedo, wp.xy).rgb;
	vec3 albedo = ax * w.x + ay * w.y + az * w.z;

	// 2. ORM TRIPLANAIRE (on n'utilise que le canal rugosite, canal G) ---------
	float rx = texture(stone_orm, wp.zy).g;
	float ry = texture(stone_orm, wp.xz).g;
	float rz = texture(stone_orm, wp.xy).g;
	float rough_map = rx * w.x + ry * w.y + rz * w.z;

	// 3. NORMALE TRIPLANAIRE (whiteout blend, correct et pas cher) -------------
	vec3 nx = texture(stone_normal, wp.zy).xyz * 2.0 - 1.0;
	vec3 ny = texture(stone_normal, wp.xz).xyz * 2.0 - 1.0;
	vec3 nz = texture(stone_normal, wp.xy).xyz * 2.0 - 1.0;
	vec3 n_ws = normalize(
		  vec3(0.0, nx.y, nx.x) * w.x
		+ vec3(ny.x, 0.0, ny.y) * w.y
		+ vec3(nz.x, nz.y, 0.0) * w.z
		+ v_world_nrm
	);

	// 4. DETAIL 8x -------------------------------------------------------------
	vec3 dn = texture(detail_normal, wp.xz * detail_scale).xyz * 2.0 - 1.0;
	n_ws = normalize(n_ws + dn * detail_gain * 0.35);

	// 5. HUMIDITE PAR ALTITUDE -------------------------------------------------
	float h = v_world_pos.y - maree_level_y;
	// 1.0 = trempe, 0.0 = sec. Deux paliers doux, jamais de marche franche.
	float wetness = 1.0 - smoothstep(soaked_below_m, damp_below_m, h);

	// 6. CONTRASTE D'INTERACTION (09.21) ---------------------------------------
	float di = distance(v_world_pos, interact_center_ws);
	float mask = 1.0 - smoothstep(interact_radius_m * 0.45, interact_radius_m, di);
	// [OBL] clamp dur : jamais plus de 0.22 de delta.
	wetness = clamp(wetness - mask * min(interact_amount, 0.22), 0.0, 1.0);

	// 7. POUSSIERE -------------------------------------------------------------
	float up = clamp(dot(v_world_nrm, vec3(0.0, 1.0, 0.0)), 0.0, 1.0);
	float dust = (1.0 - wetness) * mix(1.0 - dust_up_bias, 1.0, up) * dust_gain;

	// 8. SORTIE ----------------------------------------------------------------
	vec3 wet_albedo = albedo * wet_darkening;
	vec3 final_albedo = mix(albedo, wet_albedo, wetness);
	final_albedo = mix(final_albedo, dust_color, dust * 0.55);

	ALBEDO    = final_albedo;
	NORMAL    = n_ws;
	ROUGHNESS = mix(mix(dry_roughness, rough_map, 0.7), wet_roughness, wetness);
	METALLIC  = 0.0;
	SPECULAR  = mix(0.28, 0.62, wetness);
}
