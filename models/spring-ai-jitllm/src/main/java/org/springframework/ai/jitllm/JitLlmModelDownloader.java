/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.jitllm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Downloads a GGUF model into a local cache, once.
 *
 * <p>
 * A model is named by an {@code https} URL or by a Hugging Face reference,
 * {@code hf://<owner>/<repository>/<file>.gguf}, which resolves to the file on the
 * repository's {@code main} branch. A file already in the cache is used as it is; a
 * download is written next to its destination and moved into place only once complete, so
 * an interrupted download never leaves a partial model behind.
 *
 * @author Michalis Papadimitriou
 * @since 2.1.0
 */
final class JitLlmModelDownloader {

	static final String HUGGING_FACE_SCHEME = "hf";

	private static final Logger logger = LoggerFactory.getLogger(JitLlmModelDownloader.class);

	private static final String HUGGING_FACE_BASE_URL = "https://huggingface.co";

	private final Path cacheDirectory;

	private final @Nullable String token;

	private final String huggingFaceBaseUrl;

	private final HttpClient httpClient;

	JitLlmModelDownloader(Path cacheDirectory, @Nullable String token) {
		this(cacheDirectory, token, HUGGING_FACE_BASE_URL);
	}

	JitLlmModelDownloader(Path cacheDirectory, @Nullable String token, String huggingFaceBaseUrl) {
		this.cacheDirectory = cacheDirectory;
		this.token = token;
		this.huggingFaceBaseUrl = huggingFaceBaseUrl;
		this.httpClient = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(30))
			.build();
	}

	/**
	 * The local copy of the model, downloading it first if it is not in the cache.
	 * @param model an {@code https} URL or an {@code hf://} reference
	 * @return the model file
	 */
	Path resolve(String model) {
		URI source = sourceUri(model);
		Path target = cachePath(model);
		if (Files.isRegularFile(target)) {
			logger.info("Using cached model {}", target);
			return target;
		}
		download(source, target);
		return target;
	}

	/**
	 * Where a model reference is fetched from.
	 */
	URI sourceUri(String model) {
		URI uri = URI.create(model);
		if (HUGGING_FACE_SCHEME.equals(uri.getScheme())) {
			return URI.create(this.huggingFaceBaseUrl + "/" + huggingFaceRepository(uri) + "/resolve/main/"
					+ huggingFaceFile(uri));
		}
		Assert.isTrue("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()),
				() -> "model URL must be https://... or hf://<owner>/<repository>/<file>.gguf, but was " + model);
		return uri;
	}

	/**
	 * Where a model reference is cached: {@code <cache>/<owner>/<repository>/<file>} for
	 * Hugging Face, {@code <cache>/<host>/<path>} otherwise.
	 */
	Path cachePath(String model) {
		URI uri = URI.create(model);
		Path path;
		if (HUGGING_FACE_SCHEME.equals(uri.getScheme())) {
			path = this.cacheDirectory.resolve(huggingFaceRepository(uri)).resolve(huggingFaceFile(uri));
		}
		else {
			Assert.hasText(uri.getHost(), () -> "model URL has no host: " + model);
			Assert.hasText(uri.getPath(), () -> "model URL has no path: " + model);
			path = this.cacheDirectory.resolve(uri.getHost()).resolve(uri.getPath().substring(1));
		}
		Path normalized = path.normalize();
		Assert.isTrue(normalized.startsWith(this.cacheDirectory.normalize()),
				() -> "model reference escapes the cache directory: " + model);
		return normalized;
	}

	private static String huggingFaceRepository(URI uri) {
		String file = huggingFacePath(uri);
		int slash = file.indexOf('/');
		Assert.isTrue(slash > 0, () -> "expected hf://<owner>/<repository>/<file>.gguf, but was " + uri);
		return uri.getHost() + "/" + file.substring(0, slash);
	}

	private static String huggingFaceFile(URI uri) {
		String file = huggingFacePath(uri);
		return file.substring(file.indexOf('/') + 1);
	}

	private static String huggingFacePath(URI uri) {
		String path = uri.getPath();
		Assert.isTrue(StringUtils.hasText(uri.getHost()) && path != null && path.length() > 1
				&& path.indexOf('/', 1) > 1 && !path.endsWith("/"),
				() -> "expected hf://<owner>/<repository>/<file>.gguf, but was " + uri);
		return path.substring(1);
	}

	private void download(URI source, Path target) {
		Path partial = target.resolveSibling(target.getFileName() + ".part");
		try {
			Files.createDirectories(target.getParent());
			HttpRequest.Builder request = HttpRequest.newBuilder(source).GET();
			if (StringUtils.hasText(this.token)) {
				request.header("Authorization", "Bearer " + this.token);
			}
			logger.info("Downloading model {} to {}", source, target);
			HttpResponse<InputStream> response = this.httpClient.send(request.build(),
					HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream body = response.body()) {
				if (response.statusCode() != 200) {
					throw new IllegalStateException("Downloading " + source + " failed with HTTP "
							+ response.statusCode() + (response.statusCode() == 401 || response.statusCode() == 403
									? "; a gated or private model needs a Hugging Face token" : ""));
				}
				long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
				copy(body, partial, total);
			}
			Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			logger.info("Downloaded model {} ({} MiB)", target, Files.size(target) / (1024 * 1024));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Downloading " + source + " failed", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Downloading " + source + " was interrupted", ex);
		}
		finally {
			try {
				Files.deleteIfExists(partial);
			}
			catch (IOException ex) {
				logger.debug("Could not delete {}", partial, ex);
			}
		}
	}

	private static void copy(InputStream body, Path partial, long total) throws IOException {
		byte[] buffer = new byte[1 << 20];
		long copied = 0;
		int reportedDecile = 0;
		try (OutputStream out = Files.newOutputStream(partial)) {
			for (int read = body.read(buffer); read >= 0; read = body.read(buffer)) {
				out.write(buffer, 0, read);
				copied += read;
				if (total > 0 && copied * 10 / total > reportedDecile) {
					reportedDecile = (int) (copied * 10 / total);
					logger.info("Downloaded {}% ({} of {} MiB)", reportedDecile * 10, copied / (1024 * 1024),
							total / (1024 * 1024));
				}
			}
		}
		if (total > 0 && copied != total) {
			throw new IOException("Expected " + total + " bytes but received " + copied);
		}
	}

}
