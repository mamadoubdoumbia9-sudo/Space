// =============================================================================
// memory_person.gdshader — compagnon de SHADER 03
// Reference maitre : BLOC 05.19 · BLOC 11 (les 31 Echos).
// -----------------------------------------------------------------------------
// LES GENS DES SOUVENIRS.
//   Silhouette pleine, albedo uni, contour additif, PAS D'OMBRE PROJETEE.
//   [OBL] Ce sont des presences, pas des corps. Aucun detail de visage n'est
//   jamais rendu : le materiau est plat par construction, meme si le mesh a
//   des UV et une texture, elle n'est pas echantillonnee.
//
// [OBL] LES FANTOMES NE REGARDENT JAMAIS LOHEN. Une seule exception dans
//   tout le chapitre : E23, la femme de la place de l'Ancienne Halle
//   (S7_ECH_L204..L211). Ce shader ne gere pas le regard — c'est l'animation
//   qui le fait — mais `is_e23` existe ici pour une raison : E23 est le seul
//   fantome dont le contour ne faiblit PAS avec l'angle de vue. Elle est
//   aussi presente de face que de profil. Personne ne le remarquera.
//
// [OBL] ESTEBAN A UN LISERET AMBRE. TOUJOURS. C'est la seule utilisation
//   autorisee de #FFA33C sur un personnage. `amber-guard` verifie que
//   `rim_color` ne vaut `amber_esteban` que sur les materiaux dont le nom
//   contient `esteban`.
//
// [OBL] AUCUN ECHO NE MONTRE ESTEBAN (regle du chapitre 1). Ce chemin de
//   code n'est donc emprunte que par E21 (la salle de bal, S6), ou Esteban
//   n'est PAS un fantome : il est vivant dans le souvenir joue. Le liseret
//   ambre y est desactive (`rim_gain = 0.0`) et la couleur sert uniquement
//   a la lampe du Phare en S8. C'est documente ici pour que personne ne
//   "corrige" l'absence de liseret en S6.
//
// BUDGET : 19 instructions, 0 sample.
// =============================================================================

shader_type spatial;
render_mode unshaded, blend_mix, cull_back, depth_draw_opaque, shadows_disabled;

uniform vec3  silhouette_color : source_color = vec3(0.682, 0.729, 0.769);
uniform float silhouette_alpha : hint_range(0.0, 1.0) = 0.86;

// Contour additif. Cyan par defaut (couleur de la Maree).
uniform vec3  rim_color : source_color = vec3(0.4196, 0.9490, 0.8471);
uniform float rim_power : hint_range(0.5, 8.0) = 2.2;
uniform float rim_gain  : hint_range(0.0, 4.0) = 1.4;

// #FFA33C. [OBL] Reserve a Esteban. Ne jamais assigner ailleurs.
uniform vec3  amber_esteban : source_color = vec3(1.0, 0.639, 0.235);
uniform float is_esteban : hint_range(0.0, 1.0) = 0.0;

// E23 : contour constant quel que soit l'angle.
uniform float is_e23 : hint_range(0.0, 1.0) = 0.0;

// Dissolution. Le controleur la pousse de 0 a 1 en fin d'Echo.
// [OBL] Aucun Echo n'est rejouable : une fois `dissolve` a 1, le noeud est
// libere et le flag est ecrit. Voir `echo_system.gd`.
uniform float dissolve : hint_range(0.0, 1.0) = 0.0;

void fragment() {
	float ndv = clamp(dot(normalize(NORMAL), normalize(VIEW)), 0.0, 1.0);
	float rim = pow(1.0 - ndv, rim_power);
	// E23 : on ecrase la dependance a l'angle.
	rim = mix(rim, 0.72, is_e23);

	vec3 contour = mix(rim_color, amber_esteban, is_esteban);

	ALBEDO   = vec3(0.0);
	EMISSION = silhouette_color * 0.35 + contour * rim * rim_gain;
	ALPHA    = clamp(silhouette_alpha * (1.0 - dissolve), 0.0, 1.0);
}
