package com.synth.synthmusic.data.ai.customtools

import com.synth.synthmusic.domain.usecase.ai.ToolOutcome
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.UniqueTag
import org.mozilla.javascript.WrappedException

/**
 * Blocking-tolerant view of the app capabilities a custom script may call.
 * The runner executes on a worker thread and bridges calls with
 * [runBlocking]; implementations enforce the manifest's grants themselves
 * (e.g. every `http*` function requires the INTERNET capability).
 */
interface ScriptHostContext {

    /** GET returning the response body as text. */
    fun httpGetText(url: String, headers: Map<String, String>): String

    /** GET returning status, content type and body text. */
    fun httpGet(url: String, headers: Map<String, String>): HttpTextResponse

    /** POST with a raw body; returns status and body text. */
    fun httpPost(url: String, body: String, contentType: String?, headers: Map<String, String>): HttpTextResponse

    /**
     * Downloads [url] into the music library (same pipeline as the built-in
     * `download_audio`); returns a result map (savedAs, folder, songId…).
     */
    fun downloadAudio(
        url: String,
        folder: String?,
        fileName: String?,
        headers: Map<String, String>
    ): Map<String, Any?>

    /** Fuzzy library search; returns compact song maps. */
    fun librarySearchSongs(query: String, limit: Int): List<Map<String, Any?>>

    /** Decrypts a tool secret, or null when not configured. */
    fun secret(name: String): String?

    /** Script progress notes surfaced next to the tool result. */
    fun log(message: String)

    /** Text response summary used by the generic HTTP host functions. */
    data class HttpTextResponse(val status: Int, val contentType: String, val text: String)
}

/** Raised by host functions to abort the script with a clean message. */
class ToolHostException(message: String) : RuntimeException(message)

/**
 * Executes user-provided tool scripts inside a sandboxed Mozilla Rhino
 * interpreter:
 *
 * - no Java/class access ([ClassShutter] denies everything; `Packages` is
 *   unreachable) — the only capabilities are the functions on `ctx`;
 * - the standard scope is sealed; scripts define `execute`/`summarize` in a
 *   fresh child scope and cannot mutate built-ins;
 * - interpreted mode with an instruction observer enforces a wall-clock
 *   deadline against infinite loops (network calls inside `ctx` functions
 *   are bounded by the caller's overall timeout);
 * - results are plain JS values: a string, or `{content, isError}`.
 */
object ToolScriptRunner {

    /** Default wall-clock budget for a single script execution. */
    const val DEFAULT_TIMEOUT_MS = 20_000L

    /** Instruction-check frequency; combined with the deadline below. */
    private const val INSTRUCTION_THRESHOLD = 10_000

    /**
     * Runs [script] with [args] (JSON) and a host-backed `ctx` object.
     * Never throws: failures — syntax errors, runtime errors, host errors,
     * timeouts — come back as error [ToolOutcome]s.
     */
    fun run(
        script: String,
        args: JsonObject,
        host: ScriptHostContext,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        sourceName: String = "tool.js"
    ): ToolOutcome {
        val deadlineNanos = System.nanoTime() + timeoutMs * 1_000_000
        val factory = object : ContextFactory() {
            override fun observeInstructionCount(cx: Context, instructionCount: Int) {
                if (System.nanoTime() > deadlineNanos) {
                    throw Error("Script exceeded the ${timeoutMs / 1000}s execution limit")
                }
            }
        }
        val logs = StringBuilder()
        val cx = factory.enterContext()
        try {
            cx.optimizationLevel = -1                    // interpreter mode (Android)
            cx.languageVersion = Context.VERSION_ES6
            cx.instructionObserverThreshold = INSTRUCTION_THRESHOLD
            cx.setClassShutter(ClassShutter { false }) // no Java access from scripts

            val shared = cx.initStandardObjects(null, true)
            val scope = cx.newObject(shared)
            scope.prototype = shared

            val ctxObject = buildHostContext(cx, scope, host, logs)
            ScriptableObject.putProperty(scope, "ctx", ctxObject)

            cx.evaluateString(scope, script, sourceName, 1, null)

            val execute = scope.get("execute", scope)
            if (execute == UniqueTag.NOT_FOUND || execute !is Function) {
                return ToolOutcome("Script must define a global execute(args, ctx) function", isError = true)
            }
            val argsObject = jsonToJs(cx, scope, args)
            val result = execute.call(cx, scope, scope, arrayOf<Any?>(argsObject, ctxObject))
            return resultToOutcome(result, logs)
        } catch (e: ToolHostException) {
            return errorWithLogs(e.message ?: "Host error", logs)
        } catch (e: WrappedException) {
            val cause = e.cause
            if (cause is ToolHostException) return errorWithLogs(cause.message ?: "Host error", logs)
            return errorWithLogs("Script error: ${describe(e)}", logs)
        } catch (e: RhinoException) {
            return errorWithLogs("Script error: ${describe(e)}", logs)
        } catch (e: StackOverflowError) {
            return errorWithLogs("Script recursion is too deep", logs)
        } catch (e: Error) {
            return errorWithLogs(e.message ?: "Script execution error", logs)
        } finally {
            Context.exit()
        }
    }

