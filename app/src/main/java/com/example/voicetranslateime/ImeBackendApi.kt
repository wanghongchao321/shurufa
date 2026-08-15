package com.example.voicetranslateime

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ImeBackendApi(
    private val baseUrl: String,
    private val sharedToken: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    suspend fun process(file: File, mode: InputMode): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("mode", mode.wireValue)
            .addFormDataPart(
                "audio",
                file.name,
                file.asRequestBody("audio/mp4".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(baseUrl.resolveEndpoint("v1/ime/process"))
            .header("X-IME-Token", sharedToken)
            .post(body)
            .build()

        return client.newCall(request).await().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val serverMessage = runCatching {
                    JSONObject(responseText).optString("error")
                }.getOrNull()
                throw IOException(
                    serverMessage?.takeIf { it.isNotBlank() }
                        ?: "Backend returned HTTP ${response.code}"
                )
            }

            JSONObject(responseText).getString("text").trim().also {
                if (it.isBlank()) throw IOException("Backend returned empty text")
            }
        }
    }

    private fun String.resolveEndpoint(path: String): String =
        trimEnd('/') + "/" + path.trimStart('/')

    private suspend fun Call.await(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            })
        }
}
