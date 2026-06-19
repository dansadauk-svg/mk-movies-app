package ng.com.mkmovies.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MkFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "MKMoviesFCMService";
    private static final String CHANNEL_ID = "mk_movies_channel";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        MkTokenRegistrar.registerTokenAsync(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        Map<String, String> data = message.getData();

        String title = value(data, "title", "MK Movies");
        String body = value(data, "body", "New update available");
        String url = value(data, "url", "https://mkmovies.com.ng/");

        if (message.getNotification() != null) {
            if (message.getNotification().getTitle() != null) title = message.getNotification().getTitle();
            if (message.getNotification().getBody() != null) body = message.getNotification().getBody();
        }

        showNotification(title, body, url);
    }

    private String value(Map<String, String> data, String key, String fallback) {
        String v = data != null ? data.get(key) : null;
        return (v == null || v.trim().isEmpty()) ? fallback : v;
    }

    private void showNotification(String title, String body, String url) {
        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Notification permission not granted; cannot show notification.");
            return;
        }

        Intent intent = new Intent(this, com.google.androidbrowserhelper.trusted.LauncherActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url == null || url.isEmpty() ? "https://mkmovies.com.ng/" : url));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) System.currentTimeMillis(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_mk)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(this).notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "MK Movies Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("New movies, new episodes and account alerts from MK Movies.");
            manager.createNotificationChannel(channel);
        }
    }
}
