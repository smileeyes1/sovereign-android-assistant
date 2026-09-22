import crypto from "node:crypto";

export const CARRIER_PREFIX = "HC1.";
export const CARRIER_AAD = "HAKIM-CARRIER-v1";
export const ALLOWED_OPS = ["status","ui","notifications","screenshot","action","launch"] as const;
export type HakimOp = typeof ALLOWED_OPS[number];

export type DeviceCredential = {
  v: 1;
  topic: string;
  relayKey: string;
  resultTopic: string;
  callbackSecret: string;
  pairToken: string;
};

const b64u = (b: Buffer) => b.toString("base64url");
const fromB64u = (s: string) => Buffer.from(s, "base64url");
export const randomSecret = (bytes=32) => b64u(crypto.randomBytes(bytes));

export function createDeviceCredential(): DeviceCredential {
  return {
    v: 1,
    topic: "hakim_cmd_" + randomSecret(18),
    relayKey: randomSecret(48),
    resultTopic: "hakim_result_" + randomSecret(18),
    callbackSecret: randomSecret(32),
    pairToken: randomSecret(32)
  };
}

export function encodeBearer(c: DeviceCredential): string {
  return "HAKIM-B1." + b64u(Buffer.from(JSON.stringify(c), "utf8"));
}

export function decodeBearer(raw: string): DeviceCredential {
  if (!raw.startsWith("HAKIM-B1.")) throw new Error("invalid_bearer");
  const value = JSON.parse(fromB64u(raw.slice(9)).toString("utf8")) as DeviceCredential;
  if (value.v !== 1) throw new Error("unsupported_bearer");
  if (!/^[A-Za-z0-9_-]{20,120}$/.test(value.topic)) throw new Error("invalid_topic");
  if (!/^[A-Za-z0-9_-]{40,100}$/.test(value.relayKey)) throw new Error("invalid_key");
  if (!/^[A-Za-z0-9_-]{20,120}$/.test(value.resultTopic)) throw new Error("invalid_result_topic");
  if (!/^[A-Za-z0-9_-]{32,100}$/.test(value.callbackSecret)) throw new Error("invalid_callback_secret");
  if (!/^[A-Za-z0-9_-]{32,256}$/.test(value.pairToken)) throw new Error("invalid_pair_token");
  return value;
}

export function makeEnvelope(relayKey: string, op: HakimOp, payload: unknown, ttlMs=60_000) {
  const requestId = "chatgpt-" + randomSecret(12);
  const expiresAt = Date.now() + ttlMs;
  const payloadB64 = b64u(Buffer.from(JSON.stringify(payload ?? {}), "utf8"));
  const canonical = [requestId, op, String(expiresAt), payloadB64].join("\n");
  const signature = crypto.createHmac("sha256", relayKey).update(canonical, "utf8").digest("hex");
  return {request_id: requestId, op, expires_at_ms: expiresAt, payload_b64: payloadB64, signature};
}

export function encryptCarrier(relayKey: string, envelope: object): string {
  const nonce = crypto.randomBytes(12);
  const key = crypto.createHash("sha256").update(CARRIER_AAD + "\0" + relayKey, "utf8").digest();
  const cipher = crypto.createCipheriv("aes-256-gcm", key, nonce);
  cipher.setAAD(Buffer.from(CARRIER_AAD, "utf8"));
  const body = Buffer.from(JSON.stringify(envelope), "utf8");
  const ciphertext = Buffer.concat([cipher.update(body), cipher.final()]);
  const tag = cipher.getAuthTag();
  return CARRIER_PREFIX + b64u(Buffer.concat([nonce, ciphertext, tag]));
}

export function pairingUrl(origin: string, c: DeviceCredential): string {
  const resultUrl = origin.replace(/\/$/,"") + "/relay/result/" +
    encodeURIComponent(c.resultTopic) + "/" + encodeURIComponent(c.callbackSecret);
  const q = new URLSearchParams({
    token: c.pairToken,
    relay_topic: c.topic,
    result_url: resultUrl,
    relay_key: c.relayKey
  });
  return "hakim://pair?" + q.toString();
}
