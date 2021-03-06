/*
 * Copyright (c) 2016-2019 The CallKeep Authors (see the AUTHORS file)
 * SPDX-License-Identifier: ISC, MIT
 *
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package io.wazo.callkeep;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.WindowManager;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel.Result;
import io.wazo.callkeep.utils.Callback;
import io.wazo.callkeep.utils.ConstraintsMap;
import io.wazo.callkeep.utils.ConstraintsArray;
import io.wazo.callkeep.utils.PermissionUtils;

import static io.wazo.callkeep.Constants.*;
import io.wazo.callkeep.R;


// @see https://github.com/kbagchiGWC/voice-quickstart-android/blob/9a2aff7fbe0d0a5ae9457b48e9ad408740dfb968/exampleConnectionService/src/main/java/com/twilio/voice/examples/connectionservice/VoiceConnectionServiceActivity.java
public class CallKeepModule {
    public static final int REQUEST_READ_PHONE_STATE = 1337;
    public static final int REQUEST_REGISTER_CALL_PROVIDER = 394859;

    private static final String E_ACTIVITY_DOES_NOT_EXIST = "E_ACTIVITY_DOES_NOT_EXIST";
    private static final String REACT_NATIVE_MODULE_NAME = "CallKeep";
    private static final String[] permissions = {Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE, Manifest.permission.RECORD_AUDIO};

    private static final String TAG = "FLT:CallKeepModule";
    private static TelecomManager telecomManager;
    private static TelephonyManager telephonyManager;
    private static MethodChannel.Result hasPhoneAccountPromise;
    private final Context _context;
    public static PhoneAccountHandle handle;
    private boolean isReceiverRegistered = false;
    private VoiceBroadcastReceiver voiceBroadcastReceiver;
    private ConstraintsMap _settings;
    Activity _currentActivity = null;
    MethodChannel _eventChannel;

    // v2
    private static final int NOTIFICATION_ID = 38496;
    private static final String NOTIFICATION_CHANNEL_ID = "call_notification_id";

    // v2

    public CallKeepModule(Context context, BinaryMessenger messenger) {
        this._context = context;
        this._eventChannel = new MethodChannel(messenger, "FlutterCallKeep.Event");
    }

    public void setActivity(Activity activity) {
        this._currentActivity = activity;
    }

    public void dispose() {
        LocalBroadcastManager.getInstance(this._context).unregisterReceiver(voiceBroadcastReceiver);
        VoiceConnectionService.setPhoneAccountHandle(null);
    }

    public boolean HandleMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "setup": {
                setup(new ConstraintsMap(call.argument("options")));
                result.success(null);
            }
            break;
            case "displayIncomingCall": {
                displayIncomingCall(call.argument("uuid"), call.argument("handle"), call.argument("localizedCallerName"));
                result.success(null);
            }
            break;
            case "answerIncomingCall": {
                answerIncomingCall(call.argument("uuid"));
                result.success(null);
            }
            break;
            case "startCall": {
                startCall(call.argument("uuid"), call.argument("number"), call.argument("callerName"));
                result.success(null);
            }
            break;
            case "endCall": {
                endCall(call.argument("uuid"));
                result.success(null);
            }
            break;
            case "endAllCalls": {
                endAllCalls();
                result.success(null);
            }
            break;
            case "checkPhoneAccountPermission": {
                checkPhoneAccountPermission(new ConstraintsArray(call.argument("optionalPermissions")), result);
            }
            break;
            case "checkDefaultPhoneAccount": {
                checkDefaultPhoneAccount(result);
            }
            break;
            case "setOnHold": {
                setOnHold(call.argument("uuid"), call.argument("hold"));
                result.success(null);
            }
            break;
            case "reportEndCallWithUUID": {
                reportEndCallWithUUID(call.argument("uuid"), call.argument("reason"));
                result.success(null);
            }
            break;
            case "rejectCall": {
                rejectCall(call.argument("uuid"));
                result.success(null);
            }
            break;
            case "setMutedCall": {
                setMutedCall(call.argument("uuid"), call.argument("muted"));
                result.success(null);
            }
            break;
            case "sendDTMF": {
                sendDTMF(call.argument("uuid"), call.argument("key"));
                result.success(null);
            }
            break;
            case "updateDisplay": {
                updateDisplay(call.argument("uuid"), call.argument("displayName"), call.argument("handle"));
                result.success(null);
            }
            break;
            case "hasPhoneAccount": {
                hasPhoneAccount(result);
            }
            break;
            case "hasOutgoingCall": {
                hasOutgoingCall(result);
            }
            break;
            case "hasPermissions": {
                hasPermissions(result);
            }
            break;
            case "setAvailable": {
                setAvailable(call.argument("available"));
                result.success(null);
            }
            break;
            case "setReachable": {
                setReachable();
                result.success(null);
            }
            break;
            case "setCurrentCallActive": {
                setCurrentCallActive(call.argument("uuid"));
                result.success(null);
            }
            break;
            case "openPhoneAccounts": {
                openPhoneAccounts(result);
            }
            break;
            case "backToForeground": {
                backToForeground(result);
            }
            break;
            case "displayCustomIncomingCall": {
                displayCustomIncomingCall(
                        call.argument("packageName"),
                        call.argument("className"),
                        call.argument("icon"),
                        call.argument("extra"),
                        call.argument("contentTitle"),
                        call.argument("answerText"),
                        call.argument("declineText"),
                        call.argument("ringtoneUri")
                );
                result.success(null);
            }
            break;
            case "dismissCustomIncomingCall": {
                dismissCustomIncomingCall();
                result.success(null);
            }
            break;
            default:
                return false;
        }

        return true;
    }

    public void setup(ConstraintsMap options) {
        VoiceConnectionService.setAvailable(false);
        this._settings = options;
        if (isConnectionServiceAvailable()) {
            this.registerPhoneAccount();
            this.registerEvents();
            VoiceConnectionService.setAvailable(true);
        }
    }


    public void registerPhoneAccount() {
        if (!isConnectionServiceAvailable()) {
            return;
        }

        this.registerPhoneAccount(this.getAppContext());
    }


    public void registerEvents() {
        if (!isConnectionServiceAvailable()) {
            return;
        }

        voiceBroadcastReceiver = new VoiceBroadcastReceiver();
        registerReceiver();
        VoiceConnectionService.setPhoneAccountHandle(handle);
    }


    private void showCustomNotification(final String uuid, final String number, final String callerName) {
        final NotificationManager manager = (NotificationManager) getAppContext().getSystemService(NotificationManager.class);
        final String packageName = getAppContext().getPackageName();
        final RemoteViews notificationView = new RemoteViews(packageName, R.layout.call_notification_layout);

        final int icon = getAppContext().getResources().getIdentifier("icon", "drawable", packageName);

        final Intent intent = getAppContext().getPackageManager().getLaunchIntentForPackage(packageName);
        final PendingIntent pendingIntent = PendingIntent.getActivity(getAppContext(), 0, intent, 0);

        notificationView.setTextViewText(R.id.callerName, callerName);
        notificationView.setImageViewResource(R.id.logo, icon);

        final HashMap<String, String> _attributeMap = new HashMap<String, String>();
        _attributeMap.put("'callUUID'", uuid);
        _attributeMap.put("'number'", number);
        _attributeMap.put("'callerName'", callerName);




        // accept call intent
        final Intent acceptCallIntent = new Intent(getAppContext(), CallNotificationReceiver.class);
        acceptCallIntent.setAction(ACTION_ANSWER_CALL);
        acceptCallIntent.putExtra("attributeMap", _attributeMap);
        final PendingIntent acceptCallPendingIntent = PendingIntent.getBroadcast(getAppContext(), 0, acceptCallIntent, 0);
        notificationView.setOnClickPendingIntent(R.id.acceptBtn, acceptCallPendingIntent);

        // decline call intent
        final Intent declineCallIntent = new Intent(getAppContext(), CallNotificationReceiver.class);
        declineCallIntent.setAction(ACTION_END_CALL);
        declineCallIntent.putExtra("attributeMap", _attributeMap);
        final PendingIntent declineCallPendingIntent = PendingIntent.getBroadcast(getAppContext(), 0, declineCallIntent, 0);
        notificationView.setOnClickPendingIntent(R.id.declineBtn, declineCallPendingIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Call Notification Channel",
                    NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        final Uri ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(getAppContext(), RingtoneManager.TYPE_RINGTONE);

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(getAppContext(), NOTIFICATION_CHANNEL_ID);
        builder.setSmallIcon(icon)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setCustomContentView(notificationView)
                .setCustomBigContentView(notificationView)
                .setCustomHeadsUpContentView(notificationView)
                .setTimeoutAfter(60000)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setTicker("Incoming Call")
                .setSound(ringtoneUri)
                .setVibrate(new long[] { 100, 30, 100, 30, 100, 200, 200, 30, 200, 30, 200, 200, 100, 30, 100, 30, 100, 100, 30, 100, 30, 100, 200, 200, 30, 200, 30, 200, 200, 100, 30, 100, 30, 100 })
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true);

        manager.notify(NOTIFICATION_ID, builder.build());
    }


    public void displayIncomingCall(String uuid, String number, String callerName) {
        final NotificationManager notificationManager = (NotificationManager) getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getAppContext());
        if (!isConnectionServiceAvailable() || !hasPhoneAccount()) {

            dismissCustomIncomingCall();

            showCustomNotification(uuid, number, callerName);
            return;
        }

        Log.d(TAG, "displayIncomingCall number: " + number + ", callerName: " + callerName);

        Bundle extras = new Bundle();
        Uri uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);

        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, uri);
        extras.putString(EXTRA_CALLER_NAME, callerName);
        extras.putString(EXTRA_CALL_UUID, uuid);

        telecomManager.addNewIncomingCall(handle, extras);
    }


    public void answerIncomingCall(String uuid) {
        if (!isConnectionServiceAvailable() || !hasPhoneAccount()) {
            return;
        }

        Connection conn = VoiceConnectionService.getConnection(uuid);
        if (conn == null) {
            return;
        }

        conn.onAnswer();
    }

    // v2
    private void displayCustomIncomingCall(String packageName, String className, String icon, HashMap<String, String> extra, String contentTitle, String answerText, String declineText, String ringtoneUri) {
        final NotificationManager notificationManager = (NotificationManager) getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);

        final Intent launchIntent = new Intent();
        launchIntent.setClassName(packageName, "$packageName.$className");
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        launchIntent.putExtra("io.wazo.callkeep.NOTIFICATION_ID", NOTIFICATION_ID);

        for (Map.Entry<String, String> entry : extra.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            launchIntent.putExtra(key, value);
        }


        final Intent answerIntent = new Intent();

        answerIntent.putExtra("io.wazo.callkeep.ACTION", "answer");
        answerIntent.setClassName(packageName, "$packageName.$className");

        for (Map.Entry<String, String> entry : extra.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            answerIntent.putExtra(key, value);
        }

        final Intent declineIntent = new Intent();
        declineIntent.putExtra("io.wazo.callkeep.ACTION", "decline");
        declineIntent.setClassName(packageName, "$packageName.$className");

        for (Map.Entry<String, String> entry : extra.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            declineIntent.putExtra(key, value);
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationChannel channel = new NotificationChannel("incoming_calls", "Incoming Calls", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        final PendingIntent pendingIntent = PendingIntent.getActivity(getAppContext(), 0, launchIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(getAppContext(), "incoming_calls");

        builder.setSmallIcon(getAppContext().getResources().getIdentifier(icon, "drawable", getAppContext().getPackageName()));
        builder.setFullScreenIntent(pendingIntent, true);
        builder.setOngoing(true);
        builder.setCategory(NotificationCompat.CATEGORY_CALL);
        builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        builder.setPriority(NotificationCompat.PRIORITY_MAX);
        builder.setAutoCancel(true);

        if (ringtoneUri != null) {
            builder.setSound(Uri.parse(ringtoneUri));
        }

        builder.setContentTitle(contentTitle);
        builder.addAction(0, declineText, PendingIntent.getActivity(getAppContext(), 1, declineIntent, PendingIntent.FLAG_CANCEL_CURRENT));
        builder.addAction(0, answerText, PendingIntent.getActivity(getAppContext(), 2, answerIntent, PendingIntent.FLAG_CANCEL_CURRENT));

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void dismissCustomIncomingCall() {
        final NotificationManager notificationManager = (NotificationManager)getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }





    // v2


    @SuppressLint("MissingPermission")
    public void startCall(String uuid, String number, String callerName) {
        if (!isConnectionServiceAvailable() || !hasPhoneAccount() || !hasPermissions() || number == null) {
            return;
        }

        Log.d(TAG, "startCall number: " + number + ", callerName: " + callerName);

        Bundle extras = new Bundle();
        Uri uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);

        Bundle callExtras = new Bundle();
        callExtras.putString(EXTRA_CALLER_NAME, callerName);
        callExtras.putString(EXTRA_CALL_UUID, uuid);
        callExtras.putString(EXTRA_CALL_NUMBER, number);

        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);
        extras.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, callExtras);


        telecomManager.placeCall(uri, extras);
    }


    public void endCall(String uuid) {
        Log.d(TAG, "endCall called");
        if (!isConnectionServiceAvailable() || !hasPhoneAccount()) {
            return;
        }

        Connection conn = VoiceConnectionService.getConnection(uuid);
        if (conn == null) {
            return;
        }
        conn.onDisconnect();

        Log.d(TAG, "endCall executed");
    }


    public void endAllCalls() {
        Log.d(TAG, "endAllCalls called");
        if (!isConnectionServiceAvailable() || !hasPhoneAccount()) {
            return;
        }

        Map<String, VoiceConnection> currentConnections = VoiceConnectionService.currentConnections;
        for (Map.Entry<String, VoiceConnection> connectionEntry : currentConnections.entrySet()) {
            Connection connectionToEnd = connectionEntry.getValue();
            connectionToEnd.onDisconnect();
        }

        Log.d(TAG, "endAllCalls executed");
    }


    public void checkPhoneAccountPermission(ConstraintsArray optionalPermissions, @NonNull MethodChannel.Result result) {
        if (!isConnectionServiceAvailable()) {
            result.error(E_ACTIVITY_DOES_NOT_EXIST, "ConnectionService not available for this version of Android.", null);
            return;
        }
        if (_currentActivity == null) {
            result.error(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist", null);
            return;
        }
        String[] optionalPermsArr = new String[optionalPermissions.size()];
        for (int i = 0; i < optionalPermissions.size(); i++) {
            optionalPermsArr[i] = optionalPermissions.getString(i);
        }

        String[] allPermissions = Arrays.copyOf(permissions, permissions.length + optionalPermsArr.length);
        System.arraycopy(optionalPermsArr, 0, allPermissions, permissions.length, optionalPermsArr.length);

        if (!this.hasPermissions()) {
            //requestPermissions(_currentActivity, allPermissions, REQUEST_READ_PHONE_STATE);
            ArrayList<String> list = new ArrayList<String>();
            Collections.addAll(list, allPermissions);
            requestPermissions(
                    list,
                    /* successCallback */ new Callback() {
                        @Override
                        public void invoke(Object... args) {
                            List<String> grantedPermissions = (List<String>) args[0];
                            result.success(grantedPermissions.size() == list.size());
                        }
                    },
                    /* errorCallback */ new Callback() {
                        @Override
                        public void invoke(Object... args) {
                            result.success(false);
                        }
                    });
            return;
        }

        result.success(!hasPhoneAccount());
    }


    public void checkDefaultPhoneAccount(@NonNull MethodChannel.Result result) {
        if (!isConnectionServiceAvailable() || !hasPhoneAccount()) {
            result.success(true);
            return;
        }

        if (!Build.MANUFACTURER.equalsIgnoreCase("Samsung")) {
            result.success(true);
            return;
        }

        boolean hasSim = telephonyManager.getSimState() != TelephonyManager.SIM_STATE_ABSENT;
        @SuppressLint("MissingPermission") boolean hasDefaultAccount = telecomManager.getDefaultOutgoingPhoneAccount("tel") != null;

        result.success(!hasSim || hasDefaultAccount);
    }


    public void setOnHold(String uuid, boolean shouldHold) {
        Connection conn = VoiceConnectionService.getConnection(uuid);
        if (conn == null) {
            return;
        }

        if (shouldHold == true) {
            conn.onHold();
        } else {
            conn.onUnhold();
        }
    }


    public void reportEndCallWithUUID(String uuid, int reason) {
        if (!isConnectionServiceAvailable() || !hasPhoneAccount()) {
            return;
        }

        VoiceConnection conn = (VoiceConnection) VoiceConnectionService.getConnection(uuid);
        if (conn == null) {
            return;
        }
        conn.reportDisconnect(reason);
    }


    public void rejectCall(String uuid) {
        if (!isConnectionServiceAvailable() || !hasPhoneAccount()) {
            return;
        }

        Connection conn = VoiceConnectionService.getConnection(uuid);
        if (conn == null) {
            return;
        }

        conn.onReject();
    }


    public void setMutedCall(String uuid, boolean shouldMute) {
        Connection conn = VoiceConnectionService.getConnection(uuid);
        if (conn == null) {
            return;
        }

        CallAudioState newAudioState = null;
        //if the requester wants to mute, do that. otherwise unmute
        if (shouldMute) {
            newAudioState = new CallAudioState(true, conn.getCallAudioState().getRoute(),
                    conn.getCallAudioState().getSupportedRouteMask());
        } else {
            newAudioState = new CallAudioState(false, conn.getCallAudioState().getRoute(),
                    conn.getCallAudioState().getSupportedRouteMask());
        }
        conn.onCallAudioStateChanged(newAudioState);
    }


    public void sendDTMF(String uuid, String key) {
        Connection conn = VoiceConnectionService.getConnection(uuid);
        if (conn == null) {
            return;
        }
        char dtmf = key.charAt(0);
        conn.onPlayDtmfTone(dtmf);
    }


    public void updateDisplay(String uuid, String displayName, String uri) {
        Connection conn = VoiceConnectionService.getConnection(uuid);
        if (conn == null) {
            return;
        }

        conn.setAddress(Uri.parse(uri), TelecomManager.PRESENTATION_ALLOWED);
        conn.setCallerDisplayName(displayName, TelecomManager.PRESENTATION_ALLOWED);
    }


    public void hasPhoneAccount(@NonNull MethodChannel.Result result) {
        if (telecomManager == null) {
            this.initializeTelecomManager();
        }

        result.success(hasPhoneAccount());
    }


    public void hasOutgoingCall(@NonNull MethodChannel.Result result) {
        result.success(VoiceConnectionService.hasOutgoingCall);
    }


    public void hasPermissions(@NonNull MethodChannel.Result result) {
        result.success(this.hasPermissions());
    }


    public void setAvailable(Boolean active) {
        VoiceConnectionService.setAvailable(active);
    }


    public void setReachable() {
        VoiceConnectionService.setReachable();
    }


    public void setCurrentCallActive(String uuid) {
        Connection conn = VoiceConnectionService.getConnection(uuid);
        if (conn == null) {
            return;
        }

        conn.setConnectionCapabilities(conn.getConnectionCapabilities() | Connection.CAPABILITY_HOLD);
        conn.setActive();
    }


    public void openPhoneAccounts(@NonNull MethodChannel.Result result) {
        if (!isConnectionServiceAvailable()) {
            result.error("ConnectionServiceNotAvailable", null, null);
            return;
        }

        if (Build.MANUFACTURER.equalsIgnoreCase("Samsung")) {
            Intent intent = new Intent();
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            intent.setComponent(new ComponentName("com.android.server.telecom",
                    "com.android.server.telecom.settings.EnableAccountPreferenceActivity"));
            this._currentActivity.startActivity(intent);
            result.success(null);
            return;
        }

        Intent intent = new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        this._currentActivity.startActivity(intent);
        result.success(null);
    }

    public static Boolean isConnectionServiceAvailable() {
        // PhoneAccount is available since api level 23
        return Build.VERSION.SDK_INT >= 23;
    }


    @SuppressLint("WrongConstant")
    public void backToForeground(@NonNull MethodChannel.Result result) {
        Context context = getAppContext();
        String packageName = context.getApplicationContext().getPackageName();
        Intent focusIntent = context.getPackageManager().getLaunchIntentForPackage(packageName).cloneFilter();
        Activity activity = this._currentActivity;
        boolean isOpened = activity != null;
        Log.d(TAG, "backToForeground, app isOpened ?" + (isOpened ? "true" : "false"));
        if (isOpened) {
            focusIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            activity.startActivity(focusIntent);
        } else {
            focusIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK +
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED +
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD +
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

            this._currentActivity.startActivity(focusIntent);
        }
        result.success(null);
    }

    private void initializeTelecomManager() {
        Context context = this.getAppContext();
        ComponentName cName = new ComponentName(context, VoiceConnectionService.class);
        String appName = this.getApplicationName(context);

        handle = new PhoneAccountHandle(cName, appName);
        telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
    }

    private void registerPhoneAccount(Context appContext) {
        if (!isConnectionServiceAvailable()) {
            return;
        }

        this.initializeTelecomManager();
        String appName = this.getApplicationName(this.getAppContext());

        PhoneAccount.Builder builder = new PhoneAccount.Builder(handle, appName)
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER);

        if (_settings != null && _settings.hasKey("imageName")) {
            int identifier = appContext.getResources().getIdentifier(_settings.getString("imageName"), "drawable", appContext.getPackageName());
            Icon icon = Icon.createWithResource(appContext, identifier);
            builder.setIcon(icon);
        }

        PhoneAccount account = builder.build();

        telephonyManager = (TelephonyManager) this.getAppContext().getSystemService(Context.TELEPHONY_SERVICE);

        telecomManager.registerPhoneAccount(account);
    }

    private void sendEventToFlutter(String eventName, @Nullable ConstraintsMap params) {
        _eventChannel.invokeMethod(eventName, params.toMap());
    }

    private String getApplicationName(Context appContext) {
        ApplicationInfo applicationInfo = appContext.getApplicationInfo();
        int stringId = applicationInfo.labelRes;

        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : appContext.getString(stringId);
    }

    private Boolean hasPermissions() {
        boolean hasPermissions = true;
        for (String permission : permissions) {
            int permissionCheck = ContextCompat.checkSelfPermission(_currentActivity, permission);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                hasPermissions = false;
            }
        }

        return hasPermissions;
    }

    private static boolean hasPhoneAccount() {
        return isConnectionServiceAvailable() && telecomManager != null
                && telecomManager.getPhoneAccount(handle) != null && telecomManager.getPhoneAccount(handle).isEnabled();
    }

    private void registerReceiver() {
        if (!isReceiverRegistered) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_END_CALL);
            intentFilter.addAction(ACTION_ANSWER_CALL);
            intentFilter.addAction(ACTION_MUTE_CALL);
            intentFilter.addAction(ACTION_UNMUTE_CALL);
            intentFilter.addAction(ACTION_DTMF_TONE);
            intentFilter.addAction(ACTION_UNHOLD_CALL);
            intentFilter.addAction(ACTION_HOLD_CALL);
            intentFilter.addAction(ACTION_ONGOING_CALL);
            intentFilter.addAction(ACTION_AUDIO_SESSION);
            intentFilter.addAction(ACTION_CHECK_REACHABILITY);
            LocalBroadcastManager.getInstance(this._context).registerReceiver(voiceBroadcastReceiver, intentFilter);
            isReceiverRegistered = true;
        }
    }

    private Context getAppContext() {
        return this._context.getApplicationContext();
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestPermissions(
            final ArrayList<String> permissions,
            final Callback successCallback,
            final Callback errorCallback) {
        PermissionUtils.Callback callback = (permissions_, grantResults) -> {
            List<String> grantedPermissions = new ArrayList<>();
            List<String> deniedPermissions = new ArrayList<>();

            for (int i = 0; i < permissions_.length; ++i) {
                String permission = permissions_[i];
                int grantResult = grantResults[i];

                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    grantedPermissions.add(permission);
                } else {
                    deniedPermissions.add(permission);
                }
            }

            // Success means that all requested permissions were granted.
            for (String p : permissions) {
                if (!grantedPermissions.contains(p)) {
                    // According to step 6 of the getUserMedia() algorithm
                    // "if the result is denied, jump to the step Permission
                    // Failure."
                    errorCallback.invoke(deniedPermissions);
                    return;
                }
            }
            successCallback.invoke(grantedPermissions);
        };

        final Activity activity = _currentActivity;
        if (activity != null) {
            PermissionUtils.requestPermissions(
                    activity, permissions.toArray(new String[permissions.size()]), callback);
        }
    }

    private class VoiceBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConstraintsMap args = new ConstraintsMap();
            HashMap<String, String> attributeMap = (HashMap<String, String>)intent.getSerializableExtra("attributeMap");

            switch (intent.getAction()) {
                case ACTION_END_CALL:
                    Log.d(TAG, "Tapped on End Call");
                    args.putString("callUUID", attributeMap.get(EXTRA_CALL_UUID));
                    sendEventToFlutter("CallKeepPerformEndCallAction", args);
                    break;
                case ACTION_ANSWER_CALL:
                    Log.d(TAG, "Tapped on Answer");
                    args.putString("callUUID", attributeMap.get(EXTRA_CALL_UUID));
                    sendEventToFlutter("CallKeepPerformAnswerCallAction", args);
                    break;
                case ACTION_HOLD_CALL:
                    args.putBoolean("hold", true);
                    args.putString("callUUID", attributeMap.get(EXTRA_CALL_UUID));
                    sendEventToFlutter("CallKeepDidToggleHoldAction", args);
                    break;
                case ACTION_UNHOLD_CALL:
                    args.putBoolean("hold", false);
                    args.putString("callUUID", attributeMap.get(EXTRA_CALL_UUID));
                    sendEventToFlutter("CallKeepDidToggleHoldAction", args);
                    break;
                case ACTION_MUTE_CALL:
                    args.putBoolean("muted", true);
                    args.putString("callUUID", attributeMap.get(EXTRA_CALL_UUID));
                    sendEventToFlutter("CallKeepDidPerformSetMutedCallAction", args);
                    break;
                case ACTION_UNMUTE_CALL:
                    args.putBoolean("muted", false);
                    args.putString("callUUID", attributeMap.get(EXTRA_CALL_UUID));
                    sendEventToFlutter("CallKeepDidPerformSetMutedCallAction", args);
                    break;
                case ACTION_DTMF_TONE:
                    args.putString("digits", attributeMap.get("DTMF"));
                    args.putString("callUUID", attributeMap.get(EXTRA_CALL_UUID));
                    sendEventToFlutter("CallKeepDidPerformDTMFAction", args);
                    break;
                case ACTION_ONGOING_CALL:
                    args.putString("callUUID", attributeMap.get(EXTRA_CALL_UUID));
                    args.putString("handle", attributeMap.get(EXTRA_CALL_NUMBER));
                    args.putString("name", attributeMap.get(EXTRA_CALLER_NAME));
                    sendEventToFlutter("CallKeepDidReceiveStartCallAction", args);
                    break;
                case ACTION_AUDIO_SESSION:
                    sendEventToFlutter("CallKeepDidActivateAudioSession", args);
                    break;
                case ACTION_CHECK_REACHABILITY:
                    sendEventToFlutter("CallKeepCheckReachability", args);
                    break;
                case ACTION_WAKE_APP:
                    Intent headlessIntent = new Intent(_context, CallKeepBackgroundMessagingService.class);
                    headlessIntent.putExtra("callUUID", attributeMap.get(EXTRA_CALL_UUID));
                    headlessIntent.putExtra("name", attributeMap.get(EXTRA_CALLER_NAME));
                    headlessIntent.putExtra("handle", attributeMap.get(EXTRA_CALL_NUMBER));
                    Log.d(TAG, "wakeUpApplication: " + attributeMap.get(EXTRA_CALL_UUID) + ", number : " + attributeMap.get(EXTRA_CALL_NUMBER) + ", displayName:" + attributeMap.get(EXTRA_CALLER_NAME));

                    ComponentName name = _context.startService(headlessIntent);
                    if (name != null) {
                        CallKeepBackgroundMessagingService.acquireWakeLockNow(_context);
                    }
                    break;
            }
        }
    }
}
