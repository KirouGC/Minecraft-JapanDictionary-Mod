#!/usr/bin/env python3
"""Lee japandict.db como el mod y muestra un resumen de algunas palabras. Falla si alguna no aparece."""

import gzip
import json
import struct
import sys

from build_dictionary import MAGIC, fnv1a

WORDS = ["作業台", "ダイヤモンド", "剣", "原木", "設定", "進捗", "統計", "取引", "司書", "鍛冶", "食べる", "食"]


def main(path):
    with open(path, "rb") as f:
        assert f.read(8) == MAGIC, "magic incorrecto"
        header = json.loads(f.read(struct.unpack(">i", f.read(4))[0]))
        count = header["buckets"] + header["blocks"]
        table = [struct.unpack(">qi", f.read(12)) for _ in range(count)]

        def chunk(i):
            f.seek(table[i][0])
            return json.loads(gzip.decompress(f.read(table[i][1])))

        print(json.dumps(header, ensure_ascii=False, indent=1))
        missing = []
        for word in WORDS:
            slot = chunk(fnv1a(word) % header["buckets"]).get(word)
            if not slot:
                missing.append(word)
                print(f"{word}: NO ENCONTRADA")
                continue
            line = [word]
            for wid in slot.get("w", [])[:1]:
                entry = chunk(header["buckets"] + wid // header["blockSize"])[wid % header["blockSize"]]
                line.append("/".join(entry["r"][:2]))
                line.append("es=" + "; ".join(entry["es"][0]) if entry.get("es") else "en=" + "; ".join(entry["s"][0]["en"]))
                if entry.get("x"):
                    line.append("ej=" + entry["x"][0]["ja"])
            if "k" in slot:
                line.append("kanji=" + ",".join(slot["k"].get("es") or slot["k"].get("en", [])))
            if "ja" in slot:
                line.append("def=" + slot["ja"][0]["d"][0][:40])
            print(" | ".join(line))
        if missing:
            sys.exit(f"Faltan {len(missing)} palabras: {missing}")


if __name__ == "__main__":
    main(sys.argv[1])
