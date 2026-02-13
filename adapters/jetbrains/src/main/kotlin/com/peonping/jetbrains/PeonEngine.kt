package com.peonping.jetbrains

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlin.random.Random

@Service(Service.Level.APP)
class PeonEngine {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val lastPlayedByCategory = ConcurrentHashMap<String, String>()
    private val sessionPackByProject = ConcurrentHashMap<String, String>()
    private val random = Random.Default

    private data class PeonConfig(
        val enabled: Boolean,
        val volume: Double,
        val activePack: String,
        val packRotation: List<String>,
        val categories: Map<String, Boolean>,
    )

    private data class PackManifest(
        val categories: Map<String, List<String>>,
    )

    fun emitSessionStart(project: Project) {
        emit(CespCategory.SESSION_START, project.name)
    }

    fun emit(category: CespCategory, projectName: String) {
        val config = loadConfig()
        if (!config.enabled) {
            return
        }
        if (config.categories[category.key] == false) {
            return
        }
        if (isPaused()) {
            return
        }

        val packDir = resolvePackDir(config, projectName) ?: return
        val manifest = loadManifest(packDir) ?: return
        val soundPath = pickSoundPath(packDir, manifest, category) ?: return
        playSound(soundPath, config.volume)
    }

    fun handleIdeNotification(project: Project, notification: Notification) {
        if (notification.groupId.equals(NOTIFICATION_GROUP, ignoreCase = true)) {
            return
        }

        val mappedCategory = mapNotificationToCategory(notification) ?: return
        emit(mappedCategory, project.name)
    }

    fun togglePause(): Boolean {
        val paused = isPaused()
        return if (paused) {
            Files.deleteIfExists(PeonPaths.pausedPath)
            false
        } else {
            Files.createDirectories(PeonPaths.pausedPath.parent)
            Files.writeString(
                PeonPaths.pausedPath,
                "",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
            true
        }
    }

    fun isPaused(): Boolean = Files.exists(PeonPaths.pausedPath)

    fun cyclePack(): String? {
        val packs = listPacks()
        if (packs.isEmpty()) {
            return null
        }

        val config = loadConfig()
        val currentIndex = packs.indexOf(config.activePack)
        val next = if (currentIndex >= 0) {
            packs[(currentIndex + 1) % packs.size]
        } else {
            packs.first()
        }

        updateActivePack(next)
        sessionPackByProject.clear()
        return next
    }

    fun notifyInfo(project: Project?, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification("Peon Ping", content, NotificationType.INFORMATION)
            .notify(project)
    }

    companion object {
        private const val NOTIFICATION_GROUP = "Peon Ping"

        private val DEFAULT_CATEGORY_FLAGS = mapOf(
            CespCategory.SESSION_START.key to true,
            CespCategory.TASK_ACKNOWLEDGE.key to true,
            CespCategory.TASK_COMPLETE.key to true,
            CespCategory.TASK_ERROR.key to true,
            CespCategory.INPUT_REQUIRED.key to true,
            CespCategory.RESOURCE_LIMIT.key to true,
            CespCategory.USER_SPAM.key to true,
        )

        private val LEGACY_CATEGORY_MAP = mapOf(
            "greeting" to CespCategory.SESSION_START.key,
            "acknowledge" to CespCategory.TASK_ACKNOWLEDGE.key,
            "complete" to CespCategory.TASK_COMPLETE.key,
            "error" to CespCategory.TASK_ERROR.key,
            "permission" to CespCategory.INPUT_REQUIRED.key,
            "resource_limit" to CespCategory.RESOURCE_LIMIT.key,
            "annoyed" to CespCategory.USER_SPAM.key,
        )

        private val AI_HINTS = listOf(
            "ai assistant",
            "jetbrains ai",
            "jetbrains assistant",
            "assistant",
            "agent",
            "copilot",
            "codeium",
            "tabnine",
            "junie",
            "mellum",
            "cody",
            "claude",
            "gemini",
            "continue",
            "cursor",
        )

        private val PERMISSION_HINTS = listOf(
            "permission",
            "approve",
            "approval",
            "allow",
            "accept changes",
            "review and apply",
            "requires approval",
            "requires your input",
            "needs confirmation",
        )

        private val RESOURCE_HINTS = listOf(
            "rate limit",
            "quota",
            "resource limit",
            "usage limit reached",
            "too many requests",
            "token limit",
            "credits",
        )

        private val COMPLETE_HINTS = listOf(
            "complete",
            "completed",
            "done",
            "finished",
            "task finished",
            "ready for review",
            "generated",
            "resolved",
            "succeeded",
            "success",
        )

        private val ACK_HINTS = listOf(
            "working",
            "running",
            "processing",
            "thinking",
            "started",
            "planning",
            "analyzing",
            "implementing",
        )
    }

    private fun loadConfig(): PeonConfig {
        val default = PeonConfig(
            enabled = true,
            volume = 0.5,
            activePack = "peon",
            packRotation = emptyList(),
            categories = DEFAULT_CATEGORY_FLAGS,
        )

        val configPath = PeonPaths.configPath
        if (!Files.isRegularFile(configPath)) {
            return default
        }

        return runCatching {
            val json = parseObject(configPath)
            val categories = DEFAULT_CATEGORY_FLAGS.toMutableMap()
            json.getAsJsonObjectOrNull("categories")
                ?.entrySet()
                ?.forEach { entry ->
                    categories[entry.key] = entry.value.asBooleanOrNull() ?: categories.getOrDefault(entry.key, true)
                }

            PeonConfig(
                enabled = json.getBooleanOrDefault("enabled", true),
                volume = (json.getDoubleOrDefault("volume", 0.5)).coerceIn(0.0, 1.0),
                activePack = json.getStringOrDefault("active_pack", "peon"),
                packRotation = json.getArrayAsStrings("pack_rotation"),
                categories = categories,
            )
        }.getOrDefault(default)
    }

    private fun parseObject(path: Path): JsonObject {
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
            val parsed = JsonParser.parseReader(reader)
            if (parsed.isJsonObject) {
                return parsed.asJsonObject
            }
        }
        return JsonObject()
    }

