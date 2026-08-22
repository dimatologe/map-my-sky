#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Vollstaendiger Qualitaets-Audit ALLER aktuellen dso_names.json-Eintraege (nicht nur eine Teilmenge) --
prueft jedes name- UND jedes Uebersetzungsfeld gegen die verschaerfte is_plausible_common_name()
(s. build_dso_names.py, 2026-08-19 gehaertet gegen Zwicky-/MCG-/Nachname+Nummer-Katalogcodes).

Fuer jeden durchfallenden Eintrag: per SIMBAD pruefen, ob eine ECHTE Alternative existiert (z.B.
RCW 100 hat aktuell den Katalogcode "Shapley 1", SIMBAD selbst kennt es aber als
"NAME Fine Ring Nebula") -- wenn ja, ERSETZEN, sonst das Namensfeld komplett entfernen (Objekt zeigt
danach nur noch die Katalogbezeichnung).

Schreibt NICHTS automatisch -- Ausgabe ist ein Report (dso_names.full_audit_report.json) zur
manuellen Durchsicht, das eigentliche Anwenden passiert in einem zweiten, expliziten Schritt.

Aufruf: python tools/audit_dso_names_full.py
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
REPORT = os.path.join(HERE, 'dso_names.full_audit_report.json')
CHECKPOINT = os.path.join(HERE, 'dso_names.full_audit_checkpoint.json')

sys.path.insert(0, HERE)
from build_dso_names import is_plausible_common_name, normalize  # noqa: E402
from build_dso_names_stellarium import simbad_check_one, simbad_matches  # noqa: E402

LANGS = ('de', 'zh', 'es', 'ru', 'ar', 'ja')


def load_key_to_raw():
    """normalisierter Schluessel -> rohe Bezeichnung (ueber id ODER desig), s. wiederholt in dieser
    Sitzung verwendetes Muster."""
    with open(os.path.join(CAT, 'dsos.20.json'), encoding='utf-8') as f:
        dsos = json.load(f)
    key_to_raw = {}
    for feat in dsos['features']:
        p = feat['properties']
        rid = str(feat.get('id', '') or '')
        desig = p.get('desig') or rid
        for raw in {rid, desig}:
            if raw:
                key_to_raw.setdefault(normalize(raw), raw)
    return key_to_raw


def find_simbad_replacement(raw_desig):
    """Sucht unter den SIMBAD-Alternativkennungen fuer [raw_desig] eine, die (a) mit "NAME " beginnt
    (SIMBADs eigene Informell-Name-Markierung) UND (b) is_plausible_common_name() besteht. Liefert die
    erste passende (ohne "NAME "-Praefix) oder None."""
    try:
        alts = simbad_check_one(raw_desig)
    except Exception:
        return None
    for alt in alts:
        if alt.upper().startswith('NAME '):
            candidate = alt[5:].strip()
            if is_plausible_common_name(candidate):
                return candidate
    return None


def main():
    with open(os.path.join(CAT, 'dso_names.json'), encoding='utf-8') as f:
        current = json.load(f)
    key_to_raw = load_key_to_raw()

    keys = sorted(current.keys())
    report = {'remove_name': [], 'replace_name': [], 'remove_translation': [], 'unchanged_but_flagged': []}
    start = 0
    if os.path.exists(CHECKPOINT):
        cp = json.load(open(CHECKPOINT, encoding='utf-8'))
        start = cp['progress']
        report = cp['report']
        print(f'Checkpoint geladen, setze fort ab {start}/{len(keys)}')

    def _checkpoint(progress):
        json.dump({'progress': progress, 'report': report}, open(CHECKPOINT, 'w', encoding='utf-8'), ensure_ascii=False)

    for idx in range(start, len(keys)):
        key = keys[idx]
        entry = current[key]
        name = entry.get('name', '')
        raw = key_to_raw.get(key)

        if not is_plausible_common_name(name):
            replacement = find_simbad_replacement(raw) if raw else None
            time.sleep(0.3)
            if replacement:
                report['replace_name'].append({'key': key, 'old': name, 'new': replacement})
            else:
                report['remove_name'].append({'key': key, 'old': name})
        else:
            bad_translations = [lang for lang in LANGS if lang in entry and not is_plausible_common_name(entry[lang])]
            if bad_translations:
                report['remove_translation'].append({'key': key, 'name': name, 'langs': bad_translations,
                                                       'values': {l: entry[l] for l in bad_translations}})

        if (idx + 1) % 40 == 0:
            print(f'  {idx + 1}/{len(keys)} geprueft (entfernen: {len(report["remove_name"])}, '
                  f'ersetzen: {len(report["replace_name"])})')
            _checkpoint(idx + 1)

    json.dump(report, open(REPORT, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print()
    print(f'{len(report["remove_name"])} Namen komplett entfernen (keine SIMBAD-Alternative gefunden)')
    print(f'{len(report["replace_name"])} Namen durch SIMBAD-Alternative ersetzen')
    print(f'{len(report["remove_translation"])} Objekte mit nur fehlerhafter Uebersetzung (name bleibt)')
    print(f'Geschrieben -> {REPORT}')
    if os.path.exists(CHECKPOINT):
        os.remove(CHECKPOINT)


if __name__ == '__main__':
    main()
