# SolidLink testing

SolidLink uses three validation layers. The Python suite in `tests/` checks repository contracts that are easy to regress, including the Compose navigation drawer, destination routing, local-only transport policy, Protobuf codec primitives, Android permissions, and iOS Bonjour metadata. Run it with `pytest -q tests`.

The Gradle suite runs every JVM unit test across the domain, crypto, protocol, transfer, database, file, platform, and transport modules. The Android validation command also runs lint, assembles the debug APK, and compiles the Compose instrumentation tests:

```bash
./gradlew test lint assembleDebug assembleAndroidTest
```

The Compose instrumentation tests in `app/src/androidTest/kotlin/com/solidlink/app/HomeScreenTest.kt` verify that the hamburger button opens the drawer and that Send and Settings selections render the corresponding destination content. They require an Android emulator or physical device for runtime execution:

```bash
./gradlew connectedDebugAndroidTest
```

The current sandbox has no connected Android device and cannot boot an emulator without KVM hardware acceleration, so the instrumentation suite is compiled but not executed here. The iOS foundation has passed static verification, but Swift/Xcode compilation and Android↔iPhone runtime interoperability require macOS/Xcode and physical devices.

The last completed validation run produced the following results:

| Gate | Result |
|---|---|
| `pytest -q tests` | 9 passed |
| `./gradlew test` | Passed |
| `./gradlew lint` | Passed |
| `./gradlew assembleDebug` | Passed |
| `./gradlew assembleAndroidTest` | Passed |
| iOS foundation static checks | Passed |
| Physical Android UI runtime tests | Not runnable in the sandbox |
| Physical Android↔iPhone transfer test | Requires devices and macOS/Xcode |
