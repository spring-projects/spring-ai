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

package org.springframework.ai.chat.client.advisor;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

/**
 * A {@link StreamAdvisor} that uses a {@link ChatModel} to generate a streaming response.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class ChatModelStreamAdvisor implements StreamAdvisor {

	private final ChatModel chatModel;

	private ChatModelStreamAdvisor(ChatModel chatModel) {
		Assert.notNull(chatModel, "chatModel cannot be null");
		this.chatModel = chatModel;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		Assert.notNull(chatClientRequest, "the chatClientRequest cannot be null");

		return this.chatModel.stream(chatClientRequest.prompt())
			.map(chatResponse -> ChatClientResponse.builder()
				.chatResponse(chatResponse)
				.context(Map.copyOf(chatClientRequest.context()))
				.build())
			.publishOn(Schedulers.boundedElastic()); // TODO add option to disable
	}

	@Override
	public String getName() {
		return "stream";
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable ChatModel chatModel;

		private Builder() {
		}

		public Builder chatModel(ChatModel chatModel) {
			this.chatModel = chatModel;
			return this;
		}

		public ChatModelStreamAdvisor build() {
			Assert.state(this.chatModel != null, "chatModel cannot be null");
			return new ChatModelStreamAdvisor(this.chatModel);
		}

	}

}
