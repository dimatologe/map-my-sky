#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SIMBAD als ZWEITE Entdeckungsquelle fuer DSO-Populaernamen, zusaetzlich zu Stellarium (Nutzerwunsch
2026-08-19). SIMBAD markiert informelle Bezeichner selbst mit dem Praefix "NAME " -- diese Kennungen
werden hier komplett abgefragt (eingeschraenkt auf DSO-passende Objekttypen), gegen unseren eigenen
Katalog abgeglichen und -- anders als Stellarium-Funde -- OHNE "src"-Feld (also ohne "Quelle:
Stellarium"-Badge) uebernommen: SIMBAD ist hier eine reine Faktenabfrage (welche Kennung hat SIMBAD
selbst als informellen Namen getaggt), kein Zitat aus einer GPL-Datei wie bei Stellarium.

Schreibt NUR einen Report zur manuellen Durchsicht -- das Mergen in dso_names.json passiert getrennt,
erst nach Stichprobenpruefung (gleiches Vorgehen wie bei den Stellarium-Runden dieser Sitzung).

Aufruf: python tools/build_dso_names_simbad.py
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
REPORT = os.path.join(HERE, 'dso_names.simbad_discovery_report.json')

sys.path.insert(0, HERE)
from build_dso_names import is_plausible_common_name, normalize, load_catalog_designations  # noqa: E402
from build_dso_names_stellarium import _run_sparql_tap, load_current_names  # noqa: E402

DSO_OTYPES = ('GNe', 'PN', 'SNR', 'OpC', 'GlC', 'G', 'GiG', 'GiP', 'ISM', 'HII', 'RNe', 'DNe', 'Cl*', 'As*')
BATCH_SIZE = 200


def fetch_name_tagged_oidrefs():
    """oidref -> Liste der 'NAME '-Kennungen (ohne Praefix), eingeschraenkt auf DSO-passende otype."""
    otypes = ','.join("'%s'" % t for t in DSO_OTYPES)
    q = f"""
SELECT i.id, i.oidref FROM ident AS i
JOIN basic AS b ON i.oidref = b.oid
WHERE i.id LIKE 'NAME %%'
AND b.otype IN ({otypes})
"""
    rows = _run_sparql_tap(q)
    by_oidref = {}
    for id_, oidref in rows.get('data', []):
        by_oidref.setdefault(oidref, []).append(id_[5:].strip())
    return by_oidref


def fetch_all_identifiers(oidrefs):
    """oidref -> Liste ALLER seiner SIMBAD-Kennungen (fuer den Katalog-Abgleich), gebatcht."""
    result = {}
    oidrefs = sorted(oidrefs)
    for i in range(0, len(oidrefs), BATCH_SIZE):
        batch = oidrefs[i:i + BATCH_SIZE]
        ids_str = ','.join(str(o) for o in batch)
        rows = _run_sparql_tap(f'SELECT oidref, id FROM ident WHERE oidref IN ({ids_str})')
        for oidref, id_ in rows.get('data', []):
            result.setdefault(oidref, []).append(id_)
        print(f'  Kennungen {min(i + BATCH_SIZE, len(oidrefs))}/{len(oidrefs)} oidrefs')
        time.sleep(0.3)
    return result


def main():
    print('Frage SIMBAD nach allen NAME-getaggten DSO-Objekten (weltweit) ...')
    name_tags_by_oidref = fetch_name_tagged_oidrefs()
    print(f'  {len(name_tags_by_oidref)} eindeutige Objekte mit mind. einer NAME-Kennung')

    print('Frage alle Kennungen dieser Objekte ab (gebatcht) ...')
    all_ids_by_oidref = fetch_all_identifiers(name_tags_by_oidref.keys())

    print('Lade eigenen Katalog ...')
    our_desigs = set(normalize(d) for d in load_catalog_designations())
    current = load_current_names()
    # id<->desig-Paare (110 bekannte Faelle, z.B. id="NGC 4406"/desig="M 86") -- ein Kandidat, der nur
    # EINEN der beiden Schluessel trifft, waere sonst faelschlich als "neu" akzeptiert worden, obwohl
    # der ANDERE Schluessel (z.B. "NGC4406") bereits einen guten Namen traegt: die App prueft beim
    # Laden desig VOR id, ein neuer desig-Treffer wuerde den bestehenden id-Namen also verdecken (live
    # gefunden: SIMBAD-Kandidat "M86"->"FAUST V023" haette "NGC4406"->"Markarian's Chain" verdeckt).
    equivalent_keys = {}
    with open(os.path.join(CAT, 'dsos.20.json'), encoding='utf-8') as f:
        for feat in json.load(f)['features']:
            rid = str(feat.get('id', '') or '')
            desig = feat['properties'].get('desig') or rid
            if rid and desig and rid != desig:
                a, b = normalize(rid), normalize(desig)
                equivalent_keys.setdefault(a, set()).add(b)
                equivalent_keys.setdefault(b, set()).add(a)

    accepted = {}
    report = {'accepted': [], 'rejected_implausible': [], 'rejected_already_named': [],
              'rejected_no_catalog_match': []}
    for oidref, all_ids in all_ids_by_oidref.items():
        name_candidates = name_tags_by_oidref.get(oidref, [])
        plausible_names = [n for n in name_candidates if is_plausible_common_name(n)]
        if not plausible_names:
            report['rejected_implausible'].append({'oidref': oidref, 'candidates': name_candidates})
            continue

        matched_key = None
        matched_raw = None
        for ident in all_ids:
            key = normalize(ident)
            if key in our_desigs:
                matched_key = key
                matched_raw = ident
                break
        if not matched_key:
            continue  # kein Treffer in unserem Katalog -- kein Report-Eintrag noetig (zu viele weltweit)

        shadowed_by = equivalent_keys.get(matched_key, set())
        already_named = (
            matched_key in current or matched_key in accepted
            or any(k in current or k in accepted for k in shadowed_by)
        )
        if already_named:
            report['rejected_already_named'].append({
                'key': matched_key, 'raw': matched_raw, 'candidates': plausible_names,
            })
            continue

        chosen = plausible_names[0]
        accepted[matched_key] = {'name': chosen}
        report['accepted'].append({
            'key': matched_key, 'raw': matched_raw, 'name': chosen,
            'other_candidates': plausible_names[1:],
        })

    json.dump(report, open(REPORT, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print()
    print(f"{len(report['accepted'])} neue Kandidaten akzeptiert")
    print(f"{len(report['rejected_already_named'])} passen zu bereits benannten Objekten (uebersprungen)")
    print(f"{len(report['rejected_implausible'])} ohne plausiblen Namenskandidaten verworfen")
    print(f'Geschrieben -> {REPORT} (NICHT automatisch in dso_names.json gemergt)')


if __name__ == '__main__':
    main()
