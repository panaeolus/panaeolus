import { resolve } from "node:path";
import { build, root } from "./build";

const routes: Record<string, string> = {
  "/": "playground/index.html",
  "/app.js": "dist/web/app.js",
  "/style.css": "playground/style.css",
  "/playground/playground.mal": "playground/playground.mal",
  "/dist/make-a-lisp.orc": "dist/make-a-lisp.orc",
  "/dist/panaeolus.orc": "dist/panaeolus.orc",
  "/vendor/csound.js": "dist/web/csound.js",
};

export async function serveRequest(request: Request): Promise<Response> {
  const headers = {
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    "Cross-Origin-Opener-Policy": "same-origin",
    "Cross-Origin-Embedder-Policy": "require-corp",
    "Cross-Origin-Resource-Policy": "same-origin",
  };
  if (request.method !== "GET" && request.method !== "HEAD") {
    return new Response("Method not allowed", { status: 405, headers });
  }
  const path = routes[new URL(request.url).pathname];
  if (!path) return new Response("Not found", { status: 404, headers });
  const file = Bun.file(resolve(root, path));
  if (!await file.exists()) return new Response("Build file missing", { status: 404, headers });
  return new Response(request.method === "HEAD" ? null : file, {
    headers: {
      "Content-Type": file.type || "text/plain; charset=utf-8",
      ...headers,
    },
  });
}

if (import.meta.main) {
  await build();
  const server = Bun.serve({
    hostname: "127.0.0.1",
    port: Number(process.env.PORT || 3000),
    fetch: serveRequest,
  });
  console.log(`Panaeolus playground: ${server.url}`);
  console.log("SharedArrayBuffer enabled. After source changes, restart bun run dev and reload the page.");
}
