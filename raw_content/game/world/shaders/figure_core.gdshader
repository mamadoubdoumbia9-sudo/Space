// =============================================================================
// figure_core.gdshader — compagnon de SHADER 02
// Reference maitre : BLOC 05.18 ("un second mesh reduit a 0.82").
// -----------------------------------------------------------------------------
// L'AME de la Figure. Mesh interne, echelle 0.82, materiau emissif cyan
// anime par un bruit 3D. Il n'a pas d'ombre, pas de collision, pas de PBR.
//
// [OBL] Il est rendu APRES le corps (render_priority 1) et n'ecrit pas la
// profondeur : sans ca, il perce le verre au lieu de le remplir.
//
// BUDGET : 22 instructions, 1 sample 3D.
// =============================================================================

shader_type spatial;
render_mode unshaded, blend_add, cull_front, depth_draw_never, depth_test_disabled;

uniform sampler3D noise_3d : filter_linear_mipmap, repeat_enable;
uniform vec3  core_color : source_color = vec3(0.4196, 0.9490, 0.8471);
uniform float pulse_hz    : hint_range(0.1, 4.0) = 1.15;
uniform float noise_scale : hint_range(0.2, 8.0) = 2.40;
uniform float noise_speed : hint_range(0.0, 2.0) = 0.35;
uniform float core_gain   : hint_range(0.0, 6.0) = 2.10;
// Monte a 1.0 quand la Figure est staggered : le coeur remonte ET s'intensifie.
uniform float reveal : hint_range(0.0, 1.0) = 0.0;
// Deplacement vertical du coeur : ventre (0.0) -> poitrine (1.0).
uniform float rise_m : hint_range(0.0, 0.8) = 0.0;

varying vec3 v_local;

void vertex() {
	// Le coeur MONTE physiquement. Ce n'est pas un effet de lumiere :
	// le mesh se deplace, et le joueur voit une chose bouger dans un corps.
	VERTEX.y += rise_m;
	v_local = VERTEX;
}

void fragment() {
	vec3 p = v_local * noise_scale + vec3(0.0, TIME * noise_speed, 0.0);
	float n = texture(noise_3d, fract(p * 0.5 + 0.5)).r;

	float pulse = sin(TIME * pulse_hz * 6.2831853) * 0.5 + 0.5;
	float body  = mix(0.45, 1.0, n) * mix(0.55, 1.0, pulse);

	// Falloff sur la normale : le coeur est plus dense au centre.
	float ndv = clamp(dot(normalize(NORMAL), normalize(VIEW)), 0.0, 1.0);
	float core_falloff = pow(1.0 - ndv, 1.6);

	ALBEDO = vec3(0.0);
	EMISSION = core_color * body * core_falloff * core_gain * mix(0.22, 1.0, reveal);
	ALPHA = 1.0;
}
