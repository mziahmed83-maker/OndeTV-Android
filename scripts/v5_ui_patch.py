from pathlib import Path

path = Path('app/src/main/java/com/ondetv/app/ui/EnhancedOndeTvUi.kt')
text = path.read_text()

# Personalize palette: deep navy + cyan/teal, inspired by the references but distinct.
text = text.replace('private val Bg = Color(0xFF07110B)\nprivate val Panel = Color(0xFF101B14)\nprivate val PanelSoft = Color(0xFF162319)\nprivate val Green = Color(0xFF6CFF59)\nprivate val TextMain = Color(0xFFF3F6F3)\nprivate val TextMuted = Color(0xFFAEB8AF)\nprivate val Border = Color(0xFF33473A)\n',
'''private val Bg = Color(0xFF061423)
private val Panel = Color(0xFF0D2135)
private val PanelSoft = Color(0xFF13304A)
private val Green = Color(0xFF44E3D0)
private val TextMain = Color(0xFFF4F8FC)
private val TextMuted = Color(0xFFA8B7C8)
private val Border = Color(0xFF2B4D69)
private val LiveCard = Color(0xFF0D6E86)
private val MovieCard = Color(0xFF3F3BA9)
private val SeriesCard = Color(0xFF70278F)
private val RadioCard = Color(0xFF166D62)
''')

# Add dashboard state after the radio mode introduced by v4.
text = text.replace(
    '    var radioOnly by remember { mutableStateOf(false) }\n',
    '    var radioOnly by remember { mutableStateOf(false) }\n    var dashboard by remember { mutableStateOf(true) }\n',
    1
)

# Home from settings/player must really return to the dashboard.
text = text.replace(
    '                settingsOpen = false\n                radioOnly = false\n                vm.home()\n',
    '                settingsOpen = false\n                radioOnly = false\n                dashboard = true\n                vm.home()\n',
    1
)
text = text.replace(
    '                playing = null\n                radioOnly = false\n                vm.home()\n',
    '                playing = null\n                radioOnly = false\n                dashboard = true\n                vm.home()\n',
    1
)
text = text.replace(
    '                IconButton(onClick = { radioOnly = false; vm.home() }) {\n',
    '                IconButton(onClick = { radioOnly = false; dashboard = true; vm.home() }) {\n',
    1
)

# Back button: go back inside the app instead of leaving when possible.
old_back = '''    BackHandler(enabled = true) {
        when {
            selectedSeries != null || selectedCategory != null -> vm.backOneLevel()
            kind != "live" -> vm.home()
            else -> Unit
        }
    }
'''
new_back = '''    BackHandler(enabled = true) {
        when {
            selectedSeries != null || selectedCategory != null -> vm.backOneLevel()
            !dashboard -> { radioOnly = false; dashboard = true; vm.home() }
            else -> Unit
        }
    }
'''
text = text.replace(old_back, new_back, 1)

# Replace the compact selector row with a real home dashboard and keep compact tabs while browsing.
old_tabs = '''            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionCard("TV LIVE", Icons.Filled.LiveTv, kind == "live" && !radioOnly, Modifier.weight(1f)) { radioOnly = false; vm.selectKind("live") }
                SectionCard("FILMS", Icons.Filled.Theaters, kind == "vod", Modifier.weight(1f)) { radioOnly = false; vm.selectKind("vod") }
                SectionCard("SÉRIES", Icons.Filled.VideoLibrary, kind == "series", Modifier.weight(1f)) { radioOnly = false; vm.selectKind("series") }
                SectionCard("RADIO", Icons.Filled.Radio, radioOnly, Modifier.weight(1f)) { radioOnly = true; vm.selectKind("live") }
            }
'''
new_tabs = '''            if (dashboard) {
                OndeDashboard(
                    serviceName = service.name,
                    busy = busy,
                    onRefresh = vm::refresh,
                    onTv = { dashboard = false; radioOnly = false; vm.selectKind("live") },
                    onMovies = { dashboard = false; radioOnly = false; vm.selectKind("vod") },
                    onSeries = { dashboard = false; radioOnly = false; vm.selectKind("series") },
                    onRadio = { dashboard = false; radioOnly = true; vm.selectKind("live") }
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionCard("TV", Icons.Filled.LiveTv, kind == "live" && !radioOnly, Modifier.weight(1f)) { radioOnly = false; vm.selectKind("live") }
                    SectionCard("FILMS", Icons.Filled.Theaters, kind == "vod", Modifier.weight(1f)) { radioOnly = false; vm.selectKind("vod") }
                    SectionCard("SÉRIES", Icons.Filled.VideoLibrary, kind == "series", Modifier.weight(1f)) { radioOnly = false; vm.selectKind("series") }
                    SectionCard("RADIO", Icons.Filled.Radio, radioOnly, Modifier.weight(1f)) { radioOnly = true; vm.selectKind("live") }
                }
            }
'''
if old_tabs not in text:
    raise SystemExit('Main section row not found after v4 patch')
