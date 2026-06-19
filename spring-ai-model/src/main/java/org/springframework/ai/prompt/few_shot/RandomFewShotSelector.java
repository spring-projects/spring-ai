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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.springframework.util.Assert;

/**
 * Implementation of {@link FewShotSelector} that selects examples randomly.
 *
 * This selector is useful for:
 * <ul>
 * <li>Testing and development (simple, predictable with seed)
 * <li>General cases where all examples are equally relevant
 * <li>Load balancing across example pool
 * <li>Avoiding bias towards certain examples
 * </ul>
 *
 * <p>
 * Thread-safe when using different instances per thread, or provide a synchronized
 * Random.
 *
 * @author galt-k
 * @since 1.0
 */
public class RandomFewShotSelector implements FewShotSelector {

	private final Random random;

	/**
	 * Creates a new RandomFewShotSelector with a default Random instance.
	 */
	public RandomFewShotSelector() {
		this.random = new Random();
	}

	/**
	 * Creates a new RandomFewShotSelector with the provided Random instance.
	 *
	 * Useful for testing (with a seeded Random) or for controlling the random number
	 * generation.
	 * @param random the Random instance to use, must not be null
	 */
	public RandomFewShotSelector(Random random) {
		Assert.notNull(random, "random cannot be null");
		this.random = random;
	}

	@Override
	public List<FewShotExample> select(String userQuery, List<FewShotExample> availableExamples, int maxExamples) {
		Assert.hasText(userQuery, "userQuery cannot be null or empty");
		Assert.notNull(availableExamples, "availableExamples cannot be null");
		Assert.isTrue(maxExamples > 0, "maxExamples must be greater than 0");

		// Return empty list if no examples available
		if (availableExamples.isEmpty()) {
			return new ArrayList<>();
		}

		// Create a copy to avoid modifying the original list
		List<FewShotExample> examples = new ArrayList<>(availableExamples);

		// Shuffle the examples randomly
		Collections.shuffle(examples, this.random);

		// Return only the first maxExamples
		return examples.subList(0, Math.min(maxExamples, examples.size()));
	}

}
