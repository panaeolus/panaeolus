import { strict as assert } from "node:assert";
import { serveRequest } from "../tools/serve";

for (const [path, method, status] of [
  ["/", "GET", 200], ["/", "HEAD", 200],
  ["/app.js", "GET", 200], ["/vendor/csound.js", "GET", 200],
  ["/missing", "GET", 404], ["/", "POST", 405],
] as const) {
  const response = await serveRequest(new Request(`http://localhost${path}`, { method }));
  assert.equal(response.status, status, `${method} ${path}`);
  assert.equal(response.headers.get("Cross-Origin-Opener-Policy"), "same-origin");
  assert.equal(response.headers.get("Cross-Origin-Embedder-Policy"), "require-corp");
  assert.equal(response.headers.get("Cross-Origin-Resource-Policy"), "same-origin");
  assert.equal(response.headers.get("Cache-Control"), "no-store");
  if (method === "HEAD") assert.equal(await response.text(), "");
}
console.log("PASS: document, assets, and error responses retain SharedArrayBuffer isolation headers.");
