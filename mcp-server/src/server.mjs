import { randomUUID, timingSafeEqual } from "node:crypto";
import express from "express";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { isInitializeRequest } from "@modelcontextprotocol/sdk/types.js";
import { z } from "zod";
import { loadConfig, RelayClient } from "./relay.mjs";

const VERSION = "0.1.0";
const SENSITIVE_TARGET = /(?:password|passcode|otp|pin|cvv|cvc|secret|token|api.?key|كلمة.?المرور|رمز.?التحقق|رمز.?الأمان|مفتاح.?سري)/i;

function isSafeHttpUrl(value) {
  try {
    const url = new URL(value);
    return ["http:", "https:"].includes(url.protocol) && !url.username && !url.password && !SENSITIVE_TARGET.test(url.search);
  } catch {
    return false;
  }
}

function sameToken(left, right) {
  const a = Buffer.from(left || "", "utf8");
  const b = Buffer.from(right || "", "utf8");
  return a.length === b.length && a.length > 0 && timingSafeEqual(a, b);
}

function bearerGuard(expected) {
  return (req, res, next) => {
    const header = String(req.get("authorization") || "");
    const supplied = header.startsWith("Bearer ") ? header.slice(7) : "";
    if (!sameToken(supplied, expected)) {
      res.set("WWW-Authenticate", 'Bearer realm="hakim-browser"').status(401).json({ error: "unauthorized" });
      return;
    }
    next();
  };
}

function toolResult(value) {
  const safe = value && typeof value === "object" ? value : { ok: false, error: "invalid_phone_result" };
  return {
    structuredContent: safe,
    content: [{ type: "text", text: JSON.stringify(safe) }],
    isError: safe.ok === false || ["error", "expired", "rejected"].includes(safe.status),
  };
}

export function createHakimMcpServer(relay) {
  const server = new McpServer(
    { name: "hakim-browser", version: VERSION },
    { instructions: "ابدأ بالحالة ثم مشاهدة الصفحة. بعد كل فعل أعد المشاهدة. لا تطلب أو تكتب كلمات مرور أو رموز تحقق أو بيانات دفع، ولا تتجاوز CAPTCHA. الأفعال المتغيرة للحالة لا تنفذ إلا بعد موافقة الهاتف." },
  );

  server.registerTool("hakim_status", {
    title: "حالة حكيم",
    description: "تحقق من اتصال هاتف حكيم والمتصفح قبل بدء المهمة أو عند فشل أي خطوة.",
    inputSchema: {},
    annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false },
  }, async () => toolResult(await relay.call("status", {})));

  server.registerTool("browser_observe", {
    title: "مشاهدة متصفح حكيم",
    description: "اقرأ عنوان الصفحة ورابطها والعناصر المرئية غير الحساسة. استخدمه قبل أي فعل وبعده للتحقق.",
    inputSchema: { max_elements: z.number().int().min(1).max(60).default(40) },
    annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: true },
  }, async ({ max_elements }) => toolResult(await relay.call("browser_observe", { max_elements })));

  const actionTool = (name, title, description, schema, build) => server.registerTool(name, {
    title,
    description: `${description} يتطلب موافقة ظاهرة على هاتف حكيم، ثم يجب استدعاء browser_observe للتحقق.`,
    inputSchema: schema,
    annotations: { readOnlyHint: false, destructiveHint: false, openWorldHint: true },
  }, async (args) => toolResult(await relay.call("browser_action", build(args))));

  actionTool("browser_navigate", "فتح صفحة في حكيم", "افتح رابط HTTP أو HTTPS صريحًا داخل متصفح حكيم.",
    { url: z.string().url().max(5000).refine(isSafeHttpUrl, "الرابط يجب أن يكون HTTP/HTTPS بلا اعتماد أو سر في الاستعلام") }, ({ url }) => ({ action: "navigate", url }));
  actionTool("browser_click", "النقر في حكيم", "انقر عنصرًا مرئيًا باستخدام مرجع العنصر أو نصه؛ لا تستخدمه لتأكيد شراء أو حذف أو إرسال دون تفويض صريح.",
    { target: z.string().min(1).max(500) }, ({ target }) => ({ action: "click", target }));
  actionTool("browser_type", "الكتابة في حكيم", "اكتب نصًا عابرًا في حقل غير حساس. ممنوع لكلمات المرور ورموز التحقق وبيانات الدفع والأسرار.",
    { target: z.string().min(1).max(500).refine((value) => !SENSITIVE_TARGET.test(value), "الحقل الحساس يُدخل يدويًا على الهاتف"), value: z.string().min(1).max(6000) }, ({ target, value }) => ({ action: "type", target, value }));
  actionTool("browser_select", "اختيار قيمة في حكيم", "اختر قيمة من قائمة غير حساسة.",
    { target: z.string().min(1).max(500), value: z.string().min(1).max(1000) }, ({ target, value }) => ({ action: "select", target, value }));
  actionTool("browser_scroll", "تمرير صفحة حكيم", "مرر الصفحة أو عنصرًا مرئيًا بمقدار محدود.",
    { target: z.string().max(500).default(""), dx: z.number().int().min(-3000).max(3000).default(0), dy: z.number().int().min(-3000).max(3000).default(900) },
    ({ target, dx, dy }) => ({ action: "scroll", target, dx, dy }));
  actionTool("browser_back", "الرجوع في حكيم", "ارجع صفحة واحدة داخل سجل متصفح حكيم.", {}, () => ({ action: "back" }));
  actionTool("browser_reload", "إعادة تحميل صفحة حكيم", "أعد تحميل الصفحة الحالية.", {}, () => ({ action: "reload" }));
  return server;
}

