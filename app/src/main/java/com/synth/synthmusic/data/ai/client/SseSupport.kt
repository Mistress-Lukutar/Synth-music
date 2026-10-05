package com.synth.synthmusic.data.ai.client

/**
 * One parsed Server-Sent-Events frame.
 *
 * @property event the SSE `event:` field, if the stream provides one.
 * @property data the accumulated `data:` payload (multiple data lines joined
 * with `\n`).
 */
data class SseFrame(
    val event: String?,
    val data: String
)

/**
 * Converts a line sequence from an SSE response body into frames.
 *
 * Handles `data:` accumulation across multiple lines, `event:` typing,
 * comment lines starting with `:` and the terminating blank line per frame.
 */
fun Sequence<String>.asSseFrames(): Sequence<SseFrame> = sequence {
    var event: String? = null
    val data = StringBuilder()
    for (line in this@asSseFrames) {
        when {
            line.isEmpty() -> {
                if (data.isNotEmpty() || event != null) {
                    yield(SseFrame(event, data.toString()))
                    event = null
                    data.setLength(0)
                }
            }
            line.startsWith(":") -> Unit // comment / keep-alive
            line.startsWith("data:") -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").removePrefix(" "))
            }
            line.startsWith("event:") -> {
                event = line.removePrefix("event:").removePrefix(" ")
            }
            // "id:" and "retry:" fields are irrelevant here
        }
    }
    if (data.isNotEmpty() || event != null) {
        yield(SseFrame(event, data.toString()))
    }
}
