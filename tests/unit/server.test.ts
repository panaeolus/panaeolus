import { describe, expect, test } from "bun:test";
import { resolve } from "node:path";
import { root } from "../../tools/build";
import { serveRequest } from "../../tools/serve";

const isolationHeaders = {
  "Cross-Origin-Opener-Policy": "same-origin",
  "Cross-Origin-Embedder-Policy": "require-corp",
  "Cross-Origin-Resource-Policy": "same-origin",
  "X-Content-Type-Options": "nosniff",
  "Cache-Control": "no-store",
};

function request(path: string, method = "GET") {
  return serveRequest(new Request(`http://localhost${path}`, { method }));
}

function checkHeaders(response: Response) {
  for (const [name, value] of Object.entries(isolationHeaders)) {
    expect(response.headers.get(name)).toBe(value);
  }
}

describe("playground HTTP routes", () => {
  test("serves the playground document and ignores the query string", async () => {
    const response = await request("/?reload=1");
    expect(response.status).toBe(200);
    expect(response.headers.get("Content-Type")).toContain("text/html");
    expect(await response.text()).toBe(
      await Bun.file(resolve(root, "playground/index.html")).text(),
    );
    checkHeaders(response);
  });

  test("HEAD retains GET headers without sending the file body", async () => {
    const response = await request("/style.css", "HEAD");
    const get = await request("/style.css");
    expect(response.status).toBe(200);
    expect([...response.headers]).toEqual([...get.headers]);
    expect(await response.text()).toBe("");
    expect((await get.text()).length).toBeGreaterThan(0);
    checkHeaders(response);
  });

  test.each(["POST", "PUT", "PATCH", "DELETE", "OPTIONS"])(
    "rejects %s while retaining isolation headers",
    async (method) => {
      const response = await request("/", method);
      expect(response.status).toBe(405);
      checkHeaders(response);
    },
  );

  test.each([
    "/missing",
    "/package.json",
    "/src/runtime.orc",
    "/.git/config",
    "/node_modules/@csound/browser/package.json",
    "/../package.json",
    "/dist/../../package.json",
    "/%2e%2e/package.json",
    "/dist/%2e%2e/%2e%2e/package.json",
    "/%2e%2e%2fpackage.json",
    "/%252e%252e%252fpackage.json",
    "/__proto__",
    "/constructor",
    "/toString",
  ])("keeps unlisted path %s inaccessible", async (path) => {
    const response = await request(path);
    expect(response.status).toBe(404);
    expect(await response.text()).toBe("Not found");
    checkHeaders(response);
  });
});
