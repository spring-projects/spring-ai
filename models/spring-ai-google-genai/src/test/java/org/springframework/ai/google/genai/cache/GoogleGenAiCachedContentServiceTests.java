/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.google.genai.cache;

import java.time.Duration;
import java.time.Instant;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.ai.google.genai.TestGoogleGenAiCachedContentService;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService.CachedContentPage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for GoogleGenAiCachedContentService using
 * TestGoogleGenAiCachedContentService.
 *
 * @author Dan Dobrin
 * @since 1.1.0
 */
public class GoogleGenAiCachedContentServiceTests {

	@Mock
	private Client mockClient;

	private TestGoogleGenAiCachedContentService service;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
		// Use the test implementation which doesn't require real API calls
		this.service = new TestGoogleGenAiCachedContentService(this.mockClient);
	}

	@Test
	void testCreateCachedContent() {
		// Prepare test data
		String model = "gemini-2.0-flash";
		String displayName = "Test Cache";
		Duration ttl = Duration.ofHours(1);

		Content systemContent = Content.builder()
			.parts(Part.builder().text("You are a helpful assistant.").build())
			.build();

		Content contextContent = Content.builder()
			.parts(Part.builder().text("Additional context here.").build())
			.build();

		CachedContentRequest request = CachedContentRequest.builder()
			.model(model)
			.displayName(displayName)
			.systemInstruction(systemContent)
			.addContent(contextContent)
			.ttl(ttl)
			.build();

		// Execute
		GoogleGenAiCachedContent result = this.service.create(request);

		// Verify
		assertThat(result).isNotNull();
		assertThat(result.getName()).startsWith("cachedContent/");
		assertThat(result.getModel()).isEqualTo(model);
		assertThat(result.getDisplayName()).isEqualTo(displayName);
		assertThat(result.getTtl()).isEqualTo(ttl);
		assertThat(result.getContents()).contains(contextContent);
		assertThat(result.getSystemInstruction()).isEqualTo(systemContent);
		assertThat(result.getCreateTime()).isNotNull();

		// Verify it's stored
		assertThat(this.service.contains(result.getName())).isTrue();
		assertThat(this.service.size()).isEqualTo(1);
	}

	@Test
	void testGetCachedContent() {
		// Create a cached content first
		Content content = Content.builder().parts(Part.builder().text("Test content").build()).build();

		CachedContentRequest request = CachedContentRequest.builder()
			.model("gemini-2.0-flash")
			.displayName("Test Cache")
			.addContent(content)
			.ttl(Duration.ofHours(1))
			.build();

		GoogleGenAiCachedContent created = this.service.create(request);
		String name = created.getName();

		// Get the cached content
		GoogleGenAiCachedContent retrieved = this.service.get(name);

		// Verify
		assertThat(retrieved).isNotNull();
		assertThat(retrieved.getName()).isEqualTo(name);
		assertThat(retrieved.getModel()).isEqualTo(created.getModel());
		assertThat(retrieved.getDisplayName()).isEqualTo(created.getDisplayName());
	}

	@Test
	void testGetNonExistentCachedContent() {
		GoogleGenAiCachedContent result = this.service.get("cachedContent/nonexistent");
		assertThat(result).isNull();
	}

	@Test
	void testUpdateCachedContent() {
		// Create a cached content first
		Content content = Content.builder().parts(Part.builder().text("Test content").build()).build();

		CachedContentRequest createRequest = CachedContentRequest.builder()
			.model("gemini-2.0-flash")
			.displayName("Original Name")
			.addContent(content)
			.ttl(Duration.ofHours(1))
			.build();

		GoogleGenAiCachedContent created = this.service.create(createRequest);
		String name = created.getName();

		// Update with new TTL
		Duration newTtl = Duration.ofHours(2);
		CachedContentUpdateRequest updateRequest = CachedContentUpdateRequest.builder().ttl(newTtl).build();

		GoogleGenAiCachedContent updated = this.service.update(name, updateRequest);

		// Verify
		assertThat(updated).isNotNull();
		assertThat(updated.getName()).isEqualTo(name);
		assertThat(updated.getTtl()).isEqualTo(newTtl);
		assertThat(updated.getUpdateTime()).isNotNull();
		assertThat(updated.getUpdateTime()).isAfterOrEqualTo(created.getCreateTime());
	}

	@Test
	void testUpdateNonExistentCachedContent() {
		CachedContentUpdateRequest updateRequest = CachedContentUpdateRequest.builder()
			.ttl(Duration.ofHours(2))
			.build();

		assertThatThrownBy(() -> this.service.update("cachedContent/nonexistent", updateRequest))
			.isInstanceOf(TestGoogleGenAiCachedContentService.CachedContentException.class)
			.hasMessageContaining("Cached content not found");
	}

	@Test
	void testDeleteCachedContent() {
		// Create a cached content first
		Content content = Content.builder().parts(Part.builder().text("Test content").build()).build();

		CachedContentRequest request = CachedContentRequest.builder()
			.model("gemini-2.0-flash")
			.displayName("To Delete")
			.addContent(content)
			.ttl(Duration.ofHours(1))
			.build();

		GoogleGenAiCachedContent created = this.service.create(request);
		String name = created.getName();

		// Verify it exists
		assertThat(this.service.contains(name)).isTrue();

		// Delete it
		boolean deleted = this.service.delete(name);
		assertThat(deleted).isTrue();

		// Verify it's gone
		assertThat(this.service.contains(name)).isFalse();
		assertThat(this.service.get(name)).isNull();
	}

	@Test
	void testDeleteNonExistentCachedContent() {
		boolean deleted = this.service.delete("cachedContent/nonexistent");
		assertThat(deleted).isFalse();
	}

	@Test
	void testListCachedContent() {
		// Create multiple cached contents
		for (int i = 0; i < 3; i++) {
			Content content = Content.builder().parts(Part.builder().text("Content " + i).build()).build();

			CachedContentRequest request = CachedContentRequest.builder()
				.model("gemini-2.0-flash")
				.displayName("Cache " + i)
				.addContent(content)
				.ttl(Duration.ofHours(i + 1))
				.build();
			this.service.create(request);
		}

		// List them
		CachedContentPage page = this.service.list(10, null);

		// Verify
		assertThat(page).isNotNull();
		assertThat(page.getContents()).hasSize(3);
		assertThat(page.hasNextPage()).isFalse();
	}

	@Test
	void testListEmptyCachedContent() {
		CachedContentPage page = this.service.list(10, null);

		assertThat(page).isNotNull();
		assertThat(page.getContents()).isEmpty();
		assertThat(page.hasNextPage()).isFalse();
	}

	@Test
	void testCachedContentExpiration() {
		// Create cached content that's already expired
		Content content = Content.builder().parts(Part.builder().text("Test content").build()).build();

		Instant expiredTime = Instant.now().minus(Duration.ofHours(1));
		CachedContentRequest request = CachedContentRequest.builder()
			.model("gemini-2.0-flash")
			.displayName("Expired Cache")
			.addContent(content)
			.expireTime(expiredTime)
			.build();

		GoogleGenAiCachedContent cached = this.service.create(request);

		// Verify expiration
		assertThat(cached.isExpired()).isTrue();
		assertThat(cached.getRemainingTtl()).isEqualTo(Duration.ZERO);
	}

	@Test
	void testCachedContentNotExpired() {
		// Create cached content with future expiration
		Content content = Content.builder().parts(Part.builder().text("Test content").build()).build();

		Instant futureTime = Instant.now().plus(Duration.ofHours(1));
		CachedContentRequest request = CachedContentRequest.builder()
			.model("gemini-2.0-flash")
			.displayName("Valid Cache")
			.addContent(content)
			.expireTime(futureTime)
			.build();

		GoogleGenAiCachedContent cached = this.service.create(request);

		// Verify not expired
		assertThat(cached.isExpired()).isFalse();
		assertThat(cached.getRemainingTtl()).isNotNull();
		assertThat(cached.getRemainingTtl().toHours()).isCloseTo(1L, org.assertj.core.data.Offset.offset(1L));
	}

	@Test
	void testClearAllCachedContent() {
		// Create multiple cached contents
		for (int i = 0; i < 3; i++) {
			Content content = Content.builder().parts(Part.builder().text("Content " + i).build()).build();

			CachedContentRequest request = CachedContentRequest.builder()
				.model("gemini-2.0-flash")
				.displayName("Cache " + i)
				.addContent(content)
				.ttl(Duration.ofHours(1))
				.build();
			this.service.create(request);
		}

		// Verify they exist
		assertThat(this.service.size()).isEqualTo(3);

		// Clear all
		this.service.clearAll();

		// Verify all gone
		assertThat(this.service.size()).isEqualTo(0);
		CachedContentPage page = this.service.list(10, null);
		assertThat(page.getContents()).isEmpty();
	}

}
