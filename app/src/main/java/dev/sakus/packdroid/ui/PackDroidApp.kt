package dev.sakus.packdroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sakus.packdroid.model.ExportFormat
import dev.sakus.packdroid.model.ModSource
import dev.sakus.packdroid.model.ModrinthProject
import dev.sakus.packdroid.model.PackMod
import dev.sakus.packdroid.model.PackProject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackDroidApp(vm: PackViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val jarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(vm::addLocalJar)
    }
    val fullZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        it?.let { uri -> vm.export(ExportFormat.FULL_ZIP, uri) }
    }
    val manifest = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        it?.let { uri -> vm.export(ExportFormat.MANIFEST_PACK, uri) }
    }
    val mrpack = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-modrinth-modpack+zip")
    ) { it?.let { uri -> vm.export(ExportFormat.MRPACK, uri) } }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text("PackDroid", fontWeight = FontWeight.Bold)
                        Text(
                            "${state.project.minecraftVersion} / ${state.project.loader}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                })
            },
            bottomBar = {
                NavigationBar {
                    listOf("パック", "検索", "出力").forEachIndexed { index, text ->
                        NavigationBarItem(
                            selected = state.selectedTab == index,
                            onClick = { vm.selectTab(index) },
                            icon = { Text((index + 1).toString()) },
                            label = { Text(text) }
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (state.selectedTab) {
                    0 -> EditorScreen(
                        state.project,
                        vm::updateProject,
                        { jarPicker.launch(arrayOf("application/java-archive", "application/octet-stream")) },
                        vm::removeMod
                    )
                    1 -> SearchScreen(state, vm::setQuery, vm::search, vm::addProject)
                    else -> ExportScreen(
                        state.project,
                        { fullZip.launch(vm.suggestedFileName(ExportFormat.FULL_ZIP)) },
                        { manifest.launch(vm.suggestedFileName(ExportFormat.MANIFEST_PACK)) },
                        { mrpack.launch(vm.suggestedFileName(ExportFormat.MRPACK)) }
                    )
                }
                if (state.busy) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Card { Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("処理中…", modifier = Modifier.align(Alignment.CenterVertically))
                        } }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorScreen(
    project: PackProject,
    update: ((PackProject) -> PackProject) -> Unit,
    addJar: () -> Unit,
    remove: (PackMod) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("基本設定", style = MaterialTheme.typography.titleLarge) }
        item { Field(project.name, "MODPACK名") { value -> update { it.copy(name = value) } } }
        item { Field(project.versionId, "パックバージョン") { value -> update { it.copy(versionId = value) } } }
        item { Field(project.summary, "説明", false) { value -> update { it.copy(summary = value) } } }
        item { Field(project.minecraftVersion, "Minecraftバージョン") { value -> update { it.copy(minecraftVersion = value) } } }
        item {
            Text("ローダー", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("fabric", "forge", "neoforge", "quilt").forEach { loader ->
                    FilterChip(
                        selected = project.loader == loader,
                        onClick = { update { it.copy(loader = loader) } },
                        label = { Text(loader) }
                    )
                }
            }
        }
        item { Field(project.loaderVersion, "ローダーバージョン") { value -> update { it.copy(loaderVersion = value) } } }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("追加済みMOD", style = MaterialTheme.typography.titleLarge)
                    Text("${project.mods.size}件")
                }
                OutlinedButton(onClick = addJar) { Text("jarを追加") }
            }
        }
        if (project.mods.isEmpty()) {
            item { Card { Text("検索または端末からMODを追加してください。", Modifier.padding(16.dp)) } }
        } else {
            items(project.mods, key = { "${it.projectId}:${it.versionId}:${it.cachedPath}" }) { mod ->
                ModRow(mod) { remove(mod) }
            }
        }
    }
}

@Composable
private fun Field(value: String, label: String, singleLine: Boolean = true, change: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2
    )
}

@Composable
private fun ModRow(mod: PackMod, remove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(mod.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${mod.versionNumber} • ${if (mod.source == ModSource.MODRINTH) "Modrinth" else "Local"}")
                Text(mod.filename, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = remove) { Text("削除") }
        }
    }
}

@Composable
private fun SearchScreen(
    state: PackUiState,
    setQuery: (String) -> Unit,
    search: () -> Unit,
    add: (ModrinthProject) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Modrinthから追加", style = MaterialTheme.typography.titleLarge)
            Text("${state.project.minecraftVersion} / ${state.project.loader} で検索")
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(state.query, setQuery, Modifier.weight(1f), label = { Text("MOD名") }, singleLine = true)
                Button(onClick = search) { Text("検索") }
            }
        }
        items(state.searchResults, key = { it.projectId }) { project ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(project.title, fontWeight = FontWeight.Bold)
                            Text("by ${project.author} • ${project.downloads} DL", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(onClick = { add(project) }) { Text("追加") }
                    }
                    Text(project.description, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ExportScreen(project: PackProject, full: () -> Unit, manifest: () -> Unit, mrpack: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("${project.name} を書き出す", style = MaterialTheme.typography.titleLarge) }
        item { ExportButton("完全版ZIP", "jarをmods/へ含める", "ZIPを保存", full) }
        item { ExportButton("Manifest (.pdpack)", "ID・バージョン・URL・ハッシュのみ", "Manifestを保存", manifest) }
        item { ExportButton("Modrinth (.mrpack)", "Modrinth互換形式", ".mrpackを保存", mrpack) }
    }
}

@Composable
private fun ExportButton(title: String, detail: String, label: String, click: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail)
            Button(onClick = click, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
    }
}
