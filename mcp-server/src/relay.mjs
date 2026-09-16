import {
  createCipheriv,
  createDecipheriv,
  createHash,
  createHmac,
  randomBytes,
  randomUUID,
  timingSafeEqual,
} from "node:crypto";

const CARRIER_PREFIX = "HC1.";
const CARRIER_AAD = "HAKIM-CARRIER-v1";
const REQUEST_ID = /^[A-Za-z0-9._:-]{8,128}$/;
const TOPIC = /^hakim-cmd-[A-Za-z0-9_-]{20,100}$/;
const RELAY_KEY = /^[A-Za-z0-9_-]{40,100}$/;
const SIGNATURE = /^[0-9a-f]{64}$/;
const RESULT_MAX_BYTES = 1_000_000;

function base64url(input) {
  return Buffer.from(input).toString("base64url");
}

function fromBase64url(value) {
  return Buffer.from(value, "base64url");
}

function sha256(input) {
  return createHash("sha256").update(input).digest();
}

function hmacHex(key, value) {
  return createHmac("sha256", key).update(value, "utf8").digest("hex");
}

function equalHex(left, right) {
  if (!SIGNATURE.test(left) || !SIGNATURE.test(right)) return false;
  return timingSafeEqual(Buffer.from(left, "hex"), Buffer.from(right, "hex"));
}

function requireHttpsOrigin(value, name) {
  const parsed = new URL(value);
  if (parsed.protocol !== "https:" || parsed.username || parsed.password) {
    throw new Error(`${name} must be an HTTPS origin without embedded credentials`);
  }
  return parsed.origin;
}

export function loadConfig(env = process.env) {
  const topic = String(env.HAKIM_RELAY_TOPIC || "").trim();
  const relayKey = String(env.HAKIM_RELAY_KEY || "").trim();
  const bearer = String(env.HAKIM_MCP_BEARER || "").trim();
  if (!TOPIC.test(topic)) throw new Error("HAKIM_RELAY_TOPIC is missing or invalid");
  if (!RELAY_KEY.test(relayKey)) throw new Error("HAKIM_RELAY_KEY is missing or invalid");
  if (bearer.length < 32 || bearer.length > 512) throw new Error("HAKIM_MCP_BEARER must contain 32-512 characters");
  const timeoutMs = Math.min(Math.max(Number(env.HAKIM_COMMAND_TIMEOUT_MS || 75_000), 5_000), 120_000);
  const ntfyOrigin = requireHttpsOrigin(env.HAKIM_NTFY_ORIGIN || "https://ntfy.sh", "HAKIM_NTFY_ORIGIN");
  return Object.freeze({ topic, relayKey, bearer, timeoutMs, ntfyOrigin });
}

export function buildCarrier(config, op, payload = {}, now = Date.now(), requestId = `mcp-${randomUUID()}`) {
  if (!REQUEST_ID.test(requestId)) throw new Error("invalid request id");
  if (!/^[a-z_]{2,40}$/.test(op)) throw new Error("invalid operation");
  const payloadBytes = Buffer.from(JSON.stringify(payload), "utf8");
  if (payloadBytes.length > 24_000) throw new Error("payload is too large");
  const payloadB64 = base64url(payloadBytes);
  const expiresAtMs = now + Math.min(config.timeoutMs + 20_000, 180_000);
  const canonical = `${requestId}\n${op}\n${expiresAtMs}\n${payloadB64}`;
  const envelope = JSON.stringify({
    request_id: requestId,
    op,
    expires_at_ms: expiresAtMs,
    payload_b64: payloadB64,
    signature: hmacHex(config.relayKey, canonical),
  });
  const nonce = randomBytes(12);
  const key = sha256(`${CARRIER_AAD}\u0000${config.relayKey}`);
  const cipher = createCipheriv("aes-256-gcm", key, nonce);
  cipher.setAAD(Buffer.from(CARRIER_AAD, "utf8"));
  const encrypted = Buffer.concat([cipher.update(envelope, "utf8"), cipher.final(), cipher.getAuthTag()]);
  return { requestId, carrier: CARRIER_PREFIX + base64url(Buffer.concat([nonce, encrypted])) };
}

export function decodeCarrierForTest(config, carrier) {
  if (!carrier.startsWith(CARRIER_PREFIX)) throw new Error("invalid carrier prefix");
  const packed = fromBase64url(carrier.slice(CARRIER_PREFIX.length));
  const nonce = packed.subarray(0, 12);
  const ciphertext = packed.subarray(12, -16);
  const tag = packed.subarray(-16);
  const key = sha256(`${CARRIER_AAD}\u0000${config.relayKey}`);
  const decipher = createDecipheriv("aes-256-gcm", key, nonce);
  decipher.setAAD(Buffer.from(CARRIER_AAD, "utf8"));
  decipher.setAuthTag(tag);
  return JSON.parse(Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString("utf8"));
}

