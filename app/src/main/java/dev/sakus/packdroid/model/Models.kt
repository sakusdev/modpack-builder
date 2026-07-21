package dev.sakus.packdroid.model

enum class ModSource {
    MODRINTH,
    LOCAL
}

enum class ExportFormat {
    FULL_ZIP,
    MANIFEST_PACK,
    MRPACK
}

data class PackMod(
    val title: String,
    val projectId: String? = null,
    val versionId: String? = null,
    val versionNumber: String,
    val filename: String,
    val downloadUrl: String? = null,
    val sha1: String,
    val sha512: String,
    val fileSize: Long,
    val cachedPath: String,
    val clientSide: String = "required",
    val serverSide: String = "required",
    val source: ModSource
)

data class PackProject(
    val name: String = "My Modpack",
    val versionId: String = "1.0.0",
    val summary: String = "",
    val minecraftVersion: String = "1.21.1",
    val loader: String = "fabric",
    val loaderVersion: String = "0.16.14",
    val mods: List<PackMod> = emptyList()
)

data class ModrinthProject(
    val projectId: String,
    val title: String,
    val description: String,
    val author: String,
    val downloads: Long,
    val iconUrl: String?,
    val clientSide: String,
    val serverSide: String
)

data class VersionDependency(
    val versionId: String?,
    val projectId: String?,
    val filename: String?,
    val dependencyType: String
)

data class ModrinthFile(
    val filename: String,
    val url: String,
    val sha1: String,
    val sha512: String,
    val size: Long,
    val primary: Boolean
)

data class ModrinthVersion(
    val id: String,
    val projectId: String,
    val name: String,
    val versionNumber: String,
    val versionType: String,
    val dependencies: List<VersionDependency>,
    val files: List<ModrinthFile>
) {
    fun primaryFile(): ModrinthFile? = files.firstOrNull { it.primary } ?: files.firstOrNull()
}
