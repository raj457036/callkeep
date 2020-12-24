import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart'
    show
        showDialog,
        AlertDialog,
        BuildContext,
        FlatButton,
        Navigator,
        Text,
        Widget,
        required;
import 'package:flutter/services.dart';
import 'package:flutter/services.dart' show MethodChannel;

import 'actions.dart';
import 'event.dart';

bool get isIOS => Platform.isIOS;
bool get supportConnectionService =>
    !isIOS && int.parse(Platform.version) >= 23;

class FlutterCallkeep extends EventManager {
  factory FlutterCallkeep() {
    return _instance;
  }
  FlutterCallkeep._internal() {
    _event.setMethodCallHandler(eventListener);
  }
  static final FlutterCallkeep _instance = FlutterCallkeep._internal();
  static const MethodChannel _channel = MethodChannel('FlutterCallKeep.Method');
  static const MethodChannel _event = MethodChannel('FlutterCallKeep.Event');
  BuildContext _context;

  Future<void> setup(Map<String, dynamic> options) async {
    if (!isIOS) {
      await _setupAndroid(options['android'] as Map<String, dynamic>);
    }
    await _setupIOS(options['ios'] as Map<String, dynamic>);
  }

  Future<void> registerPhoneAccount() async {
    if (isIOS) {
      return;
    }
    return _channel
        .invokeMethod<void>('registerPhoneAccount', <String, dynamic>{});
  }

  Future<void> registerAndroidEvents() async {
    if (isIOS) {
      return;
    }
    return _channel.invokeMethod<void>('registerEvents', <String, dynamic>{});
  }

  Future<void> hasDefaultPhoneAccount(
      BuildContext context, Map<String, dynamic> options) async {
    _context = context;
    if (!isIOS) {
      return _hasDefaultPhoneAccount(options);
    }
    return;
  }

  Future<bool> _checkDefaultPhoneAccount() async {
    return await _channel
        .invokeMethod<bool>('checkDefaultPhoneAccount', <String, dynamic>{});
  }

  Future<void> _hasDefaultPhoneAccount(Map<String, dynamic> options) async {
    final hasDefault = await _checkDefaultPhoneAccount();
    final shouldOpenAccounts = await _alert(options, hasDefault);
    if (shouldOpenAccounts == true) {
      await _openPhoneAccounts();
    }
  }

  Future<void> displayIncomingCall(String uuid, String handle,
      {String localizedCallerName = '',
      String handleType = 'number',
      bool hasVideo = false}) async {
    if (!isIOS) {
      await _channel.invokeMethod<void>(
          'displayIncomingCall', <String, dynamic>{
        'uuid': uuid,
        'handle': handle,
        'localizedCallerName': localizedCallerName
      });
      return;
    }
    await _channel.invokeMethod<void>('displayIncomingCall', <String, dynamic>{
      'uuid': uuid,
      'handle': handle,
      'handleType': handleType,
      'hasVideo': hasVideo,
      'localizedCallerName': localizedCallerName
    });
  }

  Future<void> answerIncomingCall(String uuid) async {
    if (!isIOS) {
      await _channel.invokeMethod<void>(
          'answerIncomingCall', <String, dynamic>{'uuid': uuid});
    }
  }

  Future<void> startCall(String uuid, String handle, String callerName,
      {String handleType = 'number', bool hasVideo = false}) async {
    if (!isIOS) {
      await _channel.invokeMethod<void>('startCall', <String, dynamic>{
        'uuid': uuid,
        'handle': handle,
        'callerName': callerName
      });
      return;
    }
    await _channel.invokeMethod<void>('startCall', <String, dynamic>{
      'uuid': uuid,
      'handle': handle,
      'callerName': callerName,
      'handleType': handleType,
      'hasVideo': hasVideo
    });
  }

  Future<void> reportConnectingOutgoingCallWithUUID(String uuid) async {
    //only available on iOS
    if (isIOS) {
      await _channel.invokeMethod<void>('reportConnectingOutgoingCallWithUUID',
          <String, dynamic>{'uuid': uuid});
    }
  }

  Future<void> reportConnectedOutgoingCallWithUUID(String uuid) async {
    //only available on iOS
    if (isIOS) {
      await _channel.invokeMethod<void>('reportConnectedOutgoingCallWithUUID',
          <String, dynamic>{'uuid': uuid});
    }
  }

  Future<void> reportEndCallWithUUID(String uuid, int reason) async =>
      await _channel.invokeMethod<void>('reportEndCallWithUUID',
          <String, dynamic>{'uuid': uuid, 'reason': reason});

