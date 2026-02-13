# peon-ping JetBrains Plugin (Preview)

This plugin brings peon-ping style CESP sounds directly into JetBrains IDEs.

It reuses your existing peon-ping install:

- config: `~/.claude/hooks/peon-ping/config.json`
- packs: `~/.claude/hooks/peon-ping/packs/`
- pause state: `~/.claude/hooks/peon-ping/.paused`

## What it does

- Plays `session.start` when a project opens
- Listens to IDE notifications and maps likely AI-agent events to:
  - `task.complete`
  - `task.error`
  - `task.acknowledge`
  - `input.required`
  - `resource.limit`
- Provides actions in **Find Action**:
  - `Peon Ping: Toggle Pause`
  - `Peon Ping: Next Pack`
  - `Peon Ping: Test Task Complete`

## Build

From repo root (one command):

```bash
./build-jetbrains-plugin.sh
```

Optional: pass custom Gradle task(s), for example:

```bash
./build-jetbrains-plugin.sh clean buildPlugin
```

Equivalent manual build from `adapters/jetbrains`:

```bash
gradle buildPlugin
```

The plugin zip will be generated under:

`adapters/jetbrains/build/distributions/`

## Install in JetBrains IDE

1. Open `Settings` -> `Plugins`
2. Click the gear icon -> `Install Plugin from Disk...`
3. Select the generated zip from `build/distributions`
4. Restart the IDE

## Notes

- This is a heuristic adapter: JetBrains does not expose a single universal "AI task finished" hook across all assistant plugins.
- Best results are with AI plugins that emit clear notifications containing terms like `completed`, `permission`, or `rate limit`.
- Install peon-ping first (`install.sh` or Homebrew) so packs/config exist.
