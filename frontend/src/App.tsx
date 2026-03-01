import {
  Activity,
  ArchiveRestore,
  BarChart3,
  Bot,
  CalendarDays,
  Database,
  Download,
  Eye,
  FileClock,
  Languages,
  Pencil,
  PlusCircle,
  RefreshCw,
  Save,
  Send,
  Settings2,
  ShieldCheck,
  Sparkles,
  Trash2,
  Upload
} from "lucide-react";
import type { FormEvent } from "react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { apiRequest } from "./api";
import type {
  AnalyticsPayload,
  AuditLogEntry,
  BackupItem,
  DashboardStatus,
  DashboardUpdate,
  MessageState,
  MessageTone
} from "./types";

const TOKEN_STORAGE_KEY = "dashboardToken";
const LANG_STORAGE_KEY = "dashboardLangOverride";

type Locale = "de" | "en";
type MessageSlot = "global" | "action" | "config" | "add" | "edit" | "data";
type BusyKey =
  | "reload"
  | "sync"
  | "test"
  | "config"
  | "add"
  | "edit"
  | "backup"
  | "restore"
  | "importJson"
  | "importCsv"
  | "exportJson"
  | "exportCsv";

interface ConfigField {
  key: string;
  label: string;
  placeholder?: string;
}

const CONFIG_FIELDS: ConfigField[] = [
  { key: "guild_id", label: "Guild ID" },
  { key: "channel_id", label: "Channel ID" },
  { key: "timezone", label: "Timezone", placeholder: "Europe/Berlin" },
  { key: "schedule_day", label: "Schedule Day", placeholder: "MONDAY..SUNDAY" },
  { key: "schedule_time", label: "Schedule Time", placeholder: "HH:mm" },
  { key: "title_emoji", label: "Title Emoji" },
  { key: "title_emoji_id", label: "Title Emoji ID" },
  { key: "title_emoji_animated", label: "Title Emoji Animated" },
  { key: "title_text", label: "Title Text" },
  { key: "added_emoji", label: "Added Emoji" },
  { key: "added_emoji_id", label: "Added Emoji ID" },
  { key: "added_emoji_animated", label: "Added Animated" },
  { key: "changed_emoji", label: "Changed Emoji" },
  { key: "changed_emoji_id", label: "Changed Emoji ID" },
  { key: "changed_emoji_animated", label: "Changed Animated" },
  { key: "removed_emoji", label: "Removed Emoji" },
  { key: "removed_emoji_id", label: "Removed Emoji ID" },
  { key: "removed_emoji_animated", label: "Removed Animated" },
  { key: "notice_emoji", label: "Notice Emoji" },
  { key: "notice_emoji_id", label: "Notice Emoji ID" },
  { key: "notice_emoji_animated", label: "Notice Animated" },
  { key: "notice_text", label: "Notice Text" },
  { key: "no_change_text", label: "No Change Text" },
  { key: "spacer", label: "Spacer" },
  { key: "dashboard_host", label: "Dashboard Host (Restart)" },
  { key: "dashboard_port", label: "Dashboard Port (Restart)" },
  { key: "audit_enabled", label: "Audit Enabled" },
  { key: "audit_max_entries", label: "Audit Max Entries" },
  { key: "export_import_enabled", label: "Export/Import Enabled" },
  { key: "analytics_enabled", label: "Analytics Enabled" },
  { key: "analytics_weeks", label: "Analytics Weeks" },
  { key: "i18n_enabled", label: "i18n Enabled" },
  { key: "locale", label: "Locale", placeholder: "de or en" },
  { key: "fallback_locale", label: "Fallback Locale", placeholder: "de or en" },
  { key: "backup_enabled", label: "Backup Enabled" },
  { key: "backup_directory", label: "Backup Directory" },
  { key: "backup_max_files", label: "Backup Max Files" },
  { key: "backup_include_audit", label: "Backup Include Audit" }
];

