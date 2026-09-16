import assert from "node:assert/strict";
import test from "node:test";
import {
  buildCarrier,
  decodeCarrierForTest,
  loadConfig,
  signPhoneResultForTest,
  signPollForTest,
  verifyPhoneResult,
  verifyPollRequest,
} from "../src/relay.mjs";

const env = {
  HAKIM_RELAY_TOPIC: "hakim-cmd-abcdefghijklmnopqrstuvwxyz1234",
  HAKIM_RELAY_KEY: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN_1234567890",
  HAKIM_MCP_BEARER: "this-is-a-test-only-bearer-token-with-32-chars",
};
const config = loadConfig(env);

test("configuration fails closed when a secret is missing", () => {
  assert.throws(() => loadConfig({ ...env, HAKIM_RELAY_KEY: "" }), /missing or invalid/);
  assert.throws(() => loadConfig({ ...env, HAKIM_MCP_BEARER: "short" }), /32-512/);
});

test("HC1 carrier round-trips and authenticates the command", () => {
  const now = 1_800_000_000_000;
  const { requestId, carrier } = buildCarrier(config, "browser_observe", { max_elements: 30 }, now, "mcp-test-request-0001");
  const envelope = decodeCarrierForTest(config, carrier);
  assert.equal(requestId, "mcp-test-request-0001");
  assert.equal(envelope.op, "browser_observe");
  assert.equal(JSON.parse(Buffer.from(envelope.payload_b64, "base64url").toString("utf8")).max_elements, 30);
  const packed = Buffer.from(carrier.slice(4), "base64url");
  packed[15] ^= 0x01;
  const damaged = `HC1.${packed.toString("base64url")}`;
  assert.throws(() => decodeCarrierForTest(config, damaged));
});

test("phone results reject tampering and replay-age violations", () => {
  const now = 1_800_000_000_000;
  const signed = signPhoneResultForTest(config, "mcp-test-request-0002", "ok", { ok: true, title: "صفحة" }, now);
  assert.equal(verifyPhoneResult(config, signed, now).result.title, "صفحة");
  assert.throws(() => verifyPhoneResult(config, { ...signed, status: "error" }, now), /signature/);
  assert.throws(() => verifyPhoneResult(config, signed, now + 181_000), /stale/);
});

test("fallback polling is signed and bound to the paired topic", () => {
  const now = 1_800_000_000_000;
  const poll = signPollForTest(config, "10m", now);
  assert.deepEqual(verifyPollRequest(config, poll, now), { topic: config.topic, since: "10m" });
  assert.throws(() => verifyPollRequest(config, { ...poll, since: "20m" }, now), /signature/);
});
