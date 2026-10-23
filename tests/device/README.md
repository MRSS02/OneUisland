# Live shade card probe

With one Android 17 device connected through adb and JDK 17+ in `JAVA_HOME`, run from the repository root:

```powershell
./tests/device/run.ps1
```

Optional parameters: `-JavaRoot <jdk>` and `-SdkRoot <sdk>`. The runner uses SDK platform 36 and build-tools 36.0.0.

The probe executes production styling methods in a separate `app_process`, with small row/adapter fixtures and real Android drawables. It checks live classification, group and keyguard exclusions, drawable isolation, solid fallback, restoration, and transition rebuilds. It does not install the module or hook/restart SystemUI. Temporary device files are removed after execution.

This is a behavior probe, not a visual or Xposed integration test. Check light/dark themes, expanded/collapsed live rows, live-to-regular updates, lock/unlock, and reduce transparency after installing the APK. Verify that notification actions, custom RemoteViews, and ordinary rows still behave as expected.

## Регрессии ProgressStyle, мобильных значков и копий LiveBridge

`app/src/androidTest/java/dev/flexy/dynamicisland/tests/RegressionRunner.java` запускает 169 проверок в настоящем процессе приложения (со шрифтами Android 17). Нужен JDK 21.

```powershell
./gradlew.bat "-Dorg.gradle.java.home=$env:JAVA_HOME" :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.flexy.dynamicisland.test/dev.flexy.dynamicisland.tests.RegressionRunner
```

Ожидаемый результат: `PASS: 169 regression checks`. Сохраняет тестовую отрисовку в `/sdcard/Android/data/dev.flexy.dynamicisland/files/progress-card.png`. Настоящие уведомления и пользовательские настройки не создаются и не меняются. Для применения обновлённого модуля после тестов достаточно отдельно перезапустить только SystemUI.
