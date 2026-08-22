#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Baut assets/catalog/dso_names.json: DSO-Populaernamen (z.B. "Andromeda-Galaxie" statt "M 31") in
allen 7 App-Sprachen.

Runde 2 (2026-08-18): Hauptquelle ist jetzt Wikidata SPARQL (query.wikidata.org), Property P528
"catalog number" -- abgefragt gegen den GESAMTEN eigenen Katalog (dsos.20.json, alle desig/id-Werte,
~92.000 eindeutige Bezeichnungen), NICHT nur eine vorgefilterte externe Liste. Grund fuer den
Architekturwechsel: die urspruengliche, ausschliessliche Quelle (OpenNGCs "Common names"-Spalte)
erwies sich als nachweislich unvollstaendig -- selbst Herznebel (IC 1805) und Seelennebel (IC 1848)
fehlten dort, obwohl beide bei Wikidata korrekt mit Klarnamen hinterlegt sind (Nutzerbefund
2026-08-18).

Zwei Wikidata-Durchgaenge pro Objekt:
  1. Primaeres Label (rdfs:label) je Sprache -- der Regelfall (z.B. IC 1805 -> "Heart Nebula").
  2. Wo Durchgang 1 KEINEN brauchbaren Namen liefert (Label fehlt oder ist selbst nur eine
     Katalogbezeichnung), aber ein Wikidata-Item ueber P528 gefunden wurde: englischer Alias
     (skos:altLabel) desselben Items -- live noetig verifiziert (NGC 281 "Pacman Nebula" steht dort
     NUR als Alias, das primaere Label ist buchstaeblich "NGC 281"). Fuer einen brauchbaren Alias
     werden zusaetzlich die primaeren Labels desselben Items in den uebrigen 6 Sprachen mitgenommen.
Kriterium fuer "brauchbarer Name" in beiden Durchgaengen: sieht NICHT wie eine reine
Katalogbezeichnung aus (s. looks_like_catalog_code, inkl. ausgeschriebener Formen wie "Caldwell 50").

OpenNGC (github.com/mattiaverga/OpenNGC, CC-BY-SA-4.0), Spalte "Common names" in NGC.csv, bleibt als
DRITTE, ergaenzende Quelle bestehen -- deckt den (seltenen) Fall ab, dass ein Objekt dort einen Namen
traegt, aber gar keinen verknuepften Wikidata-Eintrag mit P528 hat.

Der Key ist NORMALISIERT wie AstapOverlayMapper.normalizeDesignation (uppercase, ohne Leerzeichen/
Bindestrich/Unterstrich) -> Lookup trifft sicher, identisches Muster zu DsoShapeLoader/starnames.json.

Wikidata-Abfrage in Batches (SPARQL-VALUES-Groessenlimit), mit Pause zwischen Anfragen (Nutzungs-
richtlinie) und Zwischenspeicherung nach jedem Batch (Absicherung gegen Abbruch bei einem so langen
Lauf, ~90 Batches fuer den vollen Katalog).