    private fun listPacks(): List<String> {
        val packsDir = PeonPaths.packsDir
        if (!Files.isDirectory(packsDir)) {
            return emptyList()
        }
        return Files.list(packsDir).use { stream ->
            stream
                .filter { Files.isDirectory(it) }
                .filter { packDir ->
                    Files.isRegularFile(packDir.resolve("openpeon.json")) ||
                        Files.isRegularFile(packDir.resolve("manifest.json"))
                }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }
    }

    private fun resolvePackDir(config: PeonConfig, projectName: String): Path? {
        val packs = listPacks()
        if (packs.isEmpty()) {
            return null
        }

        val validRotation = config.packRotation.filter { packs.contains(it) }
        val selectedPack = if (validRotation.isNotEmpty()) {
            sessionPackByProject.computeIfAbsent(projectName) {
                validRotation[random.nextInt(validRotation.size)]
            }.takeIf { validRotation.contains(it) } ?: validRotation.first()
        } else if (packs.contains(config.activePack)) {
            config.activePack
        } else {
            packs.first()
        }

        return PeonPaths.packsDir.resolve(selectedPack)
    }

    private fun loadManifest(packDir: Path): PackManifest? {
        val openPeonManifest = packDir.resolve("openpeon.json")
        if (Files.isRegularFile(openPeonManifest)) {
            loadManifestFromFile(openPeonManifest, legacy = false)?.let { return it }
        }

        val legacyManifest = packDir.resolve("manifest.json")
        if (Files.isRegularFile(legacyManifest)) {
            loadManifestFromFile(legacyManifest, legacy = true)?.let { return it }
        }

        return null
    }

