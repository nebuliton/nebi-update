# NebiUpdate

NebiUpdate ist ein moderner Discord-Bot für automatisierte Wochen-Changelogs mit Web-Dashboard, SQLite-Datenbank und intuitiven Slash- sowie Konsolenbefehlen. Perfekt für Teams, die ihre wöchentlichen Änderungen transparent und komfortabel im Discord teilen möchten.

---

## ✨ Features

- **Automatische Wochen-Nachricht** zum frei wählbaren Zeitpunkt
- **Slash-Commands** (`/update add|edit|remove|list|sync|test`) für die aktuelle Woche
- **Web-Dashboard** zur Verwaltung von Config & Wochenupdates
- **Console-Commands** für Power-User
- **Test-Sendeweg** (Slash, Dashboard, Console)
- **Automatisch erzeugte `config.yml`**
- **Discord Components V2** für moderne, interaktive Wochenposts
- **SQLite** als Datenbank (keine externe DB nötig)

---

## 🚀 Schnellstart

1. **Java 21** und **Maven 3.9+** installieren
2. Projekt bauen & starten:
   ```powershell
   mvn -DskipTests package
   java -jar target/NebiUpdate-1.0.0.jar
   ```
3. `config.yml` wird beim ersten Start erzeugt – passe mindestens `discord.token` und `discord.channel_id` an
4. Dashboard öffnen: [http://127.0.0.1:8080/](http://127.0.0.1:8080/)

---

## ⚙️ Wichtige Konfigurationsfelder

- `guild_id`: Optional, für guild-spezifische Slash-Commands
- `channel_id`: Zielkanal für Wochenpost
- `timezone`: z.B. `Europe/Berlin`
- `schedule_day`: `MONDAY` bis `SUNDAY`
- `schedule_time`: `HH:mm`
- `dashboard_host`: `0.0.0.0` (öffentlich) oder `127.0.0.1` (nur lokal)
- Emoji-Optionen: `title_emoji`, `added_emoji`, `changed_emoji`, `removed_emoji` (+ IDs & animiert)
- `notice_text`, `no_change_text`: Texte für die Wochenmeldung

---

## 💬 Slash Commands

- `/update add` – Eintrag hinzufügen (`type`, `text`)
- `/update edit` – Eintrag bearbeiten (`id`, optional `type`/`text`)
- `/update remove` – Eintrag löschen (`id`)
- `/update list` – Zeigt aktuelle Woche
- `/update sync` – Erzwingt sofortige Synchronisierung
- `/update test` – Test-Nachricht senden

## 🖥️ Console Commands

- `help`, `status`, `sync`, `test`, `config`, `set <key> <value>`, `commands`, `exit`

---

## 🛠️ Technologie-Stack

- **Java 21**
- **Maven**
- **JDA** (Discord API)
- **Javalin** (Webserver)
- **SQLite** (lokale DB)
- **Logback** (Logging)

---

## 📄 Lizenz

Dieses Projekt steht unter einer MIT-ähnlichen Lizenz mit Namensnennungspflicht. Siehe [LICENSE](LICENSE).

---

## 👤 Autoren & Mitwirken

- Hauptautor: **Eministar | nebuliton.io**
- Pull Requests, Bugreports und Feature-Ideen sind willkommen!

---

## 💡 Hinweise

- Der Bot verwaltet immer die aktuelle ISO-Woche (Mo–So)
- Dashboard & Slash-Commands bearbeiten nur Einträge der aktuellen Woche
- Änderungen an der Config werden synchronisiert (Dashboard/Console)
- Bei gesetztem Dashboard-Token muss dieser im Dashboard (Header `X-Dashboard-Token`) eingetragen werden

---

Viel Spaß mit NebiUpdate! Bei Fragen oder Feedback gerne ein Issue eröffnen oder Kontakt aufnehmen.
