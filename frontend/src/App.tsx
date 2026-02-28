import {
  Activity,
  Bot,
  CalendarDays,
  Database,
  Eye,
  Pencil,
  PlusCircle,
  RefreshCw,
  Save,
  Send,
  Settings2,
  ShieldCheck,
  Sparkles,
  Trash2
} from "lucide-react";
import type { FormEvent } from "react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { apiRequest } from "./api";
import type { DashboardStatus, DashboardUpdate, MessageState, MessageTone } from "./types";

const TOKEN_STORAGE_KEY = "dashboardToken";

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
  { key: "schedule_time", label: "Schedule Zeit", placeholder: "HH:mm" },
  { key: "title_emoji", label: "Titel Emoji" },
  { key: "title_emoji_id", label: "Titel Emoji ID" },
  { key: "title_emoji_animated", label: "Titel Emoji animiert" },
  { key: "title_text", label: "Titel Text" },
  { key: "added_emoji", label: "Emoji Added" },
  { key: "added_emoji_id", label: "Emoji-ID Added" },
  { key: "added_emoji_animated", label: "Added animiert" },
  { key: "changed_emoji", label: "Emoji Changed" },
  { key: "changed_emoji_id", label: "Emoji-ID Changed" },
  { key: "changed_emoji_animated", label: "Changed animiert" },
  { key: "removed_emoji", label: "Emoji Removed" },
  { key: "removed_emoji_id", label: "Emoji-ID Removed" },
  { key: "removed_emoji_animated", label: "Removed animiert" },
  { key: "notice_emoji", label: "Info Emoji" },
  { key: "notice_emoji_id", label: "Info Emoji ID" },
  { key: "notice_emoji_animated", label: "Info animiert" },
  { key: "notice_text", label: "Info Text" },
  { key: "no_change_text", label: "No-Change Text" },
  { key: "spacer", label: "Spacer Zeile" },
  { key: "dashboard_host", label: "Dashboard Host (Restart)" },
  { key: "dashboard_port", label: "Dashboard Port (Restart)" }
];

const CONFIG_GROUPS: Array<{ title: string; description: string; keys: string[] }> = [
  {
    title: "Core",
    description: "Routing, Zeitsteuerung und Dashboard-Host.",
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
    title: "Header",
    description: "Titelbereich der Wochennachricht.",
    keys: ["title_emoji", "title_emoji_id", "title_emoji_animated", "title_text", "spacer"]
  },
  {
    title: "Added",
    description: "Darstellung fuer neue Features.",
    keys: ["added_emoji", "added_emoji_id", "added_emoji_animated"]
  },
  {
    title: "Changed",
    description: "Darstellung fuer geaenderte Inhalte.",
    keys: ["changed_emoji", "changed_emoji_id", "changed_emoji_animated"]
  },
  {
    title: "Removed",
    description: "Darstellung fuer entfernte Inhalte.",
    keys: ["removed_emoji", "removed_emoji_id", "removed_emoji_animated"]
  },
  {
    title: "Notice",
    description: "Info-/Fallback-Texte im Footer.",
    keys: ["notice_emoji", "notice_emoji_id", "notice_emoji_animated", "notice_text", "no_change_text"]
  }
];

const CONFIG_FIELD_MAP: Record<string, ConfigField> = CONFIG_FIELDS.reduce<Record<string, ConfigField>>(
  (accumulator, field) => {
    accumulator[field.key] = field;
    return accumulator;
  },
  {}
);

type MessageSlot = "global" | "action" | "config" | "add" | "edit";
type BusyKey = "reload" | "sync" | "test" | "config" | "add" | "edit";

interface BusyState {
  reload: boolean;
  sync: boolean;
  test: boolean;
  config: boolean;
  add: boolean;
  edit: boolean;
}

function createMessage(text = "", tone: MessageTone = "info"): MessageState {
  return { text, tone };
}

function normalizeConfig(input: Record<string, unknown>): Record<string, string> {
  const normalized: Record<string, string> = {};
  for (const [key, value] of Object.entries(input)) {
    normalized[key] = value == null ? "" : String(value);
  }
  return normalized;
}

function formatAuthor(rawAuthor: string): string {
  const value = rawAuthor.trim();
  if (!value) {
    return "@Unbekannt";
  }
  const mentionMatch = value.match(/^<@!?(\d{15,25})>$/);
  if (mentionMatch) {
    return `<@${mentionMatch[1]}>`;
  }
  if (/^\d{15,25}$/.test(value)) {
    return `<@${value}>`;
  }
  if (value.startsWith("@")) {
    return value;
  }
  return `@${value}`;
}