Aufruf: python tools/build_dso_names.py
"""
import json
import re
import os
import csv
import time
import urllib.request
import urllib.parse

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)  # work/
RAW = os.path.join(HERE, 'rawdata')
CAT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'catalog')
CHECKPOINT = os.path.join(HERE, 'dso_names.wikidata_checkpoint.json')

LANGS = ['en', 'de', 'zh', 'es', 'ru', 'ar', 'ja']
BATCH_SIZE = 1000
BATCH_DELAY_SEC = 1.0

# Bekannte Katalog-Praefixe (normalisiert, ohne Trennzeichen) -- ein Wikidata-Label/Alias, das direkt
# mit einem dieser Praefixe + Ziffer beginnt, ist selbst nur eine (andere) Katalogbezeichnung, kein
# Populaername (z.B. Label "M57" fuer irgendeine Query, oder Alias "Caldwell 50" -- beide verwerfen).
# Kurzform, ausgeschriebene Form UND volle Katalogbezeichnung je Katalog, wo ueblich -- live an ECHTEN
# Faellen verifiziert: NGC 2244 hat Alias "Caldwell 50" (ohne CALDWELL faelschlich durchgerutscht),
# NGC 7635 hat Alias "New General Catalogue 7635" (ohne NEWGENERALCATALOGUE ebenso durchgerutscht).
CATALOG_CODE_RE = re.compile(
    r'^(NEWGENERALCATALOGUE|NEWGENERALGALAXY|NGC|INDEXCATALOGUE|INDEXCATALOG|IC|PRINCIPALGALAXIESCATALOGUE|PGC|'
    r'UGCA|UGC|ESO|SHARPLESS|SH2|SH|COLLINDER|CR|MELOTTE|MEL|LDN|LBN|CEDERBLAD|CED|'
    r'BARNARD|ABELL|ACO|PK|PN|ARP|VV|HCG|HICKSON|HICK|TON|MRK|MARKARIAN|VDB|GUM|RCW|DWB|WR|'
    r'TRUMPLER|TR|RUPRECHT|RU|KING|STOCK|BERKELEY|BERK|DOLIDZE|PISMIS|HOGG|HODGE|LYNGA|HAFFNER|BASEL|'
    r'CALDWELL|MESSIER|SIMEIS|SA|MCG|ZWG|ZWICKY|CGCG|IRAS|KUG|HOLM|B|C|M)\d',
)

# SIMBAD markiert informelle Bezeichner intern mit dem Literal-Praefix "NAME " -- Wikidata importiert
# das teils WOERTLICH als primaeres Label/Alias (live gefunden: "NAME Orion Loop", "NAME Golf of
# Mexico", "NAME Basel 11A"). Ungestrippt hebelt das Praefix CATALOG_CODE_RE aus, weil dessen ^-Anker
# dann vor dem eigentlichen (oft selbst katalogartigen) Text sitzt statt davor.
NAME_PREFIX_RE = re.compile(r'^NAME\s+', re.IGNORECASE)


def strip_name_prefix(label: str) -> str:
    return NAME_PREFIX_RE.sub('', (label or '').strip()).strip()

# Reale Populaernamen sind praktisch immer kurz (2-5 Woerter, "The Bubble Nebula" etc.) -- ein
# Wikidata-Label/Alias, das laenger als das ist, ist erfahrungsgemaess KEIN echter Name, sondern z.B.
# eine Kreuzreferenz-Liste ("MCG 4-2-9, ZWG 479.11, PGC 1383") oder ein zweifelhafter/unbelegter
# Spitzname mit Erklaertext (live gefunden: "aka The Glum Cyclops (nickname accredited to astronomer
# Stephen Saber)" fuer NGC 7134 -- liest sich nicht wie ein etabliertes Populaerlabel).
MAX_NAME_LENGTH = 45


def normalize(s: str) -> str:
    """EXAKT wie App-normalizeDesignation (AstapOverlayMapper.kt): trim, uppercase, ohne [\\s-_]."""
    return re.sub(r'[\s\-_]', '', (s or '').strip().upper())


def looks_like_catalog_code(label: str) -> bool:
    return bool(CATALOG_CODE_RE.match(normalize(label)))


# Einzelfaelle, die GENERIC_CODE_RE (unten) faelschlich verwerfen wuerde, weil sie derselben
# "Kuerzel/Nachname + Nummer"-Form folgen wie ein echter Katalogcode, aber live verifiziert etablierte,
# oeffentlich breit genutzte Populaernamen sind -- anders als z.B. "Terzan 12"/"Haro 30"/"Mz 3", die
# ausserhalb der Fachliteratur praktisch nie so genannt werden, IST die Katalogbezeichnung bei diesen
# Systemen bereits der gaengige Name: Westerlund-Sternhaufen (1 durch seine Masse/Groesse, 2 zusaetzlich
# durch eine Hubble-Jubilaeumsaufnahme bekannt) und Palomar-Kugelsternhaufen (3, 5 -- Letzterer u.a.
# wegen seines auffaelligen Gezeitenschweifs oft in Berichten genannt) werden durchgaengig unter genau
# dieser Bezeichnung referenziert. Nach NAME (normalisiert), nicht nach Katalogbezeichnung, weil
# derselbe Name an mehreren Objekten haengen kann.
NAME_ALLOWLIST = {'WESTERLUND1', 'WESTERLUND2', 'PALOMAR3', 'PALOMAR5'}

# Kuerzel/Nachname + Nummer, ggf. mit fuehrenden Ziffern vor dem Kuerzel selbst -- s. Docstring von
# is_plausible_common_name fuer Beispiele/Begruendung. Mit $ verankert (Nummer muss den Rest der
# Zeichenkette bis zum Ende ausfuellen, Mehrfachgruppen wie "07-17-001" via [\d.\-]* erlaubt) -- sonst
# faelschlich auch echte Namen wie "The 37 Cluster" getroffen, wo die Ziffer MITTEN im Namen steht und
# noch beschreibender Text folgt (live gefunden).
GENERIC_CODE_RE = re.compile(r'^\d{0,2}[A-Za-z]{1,10}[\s\-+]?\d[\d.\-]*$', re.IGNORECASE)


def is_plausible_common_name(label: str) -> bool:
    """Zusaetzlich zu looks_like_catalog_code: zu lang (s. MAX_NAME_LENGTH), mit "aka " beginnend
    (informelle Anmerkung, kein etabliertes Label) oder ein GENERISCHER Kreuzreferenz-Code wird
    ebenfalls verworfen -- zweite Verteidigungslinie zu CATALOG_CODE_RE, dessen Praefixliste nie alle
    Hunderte astronomischen Fachkataloge kennen kann: ein kurzes Kuerzel/ein Nachname, direkt gefolgt
    (ggf. mit Leerzeichen/Bindestrich/Plus dazwischen) von einer Ziffer (z.B. "MK 485", "KAZ 299",
    "FAIR 326", "A 1313+07", "8ZW 254", "MCG+07-17-001", "Haro 30", "Fath 703", "Mz 3", "Terzan 12" --
    live gefunden, allesamt Katalog-Kreuzreferenzen ohne echten Populaernamen). Case-insensitiv, weil
    Katalogcode-Praefixe wie "Haro"/"Fath"/"Mz" normal geschrieben in unseren Quellen auftauchen, nicht
    nur komplett grossgeschrieben wie "MK"/"KAZ". Optional 1-2 fuehrende Ziffern vor dem Kuerzel selbst
    erlaubt (deckt "3C 249.1", "8ZW 254" ab, wo das Kuerzel nicht am Anfang steht)."""
    label = (label or '').strip()
    if not label or looks_like_catalog_code(label):
        return False
    if len(label) > MAX_NAME_LENGTH:
        return False
    if label.lower().startswith('aka '):
        return False
    if normalize(label) not in NAME_ALLOWLIST and GENERIC_CODE_RE.match(label):
        return False
    # J-Koordinaten-Kennung (Himmelsposition als Bezeichnung, z.B. "MITG J2132+4435", "2MASS
    # J16292443-2625549") -- durchgaengiges Muster in rohen Survey-/SIMBAD-Kennungslisten diese
    # Sitzung, nie ein echter Populaername.
    if re.search(r'\bJ\d{3,}', label):
        return False
    return True


def _usable_translations(entry: dict) -> dict:
    """Nicht-englische Sprachlabels aus [entry], OHNE jene, die selbst nur eine Katalogbezeichnung
    sind (z.B. Item hat brauchbaren EN-Alias "Hercules Globular Cluster", aber deutsches primaeres
    Label bleibt schlicht "Messier 13" -- live beobachtet). properDisplayName() faellt fuer eine
    ausgelassene Sprache korrekt auf den (echten) englischen Namen zurueck statt einen unuebersetzten
    Katalogcode als "Populaername" auszugeben."""
    return {l: entry[l] for l in LANGS if l != 'en' and l in entry and is_plausible_common_name(entry[l])}


def spaced_desig(name: str) -> str:
    """"NGC0224" -> "NGC 224" (fuehrende Nullen der Nummer weg, wie unser eigener Katalog sie fuehrt)."""
    m = re.match(r'^([A-Za-z]+)0*(\d+.*)$', name.strip())
    if not m:
        return name.strip()
    return f"{m.group(1)} {m.group(2)}"


def load_catalog_designations():
    """Alle desig/id-Werte aus dsos.20.json -- unser eigener, voller Katalog (~92.000 eindeutige
    Bezeichnungen), NICHT nur eine externe Teilliste."""
    path = os.path.join(CAT, 'dsos.20.json')
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
    designations = set()
    for feature in data['features']:
        props = feature.get('properties', {})
        desig = (props.get('desig') or '').strip()
        obj_id = str(feature.get('id') or '').strip()
        if desig:
            designations.add(desig)
        if obj_id and not obj_id.isdigit():  # rein numerische Fallback-IDs ausschliessen (kein Katalogcode)
            designations.add(obj_id)
    return sorted(designations)


def load_openngc_common_name_rows():
    path = os.path.join(RAW, 'NGC.csv')
    rows = []
    with open(path, encoding='utf-8', newline='') as fh:
        rdr = csv.DictReader(fh, delimiter=';')
        for row in rdr:
            cn = (row.get('Common names') or '').strip()
            if not cn:
                continue
            m_num = (row.get('M') or '').strip()
            primary = f"M {int(m_num)}" if m_num else spaced_desig(row['Name'])
            rows.append({
                'primary': primary,
                'en_names': [n.strip() for n in cn.split(',') if n.strip()],
            })
    return rows


def _run_sparql(query: str) -> dict:
    # POST statt GET: bei BATCH_SIZE=1000 wird die Query gross genug, dass eine GET-URL an der
    # Server-/Proxy-Laenzenzengrenze scheitert (live beobachtet: HTTP 414 "URI Too Long" bei ALLEN
    # 92 Batches des ersten Laufs) -- der SPARQL-Query-Text im POST-Body kennt dieses Limit nicht.
    body = urllib.parse.urlencode({'query': query}).encode('utf-8')
    req = urllib.request.Request(
        'https://query.wikidata.org/sparql?format=json',
        data=body,
        headers={
            'Accept': 'application/sparql-results+json',
            'Content-Type': 'application/x-www-form-urlencoded',
            'User-Agent': 'SternbildMapper-DsoNamesBuild/2.0 (offline astrometry app, one-time asset build)',
        },
    )
    with urllib.request.urlopen(req, timeout=90) as resp:
        return json.load(resp)


def wikidata_query_by_desig(desigs):
    """Durchgang 1: primaere Labels je Sprache, PLUS die Item-QID (fuer Durchgang 2 bei Nicht-Treffern).

    KRITISCH: P528 "catalog code" ist eine GENERISCHE Wikidata-Eigenschaft, nicht astronomiespezifisch
    -- dieselbe Zeichenkette kann in voellig anderen Katalogsystemen wiederverwendet sein (live
    gefunden: unser "B138", Barnard-Dunkelnebel, kollidiert mit Chopins Walzer-Katalognummer B.138
    UND mit einer Mondrian-Gemaeldekatalognummer -- beide tragen zufaellig densselben P528-Wert).
    Deshalb zusaetzlich zu P528 ein PFLICHT-Filter: das Item muss (transitiv ueber "subclass of")
    eine Instanz von Q6999 "astronomical object" sein -- live gegen echte Faelle verifiziert (IC 443
    besteht den Filter, die Chopin-/Mondrian-Kollisionen bei B138 fallen korrekt durch)."""
    optionals = '\n  '.join(
        'OPTIONAL { ?item rdfs:label ?%s . FILTER(LANG(?%s)="%s") }' % (l, l, l) for l in LANGS
    )
    lang_vars = ' '.join('?' + l for l in LANGS)
    values = ' '.join('"%s"' % d.replace('"', '\\"').replace('\\', '\\\\') for d in desigs)
    query = f"""
