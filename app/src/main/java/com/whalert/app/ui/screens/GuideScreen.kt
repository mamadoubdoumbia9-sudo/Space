package com.whalert.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ÉCRAN 6 — GUIDE DE SÉCURITÉ & CYBERDÉFENSE
 *
 * Explique précisément :
 * 1. Reconnaître une arnaque (tactiques d'urgence, faux numéros de proches, liens frauduleux)
 * 2. Reconnaître un compte frauduleux (profils récents, incohérences d'indicatif)
 * 3. Conserver loyalement les preuves (captures, métadonnées, horodatage)
 * 4. Ne pas menacer ni insulter l'autre personne (rester calme et factuel)
 * 5. Ne jamais envoyer de faux signalements (conséquences légales et intégrité de la modération)
 * 6. Utiliser les mécanismes officiels WhatsApp (procédure in-app et e-mail documentaire)
 * 7. Protéger ses propres données personnelles (double facteur, confidentialité du numéro)
 */
@Composable
fun GuideScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guide de sécurité & de preuve", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text(
                text = "Recommandations officielles & Bonnes pratiques",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "La lutte contre les abus sur les messageries repose sur la précision des faits et le strict respect des règles légales.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            GuideSectionCard(
                title = "1. Reconnaître une arnaque ou un faux compte",
                icon = Icons.Default.Warning,
                content = """
• Fausses urgences familiales (« Coucou maman/papa, j'ai changé de numéro, aide-moi vite »).
• Demandes de codes de vérification SMS / 2FA sous un faux prétexte.
• Faux avis de livraison de colis contenant un lien externe non officiel.
• Offres de faux gains financiers, cryptomonnaies ou investissements mirobolants.
• Incohérence entre la langue parlée et l'indicatif international du numéro.
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(12.dp))

            GuideSectionCard(
                title = "2. Conserver méthodiquement les preuves",
                icon = Icons.Default.Fingerprint,
                content = """
• Prenez des captures d'écran intégrales où le numéro de l'expéditeur (+XX...) est visiblement affiché en haut.
• Ne supprimez pas immédiatement la conversation si un dépôt de plainte est envisagé.
• Relevez la date, l'heure exacte et notez tout lien ou pièce jointe sans cliquer dessus.
• Exportez la discussion par sécurité (sans les médias si volumineux).
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(12.dp))

            GuideSectionCard(
                title = "3. Règles de conduite : Zéro menace",
                icon = Icons.Default.Gavel,
                content = """
• Ne répondez pas par des insultes, menaces ou provocations.
• Toute réponse agressive peut affaiblir la recevabilité de votre signalement ou dossier juridique.
• Bloquez le contact directement après avoir sauvegardé vos éléments probatoires.
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(12.dp))

            GuideSectionCard(
                title = "4. Interdiction absolue des faux signalements",
                icon = Icons.Default.Block,
                content = """
• Tout signalement doit correspondre à une expérience réelle et vécue.
• La fabrication délibérée de faux signalements nuit à la protection des utilisateurs et constitue un abus pouvant être sanctionné.
• Cette application n'autorise aucune multiplication automatique de requêtes.
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(12.dp))

            GuideSectionCard(
                title = "5. Utiliser les canaux officiels WhatsApp",
                icon = Icons.Default.CheckCircle,
                content = """
• WhatsApp traite les signalements via leur fonctionnalité in-app (Options du chat > Plus > Signaler).
• Pour les dossiers documentaires détaillés ou les réquisitions, le support officiel est joignable à android_web@support.whatsapp.com.
• Aucun intermédiaire ne peut ordonner la suspension d'un compte sans examen direct par WhatsApp.
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(12.dp))

            GuideSectionCard(
                title = "6. Protégez votre propre compte WhatsApp",
                icon = Icons.Default.Security,
                content = """
• Activez impérativement la vérification en 2 étapes avec code PIN (Paramètres > Compte > Vérification en deux étapes).
• Ne partagez JAMAIS un code à 6 chiffres reçu par SMS avec quiconque.
• Vérifiez régulièrement les appareils connectés (Paramètres > Appareils connectés).
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GuideSectionCard(
    title: String,
    icon: ImageVector,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}
