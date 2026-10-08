package com.dwl.mutcube.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dwl.mutcube.R
import org.json.JSONObject

private data class LibraryLicense(
    val id: String,
    val name: String,
    val version: String,
    val licenseIds: List<String>,
    val website: String?,
)

private data class LicenseCatalog(
    val libraries: List<LibraryLicense>,
    val contents: Map<String, String>,
)

@Composable
fun ThirdPartyLicensesPage(onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val catalog = remember(resources) {
        val generated = runCatching {
            resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { parseCatalog(it.readText()) }
        }.getOrElse { LicenseCatalog(emptyList(), emptyMap()) }
        val iconLicense = runCatching {
            context.assets.open("licenses/lobe-icons.txt").bufferedReader().use { it.readText() }
        }.getOrNull()
        if (iconLicense == null) generated else generated.copy(
            libraries = generated.libraries + LibraryLicense(
                "lobe-icons-static-png", "Lobe Icons 静态品牌图标", "1.97.0", listOf("MIT-Lobe"),
                "https://github.com/lobehub/lobe-icons",
            ),
            contents = generated.contents + ("MIT-Lobe" to iconLicense),
        )
    }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<LibraryLicense?>(null) }
    val filtered = remember(query, catalog) {
        catalog.libraries.filter {
            query.isBlank() || it.name.contains(query, true) || it.licenseIds.any { id -> id.contains(query, true) }
        }
    }
    selected?.let { library ->
        val text = library.licenseIds.joinToString("\n\n") { id ->
            catalog.contents[id]?.let { "$id\n\n$it" } ?: "$id（生成清单中未包含许可全文）"
        }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("${library.name} ${library.version}") },
            text = {
                LazyColumn(Modifier.fillMaxWidth()) {
                    item { Text(text, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("关闭") } },
            dismissButton = {
                library.website?.let { url -> TextButton(onClick = { onOpenUrl(url) }) { Text("项目主页") } }
            },
        )
    }
    Column(Modifier.fillMaxSize()) {
        SettingsPageHeader("第三方开源许可", onBack)
        OutlinedTextField(
            query, { query = it },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text("搜索 ${catalog.libraries.size} 个依赖") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        )
        LazyColumn(Modifier.weight(1f).padding(horizontal = 18.dp)) {
            items(filtered, key = LibraryLicense::id) { library ->
                Card(onClick = { selected = library }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(library.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOf(library.version, library.licenseIds.joinToString()).filter(String::isNotBlank).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun parseCatalog(text: String): LicenseCatalog {
    val root = JSONObject(text)
    val licenseObject = root.getJSONObject("licenses")
    val contents = licenseObject.keys().asSequence().associateWith { id ->
        licenseObject.getJSONObject(id).optString("content")
    }
    val items = root.getJSONArray("libraries")
    val libraries = buildList {
        repeat(items.length()) { index ->
            val item = items.getJSONObject(index)
            val licenses = item.optJSONArray("licenses")
            add(
                LibraryLicense(
                    id = item.getString("uniqueId"),
                    name = item.optString("name").ifBlank { item.optString("uniqueId") },
                    version = item.optString("artifactVersion"),
                    licenseIds = buildList {
                        if (licenses != null) repeat(licenses.length()) { add(licenses.getString(it)) }
                    },
                    website = item.optString("website").takeIf(String::isNotBlank),
                ),
            )
        }
    }.sortedBy { it.name.lowercase() }
    return LicenseCatalog(libraries, contents)
}
