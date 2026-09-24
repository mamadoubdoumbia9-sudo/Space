/** @type {import('next').NextConfig} */

// Le navigateur ne parle JAMAIS directement au backend ni à 127.0.0.1 : toutes les
// requêtes partent en URL relative vers ce serveur Next, qui les relaie côté serveur
// vers l'API FastAPI. Cela évite toute exposition de l'URL interne et tout problème
// de CORS ou d'origine.
const API_PROXY_TARGET = process.env.API_PROXY_TARGET || "http://127.0.0.1:8000";

const nextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  async rewrites() {
    return [
      {
        source: "/proxy/:path*",
        destination: `${API_PROXY_TARGET}/:path*`,
      },
    ];
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Frame-Options", value: "SAMEORIGIN" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "X-Content-Type-Options", value: "nosniff" },
        ],
      },
    ];
  },
};

export default nextConfig;
