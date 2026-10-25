package io.github.kirougc.japandict.furigana;

/**
 * Lectura de un tramo de kanji. {@code start} y {@code end} son índices de code points
 * (no de chars UTF-16) dentro del texto analizado, con {@code end} exclusivo.
 */
public record RubySpan(int start, int end, String reading) {
}
