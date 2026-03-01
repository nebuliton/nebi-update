<div align="center">

<a href="https://nebuliton.io">
  <img src="https://nebuliton.io/logo.png" alt="Nebuliton Logo" width="200"/>
</a>

# 🚀 NebiUpdate

### *Der ultimative Discord Changelog-Bot*

[![MIT License](https://img.shields.io/badge/License-MIT%20with%20Attribution-blue.svg)](LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.0-blue.svg)](https://www.typescriptlang.org/)
[![Made with ❤️ by Nebuliton](https://img.shields.io/badge/Made%20with%20%E2%9D%A4%EF%B8%8F%20by-Nebuliton-ff69b4)](https://nebuliton.io)

**Automatisierte Wochen-Changelogs** • **Web-Dashboard** • **Slash-Commands** • **Analytics** • **i18n Support**

[Features](#-features) •
[Schnellstart](#-schnellstart) •
[Dashboard](#-dashboard) •
[Commands](#-commands) •
[API](#-api-dokumentation) •
[Lizenz](#-lizenz)

</div>

---

## ✨ Features

<table>
  <tr>
    <td width="50%">
      
### 🤖 Discord Integration
- ✅ Automatische **Wochen-Changelogs** (konfigurierbar)
- ✅ Moderne **Slash-Commands** (`/update add|edit|remove|list|sync|test`)
- ✅ **Discord Components V2** Support
- ✅ Emoji-Support für custom & animated Emojis
- ✅ Auto-Sync mit Discord-Nachrichten

    </td>
    <td width="50%">
      
### 🎨 Web-Dashboard
- ✅ **React + TypeScript** Frontend
- ✅ Live-**Preview** der Discord-Nachricht
- ✅ Update-Management (Add/Edit/Delete)
- ✅ **Config-Editor** direkt im Browser
- ✅ **Analytics & Trends** (12 Wochen)

    </td>
  </tr>
  <tr>
    <td width="50%">
      
### 📊 Daten & Analytics
- ✅ **SQLite** Datenbank (keine externe DB nötig)
- ✅ **Audit-Log** (wer hat was wann geändert)
- ✅ **Export/Import** (JSON & CSV)
- ✅ **Backup/Restore** mit Retention
- ✅ Trend-Analyse pro Woche

    </td>
    <td width="50%">
      
### 🌍 Internationale Features
- ✅ **i18n Support** (Deutsch & Englisch)
- ✅ Zeitzone-Support (per `config.yml`)
- ✅ ISO-Wochen-Kalender (Mo-So)
- ✅ Lokalisierte Dashboard-Texte
- ✅ Fallback-Locale System

    </td>
  </tr>
</table>

---

## 🚀 Schnellstart

### Voraussetzungen

- ☕ **Java 21** oder höher
- 📦 **Maven 3.9+**
- 🤖 Discord Bot Token ([Discord Developer Portal](https://discord.com/developers/applications))

### Installation

```powershell
# 1. Repository klonen
git clone https://github.com/your-repo/NebiUpdate.git
cd NebiUpdate

# 2. Projekt bauen
mvn -DskipTests package

# 3. Bot starten
java -jar target/NebiUpdate-1.0.0.jar
```

### Erste Schritte

1. 📝 Beim ersten Start wird automatisch eine `config.yml` erstellt
2. ⚙️ Fülle mindestens folgende Felder aus:
   ```yaml
   discord:
     token: "DEIN_BOT_TOKEN"
     guild_id: "DEINE_SERVER_ID"
     channel_id: "DEIN_CHANNEL_ID"
   ```
3. 🔄 Starte den Bot neu
4. 🌐 Öffne das Dashboard: **http://127.0.0.1:8080/**

---

## 🎨 Dashboard

Das moderne **React + TypeScript** Dashboard bietet dir volle Kontrolle:

### Frontend Entwicklung

```powershell
cd frontend
npm install
npm run dev     # Development-Server
npm run build   # Production-Build
```

### Dashboard Features

| Feature | Beschreibung |
|---------|-------------|
| 📋 **Update-Management** | Erstelle, bearbeite und lösche Updates direkt im Browser |
| 👁️ **Live-Preview** | Sieh in Echtzeit, wie deine Nachricht in Discord aussehen wird |
| ⚙️ **Config-Editor** | Bearbeite alle Bot-Einstellungen ohne Datei-Zugriff |
| 📊 **Analytics** | Visualisiere Trends und Statistiken der letzten 12 Wochen |
| 📥 **Export/Import** | Sichere und teile deine Daten (JSON/CSV) |
| 💾 **Backup/Restore** | Erstelle Snapshots und stelle sie wieder her |
| 📜 **Audit-Log** | Vollständige Historie aller Änderungen |

---

## 🎮 Commands

### Slash-Commands (Discord)

<table>
  <tr>
    <th width="30%">Command</th>
    <th>Beschreibung</th>
    <th width="30%">Parameter</th>
  </tr>
  <tr>
    <td><code>/update add</code></td>
    <td>Fügt einen neuen Update-Eintrag hinzu</td>
    <td><code>type</code>, <code>text</code></td>
  </tr>
  <tr>
    <td><code>/update edit</code></td>
    <td>Bearbeitet einen bestehenden Eintrag</td>
    <td><code>id</code>, <code>type</code>, <code>text</code></td>
  </tr>
  <tr>
    <td><code>/update remove</code></td>
    <td>Löscht einen Eintrag</td>
    <td><code>id</code></td>
  </tr>
  <tr>
    <td><code>/update list</code></td>
    <td>Zeigt alle Einträge der aktuellen Woche</td>
    <td>-</td>
  </tr>
  <tr>
    <td><code>/update sync</code></td>
    <td>Synchronisiert die Wochen-Nachricht sofort</td>
    <td>-</td>
  </tr>
  <tr>
    <td><code>/update test</code></td>
    <td>Sendet eine Test-Nachricht</td>
    <td>-</td>
  </tr>
</table>

### Console-Commands

```powershell
help              # Zeigt alle verfügbaren Commands
status            # Bot-Status und Statistiken
sync              # Sofortige Synchronisation
test              # Test-Nachricht senden
config            # Zeigt aktuelle Konfiguration
set <key> <value> # Setzt einen Config-Wert
commands          # Zeigt Discord-Commands
exit              # Beendet den Bot
```

---

## ⚙️ Konfiguration

Die `config.yml` wird beim ersten Start automatisch erstellt. Hier die wichtigsten Optionen:

<details>
<summary><b>🔧 Discord-Einstellungen</b></summary>

```yaml
discord:
  token: "YOUR_BOT_TOKEN"           # Discord Bot Token
  guild_id: "YOUR_GUILD_ID"         # Server-ID
  channel_id: "YOUR_CHANNEL_ID"     # Channel-ID für Wochen-Nachrichten
```
</details>

<details>
<summary><b>⏰ Zeitplan</b></summary>

```yaml
schedule:
  timezone: "Europe/Berlin"         # Zeitzone (IANA-Format)
  day: "MONDAY"                     # Wochentag für Auto-Post
  time: "09:00"                     # Uhrzeit (HH:mm)
```
</details>

<details>
<summary><b>🌐 Dashboard</b></summary>

```yaml
dashboard:
  host: "127.0.0.1"                 # Dashboard-Host
  port: 8080                        # Dashboard-Port
  token: "your-secure-token"        # API-Token (Auto-generiert)
```
</details>

<details>
<summary><b>🎨 Nachrichten-Anpassung</b></summary>

```yaml
messages:
  title_emoji: "📝"                 # Titel-Emoji
  title_text: "Wochen-Update"       # Titel-Text
  added_emoji: "✅"                 # "Added"-Emoji
  changed_emoji: "🔄"               # "Changed"-Emoji
  removed_emoji: "❌"               # "Removed"-Emoji
  notice_emoji: "ℹ️"                # "Notice"-Emoji
  notice_text: "Hinweis"            # Notice-Text
  no_change_text: "Keine Änderungen" # Text für leere Woche
  spacer: "―――――――――――――――――――"      # Trennlinie
```
</details>

<details>
<summary><b>🌍 Internationalisierung</b></summary>

```yaml
i18n:
  enabled: true                     # i18n aktivieren
  locale: "de"                      # Hauptsprache (de/en)
  fallback_locale: "en"             # Fallback-Sprache
```
</details>

<details>
<summary><b>💾 Backup-Einstellungen</b></summary>

```yaml
backup:
  enabled: true                     # Backup-Funktion aktivieren
  directory: "data/backups"         # Backup-Verzeichnis
  max_files: 20                     # Max. Anzahl Backups (Retention)
  include_audit: true               # Audit-Log in Backups einschließen
```
</details>

<details>
<summary><b>📊 Features</b></summary>

```yaml
features:
  audit_enabled: true               # Audit-Log aktivieren
  audit_max_entries: 5000           # Max. Audit-Einträge
  export_import_enabled: true       # Export/Import aktivieren
  analytics_enabled: true           # Analytics aktivieren
  analytics_weeks: 12               # Anzahl Wochen für Trends
```
</details>

---

## 📡 API-Dokumentation

Alle API-Endpunkte erfordern den Header: `X-Dashboard-Token: <your-token>`

### Status & Konfiguration

| Method | Endpoint | Beschreibung |
|--------|----------|-------------|
| `GET` | `/api/status` | Bot-Status und System-Info |
| `GET` | `/api/config` | Aktuelle Konfiguration |
| `PUT` | `/api/config` | Konfiguration aktualisieren |

### Updates

| Method | Endpoint | Beschreibung |
|--------|----------|-------------|
| `GET` | `/api/updates/current` | Alle Updates der aktuellen Woche |
| `POST` | `/api/updates/current` | Neuen Update-Eintrag erstellen |
| `PUT` | `/api/updates/current/{id}` | Update bearbeiten |
| `DELETE` | `/api/updates/current/{id}` | Update löschen |

### Preview & Aktionen

| Method | Endpoint | Beschreibung |
|--------|----------|-------------|
| `GET` | `/api/preview/current` | Preview der Discord-Nachricht |
| `POST` | `/api/actions/sync` | Wochen-Nachricht synchronisieren |
| `POST` | `/api/actions/test` | Test-Nachricht senden |

### Analytics & Audit

| Method | Endpoint | Beschreibung |
|--------|----------|-------------|
| `GET` | `/api/analytics` | Analytics-Daten (Trends, Stats) |
| `GET` | `/api/audit?limit=120` | Audit-Log abrufen |

### Export & Import

| Method | Endpoint | Beschreibung |
|--------|----------|-------------|
| `GET` | `/api/export/json?include_audit=true` | JSON-Export |
| `GET` | `/api/export/csv?scope=all\|current` | CSV-Export |
| `POST` | `/api/import/json` | JSON-Import |
| `POST` | `/api/import/csv` | CSV-Import |

### Backup & Restore

| Method | Endpoint | Beschreibung |
|--------|----------|-------------|
| `GET` | `/api/backups` | Liste aller Backups |
| `POST` | `/api/actions/backup` | Neues Backup erstellen |
| `POST` | `/api/actions/restore` | Backup wiederherstellen |

---

## 🛠️ Tech-Stack

<div align="center">

| Backend | Frontend | Database | Tools |
|---------|----------|----------|-------|
| ☕ Java 21 | ⚛️ React | 💾 SQLite | 📦 Maven |
| 🎮 JDA | 📘 TypeScript | 🏊 HikariCP | ⚡ Vite |
| 🌐 Javalin | 🎨 Modern CSS | - | 📝 Logback |

</div>

---

## 📊 Audit-System

Jede Änderung wird automatisch protokolliert:

- ✅ **Actor**: Wer hat die Änderung vorgenommen?
- ✅ **Source**: Dashboard, Discord oder Console?
- ✅ **Action**: CREATE, UPDATE, DELETE, SYNC, etc.
- ✅ **Entity**: UPDATE, CONFIG, BACKUP, etc.
- ✅ **Details**: Was genau wurde geändert?
- ✅ **Timestamp**: Wann erfolgte die Änderung?

Optional kannst du im Dashboard den Header `X-Actor` setzen, um einen custom Actor-Namen zu verwenden.

---

## 💾 Backup & Restore

### Automatisches Backup-Management

- 📁 Snapshots werden in `data/backups` gespeichert
- 🔄 Retention über `backup.max_files` (älteste werden gelöscht)
- 📦 Backups enthalten: Config, Updates, Weekly-Messages & optional Audit-Log

### Restore-Optionen

```json
{
  "file": "backup_2026-03-01_14-30-00.json",
  "replaceConfig": true,
  "replaceAudit": false
}
```

---

## 🌍 Internationalisierung (i18n)

NebiUpdate unterstützt mehrere Sprachen:

- 🇩🇪 **Deutsch** (Standard)
- 🇬🇧 **Englisch**

Die Sprache wird sowohl im **Dashboard** als auch in **Discord-Nachrichten** verwendet.

### Spracheinstellung

```yaml
i18n:
  enabled: true
  locale: "de"           # de oder en
  fallback_locale: "en"  # Fallback wenn Übersetzung fehlt
```

---

## 📈 Analytics

Das Dashboard zeigt dir detaillierte Statistiken:

- 📊 **Trend-Diagramme** (12 Wochen)
- 📈 **Verteilung** nach Type (Added/Changed/Removed)
- 🎯 **Wochen-Vergleich**
- 📉 **Historical Data**

---

## 🤝 Contributing

Wir freuen uns über Contributions! 

1. Fork das Repository
2. Erstelle einen Feature-Branch (`git checkout -b feature/AmazingFeature`)
3. Commit deine Änderungen (`git commit -m 'Add some AmazingFeature'`)
4. Push zum Branch (`git push origin feature/AmazingFeature`)
5. Öffne einen Pull Request

---

## 📄 Lizenz

Dieses Projekt ist unter der **MIT License with Attribution Requirement** lizenziert.

**Das bedeutet:**
- ✅ **Kostenlose Nutzung** für private & kommerzielle Projekte
- ✅ **Modifikation & Weitergabe** erlaubt
- ✅ **Keine Garantie** oder Haftung
- ⚠️ **Namensnennung erforderlich**: Du musst **[Nebuliton](https://nebuliton.io)** als Urheber nennen

Details findest du in der [LICENSE](LICENSE) Datei.

---

## 💬 Support

Brauchst du Hilfe? Wir sind für dich da!

- 🌐 **Website**: [nebuliton.io](https://nebuliton.io)
- 📧 **Issues**: [GitHub Issues](https://github.com/your-repo/NebiUpdate/issues)
- 💬 **Discord**: [Unser Discord Server](https://discord.gg/your-server)

---

<div align="center">

### Entwickelt mit ❤️ von [Nebuliton](https://nebuliton.io)

[![Nebuliton](https://img.shields.io/badge/Powered%20by-Nebuliton-blueviolet?style=for-the-badge)](https://nebuliton.io)

**Wenn dir NebiUpdate gefällt, gib uns einen ⭐ auf GitHub!**

</div>

