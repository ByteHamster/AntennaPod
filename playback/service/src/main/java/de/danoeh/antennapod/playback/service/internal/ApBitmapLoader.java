package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.UnstableApi;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import de.danoeh.antennapod.playback.base.MediaItemAdapter;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Loads session artwork through Glide, bounded to {@link #MAX_DIMENSION_PX} so large covers do not
 * exhaust the heap, and understanding AntennaPod's image locations via the registered model loaders.
 */
@OptIn(markerClass = UnstableApi.class)
public class ApBitmapLoader implements BitmapLoader {
    private static final int MAX_DIMENSION_PX = 1024;
    private static final long TIMEOUT_SECONDS = 10;

    private final Context context;
    private final ListeningExecutorService executor = MoreExecutors.listeningDecorator(
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "ApBitmapLoader");
                thread.setDaemon(true);
                return thread;
            }));

    public ApBitmapLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public boolean supportsMimeType(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    @Override
    public ListenableFuture<Bitmap> decodeBitmap(byte[] data) {
        return executor.submit(() -> Glide.with(context).asBitmap().load(data)
                .submit(MAX_DIMENSION_PX, MAX_DIMENSION_PX).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Override
    public ListenableFuture<Bitmap> loadBitmap(Uri uri) {
        return executor.submit(() -> Glide.with(context).asBitmap().load(uri.toString())
                .submit(MAX_DIMENSION_PX, MAX_DIMENSION_PX).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Nullable
    @Override
    public ListenableFuture<Bitmap> loadBitmapFromMetadata(MediaMetadata metadata) {
        if (metadata.artworkData != null) {
            return decodeBitmap(metadata.artworkData);
        }
        if (metadata.artworkUri == null) {
            return null;
        }
        String feedImage = metadata.extras != null
                ? metadata.extras.getString(MediaItemAdapter.KEY_FEED_IMAGE) : null;
        return executor.submit(() -> {
            RequestBuilder<Bitmap> request = Glide.with(context).asBitmap().load(metadata.artworkUri.toString());
            if (feedImage != null) {
                request = request.error(Glide.with(context).asBitmap().load(feedImage));
            }
            return request.submit(MAX_DIMENSION_PX, MAX_DIMENSION_PX).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