export function createApp(config = loadConfig()) {
  const relay = new RelayClient(config);
  const app = express();
  const transports = new Map();
  const servers = new Map();
  app.disable("x-powered-by");
  app.use(express.json({ limit: "96kb", strict: true }));

  app.get("/healthz", (_req, res) => res.json({ ok: true, service: "hakim-browser-mcp", version: VERSION }));
  app.post("/relay/result", async (req, res) => {
    try {
      if (req.body?.kind === "hc1_poll") {
        const body = await relay.proxyPoll(req.body);
        res.type("application/x-ndjson").status(200).send(body);
        return;
      }
      const accepted = relay.acceptResult(req.body);
      res.status(accepted ? 202 : 404).json({ accepted });
    } catch (error) {
      res.status(401).json({ error: "invalid_signed_phone_message" });
    }
  });

  app.use("/mcp", bearerGuard(config.bearer));
  app.post("/mcp", async (req, res) => {
    try {
      const sessionId = String(req.get("mcp-session-id") || "");
      let transport = sessionId ? transports.get(sessionId) : undefined;
      if (!transport && !sessionId && isInitializeRequest(req.body)) {
        let server;
        transport = new StreamableHTTPServerTransport({
          sessionIdGenerator: () => randomUUID(),
          onsessioninitialized: (id) => {
            transports.set(id, transport);
            servers.set(id, server);
          },
        });
        server = createHakimMcpServer(relay);
        transport.onclose = () => {
          const id = transport.sessionId;
          if (id) { transports.delete(id); servers.delete(id); }
        };
        await server.connect(transport);
      }
      if (!transport) {
        res.status(400).json({ jsonrpc: "2.0", error: { code: -32000, message: "Invalid or missing MCP session" }, id: null });
        return;
      }
      await transport.handleRequest(req, res, req.body);
    } catch (_error) {
      if (!res.headersSent) res.status(500).json({ jsonrpc: "2.0", error: { code: -32603, message: "Internal server error" }, id: null });
    }
  });

  const sessionRequest = async (req, res) => {
    const transport = transports.get(String(req.get("mcp-session-id") || ""));
    if (!transport) { res.status(400).send("Invalid or missing MCP session"); return; }
    await transport.handleRequest(req, res);
  };
  app.get("/mcp", sessionRequest);
  app.delete("/mcp", sessionRequest);
  return { app, relay };
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const port = Math.min(Math.max(Number(process.env.PORT || 3000), 1), 65535);
  const { app } = createApp();
  app.listen(port, "0.0.0.0", () => console.log(`hakim-browser-mcp listening on ${port}`));
}