    /**
     * Evaluates the optional `summarize(args)` global for approval cards;
     * any failure falls back to null (the dispatcher then uses the default
     * argument dump).
     */
    fun summarize(script: String, args: JsonObject, sourceName: String = "tool.js"): String? {
        val factory = ContextFactory()
        val cx = factory.enterContext()
        try {
            cx.optimizationLevel = -1
            cx.languageVersion = Context.VERSION_ES6
            cx.setClassShutter(ClassShutter { false })
            val shared = cx.initStandardObjects(null, true)
            val scope = cx.newObject(shared)
            scope.prototype = shared
            cx.evaluateString(scope, script, sourceName, 1, null)
            val fn = scope.get("summarize", scope)
            if (fn == UniqueTag.NOT_FOUND || fn !is Function) return null
            val result = fn.call(cx, scope, scope, arrayOf<Any?>(jsonToJs(cx, scope, args)))
            return Context.jsToJava(result, String::class.java) as? String
        } catch (_: Throwable) {
            return null
        } finally {
            Context.exit()
        }
    }

    // --- host context construction ---

    private fun buildHostContext(
        cx: Context,
        scope: Scriptable,
        host: ScriptHostContext,
        logs: StringBuilder
    ): Scriptable {
        val ctx = cx.newObject(scope)

        // Namespaces are plain objects holding functions; a getter function
        // would make `ctx.http.getText` resolve to undefined.
        val http = cx.newObject(scope)
        define(http, "getText", "getText") { args ->
            val url = stringArg(args, 0, "url")
            Context.javaToJS(host.httpGetText(url, headersArg(args, 1)), scope)
        }
        define(http, "getJson", "getJson") { args ->
            val text = host.httpGetText(stringArg(args, 0, "url"), headersArg(args, 1))
            val element = runCatching { Json.parseToJsonElement(text) }
                .getOrElse { throw ToolHostException("Response is not valid JSON: ${'$'}{it.message}") }
            jsonToJs(cx, scope, element)
        }
        define(http, "get", "get") { args ->
            val response = host.httpGet(stringArg(args, 0, "url"), headersArg(args, 1))
            mapToJs(
                cx, scope, linkedMapOf(
                    "status" to response.status,
                    "contentType" to response.contentType,
                    "text" to response.text
                )
            )
        }
        define(http, "post", "post") { args ->
            val url = stringArg(args, 0, "url")
            val body = stringArg(args, 1, "body")
            val contentType = optionalStringArg(args, 2)
            val response = host.httpPost(url, body, contentType, headersArg(args, 3))
            mapToJs(
                cx, scope, linkedMapOf(
                    "status" to response.status,
                    "contentType" to response.contentType,
                    "text" to response.text
                )
            )
        }
        ScriptableObject.putProperty(ctx, "http", http)

        val library = cx.newObject(scope)
        define(library, "searchSongs", "searchSongs") { args ->
            val query = stringArg(args, 0, "query")
            val limit = optionalNumberArg(args, 1)?.toInt() ?: 20
            val songs = host.librarySearchSongs(query, limit.coerceIn(1, 100))
            cx.newArray(scope, songs.map { mapToJs(cx, scope, it) }.toTypedArray<Any?>())
        }
        ScriptableObject.putProperty(ctx, "library", library)

        define(ctx, "downloadAudio", "downloadAudio") { args ->
            val url = stringArg(args, 0, "url")
            val options = args.getOrNull(1) as? Scriptable
            val folder = optionalProperty(options, "folder")
            val fileName = optionalProperty(options, "fileName")
            val headers = optionsHeaders(options)
            val result = host.downloadAudio(url, folder, fileName, headers)
            mapToJs(cx, scope, result)
        }
        define(ctx, "secret", "secret") { args ->
            val name = stringArg(args, 0, "name")
            host.secret(name)?.let { Context.javaToJS(it, scope) } ?: null
        }
        define(ctx, "log", "log") { args ->
            val message = jsToString(args.getOrNull(0)) ?: args.getOrNull(0)?.toString() ?: "undefined"
            logs.appendLine("[log] $message")
            host.log(message)
            Context.getUndefinedValue()
        }
        return ctx
    }

