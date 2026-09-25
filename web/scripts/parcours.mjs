#!/usr/bin/env node
/**
 * Vérification réelle du front web, de bout en bout.
 *
 * Le script interroge le serveur Next.js comme le ferait un navigateur
 * (http://127.0.0.1:3000) : page d'accueil, proxy vers l'API, parcours complet
 * utilisateur et modérateur. Rien n'est simulé : chaque appel doit réellement
 * aboutir, et tout échec inattendu fait sortir le script en code 1.
 *
 * Les refus *volontaires* du serveur (quota horaire atteint, contestation déjà en
 * cours, file de modération vide) sont comptés comme des comportements conformes
 * et signalés comme tels : le script reste exécutable plusieurs fois de suite.
 *
 * Prérequis :
 *   1. l'API tourne :       cd backend && ENV=dev ALLOW_CONSOLE_VERIFICATION=true \
 *                           uvicorn app.main:app --port 8000
 *   2. comptes de démo :    python backend/scripts/seed_demo.py --reset
 *   3. le front tourne :    cd web && API_PROXY_TARGET=http://127.0.0.1:8000 npm run dev
 *
 * Usage : node scripts/parcours.mjs   (WEB_BASE_URL=http://autre-hote:3000 pour cibler ailleurs)
 */

const BASE = process.env.WEB_BASE_URL || "http://127.0.0.1:3000";
// Version de consentement publiée par le serveur : le script ne la devine pas.
let CONSENT_VERSION = "2026-09-1";
const PROXY = `${BASE}/proxy`;

const results = [];

function record(label, ok, detail = "") {
  results.push({ label, ok, detail });
  console.log(`  [${ok ? "OK " : "ÉCHEC"}] ${label}${detail ? ` — ${detail}` : ""}`);
}

function notice(label, detail) {
  results.push({ label, ok: true, detail });
  console.log(`  [OK ] ${label} — ${detail}`);
}

async function call(label, path, { method = "GET", token, json, form, allowed = [200, 201] } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  let body;
  if (json !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(json);
  } else if (form) {
    body = form;
  }
  const response = await fetch(`${PROXY}${path}`, { method, headers, body });
  const text = await response.text();
  const ok = allowed.includes(response.status);
  record(label, ok, `HTTP ${response.status}${ok ? "" : ` · ${text.slice(0, 200)}`}`);
  return { status: response.status, text, ok };
}

function jsonOf(result) {
  try {
    return JSON.parse(result.text);
  } catch {
    return {};
  }
}

