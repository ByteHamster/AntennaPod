package de.danoeh.antennapod.playback.base;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import de.danoeh.antennapod.net.common.HttpCredentialEncoder;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import com.google.common.collect.ImmutableList;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.model.feed.FeedPreferences;
import de.danoeh.antennapod.model.playback.Playable;
import de.danoeh.antennapod.system.utils.ThreadUtils;

import java.util.List;

public class MediaItemAdapter {
    public static final String MEDIA_ID_FEED_PREFIX = "FeedId:";
    public static final String MEDIA_ID_CONFIRM_STREAMING = "confirm_streaming";
    public static final String KEY_STREAM_URL = "stream_url";
    public static final String KEY_AUTHORIZATION_HEADER = "authorization_header";
    public static final String KEY_FEED_IMAGE = "feed_image";

    /**
     * Create a basic media item without attached metadata.
     * Should be used when initiating playback from outside the service.
     */
    public static MediaItem fromMediaIdStub(long mediaId) {
        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();
        metadataBuilder.setIsPlayable(true);
        metadataBuilder.setIsBrowsable(false);
        return new MediaItem.Builder()
                .setMediaId(String.valueOf(mediaId))
                .setMediaMetadata(metadataBuilder.build())
                .build();
    }

    /**
     * Create a media item and load all its metadata.
     * Do NOT use this method on the main thread.
     */
    public static MediaItem fromPlayable(Playable playable) {
        ThreadUtils.assertNotMainThread();
        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();
        metadataBuilder.setTitle(playable.getEpisodeTitle());
        metadataBuilder.setIsPlayable(true);
        metadataBuilder.setIsBrowsable(false);
        metadataBuilder.setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE);
        String mediaId = "0";
        if (playable instanceof FeedMedia) {
            FeedMedia feedMedia = (FeedMedia) playable;
            mediaId = String.valueOf(feedMedia.getId());
            metadataBuilder.setSubtitle(feedMedia.getFeedTitle());
            metadataBuilder.setArtist(feedMedia.getFeedTitle());
        }
        // The session's ApBitmapLoader resolves this through Glide (embedded covers, placeholders,
        // authenticated http) with a bounded output size. Chromecast fetches the uri itself, and
        // getImageLocation() prefers the http url so that external devices still get artwork.
        String imageLocation = playable.getImageLocation();
        if (imageLocation != null) {
            metadataBuilder.setArtworkUri(Uri.parse(imageLocation));
        }
        Bundle extras = new Bundle();
        extras.putString(KEY_STREAM_URL, playable.getStreamUrl());
        String feedImage = getFeedImageUrl(playable);
        if (feedImage != null && !feedImage.equals(imageLocation)) {
            extras.putString(KEY_FEED_IMAGE, feedImage);
        }
        metadataBuilder.setExtras(extras);
        String localPlaybackUri;
        if (playable.localFileAvailable()) {
            localPlaybackUri = playable.getLocalFileUrl();
        } else {
            localPlaybackUri = playable.getStreamUrl();
        }
        Bundle requestExtras = new Bundle();
        if (!playable.localFileAvailable() && playable instanceof FeedMedia) {
            FeedMedia feedMedia = (FeedMedia) playable;
            if (feedMedia.getItem() != null && feedMedia.getItem().getFeed() != null) {
                FeedPreferences prefs = feedMedia.getItem().getFeed().getPreferences();
                if (prefs != null && !TextUtils.isEmpty(prefs.getUsername())
                        && !TextUtils.isEmpty(prefs.getPassword())) {
                    requestExtras.putString(KEY_AUTHORIZATION_HEADER,
                            HttpCredentialEncoder.encode(prefs.getUsername(), prefs.getPassword(), "ISO-8859-1"));
                }
            }
        }
        return new MediaItem.Builder()
                .setUri(localPlaybackUri != null ? Uri.parse(localPlaybackUri) : null)
                .setMediaId(mediaId)
                .setMediaMetadata(metadataBuilder.build())
                .setRequestMetadata(new MediaItem.RequestMetadata.Builder()
                        .setExtras(requestExtras)
                        .build())
                .build();
    }

    private static String getFeedImageUrl(Playable playable) {
        if (playable instanceof FeedMedia) {
            FeedMedia feedMedia = (FeedMedia) playable;
            if (feedMedia.getItem() != null && feedMedia.getItem().getFeed() != null) {
                return feedMedia.getItem().getFeed().getImageUrl();
            }
        }
        return null;
    }

    public static MediaItem fromFeed(Feed feed) {
        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();
        metadataBuilder.setTitle(feed.getTitle());
        if (feed.getImageUrl() != null) {
            metadataBuilder.setArtworkUri(Uri.parse(feed.getImageUrl()));
        }
        metadataBuilder.setSubtitle(feed.getAuthor());
        metadataBuilder.setIsBrowsable(true);
        metadataBuilder.setIsPlayable(false);
        return new MediaItem.Builder()
                .setMediaId(MEDIA_ID_FEED_PREFIX + feed.getId())
                .setMediaMetadata(metadataBuilder.build())
                .build();
    }

    public static MediaItem from(Context context, String id, String title,
                                      @DrawableRes int iconResId, @Nullable String subtitle) {
        Uri iconUri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.getResources().getResourcePackageName(iconResId))
                .appendPath(context.getResources().getResourceTypeName(iconResId))
                .appendPath(context.getResources().getResourceEntryName(iconResId))
                .build();

        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();
        metadataBuilder.setTitle(title);
        metadataBuilder.setArtworkUri(iconUri);
        if (subtitle != null) {
            metadataBuilder.setSubtitle(subtitle);
        }
        metadataBuilder.setIsBrowsable(true);
        metadataBuilder.setIsPlayable(false);
        return new MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(metadataBuilder.build())
                .build();
    }

    public static MediaItem buildStreamingConfirmationItem(Context context,
                                                              @RawRes int audioResId,
                                                              String title, String description) {
        Uri uri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.getResources().getResourcePackageName(audioResId))
                .appendPath(context.getResources().getResourceTypeName(audioResId))
                .appendPath(context.getResources().getResourceEntryName(audioResId))
                .build();
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(title)
                .setDescription(description)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build();
        return new MediaItem.Builder()
                .setMediaId(MEDIA_ID_CONFIRM_STREAMING)
                .setUri(uri)
                .setMediaMetadata(metadata)
                .build();
    }

    public static ImmutableList<MediaItem> fromItemList(List<FeedItem> feedItems) {
        ImmutableList.Builder<MediaItem> itemsBuilder = ImmutableList.builder();
        for (FeedItem item : feedItems) {
            FeedMedia media = item.getMedia();
            if (media != null && (media.localFileAvailable() || media.getStreamUrl() != null)) {
                itemsBuilder.add(fromPlayable(media));
            }
        }
        return itemsBuilder.build();
    }
}
