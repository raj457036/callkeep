package io.wazo.callkeep;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.HashMap;

public class CallNotificationReceiver extends BroadcastReceiver {
    @SuppressLint("WrongConstant")
    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        final HashMap<String, String> handle = (HashMap<String, String>)intent.getSerializableExtra("attributeMap");

        if (action != null) {
            final Context appContext = context.getApplicationContext();
            final Intent launchIntent = appContext.getPackageManager().getLaunchIntentForPackage(appContext.getPackageName()).cloneFilter();
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK +
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED +
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD +
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

            appContext.startActivity(launchIntent);
        }

        sendCallRequestToActivity(context, action, handle);
    }

    private void sendCallRequestToActivity(final Context context, final String action, @Nullable final HashMap attributeMap) {
        final Handler handler = new Handler();

        handler.post(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(action);
                if (attributeMap != null) {
                    Bundle extras = new Bundle();
                    extras.putSerializable("attributeMap", attributeMap);
                    intent.putExtras(extras);
                }
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            }
        });
    }
}
