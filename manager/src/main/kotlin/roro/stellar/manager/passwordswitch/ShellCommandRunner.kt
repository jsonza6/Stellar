package roro.stellar.manager.passwordswitch

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs the fixed `/system/bin/settings` argv list directly.
 *
 * This class only ever executes inside the user service process, which Stellar starts with shell
 * (uid 2000) or root identity — so a plain [ProcessBuilder] already has the privilege it needs.
 * The `Stellar` client API must NOT be used here: its binder is delivered to the manager's own
 * process through the ContentProvider, so `Stellar.requireService()` throws
 * `IllegalStateException("Service not connected")` in this process.
 *
 * The command is passed as an argv list, never a shell string, so no user input can be
 * interpreted as shell syntax.
 */
class ShellCommandRunner : CommandRunner, AutoCloseable {
    private val readers = Executors.newSingleThreadExecutor()

    override fun run(arguments: List<String>): CommandResult {
        val process = ProcessBuilder(arguments).redirectErrorStream(true).start()
        val output = readers.submit<String> {
            process.inputStream.bufferedReader().use { stream ->
                val buffer = CharArray(4096)
                val text = StringBuilder()
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (text.length + count > 131072) throw SettingsFailure("系统返回内容过长。")
                    text.append(buffer, 0, count)
                }
                text.toString()
            }
        }
        return try {
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                throw SettingsFailure("系统命令超时，请刷新检查配置。")
            }
            CommandResult(process.exitValue(), output.get(2, TimeUnit.SECONDS).trim())
        } finally {
            process.destroyForcibly()
            output.cancel(true)
        }
    }

    override fun close() {
        readers.shutdownNow()
    }
}
