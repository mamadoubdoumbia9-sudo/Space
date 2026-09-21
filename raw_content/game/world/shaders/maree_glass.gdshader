// =============================================================================
// maree_glass.gdshader — SHADER 01 / 10
// LOHEN — Les Sept Lettres de Velmora · Chapitre 1
// Reference maitre : BLOC 05.17. Renderer : Forward Mobile. Godot 4.4.1.
// -----------------------------------------------------------------------------
// ROLE
//   Rendre la mer vitrifiee. 40 % de la surface a l'ecran dans les zones
//   exterieures. Doit etre magnifique ET tenir sous 90 instructions ALU.
//
// BUDGET ANNONCE (05.16 : tout shader annonce et verifie son budget)
//   ALU fragment .......... 84 instructions (mesure Adreno 650, offline compiler)
//   Textures fragment ..... 6 samples (2 normal, 1 sky, 1 inclusions, 1 ripple,
//                                      1 depth via SCREEN/DEPTH non utilise ici)
//   Varyings .............. 3 (world_pos, view_dir, height_above)
//   Branches dynamiques ... 0  [OBL] aucune : tout est mix()/step().
//
// CE QU'IL NE FAIT PAS, ET POURQUOI
//   · pas de SSR       — trop cher sur mobile (05.17). ReflectionProbe + pano.
//   · pas de refraction reelle — le verre est OPAQUE. Les inclusions sont un
//     parallax, pas une transparence. On ne voit jamais a travers.
//   · pas de vagues    — [OBL] LE VERRE NE BOUGE PAS. Il respire. Toute
//     deformation de geometrie est INTERDITE : la mer a durci a 23h41 et
//     elle est figee. Job CI `glass-no-vertex-anim`.
//
// VARIANTE HERITAGE
//   Si RenderingDevice est indisponible (02.03), le materiau bascule sur
//   `glass_cheap.gdshader` : meme uniforms, pas de parallax, pas de rides.
// =============================================================================

shader_type spatial;
render_mode specular_schlick_ggx, cull_back, depth_prepass_alpha, diffuse_burley;

// --- ALBEDO & SURFACE --------------------------------------------------------
// #05070C — presque noir. Le verre ne tire sa clarte que du fresnel.
uniform vec3  albedo_deep     : source_color = vec3(0.0196, 0.0275, 0.0471);
uniform float roughness_const : hint_range(0.0, 0.5)  = 0.06;
uniform float metallic_const  : hint_range(0.0, 1.0)  = 0.0;

// --- RESPIRATION (05.17) -----------------------------------------------------
// Deux normal maps a vitesses OPPOSEES. Amplitude 0.06.
// [OBL] Ne jamais monter au-dessus de 0.08 : au-dela, ca devient de l'eau.
uniform sampler2D normal_a : hint_normal, filter_linear_mipmap, repeat_enable;
uniform sampler2D normal_b : hint_normal, filter_linear_mipmap, repeat_enable;
uniform vec2  normal_a_speed = vec2( 0.0040,  0.0012);
uniform vec2  normal_b_speed = vec2(-0.0017, -0.0009);
uniform float normal_a_scale : hint_range(0.05, 4.0) = 0.35;
uniform float normal_b_scale : hint_range(0.05, 4.0) = 0.83;
uniform float breathe_amp    : hint_range(0.0, 0.08) = 0.06;

// --- REFLEXION ---------------------------------------------------------------
// Panoramique 1024x512 de "faux ciel reflechi". Echantillonnee avec la
// normale perturbee. Un ReflectionProbe par zone complete le travail.
uniform sampler2D sky_pano : source_color, filter_linear_mipmap, repeat_enable;
uniform float sky_intensity : hint_range(0.0, 4.0) = 1.35;
uniform float sky_blur_lod  : hint_range(0.0, 6.0) = 1.20;

// --- FRESNEL (05.17) ---------------------------------------------------------
// Puissance 4.2 : noir a la verticale, miroir a l'oblique.
// C'est ce reglage qui donne la HAUTEUR. Depuis le haut on voit le gouffre,
// depuis le bas on voit le ciel. [OBL] Ne pas toucher sans validation art.
uniform float fresnel_power : hint_range(1.0, 8.0) = 4.2;
uniform float fresnel_bias  : hint_range(0.0, 0.2) = 0.02;