SELECT ?desig ?item {lang_vars} WHERE {{
  VALUES ?desig {{ {values} }}
  ?item wdt:P528 ?desig .
  ?item wdt:P31/wdt:P279* wd:Q6999 .
  {optionals}
}}"""
    data = _run_sparql(query)
    by_desig = {}
    for b in data['results']['bindings']:
        d = b['desig']['value']
        entry = {'item': b['item']['value']}
        entry.update({l: strip_name_prefix(b[l]['value']) for l in LANGS if l in b})
        by_desig.setdefault(d, entry)
    return by_desig


def wikidata_query_aliases_by_item(item_urls):
    """Durchgang 2: englischer Alias (skos:altLabel) je Item, PLUS dessen primaere Labels in den
    uebrigen Sprachen -- fuer Items, deren primaeres Label selbst keinen Populaernamen hergab."""
    optionals = '\n  '.join(
        'OPTIONAL { ?item rdfs:label ?%s . FILTER(LANG(?%s)="%s") }' % (l, l, l) for l in LANGS
    )
    lang_vars = ' '.join('?' + l for l in LANGS)
    values = ' '.join('<%s>' % u for u in item_urls)
    query = f"""
SELECT ?item ?enAlias {lang_vars} WHERE {{
  VALUES ?item {{ {values} }}
  OPTIONAL {{ ?item skos:altLabel ?enAlias . FILTER(LANG(?enAlias)="en") }}
  {optionals}
}}"""
    data = _run_sparql(query)
    # Ein Item kann MEHRERE Aliase haben -> mehrere Zeilen mit demselben ?item. Ersten brauchbaren
    # (nicht wie Katalogcode aussehenden) Alias je Item behalten.
    by_item = {}
    for b in data['results']['bindings']:
        item = b['item']['value']
        entry = by_item.setdefault(item, {})
        for l in LANGS:
            if l in b and l not in entry:
                entry[l] = strip_name_prefix(b[l]['value'])
        alias = strip_name_prefix(b.get('enAlias', {}).get('value'))
        if alias and is_plausible_common_name(alias) and 'enAlias' not in entry:
            entry['enAlias'] = alias
    return by_item


def fetch_all_wikidata_names(all_desigs, checkpoint_path=None):
    """Fragt Wikidata in Batches nach ALLEN [all_desigs] (Durchgang 1 + Durchgang 2 fuer die
    Nicht-Treffer mit Item). Liefert nur Eintraege mit brauchbarem Namen (looks_like_catalog_code
    gefiltert). Speichert nach jedem Batch einen Zwischenstand, falls [checkpoint_path] gesetzt ist --
    ein Neustart des Skripts nach einem Abbruch setzt dort fort, statt bereits erledigte Batches
    erneut abzufragen."""
    out = {}
    no_name_items = {}  # desig -> item-URL, fuer Durchgang 2 am Ende
    start_batch = 0
    if checkpoint_path and os.path.exists(checkpoint_path):
        try:
            cp = json.load(open(checkpoint_path, encoding='utf-8'))
            out = cp.get('out', {})
            no_name_items = cp.get('no_name_items', {})
            start_batch = cp.get('progress', 0)
            print(f"  Checkpoint geladen: {len(out)} Objekte, setze fort ab Batch {start_batch + 1}")
        except Exception:
            pass
    total_batches = (len(all_desigs) + BATCH_SIZE - 1) // BATCH_SIZE
    for batch_idx in range(start_batch, total_batches):
        i = batch_idx * BATCH_SIZE
        batch_num = batch_idx + 1
        batch = all_desigs[i:i + BATCH_SIZE]
        hits = None
        for attempt in range(2):
            try:
                hits = wikidata_query_by_desig(batch)
                break
            except Exception as e:
                if attempt == 0:
                    time.sleep(3)
                else:
                    print(f"  Batch {batch_num}/{total_batches}: FEHLER nach 2 Versuchen ({e}), ueberspringe")
        if hits is not None:
            kept = 0
            for desig, entry in hits.items():
                en = entry.get('en', '')
                if en and is_plausible_common_name(en):
                    out[desig] = {'en': en, **_usable_translations(entry)}
                    kept += 1
                else:
                    no_name_items[desig] = entry['item']
            print(f"  Batch {batch_num}/{total_batches}: {len(hits)} Treffer, {kept} mit echtem Namen (kumuliert: {len(out)})")
        if checkpoint_path:
            _save_checkpoint(checkpoint_path, out, no_name_items, batch_num)
        time.sleep(BATCH_DELAY_SEC)

    if no_name_items:
        print(f"  Durchgang 2 (Aliase): {len(no_name_items)} Objekte mit Wikidata-Item, aber ohne "
              f"brauchbares primaeres Label -- pruefe englische Aliase ...")
        items = sorted(set(no_name_items.values()))
        alias_by_item = {}
        for i in range(0, len(items), BATCH_SIZE):
            sub = items[i:i + BATCH_SIZE]
            try:
                alias_by_item.update(wikidata_query_aliases_by_item(sub))
            except Exception as e:
                print(f"  Alias-Batch FEHLER ({e}), ueberspringe {len(sub)} Items")
            time.sleep(BATCH_DELAY_SEC)
        alias_found = 0
        for desig, item in no_name_items.items():
            entry = alias_by_item.get(item)
            if not entry or 'enAlias' not in entry:
                continue
            merged = {'en': entry['enAlias'], **_usable_translations(entry)}
            out[desig] = merged
            alias_found += 1
        print(f"  {alias_found} zusaetzliche Objekte ueber Aliase gefunden (kumuliert: {len(out)})")
        if checkpoint_path:
            _save_checkpoint(checkpoint_path, out, {}, total_batches)
    return out


def _save_checkpoint(path, out, no_name_items, batch_num):
    with open(path, 'w', encoding='utf-8') as f:
        json.dump({'progress': batch_num, 'out': out, 'no_name_items': no_name_items}, f, ensure_ascii=False)


def load_seed():
    """Handverlesene Ergaenzungen (tools/dso_names.seed.json) -- fuer Objekte ohne eigene NGC/IC-
    Nummer (z.B. M 45 Plejaden), die deshalb gar nicht erst im eigenen Katalog unter einer passenden
    Bezeichnung stehen, ODER als letzte manuelle Korrektur ueber Wikidata/OpenNGC hinaus."""
    path = os.path.join(HERE, 'dso_names.seed.json')
    if not os.path.exists(path):
        return {}
    raw = json.load(open(path, encoding='utf-8'))
    return {normalize(k): v for k, v in raw.items() if not k.startswith('_')}


# Einzelfaelle, die CATALOG_CODE_RE/is_plausible_common_name auch nach strip_name_prefix() bestehen
# (kein Ziffern-Anhaengsel, daher fuer keine der beiden Regeln erkennbar), aber live gegen Wikidata
# verifiziert KEINE eigenstaendigen Populaernamen sind -- reine Umschreibungen der eigenen
# Katalogbezeichnung ("NAME Cl Auner 1"/"NAME Graham Cl" = Entdeckername + Cl[uster], deckungsgleich
# mit "Auner 1"/"Graham 1" selbst; "NAME Fornax H1"/"H2" = Hodges Katalogschema fuer Sternhaufen in
# der Fornax-Zwerggalaxie) bzw. gar kein Objektname, sondern eine Sternbild-Abkuerzung ("NAME Ret" =
# IAU-Kuerzel fuer Reticulum).
MANUAL_REJECTIONS = {'AUNER1', 'GRAHAM1', 'ESO35529', 'ESO3561', 'ESO11831'}


def main():
    print("Lade eigenen Katalog (dsos.20.json) ...")
    all_desigs = load_catalog_designations()
    print(f"  {len(all_desigs)} eindeutige Katalogbezeichnungen")

    print("Frage Wikidata (P528, primaer + Alias) fuer den GESAMTEN Katalog ab (gebatcht, das dauert "
          "mehrere Minuten) ...")
    wd_names = fetch_all_wikidata_names(all_desigs, checkpoint_path=CHECKPOINT)
    print(f"  {len(wd_names)} Objekte mit echtem Populaernamen bei Wikidata gefunden")

    out = {}
    for desig, labels in wd_names.items():
        key = normalize(desig)
        out[key] = {'name': labels['en'], **_usable_translations(labels)}

    print("Lade OpenNGC-Populaernamen-Zeilen als Ergaenzung ...")
    rows = load_openngc_common_name_rows()
    missing = [r for r in rows if normalize(r['primary']) not in out]
    print(f"  {len(rows)} OpenNGC-Zeilen, davon {len(missing)} noch nicht durch Wikidata abgedeckt")
    if missing:
        wd_extra = fetch_all_wikidata_names([r['primary'] for r in missing])
        for r in missing:
            key = normalize(r['primary'])
            wd_hit = wd_extra.get(r['primary'])
            if wd_hit and 'en' in wd_hit:
                entry = {'name': wd_hit['en'], **_usable_translations(wd_hit)}
            else:
                entry = {'name': r['en_names'][0]}
            out[key] = entry

    for k in MANUAL_REJECTIONS:
        out.pop(k, None)

    seed = load_seed()
    out.update(seed)  # Seed gewinnt zuletzt (manuelle Korrekturen).

    outpath = os.path.join(CAT, 'dso_names.json')
    with open(outpath, 'w', encoding='utf-8') as fh:
        json.dump(out, fh, ensure_ascii=False, indent=1, sort_keys=True)
        fh.write('\n')
    print(f"\n  {len(seed)} Objekte aus Seed ergaenzt/ueberschrieben")
    print(f"  Geschrieben -> {outpath} ({len(out)} Objekte)")
    if os.path.exists(CHECKPOINT):
        os.remove(CHECKPOINT)


if __name__ == '__main__':
    main()
