// =============================================================================
// glass_cheap.gdshader — VARIANTE HERITAGE de SHADER 01
// Reference maitre : 02.03 (mode Heritage) et 05.17.
// -----------------------------------------------------------------------------
// Active AUTOMATIQUEMENT si `RenderingDevice` est indisponible (OpenGL ES 3.0).
// Meme jeu d'uniforms que `maree_glass.gdshader` pour que le swap de materiau
// n'exige aucun code de migration.
//
// CE QUI TOMBE : parallax des inclusions, buffer de rides, seconde normal map.
// CE QUI RESTE : le fresnel 4.2 et le ciel panoramique — c'est-a-dire la
// LECTURE DE LA HAUTEUR. [OBL] Un joueur en mode Heritage doit toujours voir
// le gouffre par en haut et le ciel par en bas. Le reste est du confort.
//
// BUDGET : 31 instructions ALU, 2 samples.
// =============================================================================

shader_type spatial;
render_mode specular_schlick_ggx, cull_back, diffuse_lambert, specular_toon;

uniform vec3  albedo_deep     : source_color = vec3(0.0196, 0.0275, 0.0471);
uniform float roughness_const : hint_range(0.0, 0.5) = 0.06;
uniform sampler2D normal_a : hint_normal, filter_linear_mipmap, repeat_enable;
uniform vec2  normal_a_speed = vec2(0.0040, 0.0012);
uniform float normal_a_scale : hint_range(0.05, 4.0) = 0.35;
uniform float breathe_amp    : hint_range(0.0, 0.08) = 0.06;
uniform sampler2D sky_pano : source_color, filter_linear_mipmap, repeat_enable;
uniform float sky_intensity : hint_range(0.0, 4.0) = 1.35;
uniform float fresnel_power : hint_range(1.0, 8.0) = 4.2;
uniform float fresnel_bias  : hint_range(0.0, 0.2) = 0.02;

void fragment() {
	vec2 uv_a = UV * normal_a_scale + normal_a_speed * TIME;
	vec3 na = texture(normal_a, uv_a).xyz * 2.0 - 1.0;
	vec3 n_ts = normalize(vec3(na.xy * breathe_amp, 1.0));
	NORMAL_MAP = n_ts * 0.5 + 0.5;

	float ndv  = clamp(dot(normalize(NORMAL), normalize(VIEW)), 0.0, 1.0);
	float fres = fresnel_bias + (1.0 - fresnel_bias) * pow(1.0 - ndv, fresnel_power);

	vec3 refl = reflect(-normalize(VIEW), normalize(NORMAL));
	vec2 pano_uv = vec2(
		atan(refl.x, refl.z) * 0.1591549 + 0.5,
		acos(clamp(refl.y, -1.0, 1.0)) * 0.3183099
	);
	vec3 sky = texture(sky_pano, pano_uv).rgb * sky_intensity;

	ALBEDO    = albedo_deep;
	ROUGHNESS = roughness_const;
	METALLIC  = 0.0;
	SPECULAR  = mix(0.35, 0.95, fres);
	EMISSION  = sky * fres;
}
