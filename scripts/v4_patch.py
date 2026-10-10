from pathlib import Path

path = Path('app/src/main/java/com/ondetv/app/ui/EnhancedOndeTvUi.kt')
text = path.read_text()

# Imports needed by the enhanced dashboard/player.
text = text.replace(
    'import androidx.compose.material.icons.filled.Rotate90DegreesCcw\n',
    'import androidx.compose.material.icons.filled.Rotate90DegreesCcw\nimport androidx.compose.material.icons.filled.Radio\n'
)
text = text.replace(
    'import androidx.media3.ui.CaptionStyleCompat\n',
    'import androidx.media3.ui.AspectRatioFrameLayout\nimport androidx.media3.ui.CaptionStyleCompat\n'
)

# Radio section: reuse radio/music categories already supplied by the IPTV service.
text = text.replace(
    '    var settingsOpen by remember { mutableStateOf(false) }\n',
    '    var settingsOpen by remember { mutableStateOf(false) }\n'
    '    var radioOnly by remember { mutableStateOf(false) }\n'
    '    val visibleCategories = if (radioOnly) categories.filter { c ->\n'
    '        val n = c.name.lowercase()\n'
    '        n.contains("radio") || n.contains("fm") || n.contains("music") || n.contains("musique") || n.contains("audio")\n'
    '    } else categories\n'
)
text = text.replace(
    '                settingsOpen = false\n                vm.home()\n',
    '                settingsOpen = false\n                radioOnly = false\n                vm.home()\n',
    1
)
text = text.replace(
    '                playing = null\n                vm.home()\n',
    '                playing = null\n                radioOnly = false\n                vm.home()\n',
    1
)
text = text.replace(
    '                IconButton(onClick = vm::home) {\n',
    '                IconButton(onClick = { radioOnly = false; vm.home() }) {\n',
    1
)

old_tabs = '''                SectionCard("TV LIVE", Icons.Filled.LiveTv, kind == "live", Modifier.weight(1f)) { vm.selectKind("live") }
                SectionCard("FILMS", Icons.Filled.Theaters, kind == "vod", Modifier.weight(1f)) { vm.selectKind("vod") }
                SectionCard("SÉRIES", Icons.Filled.VideoLibrary, kind == "series", Modifier.weight(1f)) { vm.selectKind("series") }'''
new_tabs = '''                SectionCard("TV LIVE", Icons.Filled.LiveTv, kind == "live" && !radioOnly, Modifier.weight(1f)) { radioOnly = false; vm.selectKind("live") }
                SectionCard("FILMS", Icons.Filled.Theaters, kind == "vod", Modifier.weight(1f)) { radioOnly = false; vm.selectKind("vod") }
                SectionCard("SÉRIES", Icons.Filled.VideoLibrary, kind == "series", Modifier.weight(1f)) { radioOnly = false; vm.selectKind("series") }
                SectionCard("RADIO", Icons.Filled.Radio, radioOnly, Modifier.weight(1f)) { radioOnly = true; vm.selectKind("live") }'''
text = text.replace(old_tabs, new_tabs)
text = text.replace(
    '                            else -> "Bouquets TV"\n',
    '                            else -> if (radioOnly) "Stations radio" else "Bouquets TV"\n',
    1
)
text = text.replace(
    '                        items(categories, key = CategoryEntity::categoryId) { category ->',
    '                        items(visibleCategories, key = CategoryEntity::categoryId) { category ->',
    1
)
text = text.replace(
    '                        val title = when (kind) { "vod" -> "Films"; "series" -> "Séries"; else -> "Chaînes" }\n',
    '                        val title = when { radioOnly -> "Radios"; kind == "vod" -> "Films"; kind == "series" -> "Séries"; else -> "Chaînes" }\n',
    1
)

# Settings inspired by the reference screens, but kept in Onde TV's own visual identity.
text = text.replace(
    '    var color by remember { mutableStateOf(prefs.getInt("cc_color", AndroidColor.WHITE)) }\n',
    '    var color by remember { mutableStateOf(prefs.getInt("cc_color", AndroidColor.WHITE)) }\n'
    '    var imageMode by remember { mutableStateOf(prefs.getString("image_mode_v2", "zoom") ?: "zoom") }\n',
    1
)
text = text.replace(
    '                Text("Sous-titres CC", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 21.sp)\n',
    '''                Text("Lecteur vidéo", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Text("Format et ajustement de l'image", color = TextMuted)
                listOf(
                    "zoom" to "Plein écran (conseillé)",
                    "fit" to "Original / Adapter",
                    "16_9" to "16:9",
                    "4_3" to "4:3",
                    "wide" to "Wide / Étirer"
                ).forEach { (value, label) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            imageMode = value
                            prefs.edit().putString("image_mode_v2", value).apply()
                        },
                        colors = CardDefaults.cardColors(containerColor = if (imageMode == value) PanelSoft else Panel),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = TextMain, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            if (imageMode == value) Text("✓", color = Green, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text("Sous-titres CC", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 21.sp)
''',
    1
)

