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

package org.springframework.ai.voyageai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.voyageai.api.VoyageAiApi;
import org.springframework.util.Assert;

/**
 * Checks shared by the Voyage AI models for what the API sent back.
 *
 * @author Zoltan Fodi
 */
final class VoyageAiResponseSupport {

	private VoyageAiResponseSupport() {
	}

	/**
	 * Return the billed tokens of a response. A response without usage cannot be
	 * accounted for, so it is an error rather than zero tokens.
	 * @param usage the usage of the response
	 * @param operation what the response is for, for the error message
	 * @return the total tokens
	 */
	static int requireTotalTokens(VoyageAiApi.@Nullable Usage usage, String operation) {
		Integer totalTokens = (usage != null) ? usage.totalTokens() : null;
		if (totalTokens == null) {
			throw new IllegalStateException("Voyage AI returned no usage.total_tokens for " + operation
					+ ", so the tokens used cannot be accounted for");
		}
		return totalTokens;
	}

	/**
	 * Return the vectors of a response in the order of the request inputs.
	 * @param data the embeddings of the response
	 * @param expectedCount how many inputs the request had
	 * @return the vectors, the one at position {@code i} belonging to input {@code i}
	 */
	static List<float[]> orderedVectors(List<VoyageAiApi.EmbeddingData> data, int expectedCount) {
		if (data.size() != expectedCount) {
			throw new IllegalStateException(
					"Voyage AI returned " + data.size() + " embeddings for " + expectedCount + " inputs");
		}
		List<VoyageAiApi.EmbeddingData> sorted = new ArrayList<>(data);
		sorted.sort(Comparator.comparing(VoyageAiApi.EmbeddingData::index));
		List<float[]> vectors = new ArrayList<>(expectedCount);
		for (int i = 0; i < sorted.size(); i++) {
			Assert.state(sorted.get(i).index() == i,
					() -> "Voyage AI returned embeddings whose indexes are not 0.." + (expectedCount - 1));
			vectors.add(sorted.get(i).embedding());
		}
		return vectors;
	}

	/**
	 * Split a list into consecutive parts of at most the given size, keeping the order.
	 * @param items the items
	 * @param size the largest part
	 * @param <T> the item type
	 * @return the parts
	 */
	static <T> List<List<T>> partition(List<T> items, int size) {
		List<List<T>> parts = new ArrayList<>();
		for (int from = 0; from < items.size(); from += size) {
			parts.add(items.subList(from, Math.min(from + size, items.size())));
		}
		return parts;
	}

}
