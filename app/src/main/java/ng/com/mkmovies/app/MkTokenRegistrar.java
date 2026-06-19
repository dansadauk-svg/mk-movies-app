package ng.com.mkmovies.app;

import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class MkTokenRegistrar {
    private static final String TAG = "MKMoviesFCM";
    private static final String REGISTER_ENDPOINT = "https://mkmovies.com.ng/wp-json/mkpush/v2/register-token";

    private MkTokenRegistrar() {}

    public static void registerTokenAsync(final String token) {
        if (token == null || token.length() < 40) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                registerToken(token);
            }
        }).start();
    }

    private static void registerToken(String token) {
        HttpURLConnection connection = null;
        try {
            JSONObject body = new JSONObject();
            body.put("token", token);
            body.put("permission", "granted");
            body.put("source", "native-android");
            body.put("package", BuildConfig.APPLICATION_ID);
            body.put("app_version", BuildConfig.VERSION_NAME);
            body.put("android_sdk", Build.VERSION.SDK_INT);
            body.put("device", Build.MANUFACTURER + " " + Build.MODEL);

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            URL url = new URL(REGISTER_ENDPOINT);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "MKMoviesAndroid/" + BuildConfig.VERSION_NAME);

            OutputStream os = connection.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();

            int code = connection.getResponseCode();
            InputStream stream = (code >= 200 && code < 300) ? connection.getInputStream() : connection.getErrorStream();
            String response = readStream(stream);
            Log.d(TAG, "Token register HTTP " + code + ": " + response);
        } catch (Exception e) {
            Log.w(TAG, "Could not register native FCM token", e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readStream(InputStream stream) {
        if (stream == null) return "";
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
            reader.close();
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
