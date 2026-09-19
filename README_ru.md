<p align="center">
  <img src="https://raw.githubusercontent.com/tewek444/Light-Engine/fabric/1.21.1/src/main/resources/assets/lightengine/icon.png" width="128" alt="Иконка Light Engine">
</p>

<h1 align="center">Light Engine</h1>

<p align="center">
  <a href="README.md">English</a> | <b>Русский</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.1-44bd32" alt="Minecraft 1.21.1">
  <img src="https://img.shields.io/badge/Загрузчики-Fabric_%7C_NeoForge-e1b12c" alt="Fabric и NeoForge">
  <img src="https://img.shields.io/badge/Java-21-f39c12" alt="Java 21">
  <img src="https://img.shields.io/badge/Статус-Alpha1-e74c3c" alt="Alpha1">
  <img src="https://img.shields.io/badge/Лицензия-MIT-3498db" alt="Лицензия MIT">
</p>

<p align="center"><i>Расширенный блочный свет для Minecraft — выжми из любого блока больше ванильных 15 единиц света.</i></p>

---

## 📦 Ветки

По одной ветке на загрузчик — код живёт там, а не здесь:

| Ветка | Загрузчик | Minecraft | Мод |
| ----- | --------- | --------- | --- |
| [`fabric/1.21.1`](https://github.com/tewek444/Light-Engine/tree/fabric/1.21.1) | Fabric | 1.21.1 | 1.0.0 (Alpha1) |
| [`neoforge/1.21.1`](https://github.com/tewek444/Light-Engine/tree/neoforge/1.21.1) | NeoForge | 1.21.1 | 1.0.0 (Alpha1) |

## ✨ Что умеет мод

- Даёт **блочный свет сильнее 15** — радиус берётся из конфига, для каждого блока свой.
- Ванильное освещение в диапазоне 0–15 работает как раньше, ничего не ломается.
- Дополнительные данные хранятся отдельно, сохраняются вместе с миром и досылаются клиентам вместе с чанками.
- Без пересвета в рендере, свет неба не трогается.
- Один `COMMON` конфиг + экран настроек в игре на клиенте.

## 🗺️ Таблица поддержки

| Minecraft | Загрузчик | Версия мода   | Статус           |
| --------- | --------- | ------------- | ---------------- |
| 1.21.1    | Fabric    | 1.0.0 (Alpha1)| ✅ Поддерживается |
| 1.21.1    | NeoForge  | 1.0.0 (Alpha1)| ✅ Поддерживается |
| 1.20.1    | Fabric    | —             | 🔜 В планах      |
| 26.3      | Fabric    | —             | 🔜 В планах      |
| 26.3      | NeoForge  | —             | 🔜 В планах      |
| любая     | Forge     | —             | ❌ Не будет      |
| любая     | Quilt     | —             | ❌ Не будет      |

## 🔔 Проверка обновлений

Каждая сборка раз за запуск игры качает `update.json` из своей ветки (фоновый поток, таймаут 8с, без интернета — тихо) и один раз пишет в чат со ссылкой на Releases, если вышла версия новее. Ничего само не скачивается. NeoForge дополнительно показывает обновления в списке модов.

## 📄 Лицензия

MIT — форки разрешены, пока сохранены копирайт (`Copyright (c) 2026 Tewek`) и текст лицензии.
