package roro.stellar.manager.passwordswitch

import roro.stellar.Stellar
import java.io.InputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Runs the fixed `/system/bin/settings` argv list through the Stellar service, which executes it
 * with shell (uid 2000) or root identity. The command never passes through a shell string, so no
 * user input can be interpreted as shell syntax.
 */
class StellarCommandRunner : CommandRunner {
    private val readers = Executors.newFixedThreadPool(2)

    override fun run(arguments: List<String>): CommandResult {
        val command: Array<String?> = Array(arguments.size) { index -> arguments[index] }
        val process = Stellar.newProcess(command, null, null)
        return try {
            val stdout = readers.submit<String> { drain(process.inputStream) }
            val stderr = readers.submit<String> { drain(process.errorStream) }
            val code = process.waitFor()
            CommandResult(code, (stdout.get(2, TimeUnit.SECONDS) + stderr.get(2, TimeUnit.SECONDS)).trim())
        } catch (failure: Exception) {
            // The reader tasks run on the executor, so their failure arrives wrapped.
            val cause = (failure as? ExecutionException)?.cause ?: failure
            if (cause is SettingsFailure) throw cause
            throw SettingsFailure("无法执行系统命令，请确认 Stellar 服务正在运行。", cause.toString())
        } finally {
            runCatching { process.destroy() }
        }
    }

    private fun drain(stream: InputStream): String = stream.bufferedReader().use { reader ->
        val buffer = CharArray(4096)
        val text = StringBuilder()
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            if (text.length + count > 131072) throw SettingsFailure("系统返回内容过长。")
            text.append(buffer, 0, count)
        }
        text.toString()
    }
}
