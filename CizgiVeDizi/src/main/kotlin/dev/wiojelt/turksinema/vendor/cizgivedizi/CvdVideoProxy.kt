package dev.wiojelt.turksinema.vendor.cizgivedizi

import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.concurrent.thread

internal data class CvdVideoAuth(
    val origin: String,
    val path: String,
    val cookies: String,
)

/**
 * The site's 307 gate binds the media URL to the remote TCP port and sets a
 * second cookie on the redirect. A URL resolved by the plugin cannot be played
 * by Media3's separate HTTP client. Keep both remote requests on one TLS socket
 * and expose the resulting byte stream to Media3 on device loopback.
 */
internal class CvdVideoProxy(
    private val authorize: suspend (playerUrl: String, page: String, label: String) -> CvdVideoAuth?,
) {
    private data class Entry(
        val playerUrl: String,
        val page: String,
        val label: String,
        val firstAuth: CvdVideoAuth,
        val createdAt: Long,
    )

    private data class RemoteResponse(val status: Int, val headers: Map<String, String>) {
        fun header(name: String): String? = headers[name.lowercase()]
    }

    private val entries = ConcurrentHashMap<String, Entry>()
    private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newFixedThreadPool(4) { task ->
        Thread(task, "CvdVideoProxy-Worker").apply { isDaemon = true }
    }

    init {
        thread(name = "CvdVideoProxy-Accept", isDaemon = true) {
            while (!server.isClosed) {
                val client = try { server.accept() } catch (_: Exception) { break }
                workers.execute { client.use(::handle) }
            }
        }
    }

    fun register(playerUrl: String, page: String, label: String, auth: CvdVideoAuth): String {
        val now = System.currentTimeMillis()
        if (entries.size > 256) {
            entries.forEach { (key, entry) ->
                if (now - entry.createdAt > 6 * 60 * 60 * 1000L) entries.remove(key, entry)
            }
        }
        val token = UUID.randomUUID().toString().replace("-", "")
        entries[token] = Entry(playerUrl, page, label, auth, now)
        return "http://127.0.0.1:${server.localPort}/$token/video.mp4"
    }

    private fun handle(client: Socket) {
        client.soTimeout = 30_000
        val output = BufferedOutputStream(client.getOutputStream())
        var responseStarted = false
        try {
            val reader = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val request = reader.readLine() ?: return
            val fields = request.split(' ')
            if (fields.size < 2 || fields[0] !in listOf("GET", "HEAD")) {
                sendError(output, 405, "Method Not Allowed")
                return
            }
            var range: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range:", true)) range = line.substringAfter(':').trim()
            }
            val token = fields[1].substringBefore('?').trim('/').substringBefore('/')
            val entry = entries[token]
            if (entry == null) {
                sendError(output, 404, "Not Found")
                return
            }
            val auth = if (System.currentTimeMillis() - entry.createdAt < 15_000) {
                entry.firstAuth
            } else {
                runBlocking { authorize(entry.playerUrl, entry.page, entry.label) }
            } ?: throw IllegalStateException("Video authorization expired")

            val host = URI(auth.origin).host ?: throw IllegalArgumentException("Missing video host")
            require(host == "video.cizgivedizi.net")
            val remote = (SSLSocketFactory.getDefault().createSocket(host, 443) as SSLSocket)
            remote.use { ssl ->
                ssl.soTimeout = 30_000
                ssl.startHandshake()
                val input = BufferedInputStream(ssl.inputStream)
                val remoteOutput = BufferedOutputStream(ssl.outputStream)

                writeRemoteRequest(remoteOutput, host, auth.path, auth.cookies, "bytes=0-0", true)
                val gate = readRemoteResponse(input)
                val location = gate.header("location")
                val gateCookie = gate.header("set-cookie")?.substringBefore(';')
                if (gate.status != 307 || location.isNullOrBlank() || gateCookie.isNullOrBlank() ||
                    !location.startsWith('/') || location.startsWith("//")) {
                    throw IllegalStateException("Video gate rejected: ${gate.status}")
                }
                val gateLength = gate.header("content-length")?.toLongOrNull() ?: 0L
                if (gateLength > 0) drain(input, gateLength)

                writeRemoteRequest(remoteOutput, host, location, "${auth.cookies}; $gateCookie", range, false)
                val media = readRemoteResponse(input)
                if (media.status !in listOf(200, 206)) {
                    throw IllegalStateException("Video stream rejected: ${media.status}")
                }
                val contentType = media.header("content-type") ?: "video/mp4"
                if (!contentType.startsWith("video/", true)) {
                    throw IllegalStateException("Unexpected media type: $contentType")
                }
                val status = if (media.status == 206) "206 Partial Content" else "200 OK"
                val responseHeaders = buildString {
                    append("HTTP/1.1 $status\r\n")
                    append("Content-Type: $contentType\r\n")
                    media.header("content-length")?.let { append("Content-Length: $it\r\n") }
                    media.header("content-range")?.let { append("Content-Range: $it\r\n") }
                    append("Accept-Ranges: bytes\r\n")
                    append("Cache-Control: no-store\r\n")
                    append("Connection: close\r\n\r\n")
                }
                output.write(responseHeaders.toByteArray(Charsets.ISO_8859_1))
                output.flush()
                responseStarted = true
                if (fields[0] == "GET") {
                    val length = media.header("content-length")?.toLongOrNull()
                    copyBody(input, output, length)
                }
            }
        } catch (_: Exception) {
            if (!responseStarted) runCatching { sendError(output, 502, "Bad Gateway") }
        } finally {
            runCatching { output.flush() }
        }
    }

    private fun writeRemoteRequest(
        output: OutputStream,
        host: String,
        path: String,
        cookies: String,
        range: String?,
        keepAlive: Boolean,
    ) {
        val request = buildString {
            append("GET $path HTTP/1.1\r\n")
            append("Host: $host\r\n")
            append("User-Agent: $USER_AGENT\r\n")
            append("Referer: https://cizgivedizi.net/\r\n")
            append("Origin: https://cizgivedizi.net\r\n")
            append("Cookie: $cookies\r\n")
            append("Accept: */*\r\n")
            append("Accept-Encoding: identity\r\n")
            append("Sec-Fetch-Site: same-site\r\n")
            append("Sec-Fetch-Mode: no-cors\r\n")
            append("Sec-Fetch-Dest: video\r\n")
            if (!range.isNullOrBlank()) append("Range: $range\r\n")
            append("Connection: ${if (keepAlive) "keep-alive" else "close"}\r\n\r\n")
        }
        output.write(request.toByteArray(Charsets.ISO_8859_1))
        output.flush()
    }

    private fun readRemoteResponse(input: InputStream): RemoteResponse {
        val statusLine = readLine(input)
        val status = statusLine.split(' ').getOrNull(1)?.toIntOrNull()
            ?: throw IllegalStateException("Invalid video response")
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readLine(input)
            if (line.isEmpty()) break
            val split = line.indexOf(':')
            if (split > 0) headers[line.substring(0, split).lowercase()] = line.substring(split + 1).trim()
        }
        return RemoteResponse(status, headers)
    }

    private fun readLine(input: InputStream): String {
        val line = StringBuilder()
        while (line.length < 16_384) {
            val next = input.read()
            if (next < 0) throw IllegalStateException("Video connection closed")
            if (next == '\n'.code) return line.toString().trimEnd('\r')
            line.append(next.toChar())
        }
        throw IllegalStateException("Video response line too long")
    }

    private fun drain(input: InputStream, bytes: Long) {
        var remaining = bytes
        val buffer = ByteArray(8_192)
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw IllegalStateException("Incomplete gate response")
            remaining -= count
        }
    }

    private fun copyBody(input: InputStream, output: OutputStream, length: Long?) {
        var remaining = length ?: Long.MAX_VALUE
        val buffer = ByteArray(64 * 1024)
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) break
            output.write(buffer, 0, count)
            remaining -= count
        }
        output.flush()
    }

    private fun sendError(output: OutputStream, code: Int, reason: String) {
        val body = "$code $reason\n".toByteArray(Charsets.UTF_8)
        output.write("HTTP/1.1 $code $reason\r\nContent-Type: text/plain\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
    }
}
