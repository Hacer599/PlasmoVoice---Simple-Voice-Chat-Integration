# Plasmo Voice Simple Voice Chat Bridge

A Fabric client addon that lets Plasmo Voice connect to servers running Simple Voice Chat without requiring the Simple Voice Chat client mod.

Plasmo Voice remains the primary voice system. When a server only provides Simple Voice Chat, the addon connects to its voice service directly. The addon does not start a second connection when Plasmo Voice is active.

## Requirements

- Fabric Loader
- Fabric API
- Plasmo Voice 2.1.16 or newer
- Minecraft 1.17.1 through 1.21.11

Install the addon JAR for your Minecraft version alongside Plasmo Voice in the `mods` folder. No Simple Voice Chat client mod is required.

## Features

- Detects Plasmo Voice and Simple Voice Chat server support
- Prefers Plasmo Voice when both systems are available
- Bridges Simple Voice Chat proximity audio and supports incoming group audio when sent by the server
- Uses Plasmo Voice microphone, speaker, activation, mute, and volume settings
- Adds SVC controls to the Plasmo Voice add-ons settings
- Provides per-player volume controls in the Plasmo Voice volume tab
- Supports 53 Minecraft locales

## Settings

Open the Plasmo Voice settings and find the `SVC` section in the add-ons tab. It contains backend priority, bridge, fallback, and output volume settings.

The SVC server controls proximity range. Local activation mode and threshold control when the microphone transmits.

## Compatibility

The project provides a separate build for each Minecraft version from 1.17.1 through 1.21.11. The SVC transport uses compatibility version 20.

Builds are checked for all supported versions. Runtime audio testing has been completed on Minecraft 1.21.11; older versions still need testing with their matching Simple Voice Chat servers.

## Build

Use `build-all.bat` on Windows or `build-all.sh` on Linux and macOS to build all supported Minecraft versions. The resulting addon JARs are placed in `dist`.

## Banner assets

Animated project banners, section headers, transparent pixel-art items, and social buttons are available in [`banners/`](./banners/). The Twitch and YouTube buttons link to the project author's channels.

[![Twitch](./banners/twitch-button.gif)](https://www.twitch.tv/faspadio_if_tve)
[![YouTube](./banners/youtube-button.gif)](https://www.youtube.com/@hoziain_murki)

## Limitations

The addon implements the Simple Voice Chat client protocol directly. Compatibility depends on the protocol version used by the server. Client-side addons that require the Simple Voice Chat client mod are not supported.

The SVC server controls voice range and distribution. Plasmo Voice activation distance and its preview sphere do not change the server's range.
