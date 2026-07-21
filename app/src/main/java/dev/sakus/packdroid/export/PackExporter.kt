package dev.sakus.packdroid.export

import dev.sakus.packdroid.model.ExportFormat
import dev.sakus.packdroid.model.ModSource
import dev.sakus.packdroid.model.PackMod
import dev.sakus.packdroid.model.PackProject
import dev.sakus.packdroid.util.safeFileName
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackExporter {
    fun export(project: PackProject, format: ExportFormat, output: OutputStream) {
        require(project.name.isNotBlank()) { "MODPACK名を入力してください" }
        require(project.versionId.isNotBlank()) { "MODPACKバージョンを入力してください" }
        require(project.minecraftVersion.isNotBlank()) { "Minecraftバージョンを入力してください" }

        ZipOutputStream(output.buffered()).use { zip ->
            when (format) {
                ExportFormat.FULL_ZIP -> exportFullZip(project, zip)
                ExportFormat.MANIFEST_PACK -> exportManifestPack(project, zip)
                ExportFormat.MRPACK -> exportMrpack(project, zip)
            }
        }
    }

    private fun exportFullZip(project: PackProject, zip: ZipOutputStream) {
        val paths = uniqueModPaths(project.mods)
        writeJson(zip, "packdroid.manifest.json", customManifest(project, paths))
        project.mods.forEachIndexed { index, mod ->
            val file = requireCachedFile(mod)
            writeFile(zip, paths[index], file)
        }
    }

    private fun exportManifestPack(project: PackProject, zip: ZipOutputStream) {
        val paths = uniqueModPaths(project.mods)
        writeJson(zip, "packdroid.manifest.json", customManifest(project, paths))
        writeText(
            zip,
            "README.txt",
            """
            PackDroid manifest pack
            MOD本体は含まれていません。
            provider=modrinth の項目は downloadUrl とハッシュを使って取得できます。
            source=local の項目は元のjarを別途用意してください。
            """.trimIndent()
        )
    }

    private fun exportMrpack(project: PackProject, zip: ZipOutputStream) {
        val files = JSONArray()
        val localPaths = uniqueModPaths(project.mods)

        project.mods.forEachIndexed { index, mod ->
            val downloadable = mod.source == ModSource.MODRINTH &&
                !mod.downloadUrl.isNullOrBlank() &&
                mod.sha1.isNotBlank() &&
                mod.sha512.isNotBlank()

            if (downloadable) {
                files.put(
                    JSONObject()
                        .put("path", localPaths[index])
                        .put("hashes", JSONObject()
                            .put("sha1", mod.sha1)
                            .put("sha512", mod.sha512)
                        )
                        .put("env", JSONObject()
                            .put("client", normalizeSide(mod.clientSide))
                            .put("server", normalizeSide(mod.serverSide))
                        )
                        .put("downloads", JSONArray().put(mod.downloadUrl))
                        .put("fileSize", mod.fileSize)
                )
            } else {
                val file = requireCachedFile(mod)
                writeFile(zip, "overrides/${localPaths[index]}", file)
            }
        }

        val index = JSONObject()
            .put("formatVersion", 1)
            .put("game", "minecraft")
            .put("versionId", project.versionId)
            .put("name", project.name)
            .apply {
                if (project.summary.isNotBlank()) put("summary", project.summary)
            }
            .put("files", files)
            .put("dependencies", dependencies(project))

        writeJson(zip, "modrinth.index.json", index)
    }

    private fun customManifest(project: PackProject, paths: List<String>): JSONObject =
        JSONObject()
            .put("formatVersion", 1)
            .put("generator", "PackDroid 0.1.0")
            .put("game", "minecraft")
            .put("name", project.name)
            .put("versionId", project.versionId)
            .put("summary", project.summary)
            .put("dependencies", dependencies(project))
            .put("mods", JSONArray().apply {
                project.mods.forEachIndexed { index, mod ->
                    put(
                        JSONObject()
                            .put("path", paths[index])
                            .put("title", mod.title)
                            .put("source", mod.source.name.lowercase())
                            .put("provider", if (mod.source == ModSource.MODRINTH) "modrinth" else "local")
                            .put("projectId", mod.projectId)
                            .put("versionId", mod.versionId)
                            .put("versionNumber", mod.versionNumber)
                            .put("filename", mod.filename)
                            .put("downloadUrl", mod.downloadUrl)
                            .put("fileSize", mod.fileSize)
                            .put("hashes", JSONObject()
                                .put("sha1", mod.sha1)
                                .put("sha512", mod.sha512)
                            )
                            .put("env", JSONObject()
                                .put("client", normalizeSide(mod.clientSide))
                                .put("server", normalizeSide(mod.serverSide))
                            )
                    )
                }
            })

    private fun dependencies(project: PackProject): JSONObject {
        val result = JSONObject().put("minecraft", project.minecraftVersion)
        if (project.loader != "vanilla" && project.loaderVersion.isNotBlank()) {
            val key = when (project.loader) {
                "fabric" -> "fabric-loader"
                "quilt" -> "quilt-loader"
                "neoforge" -> "neoforge"
                "forge" -> "forge"
                else -> project.loader
            }
            result.put(key, project.loaderVersion)
        }
        return result
    }

    private fun uniqueModPaths(mods: List<PackMod>): List<String> {
        val used = mutableSetOf<String>()
        return mods.map { mod ->
            val base = safeFileName(mod.filename).let {
                if (it.endsWith(".jar", ignoreCase = true)) it else "$it.jar"
            }
            var candidate = "mods/$base"
            var counter = 2
            while (!used.add(candidate.lowercase())) {
                val stem = base.removeSuffix(".jar")
                candidate = "mods/${stem}_$counter.jar"
                counter++
            }
            candidate
        }
    }

    private fun normalizeSide(value: String): String = when (value) {
        "required", "optional", "unsupported" -> value
        else -> "required"
    }

    private fun requireCachedFile(mod: PackMod): File {
        val file = File(mod.cachedPath)
        require(file.isFile) { "${mod.title} のjarキャッシュが見つかりません" }
        return file
    }

    private fun writeJson(zip: ZipOutputStream, path: String, json: JSONObject) {
        writeText(zip, path, json.toString(2))
    }

    private fun writeText(zip: ZipOutputStream, path: String, text: String) {
        writeBytes(zip, path, text.toByteArray(Charsets.UTF_8))
    }

    private fun writeFile(zip: ZipOutputStream, path: String, file: File) {
        validatePath(path)
        zip.putNextEntry(ZipEntry(path))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun writeBytes(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        validatePath(path)
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun validatePath(path: String) {
        require(!path.contains("..")) { "危険なZIPパスです" }
        require(!path.startsWith("/") && !path.startsWith("\\")) { "危険なZIPパスです" }
        require(!Regex("""^[A-Za-z]:[\\/]""").containsMatchIn(path)) { "危険なZIPパスです" }
    }
}