  /*
   * Android explicitly states we reject a call
   * On iOS we just notify of an endCall
   */
  Future<void> rejectCall(String uuid) async {
    if (!isIOS) {
      await _channel
          .invokeMethod<void>('rejectCall', <String, dynamic>{'uuid': uuid});
    } else {
      await _channel
          .invokeMethod<void>('endCall', <String, dynamic>{'uuid': uuid});
    }
  }

  Future<bool> isCallActive(String uuid) async => await _channel
      .invokeMethod<bool>('isCallActive', <String, dynamic>{'uuid': uuid});

  Future<void> endCall(String uuid) async => await _channel
      .invokeMethod<void>('endCall', <String, dynamic>{'uuid': uuid});

  Future<void> endAllCalls() async =>
      await _channel.invokeMethod<void>('endAllCalls', <String, dynamic>{});

  FutureOr<bool> hasPhoneAccount() async {
    if (isIOS) {
      return true;
    }
    return await _channel
        .invokeMethod<bool>('hasPhoneAccount', <String, dynamic>{});
  }

  Future<bool> hasOutgoingCall() async {
    if (isIOS) {
      return true;
    }
    return await _channel
        .invokeMethod<bool>('hasOutgoingCall', <String, dynamic>{});
  }

  Future<void> setMutedCall(String uuid, bool shouldMute) async =>
      await _channel.invokeMethod<void>(
          'setMutedCall', <String, dynamic>{'uuid': uuid, 'muted': shouldMute});

  Future<void> sendDTMF(String uuid, String key) async =>
      await _channel.invokeMethod<void>(
          'sendDTMF', <String, dynamic>{'uuid': uuid, 'key': key});

  Future<void> checkIfBusy() async => isIOS
      ? await _channel.invokeMethod<void>('checkIfBusy', <String, dynamic>{})
      : throw Exception('CallKeep.checkIfBusy was called from unsupported OS');

  Future<void> checkSpeaker() async => isIOS
      ? await _channel.invokeMethod<void>('checkSpeaker', <String, dynamic>{})
      : throw Exception('CallKeep.checkSpeaker was called from unsupported OS');

  Future<void> setAvailable(String state) async {
    if (isIOS) {
      return;
    }
    // Tell android that we are able to make outgoing calls
    await _channel
        .invokeMethod<void>('setAvailable', <String, dynamic>{'state': state});
  }

  Future<void> setCurrentCallActive(String callUUID) async {
    if (isIOS) {
      return;
    }

    await _channel.invokeMethod<void>(
        'setCurrentCallActive', <String, dynamic>{'uuid': callUUID});
  }

  Future<void> updateDisplay(String uuid,
          {String displayName, String handle}) async =>
      await _channel.invokeMethod<void>('updateDisplay', <String, dynamic>{
        'uuid': uuid,
        'displayName': displayName,
        'handle': handle
      });

  Future<void> setOnHold(String uuid, bool shouldHold) async =>
      await _channel.invokeMethod<void>(
          'setOnHold', <String, dynamic>{'uuid': uuid, 'hold': shouldHold});

  Future<void> setReachable() async {
    if (isIOS) {
      return;
    }
    await _channel.invokeMethod<void>('setReachable', <String, dynamic>{});
  }

  // @deprecated
  Future<void> reportUpdatedCall(
      String uuid, String localizedCallerName) async {
    print(
        'CallKeep.reportUpdatedCall is deprecated, use CallKeep.updateDisplay instead');

    return isIOS
        ? await _channel.invokeMethod<void>(
            'reportUpdatedCall', <String, dynamic>{
            'uuid': uuid,
            'localizedCallerName': localizedCallerName
          })
        : throw Exception(
            'CallKeep.reportUpdatedCall was called from unsupported OS');
  }

  Future<void> backToForeground() async {
    if (isIOS) {
      return;
    }

    await _channel.invokeMethod<void>('backToForeground', <String, dynamic>{});
  }

  Future<void> displayCustomIncomingCall(
    String packageName,
    String className, {
    @required String icon,
    Map<String, String> extra,
    String contentTitle,
    String answerText,
    String declineText,
    String ringtoneUri,
  }) async {
    assert(packageName != null);
    assert(className != null);
    assert(icon != null);
    await _channel.invokeMethod('displayCustomIncomingCall', {
      'packageName': packageName,
      'className': className,
      'icon': icon,
      'extra': extra ?? <String, String>{},
      'contentTitle': contentTitle ?? 'Incoming call',
      'answerText': answerText ?? 'Answer',
      'declineText': declineText ?? 'Decline',
      'ringtoneUri': ringtoneUri,
    });
  }