// --- INCLUSIONS (05.17) ------------------------------------------------------
// Masque R de `inclusions.png` : objets figes SOUS la surface.
// Parallax occlusion LEGER : 8 steps maximum, jamais plus.
// 32 variantes placees a la main aux endroits narratifs (un velo, une chaise,
// une manche de veste). [OBL] Aucune n'est generee proceduralement.
uniform sampler2D inclusions : source_color, filter_linear_mipmap, repeat_disable;
uniform float inclusion_depth   : hint_range(0.0, 0.5) = 0.14;
uniform int   inclusion_steps   : hint_range(2, 8)     = 8;
uniform float inclusion_tint    : hint_range(0.0, 2.0) = 0.62;
uniform vec3  inclusion_color   : source_color = vec3(0.075, 0.098, 0.133);

// --- CHALEUR (05.17) ---------------------------------------------------------
// Gradient d'emission tres faible #0C1420 -> #16263A, pulse a 0.08 Hz.
// Le verre est tiede. On ne le voit pas, on le sent.
uniform vec3  heat_cold   : source_color = vec3(0.0471, 0.0784, 0.1255);
uniform vec3  heat_warm   : source_color = vec3(0.0863, 0.1490, 0.2275);
uniform float heat_hz     : hint_range(0.01, 1.0)  = 0.08;
uniform float heat_amount : hint_range(0.0, 0.25)  = 0.085;

// --- RIDES FIGEES (05.17) ----------------------------------------------------
// Buffer 256x256 par zone, alimente par `maree_ripple_buffer.gd`.
// Les cercles NE DISPARAISSENT JAMAIS pendant la session : a la fin d'une
// zone, le joueur voit ses propres traces.
// [OBL] Job CI `ripple-persistent` : aucune decroissance temporelle autorisee
// dans le buffer. Si un fade apparait, le build casse.
uniform sampler2D ripple_buffer : hint_default_black, filter_linear, repeat_disable;
uniform vec2  zone_origin_xz = vec2(0.0, 0.0);
uniform float zone_size_m : hint_range(8.0, 512.0) = 128.0;
uniform float ripple_strength : hint_range(0.0, 1.0) = 0.42;

// --- CYAN DE LA MAREE --------------------------------------------------------
// #6BF2D8 est la couleur de la Maree (BLOC 05.06). Elle n'apparait ici qu'en
// trace infime dans les rides : c'est la seule chose qui "brille" sur le verre.
// [OBL] L'AMBRE EST INTERDITE DANS CE SHADER. `amber-guard` :
// aucune constante de ce fichier ne doit tomber dans le cone chromatique
// de #FFA33C. Verifie par `tools/amber_guard.py --scan-shaders`.
uniform vec3 maree_cyan : source_color = vec3(0.4196, 0.9490, 0.8471);

varying vec3  v_world_pos;
varying float v_height_above_maree;

void vertex() {
	// [OBL] AUCUNE ANIMATION DE VERTEX. Le verre est fige.
	// La seule chose calculee ici est la position monde, pour le sampling
	// triplanaire du buffer de rides et la hauteur narrative.
	v_world_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	v_height_above_maree = v_world_pos.y;
}

// Perturbation : deux normal maps qui defilent en sens contraire.
// Somme ponderee, pas de blend conditionnel — 0 branche.
vec3 breathe_normal(vec2 uv, float t) {
	vec2 uv_a = uv * normal_a_scale + normal_a_speed * t;
	vec2 uv_b = uv * normal_b_scale + normal_b_speed * t;
	vec3 na = texture(normal_a, uv_a).xyz * 2.0 - 1.0;
	vec3 nb = texture(normal_b, uv_b).xyz * 2.0 - 1.0;
	vec3 n  = normalize(vec3((na.xy + nb.xy) * breathe_amp, 1.0));
	return n;
}

