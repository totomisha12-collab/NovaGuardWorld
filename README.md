# NovaGuardWORLD

**Developer:** hokagydev  
**Runtime:** Java 21 / Paper 1.21.x  
**Storage:** SQLite  
**Build:** Gradle

NovaGuardWORLD is a standalone cuboid-region protection plugin. WorldEdit and other runtime plugins are not required.

## Source layout

```text
src/main/java/dev/hokagydev/novaguard/
├── NovaGuardWorld.java
├── locale/
│   └── LocaleManager.java
├── model/
│   └── Claim.java
└── storage/
    └── ClaimStore.java

src/main/resources/
├── config.yml
├── plugin.yml
└── lang/
    ├── messages_ru.yml
    └── messages_en.yml
```

The root plugin class is the Paper entry point and lifecycle coordinator. Region data structures, persistence and localization are kept in separate packages. All Java source comments have been removed as requested.

## Localization

Translation files are bundled at exactly these paths:

- `src/main/resources/lang/messages_ru.yml`
- `src/main/resources/lang/messages_en.yml`

On first start both files are copied to:

- `plugins/NovaGuardWORLD/lang/messages_ru.yml`
- `plugins/NovaGuardWORLD/lang/messages_en.yml`

Select the active language in `config.yml`:

```yaml
settings:
  locale: ru
```

Messages support MiniMessage, `&#RRGGBB` hex colors and legacy `&` color/style codes. Missing translation keys fall back to a visible key marker instead of crashing the plugin. `/ng reload` reloads the configuration and selected translation file.

## Features

- Cuboid selection with a configurable wand, `/ng pos1`, `/ng pos2` and `/ng auto [radius]`.
- Region creation, deletion, listing and information display.
- UUID-based owners and trusted members.
- Region flags: `build`, `pvp`, `interact`, `chest-access`, `mob-damage`, `explosions`, `tnt`, `fire`, `entry`.
- Region overlap support; active region selection uses priority and then smaller volume.
- Priority, greeting and farewell persistence in SQLite.
- SQLite schema migration for priority, greeting and farewell columns.
- Protection handlers for block changes, interactions, buckets, explosions, fire, pistons, fluids, dispensers, entity block changes, hanging entities, vehicles and hopper transfers.
- Particle boundary preview.
- Russian and English messages.

## Build

JDK 21 is required.

Linux/macOS:

```bash
./gradlew clean build
```

Windows:

```bat
gradlew.bat clean build
```

The wrapper scripts use the pinned Gradle 8.10.2 distribution and do not depend on a globally installed Gradle. The first build requires network access to download Gradle and dependencies.

The shaded plugin JAR is produced at:

```text
build/libs/NovaGuardWORLD-1.1.0.jar
```

GitHub Actions uses JDK 21, the repository wrapper script and uploads the shaded JAR as an artifact.

## Install

1. Build the project or download the JAR from the GitHub Actions artifact.
2. Place the JAR in the Paper server's `plugins/` directory.
3. Start the server once to create the configuration, language files and SQLite database.
4. Configure `settings.locale` in `plugins/NovaGuardWORLD/config.yml`.
5. Edit `plugins/NovaGuardWORLD/lang/messages_ru.yml` or `messages_en.yml` and run `/ng reload`.

## Commands

- `/ng` — help
- `/ng wand` — give the selection tool
- `/ng pos1`, `/ng pos2` — set selection at the player's position
- `/ng claim <name>` or `/ng create <name>` — create a region from the selection
- `/ng auto [radius]` — select an area around the player
- `/ng delete <name>` — delete an owned region
- `/ng info [name]` — inspect a region
- `/ng list` — list owned regions
- `/ng trust <player>`, `/ng untrust <player>` — manage trusted members in the current region
- `/ng flag <name> <flag> <allow|deny>` — set a region flag
- `/ng flags` — list supported flags
- `/ng priority <number>` — set current region priority
- `/ng greeting <text>`, `/ng farewell <text>` — configure entry/exit messages
- `/ng view` — toggle boundary particles
- `/ng reload` — reload configuration and translations (admin)

## Permissions

- `novaguard.claim` — selection and region creation (default: true)
- `novaguard.admin` — administrative region management and reload (default: op)
- `novaguard.bypass` — bypass region protection (default: op)

## Validation status

All resource YAML files parse successfully. Both language files contain every message key referenced by the current Java command handlers. Java source files are organized into `model`, `storage` and `locale` packages, and no `//` or block comments remain in Java source.

A local Gradle build could not be completed in the current environment: the wrapper could not download Gradle 8.10.2 because outbound DNS/network access is unavailable (`Could not resolve host: services.gradle.org`). Therefore this delivery does not include a JAR and does not claim a successful compile. GitHub Actions is configured to perform the real JDK 21 build on GitHub-hosted runners.
