#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Ergaenzt dso_names.json um Populaernamen, deren Katalogbezeichnung Stellariums
"nebulae/default/names.dat" (github.com/Stellarium/stellarium, GPL-2.0) fuehrt, unser bisheriger
Wikidata-Sweep (build_dso_names.py) aber NICHT fand (Nutzerbefund 2026-08-18: IC 1795 "Fish Head
Nebula" fehlte trotz vollstaendigem Wikidata-Durchlauf -- Wikidata selbst hat den Namen schlicht
nicht, Wikipedia dagegen schon).

WICHTIG (Lizenz): Stellariums Datendatei ist Teil eines GPL-2.0-Projekts. Sie wird hier NICHT
kopiert/redistributiert, sondern nur als FUNDSTELLEN-INDEX genutzt (welche Katalogbezeichnung hat
vermutlich einen Populaernamen, und welche Primaerquelle nennt Stellarium dafuer) -- jeder
uebernommene Name wird eigenstaendig direkt an einer unabhaengigen, frei nutzbaren Primaerquelle
verifiziert (Wikidata P528, SIMBAD-Kennungstabelle per TAP, oder Wikipedia-Volltextsuche), nicht aus
Stellariums Text uebernommen. Kandidaten ganz ohne eigenstaendige Bestaetigung werden verworfen.

Ablauf:
  1. tools/rawdata/stellarium_names.dat parsen (Format: s. Dateikopf dort), gegen unseren eigenen
     Katalog (dsos.20.json) abgleichen, auf Bezeichnungen filtern, die NICHT bereits in
     dso_names.json stehen. Bei mehreren Namen je Objekt: die mit den meisten Quellenbelegen zuerst.
  2. Wikidata P528 gebatcht fuer ALLE Kandidaten abfragen (wiederverwendet build_dso_names.py) --
     liefert sowohl ein Bestaetigungssignal (Label/Alias == Kandidat) als auch, unabhaengig davon,
     Uebersetzungen fuer jedes gefundene Item in den anderen 6 Sprachen.
  3. SIMBAD-Kennungstabelle gebatcht per TAP/ADQL abfragen (sim-tap/sync) -- Kandidat gilt als
     bestaetigt, wenn eine bekannte Kennung (ggf. mit SIMBAD-eigenem "NAME "-Praefix) dem Kandidaten
     entspricht.
  4. Fuer verbleibende, noch unbestaetigte Kandidaten: Wikipedia-Volltextsuche (en) -- Treffer zaehlt,
     wenn Titel/Ausschnitt sowohl den Kandidatennamen als auch die Katalogbezeichnung enthaelt.
  5. Nur Kandidaten mit mindestens einer Bestaetigung UND bestehender is_plausible_common_name-Pruefung
     werden uebernommen.

