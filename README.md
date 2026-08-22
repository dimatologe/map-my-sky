# Sternbild Mapper

Native Android/Kotlin-App zum Annotieren von Astrofotos und Landschaftsbildern mit Nachthimmel.

## Stand

- Jetpack Compose + Material 3 mit dynamischer Systemfarbe.
- Bildimport ueber Android Photo Picker.
- Editor-Canvas mit Pan/Zoom und Touch-Transformation fuer Overlays.
- Lokale Stern-Erkennung als sichtbares Overlay.
- Automatische astrometrische Ausrichtung lokal (astrometry.net-Engine on-device) oder online (nova.astrometry.net), WCS/SIP-Projektion und bearbeitbarer Vorschau.
- Lokale Ausruestungsprofile fuer Sensoren, Objektive und Teleskope.
- Appweiter Diagnosebericht mit Geraete-/Displaydaten, Insets, Editorzustand, Overlays, Katalogstatus, Einstellungen, Speicher, Ereignisprotokoll und letztem Absturz. Originalbild, Vorschaubild, Dateiname und Bildpfad werden nicht eingebettet.
- Weitfeld-Index (4110-4119, ca. 1-33 Grad) ist offline enthalten; schmalere Felder koennen als Zusatzpakete optional geladen und dann ebenfalls offline genutzt werden.
- Sternbild-Katalog/Picker mit sphaerischer Vorschau und Nord-/Suedhimmel-Beispielmustern.
- Sternbild-Projektion, Snapping auf erkannte Sterne, Kreise/Ellipsen, Rechtecke, Freitext und Milchstrassenband in der Sphaere.
- Export als Bild oder transparentes Overlay in klein, mittel oder Originalgroesse.
- Vollstaendiger Sternbild-Linienkatalog fuer Nord- und Suedhimmel aus D3-Celestial.

## Entwicklung

Das Projekt ist fuer Android Studio Quail/AGP 9.2 vorbereitet. Android 16 ist als stabiler Zielpfad gesetzt:

- `compileSdk = 36`
- `targetSdk = 36`

Wenn das Android-17-SDK lokal installiert ist, koennen beide Werte auf `37` gehoben werden.

Build:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug
```

Der Build wurde mit der Android-Studio-JBR erfolgreich ausgefuehrt.

## Datenquellen

- `app/src/main/assets/catalog/constellations.lines.json`, `stars.6.json` und `mw.json` stammen aus D3-Celestial von Olaf Frohn.
- Die D3-Celestial-Daten stehen unter BSD-3-Clause; die Lizenz liegt als `app/src/main/assets/catalog/D3_CELESTIAL_LICENSE.txt` bei.
- Mythologische Illustrationsbilder aus Stellarium/Sky Cultures werden noch nicht gebuendelt, weil deren Lizenz pro Kultur und Bild separat geprueft werden muss.
