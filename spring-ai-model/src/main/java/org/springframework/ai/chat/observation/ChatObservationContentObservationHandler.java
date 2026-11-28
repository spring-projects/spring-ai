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

package org.springframework.ai.chat.observation;

import java.util.stream.Collectors;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;

import org.springframework.ai.content.Content;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class ChatObservationContentObservationHandler implements ObservationHandler<ChatModelObservationContext> {

	private final boolean includePrompt;

	private final boolean includeCompletion;

	private final int maxContentLength;

	private final String promptKey;

	private final String completionKey;

	public ChatObservationContentObservationHandler(boolean includePrompt, boolean includeCompletion,
			int maxContentLength, String promptKey, String completionKey) {
		this.includePrompt = includePrompt;
		this.includeCompletion = includeCompletion;
		this.maxContentLength = maxContentLength;
		this.promptKey = promptKey;
		this.completionKey = completionKey;
	}

	@Override
	public void onStop(ChatModelObservationContext context) {
		if (this.includePrompt && !CollectionUtils.isEmpty(context.getRequest().getInstructions())) {
			String combinedPrompt = context.getRequest()
				.getInstructions()
				.stream()
				.map(Content::getText)
				.filter(StringUtils::hasText)
				.collect(Collectors.joining("\n"));

			if (StringUtils.hasText(combinedPrompt)) {
				context.addHighCardinalityKeyValue(KeyValue.of(this.promptKey, truncate(combinedPrompt)));
			}
		}

		if (this.includeCompletion && context.getResponse() != null
				&& !CollectionUtils.isEmpty(context.getResponse().getResults())) {
			String combinedCompletion = context.getResponse()
				.getResults()
				.stream()
				.filter(gen -> StringUtils.hasText(gen.getOutput().getText()))
				.map(gen -> gen.getOutput().getText())
				.collect(Collectors.joining("\n"));

			if (StringUtils.hasText(combinedCompletion)) {
				context.addHighCardinalityKeyValue(KeyValue.of(this.completionKey, truncate(combinedCompletion)));
			}
		}
	}

	private String truncate(String text) {
		if (this.maxContentLength <= 0) {
			return "";
		}

		if (text.length() <= this.maxContentLength) {
			return text;
		}

		String suffix = "... [truncated]";
		if (this.maxContentLength <= suffix.length()) {
			return suffix.substring(0, this.maxContentLength);
		}

		return text.substring(0, this.maxContentLength - suffix.length()) + suffix;
	}

	@Override
	public boolean supportsContext(Observation.Context context) {
		return context instanceof ChatModelObservationContext;
	}

}
