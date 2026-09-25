/**
 * Gestion des sessions WhatsApp de l'utilisateur.
 *
 * Ce module est le SEUL endroit du projet qui touche à une session WhatsApp.
 * Il tourne sur la machine de l'utilisateur (ou dans son conteneur), jamais sur
 * le serveur SignalPro : le serveur ne voit que des identifiants de session et
 * des résultats d'actions.
 *
 * Les données d'authentification WhatsApp (clés multi-appareils) restent dans
 * le dossier local `GATEWAY_SESSION_DIR` et ne sont jamais copiées ailleurs.
 */
import fs from 'node:fs/promises';
import path from 'node:path';

import pkg from 'whatsapp-web.js';

import { blockContact, recentMessages, reportContact, unblockContact } from './actions.js';
import { SlidingWindowLimiter } from './rateLimit.js';
import { randomRef } from './security.js';

const { Client, LocalAuth } = pkg;

export const STATES = {
  PENDING: 'pending',
  AWAITING_SCAN: 'awaiting_scan',
  CONNECTED: 'connected',
  DISCONNECTED: 'disconnected',
  REVOKED: 'revoked',
};

export class Session {
  constructor({ id, label, userRef, sessionDir, nativeReport, onEvent }) {
    this.id = id;
    this.label = label;
    this.userRef = userRef;
    this.sessionDir = sessionDir;
    this.nativeReport = nativeReport;
    this.onEvent = onEvent ?? (() => {});
    this.status = STATES.PENDING;
    this.qr = null;
    this.jid = null;
    this.number = null;
    this.platform = null;
    this.limiter = new SlidingWindowLimiter();
    this.client = null;
    this.connectStartedAt = null;

    this.ready = this.#start();
  }

  async #start() {
    this.client = new Client({
      authStrategy: new LocalAuth({ clientId: this.id, dataPath: path.join(this.sessionDir, 'auth') }),
      // La session ne doit jamais exposer les messages au serveur : on ne
      // s'abonne qu'aux événements nécessaires au cycle de vie et au statut.
      puppeteer: {
        headless: true,
        args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-gpu'],
      },
      webVersionCache: { type: 'local' },
    });

    this.client.on('qr', (qr) => {
      this.qr = qr;
      this.status = STATES.AWAITING_SCAN;
      this.onEvent({ kind: 'qr', session: this.id });
    });

    this.client.on('authenticated', () => {
      this.status = STATES.AWAITING_SCAN;
    });

    this.client.on('ready', async () => {
      this.status = STATES.CONNECTED;
      this.qr = null;
      const info = this.client.info ?? {};
      this.jid = info.wid?._serialized ?? null;
      this.number = this.jid ? this.jid.split('@')[0] : null;
      this.platform = info.platform ?? 'unknown';
      this.onEvent({ kind: 'ready', session: this.id, jid_suffix: (this.jid ?? '').slice(-12) });
    });

    this.client.on('auth_failure', (message) => {
      this.status = STATES.DISCONNECTED;
      this.onEvent({ kind: 'auth_failure', session: this.id, message: String(message).slice(0, 200) });
    });

    this.client.on('disconnected', (reason) => {
      this.status = STATES.DISCONNECTED;
      this.onEvent({ kind: 'disconnected', session: this.id, reason: String(reason).slice(0, 200) });
    });

