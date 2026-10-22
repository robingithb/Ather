package com.aether.hmi.feature.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.hmi.R
import com.aether.hmi.ui.components.AetherGlyph
import com.aether.hmi.ui.components.Glyph
import com.aether.hmi.ui.theme.AetherColors

@Composable
fun MediaLibrary(state: PlaybackState, onChooseAudio: () -> Unit, onPlayIndex: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (state.queue.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AetherGlyph(Glyph.Music, size = 32.dp)
                Text(stringResource(R.string.nothing_playing), color = AetherColors.TextPrimary)
                Text(stringResource(R.string.choose_audio_hint), fontSize = 12.sp, color = AetherColors.TextSecondary)
                Button(onClick = onChooseAudio, enabled = state.connected) { Text(stringResource(R.string.choose_audio)) }
            }
        }
    } else {
        Column(modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.queue), color = AetherColors.TextPrimary)
                TextButton(onClick = onChooseAudio) { Text(stringResource(R.string.add_audio)) }
            }
            QueueList(state.queue, onPlayIndex, Modifier.weight(1f))
        }
    }
}

@Composable
fun QueueDialog(state: PlaybackState, onDismiss: () -> Unit, onChooseAudio: () -> Unit, onPlayIndex: (Int) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.queue)) },
        text = {
            if (state.queue.isEmpty()) Text(stringResource(R.string.queue_empty))
            else QueueList(state.queue, onPlayIndex, Modifier.heightIn(max = 240.dp))
        }, confirmButton = { TextButton(onClick = onChooseAudio) { Text(stringResource(R.string.add_audio)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun QueueList(queue: List<QueueTrack>, onPlayIndex: (Int) -> Unit, modifier: Modifier) {
    LazyColumn(modifier) {
        itemsIndexed(queue, key = { index, item -> "${item.id}:$index" }) { index, track ->
            Text(track.title ?: stringResource(R.string.unknown_title), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = AetherColors.TextPrimary, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable { onPlayIndex(index) }.padding(12.dp))
        }
    }
}