    private fun loadManifestFromFile(path: Path, legacy: Boolean): PackManifest? {
        return runCatching {
            val root = parseObject(path)
            val categoriesRoot = root.getAsJsonObjectOrNull("categories") ?: return@runCatching null
            val out = mutableMapOf<String, List<String>>()

            for ((rawName, rawCategory) in categoriesRoot.entrySet()) {
                val mappedName = if (legacy) {
                    LEGACY_CATEGORY_MAP[rawName] ?: rawName
                } else {
                    rawName
                }

                if (!DEFAULT_CATEGORY_FLAGS.containsKey(mappedName)) {
                    continue
                }

                val categoryObject = rawCategory.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val soundsArray = categoryObject.getAsJsonArray("sounds") ?: continue
                val files = soundsArray
                    .mapNotNull { soundEntry ->
                        val soundObj = soundEntry.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                        soundObj.get("file").asStringOrNull()?.trim()?.takeIf { it.isNotBlank() }
                    }
                    .distinct()

                if (files.isNotEmpty()) {
                    out[mappedName] = files
                }
            }

            if (out.isEmpty()) null else PackManifest(out)
        }.getOrNull()
    }

    private fun pickSoundPath(packDir: Path, manifest: PackManifest, category: CespCategory): Path? {
        val sounds = manifest.categories[category.key] ?: return null
        if (sounds.isEmpty()) {
            return null
        }

        val lastFile = lastPlayedByCategory[category.key]
        val candidates = if (lastFile != null && sounds.size > 1) {
            sounds.filter { it != lastFile }.ifEmpty { sounds }
        } else {
            sounds
        }

        val chosen = candidates[random.nextInt(candidates.size)]
        lastPlayedByCategory[category.key] = chosen

        val rawPath = if (chosen.contains('/')) {
            packDir.resolve(chosen)
        } else {
            packDir.resolve("sounds").resolve(chosen)
        }
        val normalizedPack = packDir.normalize()
        val normalizedSound = rawPath.normalize()
        if (!normalizedSound.startsWith(normalizedPack)) {
            return null
        }
        if (!Files.isRegularFile(normalizedSound)) {
            return null
        }
        return normalizedSound
    }

