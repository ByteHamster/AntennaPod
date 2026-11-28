package de.danoeh.antennapod.wearos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.net.sync.wearinterface.WearDataPaths
import de.danoeh.antennapod.ui.common.DateFormatter
import de.danoeh.antennapod.ui.common.R as CommonR
import de.danoeh.antennapod.wearos.composable.ListItem
import de.danoeh.antennapod.wearos.sync.WearMessageSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EpisodeActionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val episode = IntentCompat.getSerializableExtra(intent, EXTRA_EPISODE, FeedItem::class.java) ?: run {
            finish()
            return
        }
        episode.feed = IntentCompat.getSerializableExtra(intent, EXTRA_FEED, Feed::class.java)

        setContent {
            MaterialTheme(colorScheme = dynamicColorScheme(this) ?: MaterialTheme.colorScheme) {
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 24.dp),
                    autoCentering = null
                ) {
                    item {
                        Text(
                            text = episode.feed?.title ?: "",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                    item {
                        Text(
                            text = DateFormatter.formatAbbrev(LocalContext.current, episode.getPubDate()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    item {
                        Text(
                            text = episode.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    item {
                        ListItem(
                            text = stringResource(CommonR.string.wearos_open_on_phone),
                            iconRes = CommonR.drawable.ic_phone_black,
                            onClick = {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    WearMessageSender.send(
                                        this@EpisodeActionsActivity,
                                        WearDataPaths.openOnPhonePath(episode.id)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_EPISODE = "episode"
        const val EXTRA_FEED = "feed"
    }
}
