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
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Unit tests for {@link JitLlmModelDownloader}, against a local HTTP server.
 *
 * @author Michalis Papadimitriou
 */
class JitLlmModelDownloaderTests {

	private static final byte[] MODEL = "GGUF-test-model".getBytes(StandardCharsets.UTF_8);

	@TempDir
	Path cache;

	private HttpServer server;

	private String baseUrl;

	private final List<String> authorizations = new CopyOnWriteArrayList<>();

	@BeforeEach
	void startServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		// Hugging Face redirects /resolve/ to a CDN.
		this.server.createContext("/owner/repo/resolve/main/model.gguf", exchange -> {
			this.authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
			exchange.getResponseHeaders().add("Location", this.baseUrl + "/cdn/model.gguf");
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
		});
		this.server.createContext("/cdn/model.gguf", exchange -> {
			exchange.sendResponseHeaders(200, MODEL.length);
			try (OutputStream body = exchange.getResponseBody()) {
				body.write(MODEL);
			}
		});
		this.server.createContext("/owner/gated/resolve/main/model.gguf", exchange -> {
			exchange.sendResponseHeaders(401, -1);
			exchange.close();
		});
		this.server.start();
		this.baseUrl = "http://127.0.0.1:" + this.server.getAddress().getPort();
	}

	@AfterEach
	void stopServer() {
		this.server.stop(0);
	}

	@Test
	void aHuggingFaceReferenceResolvesToTheMainBranchAndIsCachedByRepository() {
		JitLlmModelDownloader downloader = new JitLlmModelDownloader(this.cache, null);

		assertThat(downloader.sourceUri("hf://beehive-lab/Llama-3.2-1B-Instruct-GGUF/Llama-3.2-1B-Instruct-FP16.gguf"))
			.isEqualTo(URI.create(
					"https://huggingface.co/beehive-lab/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-FP16.gguf"));
		assertThat(downloader.cachePath("hf://beehive-lab/Llama-3.2-1B-Instruct-GGUF/Llama-3.2-1B-Instruct-FP16.gguf"))
			.isEqualTo(this.cache.resolve("beehive-lab/Llama-3.2-1B-Instruct-GGUF/Llama-3.2-1B-Instruct-FP16.gguf"));
	}

	@Test
	void anHttpsUrlIsCachedByHostAndPath() {
		JitLlmModelDownloader downloader = new JitLlmModelDownloader(this.cache, null);

		assertThat(downloader.cachePath("https://example.com/models/model.gguf"))
			.isEqualTo(this.cache.resolve("example.com/models/model.gguf"));
	}

	@Test
	void malformedOrEscapingReferencesAreRejected() {
		JitLlmModelDownloader downloader = new JitLlmModelDownloader(this.cache, null);

		assertThatIllegalArgumentException().isThrownBy(() -> downloader.cachePath("hf://owner/model.gguf"));
		assertThatIllegalArgumentException().isThrownBy(() -> downloader.cachePath("hf://owner/repo/../../../x.gguf"));
		assertThatIllegalArgumentException().isThrownBy(() -> downloader.sourceUri("ftp://example.com/model.gguf"));
	}

	@Test
	void downloadsOnceFollowingRedirectsAndSendsTheToken() throws IOException {
		JitLlmModelDownloader downloader = new JitLlmModelDownloader(this.cache, "secret", this.baseUrl);

		Path first = downloader.resolve("hf://owner/repo/model.gguf");
		Path second = downloader.resolve("hf://owner/repo/model.gguf");

		assertThat(first).isEqualTo(second).isEqualTo(this.cache.resolve("owner/repo/model.gguf"));
		assertThat(Files.readAllBytes(first)).isEqualTo(MODEL);
		assertThat(this.authorizations).containsExactly("Bearer secret");
		assertThat(first.resolveSibling("model.gguf.part")).doesNotExist();
	}

	@Test
	void aFailedDownloadLeavesNothingBehindAndMentionsTheToken() {
		JitLlmModelDownloader downloader = new JitLlmModelDownloader(this.cache, null, this.baseUrl);

		assertThatIllegalStateException().isThrownBy(() -> downloader.resolve("hf://owner/gated/model.gguf"))
			.withMessageContaining("HTTP 401")
			.withMessageContaining("token");
		assertThat(this.cache.resolve("owner/gated")).isEmptyDirectory();
	}

}
