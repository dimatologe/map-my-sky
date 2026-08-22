#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Ergaenzt assets/catalog/dsos.20.json um echte Neuzugaenge aus der SAC-8.1-Datenbank (Saguaro
Astronomy Club, Freeware, tools/rawdata/sac.csv), die im bestehenden Katalog KOMPLETT fehlen --
z.B. NGC 896, Cr 33/34 (Sternhaufen im Herznebel), praktisch der komplette Melotte-Katalog (bisher
0 Eintraege trotz eigenem Filter-Chip in der App, s. DeepSkyCatalogs.kt).

Ausgeschlossen bewusst:
  - NONEX: von SAC selbst als "existiert nicht" geflaggte historische Katalogfehler.
  - *STAR/ASTER (Einzel-/Mehrfachsterne, Asterismen): keine echten DSOs -- die App hat einen
    eigenen, viel vollstaendigeren Sternkatalog (stars.*.json) fuer Einzelsterne.

Nur Neuzugaenge: ein SAC-Objekt gilt als "bereits bekannt", wenn seine OBJECT- ODER OTHER-Spalte
(normalisiert wie appnorm()) mit einer bestehenden Bezeichnung (desig ODER id) im Katalog
uebereinstimmt -- dann wird es NICHT importiert (verhindert Dubletten wie IC 1805 == Mel 15/Cr 26).

RA/Dec-Konvention gegen bestehende Eintraege verifiziert (z.B. M 8: reale RA 270.95 Grad -> Datei
enthaelt -89.096 = 270.95-360): RA wird nach Grad umgerechnet und bei >180 Grad um 360 Grad in den
Bereich (-180, 180] gewrappt. Dec bleibt vorzeichenbehaftet wie berechnet (-90..+90), kein Wrap.

