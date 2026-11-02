# Vintage radio / Старое радио

Android radio player by **Ilya Smirnov** for the [Staroe Radio project](https://staroeradio.ru/).

## Features

- Three stations: Vintage radio, Radio for kids, and Music.
- Stream quality: 128 (default), 64, or 32 kbit/s for all stations.
- Russian by default; English is available in Menu → Settings → Language.
- Background playback, media notification, and headset controls.
- Saves the selected station, language, and quality; no autoplay on launch.
- Android 8.0 (API 26) or newer.

The streams use HTTP on server.audiopedia.su:8000, with mount names
ices128, detskoe128, and music128 (or the selected 64/32 suffix).
Stream availability is controlled by the external provider.

## Build

Open the repository in Android Studio and run Gradle Sync.
Current build configuration: AGP 9.5.0, Gradle 9.8.2, compile/target SDK 36,
and Java/Kotlin bytecode target 17. Use Android Studio's configured Gradle JDK.

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:lintDebug
```

APKs are generated under app/build/outputs/apk/. Release APKs require signing.
Keep signing keys, passwords, and local.properties out of Git.

To regenerate launcher assets from app/app-icon-round.png:

```sh
./gradlew -I generate-icons.gradle generateLauncherIcons
```

## License and credits

Copyright (c) 2026 **Ilya Smirnov**.

Original project material is offered under the **BSD Zero Clause License
(SPDX: 0BSD)**; see [LICENSE.txt](LICENSE.txt). It permits commercial use,
modification, and redistribution without an attribution requirement.

Third-party materials retain their own licenses. See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the license texts bundled
in app/src/main/assets/. Earlier CC0 grants for the original source archive
remain unaffected.

The radio project is by Yuri Metelkin (staroeradio@mail.ru). This independent
player is not affiliated with or endorsed by the radio project. Its license
does not cover broadcast recordings or third-party trademarks.