const CONFIG_GROUPS: Array<{ title: string; keys: string[] }> = [
  {
    title: "Core",
    keys: [
      "guild_id",
      "channel_id",
      "timezone",
      "schedule_day",
      "schedule_time",
      "dashboard_host",
      "dashboard_port"
    ]
  },
  {
    title: "Messages",
    keys: [
      "title_emoji",
      "title_emoji_id",
      "title_emoji_animated",
      "title_text",
      "added_emoji",
      "added_emoji_id",
      "added_emoji_animated",
      "changed_emoji",
      "changed_emoji_id",
      "changed_emoji_animated",
      "removed_emoji",
      "removed_emoji_id",
      "removed_emoji_animated",
      "notice_emoji",
      "notice_emoji_id",
      "notice_emoji_animated",
      "notice_text",
      "no_change_text",
      "spacer"
    ]
  },
  {
    title: "Features",
    keys: [
      "audit_enabled",
      "audit_max_entries",
      "export_import_enabled",
      "analytics_enabled",
      "analytics_weeks",
      "i18n_enabled",
      "locale",
      "fallback_locale"
    ]
  },
  {
    title: "Backup",
    keys: ["backup_enabled", "backup_directory", "backup_max_files", "backup_include_audit"]
  }
];

const CONFIG_FIELD_MAP = CONFIG_FIELDS.reduce<Record<string, ConfigField>>((acc, field) => {
  acc[field.key] = field;
  return acc;
}, {});

const I18N: Record<Locale, Record<string, string>> = {
  de: {
    title: "NebiUpdate Dashboard",
    subtitle: "Wochenchangelog, Audit, Analytics und Backups.",
    token: "Token (X-Dashboard-Token)",
    saveToken: "Token speichern",
    reload: "Neu laden",
    sync: "Sync senden",
    test: "Test senden",
    updates: "Eintraege",
    preview: "Preview",
    config: "Config",
    analytics: "Analytics",
    audit: "Audit Log",
    tools: "Export/Import/Backup",
    search: "Suche",
    add: "Neu",
    changed: "Geaendert",
    removed: "Entfernt",
    save: "Speichern",
    update: "Aktualisieren",
    exportJson: "Export JSON",
    exportCsv: "Export CSV",
    importJson: "Import JSON",
    importCsv: "Import CSV",
    backupCreate: "Backup erstellen",
    backupRestore: "Backup wiederherstellen",
    language: "Sprache"
  },
  en: {
    title: "NebiUpdate Dashboard",
    subtitle: "Weekly changelog, audit, analytics and backups.",
    token: "Token (X-Dashboard-Token)",
    saveToken: "Save token",
    reload: "Reload",
    sync: "Send sync",
    test: "Send test",
    updates: "Entries",
    preview: "Preview",
    config: "Config",
    analytics: "Analytics",
    audit: "Audit Log",
    tools: "Export/Import/Backup",
    search: "Search",
    add: "Added",
    changed: "Changed",
    removed: "Removed",
    save: "Save",
    update: "Update",
    exportJson: "Export JSON",
    exportCsv: "Export CSV",
    importJson: "Import JSON",
    importCsv: "Import CSV",
    backupCreate: "Create backup",
    backupRestore: "Restore backup",
    language: "Language"
  }
};

interface BusyState {
  reload: boolean;
  sync: boolean;
  test: boolean;
  config: boolean;
  add: boolean;
  edit: boolean;
  backup: boolean;
  restore: boolean;
  importJson: boolean;
  importCsv: boolean;
  exportJson: boolean;
  exportCsv: boolean;
}

function createMessage(text = "", tone: MessageTone = "info"): MessageState {
  return { text, tone };
}

function normalizeConfig(input: Record<string, unknown>): Record<string, string> {
  const normalized: Record<string, string> = {};
  for (const [key, value] of Object.entries(input)) normalized[key] = value == null ? "" : String(value);
  return normalized;
}

function typeClass(type: string): string {
  const t = type.toLowerCase();
  if (t === "added") return "tag added";
  if (t === "changed") return "tag changed";
  if (t === "removed") return "tag removed";
  return "tag";
}

