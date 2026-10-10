from pathlib import Path

path = Path('app/src/main/java/com/ondetv/app/ui/EnhancedOndeTvUi.kt')
text = path.read_text()

# Imports for tap-to-show controls and timed auto-hide.
text = text.replace(
    'import androidx.compose.foundation.clickable\n',
    'import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.gestures.detectTapGestures\n'
)
text = text.replace(
    'import androidx.compose.ui.graphics.vector.ImageVector\n',
    'import androidx.compose.ui.graphics.vector.ImageVector\nimport androidx.compose.ui.input.pointer.pointerInput\n'
)
text = text.replace(
    'import kotlinx.coroutines.flow.MutableStateFlow\n',
    'import kotlinx.coroutines.delay\nimport kotlinx.coroutines.flow.MutableStateFlow\n'
)

# Keep app controls hidden by default after a few seconds, but visible initially.
text = text.replace(
    '    var landscape by remember { mutableStateOf(false) }\n',
    '    var landscape by remember { mutableStateOf(false) }\n    var controlsVisible by remember { mutableStateOf(true) }\n',
    1
)

old_disposable = '''    DisposableEffect(activity) {
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
'''

new_disposable = '''    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose { }
        val oldOrientation = activity.requestedOrientation
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity.requestedOrientation = oldOrientation
            WindowCompat.setDecorFitsSystemWindows(activity.window, true)
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(activity, landscape) {
        activity ?: return@LaunchedEffect
        activity.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    LaunchedEffect(controlsVisible, audioMenu, subtitleMenu, imageMenu) {
        if (controlsVisible && !audioMenu && !subtitleMenu && !imageMenu) {
            delay(5000)
            controlsVisible = false
        }
    }
'''

if old_disposable not in text:
    raise SystemExit('Immersive effect marker not found')
text = text.replace(old_disposable, new_disposable, 1)

start_marker = '    val resizeMode = when (imageMode) {'
start = text.find(start_marker)
if start == -1:
    raise SystemExit('Player layout start not found')

player_tail = r'''    val resizeMode = when (imageMode) {
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
                    useController = false
                    controllerAutoShow = false
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
                it.useController = false
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

        // The whole video is the touch target. A simple tap shows or hides only OndeTV controls.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { controlsVisible = !controlsVisible })
                }
        )

        if (controlsVisible) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color(0xD9061423))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Retour", tint = Green)
                }
                IconButton(onClick = onHome) {
                    Icon(Icons.Filled.Home, contentDescription = "Accueil", tint = Green)
                }
                Text(
                    item.name,
                    color = TextMain,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { landscape = !landscape }) {
                    Icon(
                        Icons.Filled.Rotate90DegreesCcw,
                        contentDescription = "Rotation",
                        tint = Green
                    )
                }

                Box {
                    TextButton(onClick = { audioMenu = true }) { Text("Audio", color = Green) }
                    DropdownMenu(expanded = audioMenu, onDismissRequest = { audioMenu = false }) {
                        if (audioTracks.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Piste audio par défaut") },
                                onClick = { audioMenu = false }
                            )
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
                                    choice.language?.let {
                                        prefs.edit().putString("preferred_subtitle_language", it).apply()
                                    }
                                    subtitleMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xB8061423))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Button(
                        onClick = { imageMenu = true },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xE613304A),
                            contentColor = Green
                        )
                    ) {
                        Text("FORMAT · $currentFormatLabel", fontWeight = FontWeight.ExtraBold)
                    }
                    DropdownMenu(expanded = imageMenu, onDismissRequest = { imageMenu = false }) {
                        listOf(
                            "zoom" to "Plein écran",
                            "fit" to "Original / Adapter",
                            "16_9" to "16:9",
                            "4_3" to "4:3",
                            "wide" to "Wide / Étirer"
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

                Spacer(Modifier.weight(1f))

                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        try {
                            context.startActivity(intent.setPackage("org.videolan.vlc"))
                        } catch (_: ActivityNotFoundException) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                ) { Text("VLC", color = Green, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
'''

text = text[:start] + player_tail

required = [
    'controller.hide(WindowInsetsCompat.Type.systemBars())',
    'useController = false',
    'controlsVisible',
    'detectTapGestures',
    'FORMAT · $currentFormatLabel',
    'Modifier.fillMaxSize()'
]
missing = [s for s in required if s not in text]
if missing:
    raise SystemExit(f'Missing immersive player patches: {missing}')

path.write_text(text)
