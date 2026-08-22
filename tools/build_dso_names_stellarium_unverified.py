#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Ergaenzt dso_names.json um ALLE plausiblen Stellarium-Namen, auch OHNE unabhaengige Gegenpruefung
(Nutzerentscheidung 2026-08-19, nach Ruecksprache: 738 plausible Stellarium-Treffer stehen 576
unabhaengig kuratierten Objekten gegenueber -- der Nutzer moechte die Differenz trotzdem drin haben,
im Austausch dafuer zeigt die App bei jedem so uebernommenen Namen "Quelle: Stellarium" in der
Region-Info an, statt ihn wie einen unabhaengig bestaetigten Namen zu behandeln).

Zwei Gruppen:
  A) Bereits in dso_names.json vorhanden (unabhaengig via build_dso_names.py/
     build_dso_names_stellarium.py bestaetigt) UND zusaetzlich in Stellariums Liste: bekommt NUR das
     Feld "src":"Stellarium" nachgetragen, Name/Uebersetzungen bleiben unveraendert.
  B) Neu (noch nicht in dso_names.json): komplett neuer Eintrag {"name":..., "src":"Stellarium"},
     Uebersetzungsversuch ueber dieselbe Wikidata-P528-Abfrage wie in build_dso_names_stellarium.py
     (Label-Uebereinstimmung wird NICHT verlangt, nur wie dort schon uebernommen wenn vorhanden --
     bewusst dieselbe Methode wie beim bereits ausgelieferten verifizierten Stellarium-Batch, damit
     beide Batches konsistent behandelt werden).

Lizenzhinweis bleibt wie beim urspruenglichen Stellarium-Import: names.dat ist Teil eines
GPL-2.0-Projekts (github.com/Stellarium/stellarium). Hier werden Name-Strings direkt uebernommen
(nicht nur als Fundstellen-Index genutzt wie beim verifizierten Batch) -- die Quellenangabe
"Stellarium" in jedem betroffenen dso_names.json-Eintrag UND in der Region-Info-Anzeige ist die
bewusste Attributions-Massnahme dafuer.

Aufruf: python tools/build_dso_names_stellarium_unverified.py
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
REPORT = os.path.join(HERE, 'dso_names.stellarium_unverified_report.json')

sys.path.insert(0, HERE)
from build_dso_names import (  # noqa: E402
    LANGS, normalize, is_plausible_common_name, load_catalog_designations,
    wikidata_query_by_desig, BATCH_SIZE, BATCH_DELAY_SEC,
)
from build_dso_names_stellarium import parse_stellarium_file, RAWFILE, load_current_names  # noqa: E402


def main():
    print('Parse Stellarium-Datei ...')
    stellarium = parse_stellarium_file(RAWFILE)
    our_desigs = set(normalize(d) for d in load_catalog_designations())
    current = load_current_names()

    matches = {k: v[0] for k, v in stellarium.items() if k in our_desigs}
    plausible = {k: c for k, c in matches.items() if is_plausible_common_name(c['name'])}
    print(f'  {len(plausible)} plausible Stellarium-Treffer im eigenen Katalog')

    already_keys = sorted(k for k in plausible if k in current)
    new_keys = sorted(k for k in plausible if k not in current)
    print(f'  {len(already_keys)} bereits vorhanden -> bekommen nur "src":"Stellarium"')
    print(f'  {len(new_keys)} neu -> werden komplett ergaenzt')

    marked = 0
    for k in already_keys:
        if current[k].get('src') != 'Stellarium':
            current[k]['src'] = 'Stellarium'
            marked += 1
    print(f'  {marked} bestehende Eintraege neu markiert (Rest hatte das Feld schon)')

    print('Frage Wikidata (P528) fuer alle neuen Kandidaten ab (nur fuer Uebersetzungen, keine '
          'Akzeptanz-Bedingung) ...')
    wd_labels = {}
    for i in range(0, len(new_keys), BATCH_SIZE):
        batch_keys = new_keys[i:i + BATCH_SIZE]
        batch_desigs = [plausible[k]['raw_desig'] for k in batch_keys]
        try:
            hits = wikidata_query_by_desig(batch_desigs)
        except Exception as e:
            print(f'  Wikidata-Batch FEHLER ({e}), ueberspringe {len(batch_desigs)}')
            hits = {}
        for k, d in zip(batch_keys, batch_desigs):
            entry = hits.get(d)
            if entry:
                wd_labels[k] = {l: entry[l] for l in LANGS if l in entry}
        print(f'  Wikidata {min(i + BATCH_SIZE, len(new_keys))}/{len(new_keys)}')
        time.sleep(BATCH_DELAY_SEC)

    report_new = []
    with_translation = 0
    for k in new_keys:
        cand = plausible[k]
        entry = {'name': cand['name'], 'src': 'Stellarium'}
        wd_entry = wd_labels.get(k, {})
        translations = []
        for lang, label in wd_entry.items():
            if lang != 'en' and is_plausible_common_name(label):
                entry[lang] = label
                translations.append(lang)
        if translations:
            with_translation += 1
        current[k] = entry
        report_new.append({
            'key': k, 'raw_desig': cand['raw_desig'], 'name': cand['name'],
            'stellarium_sources': cand['sources'], 'translations': translations,
        })

    outpath = os.path.join(CAT, 'dso_names.json')
    with open(outpath, 'w', encoding='utf-8') as f:
        json.dump(current, f, ensure_ascii=False, indent=1, sort_keys=True)
        f.write('\n')

    json.dump(
        {
            'already_marked': already_keys,
            'newly_marked_count': marked,
            'new_entries': report_new,
            'new_with_translation_count': with_translation,
        },
        open(REPORT, 'w', encoding='utf-8'), ensure_ascii=False, indent=1,
    )

    print(f'\n  {len(new_keys)} neue Objekte ergaenzt ({with_translation} davon mit mind. 1 Uebersetzung)')
    print(f'  {marked} bestehende Objekte mit "src":"Stellarium" markiert')
    print(f'  Geschrieben -> {outpath} ({len(current)} Objekte gesamt)')


if __name__ == '__main__':
    main()