export function signPhoneResultForTest(config, requestId, status, result, sentAtMs = Date.now()) {
  const resultB64 = base64url(Buffer.from(JSON.stringify(result), "utf8"));
  return {
    request_id: requestId,
    status,
    sent_at_ms: sentAtMs,
    result_b64: resultB64,
    signature: hmacHex(config.relayKey, `${requestId}\n${status}\n${sentAtMs}\n${resultB64}`),
  };
}

export function verifyPhoneResult(config, body, now = Date.now()) {
  if (!body || typeof body !== "object") throw new Error("invalid result body");
  const requestId = String(body.request_id || "");
  const status = String(body.status || "");
  const sentAtMs = Number(body.sent_at_ms || 0);
  const resultB64 = String(body.result_b64 || "");
  const signature = String(body.signature || "").toLowerCase();
  if (!REQUEST_ID.test(requestId) || !/^[a-z_]{2,32}$/.test(status)) throw new Error("invalid result identity");
  if (!Number.isSafeInteger(sentAtMs) || Math.abs(now - sentAtMs) > 180_000) throw new Error("stale result");
  if (!resultB64 || resultB64.length > Math.ceil(RESULT_MAX_BYTES * 4 / 3)) throw new Error("invalid result size");
  const expected = hmacHex(config.relayKey, `${requestId}\n${status}\n${sentAtMs}\n${resultB64}`);
  if (!equalHex(expected, signature)) throw new Error("invalid result signature");
  const decoded = fromBase64url(resultB64);
  if (decoded.length > RESULT_MAX_BYTES) throw new Error("result is too large");
  const result = JSON.parse(decoded.toString("utf8"));
  if (!result || typeof result !== "object" || Array.isArray(result)) throw new Error("result must be an object");
  return { requestId, status, result, sentAtMs };
}

export function verifyPollRequest(config, body, now = Date.now()) {
  const topic = String(body?.topic || "");
  const since = String(body?.since || "");
  const issuedAtMs = Number(body?.issued_at_ms || 0);
  const signature = String(body?.signature || "").toLowerCase();
  if (topic !== config.topic || !TOPIC.test(topic)) throw new Error("invalid poll topic");
  if (!/^(?:[A-Za-z0-9_-]{4,64}|[0-9]{1,4}[smhd])$/.test(since)) throw new Error("invalid poll cursor");
  if (!Number.isSafeInteger(issuedAtMs) || Math.abs(now - issuedAtMs) > 120_000) throw new Error("stale poll request");
  const expected = hmacHex(config.relayKey, `${topic}\n${since}\n${issuedAtMs}`);
  if (!equalHex(expected, signature)) throw new Error("invalid poll signature");
  return { topic, since };
}

export function signPollForTest(config, since = "10m", issuedAtMs = Date.now()) {
  return {
    kind: "hc1_poll",
    topic: config.topic,
    since,
    issued_at_ms: issuedAtMs,
    signature: hmacHex(config.relayKey, `${config.topic}\n${since}\n${issuedAtMs}`),
  };
}

export class RelayClient {
  constructor(config, { fetchImpl = globalThis.fetch } = {}) {
    this.config = config;
    this.fetch = fetchImpl;
    this.pending = new Map();
  }

  async call(op, payload = {}) {
    if (this.pending.size >= 16) throw new Error("relay is busy");
    const { requestId, carrier } = buildCarrier(this.config, op, payload);
    let timer;
    const resultPromise = new Promise((resolve, reject) => {
      timer = setTimeout(() => {
        this.pending.delete(requestId);
        reject(new Error("phone response timed out"));
      }, this.config.timeoutMs);
      this.pending.set(requestId, { resolve, reject, timer });
    });
    try {
      const response = await this.fetch(`${this.config.ntfyOrigin}/${this.config.topic}`, {
        method: "POST",
        headers: { "content-type": "text/plain; charset=utf-8" },
        body: carrier,
        signal: AbortSignal.timeout(15_000),
      });
      if (!response.ok) throw new Error(`relay publish failed (${response.status})`);
    } catch (error) {
      const pending = this.pending.get(requestId);
      if (pending) clearTimeout(pending.timer);
      this.pending.delete(requestId);
      throw error;
    }
    return resultPromise;
  }

  acceptResult(body) {
    const verified = verifyPhoneResult(this.config, body);
    const pending = this.pending.get(verified.requestId);
    if (!pending) return false;
    clearTimeout(pending.timer);
    this.pending.delete(verified.requestId);
    pending.resolve({ request_id: verified.requestId, status: verified.status, ...verified.result });
    return true;
  }

  async proxyPoll(body) {
    const { topic, since } = verifyPollRequest(this.config, body);
    const url = `${this.config.ntfyOrigin}/${topic}/json?poll=1&since=${encodeURIComponent(since)}`;
    const response = await this.fetch(url, { headers: { accept: "application/x-ndjson" }, signal: AbortSignal.timeout(20_000) });
    if (!response.ok) throw new Error(`relay poll failed (${response.status})`);
    const text = await response.text();
    if (Buffer.byteLength(text, "utf8") > 256_000) throw new Error("relay poll response is too large");
    return text;
  }
}
