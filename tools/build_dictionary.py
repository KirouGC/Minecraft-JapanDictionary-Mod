#!/usr/bin/env python3
"""
Genera la base de datos del diccionario (japandict.db) a partir de datos abiertos:

  - JMdict (EDRDG, CC BY-SA 4.0): palabras, lecturas, glosas en español e inglés
  - KANJIDIC2 (EDRDG, CC BY-SA 4.0): significado, lecturas y trazos de cada kanji
  - Tatoeba (CC BY 2.0 FR): frases de ejemplo con traducción al español o al inglés
  - Wiktionary japonés vía kaikki.org/wiktextract (CC BY-SA): definiciones en japonés

Formato del archivo (todo en big-endian, el lector está en DictionaryDatabase.java):

  "JPDICT01"                                  8 bytes
  int32 largo + JSON de cabecera              {"version", "buckets", "blocks", "blockSize", ...}
  buckets x (int64 offset, int32 largo)       índice: clave -> datos, repartido por hash FNV-1a
  blocks  x (int64 offset, int32 largo)       palabras de JMdict, de a blockSize por bloque
  datos                                       cada bucket y cada bloque es JSON comprimido con gzip

Cada bucket es un objeto {clave: {"w": [ids de palabras], "k": kanji, "ja": [definiciones]}}.
Cada definición es {"p": categoría, "d": [textos], "es": [traducción], "en": [traducción]}; las
traducciones automáticas son opcionales y van en paralelo a "d" (null donde no hay).
Cada bloque es una lista de palabras: {"k": [kanji], "r": [lecturas], "c": común, "s": [acepciones
en inglés], "es": [acepciones en español], "x": [ejemplos]}.
"""

import argparse
import bz2
import gzip
import io
import json
import re
import struct
import sys
import time
import xml.etree.ElementTree as ET
from collections import defaultdict

MAGIC = b"JPDICT01"
FORMAT_VERSION = 1
# Sube cuando cambia el contenido; el mod vuelve a descargar una base con revisión menor.
REVISION = 2
BUCKETS = 8192
BLOCK_SIZE = 64
XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"
KEEP_ENTITIES = {"amp", "lt", "gt", "quot", "apos"}
# Algunas glosas en español llevan la inglesa al lado: "diamante (eng: diamond)".
ENG_NOTE = re.compile(r"\s*\(eng: [^)]*\)")
# Definiciones del Wiktionary que solo remiten a otra entrada: "たべる の漢字表記。"
SPELLING_POINTER = re.compile(r"^\s*(.+?)\s*の(?:漢字|かな|仮名)?表記。?\s*$")


def fnv1a(key: str) -> int:
    h = 0x811C9DC5
    for b in key.encode("utf-8"):
        h ^= b
        h = (h * 0x01000193) & 0xFFFFFFFF
    return h


def open_text(path):
    if path.endswith(".gz"):
        return io.TextIOWrapper(gzip.open(path, "rb"), encoding="utf-8")
    if path.endswith(".bz2"):
        return io.TextIOWrapper(bz2.open(path, "rb"), encoding="utf-8")
    return open(path, encoding="utf-8")


def without_dtd_entities(path):
    """JMdict usa entidades del DTD (&v1;, &n;). Las cambiamos por su código corto y quitamos el DTD."""
    with open_text(path) as f:
        in_dtd = False
        for line in f:
            if line.startswith("<!DOCTYPE"):
                in_dtd = True
            if in_dtd:
                if line.startswith("]>"):
                    in_dtd = False
                continue
            yield re.sub(r"&([\w.-]+);",
                         lambda m: m.group(0) if m.group(1) in KEEP_ENTITIES else m.group(1), line)


class LineStream(io.RawIOBase):
    def __init__(self, lines):
        self.lines = iter(lines)
        self.buffer = b""

    def readable(self):
        return True

    def readinto(self, b):
        while not self.buffer:
            try:
                self.buffer = next(self.lines).encode("utf-8")
            except StopIteration:
                return 0
        n = min(len(b), len(self.buffer))
        b[:n] = self.buffer[:n]
        self.buffer = self.buffer[n:]
        return n


def priority(pri_tags):
    common = any(t in ("news1", "ichi1", "spec1", "spec2", "gai1") for t in pri_tags)
    nf = min((int(t[2:]) for t in pri_tags if t.startswith("nf")), default=60)
    return (0 if common else 1000) + nf, common


