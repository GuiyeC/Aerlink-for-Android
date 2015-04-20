iOS Wear Connect
===================================

This app uses BLE services available on iOS devices to manage notifications, control music playback and check the iOS device battery level, without jailbreaking the iOS device or rooting the Android Wear device. This app is not a hack and has zero risks on either device.

- [Apple Notification Center Service (ANCS)](https://developer.apple.com/library/ios/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Introduction/Introduction.html)
- [Apple Media Service (AMS)](https://developer.apple.com/library/ios/documentation/CoreBluetooth/Reference/AppleMediaService_Reference/Introduction/Introduction.html)
- [Battery Service (BAS)](https://developer.bluetooth.org/TechnologyOverview/Pages/BAS.aspx)

This project was inspired by [@MohammadAG](https://twitter.com/MohammadAG) and built upon [@shiitakeo](https://twitter.com/shiitakeo)'s ["android_wear_for_ios"](https://github.com/shiitakeo/android_wear_for_ios/). You can contact me on my twitter [@GuiyeC](https://twitter.com/GuiyeC).

Change log
--------------
+ v1.0
 - Manage notifications, notifications should always be on sync with the iOS device's. You can also swipe block of notifications, delete single notifications and ANCS positive and negative actions are supported.
 - Answer or hang up calls straight from the watch, I could not get the official "incoming call screen" on the watch to work so I created one inspired by the Apple Watch.
 - Control media, this should work with any app that shows up on the Control Center.
 - Get battery updates.
 - When "Not Disturb" is enabled on the iOS Device the screen on the watch doesn't light up and the vibration is more subtle.
 - Support for messaging apps, Telegram and WhatsApp. This will show the sender's name instead of the apps name as the title of the notification.
 - All notifications backgrounds are black, I set it up to had a different color matching the app of the notification but I ended going with a more "Apple Watchy" style.


### Possible updates
```
- Settings screen
- Improved media playback, long strings are truncated.
```

Tested Devices
--------------
I have only tested it on the LG G Watch and so far I had no problems with it. This is shiitakeo's table of tested devices:

| Model | Result |
|:--    |:--     |
|G Watch R| ◯ (12 hours long time test passed.)|
|G Watch  | ◯ (12 hours long time test passed.)|
|Moto 360|△ (Can get notification, but connection is unstable, connection is lost after 4-5 hours. Please turn on ambient mode. (maybe moto360's BLE stack is something different.)|
|Gear Live|◯ (12 hours long time test passed.)|
|SmartWatch3| ◯ (6 hours test passed.)|
|ZenWatch| ◯ (12 hours long time test passed.)|

Contact me with any information regarding other watches.

Getting Started
---------------
1. Install the app on your watch (PlayStore or GitHub).
2. Install [LightBlue](https://itunes.apple.com/app/id557428110) on your iOS device.
3. Launch LightBlue and create a "New Virtual Peripheral" from the "Blank" template.
4. Launch the app on your watch.
5. Switch the discovery mode ON.
6. Turn on "Blank" peripheral on LightBlue app.
7. The watch should connect to the iOS device.

App install
---------
This app is completely free.

### 1. PlayStore
Install the app from the PlayStore on your Android device and sync apps with your Android Wear watch.

- [iOS Wear Connect](https://play.google.com/store/apps/details?id=com.codegy.ioswearconnect)

### 2. Advanced
You will need to enable [developer mode](https://developer.android.com/training/wearables/apps/bt-debugging.html#SetupDevices) on your watch.
 - Clone or download this project and run it on your watch, use Android Studio to open the project.
 - Install GitHub's APK using adb. You will find the APK on the [release page](https://github.com/GuiyeC/iOS-Wear-Connect/releases).

```sh
$ adb install wear-release.apk
```

If you want to use Moto 360, check [official article](https://developer.android.com/training/wearables/apps/bt-debugging.html) for ADB over Bluetooth.

Community Support
-------
- [Twitter](https://twitter.com/GuiyeC)
- [XDA thread](http://forum.xda-developers.com/android-wear/development/android-wear-ios-connectivity-t3052524)
