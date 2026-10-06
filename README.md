<div align="center">

# 🎙️ Plasmo Voice - Simple Voice Chat Bridge

**Use Plasmo Voice on servers that run Simple Voice Chat.**<br>
One client addon. No Simple Voice Chat client mod required.

![Project banner](https://cdn.modrinth.com/data/ssioB7X9/images/cad37a45fdea86938a9713dd0d84a90752cad41c.jpeg)

[GitHub](https://github.com/Hacer599/PlasmoVoice---Simple-Voice-Chat-Integration) · [Twitch](https://www.twitch.tv/faspadio_if_tve) · [YouTube](https://www.youtube.com/@hoziain_murki)

</div>

---

A **Fabric client addon for [Plasmo Voice](https://modrinth.com/mod/plasmo-voice)** that implements the **Simple Voice Chat (SVC) client protocol**. Join SVC servers and use voice chat through Plasmo Voice without installing the SVC client mod.

Plasmo Voice remains the preferred voice system. The addon detects the server's available voice backend and uses the SVC bridge when needed. It does not open the bridge alongside an active Plasmo Voice connection.

![Features](https://img.shields.io/badge/FEATURES-E0BF2B?style=for-the-badge&labelColor=1e1e1e)

- Detects Plasmo Voice and Simple Voice Chat server support.
- Prefers Plasmo Voice when both systems are available.
- Bridges SVC proximity audio and receives group audio when the server sends it.
- Uses Plasmo Voice microphone, speaker, activation, mute, and volume settings.
- Adds bridge controls to the Plasmo Voice add-ons settings.
- Provides per-player volume controls in Plasmo Voice's volume tab.
- Includes translations for 53 Minecraft locales.

![Settings](https://cdn.modrinth.com/data/ssioB7X9/images/2fbd1b0f82fb91d66144b1f3099a710300d719bb.png)

![Installation](https://img.shields.io/badge/INSTALLATION-3B82F6?style=for-the-badge&labelColor=1e1e1e)

1. Install **Fabric Loader** and **Fabric API**.
2. Install **Plasmo Voice 2.1.16 or newer**.
3. Download the addon JAR matching your Minecraft version.
4. Put the addon JAR in your Minecraft `mods` folder alongside Plasmo Voice.
5. Join a server that uses Simple Voice Chat.

The Simple Voice Chat client mod is not required.

Open Plasmo Voice settings, select **Add-ons**, and find the **SVC** section. It contains options for backend priority, enabling the bridge, fallback behavior, allowing both backends, chat announcements, debug logging, and bridge output volume.

The SVC server controls proximity range and voice distribution. Plasmo Voice activation mode and threshold control when your microphone transmits; its activation-distance slider does not change the server's range.

![Compatibility](https://img.shields.io/badge/COMPATIBILITY-9A9695?style=for-the-badge&labelColor=1e1e1e)

| Component | Support |
| --- | --- |
| Loader | Fabric |
| Minecraft | 1.17.1 through 1.21.11, with a separate build for each supported version |
| Plasmo Voice | 2.1.16 or newer |
| SVC protocol | Compatibility version 20 |

All supported version profiles have been build-checked. Runtime audio testing has been completed on **Minecraft 1.21.11**. Older versions still need in-game testing with matching Simple Voice Chat server releases.

## Build from source

Use Java 21 to build the latest profile. On Windows, run `.\gradlew.bat clean build -Pmc=1.21.11`; on Linux or macOS, run `./gradlew clean build -Pmc=1.21.11`. To build every supported version on Windows, run `.\build-all.bat`; the JAR files are placed in `dist`.

![Limitations](https://img.shields.io/badge/LIMITATIONS-F59E0B?style=for-the-badge&labelColor=1e1e1e)

- The addon implements the SVC client protocol directly. Compatibility depends on the protocol version used by the server.
- Client-side addons that require the Simple Voice Chat client mod are not supported.
- The SVC server controls voice range and distribution. Plasmo Voice's activation-distance preview cannot change them.

## FAQ

**Do I need the Simple Voice Chat client mod?**

No. Install Plasmo Voice and this addon.

**What happens on a Plasmo Voice server?**

Plasmo Voice remains the preferred backend.

**Is there a server-side addon?**

No. This project is a Fabric client addon.

The project is provided under the terms stated in [LICENSE.txt](./LICENSE.txt). All rights reserved.

This is an unofficial addon and is not affiliated with or endorsed by the Plasmo Voice or Simple Voice Chat teams.

---

<div align="center">

# 🇷🇺 Русская версия

</div>

**Клиентский Fabric-аддон для [Plasmo Voice](https://modrinth.com/mod/plasmo-voice)**, который напрямую реализует **клиентский протокол Simple Voice Chat (SVC)**. С этим аддоном можно пользоваться голосовым чатом на SVC-серверах через Plasmo Voice, не устанавливая клиентский мод Simple Voice Chat.

Plasmo Voice остаётся предпочтительной голосовой системой. Аддон определяет доступный на сервере бэкенд и включает мост SVC, когда он нужен. Мост не подключается параллельно с активным соединением Plasmo Voice.

![Возможности](https://img.shields.io/badge/%D0%92%D0%9E%D0%97%D0%9C%D0%9E%D0%96%D0%9D%D0%9E%D0%A1%D0%A2%D0%98-E0BF2B?style=for-the-badge&labelColor=1e1e1e)

- Определяет поддержку Plasmo Voice и Simple Voice Chat на сервере.
- При наличии обеих систем отдаёт приоритет Plasmo Voice.
- Передаёт проксимити-аудио SVC и принимает групповой звук, если сервер его отправляет.
- Использует настройки микрофона, динамика, активации, мута и громкости Plasmo Voice.
- Добавляет настройки моста во вкладку дополнений Plasmo Voice.
- Добавляет регулировку громкости игроков во вкладку громкости Plasmo Voice.
- Включает переводы для 53 локалей Minecraft.

![Установка](https://img.shields.io/badge/%D0%A3%D0%A1%D0%A2%D0%90%D0%9D%D0%9E%D0%92%D0%9A%D0%90-3B82F6?style=for-the-badge&labelColor=1e1e1e)

1. Установи **Fabric Loader** и **Fabric API**.
2. Установи **Plasmo Voice версии 2.1.16 или новее**.
3. Скачай JAR аддона для своей версии Minecraft.
4. Помести JAR в папку `mods` рядом с Plasmo Voice.
5. Зайди на сервер, использующий Simple Voice Chat.

Устанавливать клиентский мод Simple Voice Chat не нужно.

Открой настройки Plasmo Voice, выбери вкладку **Add-ons** и найди раздел **SVC**. В нём находятся настройки приоритета голосовой системы, включения моста, резервного подключения, одновременной работы обоих бэкендов, сообщений в чат, подробного логирования и общей громкости SVC.

Дистанцию и распространение голоса определяет SVC-сервер. Режим и порог активации Plasmo Voice управляют тем, когда микрофон передаёт звук; ползунок дистанции активации Plasmo Voice не меняет радиус сервера.

![Совместимость](https://img.shields.io/badge/%D0%A1%D0%9E%D0%92%D0%9C%D0%95%D0%A1%D0%A2%D0%98%D0%9C%D0%9E%D0%A1%D0%A2%D0%AC-9A9695?style=for-the-badge&labelColor=1e1e1e)

| Компонент | Поддержка |
| --- | --- |
| Загрузчик | Fabric |
| Minecraft | От 1.17.1 до 1.21.11, отдельная сборка для каждой поддерживаемой версии |
| Plasmo Voice | Версия 2.1.16 или новее |
| Протокол SVC | Compatibility version 20 |

Все профили поддерживаемых версий проверены сборкой. Работа аудио в игре проверена на **Minecraft 1.21.11**. Для старых версий ещё требуется проверка в игре с соответствующими версиями серверного Simple Voice Chat.

## Сборка из исходников

Для сборки актуального профиля используй Java 21. В Windows выполни `.\gradlew.bat clean build -Pmc=1.21.11`, в Linux или macOS — `./gradlew clean build -Pmc=1.21.11`. Чтобы собрать все поддерживаемые версии в Windows, запусти `.\build-all.bat`; JAR-файлы появятся в папке `dist`.

![Ограничения](https://img.shields.io/badge/%D0%9E%D0%93%D0%A0%D0%90%D0%9D%D0%98%D0%A7%D0%95%D0%9D%D0%98%D0%AF-F59E0B?style=for-the-badge&labelColor=1e1e1e)

- Аддон напрямую реализует клиентский протокол SVC. Совместимость зависит от версии протокола на сервере.
- Клиентские дополнения, которым нужен установленный мод Simple Voice Chat, не поддерживаются.
- Дистанцию и распространение голоса определяет SVC-сервер. Предпросмотр дистанции Plasmo Voice не может их изменить.

## Частые вопросы

**Нужен ли клиентский мод Simple Voice Chat?**

Нет. Установи Plasmo Voice и этот аддон.

**Что будет на сервере с Plasmo Voice?**

Предпочтительным бэкендом остаётся Plasmo Voice.

**Есть ли серверная часть?**

Нет. Это клиентский Fabric-аддон.

Проект распространяется на условиях, указанных в файле [LICENSE.txt](./LICENSE.txt). Все права защищены.

Неофициальный аддон, не связанный с командами Plasmo Voice или Simple Voice Chat.

---

<div align="center">

[GitHub](https://github.com/Hacer599/PlasmoVoice---Simple-Voice-Chat-Integration) · [Twitch](https://www.twitch.tv/faspadio_if_tve) · [YouTube](https://www.youtube.com/@hoziain_murki)

</div>
