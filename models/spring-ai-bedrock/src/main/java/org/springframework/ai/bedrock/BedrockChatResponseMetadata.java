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
package org.springframework.ai.bedrock;

import java.util.HashMap;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;

import software.amazon.awssdk.services.bedrockruntime.model.ConverseMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetrics;

/**
 * {@link ChatResponseMetadata} implementation for {@literal Amazon Bedrock}.
 *
 * @author Wei Jiang
 * @since 1.0.0
 */
public class BedrockChatResponseMetadata extends HashMap<String, Object> implements ChatResponseMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s, id: %2$s, usage: %3$s, latency: %4$sms}";

	private final String id;

	private final Usage usage;

	private final Long latencyMs;

	public static BedrockChatResponseMetadata from(ConverseResponse response) {
		String requestId = response.responseMetadata().requestId();

		BedrockUsage usage = BedrockUsage.from(response.usage());

		ConverseMetrics metrics = response.metrics();

		return new BedrockChatResponseMetadata(requestId, usage, metrics.latencyMs());
	}

	public static BedrockChatResponseMetadata from(ConverseStreamMetadataEvent converseStreamMetadataEvent) {
		BedrockUsage usage = BedrockUsage.from(converseStreamMetadataEvent.usage());

		ConverseStreamMetrics metrics = converseStreamMetadataEvent.metrics();

		return new BedrockChatResponseMetadata(null, usage, metrics.latencyMs());
	}

	protected BedrockChatResponseMetadata(String id, BedrockUsage usage, Long latencyMs) {
		this.id = id;
		this.usage = usage;
		this.latencyMs = latencyMs;
	}

	public String getId() {
		return this.id;
	}

	public Long getLatencyMs() {
		return latencyMs;
	}

	@Override
	public Usage getUsage() {
		Usage usage = this.usage;
		return usage != null ? usage : new EmptyUsage();
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getName(), getId(), getUsage(), getLatencyMs());
	}

}
