package com.ondetv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.ondetv.app.data.MediaEntity

private val ArtworkPanel = Color(0xFF101B14)
private val ArtworkPanelSoft = Color(0xFF162319)
private val ArtworkGreen = Color(0xFF6CFF59)
private val ArtworkText = Color(0xFFF3F6F3)
private val ArtworkMuted = Color(0xFFAEB8AF)

@Composable
fun ArtworkMediaRow(item: MediaEntity, onClick: () -> Unit) {
    val isLive = item.kind == "live"
    val isMovie = item.kind == "vod"
    val artworkUrl = item.logo?.takeIf { it.isNotBlank() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = ArtworkPanel),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val artWidth = if (isLive) 74.dp else 88.dp
            val artHeight = if (isLive) 74.dp else 126.dp

            Box(
                modifier = Modifier
                    .width(artWidth)
                    .height(artHeight)
                    .clip(RoundedCornerShape(if (isLive) 14.dp else 12.dp))
                    .background(ArtworkPanelSoft),
                contentAlignment = Alignment.Center
            ) {
                if (!artworkUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = artworkUrl,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxWidth().height(artHeight),
                        contentScale = if (isLive) ContentScale.Fit else ContentScale.Crop,
                        loading = {
                            CircularProgressIndicator(
                                color = ArtworkGreen,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        error = {
                            ArtworkPlaceholder(isLive = isLive, isMovie = isMovie)
                        },
                        success = {
                            SubcomposeAsyncImageContent()
                        }
                    )
                } else {
                    ArtworkPlaceholder(isLive = isLive, isMovie = isMovie)
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    color = ArtworkText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (isLive) 2 else 3
                )
                item.plot?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(5.dp))
                    Text(it, color = ArtworkMuted, maxLines = 3, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ArtworkPlaceholder(isLive: Boolean, isMovie: Boolean) {
    Icon(
        imageVector = when {
            isLive -> Icons.Filled.LiveTv
            isMovie -> Icons.Filled.Movie
            else -> Icons.Filled.VideoLibrary
        },
        contentDescription = null,
        tint = ArtworkGreen,
        modifier = Modifier.size(if (isLive) 38.dp else 44.dp)
    )
}
