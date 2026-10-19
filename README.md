<div align="center">

# LoReport

Modern player report system with Discord integration, GUI and database support for Paper servers.

![Java](https://img.shields.io/badge/Java-21+-orange?style=flat-square&logo=openjdk&logoColor=white)
![Paper](https://img.shields.io/badge/Paper-1.19.2+-blue?style=flat-square)
![Folia](https://img.shields.io/badge/Folia-supported-purple?style=flat-square)
![License](https://img.shields.io/badge/license-GPLv3-blue?style=flat-square&logo=gnu&logoColor=white)
![version](https://img.shields.io/badge/version-1.1.0-green?style=flat-square)

[English](#english) | [Русский](#russian)

</div>

---

<a name="english"></a>

## English

### Overview

LoReport is a modern and performant player report system for Minecraft servers. It features an intuitive GUI, Discord webhook integration, statistics, and supports both SQLite and PostgreSQL databases.

### Features

| Feature | Description |
|---------|-------------|
| Report system | `/report <player> <reason>` with cooldown and limits |
| GUI interface | Browse and manage reports in an inventory menu |
| Player heads | Visual player identification in GUI |
| Pagination | Navigate large report lists |
| Statistics | Per-player report history and stats |
| Discord integration | Instant webhook notifications with embeds |
| SQLite | Zero-config database for small servers |
| PostgreSQL | Scalable database for large servers |
| Async database | All DB operations off the main thread |
| Localization | Fully customizable messages |
| SQL injection protection | All queries use PreparedStatement |
| PlaceholderAPI | Report stats in other plugins |

### Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/report <player> <reason>` | `lorep.report` | Report a player |
| `/report gui` | `lorep.admin` | Open reports GUI |
| `/report stats <player>` | `lorep.admin` | View player report stats |
| `/report ac` | `lorep.admin` | Open the anticheat (ServGuard) GUI |
| `/rep` | `lorep.report` | Alias for /report |

### Dependencies

- Required: Paper 1.19.2+, Java 21+
- Optional: PlaceholderAPI
- Database: SQLite 3.47+ (default) or PostgreSQL 12+

### Installation

1. Drop the jar into `plugins/`
2. Restart the server
3. Configure Discord webhook in `plugins/Loreport/config.yml`:
   ```yaml
   webhook-url: "https://discord.com/api/webhooks/YOUR_WEBHOOK_URL"
   ```

---

<a name="russian"></a>

## Русский

### Обзор

LoReport - современная и производительная система репортов для Minecraft серверов. Включает интуитивный GUI, Discord webhook интеграцию, статистику и поддержку SQLite и PostgreSQL.

### Возможности

| Возможность | Описание |
|-------------|----------|
| Система репортов | `/report <игрок> <причина>` с кулдауном и лимитами |
| GUI интерфейс | Просмотр и управление репортами в меню |
| Головы игроков | Визуальная идентификация в GUI |
| Пагинация | Навигация по большим спискам |
| Статистика | История репортов по каждому игроку |
| Discord интеграция | Мгновенные уведомления через webhook с embed |
| SQLite | Встроенная БД без настройки |
| PostgreSQL | Масштабируемая БД для крупных серверов |
| Асинхронная БД | Все запросы вне основного потока |
| Локализация | Полностью настраиваемые сообщения |
| Защита от SQL-иньекций | Все запросы через PreparedStatement |
| PlaceholderAPI | Статистика репортов в других плагинах |

### Команды

| Команда | Право | Описание |
|---------|-------|----------|
| `/report <игрок> <причина>` | `lorep.report` | Отправить репорт |
| `/report gui` | `lorep.admin` | Открыть GUI репортов |
| `/report stats <игрок>` | `lorep.admin` | Статистика репортов игрока |
| `/report ac` | `lorep.admin` | GUI античита (ServGuard) |

### Зависимости

- Обязательные: Paper 1.19.2+, Java 21+
- Опциональные: PlaceholderAPI
- База данных: SQLite 3.47+ (по умолчанию) или PostgreSQL 12+

### Установка

1. Положите jar в папку `plugins/`
2. Перезапустите сервер
3. Настройте Discord webhook в `plugins/Loreport/config.yml`:
   ```yaml
   webhook-url: "https://discord.com/api/webhooks/YOUR_WEBHOOK_URL"
   ```

---

### Links

- [Releases](../../releases)
- [Issues](../../issues)
- [License](LICENSE)

### License

GNU General Public License v3.0