/**
 * Actions WhatsApp exécutées depuis le compte de l'utilisateur.
 *
 * Principe d'honnêteté (identique à celui du backend) : une action ne renvoie
 * `performed: true` que si elle a RÉELLEMENT été effectuée et vérifiée. Sinon
 * elle renvoie `performed: false` avec un motif précis, et le backend bascule
 * sur le parcours guidé.
 */

/**
 * Signale un contact.
 *
 * whatsapp-web.js n'expose pas d'API de signalement : le signalement est une
 * action de l'interface WhatsApp. Deux cas :
 *
 *  - `nativeReport` désactivé (par défaut) : on répond `performed:false,
 *    reason:'native_disabled'` → le backend affiche le parcours guidé pas-à-pas.
 *  - `nativeReport` activé explicitement par l'utilisateur : on pilote le menu
 *    de signalement de SA propre session WhatsApp Web (mêmes clics qu'un
 *    signalement manuel), puis on VÉRIFIE l'apparition de la confirmation.
 *    Si la vérification échoue, on ne prétend pas que le signalement a eu lieu.
 */
export async function reportContact({ client, chat, category, note, nativeReport }) {
  if (!nativeReport) {
    return {
      performed: false,
      reason: 'native_disabled',
      detail:
        "Le signalement automatique est désactivé sur cette passerelle. Utilisez le parcours guidé " +
        "fourni par l'application : il reproduit exactement le signalement manuel dans WhatsApp.",
    };
  }
  if (!client?.pupPage) {
    return { performed: false, reason: 'no_page', detail: 'Session WhatsApp non prête (page de contrôle absente).' };
  }

  const label = CATEGORY_LABELS[category] ?? 'Spam';
  try {
    const result = await client.pupPage.evaluate(
      async (chatId, reasonLabel, userNote) => {
        // Accès à l'application WhatsApp Web déjà ouverte dans cette session.
        const store = window.Store;
        if (!store?.Chat || !store?.Contact) {
          return { ok: false, detail: "L'interface de WhatsApp Web a changé : sélecteurs indisponibles." };
        }
        const chat = await store.Chat.find(chatId);
        if (!chat) return { ok: false, detail: 'Conversation introuvable dans cette session WhatsApp.' };

        const contact = chat.contact ?? (await store.Contact.get(chatId));
        if (!contact) return { ok: false, detail: 'Contact introuvable.' };

        // Déclenche la même action que le menu « Signaler » de WhatsApp Web.
        if (typeof contact.report === 'function') {
          await contact.report(reasonLabel, userNote);
        } else if (typeof chat.report === 'function') {
          await chat.report(reasonLabel, userNote);
        } else {
          return {
            ok: false,
            detail:
              "Cette version de WhatsApp Web n'expose pas l'action de signalement " +
              "(contact.report/chat.report). Utilisez le parcours guidé.",
          };
        }

        // Vérification : WhatsApp marque la conversation comme signalée.
        const isReported =
          chat.__x_isReported ||
          (typeof chat.reported === 'boolean' && chat.reported) ||
          Boolean(store.ReportStore?.get?.(chatId));
        return { ok: Boolean(isReported), detail: isReported ? 'action transmise' : 'confirmation non observée' };
      },
      chat.id._serialized,
      reasonLabel,
      (note ?? '').slice(0, 500),
    );

    return {
      performed: Boolean(result?.ok),
      reason: result?.ok ? 'ok' : 'verification_failed',
      detail: result?.detail ?? 'Résultat indéterminé.',
      reference: result?.ok ? `native-${Date.now()}` : undefined,
    };
  } catch (error) {
    return { performed: false, reason: 'error', detail: `Échec de l'action de signalement : ${error.message}` };
  }
}

/** Bloque un contact (API officielle de whatsapp-web.js). */
export async function blockContact({ chat }) {
  try {
    const contact = await chat.getContact();
    await contact.block();
    // Vérification réelle : le contact doit apparaître comme bloqué.
    const refreshed = await chat.getContact();
    const blocked = Boolean(refreshed.isBlocked);
    return {
      performed: blocked,
      reason: blocked ? 'ok' : 'verification_failed',
      detail: blocked ? 'contact bloqué' : "WhatsApp n'a pas confirmé le blocage.",
      reference: blocked ? `block-${Date.now()}` : undefined,
    };
  } catch (error) {
    return { performed: false, reason: 'error', detail: `Échec du blocage : ${error.message}` };
  }
}

export async function unblockContact({ chat }) {
  try {
    const contact = await chat.getContact();
    if (typeof contact.unblock === 'function') {
      await contact.unblock();
    } else if (typeof contact.block === 'function' && contact.isBlocked) {
      await contact.block(); // bascule
    } else {
      return { performed: false, reason: 'unsupported', detail: 'Déblocage non supporté par cette version.' };
    }
    const refreshed = await chat.getContact();
    const ok = !refreshed.isBlocked;
    return { performed: ok, reason: ok ? 'ok' : 'verification_failed', detail: ok ? 'contact débloqué' : 'non confirmé' };
  } catch (error) {
    return { performed: false, reason: 'error', detail: `Échec du déblocage : ${error.message}` };
  }
}

/**
 * Récupère les derniers messages d'une conversation pour constituer une preuve.
 * Cette fonction n'est appelée qu'à la demande explicite de l'utilisateur, dans
 * le cadre d'un signalement qu'il a initié.
 */
export async function recentMessages({ chat, limit = 5 }) {
  const messages = await chat.fetchMessages({ limit: Math.min(Math.max(limit, 1), 20) });
  return {
    messages: messages.map((m) => ({
      id: m.id?._serialized ?? null,
      from_me: Boolean(m.fromMe),
      timestamp: m.timestamp ?? null,
      type: m.type ?? 'unknown',
      // Le corps n'est jamais transmis au serveur sans consentement explicite :
      // l'application ne demande que les identifiants et les horodatages.
      body: null,
    })),
  };
}

export const CATEGORY_LABELS = {
  spam: 'Spam',
  financial_scam: 'Fraude ou arnaque',
  impersonation: 'Usurpation d’identité',
  harassment: 'Harcèlement',
  hate_speech: 'Discours haineux',
  illegal_content: 'Contenu illégal',
  other: 'Autre',
};
