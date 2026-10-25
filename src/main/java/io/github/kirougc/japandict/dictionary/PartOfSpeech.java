package io.github.kirougc.japandict.dictionary;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Nombres en español de las categorías gramaticales y notas de uso de JMdict. */
public final class PartOfSpeech {
	private static final Map<String, String> NAMES = Map.ofEntries(
			Map.entry("n", "sustantivo"),
			Map.entry("n-suf", "sufijo nominal"),
			Map.entry("n-pref", "prefijo nominal"),
			Map.entry("n-adv", "sustantivo adverbial"),
			Map.entry("n-t", "sustantivo temporal"),
			Map.entry("pn", "pronombre"),
			Map.entry("num", "número"),
			Map.entry("ctr", "contador"),
			Map.entry("v1", "verbo ichidan"),
			Map.entry("v5u", "verbo godan (う)"),
			Map.entry("v5k", "verbo godan (く)"),
			Map.entry("v5k-s", "verbo godan (く, いく)"),
			Map.entry("v5g", "verbo godan (ぐ)"),
			Map.entry("v5s", "verbo godan (す)"),
			Map.entry("v5t", "verbo godan (つ)"),
			Map.entry("v5n", "verbo godan (ぬ)"),
			Map.entry("v5b", "verbo godan (ぶ)"),
			Map.entry("v5m", "verbo godan (む)"),
			Map.entry("v5r", "verbo godan (る)"),
			Map.entry("v5r-i", "verbo godan (る, irregular)"),
			Map.entry("v5aru", "verbo godan (-aru)"),
			Map.entry("vk", "verbo irregular くる"),
			Map.entry("vs", "sustantivo + する"),
			Map.entry("vs-i", "verbo する"),
			Map.entry("vs-s", "verbo する (especial)"),
			Map.entry("vz", "verbo ずる"),
			Map.entry("vt", "transitivo"),
			Map.entry("vi", "intransitivo"),
			Map.entry("adj-i", "adjetivo -い"),
			Map.entry("adj-ix", "adjetivo -い (いい/よい)"),
			Map.entry("adj-na", "adjetivo -な"),
			Map.entry("adj-no", "sustantivo + の"),
			Map.entry("adj-pn", "adjetivo prenominal"),
			Map.entry("adj-t", "adjetivo -たる"),
			Map.entry("adj-f", "modificador prenominal"),
			Map.entry("adv", "adverbio"),
			Map.entry("adv-to", "adverbio + と"),
			Map.entry("aux", "auxiliar"),
			Map.entry("aux-v", "verbo auxiliar"),
			Map.entry("aux-adj", "adjetivo auxiliar"),
			Map.entry("conj", "conjunción"),
			Map.entry("cop", "cópula"),
			Map.entry("exp", "expresión"),
			Map.entry("int", "interjección"),
			Map.entry("pref", "prefijo"),
			Map.entry("suf", "sufijo"),
			Map.entry("prt", "partícula"),
			Map.entry("uk", "suele escribirse en kana"),
			Map.entry("col", "coloquial"),
			Map.entry("hon", "honorífico"),
			Map.entry("hum", "humilde"),
			Map.entry("pol", "cortés"),
			Map.entry("sl", "jerga"),
			Map.entry("arch", "arcaico"),
			Map.entry("abbr", "abreviatura"),
			Map.entry("on-mim", "onomatopeya"),
			Map.entry("id", "modismo"),
			Map.entry("yoji", "yojijukugo"),
			Map.entry("obs", "obsoleto"),
			Map.entry("rare", "raro"),
			Map.entry("vulg", "vulgar"),
			Map.entry("derog", "despectivo"),
			Map.entry("fam", "familiar"),
			Map.entry("male", "masculino"),
			Map.entry("fem", "femenino"),
			Map.entry("chn", "infantil"),
			Map.entry("sens", "delicado"),
			Map.entry("poet", "poético"),
			Map.entry("form", "formal"),
			Map.entry("lit", "literario")
	);

	private PartOfSpeech() {
	}

	public static String name(String code) {
		return NAMES.getOrDefault(code, code);
	}

	public static String names(List<String> codes) {
		return codes.stream().map(PartOfSpeech::name).distinct().collect(Collectors.joining(", "));
	}
}
