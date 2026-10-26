# Launcher

Ein Android-Launcher, der Aussehen und Funktionsumfang von **Lawnchair 2** bewahrt und für aktuelle
Android-Versionen (bis Android 16) weiterentwickelt wird.

> **Herkunft:** Launcher basiert auf [Lawnchair 2](https://github.com/LawnchairLauncher/lawnchair),
> Version 2.0 Build 2589 (Commit `0b71dc40b2`, Branch `9-stable`), entwickelt vom Lawnchair-Team
> (Till Kottmann, Patryk Michalik, David Sn und viele weitere). Lawnchair wiederum basiert auf
> Launcher3 aus dem Android Open Source Project.
> Dieses Projekt steht in keiner Verbindung zum Lawnchair-Projekt und wird von diesem nicht unterstützt.

## Was ist anders als bei Lawnchair 2?

- Aktuelles Build-System (Gradle 8, Android Gradle Plugin 8, Kotlin 2), neue Target-SDK
- **Privater Bereich** (Android 15+): eigener Tab „Privat“ in der App-Übersicht mit Sperr-Schalter.
  Gesperrt sind die Apps ausgeblendet, sie erscheinen nicht in anderen Tabs, Vorschlägen,
  der Suche für andere Apps und werden nicht automatisch zum Startbildschirm hinzugefügt.
- **Fehlerbehebungen**
  - Absturz beim Öffnen einer App, die gerade aktualisiert wird
  - Absturz beim schnellen Wechseln zwischen Launcher und anderen Apps (Blur-Bitmaps wurden
    in einem Hintergrund-Thread freigegeben, während sie noch gezeichnet wurden)
  - Verschwindende App-Verknüpfungen: Icons von Apps, die beim Laden des Startbildschirms
    kurzzeitig nicht verfügbar waren (z. B. während eines Updates), wurden dauerhaft gelöscht
- Anpassungen an neue Android-Regeln (Broadcast-Receiver, PendingIntents, Paketsichtbarkeit,
  Zugriff auf Hintergrundbild und Speicher, Hidden-API-Beschränkungen, Edge-to-Edge)
- Eingebauter Fehlerbericht: Abstürze erscheinen als Benachrichtigung mit „Teilen“-Knopf

## Installation & Umzug von Lawnchair 2

Launcher hat eine eigene Paket-ID (`io.github.randomnam3.launcher`) und kann daher **neben**
Lawnchair 2 installiert werden.

1. In Lawnchair 2: *Einstellungen → Backups → Neues Backup* (Startbildschirm + Einstellungen).
2. Launcher installieren und als Standard-Startbildschirm festlegen.
3. In Launcher: *Einstellungen → Backups → Wiederherstellen* und die `.shed`-Datei auswählen.

Für den Unschärfe-Effekt (Blur) und Backups im Ordner `Documents` benötigt Android ab Version 11
die Berechtigung „Zugriff auf alle Dateien“, die App fragt bei Bedarf danach.

## Bauen

Voraussetzungen: JDK 17 oder neuer, Android SDK (Plattform 35/36).

```sh
./gradlew assembleQuickstepLawnchairDebug     # Debug-Build
./gradlew assembleQuickstepLawnchairRelease   # Release-Build
```

Für signierte Release-Builds in `local.properties` (wird nicht eingecheckt) eintragen:

```properties
storeFile=/pfad/zum/keystore.jks
storePass=...
keyAlias=launcher
keyPass=...
```

oder die Umgebungsvariablen `LAUNCHER_STORE_FILE`, `LAUNCHER_STORE_PASSWORD`,
`LAUNCHER_KEY_ALIAS` und `LAUNCHER_KEY_PASSWORD` setzen.

Der Java/Kotlin-Code liegt weiterhin in den ursprünglichen Paketen (`ch.deletescape.lawnchair`,
`com.android.launcher3`), um Änderungen gegenüber dem Original nachvollziehbar zu halten.

## Lizenz

Launcher ist freie Software unter der [GNU General Public License v3.0](LICENSE), wie Lawnchair.
Teile aus Launcher3 (AOSP) stehen unter der Apache License 2.0. Die Lizenzen der übrigen
Komponenten sind in der App unter *Einstellungen → Über → Open-Source-Lizenzen* aufgeführt.