    private fun playSound(soundPath: Path, volume: Double) {
        ApplicationManager.getApplication().executeOnPooledThread {
            when {
                SystemInfo.isMac -> playWithCommand(
                    listOf("afplay", "-v", volume.toString(), soundPath.toString()),
                )

                isWsl() -> {
                    val windowsPath = soundPath.toString().replace("/", "\\\\")
                    val script = """
                        Add-Type -AssemblyName PresentationCore
                        ${'$'}p = New-Object System.Windows.Media.MediaPlayer
                        ${'$'}p.Open([Uri]::new('file:///$windowsPath'))
                        ${'$'}p.Volume = $volume
                        Start-Sleep -Milliseconds 200
                        ${'$'}p.Play()
                        Start-Sleep -Seconds 3
                        ${'$'}p.Close()
                    """.trimIndent()
                    playWithCommand(
                        listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script),
                    )
                }

                SystemInfo.isLinux -> {
                    val linuxCommands = listOf(
                        listOf("pw-play", "--volume", volume.toString(), soundPath.toString()),
                        listOf("paplay", "--volume=${(volume.coerceIn(0.0, 1.0) * 65536.0).roundToInt()}", soundPath.toString()),
                        listOf("ffplay", "-nodisp", "-autoexit", "-volume", (volume * 100.0).roundToInt().toString(), soundPath.toString()),
                        listOf("mpv", "--no-video", "--volume=${(volume * 100.0).roundToInt()}", soundPath.toString()),
                        listOf("play", "-v", volume.toString(), soundPath.toString()),
                        listOf("aplay", "-q", soundPath.toString()),
                    )
                    for (command in linuxCommands) {
                        if (isCommandAvailable(command.first())) {
                            playWithCommand(command)
                            return@executeOnPooledThread
                        }
                    }
                }
            }
        }
    }

    private fun playWithCommand(command: List<String>) {
        if (command.isEmpty() || !isCommandAvailable(command.first())) {
            return
        }
        runCatching {
            ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
        }
    }

    private fun isCommandAvailable(command: String): Boolean {
        return PathEnvironmentVariableUtil.findInPath(command) != null
    }

    private fun updateActivePack(packName: String) {
        val configPath = PeonPaths.configPath
        val root = if (Files.isRegularFile(configPath)) {
            runCatching { parseObject(configPath) }.getOrDefault(JsonObject())
        } else {
            JsonObject()
        }
        root.addProperty("active_pack", packName)
        Files.createDirectories(configPath.parent)
        Files.writeString(
            configPath,
            gson.toJson(root),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
    }

    private fun mapNotificationToCategory(notification: Notification): CespCategory? {
        val group = notification.groupId.orEmpty().lowercase(Locale.ROOT)
        val title = stripHtml(notification.title.orEmpty()).lowercase(Locale.ROOT)
        val content = stripHtml(notification.content.orEmpty()).lowercase(Locale.ROOT)
        val text = "$group $title $content"

        if (!looksLikeAgentSignal(group, title, content)) {
            return null
        }

        if (RESOURCE_HINTS.any { text.contains(it) }) {
            return CespCategory.RESOURCE_LIMIT
        }
        if (PERMISSION_HINTS.any { text.contains(it) }) {
            return CespCategory.INPUT_REQUIRED
        }
        if (notification.type == NotificationType.ERROR) {
            return CespCategory.TASK_ERROR
        }
        if (COMPLETE_HINTS.any { text.contains(it) }) {
            return CespCategory.TASK_COMPLETE
        }
        if (ACK_HINTS.any { text.contains(it) }) {
            return CespCategory.TASK_ACKNOWLEDGE
        }

        return null
    }

    private fun looksLikeAgentSignal(group: String, title: String, content: String): Boolean {
        val text = "$group $title $content"
        if (Regex("""\bai\b""").containsMatchIn(text)) {
            return true
        }
        return AI_HINTS.any { hint -> text.contains(hint) }
    }

    private fun stripHtml(raw: String): String {
        return raw.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ")
    }

    private fun isWsl(): Boolean {
        if (!SystemInfo.isLinux) {
            return false
        }
        val versionPath = Path.of("/proc/version")
        if (!Files.isRegularFile(versionPath)) {
            return false
        }
        return runCatching {
            Files.readString(versionPath, StandardCharsets.UTF_8).contains("microsoft", ignoreCase = true)
        }.getOrDefault(false)
    }
}

private fun JsonObject.getStringOrDefault(key: String, defaultValue: String): String {
    return get(key).asStringOrNull() ?: defaultValue
}

private fun JsonObject.getBooleanOrDefault(key: String, defaultValue: Boolean): Boolean {
    return get(key).asBooleanOrNull() ?: defaultValue
}

private fun JsonObject.getDoubleOrDefault(key: String, defaultValue: Double): Double {
    return get(key).asDoubleOrNull() ?: defaultValue
}

private fun JsonObject.getArrayAsStrings(key: String): List<String> {
    val array = get(key).takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
    return array
        .mapNotNull { it.asStringOrNull()?.trim()?.takeIf { value -> value.isNotBlank() } }
}

private fun JsonObject.getAsJsonObjectOrNull(key: String): JsonObject? {
    val value = get(key) ?: return null
    return value.takeIf { it.isJsonObject }?.asJsonObject
}

private fun JsonElement?.asStringOrNull(): String? {
    if (this == null || !isJsonPrimitive || !asJsonPrimitive.isString) {
        return null
    }
    return asString
}

private fun JsonElement?.asBooleanOrNull(): Boolean? {
    if (this == null || !isJsonPrimitive) {
        return null
    }
    return runCatching { asBoolean }.getOrNull()
}

private fun JsonElement?.asDoubleOrNull(): Double? {
    if (this == null || !isJsonPrimitive) {
        return null
    }
    return runCatching { asDouble }.getOrNull()
}

private fun JsonArray.mapNotNull(transform: (JsonElement) -> String?): List<String> {
    val out = ArrayList<String>(size())
    for (element in this) {
        val mapped = transform(element)
        if (mapped != null) {
            out.add(mapped)
        }
    }
    return out
}
