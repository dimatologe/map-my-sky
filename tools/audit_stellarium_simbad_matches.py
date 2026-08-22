#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Prueft alle 75 per SIMBAD-Fuzzy-Match "bestaetigten" Namen aus dem urspruenglichen Stellarium-Batch
(dso_names.stellarium_report.json, verified_via=='simbad') erneut: hat der SIMBAD-Fuzzy-Match
(build_dso_names_stellarium.simbad_matches, streift generische Woerter wie "Nebula"/"Cluster" vom
Kandidatennamen ab) tatsaechlich ein eigenstaendiges DSO-Objekt getroffen, oder -- wie beim
vdB-107/"Antares Nebula"-Fall live gefunden -- einen STERN, dessen SIMBAD-Kennungsliste zufaellig auch
den "abgestreiften" Kern-Namen enthaelt (z.B. "NAME Antares" fuer den Stern, waehrend "Antares Nebula"
eigentlich das eigenstaendige DSO IC 4606 meint)?

Heuristik: Sterne haben in ihrer SIMBAD-Kennungsliste fast immer mindestens eine der folgenden
Kennungsformen (Katalogsysteme, die NUR fuer Einzelsterne existieren): HD/HR/HIP/SAO/TYC/GJ/GCRV/PPM/
ein "* "-Bayer-/Flamsteed-Praefix, oder eine Doppelstern-Kennung (ADS/WDS/CCDM). Kommt eine davon in
der Kennungsliste des BESTAETIGENDEN Objekts vor, ist das ein starkes Warnsignal, dass der Fuzzy-Match
zufaellig einen Stern statt eines eigenstaendigen Nebels/Haufens/etc. getroffen hat.

Aufruf: python tools/audit_stellarium_simbad_matches.py
"""
import json
import os
import sys
import time

if sys.stdout.encoding and sys.stdout.encoding.lower() != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
CAT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'catalog')

sys.path.insert(0, HERE)
from build_dso_names_stellarium import (  # noqa: E402
    parse_stellarium_file, RAWFILE, simbad_check_one, simbad_matches,
)

STAR_ONLY_PREFIXES = (
    'HD ', 'HR ', 'HIP ', 'SAO ', 'TYC ', 'GJ ', 'GCRV ', 'PPM ', 'GSC ', 'PLX ', 'FK5 ', 'FK6 ',
    'ADS ', 'WDS ', 'CCDM ', 'CPD', 'CD-', 'CD+', 'BD-', 'BD+', 'ROT ', 'SKY# ', 'AAVSO ',
)


def looks_like_star_identifier(alt):
    a = alt.strip()
    if a.startswith('* ') or a.startswith('V* '):
        return True
    return any(a.startswith(p) for p in STAR_ONLY_PREFIXES)


def main():
    with open(os.path.join(HERE, 'dso_names.stellarium_report.json'), encoding='utf-8') as f:
        report = json.load(f)
    simbad_entries = [e for e in report['accepted'] if e['verified_via'] == 'simbad']
    print(f'{len(simbad_entries)} SIMBAD-bestaetigte Eintraege zu pruefen')

    # entries[0] ist exakt der Kandidat, den build_dso_names_stellarium.py seinerzeit gewaehlt hat
    # (meiste Quellenbelege zuerst, s. parse_stellarium_file-Sortierung) -- keine Rueck-Suche noetig.
    stellarium = parse_stellarium_file(RAWFILE)
    raw_desig_by_key = {key: entries[0]['raw_desig'] for key, entries in stellarium.items()}

    with open(os.path.join(CAT, 'dso_names.json'), encoding='utf-8') as f:
        current = json.load(f)

    results = []
    for i, entry in enumerate(simbad_entries):
        key, name = entry['key'], entry['name']
        raw_desig = raw_desig_by_key.get(key)
        if not raw_desig:
            print(f'  [{i+1}/{len(simbad_entries)}] {key}: KEIN raw_desig gefunden, ueberspringe')
            continue
        alt_ids = simbad_check_one(raw_desig)
        time.sleep(0.35)
        star_hits = [a for a in alt_ids if looks_like_star_identifier(a)]
        still_matches = simbad_matches(name, alt_ids)
        in_current = key in current
        verdict = 'VERDAECHTIG (Stern-Kennungen im Treffer)' if star_hits else 'ok'
        print(f'  [{i+1}/{len(simbad_entries)}] {key} ({raw_desig}) -> {name!r}: {verdict}'
              f'{" [" + str(len(star_hits)) + " Sternkennungen]" if star_hits else ""}')
        results.append({
            'key': key, 'raw_desig': raw_desig, 'name': name,
            'star_identifier_hits': star_hits[:8],
            'still_matches_fuzzy': still_matches,
            'in_current_db': in_current,
            'verdict': 'suspicious' if star_hits else 'ok',
        })

    suspicious = [r for r in results if r['verdict'] == 'suspicious']
    print(f'\n{len(suspicious)} von {len(results)} verdaechtig (Stern-Kennungen im SIMBAD-Treffer)')
    for r in suspicious:
        print(f"  {r['key']} ({r['raw_desig']}) -> {r['name']!r}  Beispiel-Sternkennungen: {r['star_identifier_hits'][:4]}")

    with open(os.path.join(HERE, 'dso_names.simbad_audit_report.json'), 'w', encoding='utf-8') as f:
        json.dump(results, f, ensure_ascii=False, indent=1)
    print('\nGeschrieben -> tools/dso_names.simbad_audit_report.json')


if __name__ == '__main__':
    main()
