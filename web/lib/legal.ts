/** Textes légaux et avertissements affichés dans l'interface.
 *
 * Ils sont centralisés ici pour qu'une modification soit appliquée partout
 * (accueil, avant envoi, application Android) et pour rester hors des fichiers
 * de page — Next.js n'accepte qu'un composant de page par fichier.
 */

export const DISCLAIMER =
  "Tout faux signalement est passible de poursuites judiciaires et du bannissement de votre compte " +
  "WhatsApp. Ce service ne garantit pas la suspension des numéros signalés : seul Meta/WhatsApp examine " +
  "les signalements et décide de suspendre ou non un compte.";

export const NO_GUARANTEE_NOTE =
  "SignalPro ne peut pas suspendre un compte WhatsApp et ne promet aucun résultat. « Suspension confirmée » " +
  "signifie uniquement que Meta l'a notifiée ou qu'un modérateur l'a constatée.";

export const PRE_SEND_WARNING =
  "Avant d'envoyer : vérifiez que les faits sont exacts et que les preuves sont authentiques. Un signalement " +
  "sans preuve n'est jamais transmis, et un signalement jugé abusif après vérification entraîne un " +
  "avertissement puis le bannissement définitif de votre compte.";
