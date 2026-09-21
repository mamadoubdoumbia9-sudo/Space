// =============================================================================
// skybox_velmora.gdshader — SHADER 09 / 10
// Reference maitre : BLOC 05.25 · 05.27 (une source dominante par zone)
//                    · BLOC 19 (sequence finale) · 01.xx (Phare 212 m).
// -----------------------------------------------------------------------------
// LE CIEL DE VELMORA, ET DEDANS, LE PHARE.
//
//   3 couches de nuages (curl noise anime a 3 vitesses), un soleil bas a
//   12 degres dont le DISQUE N'EST PAS ICI (quad additif separe, 05.25), et
//   la silhouette du Phare.
//
// [OBL] LE PHARE EST LA BOUSSOLE EMOTIONNELLE DU CHAPITRE.
//   Il est visible depuis 100 % des zones exterieures. Pas 98 %. 100 %.
//   Job CI `lighthouse-visibility` : un raycast depuis 41 points de controle
//   du chemin principal doit atteindre le sommet du Phare.
//
// [OBL] LE RACCORD INVISIBLE A 400 M (05.25)
//   Le Phare existe DEUX FOIS : ici, peint dans le skybox, et en geometrie
//   reelle quand le joueur s'en approche. `lighthouse_fade_m` pilote le
//   fondu croise. La version skybox s'eteint exactement quand la geometrie
//   atteint sa silhouette pleine. Le joueur ne doit jamais voir les deux,
//   ni aucun des deux disparaitre.
//   Job CI `lighthouse-seam` : capture a 380 / 400 / 420 m, diff SSIM > 0,97
//   entre images consecutives.
//
// [OBL] LE PHARE N'EST JAMAIS ALLUME. Interdit absolu (21.08).
//   `lighthouse_lamp` existe dans ce fichier et vaut 0.0. Il est cable a
//   rien. Il est la pour qu'on comprenne, en lisant le code dans dix ans,
//   que quelqu'un y a pense et a decide que non.
//
// BUDGET
//   ALU ......... 96 instructions (plafond sky shader : 120)
//   Samples ..... 5 (3 couches de nuages + gradient + silhouette)
//   Resolution .. le SubViewport du ciel tourne a 0,5x, mis a jour 1 frame
//                 sur 2 (`sky/process_mode = REALTIME`, `radiance 128`).
// =============================================================================

shader_type sky;

// --- GRADIENT DE BASE --------------------------------------------------------
// Une seule source dominante (05.27). Le gradient est donc asymetrique :
// plus clair derriere-gauche, la ou est le soleil.
uniform vec3 sky_top : source_color = vec3(0.298, 0.400, 0.510);
uniform vec3 sky_horizon : source_color = vec3(0.729, 0.741, 0.702);
uniform vec3 ground_color : source_color = vec3(0.196, 0.204, 0.212);
uniform float horizon_sharpness : hint_range(0.5, 12.0) = 3.1;

// --- SOLEIL (SANS DISQUE) ----------------------------------------------------
// 05.25 : le disque est un quad additif separe. Ici on ne fait que le halo,
// parce qu'un disque dans un sky shader a 0,5x de resolution est un carre.
uniform vec3  sun_dir = vec3(-0.42, 0.208, 0.88);  // 12 deg au-dessus de l'horizon
uniform vec3  sun_color : source_color = vec3(1.0, 0.878, 0.729);
uniform float halo_gain : hint_range(0.0, 6.0) = 2.4;
uniform float halo_power : hint_range(1.0, 64.0) = 14.0;

// --- LES TROIS COUCHES DE NUAGES ---------------------------------------------
// Curl noise pre-calcule en texture. Trois vitesses : le ciel de Velmora
// bouge lentement en haut et vite en bas, ce qui donne une profondeur que
// personne ne sait nommer.
uniform sampler2D cloud_curl : filter_linear_mipmap, repeat_enable;

uniform float layer1_scale : hint_range(0.1, 8.0) = 0.65;   // cirrus hauts
uniform float layer1_speed : hint_range(0.0, 0.2) = 0.004;
uniform float layer1_cover : hint_range(0.0, 1.0) = 0.42;

uniform float layer2_scale : hint_range(0.1, 8.0) = 1.45;   // masse moyenne
uniform float layer2_speed : hint_range(0.0, 0.2) = 0.011;
uniform float layer2_cover : hint_range(0.0, 1.0) = 0.58;

uniform float layer3_scale : hint_range(0.1, 8.0) = 3.10;   // trainees basses
uniform float layer3_speed : hint_range(0.0, 0.2) = 0.027;
uniform float layer3_cover : hint_range(0.0, 1.0) = 0.31;

uniform vec3  cloud_lit : source_color = vec3(0.976, 0.949, 0.902);
uniform vec3  cloud_shadow : source_color = vec3(0.435, 0.463, 0.502);

// --- LE PHARE ----------------------------------------------------------------
// Silhouette alpha, cadree exactement sur l'azimut de la geometrie reelle.
uniform sampler2D lighthouse_silhouette : hint_default_transparent,
                                          filter_linear_mipmap;
uniform float lighthouse_azimuth : hint_range(-3.15, 3.15) = 1.047;  // radians
uniform float lighthouse_angular_w : hint_range(0.01, 1.0) = 0.088;
uniform float lighthouse_angular_h : hint_range(0.01, 1.0) = 0.211;
uniform float lighthouse_base_elev : hint_range(-0.5, 0.5) = -0.012;
uniform vec3  lighthouse_tint : source_color = vec3(0.243, 0.259, 0.278);

