package dev.sakus.packdroid.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sakus.packdroid.data.ModrinthClient
import dev.sakus.packdroid.data.PackRepository
import dev.sakus.packdroid.data.VersionCatalogClient
import dev.sakus.packdroid.export.PackExporter
import dev.sakus.packdroid.model.ExportFormat
import dev.sakus.packdroid.model.ModSource
import dev.sakus.packdroid.model.ModrinthProject
import dev.sakus.packdroid.model.ModrinthVersion
import dev.sakus.packdroid.model.PackMod
import dev.sakus.packdroid.model.PackProject
import dev.sakus.packdroid.util.Hashing
import dev.sakus.packdroid.util.safeFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class PackUiState(
    val project: PackProject = PackProject(),
    val selectedTab: Int = 0,
    val query: String = "",
    val searchResults: List<ModrinthProject> = emptyList(),
    val minecraftVersions: List<String> = emptyList(),
    val loaderVersions: List<String> = emptyList(),
    val versionCatalogBusy: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null
)

class PackViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PackRepository(application)
    private val modrinth = ModrinthClient(application)
    private val versionCatalog = VersionCatalogClient()
    private val exporter = PackExporter()

    private val _state = MutableStateFlow(PackUiState(project = repository.load()))
    val state: StateFlow<PackUiState> = _state.asStateFlow()

    init {
        refreshVersionCatalog(showMessage = false)
    }

    fun selectTab(tab: Int) = _state.update { it.copy(selectedTab = tab) }
    fun setQuery(value: String) = _state.update { it.copy(query = value) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun updateProject(transform: (PackProject) -> PackProject) {
        val updated = transform(_state.value.project)
        repository.save(updated)
        _state.update { it.copy(project = updated) }
    }

    fun setMinecraftVersion(value: String) {
        updateProject { it.copy(minecraftVersion = value) }
        _state.update { it.copy(searchResults = emptyList()) }
    }

    fun selectMinecraftVersion(value: String) {
        val hasMods = _state.value.project.mods.isNotEmpty()
        setMinecraftVersion(value)
        if (hasMods) {
            _state.update {
                it.copy(message = "Minecraftバージョンを変更しました。追加済みMODの互換性を確認してください")
            }
        }
        refreshLoaderVersions(showMessage = false)
    }

    fun selectLoader(loader: String) {
        updateProject { it.copy(loader = loader, loaderVersion = "") }
        _state.update { it.copy(searchResults = emptyList(), loaderVersions = emptyList()) }
        refreshLoaderVersions(showMessage = false)
    }

    fun setLoaderVersion(value: String) {
        updateProject { it.copy(loaderVersion = value) }
    }

    fun selectLoaderVersion(value: String) {
        setLoaderVersion(value)
    }

    fun refreshVersionCatalog(showMessage: Boolean = true) {
        val snapshot = _state.value.project
        viewModelScope.launch {
            setVersionCatalogBusy(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val minecraftVersions = versionCatalog.listMinecraftVersions()
                    val loaderVersions = versionCatalog.listLoaderVersions(
                        snapshot.loader,
                        snapshot.minecraftVersion
                    )
                    minecraftVersions to loaderVersions
                }
            }.onSuccess { (minecraftVersions, loaderVersions) ->
                applyVersionCatalog(minecraftVersions, loaderVersions)
                if (showMessage) {
                    _state.update { it.copy(message = "バージョン候補を更新しました") }
                }
            }.onFailure(::showError)
            setVersionCatalogBusy(false)
        }
    }

    fun refreshLoaderVersions(showMessage: Boolean = true) {
        val snapshot = _state.value.project
        if (snapshot.loader == "vanilla") {
            _state.update { it.copy(loaderVersions = emptyList()) }
            return
        }
        viewModelScope.launch {
            setVersionCatalogBusy(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    versionCatalog.listLoaderVersions(snapshot.loader, snapshot.minecraftVersion)
                }
            }.onSuccess { versions ->
                applyLoaderVersions(versions)
                if (showMessage) {
                    _state.update {
                        it.copy(message = if (versions.isEmpty()) "候補がありません。手入力してください" else "${versions.size}件の候補を取得しました")
                    }
                }
            }.onFailure(::showError)
            setVersionCatalogBusy(false)
        }
    }

    fun search() {
        val snapshot = _state.value
        if (snapshot.query.isBlank()) {
            _state.update { it.copy(message = "検索語を入力してください") }
            return
        }
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    modrinth.search(
                        query = snapshot.query.trim(),
                        gameVersion = snapshot.project.minecraftVersion,
                        loader = snapshot.project.loader
                    )
                }
            }.onSuccess { results ->
                _state.update {
                    it.copy(searchResults = results, message = "${results.size}件見つかりました")
                }
            }.onFailure(::showError)
            setBusy(false)
        }
    }

    fun addProject(project: ModrinthProject) {
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val current = _state.value.project
                    val resolved = mutableListOf<PackMod>()
                    val visited = current.mods.mapNotNull { it.projectId }.toMutableSet()
                    resolveProject(project, current, visited, resolved, preferredVersion = null)
                    resolved
                }
            }.onSuccess { added ->
                if (added.isEmpty()) {
                    _state.update { it.copy(message = "このMODはすでに追加されています") }
                } else {
                    updateProject { it.copy(mods = it.mods + added) }
                    _state.update {
                        it.copy(message = "${added.size}件追加しました（必須依存関係を含む）")
                    }
                }
            }.onFailure(::showError)
            setBusy(false)
        }
    }

    fun addLocalJar(uri: Uri) {
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    val displayName = resolver.query(uri, null, null, null, null)?.use { cursor ->
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                    } ?: "local-mod.jar"

                    require(displayName.endsWith(".jar", ignoreCase = true)) {
                        "jarファイルを選択してください"
                    }

                    val folder = File(getApplication<Application>().filesDir, "pack_files/local")
                        .apply { mkdirs() }
                    val target = File(folder, "${UUID.randomUUID()}_${safeFileName(displayName)}")
                    resolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("選択したファイルを開けませんでした")

                    val hashes = Hashing.file(target)
                    PackMod(
                        title = displayName.removeSuffix(".jar"),
                        versionNumber = "local",
                        filename = displayName,
                        sha1 = hashes.sha1,
                        sha512 = hashes.sha512,
                        fileSize = target.length(),
                        cachedPath = target.absolutePath,
                        source = ModSource.LOCAL
                    )
                }
            }.onSuccess { mod ->
                updateProject { it.copy(mods = it.mods + mod) }
                _state.update { it.copy(message = "${mod.filename} を追加しました") }
            }.onFailure(::showError)
            setBusy(false)
        }
    }

    fun removeMod(mod: PackMod) {
        updateProject { project ->
            project.copy(mods = project.mods.filterNot { it === mod || it == mod })
        }
        _state.update { it.copy(message = "${mod.title} を削除しました") }
    }

    fun export(format: ExportFormat, uri: Uri) {
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri, "w")?.use { output ->
                        exporter.export(_state.value.project, format, output)
                    } ?: error("保存先を開けませんでした")
                }
            }.onSuccess {
                _state.update { it.copy(message = "MODPACKを書き出しました") }
            }.onFailure(::showError)
            setBusy(false)
        }
    }

    fun suggestedFileName(format: ExportFormat): String {
        val base = safeFileName(_state.value.project.name).removeSuffix(".jar")
        return when (format) {
            ExportFormat.FULL_ZIP -> "${base}-full.zip"
            ExportFormat.MANIFEST_PACK -> "$base.pdpack"
            ExportFormat.MRPACK -> "$base.mrpack"
        }
    }

    private fun applyVersionCatalog(
        minecraftVersions: List<String>,
        loaderVersions: List<String>
    ) {
        applyLoaderVersions(loaderVersions)
        _state.update { it.copy(minecraftVersions = minecraftVersions) }
    }

    private fun applyLoaderVersions(versions: List<String>) {
        val current = _state.value.project
        val selected = when {
            current.loader == "vanilla" -> ""
            current.loaderVersion in versions -> current.loaderVersion
            versions.isNotEmpty() -> versions.first()
            else -> current.loaderVersion
        }
        val updatedProject = if (selected != current.loaderVersion) {
            current.copy(loaderVersion = selected).also(repository::save)
        } else {
            current
        }
        _state.update { it.copy(project = updatedProject, loaderVersions = versions) }
    }

    private fun resolveProject(
        project: ModrinthProject,
        pack: PackProject,
        visited: MutableSet<String>,
        result: MutableList<PackMod>,
        preferredVersion: ModrinthVersion?
    ) {
        if (!visited.add(project.projectId)) return
        val version = preferredVersion ?: chooseVersion(
            modrinth.listVersions(project.projectId, pack.minecraftVersion, pack.loader)
        )
        resolveVersion(project, version, pack, visited, result)
    }

    private fun resolveVersion(
        project: ModrinthProject,
        version: ModrinthVersion,
        pack: PackProject,
        visited: MutableSet<String>,
        result: MutableList<PackMod>
    ) {
        val file = version.primaryFile()
            ?: error("${project.title} にダウンロード可能なファイルがありません")
        val cached = modrinth.downloadToCache(project.projectId, version.id, file)

        result += PackMod(
            title = project.title,
            projectId = project.projectId,
            versionId = version.id,
            versionNumber = version.versionNumber,
            filename = file.filename,
            downloadUrl = file.url,
            sha1 = file.sha1,
            sha512 = file.sha512,
            fileSize = file.size,
            cachedPath = cached.absolutePath,
            clientSide = project.clientSide,
            serverSide = project.serverSide,
            source = ModSource.MODRINTH
        )

        version.dependencies
            .filter { it.dependencyType == "required" }
            .forEach { dependency ->
                when {
                    dependency.versionId != null -> {
                        val dependencyVersion = modrinth.getVersion(dependency.versionId)
                        if (!visited.add(dependencyVersion.projectId)) return@forEach
                        val dependencyProject = modrinth.getProject(dependencyVersion.projectId)
                        resolveVersion(
                            dependencyProject,
                            dependencyVersion,
                            pack,
                            visited,
                            result
                        )
                    }
                    dependency.projectId != null -> {
                        val dependencyProject = modrinth.getProject(dependency.projectId)
                        resolveProject(
                            dependencyProject,
                            pack,
                            visited,
                            result,
                            preferredVersion = null
                        )
                    }
                }
            }
    }

    private fun chooseVersion(versions: List<ModrinthVersion>): ModrinthVersion {
        require(versions.isNotEmpty()) {
            "選択したMinecraftバージョン・ローダーに対応するMODバージョンがありません"
        }
        return versions.firstOrNull { it.versionType == "release" } ?: versions.first()
    }

    private fun setBusy(value: Boolean) = _state.update { it.copy(busy = value) }

    private fun setVersionCatalogBusy(value: Boolean) =
        _state.update { it.copy(versionCatalogBusy = value) }

    private fun showError(error: Throwable) {
        _state.update {
            it.copy(message = error.message ?: error::class.java.simpleName)
        }
    }
}
