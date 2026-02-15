package com.llamatik.app.platform.tts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class JvmTtsEngine : TtsEngine {

    private sealed class Backend {
        data class Mac(val cmd: String = "say") : Backend()
        data class Windows(val cmd: String = "powershell") : Backend()
        data class Linux(val cmd: String) : Backend()
    }

    private val backend: Backend? = detectBackend()

    private val currentProcess = AtomicReference<Process?>(null)

    override val isAvailable: Boolean
        get() = backend != null

    override suspend fun speak(text: String, interrupt: Boolean) {
        val b = backend ?: return
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        if (interrupt) stop()

        // Run on background thread. This will suspend until speech completes.
        withContext(Dispatchers.IO) {
            val process = try {
                val pb = when (b) {
                    is Backend.Mac -> ProcessBuilder(b.cmd, trimmed)
                    is Backend.Linux -> ProcessBuilder(b.cmd, trimmed)
                    is Backend.Windows -> {
                        // Uses built-in Windows TTS (SAPI via System.Speech)
                        val safe = escapeForPowerShellSingleQuoted(trimmed)
                        val command =
                            "Add-Type -AssemblyName System.Speech; " +
                                    "\$speak = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                                    "\$speak.Speak('$safe');"

                        ProcessBuilder(b.cmd, "-NoProfile", "-Command", command)
                    }
                }

                // Keep stderr/stdout quiet and avoid deadlocks
                pb.redirectErrorStream(true)
                pb.start()
            } catch (_: Throwable) {
                return@withContext
            }

            // Register process so stop() can kill it
            if (!currentProcess.compareAndSet(null, process)) {
                // If something else started while we were launching, kill ours
                runCatching { process.destroyForcibly() }
                return@withContext
            }

            try {
                // Wait until it finishes speaking
                process.waitFor()
            } finally {
                currentProcess.compareAndSet(process, null)
            }
        }
    }

    override fun stop() {
        val p = currentProcess.getAndSet(null) ?: return
        runCatching { p.destroy() }
        runCatching { p.destroyForcibly() }
    }

    private fun detectBackend(): Backend? {
        val os = (System.getProperty("os.name") ?: "").lowercase()
        return when {
            os.contains("mac") || os.contains("darwin") -> {
                if (commandExists("say")) Backend.Mac() else null
            }
            os.contains("win") -> {
                // PowerShell is present on supported Windows; try to be safe anyway.
                if (commandExists("powershell") || commandExists("pwsh")) {
                    // Prefer pwsh if present (PowerShell 7)
                    if (commandExists("pwsh")) Backend.Windows(cmd = "pwsh") else Backend.Windows(cmd = "powershell")
                } else null
            }
            os.contains("nux") || os.contains("nix") || os.contains("linux") -> {
                when {
                    commandExists("spd-say") -> Backend.Linux("spd-say")
                    commandExists("espeak-ng") -> Backend.Linux("espeak-ng")
                    commandExists("espeak") -> Backend.Linux("espeak")
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun commandExists(cmd: String): Boolean {
        // Try PATH lookup first
        val path = System.getenv("PATH") ?: return false
        val parts = path.split(File.pathSeparatorChar)

        // On Windows we need to consider .exe/.cmd/.bat, but we also call it by name via ProcessBuilder.
        val isWindows = (System.getProperty("os.name") ?: "").lowercase().contains("win")
        val candidates = if (isWindows) {
            listOf(cmd, "$cmd.exe", "$cmd.cmd", "$cmd.bat")
        } else {
            listOf(cmd)
        }

        for (dir in parts) {
            for (c in candidates) {
                val f = File(dir, c)
                if (f.exists() && f.isFile && (isWindows || f.canExecute())) return true
            }
        }

        // Fallback: try running `which` (unix) or `where` (windows) if available
        return runCatching {
            val probe = if (isWindows) ProcessBuilder("where", cmd) else ProcessBuilder("which", cmd)
            probe.redirectErrorStream(true)
            val p = probe.start()
            val code = p.waitFor()
            code == 0
        }.getOrDefault(false)
    }

    private fun escapeForPowerShellSingleQuoted(text: String): String {
        // In PowerShell single-quoted strings escape ' as ''
        return text.replace("'", "''")
    }
}
