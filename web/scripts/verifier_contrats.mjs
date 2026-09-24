#!/usr/bin/env node
/**
 * Contrôle des contrats entre le front web et le backend.
 *
 * 1. Les catégories d'infraction proposées dans l'interface doivent correspondre
 *    exactement à l'énumération du serveur (`backend/app/constants.py`). Une valeur
 *    inventée côté client provoque un HTTP 422 chez l'utilisateur : ce contrôle
 *    l'interdit.
 * 2. Chaque route appelée depuis `lib/api.ts` doit exister dans l'API — vérifié sur
 *    `openapi.json` si le backend est joignable (sinon le contrôle est signalé comme
 *    ignoré, jamais comme réussi).
 *
 * Usage : node scripts/verifier_contrats.mjs [--api http://127.0.0.1:8000]
 * Sortie : code 1 dès qu'un contrat est rompu.
 */

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const webRoot = join(here, "..");
const repoRoot = join(webRoot, "..");

const apiIndex = process.argv.indexOf("--api");
const API_BASE = apiIndex >= 0 ? process.argv[apiIndex + 1] : process.env.API_BASE_URL || "http://127.0.0.1:8000";

const failures = [];
const check = (label, ok, detail = "") => {
  console.log(`  [${ok ? "OK " : "ÉCHEC"}] ${label}${detail ? ` — ${detail}` : ""}`);
  if (!ok) failures.push(label);
};

/** Extrait les valeurs de l'énumération InfractionCategory côté serveur. */
function serverCategories() {
  const source = readFileSync(join(repoRoot, "backend", "app", "constants.py"), "utf8");
  const block = source.split("class InfractionCategory(StrEnum):")[1]?.split("class ")[0] ?? "";
  return [...block.matchAll(/"([a-z_]+)"/g)].map((match) => match[1]).sort();
}

/** Extrait les catégories proposées par l'interface web. */
function webCategories(file, variable) {
  const source = readFileSync(join(webRoot, "components", file), "utf8");
  const block = source.split(variable)[1] ?? "";
  const cut = block.indexOf("];");
  const relevant = cut >= 0 ? block.slice(0, cut) : block;
  return [...relevant.matchAll(/value:\s*"([a-z_]+)"/g)].map((match) => match[1]).sort();
}

/** Extrait les chemins d'API appelés par le client web (gabarits inclus). */
function calledPaths() {
  const source = readFileSync(join(webRoot, "lib", "api.ts"), "utf8");
  const raw = [...source.matchAll(/[`"']([^`"']*\/api\/v1\/[^`"']*)[`"']/g)].map((match) => match[1]);
  const paths = raw.map((value) => {
    const tail = value.split("/api/v1/")[1].split("?")[0];
    // Un gabarit qui n'est qu'un paramètre de requête (`/reports${query}`) ne doit pas
    // produire un faux segment `/reports{param}`.
    return `/api/v1/${tail}`
      .replace(/\$\{[^}]*\}/g, (match, offset, whole) => (whole[offset - 1] === "/" ? "{param}" : ""))
      .replace(/\/+$/, "");
  });
  return [...new Set(paths)];
}

function normalize(path) {
  return path.replace(/\/+$/, "");
}

function matchesAny(called, available) {
  const target = normalize(called);
  return available.some((candidate) => {
    const pattern = `^${candidate.replace(/\{[^}]+\}/g, "[^/]+")}$`;
    return new RegExp(pattern).test(target);
  });
}

async function main() {
  console.log("Contrôles de contrat front ↔ backend\n");

  console.log("1) Catégories d'infraction");
  const fromServer = serverCategories();
  check("catégories lues côté serveur", fromServer.length >= 6, fromServer.join(", "));
  for (const [file, variable] of [
    ["ReportsPanel.tsx", "const CATEGORIES = "],
    ["CommunityPanel.tsx", "const CATEGORY_FILTERS = "],
  ]) {
    const fromWeb = webCategories(file, variable);
    const filtered = file === "CommunityPanel.tsx" ? fromWeb.filter((value) => value !== "") : fromWeb;
    const missing = fromServer.filter((value) => !filtered.includes(value));
    const unknown = filtered.filter((value) => !fromServer.includes(value));
    check(
      `${file} : liste identique à l'énumération serveur`,
      missing.length === 0 && unknown.length === 0,
      missing.length || unknown.length ? `manquantes ${missing.join(",") || "—"} · inconnues ${unknown.join(",") || "—"}` : `${filtered.length} valeurs`,
    );
  }

  console.log("\n2) Routes appelées depuis le client web");
  const called = calledPaths();
  check("routes détectées dans lib/api.ts", called.length >= 40, `${called.length} routes`);
  let available = null;
  try {
    const response = await fetch(`${API_BASE}/openapi.json`, { signal: AbortSignal.timeout(4000) });
    if (response.ok) available = Object.keys((await response.json()).paths ?? {});
  } catch {
    available = null;
  }
  if (!available) {
    console.log(
      `  [IGNORÉ] comparaison avec openapi.json : backend injoignable sur ${API_BASE} — ` +
        "démarrez l'API pour exécuter ce contrôle (jamais compté comme réussi).",
    );
  } else {
    check("openapi.json récupérée", available.length > 40, `${available.length} chemins`);
    const unknown = called.filter((path) => !matchesAny(path, available));
    check(
      "toutes les routes appelées existent côté serveur",
      unknown.length === 0,
      unknown.length ? `inconnues : ${unknown.join(", ")}` : `${called.length} routes vérifiées`,
    );
  }

  console.log(`\nRésultat : ${failures.length === 0 ? "contrats respectés" : `${failures.length} contrat(s) rompu(s)`}`);
  for (const label of failures) console.log(`  ✗ ${label}`);
  process.exit(failures.length ? 1 : 0);
}

main().catch((error) => {
  console.error(`Erreur pendant le contrôle : ${error.message}`);
  process.exit(2);
});