    private fun define(target: Scriptable, name: String, functionName: String, block: (Array<Any?>) -> Any?) {
        val function = object : BaseFunction() {
            override fun call(
                cx: Context,
                scope: Scriptable,
                thisObj: Scriptable,
                args: Array<Any?>
            ): Any? = block(args)

            override fun getFunctionName(): String = functionName
        }
        ScriptableObject.putProperty(target, name, function)
    }

    // --- argument helpers ---

    /** JS value → Kotlin string, or null when the value is not coercible. */
    private fun jsToString(value: Any?): String? {
        if (value == null || value == Context.getUndefinedValue() || value == UniqueTag.NOT_FOUND) return null
        return runCatching { Context.jsToJava(value, String::class.java) as? String }.getOrNull()
    }

    private fun stringArg(args: Array<Any?>, index: Int, name: String): String =
        jsToString(args.getOrNull(index))
            ?: throw ToolHostException("Argument '$name' must be a string")

    private fun optionalStringArg(args: Array<Any?>, index: Int): String? =
        jsToString(args.getOrNull(index))

    private fun optionalNumberArg(args: Array<Any?>, index: Int): Double? {
        val value = args.getOrNull(index) ?: return null
        if (value == Context.getUndefinedValue() || value == null) return null
        return runCatching { Context.toNumber(value) }.getOrNull()
    }

    private fun optionalProperty(options: Scriptable?, name: String): String? {
        if (options == null) return null
        val value = ScriptableObject.getProperty(options, name)
        return jsToString(value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun headersArg(args: Array<Any?>, index: Int): Map<String, String> {
        val value = args.getOrNull(index) ?: return emptyMap()
        if (value !is Scriptable) return emptyMap()
        val objectValue = value as? NativeObject ?: return emptyMap()
        val ids = objectValue.ids ?: return emptyMap()
        val result = linkedMapOf<String, String>()
        for (id in ids) {
            val key = id as? String ?: continue
            val entry = ScriptableObject.getProperty(objectValue, key)
            if (entry == UniqueTag.NOT_FOUND || entry == null) continue
            val text = jsToString(entry) ?: continue
            result[key] = text
        }
        return result
    }

    private fun optionsHeaders(options: Scriptable?): Map<String, String> {
        if (options == null) return emptyMap()
        val value = ScriptableObject.getProperty(options, "headers")
        if (value == UniqueTag.NOT_FOUND || value !is NativeObject) return emptyMap()
        return headersArg(arrayOf(value), 0)
    }

    // --- value conversion ---

    private fun jsonToJs(cx: Context, scope: Scriptable, element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> if (element.isString) element.content else {
            val content = element.content
            content.toLongOrNull() ?: content.toDoubleOrNull() ?: content.toBooleanStrictOrNull() ?: content
        }
        is JsonObject -> {
            val obj = cx.newObject(scope)
            element.forEach { (key, value) ->
                ScriptableObject.putProperty(obj, key, jsonToJs(cx, scope, value))
            }
            obj
        }
        is JsonArray -> cx.newArray(
            scope,
            element.map { jsonToJs(cx, scope, it) }.toTypedArray()
        )
    }

    private fun mapToJs(cx: Context, scope: Scriptable, map: Map<String, Any?>): Scriptable {
        val obj = cx.newObject(scope)
        map.forEach { (key, value) ->
            ScriptableObject.putProperty(obj, key, value ?: Context.getUndefinedValue())
        }
        return obj
    }

    private fun resultToOutcome(result: Any?, logs: StringBuilder): ToolOutcome {
        val content = when (result) {
            null, Context.getUndefinedValue() -> ""
            is String -> result
            is Scriptable -> {
                val contentProperty = ScriptableObject.getProperty(result, "content")
                when {
                    contentProperty == UniqueTag.NOT_FOUND || contentProperty == null ->
                        jsToString(result) ?: ""
                    else -> jsToString(contentProperty) ?: contentProperty.toString()
                }
            }
            else -> jsToString(result) ?: result.toString()
        }
        val isError = result is Scriptable && run {
            val flag = ScriptableObject.getProperty(result, "isError")
            flag != UniqueTag.NOT_FOUND && Context.toBoolean(flag)
        }
        val logText = logs.toString().trim()
        val merged = when {
            logText.isEmpty() -> content
            content.isBlank() -> logText
            else -> content + "\n" + logText
        }
        return ToolOutcome(merged, isError)
    }

    private fun errorWithLogs(message: String, logs: StringBuilder): ToolOutcome {
        val logText = logs.toString().trim()
        return ToolOutcome(if (logText.isEmpty()) message else "$message\n$logText", isError = true)
    }

    private fun describe(e: RhinoException): String {
        val base = (e.message?.lineSequence()?.firstOrNull() ?: "").ifBlank { e.javaClass.simpleName }
        val line = e.lineNumber()
        return if (line > 0) "$base (line $line)" else base
    }
}
