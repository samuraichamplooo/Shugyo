package dev.shugyo.build

import org.json.JSONObject

/** Reads the AI's JSON, and saves what it can when the answer was cut off part-way. */
object JsonFix {
    fun parse(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        val s = raw.substring(start)
        val end = s.lastIndexOf('}')
        if (end > 0) {
            try {
                return JSONObject(s.substring(0, end + 1))
            } catch (e: Exception) {
                // cut off or broken: try closing it below
            }
        }
        return try {
            JSONObject(close(s))
        } catch (e: Exception) {
            null
        }
    }

    /** Cuts after the last complete object and closes whatever is still open. */
    fun close(s: String): String {
        val stack = ArrayDeque<Char>()
        var inString = false
        var escaped = false
        var lastClose = -1
        var openAtLastClose: List<Char> = emptyList()
        for (i in s.indices) {
            val c = s[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> stack.addLast(c)
                '}', ']' -> {
                    if (stack.isNotEmpty()) stack.removeLast()
                    if (c == '}') {
                        lastClose = i
                        openAtLastClose = stack.toList()
                    }
                }
            }
        }
        if (lastClose < 0) return s
        val sb = StringBuilder(s.substring(0, lastClose + 1))
        for (open in openAtLastClose.asReversed()) sb.append(if (open == '{') '}' else ']')
        return sb.toString()
    }
}