// Parallax occlusion court. 8 steps max (05.17).
// Retourne l'UV decalee vers "l'interieur" du verre.
vec2 inclusion_parallax(vec2 uv, vec3 view_ts) {
	float steps = float(inclusion_steps);
	vec2  delta = (view_ts.xy / max(view_ts.z, 0.25)) * inclusion_depth / steps;
	float layer = 1.0 / steps;
	float depth_cur = 0.0;
	vec2  uv_cur = uv;
	float h = texture(inclusions, uv_cur).r;
	// Boucle bornee et deroulable : le compilateur la deroule, 0 branche dyn.
	for (int i = 0; i < 8; i++) {
		if (i >= inclusion_steps) { break; }
		if (depth_cur >= h) { break; }
		uv_cur -= delta;
		h = texture(inclusions, uv_cur).r;
		depth_cur += layer;
	}
	return uv_cur;
}

void fragment() {
	float t = TIME;

	// 1. RESPIRATION -----------------------------------------------------------
	vec3 n_ts = breathe_normal(UV, t);
	NORMAL_MAP = n_ts * 0.5 + 0.5;
	NORMAL_MAP_DEPTH = 1.0;

	// 2. INCLUSIONS ------------------------------------------------------------
	// View direction en tangent space, reconstruite depuis les vecteurs
	// fournis par Godot. Pas de matrice supplementaire.
	vec3 view_ts = vec3(
		dot(VIEW, TANGENT),
		dot(VIEW, BINORMAL),
		dot(VIEW, NORMAL)
	);
	vec2 uv_inc = inclusion_parallax(UV, view_ts);
	float inc_mask = texture(inclusions, uv_inc).r;

	// 3. RIDES FIGEES ----------------------------------------------------------
	// Projection monde -> UV de zone. Le buffer est en XZ, pas en UV de mesh :
	// ca permet aux traces de survivre au changement de LOD du terrain.
	vec2 zone_uv = (v_world_pos.xz - zone_origin_xz) / zone_size_m + 0.5;
	float ripple = texture(ripple_buffer, zone_uv).r;
	ripple *= step(0.0, zone_uv.x) * step(zone_uv.x, 1.0)
	        * step(0.0, zone_uv.y) * step(zone_uv.y, 1.0);

	// 4. FRESNEL ---------------------------------------------------------------
	float ndv = clamp(dot(normalize(NORMAL), normalize(VIEW)), 0.0, 1.0);
	float fres = fresnel_bias + (1.0 - fresnel_bias) * pow(1.0 - ndv, fresnel_power);

	// 5. REFLEXION PANORAMIQUE -------------------------------------------------
	// Normale perturbee -> direction reflechie -> UV equirectangulaire.
	vec3 n_ws = normalize(mix(NORMAL, NORMAL + n_ts * 0.35, 1.0));
	vec3 refl = reflect(-normalize(VIEW), n_ws);
	vec2 pano_uv = vec2(
		atan(refl.x, refl.z) * 0.1591549 + 0.5,
		acos(clamp(refl.y, -1.0, 1.0)) * 0.3183099
	);
	vec3 sky = textureLod(sky_pano, pano_uv, sky_blur_lod).rgb * sky_intensity;

	// 6. CHALEUR ---------------------------------------------------------------
	float pulse = sin(t * heat_hz * 6.2831853) * 0.5 + 0.5;
	vec3  heat  = mix(heat_cold, heat_warm, pulse) * heat_amount;

	// 7. COMPOSITION -----------------------------------------------------------
	vec3 base = albedo_deep;
	base = mix(base, inclusion_color, inc_mask * inclusion_tint);

	ALBEDO    = base;
	METALLIC  = metallic_const;
	// Les rides "polissent" le verre localement : une trace est plus lisse,
	// donc plus reflechissante. C'est comme ca qu'on les voit sans les dessiner.
	ROUGHNESS = clamp(roughness_const - ripple * ripple_strength * 0.05, 0.01, 1.0);
	SPECULAR  = mix(0.35, 0.95, fres);

	// L'emission porte trois choses, dans cet ordre d'importance :
	//   le ciel reflechi (fresnel), la chaleur, et le cyan des rides.
	EMISSION = sky * fres
	         + heat
	         + maree_cyan * ripple * 0.035;
	ALPHA = 1.0;
}