# Player state: preserve the ExoPlayer across orientation changes and remember the image mode.
text = text.replace(
    '    var subtitleMenu by remember { mutableStateOf(false) }\n    var landscape by remember { mutableStateOf(false) }\n',
    '    var subtitleMenu by remember { mutableStateOf(false) }\n'
    '    var imageMenu by remember { mutableStateOf(false) }\n'
    '    var imageMode by remember { mutableStateOf(prefs.getString("image_mode_v2", "zoom") ?: "zoom") }\n'
    '    var landscape by remember { mutableStateOf(false) }\n',
    1
)

# Replace the old centered player layout by a true edge-to-edge player.
start_marker = '    Column(Modifier.fillMaxSize().background(Color.Black)) {\n'
start = text.find(start_marker)
if start == -1:
    raise SystemExit('Player layout marker not found')

player_layout = r'''    val resizeMode = when (imageMode) {
        "fit" -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        "wide" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        "16_9" -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        "4_3" -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }
    val forcedRatio = when (imageMode) {
        "16_9" -> 16f / 9f
        "4_3" -> 4f / 3f
        else -> 0f
    }
    val currentFormatLabel = when (imageMode) {
        "fit" -> "Original"
        "16_9" -> "16:9"
        "4_3" -> "4:3"
        "wide" -> "Wide"
        else -> "Plein écran"
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    keepScreenOn = true
                    this.resizeMode = resizeMode
                    findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)?.let { frame ->
                        if (forcedRatio > 0f) frame.setAspectRatio(forcedRatio)
                    }
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
                it.resizeMode = resizeMode
                it.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)?.let { frame ->
                    if (forcedRatio > 0f) frame.setAspectRatio(forcedRatio)
                }
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
            modifier = Modifier.fillMaxSize()
        )

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color(0xD907110B))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Retour", tint = Green)
            }
            IconButton(onClick = onHome) {
                Icon(Icons.Filled.Home, contentDescription = "Accueil", tint = Green)
            }
            Text(item.name, color = TextMain, modifier = Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.SemiBold)

            IconButton(onClick = { landscape = !landscape }) {
                Icon(
                    Icons.Filled.Rotate90DegreesCcw,
                    contentDescription = "Rotation",
                    tint = if (landscape) TextMain else Green
                )
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

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 14.dp)
        ) {
            Button(
                onClick = { imageMenu = true },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xE61B2B22), contentColor = Green)
            ) {
                Text("FORMAT · $currentFormatLabel", fontWeight = FontWeight.ExtraBold)
            }
            DropdownMenu(expanded = imageMenu, onDismissRequest = { imageMenu = false }) {
                listOf(
                    "zoom" to "Plein écran",
                    "fit" to "Original",
                    "16_9" to "16:9",
                    "4_3" to "4:3",
                    "wide" to "Wide"
                ).forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text((if (imageMode == value) "✓ " else "") + label) },
                        onClick = {
                            imageMode = value
                            prefs.edit().putString("image_mode_v2", value).apply()
                            imageMenu = false
                        }
                    )
                }
            }
        }

        TextButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                try {
                    context.startActivity(intent.setPackage("org.videolan.vlc"))
                } catch (_: ActivityNotFoundException) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 10.dp)
        ) { Text("VLC", color = Green) }
    }
}
'''

text = text[:start] + player_layout

required = [
    'Icons.Filled.Radio',
    'AspectRatioFrameLayout.RESIZE_MODE_ZOOM',
    'Modifier.fillMaxSize()',
    'image_mode_v2',
    'visibleCategories',
    'Stations radio',
    'Plein écran (conseillé)',
    'FORMAT · $currentFormatLabel'
]
missing = [s for s in required if s not in text]
if missing:
    raise SystemExit(f'Missing patches: {missing}')

path.write_text(text)
