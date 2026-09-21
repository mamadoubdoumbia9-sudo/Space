// =============================================================================
// grapple_cable.gdshader — SHADER 08 / 10
// Reference maitre : BLOC 05.24 · 03.xx (« anti_alias analytique propre »)
//                    · S7/D01 (le cable casse) · S7/D08 + S8/D01 (Sol le repare).
// -----------------------------------------------------------------------------
// LE CABLE DU GRAPPIN.
//   Rendu en `ImmediateMesh` reconstruit chaque frame le long d'une catenaire
//   de 32 segments (voir `grapple_cable_mesh.gd`). Ce shader ne calcule PAS la
//   courbe : il habille le ruban.
//
// ANTI-ALIASING ANALYTIQUE (05.24)
//   Un cable de 6 mm a 40 m, c'est un sous-pixel. Sans AA analytique il
//   scintille et l'image entiere devient sale. On mesure la distance au
//   centre du segment EN SCREEN-SPACE et on module l'alpha avec `fwidth`.
//   C'est la seule facon propre, et c'est 5 instructions.
//
// LA TORSADE
//   Le cable a une torsade visible qui TOURNE QUAND ON TIRE. Ce n'est pas
//   decoratif : c'est le seul retour visuel de tension du jeu. Il n'y a
//   aucune jauge, aucun son de tension, aucune vibration continue.
//
// [OBL] APRES S7, LE CABLE EST PLUS COURT DE QUATRE METRES.
//   Sol le repare avec un epissage a quatre torons (S8_SOL_L007). La portee
//   du grappin est REELLEMENT reduite pour tout S8, et le level design de S8
//   est concu pour la portee courte. `splice_uv` marque l'endroit de
//   l'epissage : une zone de 11 cm ou la torsade change de pas.
//   [OBL] Le joueur ne le remarque pas : il n'a jamais mesure.
//   Job CI `cable-splice-s8` : la marque doit etre presente dans toutes les
//   scenes de S8 et absente avant.
//
// BUDGET : 27 instructions, 1 sample.
// =============================================================================

shader_type spatial;
render_mode blend_mix, cull_disabled, depth_draw_opaque, diffuse_lambert,
            specular_schlick_ggx, shadows_disabled;

uniform sampler2D cable_strands : source_color, filter_linear_mipmap, repeat_enable;
uniform vec3  cable_color : source_color = vec3(0.184, 0.176, 0.161);
uniform float roughness_const : hint_range(0.0, 1.0) = 0.68;

// --- TORSADE -----------------------------------------------------------------
uniform float twist_pitch : hint_range(1.0, 200.0) = 48.0;   // torsades par metre d'UV
uniform float twist_speed : hint_range(0.0, 8.0) = 0.0;      // pousse par la tension
uniform float tension : hint_range(0.0, 1.0) = 0.0;

// --- SCINTILLEMENT SOUS TENSION (05.24) --------------------------------------
// « Un leger scintillement quand il est sous tension. »
uniform float shimmer_gain : hint_range(0.0, 1.0) = 0.22;
uniform float shimmer_hz : hint_range(1.0, 60.0) = 23.0;

// --- L'EPISSAGE DE SOL -------------------------------------------------------
uniform float splice_uv : hint_range(0.0, 1.0) = -1.0;   // < 0 = pas d'epissage
uniform float splice_width : hint_range(0.005, 0.2) = 0.042;

// --- AA ANALYTIQUE -----------------------------------------------------------
uniform float cable_half_width : hint_range(0.001, 0.5) = 0.35;  // en UV.x du ruban
uniform float aa_softness : hint_range(0.5, 3.0) = 1.15;

void fragment() {
	// UV.x traverse le ruban (-1 a 1 apres remap), UV.y court le long du cable.
	float across = UV.x * 2.0 - 1.0;

	// 1. AA ANALYTIQUE ----------------------------------------------------------
	// fwidth donne la taille du pixel en espace UV : on s'en sert pour une
	// transition d'exactement un pixel, quelle que soit la distance.
	float d = abs(across);
	float px = fwidth(across) * aa_softness;
	float coverage = 1.0 - smoothstep(cable_half_width - px,
	                                  cable_half_width + px, d);

	// 2. TORSADE ----------------------------------------------------------------
	float pitch = twist_pitch;
	// L'epissage change le pas de la torsade sur 11 cm : quatre torons au lieu
	// de trois. C'est techniquement ce que Sol a fait.
	float in_splice = 1.0 - smoothstep(0.0, splice_width, abs(UV.y - splice_uv));
	in_splice *= step(0.0, splice_uv);
	pitch = mix(pitch, pitch * 1.333, in_splice);

	float twist_phase = UV.y * pitch + TIME * twist_speed * tension;
	vec2 strand_uv = vec2(fract(twist_phase), across * 0.5 + 0.5);
	float strands = texture(cable_strands, strand_uv).r;

	// 3. OMBRAGE CYLINDRIQUE ----------------------------------------------------
	// Le ruban est plat ; on lui invente un volume en courbant la normale.
	vec3 n_ts = normalize(vec3(across * 0.92, 0.0, 0.62));
	NORMAL_MAP = n_ts * 0.5 + 0.5;

	// 4. SCINTILLEMENT ----------------------------------------------------------
	float shimmer = sin(TIME * shimmer_hz + UV.y * 31.0) * 0.5 + 0.5;
	shimmer *= tension * shimmer_gain;

	// 5. SORTIE -----------------------------------------------------------------
	vec3 col = cable_color * mix(0.72, 1.18, strands);
	// L'epissage est legerement plus clair : le toron neuf n'a pas l'huile
	// des trois autres.
	col = mix(col, col * 1.28, in_splice * 0.6);

	ALBEDO    = col;
	ROUGHNESS = mix(roughness_const, 0.42, shimmer);
	METALLIC  = 0.12;
	SPECULAR  = 0.35 + shimmer * 0.4;
	EMISSION  = vec3(shimmer * 0.06);
	ALPHA     = coverage;
}
