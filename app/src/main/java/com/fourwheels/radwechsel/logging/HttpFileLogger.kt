package com.fourwheels.radwechsel.logging

import okhttp3.Headers
import okhttp3.Interceptor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schreibt jeden Request und jede Response mit Zeitstempel und Headern über
 * [AppLog] ins Dateiprotokoll – damit sich im Nachhinein klären lässt, ob ein
 * Radwechsel wirklich beim Server angekommen ist.
 *
 * Bewusst *ohne* Bodys: die enthalten Passwörter und Tokens im Klartext. Aus dem
 * gleichen Grund sind die Auth-Header geschwärzt – das Protokoll liegt 30 Tage
 * lang auf einem Gerät, das durch viele Hände geht, und geht per Mail heraus.
 */
@Singleton
class HttpFileLogger @Inject constructor(
    private val appLog: AppLog
) {
    /** In die OkHttp-Kette einhängen (siehe NetworkModule). */
    val interceptor = Interceptor { chain ->
        val request = chain.request()
        val started = System.nanoTime()

        appLog.log(TAG, buildString {
            appendLine("--> ${request.method} ${request.url}")
            appendHeaders(request.headers)
        })

        val response = runCatching { chain.proceed(request) }
            .onFailure { e ->
                appLog.log(TAG, "<-- FEHLER ${request.method} ${request.url} " +
                    "(${elapsedMs(started)} ms): ${e.javaClass.simpleName}: ${e.message}")
            }
            .getOrThrow()

        appLog.log(TAG, buildString {
            appendLine("<-- ${response.code} ${response.message} ${request.method} ${request.url} " +
                "(${elapsedMs(started)} ms)")
            appendHeaders(response.headers)
        })
        response
    }

    private fun elapsedMs(startedNanos: Long) = (System.nanoTime() - startedNanos) / 1_000_000

    private fun StringBuilder.appendHeaders(headers: Headers) {
        headers.forEach { (name, value) ->
            val shown = if (name.lowercase() in REDACTED_HEADERS) "<geschwärzt>" else value
            appendLine("    $name: $shown")
        }
    }

    private companion object {
        const val TAG = "HTTP"
        val REDACTED_HEADERS = setOf("authorization", "proxy-authorization", "cookie", "set-cookie")
    }
}
