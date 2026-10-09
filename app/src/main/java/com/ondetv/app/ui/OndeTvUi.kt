package com.ondetv.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
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
import java.util.Locale

private val OndeBg = Color(0xFF07110B)
private val OndePanel = Color(0xFF101B14)
private val OndePanelSoft = Color(0xFF162319)
private val OndeGreen = Color(0xFF6CFF59)
private val OndeText = Color(0xFFF3F6F3)
private val OndeMuted = Color(0xFFAEB8AF)
private val OndeBorder = Color(0xFF33473A)

private class MainViewModel(private val repo: IptvRepository) : ViewModel() {
    val services = repo.dao.services().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val active = services.map { list -> list.firstOrNull { it.active } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val kind = MutableStateFlow("live")
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val selectedSeries = MutableStateFlow<String?>(null)

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
    val currentCategory = selectedCategory
    val currentSeries = selectedSeries
    val seriesEpisodes = MutableStateFlow<List<MediaEntity>>(emptyList())
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

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

    fun backToCategories() {
        selectedCategory.value = null
        selectedSeries.value = null
        seriesEpisodes.value = emptyList()
        error.value = null
    }

    fun backToSeriesList() {
        selectedSeries.value = null
        seriesEpisodes.value = emptyList()
        error.value = null
    }

    fun openSeries(service: ServiceEntity, item: MediaEntity) = viewModelScope.launch {
        busy.value = true
        error.value = null
        selectedSeries.value = item.name
        runCatching { repo.getSeriesEpisodes(service, item.streamId) }
            .onSuccess { episodes ->
                seriesEpisodes.value = episodes
                if (episodes.isEmpty()) error.value = "Aucun épisode trouvé pour cette série."
            }
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
    val scroll = rememberScrollState()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = OndeText,
        unfocusedTextColor = OndeText,
        focusedBorderColor = OndeGreen,
        unfocusedBorderColor = OndeBorder,
        focusedLabelColor = OndeGreen,
        unfocusedLabelColor = OndeMuted,
        cursorColor = OndeGreen,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent
    )

    Surface(color = OndeBg, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.background(OndeGreen, RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text("ONDE TV", color = OndeBg, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text("Connectez votre service IPTV", color = OndeText, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Spacer(Modifier.height(6.dp))
            Text("Ajoutez vos identifiants Xtream Codes ou votre lien M3U.", color = OndeMuted, fontSize = 14.sp)
            Spacer(Modifier.height(22.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = OndePanel)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Xtream Codes", color = OndeText, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    OutlinedTextField(name, { name = it }, label = { Text("Nom du service") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = fieldColors, singleLine = true)
                    OutlinedTextField(base, { base = it }, label = { Text("URL du serveur") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = fieldColors, singleLine = true)
                    OutlinedTextField(user, { user = it }, label = { Text("Identifiant") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = fieldColors, singleLine = true)
                    OutlinedTextField(pass, { pass = it }, label = { Text("Mot de passe") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = fieldColors, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Button(
                        onClick = { vm.addXtream(name, base, user, pass) },
                        enabled = !busy && name.isNotBlank() && base.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OndeGreen, contentColor = OndeBg)
                    ) { Text("SE CONNECTER", fontWeight = FontWeight.ExtraBold) }
                }
            }

            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = OndePanelSoft)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Playlist M3U", color = OndeText, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    OutlinedTextField(m3u, { m3u = it }, label = { Text("URL M3U / M3U8") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = fieldColors, singleLine = true)
                    OutlinedButton(
                        onClick = { vm.addM3u(name.ifBlank { "Ma playlist" }, m3u) },
                        enabled = !busy && m3u.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OndeGreen)
                    ) { Text("AJOUTER LA PLAYLIST", fontWeight = FontWeight.Bold) }
                }
            }

            if (busy) {
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator(color = OndeGreen)
            }
            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = Color(0xFFFFB4AB))
            }
        }
    }
}

