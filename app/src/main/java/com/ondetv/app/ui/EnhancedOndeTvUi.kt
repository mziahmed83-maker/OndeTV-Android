package com.ondetv.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.ondetv.app.data.CategoryEntity
import com.ondetv.app.data.IptvRepository
import com.ondetv.app.data.MediaEntity
import com.ondetv.app.data.ServiceEntity
import com.ondetv.app.player.PlayerCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

private val Bg = Color(0xFF07110B)
private val Panel = Color(0xFF101B14)
private val PanelSoft = Color(0xFF162319)
private val Green = Color(0xFF6CFF59)
private val TextMain = Color(0xFFF3F6F3)
private val TextMuted = Color(0xFFAEB8AF)
private val Border = Color(0xFF33473A)

private class EnhancedViewModel(private val repo: IptvRepository) : ViewModel() {
    val services = repo.dao.services().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val active = services.map { list -> list.firstOrNull { it.active } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val kind = MutableStateFlow("live")
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val selectedSeries = MutableStateFlow<String?>(null)

    val currentKind = kind
    val currentCategory = selectedCategory
    val currentSeries = selectedSeries
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val seriesEpisodes = MutableStateFlow<List<MediaEntity>>(emptyList())

    val categories = combine(active, kind) { service, currentKind -> service to currentKind }
        .flatMapLatest { (service, currentKind) ->
            if (service == null) flowOf(emptyList()) else repo.dao.categories(service.id, currentKind)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val media = combine(active, kind, selectedCategory) { service, currentKind, category ->
        Triple(service, currentKind, category)
    }.flatMapLatest { (service, currentKind, category) ->
        if (service == null || category == null) flowOf(emptyList())
        else repo.dao.mediaByCategory(service.id, currentKind, category)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectKind(value: String) {
        kind.value = value
        selectedCategory.value = null
        selectedSeries.value = null
        seriesEpisodes.value = emptyList()
        error.value = null
    }

    fun selectCategory(value: String) {
        selectedCategory.value = value
        selectedSeries.value = null
        seriesEpisodes.value = emptyList()
        error.value = null
    }

    fun backOneLevel() {
        when {
            selectedSeries.value != null -> {
                selectedSeries.value = null
                seriesEpisodes.value = emptyList()
            }
            selectedCategory.value != null -> selectedCategory.value = null
            else -> home()
        }
        error.value = null
    }

    fun home() {
        kind.value = "live"
        selectedCategory.value = null
        selectedSeries.value = null
        seriesEpisodes.value = emptyList()
        error.value = null
    }

    fun openSeries(service: ServiceEntity, item: MediaEntity) = viewModelScope.launch {
        busy.value = true
        error.value = null
        selectedSeries.value = item.name
        runCatching { repo.getSeriesEpisodes(service, item.streamId) }
            .onSuccess { seriesEpisodes.value = it }
            .onFailure { error.value = it.message ?: "Impossible de charger les épisodes" }
        busy.value = false
    }

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

private class EnhancedFactory(private val repo: IptvRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = EnhancedViewModel(repo) as T
}

@Composable
fun OndeTvRootEnhanced(repo: IptvRepository) {
    val vm: EnhancedViewModel = viewModel(factory = EnhancedFactory(repo))
    val active by vm.active.collectAsState()
    if (active == null) EnhancedAddServiceScreen(vm) else EnhancedLibraryScreen(vm, repo, active!!)
}

@Composable
private fun EnhancedAddServiceScreen(vm: EnhancedViewModel) {
    var name by remember { mutableStateOf("") }
    var base by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var m3u by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val scroll = rememberScrollState()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextMain,
        unfocusedTextColor = TextMain,
        focusedBorderColor = Green,
        unfocusedBorderColor = Border,
        focusedLabelColor = Green,
        unfocusedLabelColor = TextMuted,
        cursorColor = Green
    )

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.background(Green, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text("ONDE TV", color = Bg, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text("Connectez votre service IPTV", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Xtream Codes", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    OutlinedTextField(name, { name = it }, label = { Text("Nom du service") }, colors = fieldColors, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(base, { base = it }, label = { Text("URL du serveur") }, colors = fieldColors, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(user, { user = it }, label = { Text("Identifiant") }, colors = fieldColors, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(pass, { pass = it }, label = { Text("Mot de passe") }, colors = fieldColors, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Button(
                        onClick = { vm.addXtream(name, base, user, pass) },
                        enabled = !busy && name.isNotBlank() && base.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Bg)
                    ) { Text("SE CONNECTER", fontWeight = FontWeight.ExtraBold) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Card(colors = CardDefaults.cardColors(containerColor = PanelSoft), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Playlist M3U", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    OutlinedTextField(m3u, { m3u = it }, label = { Text("URL M3U / M3U8") }, colors = fieldColors, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedButton(onClick = { vm.addM3u(name.ifBlank { "Ma playlist" }, m3u) }, enabled = !busy && m3u.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Text("AJOUTER LA PLAYLIST", color = Green)
                    }
                }
            }
            if (busy) {
                Spacer(Modifier.height(18.dp))
                CircularProgressIndicator(color = Green)
            }
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFFFFB4AB))
            }
        }
    }
}

