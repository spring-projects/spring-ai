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

package org.springframework.ai.chat.client.advisor;

import java.util.List;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * An advisor that uses a {@link ModerationModel} to decide whether a request should be
 * blocked before invoking the next advisor in the chain.
 *
 * @author Kim Taewoong
 * @since 2.0.0
 */
public final class ModerationAdvisor implements CallAdvisor, StreamAdvisor {

	public static final String MODERATION_RESPONSE_CONTEXT_KEY = "moderation.response";

	public static final String MODERATION_BLOCKED_CONTEXT_KEY = "moderation.blocked";

	private static final String DEFAULT_FAILURE_RESPONSE = "I'm unable to respond to that due to safety policy.";

	private static final int DEFAULT_ORDER = 0;

	private final ModerationModel moderationModel;

	private final String failureResponse;

	private final int order;

	private final Scheduler scheduler;

	private final boolean failOpen;

	private ModerationAdvisor(ModerationModel moderationModel, String failureResponse, int order, Scheduler scheduler,
			boolean failOpen) {
		Assert.notNull(moderationModel, "moderationModel must not be null");
		Assert.notNull(failureResponse, "failureResponse must not be null");
		Assert.notNull(scheduler, "scheduler must not be null");
		this.moderationModel = moderationModel;
		this.failureResponse = failureResponse;
		this.order = order;
		this.scheduler = scheduler;
		this.failOpen = failOpen;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		Assert.notNull(chatClientRequest, "chatClientRequest must not be null");
		Assert.notNull(callAdvisorChain, "callAdvisorChain must not be null");

		ModerationResponse moderationResponse;
		try {
			moderationResponse = moderate(chatClientRequest);
		}
		catch (RuntimeException exception) {
			if (this.failOpen) {
				return callAdvisorChain.nextCall(chatClientRequest);
			}
			throw new IllegalStateException("Moderation processing failed", exception);
		}

		if (isFlagged(moderationResponse)) {
			return createFailureResponse(chatClientRequest, moderationResponse);
		}
		return callAdvisorChain.nextCall(addModerationContext(chatClientRequest, moderationResponse, false));
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		Assert.notNull(chatClientRequest, "chatClientRequest must not be null");
		Assert.notNull(streamAdvisorChain, "streamAdvisorChain must not be null");

		Mono<ModerationResponse> moderationResponseMono = Mono.fromCallable(() -> moderate(chatClientRequest))
			.publishOn(this.scheduler);

		if (this.failOpen) {
			moderationResponseMono = moderationResponseMono.onErrorResume(error -> Mono.empty());
		}
		else {
			moderationResponseMono = moderationResponseMono
				.onErrorMap(error -> new IllegalStateException("Moderation processing failed", error));
		}

		return moderationResponseMono.flatMapMany(moderationResponse -> {
			if (isFlagged(moderationResponse)) {
				return Flux.just(createFailureResponse(chatClientRequest, moderationResponse));
			}
			return streamAdvisorChain.nextStream(addModerationContext(chatClientRequest, moderationResponse, false));
		}).switchIfEmpty(Flux.defer(() -> streamAdvisorChain.nextStream(chatClientRequest)));
	}

	private ModerationResponse moderate(ChatClientRequest chatClientRequest) {
		return this.moderationModel.call(new ModerationPrompt(chatClientRequest.prompt().getContents()));
	}

	private static boolean isFlagged(ModerationResponse moderationResponse) {
		if (moderationResponse.getResult() == null || moderationResponse.getResult().getOutput() == null
				|| CollectionUtils.isEmpty(moderationResponse.getResult().getOutput().getResults())) {
			return false;
		}
		return moderationResponse.getResult().getOutput().getResults().stream().anyMatch(ModerationResult::isFlagged);
	}

	private static ChatClientRequest addModerationContext(ChatClientRequest chatClientRequest,
			ModerationResponse moderationResponse, boolean blocked) {
		return chatClientRequest.mutate()
			.context(MODERATION_RESPONSE_CONTEXT_KEY, moderationResponse)
			.context(MODERATION_BLOCKED_CONTEXT_KEY, blocked)
			.build();
	}

	private ChatClientResponse createFailureResponse(ChatClientRequest chatClientRequest,
			ModerationResponse moderationResponse) {
		return ChatClientResponse.builder()
			.chatResponse(ChatResponse.builder()
				.generations(List.of(new Generation(new AssistantMessage(this.failureResponse))))
				.build())
			.context(chatClientRequest.context())
			.context(MODERATION_RESPONSE_CONTEXT_KEY, moderationResponse)
			.context(MODERATION_BLOCKED_CONTEXT_KEY, true)
			.build();
	}

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public static final class Builder {

		private @Nullable ModerationModel moderationModel;

		private String failureResponse = DEFAULT_FAILURE_RESPONSE;

		private int order = DEFAULT_ORDER;

		private Scheduler scheduler = BaseAdvisor.DEFAULT_SCHEDULER;

		private boolean failOpen = true;

		private Builder() {
		}

		public Builder moderationModel(ModerationModel moderationModel) {
			Assert.notNull(moderationModel, "moderationModel must not be null");
			this.moderationModel = moderationModel;
			return this;
		}

		public Builder failureResponse(String failureResponse) {
			Assert.notNull(failureResponse, "failureResponse must not be null");
			this.failureResponse = failureResponse;
			return this;
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public Builder scheduler(Scheduler scheduler) {
			Assert.notNull(scheduler, "scheduler must not be null");
			this.scheduler = scheduler;
			return this;
		}

		public Builder failOpen(boolean failOpen) {
			this.failOpen = failOpen;
			return this;
		}

		public ModerationAdvisor build() {
			Assert.state(this.moderationModel != null, "moderationModel must not be null");
			return new ModerationAdvisor(this.moderationModel, this.failureResponse, this.order, this.scheduler,
					this.failOpen);
		}

	}

}
