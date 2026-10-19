# Cesium - 1.21.11

> Acid-minimal Minecraft client: a Fabric mod with a Meteor-style module system and a custom JavaFX launcher.

<p align="center">
  <b>#0A0A0A</b> deep black &nbsp;·&nbsp; <b>#B6FF00</b> acid green &nbsp;·&nbsp; <b>#FFFFFF</b> text
</p>

---

## What's inside

| Part | Tech | Path |
|---|---|---|
| Launcher | Java 21, JavaFX 21, Gson, Maven | `launcher/` |
| Mod (client) | Fabric, Loom, Mojang mappings, Java 21 | `mod/` |

### Launcher
- **Ely.by login** over OAuth2 (authorization code, loopback callback on `127.0.0.1:49152-49162`, refresh tokens).
- **Offline login** with any nickname (3-16 chars). UUID is derived the same way the vanilla offline mode does it.
- **Version picker**: `Cesium 1.21.11 (Fabric)` plus every vanilla release from Mojang's manifest.
- **Install + launch** on background threads: client jar, libraries (with rules), assets (16 parallel downloads, sha1-checked), Fabric profile merge, JVM/game argument building.
- **Cesium build** pulls Fabric API from Modrinth and the Cesium mod jar into `mods/`.
- **Ely.by sessions** run through authlib-injector; tokens are stored owner-only.

### Mod
- **Module system** like Meteor: each feature is a `Module` subclass in its own category package, with typed settings (`BoolSetting`, `NumberSetting`, `ModeSetting`).
- **ClickGUI** on **RShift** (rebindable per module): fade-in on open, eased panel animations, animated toggles and sliders, draggable panels, right-click a module to bind a key.
- **Config** saved to `config/cesium.properties`, hand-editable.

| Category | Module | What it does |
|---|---|---|
| Combat | KillAura | CPS with jitter, range, smooth/instant rotations, multi-target, sort by distance / health / angle |
| Movement | Flight | Vanilla (no gravity) and Velocity modes |
| Movement | Speed | Ground speed with acceleration, capped max, optional auto-jump |
| Movement | NoFall | Packet mode (onGround spoof) or clientside fall-distance reset |
| Render | ESP | Vanilla glow outline on players/mobs, team check, range |
| Render | HUD | Animated watermark `Cesium - 1.21.11`, active module list, FPS, coordinates |
| Player | AutoTotem | Keeps a Totem of Undying in the offhand |
| Player | ChestStealer | Quick-moves container items into your inventory |

---

## Build

### Mod
Requires JDK 21.
```bash
cd mod
gradle build             # or ./gradlew build once the wrapper is generated
# output: mod/build/libs/cesium-mod-1.0.0.jar
gradle runClient         # dev client with Fabric Loader
```

### Launcher
Requires JDK 21 and Maven.
```bash
cd launcher
mvn package              # target/cesium-launcher.jar (shaded, runnable)
mvn javafx:run           # run from sources
```

## Ely.by setup
1. Register an application at <https://account.ely.by/dev/applications>, redirect URI `http://127.0.0.1:49152/callback`.
2. Copy `launcher/launcher.properties.example` to `~/.cesium/launcher.properties` and fill in `ely.client_id` / `ely.client_secret`.
   Environment variables `CESIUM_ELY_CLIENT_ID` / `CESIUM_ELY_CLIENT_SECRET` take precedence.

## Keybinds
- **RShift**: open ClickGUI (rebindable in Controls → Cesium).
- Every module has its own bind; set it from the ClickGUI with right-click.

## Layout

```
cesium/
├── launcher/
│   ├── pom.xml
│   ├── launcher.properties.example
│   └── src/main/
│       ├── java/dev/cesium/launcher/
│       │   ├── App.java, Main.java
│       │   ├── auth/      Ely OAuth2, offline accounts, account store, authlib-injector
│       │   ├── game/      version manifest, installer, rules, launch args, mods
│       │   ├── ui/        LauncherWindow (login + main views)
│       │   └── util/      HTTP, hashing, paths, OS rules
│       └── resources/dev/cesium/launcher/cesium.css
└── mod/
    ├── build.gradle, gradle.properties, settings.gradle
    └── src/main/
        ├── java/dev/cesium/
        │   ├── CesiumClient.java      Fabric entry point
        │   ├── core/                  Theme, Anim, Easing
        │   ├── setting/               Setting, BoolSetting, NumberSetting, ModeSetting
        │   ├── module/                Module, ModuleManager, Category
        │   │   ├── combat/            KillAura
        │   │   ├── movement/          Flight, Speed, NoFall
        │   │   ├── render/            Esp, Hud
        │   │   └── player/            AutoTotem, ChestStealer
        │   ├── gui/                   ClickGui
        │   ├── config/                ConfigManager
        │   └── util/                  Mc
        └── resources/fabric.mod.json
```

## Status
- Written against Minecraft **1.21.11**, Fabric Loader **0.19.5**, Fabric API **0.141.6+1.21.11**, Loom **1.18**.
- ESP is a glow outline in this pass; box ESP needs world-to-screen and is not in yet.

## License
MIT. See [LICENSE](LICENSE).
