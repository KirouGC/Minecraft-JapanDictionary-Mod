package io.github.kirougc.japandict.dictionary;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Lector de japandict.db (el formato está descrito en tools/build_dictionary.py). Solo guarda en
 * memoria las tablas de posiciones; cada consulta lee y descomprime un bucket y los bloques que
 * necesite, con una pequeña caché.
 */
public final class DictionaryDatabase implements Closeable {
	public static final int FORMAT_VERSION = 1;
	private static final byte[] MAGIC = "JPDICT01".getBytes(StandardCharsets.US_ASCII);
	private static final int CACHE_SIZE = 128;
	private static final Gson GSON = new Gson();

	private final RandomAccessFile file;
	private final JsonObject header;
	private final int bucketCount;
	private final int blockSize;
	private final long[] offsets;
	private final int[] lengths;
	private final Map<Integer, JsonObject> bucketCache = lru();
	private final Map<Integer, List<DictionaryModel.Word>> blockCache = lru();

	private DictionaryDatabase(RandomAccessFile file) throws IOException {
		this.file = file;
		byte[] magic = new byte[MAGIC.length];
		file.readFully(magic);
		if (!java.util.Arrays.equals(magic, MAGIC)) {
			throw new IOException("No es una base de datos de Japan Dictionary");
		}
		byte[] headerBytes = new byte[file.readInt()];
		file.readFully(headerBytes);
		header = JsonParser.parseString(new String(headerBytes, StandardCharsets.UTF_8)).getAsJsonObject();
		if (header.get("version").getAsInt() != FORMAT_VERSION) {
			throw new IOException("Versión de base de datos no soportada: " + header.get("version"));
		}
		bucketCount = header.get("buckets").getAsInt();
		blockSize = header.get("blockSize").getAsInt();
		int entries = bucketCount + header.get("blocks").getAsInt();
		offsets = new long[entries];
		lengths = new int[entries];
		for (int i = 0; i < entries; i++) {
			offsets[i] = file.readLong();
			lengths[i] = file.readInt();
		}
	}

	public static DictionaryDatabase open(Path path) throws IOException {
		RandomAccessFile file = new RandomAccessFile(path.toFile(), "r");
		try {
			return new DictionaryDatabase(file);
		} catch (IOException | RuntimeException e) {
			file.close();
			throw e;
		}
	}

	public JsonObject header() {
		return header;
	}

	public synchronized DictionaryModel.Result lookup(String key) throws IOException {
		JsonObject bucket = bucket(Integer.remainderUnsigned(fnv1a(key), bucketCount));
		JsonObject slot = bucket.getAsJsonObject(key);
		List<DictionaryModel.Word> words = new ArrayList<>();
		DictionaryModel.Kanji kanji = null;
		List<DictionaryModel.Definition> definitions = List.of();
		if (slot != null) {
			JsonArray ids = slot.getAsJsonArray("w");
			if (ids != null) {
				for (JsonElement id : ids) {
					int i = id.getAsInt();
					words.add(block(i / blockSize).get(i % blockSize));
				}
			}
			if (slot.has("k")) {
				kanji = GSON.fromJson(slot.get("k"), DictionaryModel.Kanji.class);
			}
			if (slot.has("ja")) {
				definitions = GSON.fromJson(slot.get("ja"), new TypeToken<List<DictionaryModel.Definition>>() {}.getType());
			}
		}
		return new DictionaryModel.Result(key, words, kanji, definitions);
	}

	private JsonObject bucket(int index) throws IOException {
		JsonObject cached = bucketCache.get(index);
		if (cached == null) {
			cached = JsonParser.parseReader(reader(index)).getAsJsonObject();
			bucketCache.put(index, cached);
		}
		return cached;
	}

	private List<DictionaryModel.Word> block(int index) throws IOException {
		List<DictionaryModel.Word> cached = blockCache.get(index);
		if (cached == null) {
			cached = GSON.fromJson(reader(bucketCount + index), new TypeToken<List<DictionaryModel.Word>>() {}.getType());
			blockCache.put(index, cached);
		}
		return cached;
	}

	private InputStreamReader reader(int tableIndex) throws IOException {
		byte[] data = new byte[lengths[tableIndex]];
		file.seek(offsets[tableIndex]);
		file.readFully(data);
		return new InputStreamReader(new GZIPInputStream(new ByteArrayInputStream(data)), StandardCharsets.UTF_8);
	}

	/** FNV-1a de 32 bits sobre UTF-8, igual que en build_dictionary.py. */
	static int fnv1a(String key) {
		int hash = 0x811C9DC5;
		for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
			hash ^= b & 0xFF;
			hash *= 0x01000193;
		}
		return hash;
	}

	private static <V> Map<Integer, V> lru() {
		return new LinkedHashMap<>(16, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Integer, V> eldest) {
				return size() > CACHE_SIZE;
			}
		};
	}

	@Override
	public synchronized void close() throws IOException {
		file.close();
	}
}