Aufruf: python tools/build_dso_names_stellarium.py
"""
import json
import os
import re
import sys
import time
import urllib.request
import urllib.parse

# Windows-Konsole faellt sonst auf die System-Codepage (z.B. cp1252) zurueck, auch bei Umleitung in
# eine Log-Datei -- ein Kandidatenname mit z.B. griechischem Buchstaben ("λ") liess frueher schon
# das FEHLER-Print selbst abstuerzen (UnicodeEncodeError, live beobachtet), nicht nur die eigentliche
# Netzwerklogik. UTF-8 erzwingen behebt das an der Wurzel fuer alle print()-Aufrufe im Skript.
if sys.stdout.encoding and sys.stdout.encoding.lower() != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
CAT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'catalog')
RAWFILE = os.path.join(HERE, 'rawdata', 'stellarium_names.dat')
CHECKPOINT = os.path.join(HERE, 'dso_names.stellarium_checkpoint.json')
REPORT = os.path.join(HERE, 'dso_names.stellarium_report.json')

sys.path.insert(0, HERE)
from build_dso_names import (  # noqa: E402
    LANGS, normalize, is_plausible_common_name, load_catalog_designations,
    wikidata_query_by_desig, BATCH_SIZE, BATCH_DELAY_SEC,
)

NAME_LINE_RE = re.compile(r'_\("(.+?)"\)\s*(?:#\s*(.*))?\s*$')


def parse_stellarium_file(path):
    """Liest names.dat, liefert dict normalisierte_Bezeichnung -> Liste von
    {'raw_desig':, 'name':, 'sources': [...] }, sortiert je Bezeichnung nach Quellenzahl absteigend."""
    by_key = {}
    with open(path, encoding='utf-8', errors='replace') as f:
        for line in f:
            if not line.strip() or line.startswith('#'):
                continue
            prefix = line[0:5].strip()
            obj_id = line[5:20].strip()
            rest = line[20:]
            m = NAME_LINE_RE.search(rest)
            if not m or not prefix or not obj_id:
                continue
            name, src = m.group(1).strip(), (m.group(2) or '').strip()
            sources = [s.strip() for s in src.split(',') if s.strip()]
            raw_desig = f'{prefix} {obj_id}'
            key = normalize(raw_desig)
            by_key.setdefault(key, []).append({
                'raw_desig': raw_desig, 'name': name, 'sources': sources,
            })
    for key in by_key:
        by_key[key].sort(key=lambda e: -len(e['sources']))
    return by_key


def load_current_names():
    path = os.path.join(CAT, 'dso_names.json')
    with open(path, encoding='utf-8') as f:
        return json.load(f)


def _run_sparql_tap(query: str) -> dict:
    body = urllib.parse.urlencode({
        'request': 'doQuery', 'lang': 'adql', 'format': 'json', 'query': query,
    }).encode('utf-8')
    req = urllib.request.Request(
        'https://simbad.u-strasbg.fr/simbad/sim-tap/sync',
        data=body,
        headers={
            'Accept': 'application/json',
            'Content-Type': 'application/x-www-form-urlencoded',
            'User-Agent': 'SternbildMapper-DsoNamesBuild/2.0 (offline astrometry app, one-time asset build)',
        },
    )
    with urllib.request.urlopen(req, timeout=90) as resp:
        return json.load(resp)


def simbad_check_one(desig, attempt=0):
    """Liste bekannter alternativer Kennungen fuer EINE Bezeichnung (SIMBAD ident-Tabelle).

    BEWUSST kein Batch-IN(...) mit Rueckschluss ueber die zurueckgelieferte id1.id-Spalte: live
    beobachtet, dass SIMBAD fuer manche Praefixe (z.B. PGC) eine ANDERE, kanonische Schreibweise
    zurueckliefert (z.B. "LEDA 2807155" statt der abgefragten "PGC 2807155") -- eine Batch-Zuordnung
    ueber die Echo-Spalte waere dadurch falsch/mehrdeutig. Eine Abfrage pro Bezeichnung ist langsamer,
    aber eindeutig: der Aufrufer kennt die Bezeichnung bereits aus der eigenen Schleife, muss sie nicht
    aus der Antwort zurueckgewinnen."""
    query = "SELECT id2.id AS alt_id FROM ident id1 JOIN ident id2 ON id1.oidref = id2.oidref " \
            "WHERE id1.id = '%s'" % desig.replace("'", "''")
    try:
        data = _run_sparql_tap(query)
    except Exception as e:
        if attempt < 1:
            time.sleep(3)
            return simbad_check_one(desig, attempt + 1)
        print(f'    SIMBAD FEHLER fuer "{desig}" ({e})')
        return []
    return [row[0] for row in data.get('data', [])]


GENERIC_SUFFIXES = [
    'GALAXIES', 'GALAXY', 'NEBULAE', 'NEBULA', 'CLUSTERS', 'CLUSTER', 'GROUPS', 'GROUP',
    'REGION', 'COMPLEX', 'SYSTEM', 'ASSOCIATION', 'CLOUDS', 'CLOUD',
]


def _strip_generic_suffix(s):
    s = s.strip()
    upper = s.upper()
    for suf in GENERIC_SUFFIXES:
        if upper.endswith(' ' + suf):
            return s[: -(len(suf) + 1)].strip()
    return s


def _core(s):
    return normalize(_strip_generic_suffix(s))


def _fuzzy_core_equal(a_core, b_core, min_prefix_len=6):
    """Exakt gleich ODER (bei ausreichender Laenge) einer ein Praefix des anderen -- SIMBAD nennt
    denselben Namen oft nur verkuerzt/ohne generisches Suffix (live gefunden: SIMBAD "NAME Browning"
    vs. Stellariums "Browning Galaxy"; SIMBAD "NAME Cassiopeia Dwarf Galaxy" vs. "Cassiopeia Dwarf
    Spheroidal Galaxy") -- Mindestlaenge verhindert zufaellige Kurz-Praefix-Treffer."""
    if not a_core or not b_core:
        return False
    if a_core == b_core:
        return True
    shorter, longer = (a_core, b_core) if len(a_core) <= len(b_core) else (b_core, a_core)
    return len(shorter) >= min_prefix_len and longer.startswith(shorter)


def simbad_matches(candidate_name, alt_ids):
    cand_core = _core(candidate_name)
    for alt in alt_ids:
        alt_body = re.sub(r'^NAME\s+', '', alt.strip(), flags=re.IGNORECASE)
        if _fuzzy_core_equal(cand_core, _core(alt_body)):
            return True
    return False


def wikipedia_check(candidate_name, raw_desig, attempt=0):
    url = (
        'https://en.wikipedia.org/w/api.php?action=query&list=search'
        '&srsearch=' + urllib.parse.quote(f'"{candidate_name}"') +
        '&format=json&srlimit=3'
    )
    req = urllib.request.Request(url, headers={'User-Agent': 'SternbildMapper-DsoNamesBuild/2.0'})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.load(resp)
    except urllib.error.HTTPError as e:
        if e.code == 429 and attempt < 2:
            time.sleep(8 * (attempt + 1))
            return wikipedia_check(candidate_name, raw_desig, attempt + 1)
        print(f'    Wikipedia-Suche FEHLER fuer "{candidate_name}" ({e})')
        return False
    except Exception as e:
        print(f'    Wikipedia-Suche FEHLER fuer "{candidate_name}" ({e})')
        return False
    desig_norm = normalize(raw_desig)
    for hit in data.get('query', {}).get('search', []):
        title_norm = normalize(hit.get('title', ''))
        snippet = re.sub(r'<[^>]+>', '', hit.get('snippet', ''))
        if title_norm == normalize(candidate_name):
            return True
        if desig_norm in normalize(snippet):
            return True
    return False


def main():
    print('Parse Stellarium-Fundstellen-Index ...')
    stellarium = parse_stellarium_file(RAWFILE)
    print(f'  {len(stellarium)} Bezeichnungen mit mindestens einem Namensvorschlag')

    our_desigs = set(normalize(d) for d in load_catalog_designations())
    current = load_current_names()

    candidates = {}  # key -> {'raw_desig', 'name', 'sources'}
    for key, entries in stellarium.items():
        if key not in our_desigs or key in current:
            continue
        candidates[key] = entries[0]  # meiste Quellenbelege zuerst (s. Sortierung oben)
    print(f'  {len(candidates)} NEUE Kandidaten (im eigenen Katalog vorhanden, noch ohne Namen)')

    keys = sorted(candidates.keys())
    wd_labels = {}  # key -> {lang: label}
    accepted = {}
    report = {'accepted': [], 'rejected_no_confirmation': [], 'rejected_implausible': []}
    start = 0
    if os.path.exists(CHECKPOINT):
        cp = json.load(open(CHECKPOINT, encoding='utf-8'))
        start = cp.get('progress', 0)
        wd_labels = cp.get('wd_labels', {})
        accepted = cp.get('accepted', {})
        report = cp.get('report', report)
        print(f'  Checkpoint geladen, setze fort ab Index {start}/{len(keys)}')

    if not wd_labels:
        print('Frage Wikidata (P528) fuer alle Kandidaten ab (guenstige erste Bestaetigungsrunde) ...')
        for i in range(0, len(keys), BATCH_SIZE):
            batch_keys = keys[i:i + BATCH_SIZE]
            batch_desigs = [candidates[k]['raw_desig'] for k in batch_keys]
            try:
                hits = wikidata_query_by_desig(batch_desigs)
            except Exception as e:
                print(f'  Wikidata-Batch FEHLER ({e}), ueberspringe {len(batch_desigs)}')
                hits = {}
            for k, d in zip(batch_keys, batch_desigs):
                entry = hits.get(d)
                if entry:
                    wd_labels[k] = {l: entry[l] for l in LANGS if l in entry}
            print(f'  Wikidata {min(i + BATCH_SIZE, len(keys))}/{len(keys)}')
            time.sleep(BATCH_DELAY_SEC)

    def _checkpoint(progress):
        json.dump(
            {'progress': progress, 'wd_labels': wd_labels, 'accepted': accepted, 'report': report},
            open(CHECKPOINT, 'w', encoding='utf-8'), ensure_ascii=False,
        )

    print('Werte pro Kandidat aus (Wikidata zuerst, sonst SIMBAD, sonst Wikipedia) ...')
    for idx in range(start, len(keys)):
        key = keys[idx]
        cand = candidates[key]
        name, raw_desig, sources = cand['name'], cand['raw_desig'], cand['sources']
        if not is_plausible_common_name(name):
            report['rejected_implausible'].append({'key': key, 'name': name, 'sources': sources})
            continue

        wd_entry = wd_labels.get(key, {})
        method = None
        if any(normalize(v) == normalize(name) for v in wd_entry.values()):
            method = 'wikidata'
        else:
            alt_ids = simbad_check_one(raw_desig)
            time.sleep(0.4)
            if simbad_matches(name, alt_ids):
                method = 'simbad'
            elif wikipedia_check(name, raw_desig):
                method = 'wikipedia'
            time.sleep(0.8)

        if method is None:
            report['rejected_no_confirmation'].append({
                'key': key, 'name': name, 'sources': sources, 'stellarium_desig': raw_desig,
            })
        else:
            entry = {'name': name}
            for lang, label in wd_entry.items():
                if lang != 'en' and is_plausible_common_name(label):
                    entry[lang] = label
            accepted[key] = entry
            report['accepted'].append({
                'key': key, 'name': name, 'sources': sources, 'verified_via': method,
                'translations': [l for l in entry if l != 'name'],
            })

        if (idx + 1) % 20 == 0:
            print(f'  ausgewertet {idx + 1}/{len(keys)}, bisher akzeptiert {len(accepted)}')
            _checkpoint(idx + 1)

    current.update(accepted)
    outpath = os.path.join(CAT, 'dso_names.json')
    with open(outpath, 'w', encoding='utf-8') as f:
        json.dump(current, f, ensure_ascii=False, indent=1, sort_keys=True)
        f.write('\n')
    json.dump(report, open(REPORT, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)

    print(f'\n  {len(accepted)} neue Objekte akzeptiert (davon per Wikidata: '
          f"{sum(1 for r in report['accepted'] if r['verified_via'] == 'wikidata')}, "
          f"SIMBAD: {sum(1 for r in report['accepted'] if r['verified_via'] == 'simbad')}, "
          f"Wikipedia: {sum(1 for r in report['accepted'] if r['verified_via'] == 'wikipedia')})")
    print(f"  {len(report['rejected_no_confirmation'])} ohne jede Bestaetigung verworfen")
    print(f"  {len(report['rejected_implausible'])} als Katalogcode/unplausibel verworfen")
    print(f'  Geschrieben -> {outpath} ({len(current)} Objekte gesamt)')
    if os.path.exists(CHECKPOINT):
        os.remove(CHECKPOINT)


if __name__ == '__main__':
    main()