function download(name: string, content: string, mime: string): void {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = name;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export function App() {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_STORAGE_KEY) ?? "");
  const [langOverride, setLangOverride] = useState(() => localStorage.getItem(LANG_STORAGE_KEY) ?? "");
  const [status, setStatus] = useState<DashboardStatus | null>(null);
  const [configValues, setConfigValues] = useState<Record<string, string>>({});
  const [updates, setUpdates] = useState<DashboardUpdate[]>([]);
  const [preview, setPreview] = useState("Loading preview ...");
  const [updateQuery, setUpdateQuery] = useState("");
  const [analytics, setAnalytics] = useState<AnalyticsPayload | null>(null);
  const [auditEntries, setAuditEntries] = useState<AuditLogEntry[]>([]);
  const [backups, setBackups] = useState<BackupItem[]>([]);
  const [selectedBackup, setSelectedBackup] = useState("");
  const [jsonImportPayload, setJsonImportPayload] = useState("");
  const [csvImportPayload, setCsvImportPayload] = useState("");
  const [messages, setMessages] = useState<Record<MessageSlot, MessageState>>({
    global: createMessage(),
    action: createMessage(),
    config: createMessage(),
    add: createMessage(),
    edit: createMessage(),
    data: createMessage()
  });
  const [toast, setToast] = useState<{ visible: boolean; text: string; tone: MessageTone }>({
    visible: false,
    text: "",
    tone: "info"
  });
  const [busy, setBusy] = useState<BusyState>({
    reload: false,
    sync: false,
    test: false,
    config: false,
    add: false,
    edit: false,
    backup: false,
    restore: false,
    importJson: false,
    importCsv: false,
    exportJson: false,
    exportCsv: false
  });
  const [newType, setNewType] = useState("added");
  const [newText, setNewText] = useState("");
  const [newAuthor, setNewAuthor] = useState("Dashboard");
  const [editId, setEditId] = useState("");
  const [editType, setEditType] = useState("");
  const [editText, setEditText] = useState("");
  const [editAuthor, setEditAuthor] = useState("Dashboard");

  const locale = useMemo<Locale>(() => {
    const explicit = langOverride.trim().toLowerCase();
    if (explicit === "de" || explicit === "en") return explicit;
    return (configValues.locale ?? status?.locale ?? "de").toLowerCase() === "en" ? "en" : "de";
  }, [configValues.locale, langOverride, status?.locale]);
  const t = useCallback((key: string) => I18N[locale][key] ?? key, [locale]);

  const setBusyFlag = useCallback((key: BusyKey, value: boolean) => {
    setBusy((prev) => ({ ...prev, [key]: value }));
  }, []);
  const setMessage = useCallback((slot: MessageSlot, text: string, tone: MessageTone = "info") => {
    setMessages((prev) => ({ ...prev, [slot]: createMessage(text, tone) }));
  }, []);
  const handleError = useCallback(
    (slot: MessageSlot, error: unknown) => {
      const text = error instanceof Error ? error.message : "Unknown error";
      setMessage(slot, text, "error");
      setToast({ visible: true, text, tone: "error" });
    },
    [setMessage]
  );

  const loadAudit = useCallback(async () => {
    const response = await apiRequest<{ enabled: boolean; entries: AuditLogEntry[] }>("/api/audit?limit=120", token);
    setAuditEntries(response.entries ?? []);
  }, [token]);
  const loadAnalytics = useCallback(async () => {
    const response = await apiRequest<AnalyticsPayload>("/api/analytics", token);
    setAnalytics(response);
  }, [token]);
  const loadBackups = useCallback(async () => {
    const response = await apiRequest<{ enabled: boolean; items: BackupItem[] }>("/api/backups", token);
    const items = response.items ?? [];
    setBackups(items);
    if (!selectedBackup && items.length > 0) setSelectedBackup(items[0].fileName);
  }, [selectedBackup, token]);

  const reloadAll = useCallback(async () => {
    setBusyFlag("reload", true);
    try {
      const [s, c, u, p] = await Promise.all([
        apiRequest<DashboardStatus>("/api/status", token),
        apiRequest<Record<string, unknown>>("/api/config", token),
        apiRequest<DashboardUpdate[]>("/api/updates/current", token),
        apiRequest<string>("/api/preview/current", token)
      ]);
      setStatus(s);
      setConfigValues(normalizeConfig(c));
      setUpdates(u);
      setPreview(p);
      await Promise.all([loadAudit(), loadAnalytics(), loadBackups()]);
      setMessage("global", "Dashboard synced.");
    } catch (error) {
      handleError("global", error);
    } finally {
      setBusyFlag("reload", false);
    }
  }, [handleError, loadAnalytics, loadAudit, loadBackups, setBusyFlag, setMessage, token]);

  useEffect(() => {
    void reloadAll();
  }, [reloadAll]);
  useEffect(() => {
    if (!toast.visible) return;
    const timeout = window.setTimeout(() => setToast((prev) => ({ ...prev, visible: false })), 2800);
    return () => window.clearTimeout(timeout);
  }, [toast.visible]);

  const filteredUpdates = useMemo(() => {
    const q = updateQuery.trim().toLowerCase();
    if (!q) return updates;
    return updates.filter((e) => `${e.id} ${e.type} ${e.content} ${e.author}`.toLowerCase().includes(q));
  }, [updateQuery, updates]);

  const typeLabel = (type: string): string => {
    const normalized = type.toLowerCase();
    if (normalized === "added") return t("add");
    if (normalized === "changed") return t("changed");
    if (normalized === "removed") return t("removed");
    return type;
  };

  const saveToken = () => {
    const safe = token.trim();
    localStorage.setItem(TOKEN_STORAGE_KEY, safe);
    setToken(safe);
    setToast({ visible: true, text: "Token saved.", tone: "info" });
  };
  const switchLocale = (next: string) => {
    const safe = next === "en" ? "en" : "de";
    localStorage.setItem(LANG_STORAGE_KEY, safe);
    setLangOverride(safe);
  };

  const sendSync = async () => {
    setBusyFlag("sync", true);
    try {
      await apiRequest("/api/actions/sync", token, { method: "POST", body: "{}" });
      await reloadAll();
    } catch (error) {
      handleError("action", error);
    } finally {
      setBusyFlag("sync", false);
    }
  };
  const sendTest = async () => {
    setBusyFlag("test", true);
    try {
      await apiRequest("/api/actions/test", token, { method: "POST", body: "{}" });
      setMessage("action", "Test triggered.");
    } catch (error) {
      handleError("action", error);
    } finally {
      setBusyFlag("test", false);
    }
  };

  const submitConfig = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setBusyFlag("config", true);
    try {
      await apiRequest("/api/config", token, { method: "PUT", body: JSON.stringify(configValues) });
      await reloadAll();
      setMessage("config", "Config saved.");
    } catch (error) {
      handleError("config", error);
    } finally {
      setBusyFlag("config", false);
    }
  };
  const submitNewUpdate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setBusyFlag("add", true);
    try {
      await apiRequest("/api/updates/current", token, {
        method: "POST",
        body: JSON.stringify({ type: newType, text: newText, author: newAuthor })
      });
      setNewText("");
      await reloadAll();
    } catch (error) {
      handleError("add", error);
    } finally {
      setBusyFlag("add", false);
    }
  };
  const submitEditUpdate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const id = Number(editId);
    if (!Number.isInteger(id) || id <= 0) return;
    setBusyFlag("edit", true);
    try {
      await apiRequest(`/api/updates/current/${id}`, token, {
        method: "PUT",
        body: JSON.stringify({ type: editType, text: editText, author: editAuthor })
      });
      await reloadAll();
    } catch (error) {
      handleError("edit", error);
    } finally {
      setBusyFlag("edit", false);
    }
  };

  const exportJson = async () => {
    setBusyFlag("exportJson", true);
    try {
      const payload = await apiRequest<Record<string, unknown>>("/api/export/json?include_audit=true", token);
      download(`nebiupdate-export-${Date.now()}.json`, JSON.stringify(payload, null, 2), "application/json");
    } catch (error) {
      handleError("data", error);
    } finally {
      setBusyFlag("exportJson", false);
    }
  };
  const exportCsv = async () => {
    setBusyFlag("exportCsv", true);
    try {
      const payload = await apiRequest<string>("/api/export/csv?scope=all", token);
      download(`nebiupdate-updates-${Date.now()}.csv`, payload, "text/csv");
    } catch (error) {
      handleError("data", error);
    } finally {
      setBusyFlag("exportCsv", false);
    }
  };
  const runImportJson = async () => {
    if (!jsonImportPayload.trim()) return;
    setBusyFlag("importJson", true);
    try {
      await apiRequest("/api/import/json?replace_data=true&replace_config=false&replace_audit=true", token, {
        method: "POST",
        body: jsonImportPayload
      });
      await reloadAll();
    } catch (error) {
      handleError("data", error);
    } finally {
      setBusyFlag("importJson", false);
    }
  };
  const runImportCsv = async () => {
    if (!csvImportPayload.trim()) return;
    setBusyFlag("importCsv", true);
    try {
      await apiRequest("/api/import/csv", token, { method: "POST", body: csvImportPayload });
      await reloadAll();
    } catch (error) {
      handleError("data", error);
    } finally {
      setBusyFlag("importCsv", false);
    }
  };
  const createBackup = async () => {
    setBusyFlag("backup", true);
    try {
      await apiRequest("/api/actions/backup", token, { method: "POST", body: "{}" });
      await loadBackups();
    } catch (error) {
      handleError("data", error);
    } finally {
      setBusyFlag("backup", false);
    }
  };
  const restoreBackup = async () => {
    if (!selectedBackup) return;
    setBusyFlag("restore", true);
    try {
      await apiRequest("/api/actions/restore", token, {
        method: "POST",
        body: JSON.stringify({ file: selectedBackup, replaceConfig: false, replaceAudit: true })
      });
      await reloadAll();
    } catch (error) {
      handleError("data", error);
    } finally {
      setBusyFlag("restore", false);
    }
  };

  const fillEdit = (entry: DashboardUpdate) => {
    setEditId(String(entry.id));
    setEditType(entry.type);
    setEditText(entry.content);
    setEditAuthor(entry.author || "Dashboard");
  };
  const deleteUpdate = async (id: number) => {
    if (!window.confirm(`Delete #${id}?`)) return;
    try {
      await apiRequest(`/api/updates/current/${id}`, token, { method: "DELETE" });
      await reloadAll();
    } catch (error) {
      handleError("action", error);
    }
  };

  return (
    <>
      <main className="shell">
        <section className="card hero reveal rise-a">
          <div className="hero-grid">
            <div>
              <span className="eyebrow">
                <Sparkles size={16} />
                {t("title")}
              </span>
              <h1>{t("title")}</h1>
              <p>{t("subtitle")}</p>
            </div>
            <aside className="week-card">
              <p>Week</p>
              <strong>{status?.weekLabel || "-"}</strong>
              <span>{status ? `${status.weekStart} - ${status.weekEnd}` : "-"}</span>
            </aside>
          </div>
          <div className="auth-row">
            <label className="field token-field">
              <span>{t("token")}</span>
              <input type="password" value={token} onChange={(event) => setToken(event.target.value)} />
            </label>
            <label className="field lang-field">
              <span>
                <Languages size={14} /> {t("language")}
              </span>
              <select value={locale} onChange={(event) => switchLocale(event.target.value)}>
                <option value="de">Deutsch</option>
                <option value="en">English</option>
              </select>
            </label>
            <button className="btn subtle" type="button" onClick={saveToken}>
              <Save size={16} />
              {t("saveToken")}
            </button>
            <button className="btn subtle" type="button" onClick={() => void reloadAll()} disabled={busy.reload}>
              <RefreshCw size={16} className={busy.reload ? "spin" : ""} />
              {t("reload")}
            </button>
          </div>
          <p className={`message ${messages.global.tone}`}>{messages.global.text}</p>
        </section>

        <section className="status-strip reveal rise-b">
          {[Bot, CalendarDays, Database, ShieldCheck, Activity, Settings2, Sparkles].map((Icon, index) => (
            <article className="card status-card" key={index}>
              <div className="status-icon good">
                <Icon size={16} />
              </div>
              <div>
                <p>{["Bot", "Week", "Updates", "Guild", "Channel", "Schedule", "Timezone"][index]}</p>
                <strong>
                  {index === 0
                    ? status?.connected
                      ? "Connected"
                      : "Offline"
                    : index === 1
                    ? status?.weekLabel || "-"
                    : index === 2
                    ? String(status?.updateCount ?? 0)
                    : index === 3
                    ? status?.guildId || "(global)"
                    : index === 4
                    ? status?.channelId || "(unset)"
                    : index === 5
                    ? `${status?.scheduleDay || "-"} ${status?.scheduleTime || "-"}`
                    : status?.timezone || "-"}
                </strong>
              </div>
            </article>
          ))}
        </section>

        <section className="layout-grid reveal rise-c">
          <aside className="left-column">
            <article className="card panel">
              <header>
                <h2>Operations</h2>
              </header>
              <div className="button-stack">
                <button className="btn primary" type="button" onClick={() => void sendSync()} disabled={busy.sync}>
                  <Send size={16} /> {t("sync")}
                </button>
                <button className="btn warning" type="button" onClick={() => void sendTest()} disabled={busy.test}>
                  <Activity size={16} /> {t("test")}
                </button>
                <button className="btn subtle" type="button" onClick={() => void reloadAll()} disabled={busy.reload}>
                  <Eye size={16} /> {t("reload")}
                </button>
              </div>
              <p className={`message ${messages.action.tone}`}>{messages.action.text}</p>
            </article>

            <article className="card panel">
              <header><h2><PlusCircle size={18} /> Add</h2></header>
              <form onSubmit={submitNewUpdate} className="stack">
                <label className="field"><span>Type</span><select value={newType} onChange={(e) => setNewType(e.target.value)}>
                  <option value="added">{t("add")}</option><option value="changed">{t("changed")}</option><option value="removed">{t("removed")}</option>
                </select></label>
                <label className="field"><span>Text</span><textarea value={newText} onChange={(e) => setNewText(e.target.value)} /></label>
                <label className="field"><span>User</span><input value={newAuthor} onChange={(e) => setNewAuthor(e.target.value)} /></label>
                <button className="btn primary" type="submit" disabled={busy.add}><Save size={16} /> {t("save")}</button>
              </form>
            </article>

            <article className="card panel">
              <header><h2><Pencil size={18} /> Edit</h2></header>
              <form onSubmit={submitEditUpdate} className="stack">
                <div className="split">
                  <label className="field"><span>ID</span><input type="number" min={1} required value={editId} onChange={(e) => setEditId(e.target.value)} /></label>
                  <label className="field"><span>Type</span><select value={editType} onChange={(e) => setEditType(e.target.value)}>
                    <option value="">(keep)</option><option value="added">{t("add")}</option><option value="changed">{t("changed")}</option><option value="removed">{t("removed")}</option>
                  </select></label>
                </div>
                <label className="field"><span>Text</span><textarea value={editText} onChange={(e) => setEditText(e.target.value)} /></label>
                <label className="field"><span>User</span><input value={editAuthor} onChange={(e) => setEditAuthor(e.target.value)} /></label>
                <button className="btn warning" type="submit" disabled={busy.edit}><Save size={16} /> {t("update")}</button>
              </form>
            </article>
          </aside>

          <section className="right-column">
            <article className="card panel">
              <header className="header-spread">
                <h2>{t("updates")}</h2>
                <label className="field search-field"><span>{t("search")}</span><input value={updateQuery} onChange={(e) => setUpdateQuery(e.target.value)} /></label>
              </header>
              <div className="table-wrap">
                <table><thead><tr><th>ID</th><th>Type</th><th>Text</th><th>User</th><th>Action</th></tr></thead>
                  <tbody>
                    {filteredUpdates.length === 0 && <tr><td colSpan={5} className="empty-row">No entries.</td></tr>}
                    {filteredUpdates.map((entry) => (
                      <tr key={entry.id}>
                        <td>#{entry.id}</td>
                        <td><span className={typeClass(entry.type)}>{typeLabel(entry.type)}</span></td>
                        <td>{entry.content}</td>
                        <td>{entry.author || "-"}</td>
                        <td><div className="table-actions">
                          <button className="icon-btn" type="button" onClick={() => fillEdit(entry)}><Pencil size={15} /></button>
                          <button className="icon-btn danger" type="button" onClick={() => void deleteUpdate(entry.id)}><Trash2 size={15} /></button>
                        </div></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </article>

            <article className="card panel"><header><h2>{t("preview")}</h2></header><pre>{preview}</pre></article>

            <article className="card panel"><header><h2><BarChart3 size={18} /> {t("analytics")}</h2></header>
              {analytics?.enabled ? (
                <div className="analytics-list">
                  {analytics.weeks.map((w) => <div key={w.weekStart} className="analytics-row"><span>{w.weekStart}</span><strong>{w.total}</strong></div>)}
                </div>
              ) : <p className="message">Analytics disabled.</p>}
            </article>

            <article className="card panel"><header><h2><FileClock size={18} /> {t("audit")}</h2></header>
              <div className="table-wrap"><table><thead><tr><th>Time</th><th>Actor</th><th>Action</th><th>Entity</th><th>Details</th></tr></thead>
                <tbody>
                  {auditEntries.length === 0 && <tr><td colSpan={5} className="empty-row">No audit entries.</td></tr>}
                  {auditEntries.map((entry) => <tr key={entry.id}><td>{entry.createdAt}</td><td>{entry.actor}</td><td>{entry.action}</td><td>{entry.entityType}/{entry.entityId}</td><td>{entry.details}</td></tr>)}
                </tbody></table></div>
            </article>

            <article className="card panel"><header><h2><ArchiveRestore size={18} /> {t("tools")}</h2></header>
              <div className="button-row">
                <button className="btn subtle" type="button" onClick={() => void exportJson()} disabled={busy.exportJson}><Download size={15} /> {t("exportJson")}</button>
                <button className="btn subtle" type="button" onClick={() => void exportCsv()} disabled={busy.exportCsv}><Download size={15} /> {t("exportCsv")}</button>
                <button className="btn warning" type="button" onClick={() => void createBackup()} disabled={busy.backup}><Save size={15} /> {t("backupCreate")}</button>
              </div>
              <div className="split">
                <label className="field"><span>Backups</span><select value={selectedBackup} onChange={(e) => setSelectedBackup(e.target.value)}>
                  {backups.length === 0 && <option value="">No backups</option>}
                  {backups.map((b) => <option key={b.fileName} value={b.fileName}>{b.fileName}</option>)}
                </select></label>
                <button className="btn warning" type="button" onClick={() => void restoreBackup()} disabled={busy.restore}><ArchiveRestore size={15} /> {t("backupRestore")}</button>
              </div>
              <label className="field"><span>{t("importJson")}</span><textarea value={jsonImportPayload} onChange={(e) => setJsonImportPayload(e.target.value)} /></label>
              <button className="btn subtle" type="button" onClick={() => void runImportJson()} disabled={busy.importJson}><Upload size={15} /> {t("importJson")}</button>
              <label className="field"><span>{t("importCsv")}</span><textarea value={csvImportPayload} onChange={(e) => setCsvImportPayload(e.target.value)} /></label>
              <button className="btn subtle" type="button" onClick={() => void runImportCsv()} disabled={busy.importCsv}><Upload size={15} /> {t("importCsv")}</button>
              <p className={`message ${messages.data.tone}`}>{messages.data.text}</p>
            </article>

            <article className="card panel">
              <header><h2>{t("config")}</h2></header>
              <form onSubmit={submitConfig}>
                <div className="config-stack">{CONFIG_GROUPS.map((group) => (
                  <section className="config-group" key={group.title}>
                    <div className="config-group-head"><h3>{group.title}</h3></div>
                    <div className="config-grid">{group.keys.map((key) => {
                      const field = CONFIG_FIELD_MAP[key] ?? { key, label: key };
                      return <label className="field" key={field.key}>
                        <span>{field.label}</span>
                        <input value={configValues[field.key] ?? ""} placeholder={field.placeholder ?? ""} onChange={(e) => setConfigValues((prev) => ({ ...prev, [field.key]: e.target.value }))} />
                      </label>;
                    })}</div>
                  </section>
                ))}</div>
                <div className="button-row"><button className="btn primary" type="submit" disabled={busy.config}><Save size={16} /> {t("save")}</button></div>
                <p className={`message ${messages.config.tone}`}>{messages.config.text}</p>
              </form>
            </article>
          </section>
        </section>
      </main>
      <aside className={`toast ${toast.visible ? "show" : ""} ${toast.tone}`}>{toast.text}</aside>
    </>
  );
}
