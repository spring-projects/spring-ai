/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class ResourceCacheServiceTests {

	@TempDir
	File tempDir;

	@Test
	public void fileResourcesAreExcludedByDefault() throws IOException {
		var cache = new ResourceCacheService(tempDir);
		var originalResourceUri = "file:src/main/resources/onnx/all-MiniLM-L6-v2/tokenizer.json";
		var cachedResource = cache.getCachedResource(originalResourceUri);

		assertThat(cachedResource).isEqualTo(new DefaultResourceLoader().getResource(originalResourceUri));
		assertThat(Files.list(tempDir.toPath()).count()).isEqualTo(0);
	}

	@Test
	public void cacheFileResources() throws IOException {
		var cache = new ResourceCacheService(tempDir);

		cache.setExcludedUriSchemas(List.of()); // erase the excluded schema names,
												// including 'file'.

		var originalResourceUri = "file:src/main/resources/onnx/all-MiniLM-L6-v2/tokenizer.json";
		var cachedResource1 = cache.getCachedResource(originalResourceUri);

		assertThat(cachedResource1).isNotEqualTo(new DefaultResourceLoader().getResource(originalResourceUri));
		assertThat(Files.list(tempDir.toPath()).count()).isEqualTo(1);
		assertThat(Files.list(Files.list(tempDir.toPath()).iterator().next()).count()).isEqualTo(1);

		// Attempt to cache the same resource again should return the already cached
		// resource.
		var cachedResource2 = cache.getCachedResource(originalResourceUri);

		assertThat(cachedResource2).isNotEqualTo(new DefaultResourceLoader().getResource(originalResourceUri));
		assertThat(cachedResource2).isEqualTo(cachedResource1);

		assertThat(Files.list(tempDir.toPath()).count()).isEqualTo(1);
		assertThat(Files.list(Files.list(tempDir.toPath()).iterator().next()).count()).isEqualTo(1);

	}

	@Test
	public void cacheFileResourcesFromSameParentFolder() throws IOException {
		var cache = new ResourceCacheService(tempDir);

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

		assertThat(Files.list(tempDir.toPath()).count()).isEqualTo(1)
			.describedAs(
					"As both resources come from the same parent segments they should be cached in a single common parent.");
		assertThat(Files.list(Files.list(tempDir.toPath()).iterator().next()).count()).isEqualTo(2);

	}

	@Test
	public void cacheHttpResources() throws IOException {
		var cache = new ResourceCacheService(tempDir);

		var originalResourceUri1 = "https://raw.githubusercontent.com/spring-projects/spring-ai/main/spring-ai-core/src/main/resources/embedding/embedding-model-dimensions.properties";
		var cachedResource1 = cache.getCachedResource(originalResourceUri1);

		assertThat(cachedResource1).isNotEqualTo(new DefaultResourceLoader().getResource(originalResourceUri1));
		assertThat(Files.list(tempDir.toPath()).count()).isEqualTo(1);
		assertThat(Files.list(Files.list(tempDir.toPath()).iterator().next()).count()).isEqualTo(1);
	}

}
