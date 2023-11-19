package de.danoeh.antennapod.ui.echo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import de.danoeh.antennapod.core.feed.util.PlaybackSpeedUtils;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.StatisticsItem;
import de.danoeh.antennapod.core.util.Converter;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.preferences.UserPreferences;
import de.danoeh.antennapod.ui.echo.databinding.EchoActivityBinding;
import de.danoeh.antennapod.ui.echo.databinding.EchoBaseBinding;
import de.danoeh.antennapod.ui.echo.databinding.EchoSubscriptionsBinding;
import de.danoeh.antennapod.ui.echo.screens.BubbleScreen;
import de.danoeh.antennapod.ui.echo.screens.FinalShareScreen;
import de.danoeh.antennapod.ui.echo.screens.RotatingSquaresScreen;
import de.danoeh.antennapod.ui.echo.screens.StripesScreen;
import de.danoeh.antennapod.ui.echo.screens.WaveformScreen;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class EchoActivity extends AppCompatActivity {
    private static final String TAG = "EchoActivity";
    private static final int NUM_SCREENS = 7;

    private EchoActivityBinding viewBinding;
    private int currentScreen = -1;
    private boolean progressPaused = false;
    private float progress = 0;
    private Drawable currentDrawable;
    private EchoProgress echoProgress;
    private Disposable redrawTimer;
    private long timeTouchDown;
    private long lastFrame;
    private Disposable disposable;

    private long totalTime = 0;
    private int totalPodcasts = 0;
    private int playedPodcasts = 0;
    private int queueNumEpisodes = 0;
    private long queueTimeLeft = 0;
    private long timeBetweenReleaseAndPlay = 0;
    private long oldestDate = 0;
    private final ArrayList<Pair<String, Drawable>> favoritePods = new ArrayList<>();

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        viewBinding = EchoActivityBinding.inflate(getLayoutInflater());
        viewBinding.closeButton.setOnClickListener(v -> finish());
        viewBinding.echoImage.setOnTouchListener((v, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                progressPaused = true;
                timeTouchDown = System.currentTimeMillis();
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                progressPaused = false;
                if (timeTouchDown + 500 > System.currentTimeMillis()) {
                    int newScreen = (currentScreen + 1) % NUM_SCREENS;
                    progress = newScreen;
                    echoProgress.setProgress(progress);
                    loadScreen(newScreen);
                }
            }
            return true;
        });
        echoProgress = new EchoProgress(NUM_SCREENS);
        viewBinding.echoProgressImage.setImageDrawable(echoProgress);
        setContentView(viewBinding.getRoot());
        loadScreen(0);
        loadStatistics();
    }

    private void share() {
        try {
            Bitmap bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            currentDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            currentDrawable.draw(canvas);
            viewBinding.echoImage.setImageDrawable(null);
            viewBinding.echoImage.setImageDrawable(currentDrawable);
            File file = new File(UserPreferences.getDataFolder(null), "AntennaPodEcho.png");
            FileOutputStream stream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream);
            stream.close();

            Uri fileUri = FileProvider.getUriForFile(this, getString(R.string.provider_authority), file);
            new ShareCompat.IntentBuilder(this)
                    .setType("image/png")
                    .addStream(fileUri)
                    .setText(getString(R.string.echo_share))
                    .setChooserTitle(R.string.share_file_label)
                    .startChooser();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        redrawTimer = Flowable.timer(10, TimeUnit.MILLISECONDS)
                .observeOn(Schedulers.io())
                .repeat()
                .subscribe(i -> {
                    if (progressPaused) {
                        return;
                    }
                    viewBinding.echoImage.postInvalidate();
                    if (progress >= NUM_SCREENS - 0.001f) {
                        return;
                    }
                    long timePassed = System.currentTimeMillis() - lastFrame;
                    lastFrame = System.currentTimeMillis();
                    if (timePassed > 500) {
                        timePassed = 0;
                    }
                    progress = Math.min(NUM_SCREENS - 0.001f, progress + timePassed / 10000.0f);
                    echoProgress.setProgress(progress);
                    viewBinding.echoProgressImage.postInvalidate();
                    loadScreen((int) progress);
                });
    }

    @Override
    protected void onStop() {
        super.onStop();
        redrawTimer.dispose();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    private void loadScreen(int screen) {
        if (screen == currentScreen) {
            return;
        }
        currentScreen = screen;
        runOnUiThread(() -> {
            viewBinding.screenContainer.removeAllViews();
            switch (currentScreen) {
                case 0:
                    EchoBaseBinding introBinding = EchoBaseBinding.inflate(getLayoutInflater());
                    introBinding.aboveLabel.setText(R.string.echo_intro_your_year);
                    introBinding.largeLabel.setText(String.format(getEchoLanguage(), "%d", 2023));
                    introBinding.belowLabel.setText(R.string.echo_intro_in_podcasts);
                    introBinding.smallLabel.setText(R.string.echo_intro_locally);
                    introBinding.echoLogo.setVisibility(View.VISIBLE);
                    viewBinding.screenContainer.addView(introBinding.getRoot());
                    currentDrawable = new BubbleScreen();
                    break;
                case 1:
                    EchoBaseBinding hoursPlayedBinding = EchoBaseBinding.inflate(getLayoutInflater());
                    hoursPlayedBinding.aboveLabel.setText(R.string.echo_hours_this_year);
                    hoursPlayedBinding.largeLabel.setText(String.format(getEchoLanguage(), "%d", totalTime / 3600));
                    hoursPlayedBinding.belowLabel.setText(getResources()
                            .getQuantityString(R.plurals.echo_hours_podcasts, playedPodcasts, playedPodcasts));
                    hoursPlayedBinding.smallLabel.setText("");
                    viewBinding.screenContainer.addView(hoursPlayedBinding.getRoot());
                    currentDrawable = new WaveformScreen();
                    break;
                case 2:
                    EchoBaseBinding queueBinding = EchoBaseBinding.inflate(getLayoutInflater());
                    queueBinding.largeLabel.setText(String.format(getEchoLanguage(), "%d", queueTimeLeft / 3600));
                    queueBinding.belowLabel.setText(getResources().getQuantityString(
                            R.plurals.echo_queue_hours_waiting, queueNumEpisodes, queueNumEpisodes));
                    int daysUntil2024 = 31 - Calendar.getInstance().get(Calendar.DAY_OF_MONTH) + 1;
                    double hoursPerDay = (double) (queueTimeLeft / 3600) / daysUntil2024;
                    if (hoursPerDay < 1.5) {
                        queueBinding.aboveLabel.setText(R.string.echo_queue_title_clean);
                        queueBinding.smallLabel.setText(getString(R.string.echo_queue_hours_clean, hoursPerDay));
                    } else if (hoursPerDay <= 24) {
                        queueBinding.aboveLabel.setText(R.string.echo_queue_title_many);
                        queueBinding.smallLabel.setText(getString(R.string.echo_queue_hours_normal, hoursPerDay));
                    } else {
                        queueBinding.aboveLabel.setText(R.string.echo_queue_title_many);
                        queueBinding.smallLabel.setText(getString(R.string.echo_queue_hours_much, hoursPerDay));
                    }
                    viewBinding.screenContainer.addView(queueBinding.getRoot());
                    currentDrawable = new StripesScreen();
                    break;
                case 3:
                    EchoBaseBinding listenedAfterBinding = EchoBaseBinding.inflate(getLayoutInflater());
                    listenedAfterBinding.aboveLabel.setText(R.string.echo_listened_after_title);
                    if (timeBetweenReleaseAndPlay <= 1000L * 3600 * 24 * 2.5) {
                        listenedAfterBinding.largeLabel.setText(R.string.echo_listened_after_emoji_run);
                        listenedAfterBinding.belowLabel.setText(R.string.echo_listened_after_comment_addict);
                    } else {
                        listenedAfterBinding.largeLabel.setText(R.string.echo_listened_after_emoji_yoga);
                        listenedAfterBinding.belowLabel.setText(R.string.echo_listened_after_comment_easy);
                    }
                    listenedAfterBinding.smallLabel.setText(getString(R.string.echo_listened_after_time,
                        Converter.getDurationStringLocalized(
                                getLocalizedResources(this, getEchoLanguage()), timeBetweenReleaseAndPlay)));
                    viewBinding.screenContainer.addView(listenedAfterBinding.getRoot());
                    currentDrawable = new RotatingSquaresScreen();
                    break;
                case 4:
                    EchoBaseBinding hoarderBinding = EchoBaseBinding.inflate(getLayoutInflater());
                    hoarderBinding.aboveLabel.setText(R.string.echo_hoarder_title);
                    int percentagePlayed = (int) (100.0 * playedPodcasts / totalPodcasts);
                    if (percentagePlayed >= 75) {
                        hoarderBinding.largeLabel.setText(R.string.echo_hoarder_emoji_check);
                        hoarderBinding.belowLabel.setText(R.string.echo_hoarder_subtitle_check);
                        hoarderBinding.smallLabel.setText(getString(R.string.echo_hoarder_comment_check,
                                percentagePlayed, totalPodcasts));
                    } else {
                        hoarderBinding.largeLabel.setText(R.string.echo_hoarder_emoji_cabinet);
                        hoarderBinding.belowLabel.setText(R.string.echo_hoarder_subtitle_hoarder);
                        hoarderBinding.smallLabel.setText(getString(R.string.echo_hoarder_comment_hoarder,
                                percentagePlayed, totalPodcasts));
                    }
                    viewBinding.screenContainer.addView(hoarderBinding.getRoot());
                    currentDrawable = new StripesScreen();
                    break;
                case 5:
                    EchoBaseBinding thanksBinding = EchoBaseBinding.inflate(getLayoutInflater());
                    if (oldestDate < jan1()) {
                        thanksBinding.largeLabel.setText(R.string.echo_thanks_old);
                        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM yyyy", getEchoLanguage());
                        String dateFrom = dateFormat.format(new Date(oldestDate));
                        thanksBinding.belowLabel.setText(getString(R.string.echo_thanks_we_are_glad_old, dateFrom));
                    } else {
                        thanksBinding.largeLabel.setText(R.string.echo_thanks_new);
                        thanksBinding.belowLabel.setText(R.string.echo_thanks_we_are_glad_new);
                    }
                    thanksBinding.smallLabel.setText(R.string.echo_thanks_now_favorite);
                    viewBinding.screenContainer.addView(thanksBinding.getRoot());
                    currentDrawable = new RotatingSquaresScreen();
                    break;
                case 6:
                    EchoSubscriptionsBinding subsBinding = EchoSubscriptionsBinding.inflate(getLayoutInflater());
                    subsBinding.shareButton.setOnClickListener(v -> share());
                    viewBinding.screenContainer.addView(subsBinding.getRoot());
                    currentDrawable = new FinalShareScreen(this, favoritePods);
                    break;
                default: // Keep
            }
            viewBinding.echoImage.setImageDrawable(currentDrawable);
        });
    }

    private Locale getEchoLanguage() {
        boolean hasTranslation = !getString(R.string.echo_listened_after_title)
                .equals(getLocalizedResources(this, Locale.US).getString(R.string.echo_listened_after_title));
        if (hasTranslation) {
            return Locale.getDefault();
        } else {
            return Locale.US;
        }
    }

    @NonNull
    Resources getLocalizedResources(Context context, Locale desiredLocale) {
        Configuration conf = context.getResources().getConfiguration();
        conf = new Configuration(conf);
        conf.setLocale(desiredLocale);
        Context localizedContext = context.createConfigurationContext(conf);
        return localizedContext.getResources();
    }

    private long jan1() {
        Calendar date = Calendar.getInstance();
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        date.set(Calendar.DAY_OF_MONTH, 1);
        date.set(Calendar.MONTH, 0);
        date.set(Calendar.YEAR, 2023);
        return date.getTimeInMillis();
    }

    private void loadStatistics() {
        if (disposable != null) {
            disposable.dispose();
        }
        long timeFilterFrom = jan1();
        long timeFilterTo = Long.MAX_VALUE;
        disposable = Observable.fromCallable(
                () -> {
                    DBReader.StatisticsResult statisticsData = DBReader.getStatistics(
                            false, timeFilterFrom, timeFilterTo);
                    Collections.sort(statisticsData.feedTime, (item1, item2) ->
                            Long.compare(item2.timePlayed, item1.timePlayed));

                    favoritePods.clear();
                    for (int i = 0; i < 5 && i < statisticsData.feedTime.size(); i++) {
                        BitmapDrawable cover = new BitmapDrawable(getResources(), (Bitmap) null);
                        try {
                            final int size = 500;
                            final int radius = (i == 0) ? (size / 16) : (size / 8);
                            cover = new BitmapDrawable(getResources(), Glide.with(this)
                                    .asBitmap()
                                    .load(statisticsData.feedTime.get(i).feed.getImageUrl())
                                    .apply(new RequestOptions()
                                            .fitCenter()
                                            .transform(new RoundedCorners(radius)))
                                    .submit(size, size)
                                    .get(1, TimeUnit.SECONDS));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        favoritePods.add(new Pair<>(statisticsData.feedTime.get(i).feed.getTitle(), cover));
                    }

                    totalPodcasts = statisticsData.feedTime.size();
                    playedPodcasts = 0;
                    totalTime = 0;
                    for (StatisticsItem item : statisticsData.feedTime) {
                        totalTime += item.timePlayed;
                        if (item.timePlayed > 0) {
                            playedPodcasts++;
                        }
                    }

                    List<FeedItem> queue = DBReader.getQueue();
                    queueNumEpisodes = queue.size();
                    queueTimeLeft = 0;
                    for (FeedItem item : queue) {
                        float playbackSpeed = 1;
                        if (UserPreferences.timeRespectsSpeed()) {
                            playbackSpeed = PlaybackSpeedUtils.getCurrentPlaybackSpeed(item.getMedia());
                        }
                        if (item.getMedia() != null) {
                            long itemTimeLeft = item.getMedia().getDuration() - item.getMedia().getPosition();
                            queueTimeLeft += itemTimeLeft / playbackSpeed;
                        }
                    }
                    queueTimeLeft /= 1000;

                    timeBetweenReleaseAndPlay = DBReader.getTimeBetweenReleaseAndPlayback(timeFilterFrom, timeFilterTo);
                    oldestDate = statisticsData.oldestDate;
                    return statisticsData;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> { }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }
}