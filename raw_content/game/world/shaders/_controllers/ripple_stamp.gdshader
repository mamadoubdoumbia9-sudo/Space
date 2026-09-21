// =============================================================================
// ripple_stamp.gdshader — materiau d'accumulation du buffer de rides.
// Utilise UNIQUEMENT par `maree_ripple_buffer.gd`. Jamais dans une scene 3D.
// Ecrit un point gaussien additif dans un SubViewport 256x256 R8.
// BUDGET : 7 instructions. Appele une fois par contact, jamais par frame.
// =============================================================================
shader_type canvas_item;
render_mode blend_add, unshaded;

uniform vec2  stamp_uv = vec2(0.5, 0.5);
uniform float stamp_radius : hint_range(0.001, 0.2) = 0.0033;
uniform float stamp_strength : hint_range(0.0, 1.0) = 0.55;

void fragment() {
	float d = distance(UV, stamp_uv);
	// Gaussienne : un bord net ferait un disque, et un disque sur du verre
	// se lit comme un defaut de texture, pas comme une trace de main.
	float g = exp(-(d * d) / max(stamp_radius * stamp_radius * 0.5, 1e-6));
	COLOR = vec4(vec3(g * stamp_strength), 1.0);
}
