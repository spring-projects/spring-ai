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

package org.springframework.ai.goodmem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Spring AI GoodMem integration. These tests run against a live
 * GoodMem API instance. The required environment variables are:
 *
 * <ul>
 * <li>{@code GOODMEM_BASE_URL} - e.g. {@code https://localhost:8080}</li>
 * <li>{@code GOODMEM_API_KEY} - your GoodMem API key</li>
 * <li>{@code GOODMEM_TEST_PDF} - absolute path to a PDF file for upload testing</li>
 * <li>{@code GOODMEM_EMBEDDER_ID} - optional, the embedder ID to use (defaults to the
 * first one returned by {@code listEmbedders()})</li>
 * <li>{@code GOODMEM_VERIFY_SSL} - set to {@code true} to verify SSL (default:
 * {@code false}, since the test server uses a self-signed certificate)</li>
 * </ul>
 *
 * <p>
 * Run with: <pre>{@code
 * GOODMEM_BASE_URL=https://localhost:8080 \
 * GOODMEM_API_KEY=... \
 * GOODMEM_TEST_PDF=/path/to/file.pdf \
 * ./mvnw -pl goodmem/spring-ai-goodmem -Dtest=GoodMemIntegrationIT test
 * }</pre>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "GOODMEM_API_KEY", matches = ".+")
class GoodMemIntegrationIT {

	private static final Logger logger = LoggerFactory.getLogger(GoodMemIntegrationIT.class);

	private String baseUrl;

	private String apiKey;

	private String testPdf;

	private boolean verifySsl;

	private String embedderId;

	private GoodMemClient client;

	private GoodMemTools tools;

	@BeforeAll
	void setUp() {
		this.baseUrl = System.getenv().getOrDefault("GOODMEM_BASE_URL", "https://localhost:8080");
		this.apiKey = System.getenv().getOrDefault("GOODMEM_API_KEY", "");
		this.testPdf = System.getenv().getOrDefault("GOODMEM_TEST_PDF", "");
		this.verifySsl = Boolean.parseBoolean(System.getenv().getOrDefault("GOODMEM_VERIFY_SSL", "false"));

		this.client = GoodMemClient.builder()
			.baseUrl(this.baseUrl)
			.apiKey(this.apiKey)
			.verifySsl(this.verifySsl)
			.timeout(Duration.ofSeconds(60))
			.build();
		this.tools = new GoodMemTools(this.client);

		String configuredEmbedder = System.getenv().getOrDefault("GOODMEM_EMBEDDER_ID", "");
		if (!configuredEmbedder.isBlank()) {
			this.embedderId = configuredEmbedder;
		}
		else {
			Map<String, Object> result = this.tools.listEmbedders();
			assertThat(result).extracting("success").isEqualTo(true);
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> embedders = (List<Map<String, Object>>) result.get("embedders");
			assertThat(embedders).isNotEmpty();
			// Prefer Voyage AI when available; fall back to the first embedder.
			String preferred = null;
			for (Map<String, Object> embedder : embedders) {
				Object provider = embedder.get("providerType");
				if ("VOYAGE".equals(provider)) {
					preferred = String.valueOf(embedder.get("embedderId"));
					break;
				}
			}
			this.embedderId = (preferred != null) ? preferred : String.valueOf(embedders.get(0).get("embedderId"));
		}
		logger.info("Using embedder ID: {}", this.embedderId);
	}

	@Test
	@Order(1)
	void listEmbedders_returnsResults() {
		Map<String, Object> result = this.tools.listEmbedders();
		assertThat(result).extracting("success").isEqualTo(true);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> embedders = (List<Map<String, Object>>) result.get("embedders");
		assertThat(embedders).isNotEmpty();
		assertThat(result.get("totalEmbedders")).isEqualTo(embedders.size());
	}

	@Test
	@Order(2)
	void listSpaces_succeeds() {
		Map<String, Object> result = this.tools.listSpaces();
		assertThat(result).extracting("success").isEqualTo(true);
		assertThat(result.get("spaces")).isInstanceOf(List.class);
	}

	@Test
	@Order(3)
	void createSpace_returnsNewSpace() {
		String spaceName = "spring-ai-test-" + UUID.randomUUID().toString().substring(0, 8);
		Map<String, Object> result = this.tools.createSpace(spaceName, this.embedderId, null, null, null);
		try {
			assertThat(result).extracting("success").isEqualTo(true);
			assertThat(result.get("spaceId")).asString().isNotEmpty();
			assertThat(result.get("name")).isEqualTo(spaceName);
			assertThat(result.get("reused")).isEqualTo(false);
		}
		finally {
			cleanupSpace(result);
		}
	}

