/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.chat.client.advisor;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.content.Content;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/**
 * Returns a new list of content (e.g list of messages of list of documents) that is a
 * subset of the input list of contents and complies with the max token size constraint.
 * The token estimator is used to estimate the token count of the datum.
 *
 * @author Christian Tzolov
 * @since 1.0.0 M1
 */
public class LastMaxTokenSizeContentPurger {

	protected final TokenCountEstimator tokenCountEstimator;

	protected final int maxTokenSize;

	public LastMaxTokenSizeContentPurger(TokenCountEstimator tokenCountEstimator, int maxTokenSize) {
		this.tokenCountEstimator = tokenCountEstimator;
		this.maxTokenSize = maxTokenSize;
	}

	public List<Content> purgeExcess(List<MediaContent> datum, int totalSize) {

		int index = 0;
		List<Content> newList = new ArrayList<>();

		while (index < datum.size() && totalSize > this.maxTokenSize) {
			MediaContent oldDatum = datum.get(index++);
			int oldMessageTokenSize = this.doEstimateTokenCount(oldDatum);
			totalSize = totalSize - oldMessageTokenSize;
		}

		if (index >= datum.size()) {
			return List.of();
		}

		// add the rest of the messages.
		newList.addAll(datum.subList(index, datum.size()));

		return newList;
	}

	protected int doEstimateTokenCount(MediaContent datum) {
		return this.tokenCountEstimator.estimate(datum);
	}

	protected int doEstimateTokenCount(List<MediaContent> datum) {
		return datum.stream().mapToInt(this::doEstimateTokenCount).sum();
	}

}
