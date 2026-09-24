import { openDB } from "idb";
import type { GeneratedArtifact, UserSettings } from "./types";

const DB_NAME = "hakim-local-v1";
const ARTIFACTS = "artifacts";
const SECRETS = "secrets";

const dbPromise = openDB(DB_NAME, 1, {
  upgrade(db) {
    if (!db.objectStoreNames.contains(ARTIFACTS)) {
      db.createObjectStore(ARTIFACTS, { keyPath: "id" });
    }
    if (!db.objectStoreNames.contains(SECRETS)) {
      db.createObjectStore(SECRETS);
    }
  },
});

export async function saveArtifact(artifact: GeneratedArtifact): Promise<void> {
  const db = await dbPromise;
  await db.put(ARTIFACTS, artifact);
}

export async function listArtifacts(): Promise<GeneratedArtifact[]> {
  const db = await dbPromise;
  const all = (await db.getAll(ARTIFACTS)) as GeneratedArtifact[];
  return all.sort((a, b) => b.createdAt - a.createdAt);
}

export async function deleteArtifact(id: string): Promise<void> {
  const db = await dbPromise;
  await db.delete(ARTIFACTS, id);
}

export async function clearArtifacts(): Promise<void> {
  const db = await dbPromise;
  await db.clear(ARTIFACTS);
}

export async function setSecret(key: "gemini" | "openrouter", value: string): Promise<void> {
  const db = await dbPromise;
  if (!value.trim()) {
    await db.delete(SECRETS, key);
    return;
  }
  await db.put(SECRETS, value.trim(), key);
}

export async function getSecret(key: "gemini" | "openrouter"): Promise<string> {
  const db = await dbPromise;
  return ((await db.get(SECRETS, key)) as string | undefined) ?? "";
}

export async function clearSecrets(): Promise<void> {
  const db = await dbPromise;
  await db.clear(SECRETS);
}

const SETTINGS_KEY = "hakim-settings-v1";

export function defaultSettings(): UserSettings {
  return { role: "general", provider: "none", freeOnly: true };
}

export function loadSettings(): UserSettings {
  try {
    const raw = localStorage.getItem(SETTINGS_KEY);
    if (!raw) return defaultSettings();
    const parsed = JSON.parse(raw) as Partial<UserSettings>;
    return {
      role: parsed.role ?? "general",
      provider: parsed.provider ?? "none",
      freeOnly: true,
    };
  } catch {
    return defaultSettings();
  }
}

export function saveSettings(settings: UserSettings): void {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify({ ...settings, freeOnly: true }));
}

export function downloadArtifact(artifact: GeneratedArtifact): void {
  const url = URL.createObjectURL(artifact.blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = artifact.name;
  a.rel = "noopener";
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
}

export async function shareArtifact(artifact: GeneratedArtifact): Promise<boolean> {
  if (!("share" in navigator)) return false;
  const file = new File([artifact.blob], artifact.name, { type: artifact.mime });
  const data: ShareData = { files: [file], title: artifact.name };
  if ("canShare" in navigator && !(navigator as Navigator & { canShare?: (data: ShareData) => boolean }).canShare?.(data)) {
    return false;
  }
  await navigator.share(data);
  return true;
}
