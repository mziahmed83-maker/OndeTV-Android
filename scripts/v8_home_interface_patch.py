from pathlib import Path

path = Path('app/src/main/java/com/ondetv/app/ui/EnhancedOndeTvUi.kt')
text = path.read_text()

# Add icons used by the new home if they are not already present.
if 'import androidx.compose.material.icons.filled.Refresh\n' not in text:
    text = text.replace(
        'import androidx.compose.material.icons.filled.Radio\n',
        'import androidx.compose.material.icons.filled.Radio\nimport androidx.compose.material.icons.filled.Refresh\n'
    )

start = text.find('@Composable\nprivate fun OndeDashboard(')
end = text.find('@Composable\nprivate fun SectionCard(', start)
if start == -1 or end == -1:
    raise SystemExit('OndeDashboard/SectionCard markers not found')

new_dashboard = r'''@Composable
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Personalised NOVA IPTV home, inspired by modern IPTV launchers but not copied.
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1A2E)),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("NOVA IPTV", color = Green, fontWeight = FontWeight.Black, fontSize = 21.sp)
                Text("Votre espace IPTV", color = TextMain, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp)
                Text(serviceName.ifBlank { "Service connecté" }, color = TextMuted, fontSize = 13.sp)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.weight(1.08f).height(300.dp).clickable(onClick = onTv),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0BA7B8)),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(Modifier.background(Color(0x33000000), RoundedCornerShape(20.dp)).padding(14.dp)) {
                        Icon(Icons.Filled.LiveTv, contentDescription = "TV en direct", tint = Color.White)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("TV EN DIRECT", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                        Text("Bouquets, chaînes et logos", color = Color(0xFFE4FBFF), fontSize = 13.sp)
                    }
                }
            }

            Column(Modifier.weight(0.92f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(144.dp).clickable(onClick = onMovies),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE83976)),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(Modifier.background(Color(0x33000000), RoundedCornerShape(18.dp)).padding(12.dp)) {
                            Icon(Icons.Filled.Theaters, contentDescription = "Films", tint = Color.White)
                        }
                        Column {
                            Text("FILMS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 21.sp)
                            Text("VOD & affiches", color = Color(0xFFFFE6EF), fontSize = 12.sp)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().height(144.dp).clickable(onClick = onSeries),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF6B48C8)),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(Modifier.background(Color(0x33000000), RoundedCornerShape(18.dp)).padding(12.dp)) {
                            Icon(Icons.Filled.VideoLibrary, contentDescription = "Séries", tint = Color.White)
                        }
                        Column {
                            Text("SÉRIES", color = Color.White, fontWeight = FontWeight.Black, fontSize = 21.sp)
                            Text("Saisons & épisodes", color = Color(0xFFEDE7FF), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.weight(1f).height(108.dp).clickable(onClick = onRadio),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF146E64)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Radio, contentDescription = "Radio", tint = Color.White)
                    Column {
                        Text("RADIO", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        Text("Stations du service", color = Color(0xFFD9FFF6), fontSize = 12.sp)
                    }
                }
            }

            Card(
                modifier = Modifier.weight(1f).height(108.dp).clickable(enabled = !busy, onClick = onRefresh),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D415D)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Actualiser", tint = Green)
                    Column {
                        Text(if (busy) "ACTUALISATION…" else "ACTUALISER", color = TextMain, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Text("Mettre les listes à jour", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(Modifier.background(PanelSoft, RoundedCornerShape(16.dp)).padding(11.dp)) {
                    Icon(Icons.Filled.Movie, contentDescription = null, tint = Green)
                }
                Column(Modifier.weight(1f)) {
                    Text("LECTEUR NOVA IPTV", color = Green, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    Text("Touchez l'image pendant la lecture pour afficher Audio, CC et Format.", color = TextMain, fontSize = 14.sp)
                    Text("Plein écran • 16:9 • 4:3 • Wide • Original", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

'''

text = text[:start] + new_dashboard + text[end:]

required = [
    'TV EN DIRECT',
    'VOD & affiches',
    'Saisons & épisodes',
    'Stations du service',
    'Mettre les listes à jour',
    'Icons.Filled.Refresh'
]
missing = [x for x in required if x not in text]
if missing:
    raise SystemExit(f'Missing V8 home UI pieces: {missing}')

path.write_text(text)