Aufruf: python tools/build_dsos_merge.py
"""
import json
import re
import os
import csv
import collections

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)  # work/
RAW = os.path.join(HERE, 'rawdata')
CAT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'catalog')
DSOS_PATH = os.path.join(CAT, 'dsos.20.json')
SAC_PATH = os.path.join(RAW, 'sac.csv')

EXCLUDE_TYPES = {'NONEX', '1STAR', '2STAR', '3STAR', '4STAR', '8STAR', 'ASTER'}

# SAC-TYPE -> App-eigene Typ-Konvention (s. AstapOverlayMapper.dsoStyleForType: g/s/s0/sd/i/e/gg =
# Galaxie*Cyan, gc = Kugelsternhaufen, oc = offener Sternhaufen, en/bn/sfr/rn/pn/snr/dn = Nebel).
TYPE_MAP = {
    'GALXY': 'g',
    'OPNCL': 'oc',
    'GLOCL': 'gc',
    'PLNNB': 'pn',
    'BRTNB': 'bn',
    'DRKNB': 'dn',
    'SNREM': 'snr',
    'GALCL': 'gg',   # Galaxienhaufen (Abell) -- wie bestehende ACO-Eintraege bereits getypt
    'CL+NB': 'sfr',  # Haufen+Nebel-Komplex -- wie bestehendes IC 1805 bereits getypt
    'LMCOC': 'oc',
    'LMCCN': 'sfr',
    'LMCDN': 'dn',
    'SMCDN': 'dn',
    'SMCCN': 'sfr',
    'GX+DN': 'g',
    'G+C+N': 'g',
    'QUASR': 'g',
}


def appnorm(s: str) -> str:
    """EXAKT wie App-normalizeDesignation (AstapOverlayMapper.kt): trim, uppercase, ohne [\\s-_]."""
    return re.sub(r'[\s\-_]', '', (s or '').strip().upper())


def abell_to_aco_alias(n: str) -> str:
    """n ist bereits appnorm()-normalisiert. NUR fuer SAC-Zeilen mit TYPE=GALCL aufrufen (s.
    Aufrufstelle) -- Abell-Galaxienhaufen ("Abell 262") fuehrt dsos.20.json unter "ACO 262" (2726x
    bestehende Eintraege). ACHTUNG, Lektion aus Runde 1 dieses Skripts: George Abell hat ZWEI
    komplett verschiedene Kataloge mit derselben Nummerierung veroeffentlicht (Galaxienhaufen 1958,
    UNABHAENGIG davon Planetarische Nebel 1966) -- eine SAC-Zeile mit OBJECT="PK ...",
    OTHER-Kreuzverweis "Abell 48", TYPE=PLNNB meint ein VOELLIG ANDERES Objekt als ein eventuell
    bereits vorhandenes "ACO 48" (Galaxienhaufen)! Ein blindes, typunabhaengiges Alias haette in der
    ersten Fassung 60 echte planetarische Nebel faelschlich als "schon bekannt" verworfen, weil ihr
    OTHER-Kreuzverweis zufaellig auf eine ACO-Nummer eines UNVERWANDTEN Galaxienhaufens "traf"."""
    if n.startswith('ABELL'):
        return 'ACO' + n[len('ABELL'):]
    return n


def clean_designation(s: str) -> str:
    """Fixed-width-Innenpolsterung ('NGC  896') auf einfache Leerzeichen normalisieren, fuer die
    ANZEIGE (nicht fuer den Abgleich -- der laeuft ueber appnorm())."""
    return re.sub(r'\s+', ' ', (s or '').strip())


def parse_ra(hms: str):
    """'02 25.5' (Std Min.m) -> Dezimalgrad, nach (-180,180] gewrappt (Dateikonvention)."""
    m = re.match(r'^\s*(\d+)\s+([\d.]+)\s*$', hms or '')
    if not m:
        return None
    hh, mm = float(m.group(1)), float(m.group(2))
    deg = (hh + mm / 60.0) * 15.0
    if deg > 180.0:
        deg -= 360.0
    return deg


def parse_dec(dms: str):
    """'+62 01' / '-05 30' (Grad Bogenmin) -> vorzeichenbehaftetes Dezimalgrad."""
    m = re.match(r'^\s*([+-]?\d+)\s+([\d.]+)\s*$', dms or '')
    if not m:
        return None
    deg_tok, mm = m.group(1), float(m.group(2))
    sign = -1.0 if deg_tok.strip().startswith('-') else 1.0
    deg = abs(float(deg_tok))
    return sign * (deg + mm / 60.0)


def parse_size(v: str):
    """SAC-Groesse ('  27   m' / '13.5s' / '0.3d') -> Bogenminuten (identisch zu
    tools/build_dso_shapes.py:_arcmin, fuer Konsistenz mit der bereits gemergten dso_shapes.json)."""
    if not v:
        return None
    v = v.strip().lower()
    if not v or v in ('-', '--'):
        return None
    unit = 'm'
    if v.endswith('s'):
        unit, v = 's', v[:-1]
    elif v.endswith('m'):
        unit, v = 'm', v[:-1]
    elif v.endswith('d'):
        unit, v = 'd', v[:-1]
    try:
        x = float(v)
    except ValueError:
        return None
    if unit == 's':
        x /= 60.0
    elif unit == 'd':
        x *= 60.0
    return round(x, 3) if x > 0 else None


def fmt_num(x):
    """Kompakte Zahl-Formatierung ohne unnoetige Nachkommastellen (matcht bestehenden Dateistil)."""
    r = round(x, 4)
    return int(r) if r == int(r) else r


def load_known_designations():
    with open(DSOS_PATH, encoding='utf-8') as fh:
        data = json.load(fh)
    known = set()
    for f in data['features']:
        known.add(appnorm(f.get('properties', {}).get('desig', '')))
        known.add(appnorm(f.get('id', '')))
    known.discard('')
    return data, known


def main():
    print("Lade bestehenden Katalog ...")
    data, known = load_known_designations()
    print(f"  {len(data['features'])} bestehende Objekte, {len(known)} bekannte Bezeichnungen")

    print("Lese SAC 8.1 ...")
    with open(SAC_PATH, encoding='latin-1', newline='') as fh:
        rdr = csv.DictReader(fh)
        rdr.fieldnames = [h.strip() for h in (rdr.fieldnames or [])]
        rows = list(rdr)
    print(f"  {len(rows)} SAC-Zeilen")

    new_features = []
    stats = collections.Counter()
    for row in rows:
        obj_raw = row.get('OBJECT') or ''
        obj = clean_designation(obj_raw)
        other = (row.get('OTHER') or '').strip()
        sac_type = (row.get('TYPE') or '').strip()
        if not obj:
            continue
        if sac_type in EXCLUDE_TYPES:
            stats['excluded_type'] += 1
            continue
        obj_n = appnorm(obj)
        other_tokens = [appnorm(t) for t in re.split(r'[;/]', other) if t.strip()]
        candidates = [obj_n] + other_tokens
        if sac_type == 'GALCL':
            # NUR hier (s. abell_to_aco_alias-Kommentar): SAC schreibt Abell-Galaxienhaufen aus,
            # dsos.20.json fuehrt dieselben Objekte unter "ACO ...".
            candidates += [abell_to_aco_alias(c) for c in candidates]
        if any(c in known for c in candidates):
            stats['already_known'] += 1
            continue
        app_type = TYPE_MAP.get(sac_type)
        if app_type is None:
            stats['unmapped_type_skipped'] += 1
            continue
        ra = parse_ra(row.get('RA'))
        dec = parse_dec(row.get('DEC'))
        if ra is None or dec is None:
            stats['bad_coords'] += 1
            continue
        maj = parse_size(row.get('SIZE_MAX'))
        minr = parse_size(row.get('SIZE_MIN'))
        if maj and minr:
            dim = f"{fmt_num(maj)}x{fmt_num(minr)}"
        elif maj:
            dim = f"{fmt_num(maj)}"
        else:
            dim = ""
        mag = (row.get('MAG') or '').strip()
        morph = clean_designation(row.get('CLASS') or '')
        feature = {
            "type": "Feature",
            "id": obj,
            "properties": {
                "desig": obj,
                "type": app_type,
                "morph": morph,
                "mag": mag,
                "dim": dim,
                "bv": "",
            },
            "geometry": {"type": "Point", "coordinates": [fmt_num(ra), fmt_num(dec)]},
        }
        new_features.append(feature)
        known.add(obj_n)  # verhindert Dubletten, falls OBJECT innerhalb SAC selbst mehrfach vorkaeme
        stats['imported'] += 1

    print("\n=== Ergebnis ===")
    for k, v in sorted(stats.items()):
        print(f"  {k}: {v}")
    print(f"  NEU importiert: {len(new_features)}")

    data['features'].extend(new_features)
    with open(DSOS_PATH, 'w', encoding='utf-8') as fh:
        json.dump(data, fh, ensure_ascii=False, separators=(',', ':'))
    print(f"\n  Geschrieben -> {DSOS_PATH} ({len(data['features'])} Objekte gesamt)")


if __name__ == '__main__':
    main()
