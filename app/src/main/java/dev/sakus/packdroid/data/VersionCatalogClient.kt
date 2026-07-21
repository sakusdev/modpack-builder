package dev.sakus.packdroid.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class VersionCatalogClient {
    companion object {
        private const val MODRINTH_API = "https://api.modrinth.com/v2"
        private const val FABRIC_META = "https://meta.fabricmc.net/v2"
        private const val QUILT_META = "https://meta.quiltmc.org/v3"
        private const val FORGE_METADATA =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml"
        private const val NEOFORGE_METADATA =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml"
        private const val USER_AGENT = "sakusdev/PackDroid/0.2.0"
    }

    fun listMinecraftVersions(includeSnapshots: Boolean = false): List<String> {
        val versions = JSONArray(get("$MODRINTH_API/tag/game_version", "Modrinth"))
        return buildList {
            for (index in 0 until versions.length()) {
                val item = versions.getJSONObject(index)
                val type = item.optString("version_type")
                if (type == "release" || includeSnapshots) {
                    add(item.getString("version"))
                }
            }
        }.distinct()
    }

    fun listLoaderVersions(loader: String, minecraftVersion: String): List<String> = when (loader) {
        "fabric" -> listFabricVersions(minecraftVersion)
        "quilt" -> listQuiltVersions(minecraftVersion)
        "forge" -> listForgeVersions(minecraftVersion)
        "neoforge" -> listNeoForgeVersions(minecraftVersion)
        else -> emptyList()
    }

    private fun listFabricVersions(minecraftVersion: String): List<String> {
        val encoded = Uri.encode(minecraftVersion)
        val entries = JSONArray(get("$FABRIC_META/versions/loader/$encoded", "Fabric Meta"))
        return buildList {
            for (index in 0 until entries.length()) {
                val loader = entries.getJSONObject(index).getJSONObject("loader")
                add(loader.getString("version"))
            }
        }.distinct()
    }

    private fun listQuiltVersions(minecraftVersion: String): List<String> {
        val encoded = Uri.encode(minecraftVersion)
        val entries = JSONArray(get("$QUILT_META/versions/loader/$encoded", "Quilt Meta"))
        return buildList {
            for (index in 0 until entries.length()) {
                val loader = entries.getJSONObject(index).getJSONObject("loader")
                add(loader.getString("version"))
            }
        }.distinct()
    }

    private fun listForgeVersions(minecraftVersion: String): List<String> {
        val prefix = "$minecraftVersion-"
        return parseMavenVersions(get(FORGE_METADATA, "Forge Maven"))
            .asReversed()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .distinct()
    }

    private fun listNeoForgeVersions(minecraftVersion: String): List<String> {
        val prefix = neoForgePrefix(minecraftVersion) ?: return emptyList()
        return parseMavenVersions(get(NEOFORGE_METADATA, "NeoForge Maven"))
            .asReversed()
            .filter { it.startsWith(prefix) }
            .distinct()
    }

    private fun neoForgePrefix(minecraftVersion: String): String? {
        val parts = minecraftVersion.split('.')
        if (parts.firstOrNull() != "1" || parts.size < 2) return null
        val major = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return "$major.$patch."
    }

    private fun parseMavenVersions(xml: String): List<String> =
        Regex("<version>([^<]+)</version>")
            .findAll(xml)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()

    private fun get(url: String, service: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json, application/xml, text/xml, text/plain")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching {
                    JSONObject(body).optString("description").ifBlank {
                        JSONObject(body).optString("error")
                    }
                }.getOrDefault(body).take(160)
                throw IllegalStateException("$service: HTTP $code $detail")
            }
            body
        } finally {
            connection.disconnect()
        }
    }
}
