package de.danoeh.antennapod.wearos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.net.sync.wearinterface.WearDataPaths
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.ui.common.R as CommonR
import de.danoeh.antennapod.ui.notifications.R as NotificationsR
import de.danoeh.antennapod.wearos.sync.WearMessageSender
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EpisodeDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val episode = IntentCompat.getSerializableExtra(intent, EXTRA_EPISODE, FeedItem::class.java) ?: run {
            finish()
            return
        }
        episode.feed = IntentCompat.getSerializableExtra(intent, EXTRA_FEED, Feed::class.java)
        val viewModel = ViewModelProvider(this, EpisodeDetailViewModel.factory(episode))
            .get(EpisodeDetailViewModel::class.java)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    WearMessageSender.send(this@EpisodeDetailActivity, WearDataPaths.NOW_PLAYING)
                    delay(1.seconds)
                }
            }
        }

        setContent {
            MaterialTheme(colorScheme = dynamicColorScheme(this) ?: MaterialTheme.colorScheme) {
                val uiState by viewModel.uiState.collectAsState()
                EpisodeDetailScreen(
                    uiState = uiState,
                    onPlay = { viewModel.play() },
                    onPause = { viewModel.pause() },
                    onSkipForward = { viewModel.skipForward() },
                    onSkipBackward = { viewModel.skipBackward() },
                    onMore = {
                        val intent = Intent(this, EpisodeActionsActivity::class.java)
                        intent.putExtra(EpisodeActionsActivity.EXTRA_EPISODE, episode)
                        intent.putExtra(EpisodeActionsActivity.EXTRA_FEED, episode.feed)
                        startActivity(intent)
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_EPISODE = "episode"
        const val EXTRA_FEED = "feed"
    }
}

@Composable
fun EpisodeDetailScreen(
    uiState: EpisodeDetailUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onMore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 28.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = uiState.item.feed?.title ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = uiState.item.title,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (uiState.duration > 0) {
                val progress by rememberUpdatedState(uiState.position.toFloat() / uiState.duration.toFloat())
                Column {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = Converter.getDurationStringLong(uiState.position),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = Converter.getDurationStringLong(uiState.duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledTonalIconButton(onClick = onSkipBackward, modifier = Modifier.size(44.dp)) {
                Icon(
                    painter = painterResource(NotificationsR.drawable.ic_notification_fast_rewind),
                    contentDescription = stringResource(CommonR.string.rewind_label),
                    modifier = Modifier.size(24.dp)
                )
            }
            val playIcon = if (uiState.isCurrentlyPlaying) {
                CommonR.drawable.ic_pause_black
            } else {
                R.drawable.ic_phone_play
            }
            val playLabel = if (uiState.isCurrentlyPlaying) {
                CommonR.string.pause_label
            } else {
                CommonR.string.wearos_play_on_phone
            }
            FilledIconButton(
                onClick = if (uiState.isCurrentlyPlaying) onPause else onPlay,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    painter = painterResource(playIcon),
                    contentDescription = stringResource(playLabel),
                    modifier = Modifier.size(28.dp)
                )
            }
            FilledTonalIconButton(onClick = onSkipForward, modifier = Modifier.size(44.dp)) {
                Icon(
                    painter = painterResource(NotificationsR.drawable.ic_notification_fast_forward),
                    contentDescription = stringResource(CommonR.string.fast_forward_label),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        IconButton(onClick = onMore, modifier = Modifier.size(width = 56.dp, height = 32.dp)) {
            Icon(
                painter = painterResource(CommonR.drawable.dots_vertical),
                contentDescription = stringResource(CommonR.string.overflow_more),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(90f)
            )
        }
    }
}