  Future<void> dismissCustomIncomingCall() async {
    await _channel.invokeMethod('dismissCustomIncomingCall');
  }

  Future<void> _setupIOS(Map<String, dynamic> options) async {
    if (options['appName'] == null) {
      throw Exception('CallKeep.setup: option "appName" is required');
    }
    if (options['appName'] is String == false) {
      throw Exception(
          'CallKeep.setup: option "appName" should be of type "string"');
    }
    return await _channel
        .invokeMethod<void>('setup', <String, dynamic>{'options': options});
  }

  Future<bool> _setupAndroid(Map<String, dynamic> options) async {
    await _channel.invokeMethod<void>('setup', {'options': options});
    final showAccountAlert = await _checkPhoneAccountPermission(
        options['additionalPermissions'] as List<String> ?? <String>[]);
    final shouldOpenAccounts = await _alert(options, showAccountAlert);

    if (shouldOpenAccounts) {
      await _openPhoneAccounts();
      return true;
    }
    return false;
  }

  Future<void> _openPhoneAccounts() async {
    if (!Platform.isAndroid) {
      return;
    }
    await _channel.invokeMethod<void>('openPhoneAccounts', <String, dynamic>{});
  }

  Future<bool> _checkPhoneAccountPermission(
      [List<String> optionalPermissions]) async {
    if (!Platform.isAndroid) {
      return true;
    }
    return await _channel
        .invokeMethod<bool>('checkPhoneAccountPermission', <String, dynamic>{
      'optionalPermissions': optionalPermissions ?? <String>[],
    });
  }

  Future<bool> _alert(Map<String, dynamic> options, bool condition) async {
    if (_context == null) {
      return false;
    }
    return await _showAlertDialog(
        _context,
        options['alertTitle'] as String,
        options['alertDescription'] as String,
        options['cancelButton'] as String,
        options['okButton'] as String);
  }

  Future<bool> _showAlertDialog(BuildContext context, String alertTitle,
      String alertDescription, String cancelButton, String okButton) {
    return showDialog<bool>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        title: Text(alertTitle ?? 'Permissions required'),
        content: Text(alertDescription ??
            'This application needs to access your phone accounts'),
        actions: <Widget>[
          FlatButton(
            child: Text(cancelButton ?? 'Cancel'),
            onPressed: () =>
                Navigator.of(context, rootNavigator: true).pop(false),
          ),
          FlatButton(
            child: Text(okButton ?? 'ok'),
            onPressed: () =>
                Navigator.of(context, rootNavigator: true).pop(true),
          ),
        ],
      ),
    );
  }

  Future<void> eventListener(MethodCall call) async {
    print('[CallKeep] INFO: received event "${call.method}" ${call.arguments}');
    final data = call.arguments as Map<dynamic, dynamic>;
    switch (call.method) {
      case 'CallKeepDidReceiveStartCallAction':
        emit(CallKeepDidReceiveStartCallAction.fromMap(data));
        break;
      case 'CallKeepPerformAnswerCallAction':
        emit(CallKeepPerformAnswerCallAction.fromMap(data));
        break;
      case 'CallKeepPerformEndCallAction':
        emit(CallKeepPerformEndCallAction.fromMap(data));
        break;
      case 'CallKeepDidActivateAudioSession':
        emit(CallKeepDidActivateAudioSession());
        break;
      case 'CallKeepDidDeactivateAudioSession':
        emit(CallKeepDidActivateAudioSession());
        break;
      case 'CallKeepDidDisplayIncomingCall':
        emit(CallKeepDidDisplayIncomingCall.fromMap(data));
        break;
      case 'CallKeepDidPerformSetMutedCallAction':
        emit(CallKeepDidPerformSetMutedCallAction.fromMap(data));
        break;
      case 'CallKeepDidToggleHoldAction':
        emit(CallKeepDidToggleHoldAction.fromMap(data));
        break;
      case 'CallKeepDidPerformDTMFAction':
        emit(CallKeepDidPerformDTMFAction.fromMap(data));
        break;
      case 'CallKeepProviderReset':
        emit(CallKeepProviderReset());
        break;
      case 'CallKeepCheckReachability':
        emit(CallKeepCheckReachability());
        break;
      case 'CallKeepDidLoadWithEvents':
        emit(CallKeepDidLoadWithEvents());
        break;
      case 'CallKeepPushKitToken':
        emit(CallKeepPushKitToken.fromMap(data));
        break;
    }
  }
}
