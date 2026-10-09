package com.ondetv.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ondetv.app.data.CategoryEntity
import com.ondetv.app.data.IptvRepository
import com.ondetv.app.data.MediaEntity
import com.ondetv.app.data.ServiceEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private class MainViewModel(private val repo: IptvRepository) : ViewModel() {
    val services = repo.dao.services().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val active = services.map { list -> list.firstOrNull { it.active } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val kind = MutableStateFlow("live")
    private val selectedCategory = MutableStateFlow<String?>(null)

    val categories = combine(active, kind) { service, k -> service to k }
        .flatMapLatest { (service, k) ->
            if (service == null) flowOf(emptyList()) else repo.dao.categories(service.id, k)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val media = combine(active, kind, selectedCategory) { service, k, category -> Triple(service, k, category) }
        .flatMapLatest { (service, k, category) ->
            if (service == null || category == null) flowOf(emptyList())
            else repo.dao.mediaByCategory(service.id, k, category)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentKind = kind
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun selectKind(value: String) {
        kind.value = value
        selectedCategory.value = null
    }

    fun selectCategory(value: String) { selectedCategory.value = value }

    fun addXtream(name: String, base: String, user: String, pass: String) = viewModelScope.launch {
        busy.value = true
        error.value = null
        repo.addXtream(name, base, user, pass).onFailure { error.value = it.message ?: "Erreur inconnue" }
        busy.value = false
    }

    fun addM3u(name: String, url: String) = viewModelScope.launch {
        busy.value = true
        error.value = null
        repo.addM3u(name, url).onFailure { error.value = it.message ?: "Erreur inconnue" }
        busy.value = false
    }

    fun refresh() = viewModelScope.launch {
        val service = active.value ?: return@launch
        busy.value = true
        error.value = null
        runCatching { repo.refresh(service.id) }.onFailure { error.value = it.message ?: "Actualisation impossible" }
        busy.value = false
    }
}

private class VmFactory(private val repo: IptvRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repo) as T
}

@Composable
fun OndeTvRoot(repo: IptvRepository) {
    val vm: MainViewModel = viewModel(factory = VmFactory(repo))
    val active by vm.active.collectAsState()
    if (active == null) AddServiceScreen(vm) else LibraryScreen(vm, repo, active!!)
}

@Composable
private fun AddServiceScreen(vm: MainViewModel) {
    var name by remember { mutableStateOf("") }
    var base by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var m3u by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Onde TV — Ajouter un service", style = MaterialTheme.typography.headlineMedium)
        Text("Utilisez uniquement un service IPTV auquel vous êtes autorisé à accéder.")
        OutlinedTextField(name, { name = it }, label = { Text("Nom du service") })
        OutlinedTextField(base, { base = it }, label = { Text("URL serveur Xtream") })
        OutlinedTextField(user, { user = it }, label = { Text("Identifiant") })
        OutlinedTextField(pass, { pass = it }, label = { Text("Mot de passe") })
        Button(
            onClick = { vm.addXtream(name, base, user, pass) },
            enabled = !busy && name.isNotBlank() && base.isNotBlank() && user.isNotBlank()
        ) { Text("Ajouter Xtream Codes") }
        HorizontalDivider()
        OutlinedTextField(m3u, { m3u = it }, label = { Text("URL M3U / M3U8") })
        Button(
            onClick = { vm.addM3u(name, m3u) },
            enabled = !busy && name.isNotBlank() && m3u.isNotBlank()
        ) { Text("Ajouter M3U") }
        if (busy) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun LibraryScreen(vm: MainViewModel, repo: IptvRepository, service: ServiceEntity) {
    val kind by vm.currentKind.collectAsState()
    val categories by vm.categories.collectAsState()
    val media by vm.media.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    var playing by remember { mutableStateOf<MediaEntity?>(null) }

    if (playing != null) {
        PlayerScreen(repo, service, playing!!) { playing = null }
        return
    }

    Row(Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        Column(
            Modifier.width(210.dp).fillMaxHeight().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Onde TV", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { vm.selectKind("live") }, modifier = Modifier.fillMaxWidth()) { Text("TV LIVE") }
            Button(onClick = { vm.selectKind("vod") }, modifier = Modifier.fillMaxWidth()) { Text("FILMS") }
            Button(onClick = { vm.selectKind("series") }, modifier = Modifier.fillMaxWidth()) { Text("SÉRIES") }
            OutlinedButton(onClick = vm::refresh, modifier = Modifier.fillMaxWidth()) { Text("Actualiser") }
            if (busy) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        LazyColumn(Modifier.width(300.dp).fillMaxHeight().padding(8.dp)) {
            item { Text("Bouquets / catégories", style = MaterialTheme.typography.titleMedium) }
            items(categories, key = CategoryEntity::categoryId) { category ->
                Surface(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { vm.selectCategory(category.categoryId) },
                    tonalElevation = 2.dp
                ) { Text(category.name, Modifier.padding(12.dp)) }
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
            item {
                val title = when (kind) { "vod" -> "Films"; "series" -> "Séries"; else -> "Chaînes" }
                Text("$title — ${media.size}", style = MaterialTheme.typography.titleMedium)
            }
            items(media, key = MediaEntity::streamId) { item ->
                Surface(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                        if (kind != "series") playing = item
                    }, tonalElevation = 1.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.name)
                        item.plot?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 2, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerScreen(repo: IptvRepository, service: ServiceEntity, item: MediaEntity, onBack: () -> Unit) {
    val context = LocalContext.current
    val url = remember(item) { repo.streamUrl(service, item) }
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("← Retour") }
            Text(item.name, Modifier.weight(1f).padding(12.dp))
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                try {
                    context.startActivity(intent.setPackage("org.videolan.vlc"))
                } catch (_: ActivityNotFoundException) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }) { Text("VLC / externe") }
        }
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = true } },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}
