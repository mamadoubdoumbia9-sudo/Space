// =============================================================================
// figure_body.gdshader — SHADER 02 / 10
// Reference maitre : BLOC 05.18. Corps des Figures (ennemis de verre).
// -----------------------------------------------------------------------------
// ROLE
//   Verre translucide avec une "ame" a l'interieur. Le shader porte TOUT le
//   langage de combat du jeu : le joueur lit la fenetre de riposte sur le
//   CORPS de l'ennemi, jamais sur une interface.
//
// BUDGET
//   ALU fragment .......... 71 instructions
//   Textures .............. 4 samples (normal, fissures, noise3d slice, matcap)
//   Branches dynamiques ... 0
//
// LES TROIS ETATS, ET CE QU'ILS DISENT AU JOUEUR
//   1. NORMAL    — le coeur est bas, dans le ventre, a peine visible.
//   2. STAGGERED — LE COEUR REMONTE DANS LA POITRINE ET DEVIENT VISIBLE.
//      [OBL] C'est le SEUL tell de la fenetre de riposte du jeu. Il n'y a
//      aucun indicateur d'interface, aucun contour, aucun son dedie.
//      Job CI `no-parry-ui` : si un element d'UI apparait a ce moment, echec.
//   3. SHATTER   — a 100 % de dommage, le mesh est remplace par 47 fragments
//      physiques (pool preinstancie). Ce shader gere la derniere frame avant
//      la substitution : dissolve directionnel complet.
//
// INTERDIT [OBL]
//   · aucune barre de vie, ici ou ailleurs
//   · aucun chiffre de degats
//   · l'AMBRE est interdite : les Figures sont CYAN (#6BF2D8). L'ambre
//     appartient a Esteban. `amber-guard`.
// =============================================================================

shader_type spatial;
render_mode blend_mix, cull_back, depth_prepass_alpha, diffuse_burley,
            specular_schlick_ggx;

// --- CORPS DE VERRE ----------------------------------------------------------
uniform vec3  glass_tint   : source_color = vec3(0.129, 0.184, 0.216);
uniform float glass_alpha  : hint_range(0.2, 1.0) = 0.74;
uniform float roughness_const : hint_range(0.0, 1.0) = 0.13;
uniform sampler2D normal_map : hint_normal, filter_linear_mipmap;

// --- SUBSURFACE APPROXIME (05.18) --------------------------------------------
// Wrap-lighting a 0.4. Pas de vrai SSS : trop cher, et inutile sur du verre.
uniform float wrap_amount : hint_range(0.0, 1.0) = 0.40;

// --- RIM CYAN ----------------------------------------------------------------
uniform vec3  rim_color : source_color = vec3(0.4196, 0.9490, 0.8471);
uniform float rim_power : hint_range(0.5, 8.0) = 2.85;
uniform float rim_gain  : hint_range(0.0, 4.0) = 1.15;

// --- LE COEUR (05.18) --------------------------------------------------------
// Le second mesh interne (echelle 0.82) porte `figure_core.gdshader`.
// Ce que fait CE shader, c'est laisser passer sa lumiere selon l'etat :
// on module la transparence locale de la poitrine.
uniform float core_reveal : hint_range(0.0, 1.0) = 0.0;   // 0 = ventre, 1 = poitrine
uniform vec3  core_chest_local = vec3(0.0, 1.22, 0.06);   // repere du mesh
uniform float core_radius : hint_range(0.05, 1.2) = 0.46;

// --- DOMMAGE ET FISSURES -----------------------------------------------------
// Canal pilote par le gameplay (base_figure.gd -> set_shader_parameter).
uniform sampler2D crack_tex : hint_default_black, filter_linear_mipmap;
uniform float damage : hint_range(0.0, 1.0) = 0.0;
uniform vec3  crack_emit : source_color = vec3(0.4196, 0.9490, 0.8471);
// Dissolve DIRECTIONNEL : les fissures se propagent depuis le point d'impact,
// pas uniformement. C'est ce qui rend les combats lisibles.
uniform vec3  impact_local = vec3(0.0, 1.0, 0.0);
uniform float dissolve_width : hint_range(0.01, 0.5) = 0.12;

varying vec3 v_local_pos;

void vertex() {
	v_local_pos = VERTEX;
}

void fragment() {
	// 1. VERRE -----------------------------------------------------------------
	vec3 n = texture(normal_map, UV).xyz * 2.0 - 1.0;
	NORMAL_MAP = n * 0.5 + 0.5;

	// 2. RIM -------------------------------------------------------------------
	float ndv = clamp(dot(normalize(NORMAL), normalize(VIEW)), 0.0, 1.0);
	float rim = pow(1.0 - ndv, rim_power) * rim_gain;

	// 3. FENETRE DU COEUR ------------------------------------------------------
	// Une sphere douce autour de la poitrine. Quand `core_reveal` monte,
	// le verre devient localement plus transparent : on voit l'ame.
	float d_chest = distance(v_local_pos, core_chest_local);
	float chest_window = 1.0 - smoothstep(core_radius * 0.55, core_radius, d_chest);
	chest_window *= core_reveal;

	// 4. FISSURES --------------------------------------------------------------
	// Propagation depuis l'impact. `damage` pousse un front spherique.
	float d_impact = distance(v_local_pos, impact_local);
	float front = smoothstep(damage * 2.6 + dissolve_width,
	                         damage * 2.6 - dissolve_width, d_impact);
	float cracks = texture(crack_tex, UV * 2.0).r * front;

	// 5. SORTIE ----------------------------------------------------------------
	ALBEDO    = glass_tint;
	ROUGHNESS = mix(roughness_const, 0.55, cracks);
	METALLIC  = 0.0;
	SPECULAR  = 0.62;
	RIM       = 0.0;   // on fait le rim a la main dans EMISSION, moins cher
	// Le wrap-lighting est applique dans light(), pas ici.
	EMISSION  = rim_color * rim
	          + crack_emit * cracks * 1.9
	          + rim_color * chest_window * 0.85;

	// Le verre s'ouvre a la poitrine quand le coeur remonte, et se dissout
	// la ou il craque. Deux raisons, un seul canal alpha.
	ALPHA = clamp(glass_alpha - chest_window * 0.42 - cracks * 0.55, 0.0, 1.0);
}

void light() {
	// Wrap-lighting 0.4 : la lumiere "tourne" autour du volume, ce qui imite
	// la diffusion du verre epais pour le prix d'un mix().
	float ndl = dot(NORMAL, LIGHT);
	float wrapped = clamp((ndl + wrap_amount) / (1.0 + wrap_amount), 0.0, 1.0);
	DIFFUSE_LIGHT += ALBEDO * LIGHT_COLOR * ATTENUATION * wrapped;

	// Specular Blinn court : les Figures brillent peu, elles absorbent.
	vec3 H = normalize(LIGHT + VIEW);
	float ndh = clamp(dot(NORMAL, H), 0.0, 1.0);
	float spec = pow(ndh, mix(96.0, 8.0, ROUGHNESS)) * 0.35;
	SPECULAR_LIGHT += LIGHT_COLOR * ATTENUATION * spec;
}