// Fondu croise avec la geometrie reelle. 1.0 = skybox seule (joueur loin),
// 0.0 = geometrie seule (joueur a moins de 400 m). Pousse par
// `_controllers/lighthouse_handoff.gd`.
uniform float lighthouse_fade : hint_range(0.0, 1.0) = 1.0;

// [OBL] IL NE S'ALLUME PAS. 21.08. Ce uniform vaut 0.0 et n'est cable a rien.
uniform float lighthouse_lamp : hint_range(0.0, 1.0) = 0.0;

// --- BRUME D'HORIZON ---------------------------------------------------------
uniform vec3  haze_color : source_color = vec3(0.780, 0.788, 0.769);
uniform float haze_gain : hint_range(0.0, 2.0) = 0.86;

// -----------------------------------------------------------------------------
// Echantillonne une couche de nuages par projection planaire sur la voute.
// Pas de raymarch : a 0,5x de resolution, personne ne voit la difference et
// on economise 60 instructions.
float cloud_layer(vec3 dir, float scale, float speed, float cover, float t) {
	// Projection : on divise par y pour etaler la couche vers l'horizon.
	float y = max(dir.y, 0.035);
	vec2 uv = dir.xz / y * scale * 0.18;
	uv += vec2(t * speed, t * speed * 0.42);

	float a = texture(cloud_curl, fract(uv)).r;
	float b = texture(cloud_curl, fract(uv * 2.13 + vec2(0.37, 0.71))).r;
	float n = a * 0.68 + b * 0.32;

	// `cover` remappe le seuil : c'est le reglage meteo de chaque zone.
	float d = smoothstep(1.0 - cover, 1.0 - cover + 0.32, n);
	// Fondu a l'horizon, sinon la projection planaire explose.
	d *= smoothstep(0.0, 0.14, dir.y);
	return d;
}

void sky() {
	vec3 dir = normalize(EYEDIR);
	vec3 sdir = normalize(sun_dir);
	float t = TIME;

	// 1. GRADIENT ---------------------------------------------------------------
	float up = clamp(dir.y, -1.0, 1.0);
	float sky_t = pow(clamp(up, 0.0, 1.0), 1.0 / horizon_sharpness);
	vec3 col = mix(sky_horizon, sky_top, sky_t);
	// Sous l'horizon : pas de sol reel dans le skybox, juste une valeur sourde.
	col = mix(col, ground_color, smoothstep(0.0, -0.06, up));

	// 2. HALO SOLAIRE (pas de disque, 05.25) ------------------------------------
	float sun_cos = clamp(dot(dir, sdir), 0.0, 1.0);
	float halo = pow(sun_cos, halo_power);
	col += sun_color * halo * halo_gain * 0.22;
	// Diffusion large : c'est elle qui donne l'heure qu'il est.
	col += sun_color * pow(sun_cos, 2.2) * 0.13;

	// 3. NUAGES -----------------------------------------------------------------
	float c1 = cloud_layer(dir, layer1_scale, layer1_speed, layer1_cover, t);
	float c2 = cloud_layer(dir, layer2_scale, layer2_speed, layer2_cover, t);
	float c3 = cloud_layer(dir, layer3_scale, layer3_speed, layer3_cover, t);

	// Eclairage des nuages : un seul terme, oriente soleil. Les nuages du cote
	// du soleil sont blancs, ceux a l'oppose sont gris-bleu. C'est tout ce
	// qu'il faut.
	float lit_t = clamp(dot(dir, sdir) * 0.5 + 0.5, 0.0, 1.0);
	vec3 cloud_col = mix(cloud_shadow, cloud_lit, lit_t);

	// Composition de haut en bas : les cirrus derriere, les trainees devant.
	col = mix(col, cloud_col * 1.04, c1 * 0.55);
	col = mix(col, cloud_col, c2 * 0.82);
	col = mix(col, cloud_col * 0.94, c3 * 0.66);

	// 4. BRUME D'HORIZON --------------------------------------------------------
	float haze = pow(1.0 - clamp(abs(up) * 3.4, 0.0, 1.0), 2.0);
	col = mix(col, haze_color, haze * haze_gain * 0.55);

	// 5. LE PHARE ---------------------------------------------------------------
	// Coordonnees angulaires autour de son azimut. Pas de projection cube :
	// une seule silhouette plate, cadree au degre pres sur la geometrie.
	float az = atan(dir.x, dir.z);
	float daz = az - lighthouse_azimuth;
	// Repli sur [-PI, PI] sans branche.
	daz -= 6.28318530 * floor(daz / 6.28318530 + 0.5);

	vec2 luv = vec2(
		daz / lighthouse_angular_w * 0.5 + 0.5,
		(dir.y - lighthouse_base_elev) / lighthouse_angular_h
	);
	// Hors cadre = rien. step, pas de branche.
	float inside = step(0.0, luv.x) * step(luv.x, 1.0)
	             * step(0.0, luv.y) * step(luv.y, 1.0);

	vec4 lh = texture(lighthouse_silhouette, clamp(luv, 0.0, 1.0));
	float lh_a = lh.a * inside * lighthouse_fade;

	// La silhouette prend la brume : plus elle est loin, plus elle se dilue.
	// C'est ce qui rend le raccord invisible a 400 m.
	vec3 lh_col = mix(lighthouse_tint, haze_color, haze * 0.62);
	col = mix(col, lh_col, lh_a);

	// [OBL] La lampe. Elle vaut 0.0. Elle n'est cablee a rien.
	//       Le Phare de Velmora ne s'allume pas dans le chapitre 1.
	//       Ne pas connecter ce uniform. Job CI `lighthouse-dark`.
	col += vec3(0.0) * lighthouse_lamp;

	COLOR = col;
}
