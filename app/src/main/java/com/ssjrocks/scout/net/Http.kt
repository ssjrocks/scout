package com.ssjrocks.scout.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [resetSeconds]: when the server says the rate limit resets (Reddit's x-ratelimit-reset, or Retry-After). */
class HttpException(val code: Int, message: String, val resetSeconds: Int? = null) : IOException(message)

object Http {
    /** A normal mobile browser UA: several feeds (Reddit, Cloudflare sites) reject library UAs. */
    const val UA = "Mozilla/5.0 (Linux; Android 14; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/141.0.0.0 Mobile Safari/537.36"

    lateinit var client: OkHttpClient
        private set

    fun init(context: Context) {
        client = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http"), 50L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request()
                val b = req.newBuilder()
                if (req.header("User-Agent") == null) b.header("User-Agent", UA)
                if (req.header("Accept-Language") == null) b.header("Accept-Language", "en-AU,en;q=0.9")
                chain.proceed(b.build())
            }
            .build()
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) { cont.resume(response) }
        })
        cont.invokeOnCancellation { cancel() }
    }

    /** GET as text. [maxBytes] stops reading early (enough for a page's <head>). */
    suspend fun get(url: String, accept: String? = null, maxBytes: Long = 8L * 1024 * 1024, headers: Map<String, String> = emptyMap()): String {
        val b = Request.Builder().url(url)
        if (accept != null) b.header("Accept", accept)
        headers.forEach { (k, v) -> b.header(k, v) }
        val resp = client.newCall(b.build()).await()
        return withContext(Dispatchers.IO) {
            resp.use { r ->
                if (!r.isSuccessful) throw HttpException(r.code, "HTTP ${r.code}", resetOf(r))
                val body = r.body ?: return@use ""
                val source = body.source()
                source.request(maxBytes)
                val bytes = source.buffer.readByteArray(minOf(source.buffer.size, maxBytes))
                val charset = body.contentType()?.charset() ?: Charsets.UTF_8
                String(bytes, charset)
            }
        }
    }

    /** Final URL after redirects and the status code, without downloading much. */
    suspend fun probe(url: String): Pair<Int, String> {
        val req = Request.Builder().url(url).header("Range", "bytes=0-0").build()
        val noRedirect = client.newBuilder().followRedirects(false).build()
        val resp = noRedirect.newCall(req).await()
        return resp.use { it.code to (it.header("Location") ?: url) }
    }

    private fun resetOf(r: Response): Int? =
        (r.header("x-ratelimit-reset") ?: r.header("Retry-After"))?.trim()?.toDoubleOrNull()?.toInt()

    suspend fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
        val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        val b = Request.Builder().url(url).post(form)
        headers.forEach { (k, v) -> b.header(k, v) }
        val resp = client.newCall(b.build()).await()
        return withContext(Dispatchers.IO) {
            resp.use { r ->
                if (!r.isSuccessful) throw HttpException(r.code, "HTTP ${r.code}", resetOf(r))
                r.body?.string().orEmpty()
            }
        }
    }
}