function typeLabel(type: string): string {
  const normalized = type.toLowerCase();
  if (normalized === "added") return "Neu";
  if (normalized === "changed") return "Geaendert";
  if (normalized === "removed") return "Entfernt";
  return type;
}

function typeClass(type: string): string {
  const normalized = type.toLowerCase();
  if (normalized === "added") return "tag added";
  if (normalized === "changed") return "tag changed";
  if (normalized === "removed") return "tag removed";
  return "tag";
}

export function App() {
  const [token, setToken] = useState<string>(() => localStorage.getItem(TOKEN_STORAGE_KEY) ?? "");
  const [status, setStatus] = useState<DashboardStatus | null>(null);
  const [configValues, setConfigValues] = useState<Record<string, string>>({});
  const [updates, setUpdates] = useState<DashboardUpdate[]>([]);
  const [preview, setPreview] = useState<string>("Lade Preview ...");
  const [updateQuery, setUpdateQuery] = useState("");

  const [messages, setMessages] = useState<Record<MessageSlot, MessageState>>({
    global: createMessage(),
    action: createMessage(),
    config: createMessage(),
    add: createMessage(),
    edit: createMessage()
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
    edit: false
  });

  const [newType, setNewType] = useState("added");
  const [newText, setNewText] = useState("");
  const [newAuthor, setNewAuthor] = useState("Dashboard");

  const [editId, setEditId] = useState("");
  const [editType, setEditType] = useState("");
  const [editText, setEditText] = useState("");
  const [editAuthor, setEditAuthor] = useState("Dashboard");

  const setBusyFlag = useCallback((key: BusyKey, value: boolean) => {
    setBusy((prev) => ({ ...prev, [key]: value }));
  }, []);

  const setMessage = useCallback((slot: MessageSlot, text: string, tone: MessageTone = "info") => {
    setMessages((prev) => ({ ...prev, [slot]: createMessage(text, tone) }));
  }, []);

  const showToast = useCallback((text: string, tone: MessageTone = "info") => {
    setToast({ text, tone, visible: true });
  }, []);

  const handleError = useCallback(
    (slot: MessageSlot, error: unknown) => {
      const message = error instanceof Error ? error.message : "Unbekannter Fehler";
      setMessage(slot, message, "error");
      showToast(message, "error");
    },
    [setMessage, showToast]
  );

  const reloadAll = useCallback(async () => {
    setBusyFlag("reload", true);
    try {
      const [statusResult, configResult, updatesResult, previewResult] = await Promise.all([
        apiRequest<DashboardStatus>("/api/status", token),
        apiRequest<Record<string, unknown>>("/api/config", token),
        apiRequest<DashboardUpdate[]>("/api/updates/current", token),
        apiRequest<string>("/api/preview/current", token)
      ]);
      setStatus(statusResult);
      setConfigValues(normalizeConfig(configResult));
      setUpdates(updatesResult);
      setPreview(previewResult);
      setMessage("global", "Dashboard synchronisiert.");
    } catch (error) {
      handleError("global", error);
    } finally {
      setBusyFlag("reload", false);
    }
  }, [handleError, setBusyFlag, setMessage, token]);

  useEffect(() => {
    void reloadAll();
  }, [reloadAll]);

  useEffect(() => {
    if (!toast.visible) {
      return;
    }
    const timeout = window.setTimeout(() => {
      setToast((prev) => ({ ...prev, visible: false }));
    }, 2800);
    return () => window.clearTimeout(timeout);
  }, [toast.visible, toast.text]);

  const statusCards = useMemo(() => {
    if (!status) {
      return [];
    }
    return [
      {
        icon: Bot,
        label: "Bot",
        value: status.connected ? "Verbunden" : "Offline",
        isGood: status.connected
      },
      {
        icon: CalendarDays,
        label: "Woche",
        value: status.weekLabel || "-"
      },
      {
        icon: Database,
        label: "Updates",
        value: String(status.updateCount ?? 0)
      },
      {
        icon: ShieldCheck,
        label: "Guild",
        value: status.guildId || "(global)"
      },
      {
        icon: Activity,
        label: "Channel",
        value: status.channelId || "(nicht gesetzt)"
      },
      {
        icon: Settings2,
        label: "Schedule",
        value: `${status.scheduleDay || "-"} ${status.scheduleTime || "-"}`
      },
      {
        icon: Sparkles,
        label: "Timezone",
        value: status.timezone || "-"
      }
    ];
  }, [status]);

  const filteredUpdates = useMemo(() => {
    const query = updateQuery.trim().toLowerCase();
    if (!query) {
      return updates;
    }
    return updates.filter((entry) => {
      const haystack = `${entry.id} ${entry.type} ${entry.content} ${entry.author}`.toLowerCase();
      return haystack.includes(query);
    });
  }, [updateQuery, updates]);

  const goToSection = (id: string) => {
    const target = document.getElementById(id);
    if (target) {
      target.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  };

  const saveToken = () => {
    localStorage.setItem(TOKEN_STORAGE_KEY, token.trim());
    setToken(token.trim());
    setMessage("global", "Token lokal gespeichert.");
    showToast("Token gespeichert.");
  };

  const sendSync = async () => {
    setBusyFlag("sync", true);
    try {
      await apiRequest<{ ok: boolean }>("/api/actions/sync", token, {
        method: "POST",
        body: "{}"
      });
      setMessage("action", "Sync ausgeloest.");
      showToast("Wochen-Nachricht wird synchronisiert.");
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
      await apiRequest<{ ok: boolean }>("/api/actions/test", token, {
        method: "POST",
        body: "{}"
      });
      setMessage("action", "Test-Nachricht gesendet.");
      showToast("Test-Nachricht wurde angefordert.");
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
      await apiRequest<Record<string, string>>("/api/config", token, {
        method: "PUT",
        body: JSON.stringify(configValues)
      });
      setMessage("config", "Config gespeichert.");
      showToast("Config gespeichert.");
      await reloadAll();
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
      await apiRequest<DashboardUpdate>("/api/updates/current", token, {
        method: "POST",
        body: JSON.stringify({
          type: newType,
          text: newText,
          author: newAuthor
        })
      });
      setNewText("");
      setMessage("add", "Eintrag gespeichert.");
      showToast("Eintrag hinzugefuegt.");
      await reloadAll();
    } catch (error) {
      handleError("add", error);
    } finally {
      setBusyFlag("add", false);
    }
  };

  const submitEditUpdate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const parsedId = Number(editId);
    if (!Number.isInteger(parsedId) || parsedId <= 0) {
      setMessage("edit", "Bitte eine gueltige ID eintragen.", "error");
      return;
    }
    setBusyFlag("edit", true);
    try {
      await apiRequest<DashboardUpdate>(`/api/updates/current/${parsedId}`, token, {
        method: "PUT",
        body: JSON.stringify({
          type: editType,
          text: editText,
          author: editAuthor
        })
      });
      setMessage("edit", `Eintrag #${parsedId} aktualisiert.`);
      showToast(`Eintrag #${parsedId} aktualisiert.`);
      await reloadAll();
    } catch (error) {
      handleError("edit", error);
    } finally {
      setBusyFlag("edit", false);
    }
  };

  const fillEditForm = (entry: DashboardUpdate) => {
    setEditId(String(entry.id));
    setEditType(entry.type);
    setEditText(entry.content);
    setEditAuthor(entry.author || "Dashboard");
    setMessage("edit", `Eintrag #${entry.id} in das Edit-Form geladen.`);
    showToast(`Eintrag #${entry.id} geladen.`);
    goToSection("section-operations");
  };

  const deleteUpdate = async (id: number) => {
    if (!window.confirm(`Eintrag #${id} wirklich loeschen?`)) {
      return;
    }
    try {
      await apiRequest<{ ok: boolean }>(`/api/updates/current/${id}`, token, {
        method: "DELETE"
      });
      setMessage("action", `Eintrag #${id} entfernt.`);
      showToast(`Eintrag #${id} entfernt.`);
      await reloadAll();
    } catch (error) {
      handleError("action", error);
    }
  };

  const weekWindowLabel =
    status == null ? "-" : `${status.weekStart || "-"} bis ${status.weekEnd || "-"}`;

  return (
    <>
      <main className="shell">
        <section className="card hero reveal rise-a">
          <div className="hero-grid">
            <div>
              <span className="eyebrow">
                <Sparkles size={16} />
                NebiUpdate Dashboard
              </span>
              <h1>Clean control for weekly changelogs.</h1>
              <p>
                Das Board ist jetzt in klare Bereiche getrennt: Operations links, Content und
                Config rechts. Dadurch wird der Flow vom Bearbeiten bis zum Versand deutlich ruhiger.
              </p>
            </div>

            <aside className="week-card">
              <p>Aktive Woche</p>
              <strong>{status?.weekLabel || "-"}</strong>
              <span>{weekWindowLabel}</span>
            </aside>
          </div>

          <div className="hero-nav">
            <button className="nav-chip" type="button" onClick={() => goToSection("section-operations")}>
              Operations
            </button>
            <button className="nav-chip" type="button" onClick={() => goToSection("section-content")}>
              Entries & Preview
            </button>
            <button className="nav-chip" type="button" onClick={() => goToSection("section-config")}>
              Config
            </button>
          </div>

          <div className="auth-row">
            <label className="field token-field">
              <span>Token (Header: X-Dashboard-Token)</span>
              <input
                type="password"
                placeholder="Leer lassen, wenn kein Token gesetzt ist"
                value={token}
                onChange={(event) => setToken(event.target.value)}
              />
            </label>
            <button className="btn subtle" type="button" onClick={saveToken}>
              <Save size={16} />
              Token speichern
            </button>
            <button
              className="btn subtle"
              type="button"
              onClick={() => void reloadAll()}
              disabled={busy.reload}
            >
              <RefreshCw size={16} className={busy.reload ? "spin" : ""} />
              Alles neu laden
            </button>
          </div>

          <p className={`message ${messages.global.tone}`}>{messages.global.text}</p>
        </section>

        <section className="status-strip reveal rise-b">
          {statusCards.map((item) => {
            const Icon = item.icon;
            return (
              <article className="card status-card" key={item.label}>
                <div className={`status-icon ${item.isGood === false ? "bad" : "good"}`}>
                  <Icon size={16} />
                </div>
                <div>
                  <p>{item.label}</p>
                  <strong>{item.value}</strong>
                </div>
              </article>
            );
          })}
        </section>

        <section className="layout-grid reveal rise-c">
          <aside className="left-column" id="section-operations">
            <article className="card panel">
              <header>
                <h2>Operations</h2>
                <p>Sofortige Discord-Aktionen fuer die aktuelle Woche.</p>
              </header>
              <div className="button-stack">
                <button className="btn primary" type="button" onClick={() => void sendSync()} disabled={busy.sync}>
                  <Send size={16} />
                  Finale Wochen-Nachricht syncen
                </button>
                <button className="btn warning" type="button" onClick={() => void sendTest()} disabled={busy.test}>
                  <Activity size={16} />
                  Test-Nachricht senden
                </button>
                <button
                  className="btn subtle"
                  type="button"
                  onClick={() => void reloadAll()}
                  disabled={busy.reload}
                >
                  <Eye size={16} />
                  Preview refresh
                </button>
              </div>
              <p className={`message ${messages.action.tone}`}>{messages.action.text}</p>
            </article>

            <article className="card panel">
              <header>
                <h2>
                  <PlusCircle size={18} />
                  Update Add
                </h2>
                <p>Neuen Eintrag direkt in die aktuelle Woche schreiben.</p>
              </header>
              <form onSubmit={submitNewUpdate} className="stack">
                <label className="field">
                  <span>Typ</span>
                  <select value={newType} onChange={(event) => setNewType(event.target.value)}>
                    <option value="added">Neu</option>
                    <option value="changed">Geaendert</option>
                    <option value="removed">Entfernt</option>
                  </select>
                </label>
                <label className="field">
                  <span>Text</span>
                  <textarea
                    placeholder="Neue Woche, neue Features ..."
                    value={newText}
                    onChange={(event) => setNewText(event.target.value)}
                  />
                </label>
                <label className="field">
                  <span>User</span>
                  <input value={newAuthor} onChange={(event) => setNewAuthor(event.target.value)} />
                </label>
                <button className="btn primary" type="submit" disabled={busy.add}>
                  <Save size={16} />
                  Eintrag speichern
                </button>
              </form>
              <p className={`message ${messages.add.tone}`}>{messages.add.text}</p>
            </article>

            <article className="card panel">
              <header>
                <h2>
                  <Pencil size={18} />
                  Update Edit
                </h2>
                <p>Bestehenden Eintrag korrigieren oder Typ aendern.</p>
              </header>
              <form onSubmit={submitEditUpdate} className="stack">
                <div className="split">
                  <label className="field">
                    <span>ID</span>
                    <input
                      type="number"
                      min={1}
                      required
                      value={editId}
                      onChange={(event) => setEditId(event.target.value)}
                    />
                  </label>
                  <label className="field">
                    <span>Typ</span>
                    <select value={editType} onChange={(event) => setEditType(event.target.value)}>
                      <option value="">(unveraendert)</option>
                      <option value="added">Neu</option>
                      <option value="changed">Geaendert</option>
                      <option value="removed">Entfernt</option>
                    </select>
                  </label>
                </div>
                <label className="field">
                  <span>Text</span>
                  <textarea
                    placeholder="Leer lassen = alter Text bleibt"
                    value={editText}
                    onChange={(event) => setEditText(event.target.value)}
                  />
                </label>
                <label className="field">
                  <span>User</span>
                  <input value={editAuthor} onChange={(event) => setEditAuthor(event.target.value)} />
                </label>
                <button className="btn warning" type="submit" disabled={busy.edit}>
                  <Save size={16} />
                  Eintrag aktualisieren
                </button>
              </form>
              <p className={`message ${messages.edit.tone}`}>{messages.edit.text}</p>
            </article>
          </aside>

          <section className="right-column">
            <article className="card panel" id="section-content">
              <header className="header-spread">
                <div>
                  <h2>Current Week Entries</h2>
                  <p>Uebersicht aller Eintraege mit schnellem Edit/Delete.</p>
                </div>
                <label className="field search-field">
                  <span>Suche</span>
                  <input
                    value={updateQuery}
                    onChange={(event) => setUpdateQuery(event.target.value)}
                    placeholder="ID, Typ, Text, User ..."
                  />
                </label>
              </header>

              <div className="list-stats">
                <span>{filteredUpdates.length} sichtbare Eintraege</span>
                <span>{updates.length} insgesamt</span>
              </div>

              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>Typ</th>
                      <th>Text</th>
                      <th>User</th>
                      <th>Aktion</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredUpdates.length === 0 && (
                      <tr>
                        <td colSpan={5} className="empty-row">
                          Keine passenden Eintraege gefunden.
                        </td>
                      </tr>
                    )}
                    {filteredUpdates.map((entry) => (
                      <tr key={entry.id}>
                        <td>#{entry.id}</td>
                        <td>
                          <span className={typeClass(entry.type)}>{typeLabel(entry.type)}</span>
                        </td>
                        <td>{entry.content}</td>
                        <td>{formatAuthor(entry.author || "Unbekannt")}</td>
                        <td>
                          <div className="table-actions">
                            <button
                              className="icon-btn"
                              type="button"
                              onClick={() => fillEditForm(entry)}
                              aria-label={`Eintrag ${entry.id} laden`}
                            >
                              <Pencil size={15} />
                            </button>
                            <button
                              className="icon-btn danger"
                              type="button"
                              onClick={() => void deleteUpdate(entry.id)}
                              aria-label={`Eintrag ${entry.id} loeschen`}
                            >
                              <Trash2 size={15} />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </article>

            <article className="card panel">
              <header>
                <h2>
                  <Eye size={18} />
                  Discord Preview
                </h2>
                <p>Gerenderte Wochennachricht aus dem aktuellen Datenstand.</p>
              </header>
              <pre>{preview}</pre>
            </article>

            <article className="card panel" id="section-config">
              <header>
                <h2>Bot Config</h2>
                <p>In semantische Gruppen getrennt statt einer langen flachen Liste.</p>
              </header>
              <form onSubmit={submitConfig}>
                <div className="config-stack">
                  {CONFIG_GROUPS.map((group) => (
                    <section className="config-group" key={group.title}>
                      <div className="config-group-head">
                        <h3>{group.title}</h3>
                        <p>{group.description}</p>
                      </div>
                      <div className="config-grid">
                        {group.keys.map((key) => {
                          const field = CONFIG_FIELD_MAP[key] ?? { key, label: key };
                          return (
                            <label className="field" key={field.key}>
                              <span>{field.label}</span>
                              <input
                                name={field.key}
                                value={configValues[field.key] ?? ""}
                                placeholder={field.placeholder ?? ""}
                                onChange={(event) =>
                                  setConfigValues((prev) => ({
                                    ...prev,
                                    [field.key]: event.target.value
                                  }))
                                }
                              />
                            </label>
                          );
                        })}
                      </div>
                    </section>
                  ))}
                </div>

                <div className="button-row">
                  <button className="btn primary" type="submit" disabled={busy.config}>
                    <Save size={16} />
                    Config speichern + Commands re-register
                  </button>
                </div>
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
