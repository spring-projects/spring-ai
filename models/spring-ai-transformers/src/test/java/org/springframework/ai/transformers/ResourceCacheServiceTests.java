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

package org.springframework.ai.transformers;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Christian Tzolov
 */
public class ResourceCacheServiceTests {

	@TempDir
	File tempDir;

	@Test
	public void fileResourcesAreExcludedByDefault() throws IOException {
		var cache = new ResourceCacheService(this.tempDir);
		var originalResourceUri = "file:src/main/resources/onnx/all-MiniLM-L6-v2/tokenizer.json";
		var cachedResource = cache.getCachedResource(originalResourceUri);

		assertThat(cachedResource).isEqualTo(new DefaultResourceLoader().getResource(originalResourceUri));
		try (Stream<Path> paths = Files.list(this.tempDir.toPath())) {
			assertThat(paths.count()).isEqualTo(0);
		}
	}

	@Test
	public void cacheFileResources() throws IOException {
		var cache = new ResourceCacheService(this.tempDir);

		cache.setExcludedUriSchemas(List.of()); // erase the excluded schema names,
		// including 'file'.

		var originalResourceUri = "file:src/main/resources/onnx/all-MiniLM-L6-v2/tokenizer.json";
		var cachedResource1 = cache.getCachedResource(originalResourceUri);

		assertThat(cachedResource1).isNotEqualTo(new DefaultResourceLoader().getResource(originalResourceUri));
		assertThat(this.tempDir.listFiles()).hasSize(1);
		assertThat(this.tempDir.listFiles()[0].listFiles()).hasSize(2);

		// Attempt to cache the same resource again should return the already cached
		// resource.
		var cachedResource2 = cache.getCachedResource(originalResourceUri);

		assertThat(cachedResource2).isNotEqualTo(new DefaultResourceLoader().getResource(originalResourceUri));
		assertThat(cachedResource2).isEqualTo(cachedResource1);

		assertThat(this.tempDir.listFiles()).hasSize(1);
		assertThat(this.tempDir.listFiles()[0].listFiles()).hasSize(2);

	}

	@Test
	public void cacheFileResourcesFromSameParentFolder() throws IOException {
		var cache = new ResourceCacheService(this.tempDir);

		cache.setExcludedUriSchemas(List.of()); // erase the excluded schema names,
		// including 'file'.

		var originalResourceUri1 = "file:src/main/resources/onnx/all-MiniLM-L6-v2/tokenizer.json";
		var cachedResource1 = cache.getCachedResource(originalResourceUri1);

		// Attempt to cache the same resource again should return the already cached
		// resource.
		var originalResourceUri2 = "file:src/main/resources/onnx/all-MiniLM-L6-v2/model.png";
		var cachedResource2 = cache.getCachedResource(originalResourceUri2);

		assertThat(cachedResource2).isNotEqualTo(new DefaultResourceLoader().getResource(originalResourceUri1));
		assertThat(cachedResource2).isNotEqualTo(cachedResource1);

		assertThat(this.tempDir.listFiles()).hasSize(1)
			.describedAs(
					"As both resources come from the same parent segments they should be cached in a single common parent.");
		assertThat(this.tempDir.listFiles()[0].listFiles()).hasSize(4);
	}

	@Test
	public void cacheHttpResources() throws IOException {
		var cache = new ResourceCacheService(this.tempDir);

		var originalResourceUri1 = "https://raw.githubusercontent.com/spring-projects/spring-ai/main/spring-ai-model/src/main/resources/embedding/embedding-model-dimensions.properties";
		var cachedResource1 = cache.getCachedResource(originalResourceUri1);

		assertThat(cachedResource1).isNotEqualTo(new DefaultResourceLoader().getResource(originalResourceUri1));
		assertThat(this.tempDir.listFiles()).hasSize(1);
		assertThat(this.tempDir.listFiles()[0].listFiles()).hasSize(1);
	}

	@Test
	public void refreshesLocalCachedResourceWhenResourceMetadataChanges() throws IOException {
		var cache = new ResourceCacheService(this.tempDir);

		cache.setExcludedUriSchemas(List.of());

		var originalResource = new TestResource("file:/models/model.onnx", "model.onnx", "old-model", 1);
		var cachedResource = cache.getCachedResource(originalResource);

		assertThat(cachedResource.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("old-model");

		var updatedResource = new TestResource("file:/models/model.onnx", "model.onnx", "new-model", 2);
		var refreshedResource = cache.getCachedResource(updatedResource);

		assertThat(refreshedResource).isEqualTo(cachedResource);
		assertThat(refreshedResource.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("new-model");
		assertThat(this.tempDir.listFiles()).hasSize(1);
		assertThat(this.tempDir.listFiles()[0].listFiles()).hasSize(2);
	}

	@Test
	public void shouldHandleNullUri() {
		var cache = new ResourceCacheService(this.tempDir);

		assertThatThrownBy(() -> cache.getCachedResource((String) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Location must not be null");
	}

	private static final class TestResource extends ByteArrayResource {

		private final URI uri;

		private final String filename;

		private final long lastModified;

		TestResource(String uri, String filename, String content, long lastModified) {
			super(content.getBytes(StandardCharsets.UTF_8));
			this.uri = URI.create(uri);
			this.filename = filename;
			this.lastModified = lastModified;
		}

		@Override
		public URI getURI() {
			return this.uri;
		}

		@Override
		public String getFilename() {
			return this.filename;
		}

		@Override
		public long lastModified() {
			return this.lastModified;
		}

	}

}
