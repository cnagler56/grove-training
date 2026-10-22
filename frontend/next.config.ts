import type { NextConfig } from "next";

/**
 * The browser only ever talks to this site's own address; API calls are
 * forwarded to the Spring Boot backend from here. That keeps the sign-in
 * cookie "first-party", which iPhones/Safari require (they block cookies
 * between two different sites, like a .vercel.app page and a .railway.app API).
 *
 * BACKEND_URL: the backend's address. Locally it defaults to localhost:8084;
 * on Vercel set it to the Railway URL (e.g. https://grove-server-production.up.railway.app).
 */
const backend = (process.env.BACKEND_URL ?? "http://localhost:8084").replace(/\/+$/, "");

const nextConfig: NextConfig = {
  async rewrites() {
    return ["/api/:path*", "/me", "/login", "/logout", "/register"].map((source) => ({
      source,
      destination: `${backend}${source}`,
    }));
  },
};

export default nextConfig;
