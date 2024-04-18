/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.ai.chat.prompt.transformer.PromptContext;
import org.springframework.ai.chat.prompt.transformer.PromptTransformer;
import org.springframework.ai.model.Content;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/**
 * Returns a new list of content (e.g list of messages of list of documents) that is a
 * subset of the input list of contents and complies with the max token size constraint.
 *
 * The token estimator is used to estimate the token count of the datum.
 *
 * @author Christian Tzolov
 */
public class LastMaxTokenSizeContentTransformer implements PromptTransformer {

	protected final TokenCountEstimator tokenCountEstimator;

	protected final int maxTokenSize;

	/**
	 * Only Content entries with the following metadata tags will be included in the
	 * history.
	 */
	private final Set<String> filterTags;

	public LastMaxTokenSizeContentTransformer(TokenCountEstimator tokenCountEstimator, int maxTokenSize) {
		this(tokenCountEstimator, maxTokenSize, Set.of());
	}

	public LastMaxTokenSizeContentTransformer(TokenCountEstimator tokenCountEstimator, int maxTokenSize,
			Set<String> filterTags) {
		this.tokenCountEstimator = tokenCountEstimator;
		this.maxTokenSize = maxTokenSize;
		this.filterTags = filterTags;
	}

	protected List<Content> doGetDatumToModify(PromptContext promptContext) {
		return promptContext.getContents()
			.stream()
			.filter(content -> this.filterTags.stream().allMatch(tag -> content.getMetadata().containsKey(tag)))
			.toList();
	}

	protected List<Content> doGetDatumNotToModify(PromptContext promptContext) {
		return promptContext.getContents()
			.stream()
			.filter(content -> !this.filterTags.stream().allMatch(tag -> content.getMetadata().containsKey(tag)))
			.toList();
	}

	protected int doEstimateTokenCount(Content datum) {
		return this.tokenCountEstimator.estimate(datum);
	}

	protected int doEstimateTokenCount(List<Content> datum) {
		return datum.stream().mapToInt(this::doEstimateTokenCount).sum();
	}

	@Override
	public PromptContext transform(PromptContext promptContext) {

		List<Content> datum = this.doGetDatumToModify(promptContext);

		int totalSize = this.doEstimateTokenCount(datum);

		if (totalSize <= this.maxTokenSize) {
			return promptContext;
		}

		List<Content> purgedContent = this.purgeExcess(datum, totalSize);

		var updatedContent = new ArrayList<>(doGetDatumNotToModify(promptContext));
		updatedContent.addAll(purgedContent);

		return PromptContext.from(promptContext).withContents(updatedContent).build();
	}

	protected List<Content> purgeExcess(List<Content> datum, int totalSize) {

		int index = 0;
		List<Content> newList = new ArrayList<>();

		while (index < datum.size() && totalSize > this.maxTokenSize) {
			Content oldDatum = datum.get(index++);
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

}