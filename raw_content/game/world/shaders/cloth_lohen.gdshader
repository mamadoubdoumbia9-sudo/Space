// =============================================================================
// cloth_lohen.gdshader — SHADER 05 / 10
// Reference maitre : BLOC 05.21 · PN-147 (la veste posee sur Sol).
// -----------------------------------------------------------------------------
// LA VESTE DE RELAYEUR, MATRICULE 0114.
//   Tissu huile, anisotropie legere, normal map cousue a la main, et un
//   masque de salissure qui s'intensifie au fil du chapitre.
//
//   `GameState.chapter_progress` : 0.0 au reveil de S1, 1.0 au sommet du Phare.
//   [OBL] A la sequence 8, Lohen est visiblement sale, trempe, use.
//   [OBL] PERSONNE NE LE COMMENTE. Aucune ligne des 2 840 ne parle de l'etat
//   de sa veste. On le voit, et c'est tout. Job CI `no-dirt-dialogue`.
//
// TROIS CANAUX QUI RACONTENT TROIS CHOSES DIFFERENTES
//   1. `dirt`  — la poussiere. Monte lineairement avec la progression.
//   2. `soak`  — la pluie. Monte BRUTALEMENT a S7 et ne redescend pas.
//      La veste ne seche jamais dans ce chapitre.
//   3. `wear`  — l'usure aux coudes et a l'epaule DROITE.
//      [OBL] L'usure de l'epaule droite est asymetrique et permanente :
//      elle est deja a 0.6 au debut du jeu. L'epaule est abimee avant que
//      le joueur n'arrive. La lettre dira « depuis Saint-Jerome » et
//      Saint-Jerome ne sera jamais defini.
//
// LE MATRICULE
//   `0114` est brode sur la poitrine gauche. Il est dans la texture d'albedo,
//   pas en decal : il doit s'user avec le reste. C'est le seul texte lisible
//   sur un personnage du chapitre.
//
// BUDGET : 64 instructions, 5 samples.
// =============================================================================

shader_type spatial;
render_mode cull_back, diffuse_burley, specular_schlick_ggx;

uniform sampler2D cloth_albedo : source_color, filter_linear_mipmap;
uniform sampler2D cloth_normal : hint_normal,  filter_linear_mipmap;
uniform sampler2D cloth_orm    : hint_default_white, filter_linear_mipmap;
// R = poussiere, G = trempe, B = usure. Peint a la main sur l'UV de la veste.
uniform sampler2D wear_masks   : hint_default_black, filter_linear_mipmap;
uniform sampler2D dirt_detail  : hint_default_white, filter_linear_mipmap, repeat_enable;

// --- ANISOTROPIE (tissu huile) -----------------------------------------------
uniform float aniso_amount : hint_range(-1.0, 1.0) = 0.38;
uniform float base_roughness : hint_range(0.0, 1.0) = 0.62;

// --- PROGRESSION (05.21) -----------------------------------------------------
uniform float chapter_progress : hint_range(0.0, 1.0) = 0.0;
uniform float soak : hint_range(0.0, 1.0) = 0.0;   // pousse a 1.0 en S7, jamais remis a 0
uniform float shoulder_wear_right : hint_range(0.0, 1.0) = 0.6;

uniform vec3  dirt_color : source_color = vec3(0.263, 0.239, 0.208);
uniform vec3  soak_darken : source_color = vec3(0.47, 0.49, 0.52);
uniform float dirt_tiling : hint_range(1.0, 16.0) = 5.0;

void fragment() {
	vec4 base = texture(cloth_albedo, UV);
	vec3 orm  = texture(cloth_orm, UV).rgb;
	vec3 masks = texture(wear_masks, UV).rgb;

	// 1. POUSSIERE -------------------------------------------------------------
	// Le masque peint dit OU la poussiere s'accroche ; la progression dit
	// COMBIEN. Le detail tile casse la repetition du masque.
	float dirt_detail_v = texture(dirt_detail, UV * dirt_tiling).r;
	float dirt = masks.r * chapter_progress * mix(0.7, 1.3, dirt_detail_v);
	dirt = clamp(dirt, 0.0, 1.0);

	// 2. TREMPE ----------------------------------------------------------------
	// La pluie de S7 fonce le tissu et le rend plus specular. Elle s'accumule
	// dans le bas de la veste (masque G peint en degrade vertical).
	float wet = masks.g * soak;

	// 3. USURE -----------------------------------------------------------------
	// Coudes et epaule. L'epaule droite recoit un supplement permanent.
	float wear = clamp(masks.b + masks.b * shoulder_wear_right * 0.8, 0.0, 1.0);

	// 4. COMPOSITION -----------------------------------------------------------
	vec3 albedo = base.rgb;
	albedo = mix(albedo, dirt_color, dirt * 0.62);
	albedo = mix(albedo, albedo * soak_darken, wet);
	// L'usure eclaircit : le tissu huile perd son huile aux frottements.
	albedo = mix(albedo, albedo * 1.22 + 0.04, wear * 0.45);

	ALBEDO = albedo;

	vec3 n = texture(cloth_normal, UV).xyz * 2.0 - 1.0;
	NORMAL_MAP = n * 0.5 + 0.5;
	NORMAL_MAP_DEPTH = mix(1.0, 0.55, wear);   // l'usure lisse le tissage

	// Trempe = plus lisse. Poussiere = plus rugueux. Usure = plus rugueux.
	float r = mix(base_roughness, orm.g, 0.6);
	r = mix(r, 0.22, wet);
	r = clamp(r + dirt * 0.18 + wear * 0.12, 0.04, 1.0);
	ROUGHNESS = r;

	METALLIC   = 0.0;
	SPECULAR   = mix(0.42, 0.78, wet);
	AO         = orm.r;
	ANISOTROPY = aniso_amount * (1.0 - wear * 0.5);
	// Le tissu est tisse horizontalement : le flow suit la trame.
	ANISOTROPY_FLOW = vec2(1.0, 0.0);
}
