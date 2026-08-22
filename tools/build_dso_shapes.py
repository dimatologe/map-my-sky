#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Baut assets/catalog/dso_shapes.json aus GEMEINFREIEN Form-Fakten (Groesse + Positionswinkel).

Quellen (license-frei / Fakten, nicht schuetzbar):
  - RC3 (VII/155, de Vaucouleurs+ 1991, via CDS) -> Galaxien: logD25/logR25/PA
  - SAC Deep-Sky Database (Freeware, optional) -> alle Typen: size_max/size_min/PA
  - tools/dso_shapes.seed.json -> handverlesene Overrides (hoechste Prioritaet; z.B. Veil-Korrektur)

Es wird NUR emittiert, was einen Mehrwert bringt:
  - orientierte Ellipse {maj,min,pa}, wenn min+pa vorhanden, laenglich (>=RATIO_MIN) und KEIN Haufen
  - Seed-Overrides unveraendert (koennen auch maj-only = Groessenkorrektur sein)
Runde Objekte/ohne PA bleiben weg -> die App zeichnet ihren Katalog-Kreis (Altverhalten).

Der Key ist IDENTISCH zu AstapOverlayMapper.normalizeDesignation(desig) der App
(uppercase, ohne Leer-/Bindestrich/Unterstrich) -> Lookup trifft sicher.
"""
import json, re, os, sys, collections

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)                       # work/
RAW  = os.path.join(HERE, 'rawdata')
CAT  = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'catalog')

RATIO_MIN  = 1.15         # ab hier orientierte Ellipse (nur wenn PA bekannt)
RATIO_CIRC = 1.4          # laenglich ohne PA -> groessenkorrigierter Kreis (geom. Mittel) statt Riesen-Kreis
GAL_TYPES   = {'g','s','s0','sd','i','e','gg'}
CLUSTER_TYPES = {'gc','oc','cl','ocl','gcl'}

def appnorm(s: str) -> str:
    """EXAKT wie App-normalizeDesignation: trim, uppercase, ohne [\\s-_]."""
    return re.sub(r'[\s\-_]', '', (s or '').strip().upper())

def canon(s: str) -> str:
    """Lockerer Match-Key: appnorm + Fuehrungsnullen je Ziffernblock strippen (RC3 'IC 0342' == 'IC 342')."""
    s = appnorm(s)
    return re.sub(r'0*(\d+)', lambda m: m.group(1), s)

def rnd(x):
    r = round(float(x), 1)
    return int(r) if r == int(r) else r

# ---------------------------------------------------------------- RC3 (Galaxien)
def parse_rc3():
    idx = {}
    path = os.path.join(RAW, 'rc3')
    if not os.path.exists(path):
        print("  [RC3] Datei fehlt -> uebersprungen")
        return idx
    n = 0
    for line in open(path, encoding='latin-1'):
        line = line.rstrip('\n')
        if len(line) < 188:
            line = line.ljust(188)
        name    = line[62:74].strip()     # NGC/IC
        altname = line[74:89].strip()     # UGC/ESO/MCG...
        pgc     = line[105:116].strip()   # PGC nnn
        d25     = line[151:155].strip()   # log D25 (0.1 arcmin)
        r25     = line[161:165].strip()   # log R25 (a/b)
        pa      = line[185:188].strip()   # deg, N->E, <180
        if not d25:
            continue
        try:
            maj = round(10 ** float(d25) * 0.1, 2)   # arcmin
        except ValueError:
            continue
        if maj <= 0:
            continue
        ratio = None
        if r25:
            try: ratio = 10 ** float(r25)
            except ValueError: ratio = None
        mn = round(maj / ratio, 2) if ratio else None
        pav = None
        if pa:
            try: pav = int(pa)
            except ValueError: pav = None
        shape = {'maj': maj, 'min': mn, 'pa': pav}
        for desig in (name, altname, pgc):
            if desig:
                idx.setdefault(canon(desig), shape)
        n += 1
    print(f"  [RC3] {n} Galaxien, {len(idx)} Match-Keys")
    return idx

# ---------------------------------------------------------------- SAC (alle Typen, optional)
def parse_sac():
    idx = {}
    path = None
    for cand in ('sac.tsv', 'sac.csv', 'sac.txt'):
        p = os.path.join(RAW, cand)
        if os.path.exists(p):
            path = p; break
    if not path:
        print("  [SAC] Datei fehlt -> uebersprungen (nur RC3+Seed)")
        return idx
    import csv
    with open(path, encoding='latin-1', newline='') as fh:
        sample = fh.read(4096); fh.seek(0)
        delim = '\t' if sample.count('\t') > sample.count(',') else ','
        rdr = csv.DictReader(fh, delimiter=delim)
        cols = {c.lower().strip(): c for c in (rdr.fieldnames or [])}
        def col(*names):
            for nm in names:
                if nm in cols: return cols[nm]
            return None
        c_obj = col('object','obj','name')
        c_other = col('other','other name','altname')
        c_smax = col('size_max','sizemax','size max','majax','maj')
        c_smin = col('size_min','sizemin','size min','minax','min')
        c_pa   = col('pa','posang','position angle')
        n = 0
        for row in rdr:
            smax = _arcmin(row.get(c_smax)) if c_smax else None
            smin = _arcmin(row.get(c_smin)) if c_smin else None
            pav  = _int(row.get(c_pa)) if c_pa else None
            if not smax:
                continue
            shape = {'maj': smax, 'min': smin, 'pa': pav}
            for d in ((row.get(c_obj) or ''), (row.get(c_other) or '')):
                for token in re.split(r'[;/]', d):
                    token = token.strip()
                    if token:
                        idx.setdefault(canon(token), shape)
            n += 1
    print(f"  [SAC] {n} Objekte, {len(idx)} Match-Keys")
    return idx

def _arcmin(v):
    """SAC-Groesse: '16.2m'/'7.4'/'45s' -> Bogenminuten."""
    if not v: return None
    v = v.strip().lower()
    if not v or v in ('-', '--'): return None
    unit = 'm'
    if v.endswith('s'): unit = 's'; v = v[:-1]
    elif v.endswith('m'): unit = 'm'; v = v[:-1]
    elif v.endswith('d'): unit = 'd'; v = v[:-1]
    try: x = float(v)
    except ValueError: return None
    if unit == 's': x /= 60.0
    elif unit == 'd': x *= 60.0
    return round(x, 2) if x > 0 else None

def _int(v):
    if not v: return None
    v = v.strip()
    try: return int(float(v))
    except ValueError: return None

# ---------------------------------------------------------------- Merge
def main():
    print("Lade Katalog dsos.20.json ...")
    dsos = json.load(open(os.path.join(CAT, 'dsos.20.json'), encoding='utf-8'))['features']
    seed_raw = json.load(open(os.path.join(HERE, 'dso_shapes.seed.json'), encoding='utf-8'))
    # Seed laden; interne _note/_-Felder je Eintrag entfernen (nur maj/min/pa gehen in die Ausgabe).
    seed = {
        appnorm(k): {kk: vv for kk, vv in v.items() if not kk.startswith('_')}
        for k, v in seed_raw.items() if not k.startswith('_')
    }
    comment = seed_raw.get('_comment', '')

    print("Parse Quellen ...")
    rc3 = parse_rc3()
    sac = parse_sac()

    out = {}
    st = collections.Counter()
    for f in dsos:
        p = f['properties']
        desig = (p.get('desig') or '').strip()
        if not desig:
            continue
        ak = appnorm(desig); ck = canon(desig)
        t = (p.get('type') or '').lower()

        if ak in seed:                       # Hand-Override gewinnt
            out[ak] = seed[ak]; st['seed'] += 1; continue

        # Zusaetzlich ueber die Feature-id matchen: dsos.20 fuehrt bei Messier die NGC-Nummer als id
        # (M 31 -> id "NGC 224"), RC3/SAC schluesseln nach NGC -> so treffen auch Messier-Objekte.
        fid = str(f.get('id') or '').strip()
        ck_id = canon(fid) if fid else None
        def look(idx):
            return idx.get(ck) or (idx.get(ck_id) if ck_id else None)

        if t in GAL_TYPES:
            shape = look(rc3) or look(sac)
        else:
            shape = look(sac) or look(rc3)
        if not shape:
            continue
        maj, mn, pa = shape.get('maj'), shape.get('min'), shape.get('pa')
        if not maj or maj <= 0:
            continue
        if t in CLUSTER_TYPES:               # Haufen -> Kreis, kein Ellipsen-Zwang
            st['skip_cluster'] += 1; continue
        ratio = (maj / mn) if mn else 1.0
        if mn and pa is not None and ratio >= RATIO_MIN:
            # Volle orientierte Ellipse (Groesse + Winkel bekannt) -> v.a. Galaxien (RC3-PA).
            out[ak] = {'maj': rnd(maj), 'min': rnd(mn), 'pa': int(pa)}
            st['ellipse'] += 1
        elif mn and ratio >= RATIO_CIRC:
            # Laenglich, aber KEIN Positionswinkel bekannt (typisch Nebel): keine Orientierung erfinden,
            # aber auch nicht als Riesen-Kreis an der langen Achse zeichnen -> effektiver (geom. Mittel-)Durchmesser.
            import math
            out[ak] = {'maj': rnd(math.sqrt(maj * mn))}
            st['circle_sizecorr'] += 1
        else:
            st['skip_round'] += 1

    # Seed-Eintraege, die NICHT im Katalog vorkamen, trotzdem behalten (z.B. exotische Bezeichnung)
    for k, v in seed.items():
        out.setdefault(k, v);
    # deterministische, lesbare Ausgabe (nach Key sortiert)
    ordered = collections.OrderedDict()
    ordered['_comment'] = ("GENERIERT von tools/build_dso_shapes.py (nicht von Hand editieren; Overrides in tools/dso_shapes.seed.json). "
        "Quellen: RC3 (VII/155, de Vaucouleurs+ 1991, via NASA/CDS) fuer Galaxien-maj/min/PA; "
        "SAC Deep-Sky Database 8.1 (Saguaro Astronomy Club, Freeware) fuer Nebel/PN/SNR-Groessen; Hand-Seed fuer Korrekturen. "
        "maj=grosse Achse (Bogenmin), min=kleine Achse, pa=Positionswinkel Grad Nord->Ost. maj+min+pa=orientierte Ellipse; "
        "nur maj=Kreis in korrigierter Groesse. Gemeinfreie Messfakten (nicht urheberrechtlich schuetzbar).")
    for k in sorted(out.keys()):
        if float(out[k].get('maj', 0)) >= 0.1:   # degenerierte Winzeintraege (auf 0 gerundet) verwerfen
            ordered[k] = out[k]

    outpath = os.path.join(CAT, 'dso_shapes.json')
    with open(outpath, 'w', encoding='utf-8') as fh:
        json.dump(ordered, fh, ensure_ascii=False, indent=1)
        fh.write('\n')

    total = len(out)
    print("\n=== Ergebnis ===")
    print(f"  Ellipsen (RC3/SAC):     {st['ellipse']}")
    print(f"  Groessenkorr. Kreise:   {st['circle_sizecorr']}")
    print(f"  Seed-Overrides:         {st['seed']}")
    print(f"  uebersprungen Haufen:   {st['skip_cluster']}")
    print(f"  uebersprungen rund:     {st['skip_round']}")
    print(f"  GESAMT Eintraege:       {total}")
    print(f"  geschrieben -> {outpath}")

if __name__ == '__main__':
    main()
