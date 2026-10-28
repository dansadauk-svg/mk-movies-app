package ng.com.mkmovies.app;

import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Starts downloads outside the WebView and keeps a real Android progress
 * notification visible while DownloadManager does the resumable transfer.
 */
public class MovieDownloadService extends Service {
    private static final String TAG = "MKMovieDownload";
    private static final String ACTION_START = "ng.com.mkmovies.app.action.START_MOVIE_DOWNLOAD";
    private static final String EXTRA_URL = "url";
    private static final String EXTRA_FILENAME = "filename";
    private static final String EXTRA_MIME = "mime";
    private static final String EXTRA_USER_AGENT = "user_agent";
    private static final String EXTRA_COOKIES = "cookies";
    private static final String EXTRA_REFERER = "referer";

    public static final String CHANNEL_ID = "mk_movie_downloads_alerting_v2";
    public static final String COMPLETE_CHANNEL_ID = "mk_movie_download_complete_alerting_v2";
    private static final int FOREGROUND_NOTIFICATION_ID = 4301;
    private static final int FAILURE_NOTIFICATION_ID = 4302;

    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final AtomicInteger activeDownloads = new AtomicInteger(0);
    private NotificationManager notificationManager;

    public static void start(
            Context context,
            String url,
            String filename,
            String mimeType,
            String userAgent,
            String cookies,
            String referer
    ) {
        Intent intent = new Intent(context, MovieDownloadService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_FILENAME, filename);
        intent.putExtra(EXTRA_MIME, mimeType);
        intent.putExtra(EXTRA_USER_AGENT, userAgent);
        intent.putExtra(EXTRA_COOKIES, cookies);
        intent.putExtra(EXTRA_REFERER, referer);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START.equals(intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        final String url = safe(intent.getStringExtra(EXTRA_URL));
        final String filename = safe(intent.getStringExtra(EXTRA_FILENAME));
        final String mimeType = safe(intent.getStringExtra(EXTRA_MIME));
        final String userAgent = safe(intent.getStringExtra(EXTRA_USER_AGENT));
        final String cookies = safe(intent.getStringExtra(EXTRA_COOKIES));
        final String referer = safe(intent.getStringExtra(EXTRA_REFERER));

        if (url.isEmpty() || filename.isEmpty()) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        startForeground(
                FOREGROUND_NOTIFICATION_ID,
                buildProgressNotification(filename, 0, 0, "Preparing download…")
        );
        activeDownloads.incrementAndGet();
        workers.execute(() -> enqueueAndMonitor(
                url, filename, mimeType, userAgent, cookies, referer, startId
        ));
        return START_NOT_STICKY;
    }

    private void enqueueAndMonitor(
            String url,
            String filename,
            String mimeType,
            String userAgent,
            String cookies,
            String referer,
            int startId
    ) {
        try {
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("Android DownloadManager is unavailable");

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(filename);
            request.setDescription("Downloading from MK Movies");
            request.setMimeType(mimeType.isEmpty() ? "application/octet-stream" : mimeType);
            request.setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE
            );
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.allowScanningByMediaScanner();

            // Our foreground notification supplies progress. DownloadManager supplies the
            // final, clickable Android completion notification without a duplicate progress row.
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION
            );

            if (!userAgent.isEmpty()) request.addRequestHeader("User-Agent", userAgent);
            if (!cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);
            if (!referer.isEmpty()) request.addRequestHeader("Referer", referer);
            request.addRequestHeader("Accept", "video/*,application/octet-stream,*/*");

            try {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            } catch (Exception publicDirectoryError) {
                Log.w(TAG, "Public Downloads unavailable; using app Downloads", publicDirectoryError);
                request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, filename);
            }