@Composable
private fun EnhancedLibraryScreen(vm: EnhancedViewModel, repo: IptvRepository, service: ServiceEntity) {
    val kind by vm.currentKind.collectAsState()
    val categories by vm.categories.collectAsState()
    val selectedCategory by vm.currentCategory.collectAsState()
    val selectedSeries by vm.currentSeries.collectAsState()
    val media by vm.media.collectAsState()
    val episodes by vm.seriesEpisodes.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()

    var playing by remember { mutableStateOf<MediaEntity?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }

    if (settingsOpen) {
        SubtitleSettingsScreen(
            onBack = { settingsOpen = false },
            onHome = {
                settingsOpen = false
                vm.home()
            }
        )
        return
    }

    if (playing != null) {
        EnhancedPlayerScreen(
            repo = repo,
            service = service,
            item = playing!!,
            onBack = { playing = null },
            onHome = {
                playing = null
                vm.home()
            }
        )
        return
    }

    BackHandler(enabled = true) {
        when {
            selectedSeries != null || selectedCategory != null -> vm.backOneLevel()
            kind != "live" -> vm.home()
            else -> Unit
        }
    }

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Panel).padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedSeries != null || selectedCategory != null) {
                    IconButton(onClick = vm::backOneLevel) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour", tint = Green)
                    }
                }
                IconButton(onClick = vm::home) {
                    Icon(Icons.Filled.Home, contentDescription = "Accueil", tint = Green)
                }
                Text("Onde TV", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::refresh) { Text("Actualiser", color = Green) }
                IconButton(onClick = { settingsOpen = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Réglages", tint = Green)
                }
            }

            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionCard("TV LIVE", Icons.Filled.LiveTv, kind == "live", Modifier.weight(1f)) { vm.selectKind("live") }
                SectionCard("FILMS", Icons.Filled.Theaters, kind == "vod", Modifier.weight(1f)) { vm.selectKind("vod") }
                SectionCard("SÉRIES", Icons.Filled.VideoLibrary, kind == "series", Modifier.weight(1f)) { vm.selectKind("series") }
            }

            if (busy) {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = Green)
                }
            }
            error?.let { Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(12.dp)) }

            when {
                selectedSeries != null -> {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = vm::backOneLevel) { Text("← Séries", color = Green) }
                        Text(selectedSeries!!, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                        items(episodes, key = MediaEntity::streamId) { episode ->
                            ArtworkMediaRow(episode) { playing = episode }
                        }
                    }
                }

                selectedCategory == null -> {
                    Text(
                        when (kind) {
                            "vod" -> "Catégories de films"
                            "series" -> "Catégories de séries"
                            else -> "Bouquets TV"
                        },
                        color = TextMain,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                        items(categories, key = CategoryEntity::categoryId) { category ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { vm.selectCategory(category.categoryId) },
                                colors = CardDefaults.cardColors(containerColor = Panel),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(category.name, color = TextMain, fontSize = 17.sp, modifier = Modifier.weight(1f))
                                    Text("›", color = Green, fontSize = 26.sp)
                                }
                            }
                        }
                    }
                }

                else -> {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = vm::backOneLevel) { Text("← Bouquets", color = Green) }
                        val title = when (kind) { "vod" -> "Films"; "series" -> "Séries"; else -> "Chaînes" }
                        Text("$title (${media.size})", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                        items(media, key = MediaEntity::streamId) { item ->
                            ArtworkMediaRow(item) {
                                if (kind == "series") vm.openSeries(service, item) else playing = item
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(88.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Green else PanelSoft,
            contentColor = if (selected) Bg else TextMain
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, contentDescription = label)
            Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SubtitleSettingsScreen(onBack: () -> Unit, onHome: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("onde_player_preferences", Context.MODE_PRIVATE) }
    var size by remember { mutableStateOf(prefs.getString("cc_size", "medium") ?: "medium") }
    var color by remember { mutableStateOf(prefs.getInt("cc_color", AndroidColor.WHITE)) }

    BackHandler(onBack = onBack)

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(Panel).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour", tint = Green) }
                IconButton(onClick = onHome) { Icon(Icons.Filled.Home, contentDescription = "Accueil", tint = Green) }
                Text("Réglages", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            }

            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Sous-titres CC", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Text("Taille", color = TextMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("small" to "Petit", "medium" to "Moyen", "large" to "Grand").forEach { (value, label) ->
                        SettingsChoice(label, size == value, Modifier.weight(1f)) {
                            size = value
                            prefs.edit().putString("cc_size", value).apply()
                        }
                    }
                }

                Text("Couleur", color = TextMuted)
                val colors = listOf(
                    AndroidColor.YELLOW to "Jaune",
                    AndroidColor.rgb(80, 150, 255) to "Bleu",
                    AndroidColor.rgb(255, 152, 0) to "Orange",
                    AndroidColor.WHITE to "Blanc"
                )
                colors.forEach { (value, label) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            color = value
                            prefs.edit().putInt("cc_color", value).apply()
                        },
                        colors = CardDefaults.cardColors(containerColor = if (color == value) PanelSoft else Panel),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.background(Color(value), RoundedCornerShape(20.dp)).padding(12.dp))
                            Spacer(Modifier.padding(6.dp))
                            Text(label, color = TextMain, modifier = Modifier.weight(1f))
                            if (color == value) Text("✓", color = Green, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("Cache vidéo : 2 Go maximum (gestion automatique LRU)", color = TextMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun SettingsChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Green else PanelSoft,
            contentColor = if (selected) Bg else TextMain
        )
    ) { Text(label) }
}

