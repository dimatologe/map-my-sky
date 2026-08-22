# Lokaler astrometry.net-Solver – native Bibliothek `libastrometry.so`

Damit astrometry.net **auf dem Handy** (offline) laufen kann, muss der in C geschriebene
Rechenkern einmal in eine Android-Bibliothek **`libastrometry.so`** (ARM64) übersetzt werden.
Diesen Bau-Schritt kann ich (Assistent) in meiner Umgebung **nicht** ausführen – es gibt dort
keinen Compiler/kein Android-NDK. Diese Anleitung macht ihn dir so einfach wie möglich.

Quelle des nativen Codes: **DiDacTex/astrometry-android** (Fork von astrometry.net, als
JNI-Bibliothek für Android). Ziel-Architektur: **arm64-v8a** (dein Galaxy S25). Min. **API 28**.

Ergebnis, das wir brauchen: **eine Datei** `libastrometry.so`.

---

## Weg A (empfohlen): In der Cloud bauen – kein Programm auf deinem PC

Du brauchst nur ein kostenloses GitHub-Konto. GitHub baut die `.so` gratis für dich.

1. Konto anlegen auf https://github.com (falls noch keins).
2. Neues, **leeres** Repository erstellen, z. B. `astrometry-build` (Public reicht).
3. Datei **`build-astrometry-so.yml`** (liegt neben dieser README) in das Repo laden,
   und zwar in den Ordner **`.github/workflows/`**
   (In GitHub: „Add file" → „Create new file" → als Dateiname
   `.github/workflows/build-astrometry-so.yml` eintippen → Inhalt einfügen → „Commit").
4. Reiter **„Actions"** öffnen → Workflow **„Build astrometry.net .so (arm64-v8a)"**
   auswählen → **„Run workflow"**.
5. Warten (~10–20 min). Wenn grün: den Lauf öffnen → ganz unten unter **„Artifacts"**
   das Paket **`libastrometry-arm64-v8a`** herunterladen und entpacken.
6. Darin liegt **`libastrometry.so`**. Diese Datei
   - schickst du mir, **oder**
   - legst sie selbst ab unter:
     `work/app/src/main/jniLibs/arm64-v8a/libastrometry.so`

> Wenn ein Schritt **rot** wird: öffne den Lauf, kopiere die Fehlermeldung (das „Log")
> und schick sie mir. Dann passe ich den Workflow an. Der native Bau ist genau der Teil,
> den ich nicht selbst testen kann – wir iterieren notfalls über die Logs.

---

## Weg B: Lokal auf deinem Windows-PC (nur falls du Weg A nicht willst)

Braucht **WSL2 (Ubuntu)** oder **Docker Desktop**. Grober Ablauf (Ubuntu/WSL2):

```bash
# 1. Android-NDK r21e besorgen (z. B. via Android Studio SDK-Manager oder Download)
export TOOLCHAIN=/pfad/zum/ndk/toolchains/llvm/prebuilt/linux-x86_64
export API=28

# 2. Fork + cfitsio
git clone --depth 1 https://github.com/DiDacTex/astrometry-android.git
cd astrometry-android
curl -fL -o cfitsio.tar.gz "https://heasarc.gsfc.nasa.gov/FTP/software/fitsio/c/cfitsio-3.48.tar.gz"
tar xzf cfitsio.tar.gz && rm -rf cfitsio && mv cfitsio-3.48 cfitsio

# 3. Bauen
chmod +x build.sh && ./build.sh
find . -name libastrometry.so     # -> das ist die Datei
```

Der Fork enthält auch einen `docker/`-Ordner für einen Container-Build – falls du lieber
Docker Desktop nutzt.

---

## Index-Dateien (Sternkatalog) – kommen getrennt

Die `.so` ist nur der Rechner; sie braucht **Index-Dateien** (die Sterndatenbank), gegen die
gematcht wird. Für Weitwinkel/Handy sind das **4115–4119**:

- Download: http://data.astrometry.net/4100/  (Dateien `index-4115.fits` … `index-4119.fits`)
- Diese legst du später hier ab (richte ich in einer eigenen Stufe ein):
  `work/app/src/main/assets/astrometry/`  (dann werden sie in der App gebündelt)

Die App kopiert sie beim ersten Start in ihren Speicher und erzeugt automatisch die
`backend.cfg` mit `add_path` darauf – darum kümmert sich der App-Code, nicht du.

---

## Was ich (Assistent) parallel baue

Die komplette **App-Seite** – Umschalter in den Einstellungen (Lokal = Standard, Online =
optional), die JNI-Brücke `net.astrometry.JNI`, das Erzeugen der FITS-Eingabe, das Auslesen
der `.wcs`-Lösung und der Online-Fallback. Die App **kompiliert und läuft** dann bereits
(online wie bisher). **Lokal aktiv** wird sie, sobald deine `libastrometry.so` (oben) und die
Index-Dateien drin liegen.
