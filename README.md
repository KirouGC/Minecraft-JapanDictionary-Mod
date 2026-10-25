# Japan Dictionary

Mod de cliente para **Minecraft 1.21.10 (Fabric)** que ayuda a estudiar japonés. Solo se activa cuando el idioma del juego es japonés (`ja_jp`).

- **Furigana** sobre todo el texto japonés de la interfaz.
- **Modo Diccionario** (en desarrollo): una ventana compacta con estilo Minecraft con la lectura, el romaji, la traducción, los kanjis, una definición y ejemplos de cada palabra en pantalla.

## Instalación

1. Instala [Fabric Loader](https://fabricmc.net/use/) para 1.21.10 y [Fabric API](https://modrinth.com/mod/fabric-api).
2. Copia `japandict-<versión>.jar` en la carpeta `mods`.
3. Pon el juego en japonés: Opciones → Idioma → 日本語.

## Configuración

En Opciones → Controles → Japan Dictionary puedes asignar una tecla que cambia el furigana entre arriba, abajo y oculto.


`config/japandict.json`:

| Opción | Valores | Por defecto |
| --- | --- | --- |
| `enabled` | `true` / `false` | `true` |
| `position` | `ABOVE` (arriba) / `BELOW` (abajo) / `HIDDEN` (oculto) | `ABOVE` |
| `autoScale` | `true`: tamaño nítido según la escala de interfaz | `true` |
| `scale` | 0.25 a 1.0, solo si `autoScale` es `false` | 0.5 |
| `verticalOffset` | píxeles de interfaz, negativo sube | 0 |

Para corregir una lectura, crea `config/japandict-overrides.json`, por ejemplo `{"鍛冶": "かじ"}`.

## Diccionario

La primera vez que abres el juego, el mod descarga la base del diccionario (`config/japandict/japandict.db`) desde la release [`dictionary`](https://github.com/KirouGC/Minecraft-JapanDictionary-Mod/releases/tag/dictionary). También puedes copiarla a mano en esa carpeta.

La base la genera `tools/build_dictionary.py` en GitHub Actions (workflow `dictionary`).

## Compilar

```
./gradlew build
```

El jar queda en `build/libs/`. Cada push también lo compila en GitHub Actions.

## Créditos

- [Kuromoji](https://github.com/atilika/kuromoji) (Apache 2.0) con el diccionario IPADIC, para las lecturas.
- [JMdict y KANJIDIC2](https://www.edrdg.org/) de la Electronic Dictionary Research and Development Group (CC BY-SA 4.0).
- [Tatoeba](https://tatoeba.org) (CC BY 2.0 FR), para las frases de ejemplo.
- [Wiktionary japonés](https://ja.wiktionary.org) (CC BY-SA), extraído por [kaikki.org](https://kaikki.org/jawiktionary/), para las definiciones en japonés.