            long downloadId = manager.enqueue(request);
            monitor(manager, downloadId, filename);
        } catch (Exception e) {
            Log.e(TAG, "Could not start movie download", e);
            showFailure(filename, "Download could not start. Tap the movie and try again.");
        } finally {
            if (activeDownloads.decrementAndGet() <= 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                } else {
                    stopForeground(true);
                }
                stopSelf();
            }
        }
    }

    private void monitor(DownloadManager manager, long downloadId, String filename) {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        while (!Thread.currentThread().isInterrupted()) {
            Cursor cursor = null;
            try {
                cursor = manager.query(query);
                if (cursor == null || !cursor.moveToFirst()) {
                    showFailure(filename, "Download was removed before it finished.");
                    return;
                }

                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                long downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                ));
                long total = cursor.getLong(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                ));

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    showSuccess(downloadId, filename);
                    return;
                }
                if (status == DownloadManager.STATUS_FAILED) {
                    int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                    Log.w(TAG, "Download failed. DownloadManager reason=" + reason);
                    showFailure(filename, "Download failed. Check your connection and try again.");
                    return;
                }

                String state;
                if (status == DownloadManager.STATUS_PAUSED) {
                    state = "Download paused — waiting to continue";
                } else if (status == DownloadManager.STATUS_PENDING) {
                    state = "Waiting to start…";
                } else {
                    state = formatProgress(downloaded, total);
                }

                if (notificationManager != null) {
                    notificationManager.notify(
                            FOREGROUND_NOTIFICATION_ID,
                            buildProgressNotification(filename, downloaded, total, state)
                    );
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not read download progress", e);
            } finally {
                if (cursor != null) cursor.close();
            }

            try {
                Thread.sleep(900L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private android.app.Notification buildProgressNotification(
            String filename,
            long downloaded,
            long total,
            String state
    ) {
        Intent downloadsIntent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                downloadsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_mk)
                .setContentTitle("Downloading " + displayTitle(filename))
                .setContentText(state)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_VIBRATE)
                .setOnlyAlertOnce(true)
                .setOngoing(true);

        if (total > 0L) {
            int progress = (int) Math.min(100L, (downloaded * 100L) / total);
            builder.setProgress(100, progress, false);
        } else {
            builder.setProgress(0, 0, true);
        }
        return builder.build();
    }

    private void showFailure(String filename, String message) {
        if (notificationManager == null) return;
        Intent retryIntent = new Intent(this, MainActivity.class);
        retryIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                1,
                retryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        notificationManager.notify(
                FAILURE_NOTIFICATION_ID,
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_mk)
                        .setContentTitle("Couldn’t download " + displayTitle(filename))
                        .setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .build()
        );
    }

    private void showSuccess(long downloadId, String filename) {
        if (notificationManager == null) return;
        Intent downloadsIntent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) (downloadId & 0x7fffffff),
                downloadsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        int notificationId = 5000 + (int) (Math.abs(downloadId) % 1000000L);
        notificationManager.notify(
                notificationId,
                new NotificationCompat.Builder(this, COMPLETE_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_mk)
                        .setContentTitle("Movie downloaded")
                        .setContentText(filename)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(filename))
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setCategory(NotificationCompat.CATEGORY_STATUS)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .build()
        );
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || notificationManager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Movie downloads",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Movie download progress and status from MK Movies.");
        channel.setShowBadge(false);
        channel.enableVibration(true);
        channel.setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
        );
        notificationManager.createNotificationChannel(channel);

        NotificationChannel completedChannel = new NotificationChannel(
                COMPLETE_CHANNEL_ID,
                "Completed movie downloads",
                NotificationManager.IMPORTANCE_HIGH
        );
        completedChannel.setDescription("Alerts when an MK Movies download has finished.");
        completedChannel.enableVibration(true);
        completedChannel.setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
        );
        notificationManager.createNotificationChannel(completedChannel);
    }

    private String displayTitle(String filename) {
        if (filename == null) return "movie";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String formatProgress(long downloaded, long total) {
        if (total <= 0L) return readableBytes(downloaded) + " downloaded";
        int percent = (int) Math.min(100L, (downloaded * 100L) / total);
        return percent + "% • " + readableBytes(downloaded) + " of " + readableBytes(total);
    }

    private String readableBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024.0) return String.format(java.util.Locale.US, "%.1f MB", mb);
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        workers.shutdownNow();
        super.onDestroy();
    }
}
