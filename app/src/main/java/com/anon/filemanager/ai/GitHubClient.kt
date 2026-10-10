package com.anon.filemanager.ai

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object GitHubClient {

    var pat: String = ""
    var user: String = ""

    private const val API = "https://api.github.com"

    data class Result(val ok: Boolean, val body: String, val status: Int)

    private fun request(
        method: String, path: String, body: String? = null
    ): Result {
        if (pat.isBlank()) return Result(false, "ERR: chưa cấu hình GitHub PAT", 0)
        val url = URL("$API$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 60000
            setRequestProperty("Authorization", "Bearer $pat")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "AnonFiles/1.3")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return Result(code in 200..299, text, code)
        } catch (e: Exception) {
            return Result(false, "ERR: ${e.message}", 0)
        }
    }

    // Lấy SHA của file (nếu tồn tại) để update
    private fun getFileSha(owner: String, repo: String, path: String, branch: String): String? {
        val p = "/repos/$owner/$repo/contents/${path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }}?ref=$branch"
        val r = request("GET", p)
        if (!r.ok) return null
        return try { JSONObject(r.body).optString("sha", "").takeIf { it.isNotBlank() } } catch (_: Exception) { null }
    }

    fun testAuth(): Result = request("GET", "/user")

    fun getUser(): String? {
        val r = request("GET", "/user")
        if (!r.ok) return null
        return try { JSONObject(r.body).optString("login") } catch (_: Exception) { null }
    }

    fun createRepo(name: String, description: String, isPrivate: Boolean): Result {
        val body = JSONObject().apply {
            put("name", name)
            put("description", description)
            put("private", isPrivate)
            put("auto_init", true)
        }.toString()
        return request("POST", "/user/repos", body)
    }

    fun pushFile(
        repo: String,
        path: String,
        content: String,
        commitMessage: String,
        branch: String = "main"
    ): Result {
        val owner = user.ifBlank { getUser() ?: return Result(false, "ERR: không xác định được user", 0) }
        val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val sha = getFileSha(owner, repo, path, branch)
        val body = JSONObject().apply {
            put("message", commitMessage)
            put("content", b64)
            put("branch", branch)
            if (sha != null) put("sha", sha)
        }.toString()
        val p = "/repos/$owner/$repo/contents/${path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }}"
        return request("PUT", p, body)
    }

    fun listFiles(repo: String, path: String = "", branch: String = "main"): Result {
        val owner = user.ifBlank { getUser() ?: return Result(false, "ERR: chưa xác định user", 0) }
        val p = "/repos/$owner/$repo/contents/${if (path.isBlank()) "" else path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }}?ref=$branch"
        return request("GET", p)
    }

    fun getFile(repo: String, path: String, branch: String = "main"): Result {
        val owner = user.ifBlank { getUser() ?: return Result(false, "ERR: chưa xác định user", 0) }
        val p = "/repos/$owner/$repo/contents/${path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }}?ref=$branch"
        val r = request("GET", p)
        if (!r.ok) return r
        return try {
            val json = JSONObject(r.body)
            val b64 = json.optString("content", "").replace("\n", "")
            val decoded = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            Result(true, decoded, r.status)
        } catch (e: Exception) { Result(false, "ERR: ${e.message}", r.status) }
    }

    fun listRepos(): Result = request("GET", "/user/repos?per_page=100&sort=updated")

    fun listRepoNames(): List<String> {
        val r = request("GET", "/user/repos?per_page=100&sort=updated")
        if (!r.ok) return emptyList()
        return try {
            val arr = JSONArray(r.body)
            (0 until arr.length()).map { arr.getJSONObject(it).getString("name") }
        } catch (_: Exception) { emptyList() }
    }
}
