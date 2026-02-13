package com.peonping.jetbrains

import java.nio.file.Path

object PeonPaths {
    val peonDir: Path by lazy {
        val explicit = System.getenv("CLAUDE_PEON_DIR")
        if (!explicit.isNullOrBlank()) {
            return@lazy Path.of(explicit)
        }

        val claudeConfigDir = System.getenv("CLAUDE_CONFIG_DIR")
        if (!claudeConfigDir.isNullOrBlank()) {
            return@lazy Path.of(claudeConfigDir).resolve("hooks").resolve("peon-ping")
        }

        Path.of(System.getProperty("user.home"), ".claude", "hooks", "peon-ping")
    }

    val configPath: Path
        get() = peonDir.resolve("config.json")

    val packsDir: Path
        get() = peonDir.resolve("packs")

    val pausedPath: Path
        get() = peonDir.resolve(".paused")
}

