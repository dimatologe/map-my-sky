#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Fragt SIMBAD einmalig fuer den GESAMTEN eigenen Katalog (91.852 Bezeichnungen) nach der internen
Objekt-ID (oidref) ab und findet daraus AUTOMATISCH alle Faelle, in denen zwei oder mehr unserer
eigenen Katalogzeilen (dsos.20.json) dasselbe physische SIMBAD-Objekt unter verschiedenen
Katalogsystemen fuehren (z.B. NGC 6027/PGC 56579) -- ersetzt die bisherige, von Hand Fund-fuer-Fund
gepflegte KNOWN_CROSS_CATALOG_DUPLICATES-Liste in DeepSkyAssetLoader.kt (Nutzervorschlag 2026-08-19:
"wir beziehen das doch sowieso von SIMBAD", statt weiter nur zufaellig entdeckte Einzelfaelle
nachzutragen).

WICHTIG: das ist ein ANDERER Fehlertyp als die Naehe+Populaername-Unterdrueckung
(suppressDuplicatePopularNames in DeepSkyAssetLoader.kt) -- hier geht es um dasselbe SIMBAD-Objekt
unter zwei Bezeichnungen, dort um zwei ECHTE, verschiedene Objekte mit demselben Spitznamen (z.B.
Pelikannebel-Haelften). Diese Datei deckt nur den ersten Fall ab.

Ablauf:
  1. Alle Katalogbezeichnungen laden (load_catalog_designations()).
  2. Gebatcht (Batchgroesse 800) SIMBADs ident-Tabelle nach oidref abfragen.
  3. Nach oidref gruppieren, nur Gruppen mit 2+ TREFFERN UNSERES Katalogs behalten.
  4. Schreiben nach app/src/main/assets/catalog/dso_crossid_duplicates.json.

Aufruf: python tools/build_dso_crossid_duplicates.py
"""
import json
import os
import re
import sys
import time

if sys.stdout.encoding and sys.stdout.encoding.lower() != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
CAT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'catalog')
OUTPATH = os.path.join(CAT, 'dso_crossid_duplicates.json')
CHECKPOINT = os.path.join(HERE, 'dso_crossid_duplicates.checkpoint.json')

sys.path.insert(0, HERE)
from build_dso_names import load_catalog_designations, normalize  # noqa: E402
from build_dso_names_stellarium import _run_sparql_tap  # noqa: E402

BATCH_SIZE = 800
BATCH_DELAY_SEC = 0.5


def match_key(s):
    """Wie normalize(), aber zusaetzlich Punkte entfernt -- fuer den Ruecklauf-Abgleich der Batch-
    Antwort auf unsere Anfrage-Strings TOLERANTER als der sonst im Projekt verwendete normalize()
    (der Punkte bewusst STEHEN laesst, weil er auch als Katalogcode-Erkennungs-Hilfsmittel dient).
    Live gefunden: SIMBAD echot "PK 352-06.1" (unsere Schreibweise) als "PK 352-06  1" zurueck --
    Punkt wird durch Leerzeichen ersetzt, normalize() allein wuerde das nicht als gleich erkennen."""
    return normalize(s).replace('.', '')


def query_form(desig):
    """SIMBAD behandelt PGC-Nummern intern als Alias von LEDA und gibt bei einer Abfrage nach
    "PGC NNNNN" IMMER "LEDA NNNNN" zurueck, unabhaengig davon wie angefragt wurde (live verifiziert:
    auch eine direkte Anfrage nach "PGC 1022505" liefert id="LEDA 1022505"; ein reiner
    Batch-Ruecklauf-Abgleich auf den urspruenglichen "PGC..."-String scheitert deshalb systematisch
    fuer JEDE PGC-Bezeichnung -- betraf beim ersten Lauf ueber 70.000 von 91.852 Bezeichnungen, weil
    PGC mit Abstand der groesste Katalog in unseren Daten ist). Fix: gleich als "LEDA..." anfragen,
    dann stimmt Anfrage- und Antwort-Form ueberein."""
    if desig.upper().startswith('PGC '):
        return 'LEDA ' + desig[4:]
    return desig


def main():
    print('Lade eigenen Katalog ...')
    all_desigs = load_catalog_designations()
    print(f'  {len(all_desigs)} eindeutige Bezeichnungen')

    oidref_by_desig = {}
    start = 0
    if os.path.exists(CHECKPOINT):
        cp = json.load(open(CHECKPOINT, encoding='utf-8'))
        start = cp['progress']
        oidref_by_desig = cp['oidref_by_desig']
        print(f'  Checkpoint geladen, setze fort ab {start}/{len(all_desigs)}')

    def _checkpoint(progress):
        json.dump({'progress': progress, 'oidref_by_desig': oidref_by_desig},
                   open(CHECKPOINT, 'w', encoding='utf-8'), ensure_ascii=False)

    print('Frage SIMBAD gebatcht nach oidref ...')
    for i in range(start, len(all_desigs), BATCH_SIZE):
        batch = all_desigs[i:i + BATCH_SIZE]
        queried = [query_form(d) for d in batch]
        ids_str = ','.join("'%s'" % d.replace("'", "''") for d in queried)
        try:
            rows = _run_sparql_tap(f'SELECT id, oidref FROM ident WHERE id IN ({ids_str})')
            data = rows.get('data', [])
        except Exception as e:
            print(f'  Batch {i} FEHLER ({e}), ueberspringe {len(batch)} Bezeichnungen')
            data = []
        # Ruecklauf ueber match_key() (toleranter als normalize(), s.o.) auf unsere ANGEFRAGTEN
        # Strings (query_form-Form) zurueckmappen, aber unter der ROHEN dsos.20.json-Schreibweise
        # (batch-Form, z.B. "PGC ...") gespeichert -- das ist die Form, die spaeter beim Laden in
        # DeepSkyAssetLoader.kt gegen unseren eigenen Katalog abgeglichen wird.
        key_to_raw = {match_key(q): d for q, d in zip(queried, batch)}
        for id_, oidref in data:
            raw = key_to_raw.get(match_key(id_))
            if raw:
                oidref_by_desig[raw] = oidref
        print(f'  {min(i + BATCH_SIZE, len(all_desigs))}/{len(all_desigs)} '
              f'({len(oidref_by_desig)} bisher aufgeloest)')
        _checkpoint(i + BATCH_SIZE)
        time.sleep(BATCH_DELAY_SEC)

    print('Gruppiere nach oidref ...')
    by_oidref = {}
    for desig, oidref in oidref_by_desig.items():
        by_oidref.setdefault(oidref, []).append(desig)

    groups = [sorted(desigs) for desigs in by_oidref.values() if len(desigs) >= 2]
    groups.sort(key=lambda g: g[0])
    print(f'  {len(groups)} Gruppen mit 2+ eigenen Bezeichnungen (= bestätigte Dubletten)')

    with open(OUTPATH, 'w', encoding='utf-8') as f:
        json.dump(groups, f, ensure_ascii=False, indent=1)
        f.write('\n')
    print(f'Geschrieben -> {OUTPATH}')

    if os.path.exists(CHECKPOINT):
        os.remove(CHECKPOINT)


if __name__ == '__main__':
    main()
