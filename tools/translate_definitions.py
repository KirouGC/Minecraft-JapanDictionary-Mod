#!/usr/bin/env python3
"""
Traduce a máquina las definiciones del Wiktionary japonés para guardarlas en la base del diccionario,
así se ven en español aunque el jugador no tenga una clave de DeepL.

Usa los modelos libres de Argos Translate (OpenNMT/CTranslate2, licencia MIT) directamente con
CTranslate2 (pip install ctranslate2 sentencepiece), por lotes, que es mucho más rápido que la API de Argos. No hay modelo japonés→español,
así que se pasa por el inglés: ja→en→es. Se guardan las dos traducciones.

  extract    definiciones que muestra la ventana (las 4 primeras de cada palabra), sin repetir
  translate  traduce una parte (--shard i --shards n) y escribe JSON Lines {"ja", "en", "es"}
  merge      junta las partes en un JSON comprimido {ja: {"en": ..., "es": ...}}
"""

import argparse
import gzip
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Igual que DictionaryOverlay: se muestran como mucho 4 definiciones por palabra.
SHOWN_PER_WORD = 4


def extract(args):
    from build_dictionary import parse_wiktionary

    definitions = parse_wiktionary(args.wiktionary)
    unique = {}
    for blocks in definitions.values():
        shown = [d for block in blocks for d in block["d"]][:SHOWN_PER_WORD]
        for d in shown:
            unique.setdefault(d.strip(), None)
    with open(args.out, "w", encoding="utf-8") as out:
        for d in unique:
            if d:
                out.write(json.dumps(d, ensure_ascii=False) + "\n")
    print(f"{len(unique)} definiciones distintas", file=sys.stderr)


INDEX_URL = "https://raw.githubusercontent.com/argosopentech/argospm-index/main/index.json"
MODELS_DIR = os.path.join(os.path.expanduser("~"), ".cache", "japandict-argos")


def install(from_code, to_code):
    """Descarga el paquete de Argos (un zip con el modelo de CTranslate2) y devuelve su carpeta.
    Se baja directo del índice para no instalar argostranslate, que arrastra PyTorch."""
    import urllib.request
    import zipfile

    target = os.path.join(MODELS_DIR, f"{from_code}_{to_code}")
    if not os.path.isdir(target):
        with urllib.request.urlopen(INDEX_URL) as response:
            index = json.load(response)
        package = next((p for p in index if p["from_code"] == from_code and p["to_code"] == to_code), None)
        if package is None:
            raise SystemExit(f"No hay paquete de Argos {from_code}→{to_code}")
        url = next(link for link in package["links"] if link.startswith("http"))
        archive = target + ".zip"
        os.makedirs(MODELS_DIR, exist_ok=True)
        # argos-net.com rechaza el User-Agent de urllib.
        request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (japandict)"})
        with urllib.request.urlopen(request) as response, open(archive, "wb") as out:
            while chunk := response.read(1 << 20):
                out.write(chunk)
        with zipfile.ZipFile(archive) as z:
            z.extractall(target)
        os.remove(archive)
    for root, dirs, files in os.walk(target):
        if "model" in dirs and "sentencepiece.model" in files:
            return root
    raise SystemExit(f"El paquete {from_code}→{to_code} no tiene model/ y sentencepiece.model")


class Model:
    def __init__(self, path, threads):
        import ctranslate2
        import sentencepiece

        self.translator = ctranslate2.Translator(os.path.join(path, "model"), device="cpu",
                                                 compute_type="int8", inter_threads=1, intra_threads=threads)
        self.sp = sentencepiece.SentencePieceProcessor(model_file=os.path.join(path, "sentencepiece.model"))

    def translate(self, texts, batch_size):
        tokens = [self.sp.encode(t, out_type=str)[:250] for t in texts]
        results = self.translator.translate_batch(tokens, max_batch_size=batch_size, beam_size=2,
                                                  max_decoding_length=256, replace_unknowns=True)
        return [self.sp.decode(r.hypotheses[0]).strip() for r in results]


def translate(args):
    with open(args.definitions, encoding="utf-8") as f:
        texts = [json.loads(line) for i, line in enumerate(f) if i % args.shards == args.shard]
    if args.limit:
        texts = texts[:args.limit]
    print(f"Parte {args.shard}/{args.shards}: {len(texts)} definiciones", file=sys.stderr)

    ja_en = Model(install("ja", "en"), args.threads)
    en_es = Model(install("en", "es"), args.threads)
    start = time.time()
    with open(args.out, "w", encoding="utf-8") as out:
        for i in range(0, len(texts), args.chunk):
            chunk = texts[i:i + args.chunk]
            english = ja_en.translate(chunk, args.batch)
            spanish = en_es.translate(english, args.batch)
            for ja, en, es in zip(chunk, english, spanish):
                out.write(json.dumps({"ja": ja, "en": en, "es": es}, ensure_ascii=False) + "\n")
            done = i + len(chunk)
            rate = done / (time.time() - start)
            print(f"{done}/{len(texts)} ({rate:.1f}/s)", file=sys.stderr, flush=True)


def merge(args):
    merged = {}
    for path in args.parts:
        with open(path, encoding="utf-8") as f:
            for line in f:
                item = json.loads(line)
                entry = {}
                if item.get("es"):
                    entry["es"] = item["es"]
                if item.get("en"):
                    entry["en"] = item["en"]
                if entry:
                    merged[item["ja"]] = entry
    with gzip.open(args.out, "wt", encoding="utf-8") as out:
        json.dump(merged, out, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(merged)} traducciones en {args.out}", file=sys.stderr)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(required=True)

    p = sub.add_parser("extract")
    p.add_argument("--wiktionary", required=True)
    p.add_argument("--out", required=True)
    p.set_defaults(fn=extract)

    p = sub.add_parser("translate")
    p.add_argument("--definitions", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--shard", type=int, default=0)
    p.add_argument("--shards", type=int, default=1)
    p.add_argument("--threads", type=int, default=os.cpu_count() or 4)
    p.add_argument("--batch", type=int, default=32)
    p.add_argument("--chunk", type=int, default=512)
    p.add_argument("--limit", type=int, default=0)
    p.set_defaults(fn=translate)

    p = sub.add_parser("merge")
    p.add_argument("--out", required=True)
    p.add_argument("parts", nargs="+")
    p.set_defaults(fn=merge)

    args = parser.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