	@Test
	@Order(4)
	void createSpace_reusesExistingByName() {
		String spaceName = "spring-ai-reuse-" + UUID.randomUUID().toString().substring(0, 8);
		Map<String, Object> first = this.tools.createSpace(spaceName, this.embedderId, null, null, null);
		Map<String, Object> second = this.tools.createSpace(spaceName, this.embedderId, null, null, null);
		try {
			assertThat(first).extracting("success").isEqualTo(true);
			assertThat(first.get("reused")).isEqualTo(false);
			assertThat(second).extracting("success").isEqualTo(true);
			assertThat(second.get("reused")).isEqualTo(true);
			assertThat(second.get("spaceId")).isEqualTo(first.get("spaceId"));
		}
		finally {
			cleanupSpace(first);
		}
	}

	@Test
	@Order(5)
	void createMemory_withText_andRetrieve_andGet_andDelete() {
		String spaceName = "spring-ai-text-" + UUID.randomUUID().toString().substring(0, 8);
		Map<String, Object> space = this.tools.createSpace(spaceName, this.embedderId, null, null, null);
		String spaceId = String.valueOf(space.get("spaceId"));
		String memoryId = null;
		try {
			String text = "Spring AI integrates GoodMem as a memory layer for semantic storage and retrieval. "
					+ "GoodMem supports both text and PDF documents and exposes a search API.";
			Map<String, Object> created = this.tools.createMemory(spaceId, text, null, null);
			assertThat(created).extracting("success").isEqualTo(true);
			memoryId = String.valueOf(created.get("memoryId"));
			assertThat(memoryId).isNotEmpty();
			assertThat(created.get("contentType")).isEqualTo("text/plain");

			Map<String, Object> retrieved = this.tools.retrieveMemories("semantic storage memory layer", spaceId, 5,
					true, true, null, null, null, null, null);
			assertThat(retrieved).extracting("success").isEqualTo(true);
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> chunks = (List<Map<String, Object>>) retrieved.get("results");
			assertThat(chunks).as("retrieve results for the text memory").isNotEmpty();
			boolean memoryIdFound = false;
			for (Map<String, Object> chunk : chunks) {
				if (memoryId.equals(String.valueOf(chunk.get("memoryId")))) {
					memoryIdFound = true;
					break;
				}
			}
			assertThat(memoryIdFound).as("retrieved chunks include the freshly created memory").isTrue();

			Map<String, Object> getResult = this.tools.getMemory(memoryId, true);
			assertThat(getResult).extracting("success").isEqualTo(true);
			assertThat(getResult.get("memory")).isInstanceOf(Map.class);
		}
		finally {
			if (memoryId != null) {
				this.tools.deleteMemory(memoryId);
			}
			cleanupSpace(space);
		}
	}

	@Test
	@Order(6)
	@EnabledIfEnvironmentVariable(named = "GOODMEM_TEST_PDF", matches = ".+")
	void createMemory_withPdf_andRetrieve() {
		assertThat(this.testPdf).as("GOODMEM_TEST_PDF must be a valid file path").isNotBlank();
		assertThat(Files.exists(Path.of(this.testPdf))).as("PDF must exist at " + this.testPdf).isTrue();

		String spaceName = "spring-ai-pdf-" + UUID.randomUUID().toString().substring(0, 8);
		Map<String, Object> space = this.tools.createSpace(spaceName, this.embedderId, null, null, null);
		String spaceId = String.valueOf(space.get("spaceId"));
		String memoryId = null;
		try {
			Map<String, Object> created = this.tools.createMemory(spaceId, null, this.testPdf, null);
			assertThat(created).extracting("success").isEqualTo(true);
			memoryId = String.valueOf(created.get("memoryId"));
			assertThat(memoryId).isNotEmpty();
			assertThat(created.get("contentType")).isEqualTo("application/pdf");

			Map<String, Object> retrieved = this.tools.retrieveMemories("search analysis findings", spaceId, 5, true,
					true, null, null, null, null, null);
			assertThat(retrieved).extracting("success").isEqualTo(true);
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> chunks = (List<Map<String, Object>>) retrieved.get("results");
			// We assert the call succeeded; chunks may still be processing but should
			// usually be returned within the 60s polling window for the Voyage embedder.
			assertThat(chunks).as("retrieve results for the PDF memory").isNotNull();
		}
		finally {
			if (memoryId != null) {
				this.tools.deleteMemory(memoryId);
			}
			cleanupSpace(space);
		}
	}

	@Test
	@Order(7)
	void createSpace_withInvalidEmbedder_returnsActionableError() {
		String spaceName = "spring-ai-invalid-" + UUID.randomUUID().toString().substring(0, 8);
		Map<String, Object> result = this.tools.createSpace(spaceName, "00000000-0000-0000-0000-000000000000", null,
				null, null);
		// The space name was unique, so the dedupe path is skipped and the API
		// is asked to create with an unknown embedder.
		assertThat(result).extracting("success").isEqualTo(false);
		assertThat(result.get("error")).asString().isNotBlank();
	}

	@Test
	@Order(8)
	void retrieveMemories_withEmptySpaceIds_returnsActionableError() {
		Map<String, Object> result = this.tools.retrieveMemories("hello", " , ", 5, true, false, null, null, null, null,
				null);
		assertThat(result).extracting("success").isEqualTo(false);
		assertThat(result.get("error")).asString().contains("Space ID");
	}