@Composable
private fun LibraryScreen(vm: MainViewModel, repo: IptvRepository, service: ServiceEntity) {
    val kind by vm.currentKind.collectAsState()
    val categories by vm.categories.collectAsState()
    val selectedCategory by vm.currentCategory.collectAsState()
    val selectedSeries by vm.currentSeries.collectAsState()
    val media by vm.media.collectAsState()
    val episodes by vm.seriesEpisodes.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    var playing by remember { mutableStateOf<MediaEntity?>(null) }

    if (playing != null) {
        PlayerScreen(repo, service, playing!!) { playing = null }
        return
    }

    Surface(color = OndeBg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(OndePanel).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Onde TV", color = OndeText, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::refresh) { Text("Actualiser", color = OndeGreen) }
            }

            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionButton("TV LIVE", Icons.Filled.LiveTv, kind == "live", Modifier.weight(1f)) { vm.selectKind("live") }
                SectionButton("FILMS", Icons.Filled.Movie, kind == "vod", Modifier.weight(1f)) { vm.selectKind("vod") }
                SectionButton("SÉRIES", Icons.Filled.VideoLibrary, kind == "series", Modifier.weight(1f)) { vm.selectKind("series") }
            }

            if (busy) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = OndeGreen)
                }
            }

            error?.let {
                Text(it, color = Color(0xFFFFB4AB), modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            }

            when {
                selectedSeries != null -> {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = vm::backToSeriesList) { Text("← Séries", color = OndeGreen) }
                        Text(selectedSeries!!, color = OndeText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                        items(episodes, key = MediaEntity::streamId) { episode ->
                            MediaRow(episode) { playing = episode }
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
                        color = OndeText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                        items(categories, key = CategoryEntity::categoryId) { category ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { vm.selectCategory(category.categoryId) },
                                colors = CardDefaults.cardColors(containerColor = OndePanel),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(category.name, color = OndeText, fontSize = 17.sp, modifier = Modifier.weight(1f))
                                    Text("›", color = OndeGreen, fontSize = 26.sp)
                                }
                            }
                        }
                    }
                }

                else -> {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = vm::backToCategories) { Text("← Bouquets", color = OndeGreen) }
                        val title = when (kind) { "vod" -> "Films"; "series" -> "Séries"; else -> "Chaînes" }
                        Text("$title (${media.size})", color = OndeText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                        items(media, key = MediaEntity::streamId) { item ->
                            MediaRow(item) {
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
private fun SectionButton(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(78.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) OndeGreen else OndePanelSoft,
            contentColor = if (selected) OndeBg else OndeText
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = label)
            Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MediaRow(item: MediaEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = OndePanel),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(item.name, color = OndeText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            item.plot?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = OndeMuted, maxLines = 2, fontSize = 13.sp)
            }
        }
    }
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
                val languageLabel = language?.let {
                    Locale.forLanguageTag(it).displayLanguage.takeIf { text -> text.isNotBlank() }
                }
                val label = format.label?.takeIf { it.isNotBlank() }
                    ?: languageLabel
                    ?: if (type == C.TRACK_TYPE_AUDIO) "Audio ${result.size + 1}" else "Sous-titre ${result.size + 1}"
                result += TrackChoice(groupIndex, trackIndex, type, language, label, group.isTrackSelected(trackIndex))
            }
        }
    }
    return result
}

private fun selectTrack(player: ExoPlayer, choice: TrackChoice) {
    val group = player.currentTracks.groups.getOrNull(choice.groupIndex) ?: return
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(choice.type, false)
        .clearOverridesOfType(choice.type)
        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, choice.trackIndex))
        .build()
}

@Composable
private fun PlayerScreen(repo: IptvRepository, service: ServiceEntity, item: MediaEntity, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember { context.getSharedPreferences("onde_player_preferences", Context.MODE_PRIVATE) }
    val url = remember(item) { repo.streamUrl(service, item) }

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            val preferredAudio = prefs.getString("preferred_audio_language", null)
            val preferredSubtitle = prefs.getString("preferred_subtitle_language", null)
            val subtitlesEnabled = prefs.getBoolean("subtitles_enabled", true)
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .apply {
                    preferredAudio?.let { setPreferredAudioLanguage(it) }
                    preferredSubtitle?.let { setPreferredTextLanguage(it) }
                    setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                }
                .build()
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    var audioTracks by remember { mutableStateOf<List<TrackChoice>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<TrackChoice>>(emptyList()) }
    var audioMenu by remember { mutableStateOf(false) }
    var subtitleMenu by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                audioTracks = buildTrackChoices(tracks, C.TRACK_TYPE_AUDIO)
                subtitleTracks = buildTrackChoices(tracks, C.TRACK_TYPE_TEXT)
            }
        }
        player.addListener(listener)
        audioTracks = buildTrackChoices(player.currentTracks, C.TRACK_TYPE_AUDIO)
        subtitleTracks = buildTrackChoices(player.currentTracks, C.TRACK_TYPE_TEXT)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose { }
        val oldOrientation = activity.requestedOrientation
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            activity.requestedOrientation = oldOrientation
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    keepScreenOn = true
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            Modifier.fillMaxWidth().background(Color(0xAA07110B)).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Retour", color = OndeGreen) }
            Text(item.name, color = OndeText, modifier = Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1)

            Box {
                TextButton(onClick = { audioMenu = true }) {
                    Text("Audio", color = OndeGreen)
                }
                DropdownMenu(expanded = audioMenu, onDismissRequest = { audioMenu = false }) {
                    if (audioTracks.isEmpty()) {
                        DropdownMenuItem(text = { Text("Aucune autre piste audio") }, onClick = { audioMenu = false })
                    } else {
                        audioTracks.forEach { choice ->
                            DropdownMenuItem(
                                text = { Text((if (choice.selected) "✓ " else "") + choice.label) },
                                onClick = {
                                    selectTrack(player, choice)
                                    choice.language?.let {
                                        prefs.edit().putString("preferred_audio_language", it).apply()
                                    }
                                    audioMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Box {
                TextButton(onClick = { subtitleMenu = true }) {
                    Text("CC", color = OndeGreen)
                }
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
                                choice.language?.let {
                                    prefs.edit().putString("preferred_subtitle_language", it).apply()
                                }
                                subtitleMenu = false
                            }
                        )
                    }
                }
            }

            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                try {
                    context.startActivity(intent.setPackage("org.videolan.vlc"))
                } catch (_: ActivityNotFoundException) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }) { Text("VLC", color = OndeGreen) }
        }
    }
}
