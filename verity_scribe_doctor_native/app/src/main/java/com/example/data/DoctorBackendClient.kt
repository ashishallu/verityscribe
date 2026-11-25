package com.example.data

import android.os.SystemClock
import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Authenticated client for the existing VerityScribe API. No provider keys are sent to the app. */
class DoctorBackendClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(55, TimeUnit.SECONDS)
        .build()
) {
    private val apiBase = BuildConfig.APP_API_BASE_URL.trimEnd('/')
    private val supabaseUrl = BuildConfig.APP_SUPABASE_URL.trimEnd('/')
    private val publishableKey = BuildConfig.APP_SUPABASE_PUBLISHABLE_KEY
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun signIn(email: String, password: String): DoctorAuthTokens {
        require(supabaseUrl.startsWith("https://")) { "Secure Supabase URL is not configured for this app build." }
        require(publishableKey.isNotBlank()) { "Supabase publishable-key configuration is missing for this app build." }
        val request = Request.Builder()
            .url("$supabaseUrl/auth/v1/token?grant_type=password")
            .header("apikey", publishableKey)
            .header("Accept", "application/json")
            .post(JSONObject().put("email", email).put("password", password).toString().toRequestBody(jsonType))
            .build()
        return execute(request).toAuthTokens("Supabase sign-in")
    }

    fun refreshSession(refreshToken: String): DoctorAuthTokens {
        require(supabaseUrl.startsWith("https://")) { "Secure Supabase URL is not configured for this app build." }
        require(publishableKey.isNotBlank()) { "Supabase publishable-key configuration is missing for this app build." }
        val request = Request.Builder()
            .url("$supabaseUrl/auth/v1/token?grant_type=refresh_token")
            .header("apikey", publishableKey)
            .header("Accept", "application/json")
            .post(JSONObject().put("refresh_token", refreshToken).toString().toRequestBody(jsonType))
            .build()
        return execute(request).toAuthTokens("Supabase session refresh")
    }

    fun signOut(accessToken: String) {
        val request = Request.Builder()
            .url("$supabaseUrl/auth/v1/logout?scope=local")
            .header("apikey", publishableKey)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .post(JSONObject().toString().toRequestBody(jsonType))
            .build()
        execute(request)
    }

    fun get(path: String, accessToken: String): JSONObject = request("GET", path, accessToken)
    fun post(path: String, accessToken: String, payload: JSONObject): JSONObject =
        request("POST", path, accessToken, payload)
    fun patch(path: String, accessToken: String, payload: JSONObject): JSONObject =
        request("PATCH", path, accessToken, payload)

    private fun request(method: String, path: String, accessToken: String, payload: JSONObject? = null): JSONObject {
        require(apiBase.startsWith("https://")) { "Secure API URL is not configured for this app build." }
        val builder = Request.Builder()
            .url("$apiBase/${path.trimStart('/')}")
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
        val requestBody = payload?.toString()?.toRequestBody(jsonType)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: JSONObject().toString().toRequestBody(jsonType))
            "PATCH" -> builder.patch(requestBody ?: JSONObject().toString().toRequestBody(jsonType))
            else -> throw IllegalArgumentException("Unsupported HTTP method")
        }
        return execute(builder.build())
    }

    private fun execute(request: Request): JSONObject {
        val startedAt = SystemClock.elapsedRealtime()
        val operation = operationLabel(request)
        try {
            http.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                val body = bodyText.takeIf(String::isNotBlank)?.let {
                    runCatching { JSONObject(it) }.getOrNull()
                } ?: JSONObject()
                if (!response.isSuccessful) {
                    Log.w("DoctorBackendClient", "$operation failed status=${response.code} elapsed_ms=${SystemClock.elapsedRealtime() - startedAt}")
                    val detail = body.stringOrNull("detail")
                        ?: body.stringOrNull("msg")
                        ?: body.stringOrNull("message")
                        ?: "Request failed (${response.code}). Please try again."
                    throw DoctorApiException(response.code, detail)
                }
                Log.i("DoctorBackendClient", "$operation succeeded status=${response.code} elapsed_ms=${SystemClock.elapsedRealtime() - startedAt}")
                return body
            }
        } catch (error: DoctorApiException) {
            throw error
        } catch (error: IOException) {
            val supabaseHost = supabaseUrl.removePrefix("https://").substringBefore('/')
            val service = if (request.url.host.equals(supabaseHost, ignoreCase = true)) {
                "Supabase sign-in"
            } else {
                "VerityScribe API"
            }
            // Log only the failing service and exception; never log request headers or bodies.
            Log.e(
                "DoctorBackendClient",
                "$operation network request failed service=$service error=${error.javaClass.simpleName} elapsed_ms=${SystemClock.elapsedRealtime() - startedAt}",
                error
            )
            throw IOException(
                "Could not reach $service (${error.javaClass.simpleName}). Check the device connection and retry.",
                error
            )
        }
    }

    /** A fixed route label avoids logging IDs, query data, tokens, or patient information. */
    private fun operationLabel(request: Request): String {
        val path = request.url.encodedPath
        val host = request.url.host
        val supabaseHost = supabaseUrl.removePrefix("https://").substringBefore('/')
        if (host.equals(supabaseHost, ignoreCase = true)) {
            return when {
                path.endsWith("/token") && request.url.queryParameter("grant_type") == "password" -> "Supabase password sign-in"
                path.endsWith("/token") -> "Supabase session refresh"
                path.endsWith("/logout") -> "Supabase sign-out"
                else -> "Supabase auth request"
            }
        }
        return when {
            path.endsWith("/me/doctor") -> "doctor verification"
            path.endsWith("/me") -> "profile verification"
            path.endsWith("/me/appointments") -> "appointments load"
            path.endsWith("/me/consultations") -> "consultations load"
            path.endsWith("/me/reports") -> "reports load"
            path.startsWith("/api/v1/notifications") -> "notifications load"
            path.startsWith("/api/v1/patients/") -> "patient details load"
            path.contains("/ai-consultations/doctor/appointments/") -> "AI review load"
            else -> "VerityScribe API request"
        }
    }
}

class DoctorApiException(val statusCode: Int, message: String) : IOException(message)

data class DoctorAuthTokens(val accessToken: String, val refreshToken: String)

private fun JSONObject.toAuthTokens(operation: String): DoctorAuthTokens = DoctorAuthTokens(
    accessToken = stringOrNull("access_token")
        ?: throw IOException("$operation did not return an access token."),
    refreshToken = stringOrNull("refresh_token")
        ?: throw IOException("$operation did not return a refresh token.")
)

fun JSONObject.dataObject(): JSONObject = optJSONObject("data") ?: JSONObject()
fun JSONObject.dataArray(): JSONArray = optJSONArray("data") ?: JSONArray()
fun JSONObject.stringOrNull(key: String): String? {
    val value = opt(key) ?: return null
    if (value == JSONObject.NULL) return null
    return value.toString().takeIf(String::isNotBlank)
}
fun JSONObject.objectOrEmpty(key: String): JSONObject = optJSONObject(key) ?: JSONObject()
fun JSONObject.arrayOrEmpty(key: String): JSONArray = optJSONArray(key) ?: JSONArray()
fun JSONObject.hasValue(key: String): Boolean = opt(key)?.let { it != JSONObject.NULL } == true
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
