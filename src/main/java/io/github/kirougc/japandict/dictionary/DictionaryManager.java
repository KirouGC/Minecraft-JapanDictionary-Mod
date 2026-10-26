package io.github.kirougc.japandict.dictionary;

import io.github.kirougc.japandict.JapanDictClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Se encarga de que la base del diccionario esté en config/japandict/japandict.db: si no está,
 * la descarga en segundo plano la primera vez que se abre el juego, y luego la abre.
 */
public final class DictionaryManager {
	public enum Status {
		NOT_STARTED,
		DOWNLOADING,
		LOADING,
		READY,
		FAILED
	}

	/** Revisión mínima de la base que espera esta versión del mod; 2 = definiciones traducidas. */
	private static final int MIN_REVISION = 2;

	private static volatile Status status = Status.NOT_STARTED;
	private static volatile String error;
	private static volatile long downloadedBytes;
	private static volatile long totalBytes = -1;
	private static volatile DictionaryDatabase database;

	private DictionaryManager() {
	}

	public static Path databasePath() {
		return FabricLoader.getInstance().getConfigDir().resolve("japandict").resolve("japandict.db");
	}

	public static Status status() {
		return status;
	}

	public static String error() {
		return error;
	}

	/** Progreso de la descarga entre 0 y 1, o -1 si no se sabe el tamaño. */
	public static float downloadProgress() {
		long total = totalBytes;
		return total > 0 ? (float) downloadedBytes / total : -1;
	}

	/** La base abierta, o null si todavía no está lista. */
	public static DictionaryDatabase database() {
		return database;
	}

	public static synchronized void start(String url, boolean allowDownload) {
		if (status == Status.DOWNLOADING || status == Status.LOADING || status == Status.READY) {
			return;
		}
		Thread thread = new Thread(() -> run(url, allowDownload), "japandict-dictionary");
		thread.setDaemon(true);
		thread.start();
	}

	private static void run(String url, boolean allowDownload) {
		Path path = databasePath();
		try {
			if (!Files.exists(path)) {
				if (!allowDownload) {
					throw new IOException("Falta " + path + " y la descarga automática está desactivada");
				}
				download(url, path);
			} else if (allowDownload && revision(path) < MIN_REVISION) {
				// Base vieja (por ejemplo sin las traducciones de las definiciones): se baja la nueva,
				// y si no se puede se sigue usando la que hay.
				try {
					download(url, path);
				} catch (Exception e) {
					JapanDictClient.LOGGER.warn("No se pudo actualizar el diccionario, uso el que ya está", e);
				}
			}
			status = Status.LOADING;
			database = DictionaryDatabase.open(path);
			status = Status.READY;
			JapanDictClient.LOGGER.info("Diccionario listo: {}", database.header());
		} catch (Exception e) {
			error = e.getMessage();
			status = Status.FAILED;
			JapanDictClient.LOGGER.error("No se pudo preparar el diccionario", e);
		}
	}

	/** Revisión del contenido de la base (sube cuando cambian los datos, no el formato). */
	private static int revision(Path path) {
		try (DictionaryDatabase db = DictionaryDatabase.open(path)) {
			var value = db.header().get("revision");
			return value == null ? 1 : value.getAsInt();
		} catch (Exception e) {
			return 0;
		}
	}

	private static void download(String url, Path target) throws IOException, InterruptedException {
		status = Status.DOWNLOADING;
		JapanDictClient.LOGGER.info("Descargando el diccionario desde {}", url);
		Files.createDirectories(target.getParent());
		Path partial = target.resolveSibling(target.getFileName() + ".part");

		HttpClient client = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(20))
				.build();
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.header("User-Agent", "JapanDictionary-Minecraft-Mod")
				.GET()
				.build();
		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) {
			response.body().close();
			throw new IOException("La descarga respondió HTTP " + response.statusCode() + " (" + url + ")");
		}
		totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(-1);
		downloadedBytes = 0;
		try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(partial)) {
			byte[] buffer = new byte[64 * 1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
				downloadedBytes += read;
			}
		}

		// Si el archivo no es una base válida, mejor no dejarlo en su lugar.
		DictionaryDatabase.open(partial).close();
		Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		JapanDictClient.LOGGER.info("Diccionario descargado: {} MB", downloadedBytes / (1024 * 1024));
	}
}