def parse_jmdict(path):
    entries = []
    stream = io.BufferedReader(LineStream(without_dtd_entities(path)))
    for _, elem in ET.iterparse(stream, events=("end",)):
        if elem.tag != "entry":
            continue
        kanji = [k.findtext("keb") for k in elem.findall("k_ele")]
        readings = [r.findtext("reb") for r in elem.findall("r_ele")]
        pri = [p.text for p in elem.iter() if p.tag in ("ke_pri", "re_pri") and p.text]
        score, common = priority(pri)

        senses_en, senses_es = [], []
        last_pos = []
        for sense in elem.findall("sense"):
            glosses = sense.findall("gloss")
            if not glosses:
                continue
            lang = glosses[0].get(XML_LANG, "eng")
            texts = [g.text for g in glosses if g.text]
            if lang == "eng":
                pos = [p.text for p in sense.findall("pos")] or last_pos
                last_pos = pos
                item = {"en": texts}
                if pos:
                    item["p"] = pos
                misc = [m.text for m in sense.findall("misc")]
                if misc:
                    item["m"] = misc
                senses_en.append(item)
            elif lang == "spa":
                texts = [ENG_NOTE.sub("", t).strip() for t in texts]
                senses_es.append([t for t in texts if t])

        entry = {"r": readings, "s": senses_en}
        if kanji:
            entry["k"] = kanji
        if common:
            entry["c"] = 1
        if senses_es:
            entry["es"] = senses_es
        entries.append((score, entry))
        elem.clear()
    return entries


def parse_kanjidic(path):
    result = {}
    source = gzip.open(path, "rb") if path.endswith(".gz") else open(path, "rb")
    with source as f:
        for _, elem in ET.iterparse(f, events=("end",)):
            if elem.tag != "character":
                continue
            literal = elem.findtext("literal")
            misc = elem.find("misc")
            data = {"on": [], "kun": [], "en": [], "es": []}
            if misc is not None:
                for tag, key in (("grade", "g"), ("stroke_count", "st"), ("freq", "f"), ("jlpt", "jlpt")):
                    value = misc.findtext(tag)
                    if value:
                        data[key] = int(value)
            for rm in elem.iter("rmgroup"):
                for r in rm.findall("reading"):
                    if r.get("r_type") == "ja_on":
                        data["on"].append(r.text)
                    elif r.get("r_type") == "ja_kun":
                        data["kun"].append(r.text)
                for m in rm.findall("meaning"):
                    lang = m.get("m_lang", "en")
                    if lang in ("en", "es"):
                        data[lang].append(m.text)
            result[literal] = {k: v for k, v in data.items() if v}
            elem.clear()
    return result


def read_tsv(path, columns):
    with open_text(path) as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= columns:
                yield parts


def parse_tatoeba(jpn_path, spa_path, eng_path, jpn_spa_links, jpn_eng_links):
    def sentences(path):
        return {int(p[0]): p[2] for p in read_tsv(path, 3)}

    def links(path):
        out = {}
        for p in read_tsv(path, 2):
            out.setdefault(int(p[0]), int(p[1]))
        return out

    jpn = sentences(jpn_path)
    spa = sentences(spa_path)
    eng = sentences(eng_path)
    to_spa = links(jpn_spa_links)
    to_eng = links(jpn_eng_links)
    out = []
    for sid, text in jpn.items():
        example = {"ja": text}
        if to_spa.get(sid) in spa:
            example["es"] = spa[to_spa[sid]]
        if to_eng.get(sid) in eng:
            example["en"] = eng[to_eng[sid]]
        if "es" in example or "en" in example:
            out.append(example)
    return out


def attach_examples(entries, examples, per_word=2, max_len=10):
    """Busca, para cada palabra, las frases más cortas que la contienen (primero las que tienen español)."""
    index = {}
    for i, (_, entry) in enumerate(entries):
        forms = entry.get("k") or [r for r in entry["r"] if len(r) >= 3]
        for form in forms:
            index.setdefault(form, i)
    best = defaultdict(list)
    for example in examples:
        text = example["ja"]
        rank = (0 if "es" in example else 1, len(text))
        seen = set()
        for start in range(len(text)):
            for end in range(start + 1, min(len(text), start + max_len) + 1):
                i = index.get(text[start:end])
                if i is None or i in seen or len(text) < (end - start) + 4:
                    continue
                seen.add(i)
                bucket = best[i]
                if len(bucket) < per_word or rank < bucket[-1][0]:
                    bucket.append((rank, example))
                    bucket.sort(key=lambda x: x[0])
                    del bucket[per_word:]
    for i, bucket in best.items():
        entries[i][1]["x"] = [example for _, example in bucket]


def parse_wiktionary(path, per_word=3):
    result = defaultdict(list)
    with open_text(path) as f:
        for line in f:
            if '"ja"' not in line and "日本語" not in line:
                continue
            item = json.loads(line)
            if item.get("lang_code") != "ja" and item.get("lang") != "日本語":
                continue
            word = item.get("word")
            defs = []
            for sense in item.get("senses", []):
                glosses = sense.get("glosses") or []
                if glosses:
                    defs.append(glosses[-1])
            if word and defs and len(result[word]) < per_word:
                block = {"d": defs[:5]}
                if item.get("pos_title") or item.get("pos"):
                    block["p"] = item.get("pos_title") or item.get("pos")
                result[word].append(block)

    for word, blocks in result.items():
        for block in blocks:
            resolved = []
            for definition in block["d"]:
                match = SPELLING_POINTER.match(definition)
                target = result.get(match.group(1)) if match else None
                if target and target is not blocks:
                    resolved.extend(d for b in target for d in b["d"] if not SPELLING_POINTER.match(d))
                else:
                    resolved.append(definition)
            block["d"] = list(dict.fromkeys(resolved))[:5] or block["d"]
    return result