private data class TrackChoice(
    val groupIndex: Int,
    val trackIndex: Int,
    val type: Int,
    val language: String?,
    val label: String,
    val selected: Boolean
)

private fun buildTrackChoices(tracks: Tracks, type: Int): List<TrackChoice> {
    val result = mutableListOf<TrackChoice>()
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type == type) {
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                val language = format.language?.takeIf { it.isNotBlank() }
                val displayLanguage = language?.let { Locale.forLanguageTag(it).displayLanguage.takeIf(String::isNotBlank) }
                val label = format.label?.takeIf { it.isNotBlank() }
                    ?: displayLanguage
                    ?: if (type == C.TRACK_TYPE_AUDIO) "Audio ${result.size + 1}" else "Sous-titre ${result.size + 1}"
                result += TrackChoice(groupIndex, trackIndex, type, language, label, group.isTrackSelected(trackIndex))
            }
        }
    }
    return result
}

private fun selectTrack(player: ExoPlayer, choice: TrackChoice) {
    val group = player.currentTracks.groups.getOrNull(choice.groupIndex) ?: return
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(choice.type, false)
        .clearOverridesOfType(choice.type)
        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, choice.trackIndex))
        .build()
}

@Composable
private fun EnhancedPlayerScreen(
    repo: IptvRepository,
    service: ServiceEntity,
    item: MediaEntity,
    onBack: () -> Unit,
    onHome: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember { context.getSharedPreferences("onde_player_preferences", Context.MODE_PRIVATE) }
    val url = remember(item) { repo.streamUrl(service, item) }
    val ccSize = prefs.getString("cc_size", "medium") ?: "medium"
    val ccColor = prefs.getInt("cc_color", AndroidColor.WHITE)
    val ccFraction = when (ccSize) { "small" -> 0.040f; "large" -> 0.070f; else -> 0.0533f }

    val player = remember(url) {
        val mediaSourceFactory = DefaultMediaSourceFactory(PlayerCache.dataSourceFactory(context))
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                val preferredAudio = prefs.getString("preferred_audio_language", null)
                val preferredSubtitle = prefs.getString("preferred_subtitle_language", null)
                val subtitlesEnabled = prefs.getBoolean("subtitles_enabled", true)
                trackSelectionParameters = trackSelectionParameters.buildUpon().apply {
                    preferredAudio?.let { setPreferredAudioLanguage(it) }
                    preferredSubtitle?.let { setPreferredTextLanguage(it) }
                    setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                }.build()
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
    }

    var audioTracks by remember { mutableStateOf<List<TrackChoice>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<TrackChoice>>(emptyList()) }
    var audioMenu by remember { mutableStateOf(false) }
    var subtitleMenu by remember { mutableStateOf(false) }
    var landscape by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                audioTracks = buildTrackChoices(tracks, C.TRACK_TYPE_AUDIO)
                subtitleTracks = buildTrackChoices(tracks, C.TRACK_TYPE_TEXT)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose { }
        val oldOrientation = activity.requestedOrientation
        onDispose {
            activity.requestedOrientation = oldOrientation
            WindowCompat.setDecorFitsSystemWindows(activity.window, true)
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(activity, landscape) {
        activity ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (landscape) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.setDecorFitsSystemWindows(activity.window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xEE07110B)).padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour", tint = Green) }
            IconButton(onClick = onHome) { Icon(Icons.Filled.Home, contentDescription = "Accueil", tint = Green) }
            Text(item.name, color = TextMain, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = { landscape = !landscape }) {
                Icon(Icons.Filled.Rotate90DegreesCcw, contentDescription = "Rotation", tint = if (landscape) TextMain else Green)
            }

            Box {
                TextButton(onClick = { audioMenu = true }) { Text("Audio", color = Green) }
                DropdownMenu(expanded = audioMenu, onDismissRequest = { audioMenu = false }) {
                    if (audioTracks.isEmpty()) {
                        DropdownMenuItem(text = { Text("Piste audio par défaut") }, onClick = { audioMenu = false })
                    } else {
                        audioTracks.forEach { choice ->
                            DropdownMenuItem(
                                text = { Text((if (choice.selected) "✓ " else "") + choice.label) },
                                onClick = {
                                    selectTrack(player, choice)
                                    choice.language?.let { prefs.edit().putString("preferred_audio_language", it).apply() }
                                    audioMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Box {
                TextButton(onClick = { subtitleMenu = true }) { Text("CC", color = Green) }
                DropdownMenu(expanded = subtitleMenu, onDismissRequest = { subtitleMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Désactivés") },
                        onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .build()
                            prefs.edit().putBoolean("subtitles_enabled", false).apply()
                            subtitleMenu = false
                        }
                    )
                    subtitleTracks.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text((if (choice.selected) "✓ " else "") + choice.label) },
                            onClick = {
                                selectTrack(player, choice)
                                prefs.edit().putBoolean("subtitles_enabled", true).apply()
                                choice.language?.let { prefs.edit().putString("preferred_subtitle_language", it).apply() }
                                subtitleMenu = false
                            }
                        )
                    }
                }
            }
        }

        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    keepScreenOn = true
                    subtitleView?.setStyle(
                        CaptionStyleCompat(
                            ccColor,
                            AndroidColor.TRANSPARENT,
                            AndroidColor.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            AndroidColor.BLACK,
                            null
                        )
                    )
                    subtitleView?.setFractionalTextSize(ccFraction)
                }
            },
            update = {
                it.player = player
                it.subtitleView?.setStyle(
                    CaptionStyleCompat(
                        ccColor,
                        AndroidColor.TRANSPARENT,
                        AndroidColor.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        AndroidColor.BLACK,
                        null
                    )
                )
                it.subtitleView?.setFractionalTextSize(ccFraction)
            },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        Row(Modifier.fillMaxWidth().background(Panel), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                try {
                    context.startActivity(intent.setPackage("org.videolan.vlc"))
                } catch (_: ActivityNotFoundException) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }) { Text("Ouvrir dans VLC", color = Green) }
        }
    }
}
