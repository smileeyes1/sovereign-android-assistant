package ps.hakim.phoneagent

import java.net.URI

/** Persist only a navigable public page address; never store credentials or OAuth parameters. */
object HakimBrowserPrivacy {
    private val sensitivePath = Regex("(?i)(token|secret|password|api.?key|session|pair|oauth|callback)")

    fun safeAddress(raw: String): String {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return ""
        val scheme = uri.scheme?.lowercase() ?: return ""
        if (scheme != "https" && scheme != "http") return ""
        val host = uri.host ?: return ""
        val addressHost = if (host.contains(':')) "[$host]" else host
        val origin = "$scheme://$addressHost" + if (uri.port in 1..65535) ":${uri.port}" else ""
        val path = uri.rawPath.orEmpty()
        if (path.length > 160 || path.split('/').any { it.length > 48 || sensitivePath.containsMatchIn(it) }) {
            return origin
        }
        return origin + path
    }
}
