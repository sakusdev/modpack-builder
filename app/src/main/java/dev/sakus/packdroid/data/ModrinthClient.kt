package dev.sakus.packdroid.data

import android.content.Context
import android.net.Uri
import dev.sakus.packdroid.model.ModrinthFile
import dev.sakus.packdroid.model.ModrinthProject
import dev.sakus.packdroid.model.ModrinthVersion
import dev.sakus.packdroid.model.VersionDependency
import dev.sakus.packdroid.util.Hashing
import dev.sakus.packdroid.util.safeFileName
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ModrinthClient(private val context: Context) {
    companion object {
        private const val API = "https://api.modrinth.com/v2"
        private const val USER_AGENT = "sakusdev/PackDroid/0.1.0"
    }

    fun search(
        query: String,
        gameVersion: String,
        loader: String
    ): List<ModrinthProject> {
        val facets = JSONArray()
            .put(JSONArray().put("project_type:mod"))
            .put(JSONArray().put("versions:$gameVersion"))
            .apply {
                if (loader != "vanilla") {
                    put(JSONArray().put("categories:$loader"))
                }
            }

        val uri = Uri.parse("$API/search").buildUpon()
            .appendQueryParameter("query", query)
            .appendQueryParameter("facets", facets.toString())
            .appendQueryParameter("index", "relevance")
            .appendQueryParameter("limit", "30")
            .build()

        val root = JSONObject(get(uri.toString()))
        val hits = root.getJSONArray("hits")
        return buildList {
            for (i in 0 until hits.length()) {
                val item = hits.getJSONObject(i)
                add(
                    ModrinthProject(
                        projectId = item.getString("project_id"),
                        title = item.getString("title"),
                        description = item.optString("description"),
                        author = item.optString("author"),
                        downloads = item.optLong("downloads"),
                        iconUrl = item.optString("icon_url").takeIf { it.isNotBlank() && it != "null" },
                        clientSide = item.optString("client_side", "required"),
                        serverSide = item.optString("server_side", "required")
                    )
                )
            }
        }
    }

    fun getProject(projectId: String): ModrinthProject {
        val item = JSONObject(get("$API/project/${Uri.encode(projectId)}"))
        return ModrinthProject(
            projectId = item.getString("id"),
            title = item.getString("title"),
            description = item.optString("description"),
            author = item.optString("team"),
            downloads = item.optLong("downloads"),
            iconUrl = item.optString("icon_url").takeIf { it.isNotBlank() && it != "null" },
            clientSide = item.optString("client_side", "required"),
            serverSide = item.optString("server_side", "required")
        )
    }

    fun listVersions(
        projectId: String,
        gameVersion: String,
        loader: String
    ): List<ModrinthVersion> {
        val builder = Uri.parse("$API/project/${Uri.encode(projectId)}/version").buildUpon()
            .appendQueryParameter("game_versions", JSONArray().put(gameVersion).toString())
            .appendQueryParameter("include_changelog", "false")
        if (loader != "vanilla") {
            builder.appendQueryParameter("loaders", JSONArray().put(loader).toString())
        }
        return parseVersions(JSONArray(get(builder.build().toString())))
    }

    fun getVersion(versionId: String): ModrinthVersion {
        return parseVersion(JSONObject(get("$API/version/${Uri.encode(versionId)}")))
    }

    fun downloadToCache(
        projectId: String,
        versionId: String,
        file: ModrinthFile
    ): File {
        val folder = File(context.filesDir, "pack_files/$projectId/$versionId").apply { mkdirs() }
        val destination = File(folder, safeFileName(file.filename))
        if (destination.isFile && destination.length() == file.size) {
            val hashes = Hashing.file(destination)
            if (hashes.sha1.equals(file.sha1, true) && hashes.sha512.equals(file.sha512, true)) {
                return destination
            }
        }

        val temporary = File(folder, destination.name + ".part")
        temporary.delete()

        val connection = open(file.url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("MODファイルの取得に失敗しました: HTTP $code")
            }
            connection.inputStream.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }

        val hashes = Hashing.file(temporary)
        if (!hashes.sha1.equals(file.sha1, true) || !hashes.sha512.equals(file.sha512, true)) {
            temporary.delete()
            throw IllegalStateException("ダウンロードしたjarのハッシュが一致しません")
        }
        if (file.size > 0 && temporary.length() != file.size) {
            temporary.delete()
            throw IllegalStateException("ダウンロードしたjarのサイズが一致しません")
        }

        if (destination.exists()) destination.delete()
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        return destination
    }

    private fun parseVersions(array: JSONArray): List<ModrinthVersion> = buildList {
        for (i in 0 until array.length()) add(parseVersion(array.getJSONObject(i)))
    }

    private fun parseVersion(item: JSONObject): ModrinthVersion {
        val dependencies = item.optJSONArray("dependencies") ?: JSONArray()
        val files = item.getJSONArray("files")
        return ModrinthVersion(
            id = item.getString("id"),
            projectId = item.getString("project_id"),
            name = item.optString("name"),
            versionNumber = item.optString("version_number"),
            versionType = item.optString("version_type", "release"),
            dependencies = buildList {
                for (i in 0 until dependencies.length()) {
                    val dep = dependencies.getJSONObject(i)
                    add(
                        VersionDependency(
                            versionId = dep.optNullableString("version_id"),
                            projectId = dep.optNullableString("project_id"),
                            filename = dep.optNullableString("file_name"),
                            dependencyType = dep.optString("dependency_type")
                        )
                    )
                }
            },
            files = buildList {
                for (i in 0 until files.length()) {
                    val file = files.getJSONObject(i)
                    val hashes = file.getJSONObject("hashes")
                    add(
                        ModrinthFile(
                            filename = file.getString("filename"),
                            url = file.getString("url"),
                            sha1 = hashes.getString("sha1"),
                            sha512 = hashes.getString("sha512"),
                            size = file.getLong("size"),
                            primary = file.optBoolean("primary", false)
                        )
                    )
                }
            }
        )
    }

    private fun get(url: String): String {
        val connection = open(url)
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(body).optString("description").ifBlank {
                        JSONObject(body).optString("error")
                    }
                }.getOrDefault(body)
                throw IllegalStateException("Modrinth API: HTTP $code ${message.take(180)}")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() && it != "null" }
    }
}
