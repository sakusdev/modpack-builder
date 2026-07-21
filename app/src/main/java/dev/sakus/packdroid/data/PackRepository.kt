package dev.sakus.packdroid.data

import android.content.Context
import dev.sakus.packdroid.model.ModSource
import dev.sakus.packdroid.model.PackMod
import dev.sakus.packdroid.model.PackProject
import org.json.JSONArray
import org.json.JSONObject

class PackRepository(context: Context) {
    private val preferences = context.getSharedPreferences("packdroid", Context.MODE_PRIVATE)

    fun load(): PackProject {
        val text = preferences.getString("current_project", null) ?: return PackProject()
        return runCatching { decode(JSONObject(text)) }.getOrElse { PackProject() }
    }

    fun save(project: PackProject) {
        preferences.edit().putString("current_project", encode(project).toString()).apply()
    }

    private fun encode(project: PackProject): JSONObject = JSONObject()
        .put("name", project.name)
        .put("versionId", project.versionId)
        .put("summary", project.summary)
        .put("minecraftVersion", project.minecraftVersion)
        .put("loader", project.loader)
        .put("loaderVersion", project.loaderVersion)
        .put("mods", JSONArray().apply {
            project.mods.forEach { mod ->
                put(JSONObject()
                    .put("title", mod.title)
                    .put("projectId", mod.projectId)
                    .put("versionId", mod.versionId)
                    .put("versionNumber", mod.versionNumber)
                    .put("filename", mod.filename)
                    .put("downloadUrl", mod.downloadUrl)
                    .put("sha1", mod.sha1)
                    .put("sha512", mod.sha512)
                    .put("fileSize", mod.fileSize)
                    .put("cachedPath", mod.cachedPath)
                    .put("clientSide", mod.clientSide)
                    .put("serverSide", mod.serverSide)
                    .put("source", mod.source.name)
                )
            }
        })

    private fun decode(root: JSONObject): PackProject {
        val mods = root.optJSONArray("mods") ?: JSONArray()
        return PackProject(
            name = root.optString("name", "My Modpack"),
            versionId = root.optString("versionId", "1.0.0"),
            summary = root.optString("summary"),
            minecraftVersion = root.optString("minecraftVersion", "1.21.1"),
            loader = root.optString("loader", "fabric"),
            loaderVersion = root.optString("loaderVersion", "0.16.14"),
            mods = buildList {
                for (i in 0 until mods.length()) {
                    val item = mods.getJSONObject(i)
                    add(
                        PackMod(
                            title = item.getString("title"),
                            projectId = item.optNullableString("projectId"),
                            versionId = item.optNullableString("versionId"),
                            versionNumber = item.optString("versionNumber"),
                            filename = item.getString("filename"),
                            downloadUrl = item.optNullableString("downloadUrl"),
                            sha1 = item.optString("sha1"),
                            sha512 = item.optString("sha512"),
                            fileSize = item.optLong("fileSize"),
                            cachedPath = item.optString("cachedPath"),
                            clientSide = item.optString("clientSide", "required"),
                            serverSide = item.optString("serverSide", "required"),
                            source = runCatching {
                                ModSource.valueOf(item.optString("source", ModSource.LOCAL.name))
                            }.getOrDefault(ModSource.LOCAL)
                        )
                    )
                }
            }
        )
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).takeIf { it.isNotBlank() && it != "null" }
    }
}
