// =============================================================================
// echo_reveal.gdshader — SHADER 03 / 10
// Reference maitre : BLOC 05.19 · BLOC 11 (regles des Echos) · INDEX § 4.
// -----------------------------------------------------------------------------
// ROLE
//   Post-process plein ecran de la lecture de memoire. Se joue 31 fois dans
//   le chapitre, toujours identiquement.
//
// [OBL] LA DUREE DU RITUEL EST DE 2,44 s, TRENTE ET UNE FOIS, SANS EXCEPTION.
//   Ce shader ne connait pas la duree : il recoit `reveal` de 0 a 1 par le
//   controleur `echo_system.gd`. Mais il est INTERDIT d'ajouter ici le
//   moindre easing qui allongerait la sensation. La courbe est lineaire cote
//   controleur et le shader la respecte au pixel.
//   Job CI `ritual-duration` : hash de la courbe compare aux 30 autres.
//
// [OBL] EN S8, CE SHADER N'EST PAS JOUE. Le rituel de la lettre (S8/D04)
//   utilise la MEME interaction, le MEME glyphe, la MEME duree, et
//   `reveal` reste a 0.0 pendant les 2,44 s. Rien ne se passe. C'est le
//   centre du chapitre et ca se code en ne faisant rien.
//   Job CI `no-echo-on-letter`.
//
// BUDGET
//   ALU ................... 58 instructions
//   Textures .............. 3 samples (SCREEN, history, grain) + 1 depth
//   Passe ................. 1 quad plein ecran, apres tonemap
// =============================================================================

shader_type spatial;
render_mode unshaded, blend_mix, cull_disabled, depth_draw_never,
            depth_test_disabled, fog_disabled;

uniform sampler2D screen_tex : hint_screen_texture, filter_linear;
uniform sampler2D depth_tex  : hint_depth_texture,  filter_nearest;

// --- HISTORIQUE (05.19) ------------------------------------------------------
// Buffer de 6 frames de retard, alimente par `echo_history_buffer.gd`.
// Les BORDS du masque montrent la scene en retard : ca cree un liseret
// fantome et ca coute presque rien. C'est le plus beau detail du shader.
uniform sampler2D history_tex : hint_default_black, filter_linear;
uniform float history_edge_width : hint_range(0.01, 0.4) = 0.085;

// --- MASQUE RADIAL (05.19) ---------------------------------------------------
// Part de LA MAIN DE LOHEN, pas du centre de l'ecran.
// S'etend a 3,5 m/s jusqu'a 18 m. Le controleur convertit metres -> rayon
// normalise et pousse `reveal`.
uniform vec2  hand_screen_uv = vec2(0.5, 0.62);
uniform float reveal : hint_range(0.0, 1.0) = 0.0;
uniform float max_radius_uv : hint_range(0.1, 3.0) = 1.65;
uniform float aspect : hint_range(0.5, 3.0) = 2.05;

// --- ETALONNAGE DU SOUVENIR --------------------------------------------------
uniform float desaturate : hint_range(0.0, 1.0) = 0.85;   // 85 % (05.19)
uniform float grain_amount : hint_range(0.0, 0.5) = 0.12; // 0.12 (05.19)
uniform float vignette_amount : hint_range(0.0, 1.0) = 0.55;
uniform sampler2D grain_tex : hint_default_white, filter_linear, repeat_enable;
uniform float grain_scale : hint_range(1.0, 64.0) = 18.0;

// --- LISERET -----------------------------------------------------------------
uniform vec3  edge_color : source_color = vec3(0.4196, 0.9490, 0.8471);
uniform float edge_gain  : hint_range(0.0, 4.0) = 0.9;

// [OBL] AUCUN UNIFORM D'AMBRE ICI. Le liseret ambre appartient a
// `memory_person.gdshader` et UNIQUEMENT a Esteban. `amber-guard`.

void fragment() {
	vec2 uv = SCREEN_UV;
	vec3 src = texture(screen_tex, uv).rgb;

	// 1. MASQUE RADIAL DEPUIS LA MAIN ------------------------------------------
	vec2 d = uv - hand_screen_uv;
	d.x *= aspect;
	float dist = length(d);
	float radius = reveal * max_radius_uv;
	// Interieur du masque = souvenir. Exterieur = realite intacte.
	float inside = 1.0 - smoothstep(radius - history_edge_width,
	                                radius + history_edge_width, dist);

	// 2. BORD : LA SCENE AVEC 6 FRAMES DE RETARD -------------------------------
	float edge = inside * (1.0 - inside) * 4.0;          // pic sur la frontiere
	vec3 hist = texture(history_tex, uv).rgb;

	// 3. DESATURATION DE LA REALITE --------------------------------------------
	float luma = dot(src, vec3(0.2126, 0.7152, 0.0722));
	vec3 gray = vec3(luma);
	vec3 memory = mix(src, gray, desaturate);

	// Le bord melange l'historique : liseret fantome.
	memory = mix(memory, hist, edge * 0.65);

	// 4. GRAIN ------------------------------------------------------------------
	// Grain FIXE dans l'espace ecran, pas anime : un souvenir n'est pas une
	// pellicule qui tourne. [OBL] Ne jamais l'animer avec TIME.
	float g = texture(grain_tex, uv * grain_scale).r - 0.5;
	memory += g * grain_amount * inside;

	// 5. VIGNETTE ---------------------------------------------------------------
	vec2 vd = (uv - 0.5) * vec2(aspect, 1.0);
	float vig = 1.0 - smoothstep(0.35, 0.95, length(vd)) * vignette_amount;
	memory *= mix(1.0, vig, inside);

	// 6. COMPOSITION ------------------------------------------------------------
	vec3 outc = mix(src, memory, inside);
	outc += edge_color * edge * edge_gain * reveal;

	ALBEDO = vec3(0.0);
	EMISSION = outc;
	ALPHA = 1.0;
}
