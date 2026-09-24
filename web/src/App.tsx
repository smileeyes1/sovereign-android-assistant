import { useEffect, useMemo, useState } from "react";
import type { ArtifactFormat, EducationRole, GeneratedArtifact, TeacherArtifactSpec, UserSettings } from "./types";
import { FORMAT_LABELS, ROLE_LABELS } from "./types";
import { localTemplateFor } from "./templates";
import { generateArtifacts, requestedFormats } from "./factory";
import { validateSpec } from "./quality";
import { externalAiDecision } from "./privacy";
import { generateSpecWithUserProvider } from "./providers";
import {
  clearArtifacts,
  clearSecrets,
  deleteArtifact,
  downloadArtifact,
  getSecret,
  listArtifacts,
  loadSettings,
  saveArtifact,
  saveSettings,
  setSecret,
  shareArtifact,
} from "./storage";
import "./styles.css";

type Tab = "chat" | "files" | "settings";
type Message = { who: "user" | "hakim"; text: string };

const FORMATS: ArtifactFormat[] = ["pdf", "html", "docx", "pptx", "xlsx", "png"];
const ROLES: EducationRole[] = ["general", "teacher", "student", "school_admin", "supervisor", "staff", "guardian"];

export default function App() {
  const [tab, setTab] = useState<Tab>("chat");
  const [prompt, setPrompt] = useState("");
  const [settings, setSettings] = useState<UserSettings>(() => loadSettings());
  const [formats, setFormats] = useState<ArtifactFormat[]>(["pdf"]);
  const [artifacts, setArtifacts] = useState<GeneratedArtifact[]>([]);
  const [working, setWorking] = useState(false);
  const [status, setStatus] = useState("جاهز");
  const [messages, setMessages] = useState<Message[]>([
    { who: "hakim", text: "اكتب ما تريد صنعه. سأحاول إنجازه محليًا أولًا ثم أستخدم موردك الشخصي فقط إذا لزم وكان مسموحًا." },
  ]);
  const [geminiInput, setGeminiInput] = useState("");
  const [openRouterInput, setOpenRouterInput] = useState("");
  const [secretState, setSecretState] = useState({ gemini: false, openrouter: false });

  useEffect(() => {
    void refreshArtifacts();
    void refreshSecretState();
  }, []);

  useEffect(() => {
    saveSettings(settings);
  }, [settings]);

  async function refreshArtifacts() {
    setArtifacts(await listArtifacts());
  }

  async function refreshSecretState() {
    setSecretState({
      gemini: Boolean(await getSecret("gemini")),
      openrouter: Boolean(await getSecret("openrouter")),
    });
  }

  const privacyHint = useMemo(() => {
    if (settings.role === "student") return "وضع الطالب: العمل المحلي فقط افتراضيًا، ولا إرسال خارجي.";
    return "محلي أولًا؛ لا إرسال خارجي لبيانات الطلبة الحساسة.";
  }, [settings.role]);

  function toggleFormat(format: ArtifactFormat) {
    setFormats((current) =>
      current.includes(format)
        ? current.length === 1
          ? current
          : current.filter((x) => x !== format)
        : [...current, format],
    );
  }

  function updateRole(role: EducationRole) {
    const next = { ...settings, role };
    if (role === "student") next.provider = "none";
    setSettings(next);
  }

  async function resolveSpec(text: string): Promise<{ spec: TeacherArtifactSpec; source: string }> {
    const local = localTemplateFor(text);
    if (local) return { spec: local, source: "قالب محلي موثوق" };

    if (settings.provider !== "none") {
      const decision = externalAiDecision(settings.role, text);
      if (!decision.allowed) throw new Error(decision.reason);
      const spec = await generateSpecWithUserProvider(text, settings);
      return { spec, source: "مورد المستخدم الشخصي" };
    }

    throw new Error(
      "هذا الطلب يحتاج صياغة ذكية غير موجودة في القوالب المحلية الحالية. يمكنك ربط موردك الشخصي من الإعدادات، بينما تبقى القوالب المحلية مجانية وتعمل دون ربط.",
    );
  }

  async function make() {
    const text = prompt.trim();
    if (!text || working) return;

    setWorking(true);
    setStatus("يفهم المقصد ويختبر المسار…");
    setMessages((m) => [...m, { who: "user", text }]);

    try {
      const { spec, source } = await resolveSpec(text);
      const specCheck = validateSpec(spec);
      if (!specCheck.ok) throw new Error(specCheck.errors.join(" "));

      const inferred = requestedFormats(text);
      const selected = /كل الصيغ|جميع الصيغ|كل الملفات|بكل الصيغ/.test(text)
        ? inferred
        : formats;

      setStatus("ينشئ الملفات ويفحصها…");
      const made = await generateArtifacts(spec, selected);
      for (const item of made) await saveArtifact(item);
      await refreshArtifacts();

      const names = made.map((x) => FORMAT_LABELS[x.format]).join("، ");
      setMessages((m) => [
        ...m,
        {
          who: "hakim",
          text: `تم التحقق من المواصفة والملفات نفسها. أُنشئت: ${names}. المصدر: ${source}. يمكنك تنزيلها أو مشاركتها من «ملفاتي».`,
        },
      ]);
      setStatus("اكتمل بعد التحقق");
      setPrompt("");
      setTab("files");
    } catch (error) {
      const message = error instanceof Error ? error.message : "تعذر إكمال المهمة.";
      setMessages((m) => [...m, { who: "hakim", text: message }]);
      setStatus("لم أعتبر المهمة مكتملة");
    } finally {
      setWorking(false);
    }
  }

  async function saveProviderSecret(provider: "gemini" | "openrouter") {
    const value = provider === "gemini" ? geminiInput : openRouterInput;
    if (!value.trim()) return;
    await setSecret(provider, value);
    if (provider === "gemini") setGeminiInput("");
    else setOpenRouterInput("");
    await refreshSecretState();
    setStatus("حُفظ الاعتماد محليًا على هذا الجهاز");
  }

  async function wipeIntelligence() {
    await clearSecrets();
    setSettings({ ...settings, provider: "none" });
    await refreshSecretState();
    setStatus("مُسحت اعتمادات الذكاء المحلية");
  }

  async function removeArtifact(id: string) {
    await deleteArtifact(id);
    await refreshArtifacts();
  }

  async function removeAllArtifacts() {
    await clearArtifacts();
    await refreshArtifacts();
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div>
          <strong>حكيم</strong>
          <span>مصنع المعلم الفلسطيني</span>
        </div>
        <div className="status-dot" aria-live="polite">{status}</div>
      </header>

      <nav className="tabs" aria-label="التنقل">
        <button className={tab === "chat" ? "active" : ""} onClick={() => setTab("chat")}>المحادثة</button>
        <button className={tab === "files" ? "active" : ""} onClick={() => setTab("files")}>ملفاتي</button>
        <button className={tab === "settings" ? "active" : ""} onClick={() => setTab("settings")}>الإعدادات</button>
      </nav>

      {tab === "chat" && (
        <main className="chat-layout">
          <section className="conversation" aria-live="polite">
            {messages.map((message, index) => (
              <div key={index} className={"bubble " + message.who}>
                <b>{message.who === "user" ? "أنت" : "حكيم"}</b>
                <p>{message.text}</p>
              </div>
            ))}
          </section>

          <section className="composer-card">
            <label htmlFor="role">الدور</label>
            <select id="role" value={settings.role} onChange={(e) => updateRole(e.target.value as EducationRole)}>
              {ROLES.map((role) => <option key={role} value={role}>{ROLE_LABELS[role]}</option>)}
            </select>

            <div className="privacy-hint">{privacyHint}</div>

            <label htmlFor="prompt">ماذا تريد أن أصنع؟</label>
            <textarea
              id="prompt"
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="مثال: أنشئ ورقة عمل الجمع ضمن ١٠ للصف الأول بكل الصيغ"
              rows={4}
              onKeyDown={(e) => {
                if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) void make();
              }}
            />

            <div className="format-grid" aria-label="صيغ المخرجات">
              {FORMATS.map((format) => (
                <button
                  type="button"
                  key={format}
                  className={formats.includes(format) ? "format active" : "format"}
                  onClick={() => toggleFormat(format)}
                  aria-pressed={formats.includes(format)}
                >
                  {FORMAT_LABELS[format]}
                </button>
              ))}
            </div>

            <button className="primary" disabled={working || !prompt.trim()} onClick={() => void make()}>
              {working ? "يجري التنفيذ…" : "أنجز"}
            </button>
          </section>
        </main>
      )}

      {tab === "files" && (
        <main className="files-page">
          <div className="section-head">
            <div>
              <h1>ملفاتي المحلية</h1>
              <p>تبقى هذه الملفات على هذا الجهاز ما لم تشاركها أنت.</p>
            </div>
            {artifacts.length > 0 && <button className="danger ghost" onClick={() => void removeAllArtifacts()}>مسح الكل</button>}
          </div>

          {artifacts.length === 0 ? (
            <div className="empty">لا توجد ملفات بعد.</div>
          ) : (
            <div className="file-list">
              {artifacts.map((artifact) => (
                <article className="file-card" key={artifact.id}>
                  <div>
                    <b>{artifact.name}</b>
                    <small>{FORMAT_LABELS[artifact.format]} · {new Date(artifact.createdAt).toLocaleString("ar-PS")}</small>
                  </div>
                  <div className="file-actions">
                    <button onClick={() => downloadArtifact(artifact)}>تنزيل</button>
                    <button onClick={() => void shareArtifact(artifact).then((ok) => {
                      if (!ok) downloadArtifact(artifact);
                    })}>مشاركة</button>
                    <button className="danger" onClick={() => void removeArtifact(artifact.id)}>حذف</button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </main>
      )}

      {tab === "settings" && (
        <main className="settings-page">
          <section className="settings-card">
            <h1>الخصوصية والدور</h1>
            <label htmlFor="settings-role">الدور التعليمي</label>
            <select id="settings-role" value={settings.role} onChange={(e) => updateRole(e.target.value as EducationRole)}>
              {ROLES.map((role) => <option key={role} value={role}>{ROLE_LABELS[role]}</option>)}
            </select>
            <p>لا يحتاج حكيم إلى اسمك الحقيقي أو رقم هويتك أو اسم مدرستك ليعمل. وضع الطالب يغلق الذكاء الخارجي افتراضيًا.</p>
          </section>

          <section className="settings-card">
            <h2>مورد الذكاء الشخصي</h2>
            <p>اختياري. لا يوجد مفتاح مركزي لحكيم، ولا ترقية مدفوعة تلقائية.</p>
            <label htmlFor="provider">المسار</label>
            <select
              id="provider"
              value={settings.provider}
              disabled={settings.role === "student"}
              onChange={(e) => setSettings({ ...settings, provider: e.target.value as UserSettings["provider"] })}
            >
              <option value="none">محلي فقط</option>
              <option value="gemini">Gemini — اعتماد شخصي</option>
              <option value="openrouter">OpenRouter — مورد شخصي مجاني</option>
            </select>

            <div className="secret-box">
              <label htmlFor="gemini-key">اعتماد Gemini الشخصي {secretState.gemini ? "✓ محفوظ محليًا" : ""}</label>
              <div className="inline">
                <input id="gemini-key" type="password" autoComplete="off" value={geminiInput} onChange={(e) => setGeminiInput(e.target.value)} placeholder="أدخل مفتاحك الشخصي إن رغبت" />
                <button onClick={() => void saveProviderSecret("gemini")}>حفظ محلي</button>
              </div>
            </div>

            <div className="secret-box">
              <label htmlFor="openrouter-key">اعتماد OpenRouter الشخصي {secretState.openrouter ? "✓ محفوظ محليًا" : ""}</label>
              <div className="inline">
                <input id="openrouter-key" type="password" autoComplete="off" value={openRouterInput} onChange={(e) => setOpenRouterInput(e.target.value)} placeholder="أدخل مفتاحك الشخصي إن رغبت" />
                <button onClick={() => void saveProviderSecret("openrouter")}>حفظ محلي</button>
              </div>
            </div>

            <button className="danger ghost" onClick={() => void wipeIntelligence()}>محو اعتمادات الذكاء</button>
          </section>

          <section className="settings-card">
            <h2>سياسة البيانات</h2>
            <ul>
              <li>المصنع المحلي لا يحتاج خادمًا.</li>
              <li>الملفات تحفظ محليًا داخل المتصفح حتى تختار تنزيلها أو مشاركتها.</li>
              <li>طلبات تحتوي بيانات طالب حساسة تُمنع من الذكاء الخارجي.</li>
              <li>لا تحليلات تتعقب الطلبة افتراضيًا.</li>
              <li>هذا المنتج لا يدّعي اعتمادًا رسميًا من وزارة التربية.</li>
            </ul>
          </section>
        </main>
      )}

      <footer>حكيم · محلي أولًا · مجاني حسب موارد المستخدم</footer>
    </div>
  );
}
