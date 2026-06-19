package ng.com.mkmovies.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends Activity {
    private static final String TAG = "MKMoviesMain";
    private static final int REQ_POST_NOTIFICATIONS = 701;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
        } else {
            registerTokenAndOpenApp();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        registerTokenAndOpenApp();
    }

    private void registerTokenAndOpenApp() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String token = task.getResult();
                MkTokenRegistrar.registerTokenAsync(token);
                Log.d(TAG, "Native FCM token received");
            } else {
                Log.w(TAG, "Could not get native FCM token", task.getException());
            }
            openTrustedWebActivity();
        });
    }

    private void openTrustedWebActivity() {
        try {
            Intent intent = new Intent(this, com.google.androidbrowserhelper.trusted.LauncherActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Could not open TWA launcher", e);
        }
        finish();
        overridePendingTransition(0, 0);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            NotificationChannel channel = new NotificationChannel(
                    "mk_movies_channel",
                    "MK Movies Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("New movies, new episodes and account alerts from MK Movies.");
            manager.createNotificationChannel(channel);
        }
    }
}