text = text.replace(old_tabs, new_tabs, 1)

# Avoid showing bouquet lists underneath the home dashboard.
text = text.replace('            when {\n                selectedSeries != null -> {', '            when {\n                dashboard -> Unit\n                selectedSeries != null -> {', 1)

# Settings header and subtitle closer to the provided reference, with OndeTV identity.
text = text.replace('                Text("Réglages", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))',
                    '                Text("Paramètres", color = TextMain, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, modifier = Modifier.weight(1f))', 1)
text = text.replace('            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {\n',
'''            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Personnalisez la lecture, l’image, l’audio et les sous-titres.", color = TextMuted, fontSize = 16.sp)
''', 1)

# Add a full, modern dashboard inspired by the user's reference screenshots.
marker = '@Composable\nprivate fun SectionCard('
if marker not in text:
    raise SystemExit('SectionCard marker not found')

dashboard_code = r'''@Composable
private fun OndeDashboard(
    serviceName: String,
    busy: Boolean,
    onRefresh: () -> Unit,
    onTv: () -> Unit,
    onMovies: () -> Unit,
    onSeries: () -> Unit,
    onRadio: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedButton(
            onClick = onRefresh,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(if (busy) "Actualisation…" else "↻  Actualiser les listes", color = Green, fontWeight = FontWeight.Bold)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("VOTRE ESPACE", color = Green, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Text("Regardez ce que\nvous aimez.", color = TextMain, fontWeight = FontWeight.Black, fontSize = 30.sp)
                Text(serviceName.ifBlank { "Onde TV" }, color = TextMuted, fontSize = 15.sp)
                Text("TV • Films • Séries • Radios", color = Green, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }

        Text("CHOISIR UN UNIVERS", color = Green, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UniverseCard("TV EN DIRECT", "Vos bouquets et chaînes", Icons.Filled.LiveTv, LiveCard, Modifier.weight(1f), onTv)
            UniverseCard("FILMS", "Affiches et VOD", Icons.Filled.Theaters, MovieCard, Modifier.weight(1f), onMovies)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UniverseCard("SÉRIES", "Saisons et épisodes", Icons.Filled.VideoLibrary, SeriesCard, Modifier.weight(1f), onSeries)
            UniverseCard("RADIO", "Stations de votre service", Icons.Filled.Radio, RadioCard, Modifier.weight(1f), onRadio)
        }

        Card(colors = CardDefaults.cardColors(containerColor = PanelSoft), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("LECTEUR ONDE TV", color = Green, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                Text("Format d’image accessible pendant la lecture", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Plein écran • Original • 16:9 • 4:3 • Wide", color = TextMuted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun UniverseCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(170.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                Modifier.background(Color(0x55000000), RoundedCornerShape(18.dp)).padding(12.dp)
            ) {
                Icon(icon, contentDescription = title, tint = Color.White)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text(subtitle, color = Color(0xFFD8E6EF), fontSize = 12.sp)
            }
        }
    }
}

'''
text = text.replace(marker, dashboard_code + marker, 1)

required = ['OndeDashboard(', 'UniverseCard(', 'dashboard -> Unit', 'FORMAT · $currentFormatLabel', 'Paramètres']
missing = [x for x in required if x not in text]
if missing:
    raise SystemExit(f'Missing v5 UI patches: {missing}')

path.write_text(text)