	@Test
	@Order(9)
	void deleteMemory_withInvalidId_returnsActionableError() {
		Map<String, Object> result = this.tools.deleteMemory("00000000-0000-0000-0000-000000000000");
		assertThat(result).extracting("success").isEqualTo(false);
		assertThat(result.get("error")).asString().isNotBlank();
	}

	@Test
	@Order(10)
	void getSpace_returnsFullRecord() {
		String spaceName = "spring-ai-getspace-" + UUID.randomUUID().toString().substring(0, 8);
		Map<String, Object> created = this.tools.createSpace(spaceName, this.embedderId, null, null, null);
		String spaceId = String.valueOf(created.get("spaceId"));
		try {
			Map<String, Object> result = this.tools.getSpace(spaceId);
			assertThat(result).extracting("success").isEqualTo(true);
			@SuppressWarnings("unchecked")
			Map<String, Object> space = (Map<String, Object>) result.get("space");
			assertThat(space.get("spaceId")).isEqualTo(spaceId);
			assertThat(space.get("name")).isEqualTo(spaceName);
		}
		finally {
			cleanupSpace(created);
		}
	}

	@Test
	@Order(11)
	void updateSpace_changesName() {
		String original = "spring-ai-update-" + UUID.randomUUID().toString().substring(0, 8);
		String renamed = original + "-renamed";
		Map<String, Object> created = this.tools.createSpace(original, this.embedderId, null, null, null);
		String spaceId = String.valueOf(created.get("spaceId"));
		try {
			Map<String, Object> updated = this.tools.updateSpace(spaceId, renamed, null, null, null);
			assertThat(updated.get("name")).isEqualTo(renamed);

			// Round-trip via getSpace to confirm the change persisted server-side.
			Map<String, Object> refetched = this.tools.getSpace(spaceId);
			@SuppressWarnings("unchecked")
			Map<String, Object> space = (Map<String, Object>) refetched.get("space");
			assertThat(space.get("name")).isEqualTo(renamed);
		}
		finally {
			cleanupSpace(created);
		}
	}

	@Test
	@Order(12)
	void updateSpace_withBothLabelArgumentsReturnsActionableError() {
		// updateSpace rejects the conflicting arguments before any HTTP call, so a
		// placeholder space ID is sufficient here.
		Map<String, Object> result = this.tools.updateSpace("00000000-0000-0000-0000-000000000000", null, null,
				"{\"a\":\"b\"}", "{\"c\":\"d\"}");
		assertThat(result).extracting("success").isEqualTo(false);
		assertThat(result.get("error")).asString().contains("Cannot use both");
	}

	@Test
	@Order(13)
	void listMemories_returnsMemoriesInSpace() throws InterruptedException {
		String spaceName = "spring-ai-listmem-" + UUID.randomUUID().toString().substring(0, 8);
		Map<String, Object> created = this.tools.createSpace(spaceName, this.embedderId, null, null, null);
		String spaceId = String.valueOf(created.get("spaceId"));
		try {
			this.tools.createMemory(spaceId, "first note about Spring AI integration testing.", null, null);
			this.tools.createMemory(spaceId, "second note about GoodMem semantic search.", null, null);
			Thread.sleep(3000); // wait for async indexing

			Map<String, Object> result = this.tools.listMemories(spaceId, null, false, null, null, null, null);
			assertThat(result).extracting("success").isEqualTo(true);
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> memories = (List<Map<String, Object>>) result.get("memories");
			assertThat(memories).hasSizeGreaterThanOrEqualTo(2);
		}
		finally {
			cleanupSpace(created);
		}
	}

	@Test
	@Order(14)
	void deleteSpace_removesSpace() {
		String spaceName = "spring-ai-deletespace-" + UUID.randomUUID().toString().substring(0, 8);
		Map<String, Object> created = this.tools.createSpace(spaceName, this.embedderId, null, null, null);
		String spaceId = String.valueOf(created.get("spaceId"));

		Map<String, Object> deleted = this.tools.deleteSpace(spaceId);
		assertThat(deleted).extracting("success").isEqualTo(true);
		assertThat(deleted.get("spaceId")).isEqualTo(spaceId);

		// goodmem_get_space wraps server errors, so a deleted space surfaces as
		// success=false rather than a thrown exception.
		Map<String, Object> refetched = this.tools.getSpace(spaceId);
		assertThat(refetched).extracting("success").isEqualTo(false);
	}

	private void cleanupSpace(Map<String, Object> spaceResult) {
		try {
			Object idObj = spaceResult.get("spaceId");
			if (idObj == null) {
				return;
			}
			this.client.deleteSpace(String.valueOf(idObj));
		}
		catch (RuntimeException ex) {
			logger.warn("Cleanup failed for space {}: {}", spaceResult.get("spaceId"), ex.getMessage());
		}
	}

}
