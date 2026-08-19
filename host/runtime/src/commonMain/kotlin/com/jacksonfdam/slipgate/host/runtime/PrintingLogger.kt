package com.jacksonfdam.slipgate.host.runtime

/**
 * Writes a session's log to standard output, tagged with its level.
 *
 * Standard output rather than each platform's own logging API: it is where logcat, the Xcode console
 * and the browser console all already look, so one implementation is visible on all three and there
 * is no expect declaration to keep in step for the sake of a prefix.
 */
public class PrintingLogger(
    private val tag: String,
) : Logger {
    override fun log(
        level: LogLevel,
        message: String,
        cause: Throwable?,
    ) {
        println("$tag ${level.name.uppercase()}: $message")
        cause?.let { failure -> println("$tag ${level.name.uppercase()}: ${failure.stackTraceToString()}") }
    }
}
