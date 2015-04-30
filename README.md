Wear Connect for iOS
===================================

This app uses BLE services available on iOS devices to manage notifications, control music playback and check the iOS device battery level, without jailbreaking the iOS device or rooting the Android Wear device. This app is not a hack and has zero risks on either device.

- [Apple Notification Center Service (ANCS)](https://developer.apple.com/library/ios/documentation/CoreBluetooth/Reference/AppleNotificationCenterServiceSpecification/Introduction/Introduction.html)
- [Apple Media Service (AMS)](https://developer.apple.com/library/ios/documentation/CoreBluetooth/Reference/AppleMediaService_Reference/Introduction/Introduction.html)
- [Battery Service (BAS)](https://developer.bluetooth.org/TechnologyOverview/Pages/BAS.aspx)

This project was inspired by [@MohammadAG](https://twitter.com/MohammadAG) and built upon [@shiitakeo](https://twitter.com/shiitakeo)'s ["android_wear_for_ios"](https://github.com/shiitakeo/android_wear_for_ios/). You can contact me on my twitter [@GuiyeC](https://twitter.com/GuiyeC).

Change log
--------------
+ v1.6.1
 - Option to have less frequent or complete battery updates. When "Complete battery info" is disabled it will only show the battery every 10% or when it's lower than 25%.
 - It now vibrates when the iOS device's battery is at 20%, 15%, 10% or 5%.
 - Updated help card with BLE Utility instead of LightBlue.
 
+ v1.6
 - New way of connecting, more info cards, no more infinite "Searching..." loop.
 - The pairing PIN is now displayed in a card too.
 - Connection and reconnection improvements.
 - Moto 360 improvements, a little more to come.
 
+ v1.5
 - Connection and reconnection improvements. It should automatically unpair and repair with the iPhone when a there is a problem.
 
+ v1.4.1
 - Bug fixing.
 - Added Viber icon.
 
+ v1.4
 - Icon, name and package changed to be able to publish on the PlayStore.
 - More improvements in reconnecting.
 - Bug fixing.
 
+ v1.3
 - Improved connection, reconnection and communication with iOS device.
 - This version improves handling notifications and commands to the iOS device. Every notification should get through to the watch even in blocks of many.
 - Remove advice to turn on ambient mode on Moto 360 (users report to work longer without turning it on).
 - Moto 360 fix of last version is enabled by default on this watch.
 
+ v1.2
 - Another try at fixing Moto 360 problems.

+ v1.1
 - Option to turn on Color backgrounds.
 - Option to turn off battery updates.
 - Added Snapchat icon.
 - Experimental fix for Moto 360

+ v1.0
 - Manage notifications, notifications should always be on sync with the iOS device's. You can also swipe block of notifications, delete single notifications and ANCS positive and negative actions are supported.
 - Answer or hang up calls straight from the watch, I could not get the official "incoming call screen" on the watch to work so I created one inspired by the Apple Watch.
 - Control media, this should work with any app that shows up on the Control Center.
 - Get battery updates.
 - When "Not Disturb" is enabled on the iOS Device the screen on the watch doesn't light up and the vibration is more subtle.
 - Support for messaging apps, Telegram and WhatsApp. This will show the sender's name instead of the apps name as the title of the notification.
 - Every notification background is black, it's possible to have a different color matching the app of the notification but I ended up going with a more "Apple Watchy" style.

![Incoming call](https://cloud.githubusercontent.com/assets/289797/7249800/5529e7ee-e81d-11e4-9fe2-ec09d0bab814.png)
![Notification](https://cloud.githubusercontent.com/assets/289797/7250594/18b280ee-e824-11e4-9d46-81733a81ed36.png)

### Possible updates
```
- Settings screen
- Improved media playback, long strings are truncated.
```

Tested Devices
--------------

| Model | Result |
|:--    |:--     |
|G Watch R| ◯ (12 hours long time test passed.)|
|G Watch  | ◯ (12 hours long time test passed.)|
|Moto 360|△ (15 min - 6 hours. Mixed results, currently working on it.)|
|Gear Live|◯ (12 hours long time test passed.)|
|SmartWatch3| ◯ (6 hours test passed.)|
|ZenWatch| ◯ (12 hours long time test passed.)|

Contact me with any information regarding other watches.

Getting Started
---------------
1. Install the app on your watch.
2. Install [BLE Utility](https://itunes.apple.com/app/id606210918) on your iOS device.
3. Select the "Peripheral" tab on BLE Utility app.
4. Launch the app on your watch.
5. Switch ON the iOS Service.
6. Tap the "Disconnected" card on the watch to search for the iOS device.
7. The watch should connect to the iOS device.

App install
---------
This app is completely free.

### 1. PlayStore
Install the app from the PlayStore on your Android device and sync apps with your Android Wear watch.

- [Wear Connect for iOS](https://play.google.com/store/apps/details?id=com.codegy.wearconnectforios)

### 2. Install on Android handheld and sync with the watch
Download the "mobile.apk" from the [release page](https://github.com/GuiyeC/iOS-Wear-Connect/releases) and open it on your handheld to install the app, connect the Android device to your watch to sync apps as you would any app downloaded from the PlayStore.

### 3. Install directly on the watch
You will need to enable [developer mode](https://developer.android.com/training/wearables/apps/bt-debugging.html#SetupDevices) on your watch.
 - I haven't tried this option but this app could help installing the APK directly on the watch: [Android Wear APK Tools](http://forum.xda-developers.com/smartwatch/other-smartwatches/tool-android-wear-apk-tools-sideload-t2929177)
 - Clone or download this project and run it on your watch, use Android Studio to open the project.
 - Install GitHub's APK using adb. You will find the APK on the [release page](https://github.com/GuiyeC/iOS-Wear-Connect/releases).

```sh
$ adb install wear.apk
```

If you want to use Moto 360, check [official article](https://developer.android.com/training/wearables/apps/bt-debugging.html) for ADB over Bluetooth.

Community Support
-------
- [Twitter](https://twitter.com/GuiyeC)
- [XDA thread](http://forum.xda-developers.com/android-wear/development/android-wear-ios-connectivity-t3052524)
