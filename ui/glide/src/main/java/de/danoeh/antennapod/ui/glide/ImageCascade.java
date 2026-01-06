package de.danoeh.antennapod.ui.glide;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;

public class ImageCascade {
    public final String primaryUrl;
    public final String fallbackUrl;
    public final String errorSeed;
    public final String errorText;
    public final boolean showErrorText;

    public ImageCascade(String primaryUrl, String fallbackUrl, String errorSeed,
                        String errorText, boolean showErrorText) {
        this.primaryUrl = primaryUrl;
        this.fallbackUrl = fallbackUrl;
        this.errorSeed = errorSeed;
        this.errorText = errorText;
        this.showErrorText = showErrorText;
    }

    public static ImageCascade from(Feed feed, boolean showErrorText) {
        return new ImageCascade(
                feed.getImageUrl(),
                null,
                feed.getDownloadUrl(),
                feed.getTitle(),
                showErrorText);
    }

    public static ImageCascade from(Feed feed) {
        return from(feed, true);
    }

    public static ImageCascade from(FeedItem feedItem) {
        return new ImageCascade(
                feedItem.getImageUrl(),
                feedItem.getFeed().getImageUrl(),
                feedItem.getFeed().getDownloadUrl(),
                feedItem.getFeed().getTitle(),
                true);
    }
}
