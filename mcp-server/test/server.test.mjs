import assert from "node:assert/strict";
import test from "node:test";
import { once } from "node:events";
import { createApp } from "../src/server.mjs";
import { loadConfig } from "../src/relay.mjs";

const bearer = "this-is-a-test-only-bearer-token-with-32-chars";
const config = loadConfig({
  HAKIM_RELAY_TOPIC: "hakim-cmd-abcdefghijklmnopqrstuvwxyz1234",
  HAKIM_RELAY_KEY: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN_1234567890",
  HAKIM_MCP_BEARER: bearer,
});

function parseMcpResponse(text) {
  const data = text.split("\n").find((line) => line.startsWith("data: "));
  return JSON.parse(data ? data.slice(6) : text);
}

test("MCP endpoint rejects unauthenticated access and advertises governed tools", async (t) => {
  const { app } = createApp(config);
  const http = app.listen(0, "127.0.0.1");
  await once(http, "listening");
  t.after(() => http.close());
  const address = http.address();
  const origin = `http://127.0.0.1:${address.port}`;

  const unauthorized = await fetch(`${origin}/mcp`, {
    method: "POST",
    headers: { "content-type": "application/json", accept: "application/json, text/event-stream" },
    body: JSON.stringify({ jsonrpc: "2.0", id: 1, method: "initialize", params: { protocolVersion: "2025-06-18", capabilities: {}, clientInfo: { name: "test", version: "1" } } }),
  });
  assert.equal(unauthorized.status, 401);

  const headers = { authorization: `Bearer ${bearer}`, "content-type": "application/json", accept: "application/json, text/event-stream" };
  const initialized = await fetch(`${origin}/mcp`, {
    method: "POST",
    headers,
    body: JSON.stringify({ jsonrpc: "2.0", id: 2, method: "initialize", params: { protocolVersion: "2025-06-18", capabilities: {}, clientInfo: { name: "test", version: "1" } } }),
  });
  assert.equal(initialized.status, 200);
  const sessionId = initialized.headers.get("mcp-session-id");
  assert.ok(sessionId);
  assert.equal(parseMcpResponse(await initialized.text()).result.serverInfo.name, "hakim-browser");

  const sessionHeaders = { ...headers, "mcp-session-id": sessionId };
  const notified = await fetch(`${origin}/mcp`, {
    method: "POST",
    headers: sessionHeaders,
    body: JSON.stringify({ jsonrpc: "2.0", method: "notifications/initialized" }),
  });
  assert.ok([200, 202].includes(notified.status));

  const listed = await fetch(`${origin}/mcp`, {
    method: "POST",
    headers: sessionHeaders,
    body: JSON.stringify({ jsonrpc: "2.0", id: 3, method: "tools/list", params: {} }),
  });
  assert.equal(listed.status, 200);
  const tools = parseMcpResponse(await listed.text()).result.tools;
  const byName = Object.fromEntries(tools.map((tool) => [tool.name, tool]));
  assert.equal(byName.browser_observe.annotations.readOnlyHint, true);
  assert.equal(byName.browser_click.annotations.readOnlyHint, false);
  assert.ok(byName.browser_type.description.includes("ممنوع"));
  assert.equal(byName.browser_navigate.inputSchema.properties.url.type, "string");
});