def attach_translations(definitions, path):
    """Agrega a cada bloque de definiciones su traducción automática (tools/translate_definitions.py):
    "es" en paralelo a "d", y "en" solo donde no haya español."""
    with gzip.open(path, "rt", encoding="utf-8") as f:
        translations = json.load(f)
    count = 0
    for blocks in definitions.values():
        for block in blocks:
            found = [translations.get(d.strip(), {}) for d in block["d"]]
            spanish = [t.get("es") for t in found]
            if any(spanish):
                block["es"] = spanish
                count += sum(1 for t in spanish if t)
            if any(not t.get("es") and t.get("en") for t in found):
                block["en"] = [None if t.get("es") else t.get("en") for t in found]
    return [None] * count


def compress(obj):
    return gzip.compress(json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8"), 9)


def write_database(path, entries, kanji, definitions, sources):
    entries.sort(key=lambda e: e[0])
    words = [e for _, e in entries]

    index = defaultdict(dict)

    def slot(key):
        return index[fnv1a(key) % BUCKETS].setdefault(key, {})

    for i, entry in enumerate(words):
        for key in dict.fromkeys(entry.get("k", []) + entry["r"]):
            slot(key).setdefault("w", []).append(i)
    for literal, data in kanji.items():
        slot(literal)["k"] = data
    for word, defs in definitions.items():
        slot(word)["ja"] = defs

    blocks = [compress(words[i:i + BLOCK_SIZE]) for i in range(0, len(words), BLOCK_SIZE)]
    buckets = [compress(index.get(b, {})) for b in range(BUCKETS)]

    header = json.dumps({
        "version": FORMAT_VERSION,
        "revision": REVISION,
        "buckets": BUCKETS,
        "blocks": len(blocks),
        "blockSize": BLOCK_SIZE,
        "words": len(words),
        "kanji": len(kanji),
        "definitions": len(definitions),
        "built": time.strftime("%Y-%m-%d"),
        "sources": sources,
    }, ensure_ascii=False).encode("utf-8")

    table_size = (len(buckets) + len(blocks)) * 12
    offset = len(MAGIC) + 4 + len(header) + table_size
    with open(path, "wb") as out:
        out.write(MAGIC)
        out.write(struct.pack(">i", len(header)))
        out.write(header)
        for chunk in buckets + blocks:
            out.write(struct.pack(">qi", offset, len(chunk)))
            offset += len(chunk)
        for chunk in buckets + blocks:
            out.write(chunk)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jmdict", required=True)
    parser.add_argument("--kanjidic", required=True)
    parser.add_argument("--tatoeba-jpn", required=True)
    parser.add_argument("--tatoeba-spa", required=True)
    parser.add_argument("--tatoeba-eng", required=True)
    parser.add_argument("--tatoeba-jpn-spa", required=True)
    parser.add_argument("--tatoeba-jpn-eng", required=True)
    parser.add_argument("--wiktionary", required=True)
    parser.add_argument("--translations", help="traducciones automáticas de las definiciones (.json.gz)")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    def step(name, fn):
        start = time.time()
        result = fn()
        print(f"{name}: {len(result)} en {time.time() - start:.1f}s", file=sys.stderr)
        return result

    entries = step("JMdict", lambda: parse_jmdict(args.jmdict))
    kanji = step("KANJIDIC2", lambda: parse_kanjidic(args.kanjidic))
    examples = step("Tatoeba", lambda: parse_tatoeba(args.tatoeba_jpn, args.tatoeba_spa, args.tatoeba_eng,
                                                     args.tatoeba_jpn_spa, args.tatoeba_jpn_eng))
    step("Ejemplos asignados", lambda: attach_examples(entries, examples) or [e for e in entries if "x" in e[1]])
    definitions = step("Wiktionary", lambda: parse_wiktionary(args.wiktionary))
    if args.translations:
        step("Definiciones traducidas", lambda: attach_translations(definitions, args.translations))
    sources = {
        "JMdict": "EDRDG, CC BY-SA 4.0, https://www.edrdg.org/edrdg/licence.html",
        "KANJIDIC2": "EDRDG, CC BY-SA 4.0, https://www.edrdg.org/edrdg/licence.html",
        "Tatoeba": "CC BY 2.0 FR, https://tatoeba.org",
        "Wiktionary": "CC BY-SA, https://ja.wiktionary.org (vía kaikki.org)",
    }
    if args.translations:
        sources["Argos Translate"] = "traducción automática de las definiciones, MIT, https://www.argosopentech.com"
    write_database(args.out, entries, kanji, definitions, sources)
    print(f"Escrito {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