    try {
      await this.client.initialize();
    } catch (error) {
      this.status = STATES.DISCONNECTED;
      this.onEvent({ kind: 'initialize_failed', session: this.id, message: error.message });
      throw error;
    }
  }

  async state() {
    return {
      session_ref: this.id,
      user_ref: this.userRef,
      label: this.label,
      status: this.status,
      jid: this.jid,
      number: this.number,
      platform: this.platform,
      has_qr: Boolean(this.qr),
      limits: this.limiter.snapshot(),
    };
  }

  async pairingPayload() {
    if (this.qr) return this.qr;
    // whatsapp-web.js expose un code d'appairage en 8 caractères comme alternative au QR
    if (typeof this.client?.requestPairingCode === 'function' && this.numberToPair) {
      return await this.client.requestPairingCode(this.numberToPair);
    }
    return null;
  }

  requireConnected() {
    if (this.status !== STATES.CONNECTED) {
      const error = new Error(`Session WhatsApp non connectée (état : ${this.status}).`);
      error.statusCode = 409;
      throw error;
    }
  }

  async #chat(peerJid) {
    const chat = await this.client.getChatById(peerJid);
    if (!chat) {
      const error = new Error('Conversation introuvable dans cette session WhatsApp.');
      error.statusCode = 404;
      throw error;
    }
    return chat;
  }

  /** Synchronise l'empreinte des conversations (aucun contenu de message). */
  async syncContacts() {
    this.requireConnected();
    const chats = await this.client.getChats();
    return {
      count: chats.length,
      chats: chats.map((chat) => ({
        jid: chat.id?._serialized ?? null,
        is_group: Boolean(chat.isGroup),
        last_message_at: chat.timestamp ?? null,
      })).filter((c) => Boolean(c.jid)),
    };
  }

  async #guard(kind) {
    const verdict = this.limiter.check(kind);
    if (!verdict.allowed) {
      const error = new Error(verdict.reason + ' Aucun contournement n\'est appliqué : patientez ou réduisez vos demandes.');
      error.statusCode = 429;
      error.retryAfter = verdict.retry_after;
      throw error;
    }
    this.limiter.record(kind);
  }

  async report(peerJid, category, messageIds, note) {
    this.requireConnected();
    await this.#guard('report');
    const chat = await this.#chat(peerJid);
    const result = await reportContact({
      client: this.client,
      chat,
      category,
      note,
      nativeReport: this.nativeReport,
    });
    return {
      ...result,
      category,
      peer_jid: peerJid,
      messages_attached: (messageIds ?? []).length,
      capabilities_used: { native_report: this.nativeReport },
    };
  }

  async block(peerJid) {
    this.requireConnected();
    await this.#guard('action');
    const chat = await this.#chat(peerJid);
    return blockContact({ chat });
  }

  async unblock(peerJid) {
    this.requireConnected();
    await this.#guard('action');
    const chat = await this.#chat(peerJid);
    return unblockContact({ chat });
  }

  async messages(peerJid, limit) {
    this.requireConnected();
    const chat = await this.#chat(peerJid);
    return recentMessages({ chat, limit });
  }

  async destroy({ purge = true } = {}) {
    try {
      await this.client?.destroy();
    } catch {
      /* la session est peut-être déjà fermée */
    }
    this.status = STATES.REVOKED;
    if (purge) {
      await fs.rm(path.join(this.sessionDir, 'auth', `session-${this.id}`), { recursive: true, force: true });
    }
    this.onEvent({ kind: 'destroyed', session: this.id });
  }
}

export class SessionManager {
  constructor({ sessionDir, nativeReport }) {
    this.sessionDir = sessionDir;
    this.nativeReport = nativeReport;
    this.sessions = new Map();
    this.events = [];
  }

  create({ userRef, label }) {
    const id = randomRef('sess');
    const session = new Session({
      id,
      label: label ?? 'Appareil',
      userRef,
      sessionDir: this.sessionDir,
      nativeReport: this.nativeReport,
      onEvent: (event) => {
        this.events.push({ ...event, at: new Date().toISOString() });
        if (this.events.length > 200) this.events.shift();
      },
    });
    this.sessions.set(id, session);
    return session;
  }

  get(id) {
    const session = this.sessions.get(id);
    if (!session) {
      const error = new Error(`Session inconnue : ${id}`);
      error.statusCode = 404;
      throw error;
    }
    return session;
  }

  async destroy(id) {
    const session = this.get(id);
    await session.destroy();
    this.sessions.delete(id);
    return { revoked: true, session_ref: id };
  }

  capabilities() {
    return {
      report_native: this.nativeReport,
      block_contact: true,
      contact_sync: true,
    };
  }
}
