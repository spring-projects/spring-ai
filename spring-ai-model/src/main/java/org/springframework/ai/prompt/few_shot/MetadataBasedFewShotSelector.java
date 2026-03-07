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

package org.springframework.ai.prompt.few_shot;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.util.Assert;

/**
 * Implementation of {@link FewShotSelector} that selects examples based on metadata
 * filtering.
 *
 * This selector filters examples by matching a specific metadata key-value pair. Useful
 * for:
 * <ul>
 * <li>Domain-specific example selection (e.g., "domain=technical")
 * <li>Difficulty-level filtering (e.g., "difficulty=beginner")
 * <li>Language/category-based filtering
 * <li>Custom organizational tags
 * </ul>
 *
 * <p>
 * Example: <pre>{@code
 * FewShotSelector selector = new MetadataBasedFewShotSelector("domain", "financial");
 * List<FewShotExample> selected = selector.select(userQuery, examples, 3);
 * // Returns examples with metadata containing domain=financial
 * }</pre>
 *
 * @author galt-k
 * @since 1.0
 */
public class MetadataBasedFewShotSelector implements FewShotSelector {

	private final String metadataKey;

	private final Object metadataValue;

	/**
	 * Creates a new MetadataBasedFewShotSelector.
	 * @param metadataKey the metadata key to match, must not be null
	 * @param metadataValue the metadata value to match, must not be null
	 */
	public MetadataBasedFewShotSelector(String metadataKey, Object metadataValue) {
		Assert.notNull(metadataKey, "metadataKey cannot be null");
		Assert.notNull(metadataValue, "metadataValue cannot be null");
		this.metadataKey = metadataKey;
		this.metadataValue = metadataValue;
	}

	@Override
	public List<FewShotExample> select(String userQuery, List<FewShotExample> availableExamples, int maxExamples) {
		Assert.hasText(userQuery, "userQuery cannot be null or empty");
		Assert.notNull(availableExamples, "availableExamples cannot be null");
		Assert.isTrue(maxExamples > 0, "maxExamples must be greater than 0");

		// Filter examples by metadata and limit to maxExamples
		return availableExamples.stream()
			.filter(example -> this.metadataValue.equals(example.getMetadata().get(this.metadataKey)))
			.limit(maxExamples)
			.collect(Collectors.toList());
	}

	/**
	 * Returns the metadata key used for filtering.
	 * @return the metadata key
	 */
	public String getMetadataKey() {
		return this.metadataKey;
	}

	/**
	 * Returns the metadata value used for filtering.
	 * @return the metadata value
	 */
	public Object getMetadataValue() {
		return this.metadataValue;
	}

}
