# Launcher

Ein anpassbarer Android-Launcher mit dem Aussehen und Funktionsumfang von Lawnchair 2,
gepflegt für aktuelle Android-Versionen (Android 5.0 bis Android 16).

Launcher basiert auf [Lawnchair 2](https://github.com/LawnchairLauncher/lawnchair) (Version 2.0,
Build 2589) des Lawnchair-Teams, das wiederum auf Launcher3 aus dem Android Open Source Project
aufbaut. Launcher steht in keiner Verbindung zum Lawnchair-Projekt.

## Funktionen

- Startbildschirm, Dock und App-Übersicht mit umfangreichen Anpassungen (Raster, Icons,
  Icon-Pakete, Formen, Farben, Schriften, Gesten, Unschärfe-Effekt)
- App-Übersicht mit Tabs, Ordnern, automatischen Kategorien und unscharfer App-Suche
- Suchleiste: auf dem Startbildschirm öffnet sie den Standard-Browser, in der App-Übersicht
  durchsucht sie die Apps
- Arbeitsprofile und privater Bereich (Android 15+): eigener Tab „Privat“ mit Sperr-Schalter,
  gesperrte Apps sind überall ausgeblendet
- Widgets mit den Funktionen ab Android 12: abgerundete Ecken, responsive Größen,
  Standardgröße, optionale Konfiguration, Vorschauen im Widget-Auswahlmenü
- At a Glance / Smartspace mit Wetter (OpenWeatherMap, eigener API-Schlüssel) und Terminen
- Backup und Wiederherstellung von Startbildschirm und Einstellungen, zusätzlich Unterstützung
  für das Android-Backup und die Datenübertragung auf ein neues Gerät

## Sicherheit

- Memory Tagging (MTE) auf unterstützten Geräten, keine nativen Bibliotheken
- Nur verschlüsselte Netzwerkverbindungen
- Exportierte Komponenten sind auf das Nötigste beschränkt und prüfen ihre Eingaben
- Wiederhergestellte Backups werden vor dem Einspielen geprüft

## Bauen

Voraussetzungen: JDK 17 oder neuer, Android SDK.

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

## Lizenz

Launcher ist freie Software unter der [GNU General Public License v3.0](LICENSE).
Teile aus Launcher3 (AOSP) stehen unter der Apache License 2.0. Die Lizenzen der übrigen
Komponenten sind in der App unter *Einstellungen → Über → Open-Source-Lizenzen* aufgeführt.