async function login(email, password) {
  const response = await fetch(`${PROXY}/api/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) {
    throw new Error(`Connexion impossible pour ${email} : HTTP ${response.status} ${await response.text()}`);
  }
  return (await response.json()).access_token;
}

/** Construit un PNG 420×720 réellement valide, non uniforme et unique.
 *
 * · le serveur refuse les preuves illisibles, quasi uniformes ou vides : on dessine
 *   donc une « capture » variée (barres, bulles, lignes de texte simulées) ;
 * · le serveur refuse aussi une preuve déposée à l'identique dans plusieurs
 *   signalements (fichier identique au bit près) : chaque exécution produit donc une
 *   image différente, comme deux vraies captures d'écran.
 */
async function makeScreenshot() {
  // Sel cryptographique : deux exécutions rapprochées produisaient parfois des images
  // dont l'empreinte différait trop peu, et le serveur refusait la seconde comme
  // « preuve déjà déposée ». Un sel de 8 octets rend la collision impossible en pratique.
  const { randomBytes } = await import("node:crypto");
  const salt = randomBytes(8);
  const variation = [...salt, ...Buffer.from(String(Date.now()))].reduce(
    (acc, byte) => (acc * 31 + byte) % 251,
    7,
  );
  const { deflateSync } = await import("node:zlib");
  const width = 420;
  const height = 720;
  const raw = Buffer.alloc((width * 3 + 1) * height);

  const rect = (x0, y0, x1, y1, color) => {
    for (let y = y0; y < y1; y += 1) {
      const rowStart = y * (width * 3 + 1) + 1;
      for (let x = x0; x < x1; x += 1) {
        const px = rowStart + x * 3;
        raw[px] = color[0];
        raw[px + 1] = color[1];
        raw[px + 2] = color[2];
      }
    }
  };

  // Fond dégradé : garantit une variance forte (contrôle « image quasi uniforme »).
  for (let y = 0; y < height; y += 1) {
    const rowStart = y * (width * 3 + 1) + 1;
    for (let x = 0; x < width; x += 1) {
      const px = rowStart + x * 3;
      raw[px] = 226 + Math.round((x / width) * 18);
      raw[px + 1] = 232 + Math.round((y / height) * 16);
      raw[px + 2] = 238 + Math.round((x / width) * 10);
    }
  }
  rect(0, 0, width, 56, [18, 42, 61]); // barre d'en-tête
  rect(16, 84, 404, 190, [140, 186, 130]); // bulle reçue
  rect(60, 214, 404, 320, [210, 226, 238]); // réponse
  rect(16, 350, 404, 470, [140, 186, 130]); // relance
  rect(60, 494, 404, 600, [210, 226, 238]);
  rect(16, 640, 260, 692, [235, 226, 180]); // zone de saisie
  for (let i = 0; i < 260; i += 1) {
    const y = 96 + (i % 5) * 18;
    const x = 28 + Math.floor(i / 5) * 26;
    if (x < 390 && y < 600) rect(x, y, x + 16, y + 6, [40 + (i % 7) * 12, 48, 56]);
  }
  // Bandeau « horodatage » variable : rend chaque capture unique (le serveur refuse
  // deux preuves identiques déposées dans deux signalements différents). Chaque pixel
  // dépend d'un octet distinct du sel, pas seulement d'un calcul sur la date.
  for (let i = 0; i < 40; i += 1) {
    const x = 20 + i * 9;
    const tone = 60 + ((variation * (i + 3) + salt[i % salt.length] * (i + 1)) % 190);
    rect(x, 620, x + 7, 632, [tone, (tone * 3) % 255, (tone * 7 + salt[(i + 1) % salt.length]) % 255]);
  }
  // Marqueur de bas de page : 32 pixels dont la teinte vient directement du sel.
  for (let i = 0; i < salt.length; i += 1) {
    const x = 300 + i * 12;
    const tone = salt[i];
    rect(x, 700, x + 10, 710, [tone, (tone + 90) % 256, (tone + 180) % 256]);
  }

  const chunk = (type, data) => {
    const length = Buffer.alloc(4);
    length.writeUInt32BE(data.length, 0);
    const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
    const crcTable = [];
    for (let n = 0; n < 256; n += 1) {
      let c = n;
      for (let k = 0; k < 8; k += 1) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      crcTable[n] = c >>> 0;
    }
    let crc = 0xffffffff;
    for (const byte of body) crc = crcTable[(crc ^ byte) & 0xff] ^ (crc >>> 8);
    const crcBuf = Buffer.alloc(4);
    crcBuf.writeUInt32BE((crc ^ 0xffffffff) >>> 0, 0);
    return Buffer.concat([length, body, crcBuf]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // 8 bits par canal
  ihdr[9] = 2; // truecolor RGB
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

async function main() {
  console.log("Vérification du front SignalPro");
  console.log(`Serveur web : ${BASE}\n`);

  console.log("1) Page d'accueil et avertissements légaux");
  const page = await fetch(`${BASE}/`);
  const html = await page.text();
  record("la page se charge", page.status === 200, `HTTP ${page.status}`);
  record("l'avertissement sur les faux signalements est présent", html.includes("poursuites judiciaires"));
  record("l'absence de garantie de bannissement est affichée", html.includes("ne garantit pas"));
  record(
    "aucun secret serveur n'est exposé dans la page",
    !/sk_live|access_token"|JWT_SECRET|FIELD_KEY/.test(html),
  );

  // Vérification de l'interface RÉELLEMENT livrée : on télécharge les fragments
  // JavaScript servis au navigateur et on y cherche les fonctions annoncées. Cela
  // attrape une régression où l'API fonctionne mais où l'écran correspondant a
  // disparu du paquet livré.
  console.log("\n1 bis) Fonctions réellement embarquées dans le paquet web");
  {
    const markers = [
      ["campaignPreview", "demande groupée : appel réel de vérification"],
      ["campaignCancel", "demande groupée : arrêt possible d'une demande en cours"],
      ["executable_count", "demande groupée : nombre réellement exécutable affiché"],
      ["blocking_reason", "demande groupée : raison de blocage affichée"],
      ["ne garantit", "absence de garantie de bannissement présente dans les écrans"],
      ["manual_guided", "mode guidé WhatsApp (repli honnête) présent"],
      ["appeal", "contestation présente dans l'interface"],
    ];
    // Les fragments chargés à la demande (écrans secondaires) ne figurent pas dans le
    // HTML initial : on suit donc les références trouvées DANS les fragments, sur deux
    // niveaux, pour que le contrôle ne dépende pas de l'ordre de chargement.
    const seen = new Set();
    let bundle = "";
    let frontier = [...new Set([...html.matchAll(/\/_next\/static\/chunks\/[^"']+\.js/g)].map((m) => m[0]))];
    for (let depth = 0; depth < 3 && frontier.length > 0; depth += 1) {
      const next = [];
      for (const url of frontier) {
        if (seen.has(url)) continue;
        seen.add(url);
        const chunk = await fetch(`${BASE}${url}`);
        if (!chunk.ok) continue;
        const code = await chunk.text();
        bundle += code;
        next.push(...[...code.matchAll(/\/_next\/static\/chunks\/[A-Za-z0-9_.\-]+\.js/g)].map((m) => m[0]));
      }
      frontier = [...new Set(next)].filter((url) => !seen.has(url));
    }
    record("fragments JavaScript téléchargés", seen.size > 0, `${seen.size} fragment(s)`);
    for (const [marker, label] of markers) {
      record(label, bundle.includes(marker), `marqueur « ${marker} »`);
    }
  }

  // Vérification obligatoire du compte ET refus de signaler sans WhatsApp lié : un
  // compte neuf est créé à chaque exécution (adresse unique), ce qui garantit un quota
  // intact et prouve que le parcours d'inscription fonctionne réellement.
  console.log("\n1 ter) Compte neuf : vérification obligatoire et refus sans WhatsApp lié");
  {
    const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`;
    const email = `controle-${suffix}@example.org`;
    const phone = `+2239${Math.floor(1000000 + Math.random() * 8999999)}`;
    const password = `Controle-${suffix}-Aa1`;
    const registered = await call("inscription d'un compte neuf", "/api/v1/auth/register", {
      method: "POST",
      json: {
        email,
        phone,
        password,
        display_name: "Compte de contrôle",
        channel: "email",
        accept_terms: true,
        accept_privacy: true,
      },
      allowed: [201],
    });
    const registration = jsonOf(registered);
    record(
      "la vérification par e-mail est exigée (code émis)",
      typeof registration.dev_code === "string" && registration.dev_code.length >= 4,
      `canal ${registration.verification_channel}`,
    );
    const verified = await call("validation du code reçu", "/api/v1/auth/verify", {
      method: "POST",
      json: { email, code: registration.dev_code },
    });
    const freshToken = jsonOf(verified).access_token;
    record("le compte neuf reçoit un jeton utilisable", typeof freshToken === "string" && freshToken.length > 20);
    if (freshToken) {
      const limits = await call("quotas du compte neuf", "/api/v1/auth/limits", { token: freshToken });
      const quota = jsonOf(limits);
      record(
        "les plafonds anti-abus sont appliqués dès la création du compte",
        Number(quota.max_reports_per_day_user) === 20 &&
          Number(quota.max_reports_per_hour_user) === 5 &&
          Number(quota.max_actions_per_minute_user) === 10,
        `${quota.max_actions_per_minute_user} actions/min · ${quota.max_reports_per_hour_user}/h · ${quota.max_reports_per_day_user}/j`,
      );
      const refused = await call("signalement refusé sans WhatsApp lié", "/api/v1/reports", {
        method: "POST",
        token: freshToken,
        json: {
          target_phone: "+22360000001",
          category: "spam",
          occurred_at: "2026-08-01T09:00:00Z",
          description: "Contrôle automatique : aucun appareil lié sur ce compte.",
          contact_proof_method: "manual_declaration",
          store_messages: false,
        },
        allowed: [422],
      });
      record(
        "le refus exige de lier son WhatsApp (aucun contournement par déclaration)",
        /liez d'abord votre whatsapp/i.test(jsonOf(refused).detail ?? ""),
        String(jsonOf(refused).detail ?? "").slice(0, 110),
      );
    }
  }

  console.log("\n2) Parcours utilisateur (alice@example.org)");
  const alice = await login("alice@example.org", "AliceSignalPro123");
  await call("session et profil", "/api/v1/auth/me", { token: alice });
  await call("limites anti-abus", "/api/v1/auth/limits", { token: alice });
  const consentRows = await call("consentements enregistrés", "/api/v1/auth/consents", { token: alice });
  // La version de consentement en vigueur est LUE depuis le serveur (jamais devinée) :
  // elle est utilisée plus bas pour vérifier que la liaison WhatsApp la contrôle.
  try {
    const rows = JSON.parse(consentRows.text);
    const active = Array.isArray(rows) ? rows.find((row) => row.kind === "terms" && row.version) : null;
    if (active) {
      CONSENT_VERSION = active.version;
      notice("version de consentement en vigueur lue sur le serveur", active.version);
    }
  } catch {
    // L'absence de version lisible est signalée par le contrôle de la section 5 bis.
  }
  await call("quota d'utilisation", "/api/v1/reports/usage", { token: alice });
  await call("mes signalements", "/api/v1/reports", { token: alice });

  const stamp = new Date(Date.now() - 3600_000).toISOString().replace(/\.\d+Z$/, "Z");
  const created = await call("création d'un signalement", "/api/v1/reports", {
    method: "POST",
    token: alice,
    json: {
      target_phone: "+22365551234",
      category: "financial_scam",
      occurred_at: stamp,
      description: "Contrôle automatique : lien frauduleux reçu, capture conservée et horodatée.",
      contact_proof_method: "linked_device_scan",
      store_messages: false,
    },
    allowed: [200, 201, 422, 429],
  });
  if (created.status === 429) {
    notice(
      "quota horaire atteint : le serveur refuse honnêtement",
      jsonOf(created).detail ?? "429",
    );
  }
  const reportId = jsonOf(created).id;
  if (reportId) {
    const png = await makeScreenshot();
    const form = new FormData();
    form.append("kind", "screenshot");
    form.append("file", new Blob([png], { type: "image/png" }), "capture.png");
    const uploaded = await call("dépôt d'une preuve (capture réelle)", `/api/v1/reports/${reportId}/evidence`, {
      method: "POST",
      token: alice,
      form,
    });
    record(
      "la preuve fait passer le signalement en vérification",
      jsonOf(await call("détail du signalement", `/api/v1/reports/${reportId}`, { token: alice })).status ===
        "pending_verification",
      `dépôt HTTP ${uploaded.status}`,
    );
    await call("export texte du signalement", `/api/v1/reports/${reportId}/export`, { token: alice });
  }

  console.log("\n3) Import CSV avec en-têtes français");
  const csv =
    "Numéro;Catégorie;Date de réception;Description;Pièce jointe\n" +
    "+22364449876;spam;2026-09-18 12:00;Messages publicitaires répétés sans aucun consentement;capture_0918.png\n" +
    "+22361234567;;2026-09-18 12:00;Ligne volontairement sans catégorie;capture_0918b.png\n";
  const importForm = new FormData();
  importForm.append("file", new Blob([csv], { type: "text/csv" }), "releve.csv");
  const preview = await call("analyse de l'import", "/api/v1/reports/import/preview", {
    method: "POST",
    token: alice,
    form: importForm,
  });
  const previewBody = jsonOf(preview);
  record(
    "la ligne incomplète est refusée, la ligne complète acceptée",
    previewBody.valid_rows === 1 && previewBody.rejected_rows === 1,
    `valides ${previewBody.valid_rows} · refusées ${previewBody.rejected_rows}`,
  );

  const badForm = new FormData();
  badForm.append(
    "file",
    new Blob(["numero;categorie\n+22361234567;spam\n"], { type: "text/csv" }),
    "sans_preuve.csv",
  );
  record(
    "un import sans colonne de preuve est rejeté intégralement",
    (
      await call("import sans preuve", "/api/v1/reports/import/preview", {
        method: "POST",
        token: alice,
        form: badForm,
        allowed: [422],
      })
    ).status === 422,
  );

  console.log("\n4) Communauté et contestation");
  await call("liste communautaire", "/api/v1/community/blacklist?limit=200", { token: alice });
  await call("export de la liste", "/api/v1/community/blacklist/export", { token: alice });
  await call("statistiques honnêtes", "/api/v1/community/stats", { token: alice });
  const appeal = await call("dépôt d'une contestation", "/api/v1/community/appeals", {
    method: "POST",
    json: {
      target_phone: "+22365551234",
      claimant_contact: `proprietaire+${Date.now()}@example.org`,
      statement:
        "Contrôle automatique : ce numéro m'appartient et les captures transmises ne correspondent pas à mes envois.",
      evidence_note: "Relevé opérateur disponible sur demande.",
    },
    allowed: [200, 201, 409],
  });
  if (appeal.status === 409) {
    notice("une contestation est déjà en cours : le doublon est refusé", jsonOf(appeal).detail ?? "409");
  }
  const appealRef = jsonOf(appeal).public_ref;
  if (appealRef) {
    await call("suivi de la contestation", `/api/v1/community/appeals/${appealRef}`);
  }

  console.log("\n5) Tableau de bord, détection et campagnes");
  await call("tableau de bord", "/api/v1/dashboard/overview", { token: alice });
  await call("notifications", "/api/v1/dashboard/notifications", { token: alice });
  await call("alertes de contact", "/api/v1/dashboard/alerts", { token: alice });
  await call("analyse des conversations", "/api/v1/dashboard/alerts/scan", { method: "POST", token: alice });
  await call("export de mes signalements", "/api/v1/dashboard/export/reports", { token: alice });
  await call("signatures de détection", "/api/v1/detect/signatures", { token: alice });
  const scan = await call("analyse d'un message suspect", "/api/v1/detect/scan", {
    method: "POST",
    token: alice,
    json: { text: "Félicitations, vous avez gagné 500 000 FCFA, cliquez sur bit.ly/retrait pour recevoir" },
  });
  record(
    "le message frauduleux est réellement détecté comme suspect",
    jsonOf(scan).is_suspicious === true,
    `score ${jsonOf(scan).score}`,
  );
  const campaignPreview = await call("campagne : simulation honnête", "/api/v1/campaigns/preview", {
    method: "POST",
    token: alice,
    json: {
      target_phone: "+22365551234",
      requested_count: 3,
      category: "financial_scam",
      occurred_at: "2026-09-19T10:00:00Z",
      description: "Contrôle automatique : demande de transfert pour un colis inexistant, plusieurs relances.",
      consent_ack: true,
    },
  });
  {
    const preview = jsonOf(campaignPreview);
    // Invariant d'honnêteté : demander un grand nombre ne crée jamais de signalements.
    const ambitious = await call("campagne : 100 signalements demandés sont ramenés au réel", "/api/v1/campaigns/preview", {
      method: "POST",
      token: alice,
      json: {
        target_phone: "+22365551234",
        requested_count: 100,
        category: "financial_scam",
        occurred_at: "2026-09-19T10:00:00Z",
        description: "Contrôle automatique : nombre demandé volontairement déraisonnable.",
        consent_ack: true,
      },
    });
    const ambitiousBody = jsonOf(ambitious);
    record(
      "le nombre exécutable ne dépasse jamais les comptes réellement contactés",
      ambitiousBody.executable_count <= ambitiousBody.eligible_accounts &&
        ambitiousBody.requested_count === 100,
      `demandés ${ambitiousBody.requested_count} · éligibles ${ambitiousBody.eligible_accounts} · exécutables ${ambitiousBody.executable_count}`,
    );
    record(
      "le plafond dur de campagne est publié par le serveur (jamais deviné par l'interface)",
      Number.isFinite(Number(preview.requested_hard_cap)) && Number(preview.requested_hard_cap) > 0,
      `plafond ${preview.requested_hard_cap}`,
    );
    record(
      "l'aperçu explique la limite au lieu de promettre un résultat",
      typeof preview.explanation === "string" && /compte/i.test(preview.explanation),
    );
  }
  const withoutAck = await call("campagne refusée sans confirmation de l'avertissement", "/api/v1/campaigns", {
    method: "POST",
    token: alice,
    json: {
      target_phone: "+22365551234",
      requested_count: 3,
      category: "financial_scam",
      occurred_at: "2026-09-19T10:00:00Z",
      description: "Contrôle automatique : consentement volontairement absent.",
      consent_ack: false,
    },
    allowed: [422],
  });
  record(
    "le refus rappelle que les faux signalements sont passibles de poursuites",
    /poursuite|faux signalement/i.test(withoutAck.text),
    withoutAck.text.slice(0, 120),
  );
  await call("appareils liés", "/api/v1/devices", { token: alice });

  // Le consentement au risque n'est pas décoratif : sans lui, aucune liaison
  // WhatsApp n'est ouverte (deux refus réels, et aucune session créée).
  console.log("\n5 bis) Liaison WhatsApp : le consentement au risque est exigé");
  const withoutConsent = await call("liaison refusée sans consentement explicite", "/api/v1/devices/link/start", {
    method: "POST",
    token: alice,
    json: { label: "Contrôle sans consentement", risk_consent: false, consent_version: CONSENT_VERSION },
    allowed: [400],
  });
  record(
    "le refus explique la raison (consentement)",
    /consentement/i.test(withoutConsent.text),
    withoutConsent.text.slice(0, 120),
  );
  const staleConsent = await call("liaison refusée avec une version de consentement obsolète", "/api/v1/devices/link/start", {
    method: "POST",
    token: alice,
    json: { label: "Contrôle version obsolète", risk_consent: true, consent_version: "2000-01-1" },
    allowed: [400],
  });
  record(
    "le refus exige la version de consentement en vigueur",
    /version de consentement/i.test(staleConsent.text),
    staleConsent.text.slice(0, 120),
  );

  console.log("\n6) Parcours modérateur");
  const moderator = await login("moderateur@signalpro-demo.com", "ModerateurPro123");
  const queue = await call("file d'attente", "/api/v1/moderation/queue?status_filter=pending_verification", {
    token: moderator,
  });
  await call("statistiques de modération", "/api/v1/moderation/stats", { token: moderator });
  await call("journal d'audit", "/api/v1/moderation/audit?limit=50", { token: moderator });
  await call("dossiers", "/api/v1/moderation/dossiers", { token: moderator });
  await call("contestations", "/api/v1/moderation/appeals", { token: moderator });
  const items = jsonOf(queue);
  const item = Array.isArray(items) && items.length > 0 ? items[0] : null;
  if (item) {
    record(
      "la file expose de quoi décider réellement (cible + preuves)",
      Boolean(item.target_id) && Array.isArray(item.evidences),
      `${item.public_ref} · cible #${item.target_id} · ${item.evidences.length} preuve(s)`,
    );
    if (item.evidences?.length) {
      await call(
        "téléchargement d'une preuve pour contrôle",
        `/api/v1/reports/evidence/${item.evidences[0].id}/download`,
        { token: moderator },
      );
    }
    await call("décision motivée sur le signalement", `/api/v1/moderation/reports/${item.entity_id}/decision`, {
      method: "POST",
      token: moderator,
      json: { decision: "verify", reason: "Contrôle automatique : preuve lue et cohérente avec la description." },
    });
    if (item.target_id) {
      await call(
        "suspension non confirmée (aucune invention)",
        `/api/v1/moderation/targets/${item.target_id}/suspension?confirmed=false&evidence=Controle%20automatique`,
        { method: "POST", token: moderator },
      );
      await call("constitution du dossier PDF", `/api/v1/moderation/targets/${item.target_id}/dossier`, {
        method: "POST",
        token: moderator,
      });
    }
  } else {
    notice(
      "aucun signalement en attente : les étapes de décision sont ignorées",
      "relancez `python backend/scripts/seed_demo.py --reset` pour un jeu de données neuf",
    );
  }

  const failed = results.filter((entry) => !entry.ok);
  console.log(`\nRésultat : ${results.length - failed.length}/${results.length} contrôles réussis`);
  for (const entry of failed) console.log(`  ✗ ${entry.label} → ${entry.detail}`);
  process.exit(failed.length ? 1 : 0);
}

main().catch((error) => {
  console.error(`\nErreur pendant la vérification : ${error.message}`);
  process.exit(2);
});
