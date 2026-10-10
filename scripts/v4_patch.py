from pathlib import Path

path = Path('app/src/main/java/com/ondetv/app/ui/EnhancedOndeTvUi.kt')
text = path.read_text()

text = text.replace('import androidx.compose.foundation.layout.Arrangement\n','import androidx.compose.foundation.layout.Arrangement\nimport androidx.compose.foundation.layout.aspectRatio\n')
text = text.replace('import androidx.compose.material.icons.filled.Rotate90DegreesCcw\n','import androidx.compose.material.icons.filled.Rotate90DegreesCcw\nimport androidx.compose.material.icons.filled.Radio\n')
text = text.replace('import androidx.media3.ui.CaptionStyleCompat\n','import androidx.media3.ui.AspectRatioFrameLayout\nimport androidx.media3.ui.CaptionStyleCompat\n')

text = text.replace('    var settingsOpen by remember { mutableStateOf(false) }\n','    var settingsOpen by remember { mutableStateOf(false) }\n    var radioOnly by remember { mutableStateOf(false) }\n    val visibleCategories = if (radioOnly) categories.filter { c ->\n        val n = c.name.lowercase()\n        n.contains("radio") || n.contains("fm") || n.contains("music") || n.contains("musique") || n.contains("audio")\n    } else categories\n')
text = text.replace('                settingsOpen = false\n                vm.home()\n','                settingsOpen = false\n                radioOnly = false\n                vm.home()\n',1)
text = text.replace('                playing = null\n                vm.home()\n','                playing = null\n                radioOnly = false\n                vm.home()\n',1)
text = text.replace('                IconButton(onClick = vm::home) {\n','                IconButton(onClick = { radioOnly = false; vm.home() }) {\n',1)

old_tabs = '''                SectionCard("TV LIVE", Icons.Filled.LiveTv, kind == "live", Modifier.weight(1f)) { vm.selectKind("live") }
                SectionCard("FILMS", Icons.Filled.Theaters, kind == "vod", Modifier.weight(1f)) { vm.selectKind("vod") }
                SectionCard("SÉRIES", Icons.Filled.VideoLibrary, kind == "series", Modifier.weight(1f)) { vm.selectKind("series") }'''
new_tabs = '''                SectionCard("TV LIVE", Icons.Filled.LiveTv, kind == "live" && !radioOnly, Modifier.weight(1f)) { radioOnly = false; vm.selectKind("live") }
                SectionCard("FILMS", Icons.Filled.Theaters, kind == "vod", Modifier.weight(1f)) { radioOnly = false; vm.selectKind("vod") }
                SectionCard("SÉRIES", Icons.Filled.VideoLibrary, kind == "series", Modifier.weight(1f)) { radioOnly = false; vm.selectKind("series") }
                SectionCard("RADIO", Icons.Filled.Radio, radioOnly, Modifier.weight(1f)) { radioOnly = true; vm.selectKind("live") }'''
text = text.replace(old_tabs,new_tabs)
text = text.replace('                            else -> "Bouquets TV"\n','                            else -> if (radioOnly) "Stations radio" else "Bouquets TV"\n',1)
text = text.replace('                        items(categories, key = CategoryEntity::categoryId) { category ->','                        items(visibleCategories, key = CategoryEntity::categoryId) { category ->',1)
text = text.replace('                        val title = when (kind) { "vod" -> "Films"; "series" -> "Séries"; else -> "Chaînes" }\n','                        val title = when { radioOnly -> "Radios"; kind == "vod" -> "Films"; kind == "series" -> "Séries"; else -> "Chaînes" }\n',1)

text = text.replace('    var color by remember { mutableStateOf(prefs.getInt("cc_color", AndroidColor.WHITE)) }\n','    var color by remember { mutableStateOf(prefs.getInt("cc_color", AndroidColor.WHITE)) }\n    var imageMode by remember { mutableStateOf(prefs.getString("image_mode", "fit") ?: "fit") }\n',1)
text = text.replace('                Text("Sous-titres CC", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 21.sp)\n','''                Text("Lecteur vidéo", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Text("Format d'image", color = TextMuted)
                listOf("fit" to "Original / Adapter", "16_9" to "16:9", "4_3" to "4:3", "wide" to "Wide / Étirer", "zoom" to "Zoom plein écran").forEach { (value, label) ->
                    Card(modifier = Modifier.fillMaxWidth().clickable {
                        imageMode = value
                        prefs.edit().putString("image_mode", value).apply()
                    }, colors = CardDefaults.cardColors(containerColor = if (imageMode == value) PanelSoft else Panel), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = TextMain, modifier = Modifier.weight(1f))
                            if (imageMode == value) Text("✓", color = Green, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text("Sous-titres CC", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 21.sp)
''',1)

text = text.replace('    var subtitleMenu by remember { mutableStateOf(false) }\n    var landscape by remember { mutableStateOf(false) }\n','    var subtitleMenu by remember { mutableStateOf(false) }\n    var imageMenu by remember { mutableStateOf(false) }\n    var imageMode by remember { mutableStateOf(prefs.getString("image_mode", "fit") ?: "fit") }\n    var landscape by remember { mutableStateOf(false) }\n',1)

marker = '    Column(Modifier.fillMaxSize().background(Color.Black)) {\n'
text = text.replace(marker,'''    val resizeMode = when (imageMode) {
        "wide" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
    val forcedRatio = when (imageMode) {
        "16_9" -> 16f / 9f
        "4_3" -> 4f / 3f
        else -> null
    }

''' + marker,1)

rotate_marker = '''            IconButton(onClick = { landscape = !landscape }) {
                Icon(Icons.Filled.Rotate90DegreesCcw, contentDescription = "Rotation", tint = if (landscape) TextMain else Green)
            }
'''
text = text.replace(rotate_marker,'''            Box {
                TextButton(onClick = { imageMenu = true }) { Text("Image", color = Green) }
                DropdownMenu(expanded = imageMenu, onDismissRequest = { imageMenu = false }) {
                    listOf("fit" to "Original", "16_9" to "16:9", "4_3" to "4:3", "wide" to "Wide", "zoom" to "Zoom").forEach { (value, label) ->
                        DropdownMenuItem(text = { Text((if (imageMode == value) "✓ " else "") + label) }, onClick = {
                            imageMode = value
                            prefs.edit().putString("image_mode", value).apply()
                            imageMenu = false
                        })
                    }
                }
            }
''' + rotate_marker,1)

text = text.replace('                    keepScreenOn = true\n                    subtitleView?.setStyle(','                    keepScreenOn = true\n                    this.resizeMode = resizeMode\n                    subtitleView?.setStyle(',1)
text = text.replace('                it.player = player\n                it.subtitleView?.setStyle(','                it.player = player\n                it.resizeMode = resizeMode\n                it.subtitleView?.setStyle(',1)
text = text.replace('            modifier = Modifier.weight(1f).fillMaxWidth()\n','            modifier = if (forcedRatio != null) Modifier.fillMaxWidth().aspectRatio(forcedRatio) else Modifier.weight(1f).fillMaxWidth()\n',1)

required = ['Icons.Filled.Radio','AspectRatioFrameLayout.RESIZE_MODE_FILL','image_mode','visibleCategories','Stations radio','aspectRatio']
missing = [s for s in required if s not in text]
if missing:
    raise SystemExit(f'Missing patches: {missing}')
path.write_text(text)
