from pathlib import Path

path = Path('app/src/main/java/com/ondetv/app/ui/EnhancedOndeTvUi.kt')
text = path.read_text()

state_marker = '    var controlsVisible by remember { mutableStateOf(true) }\n'
if state_marker not in text:
    raise SystemExit('controlsVisible marker not found')
text = text.replace(
    state_marker,
    state_marker + '    var italianClassicFrenchCc by remember(item.streamId) { mutableStateOf(false) }\n',
    1
)

insert_marker = '''    LaunchedEffect(controlsVisible, audioMenu, subtitleMenu, imageMenu) {
        if (controlsVisible && !audioMenu && !subtitleMenu && !imageMenu) {
            delay(5000)
            controlsVisible = false
        }
    }
'''
if insert_marker not in text:
    raise SystemExit('auto-hide effect marker not found')

french_effect = '''
    LaunchedEffect(service.id, item.kind, item.categoryId) {
        val categoryName = runCatching {
            repo.dao.categoryName(service.id, item.kind, item.categoryId)
        }.getOrNull().orEmpty()
        italianClassicFrenchCc = categoryName.contains("italien classic", ignoreCase = true) ||
            categoryName.contains("italian classic", ignoreCase = true)

        if (italianClassicFrenchCc) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setPreferredTextLanguage("fr")
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }
    }
'''
text = text.replace(insert_marker, insert_marker + french_effect, 1)

menu_marker = '''                    DropdownMenu(expanded = subtitleMenu, onDismissRequest = { subtitleMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Désactivés") },
'''
if menu_marker not in text:
    raise SystemExit('subtitle menu marker not found')

menu_replacement = '''                    DropdownMenu(expanded = subtitleMenu, onDismissRequest = { subtitleMenu = false }) {
                        if (italianClassicFrenchCc) {
                            DropdownMenuItem(
                                text = { Text("FR · Français automatique si disponible") },
                                onClick = {
                                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                        .setPreferredTextLanguage("fr")
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        .build()
                                    prefs.edit().putBoolean("subtitles_enabled", true).apply()
                                    subtitleMenu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Désactivés") },
'''
text = text.replace(menu_marker, menu_replacement, 1)

required = [
    'italianClassicFrenchCc',
    'categoryName(service.id, item.kind, item.categoryId)',
    'setPreferredTextLanguage("fr")',
    'FR · Français automatique si disponible'
]
missing = [s for s in required if s not in text]
if missing:
    raise SystemExit(f'Missing French CC patches: {missing}')

path.write_text(text)
