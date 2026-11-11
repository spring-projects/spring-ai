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

package org.springframework.ai.chat.client.advisor.guardrail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.GuardrailRefusal;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionOptions;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Screens what the user sends and what the model answers with a {@link DecisionModel},
 * refusing the turn when either crosses a line.
 *
 * <p>
 * Both directions are checked because they fail differently. An input battery catches the
 * request that should never have been made; an output battery catches the reply that
 * should never have been given, which is the only one of the two that notices a jailbreak
 * that actually worked. A battery screens a text in a single call carrying all its
 * hazards.
 *
 * <p>
 * A blocked input never reaches the model at all: the refusal is returned in place of the
 * call, so nothing is spent and nothing is generated. A blocked output replaces the
 * generated text.
 *
 * <h3>Where it sits, and why the two directions belong in different places</h3>
 *
 * The request is best screened at the outer edge of the chain and the reply at the inner
 * one, so the recommended setup is two advisors, one per direction:
 *
 * <pre>{@code
 * ChatClient.builder(chatModel)
 *     .defaultAdvisors(DecisionGuardrailAdvisor.input(decisionModel),
 *             DecisionGuardrailAdvisor.output(decisionModel))
 *     .defaultAdvisors(SessionAdvisors.builder(sessionService).build())
 *     .build();
 * }</pre>
 *
 * {@link #input(DecisionModel)} runs at {@link #BEFORE_MEMORY_ORDER}, ahead of the
 * session and chat memory advisors and of retrieval. There it screens what the person
 * actually wrote, once per turn, before a retrieval advisor inlines documents into the
 * message; and a blocked message is never stored, so it cannot come back as history on
 * the next turn. {@link #output(DecisionModel)} runs at {@link #DEFAULT_ORDER}, nearer
 * the model than memory and the tool-calling loop. There it screens every reply the model
 * gives, and what memory stores is the refusal rather than the reply it replaced.
 *
 * <p>
 * A single advisor from {@link #builder(DecisionModel)} screens both directions at
 * {@link #DEFAULT_ORDER}. That is simpler, and enough for a chain with no memory and no
 * advisor that rewrites the user message. With them, its input battery sees the rewritten
 * message rather than the person's, runs again on every model call, and a blocked message
 * is already in memory by the time it is refused.
 *
 * <h3>Streaming</h3>
 *
 * The input is screened before the stream starts. An output battery needs the whole reply
 * before it can judge it, so with one configured the stream is held back until the reply
 * is complete and screened, and is then released as it was received, or replaced by the
 * refusal. An advisor that screens only the input streams through untouched.
 *
 * <h3>What a refusal looks like</h3>
 *
 * A refusal is an ordinary reply: one generation whose text is the refusal or the support
 * message. Two markers tell it from an answer of the model: the generation is a
 * {@link GuardrailRefusal}, and the response context holds the outcome under
 * {@link #OUTCOME_CONTEXT_KEY}. A refused reply keeps the metadata of the reply it
 * replaces, the token usage included, since that reply was generated and paid for.
 * <p>
 * As a {@link GuardrailRefusal}, a refusal is not taken for structured output:
 * {@code ChatClient.entity(...)} throws a {@code GuardrailRefusalException} that carries
 * the refusal and the context, and a {@code StructuredOutputValidationAdvisor} returns it
 * without validating it or asking the model again.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see DecisionGuardrail
 */
public final class DecisionGuardrailAdvisor implements CallAdvisor, StreamAdvisor {

	/**
	 * The default order: nearer the model than memory and the tool-calling loop, so every
	 * reply is screened before anything else sees it.
	 */
	public static final int DEFAULT_ORDER = BaseAdvisor.LOWEST_PRECEDENCE - 1000;

	/**
	 * An order ahead of the session and chat memory advisors
	 * ({@code HIGHEST_PRECEDENCE + 200}) and everything after them, where the user
	 * message is still what the person wrote and a blocked one is never remembered. The
	 * place for screening the input.
	 */
	public static final int BEFORE_MEMORY_ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

	/** What a blocked turn says when the caller has not supplied wording. */
	public static final String DEFAULT_REFUSAL = "I can't help with that.";

	/** What a turn routed to support says when the caller has not supplied wording. */
	public static final String DEFAULT_SUPPORT_MESSAGE = "It sounds like you may be going through something "
			+ "difficult. I can't help with this here, but people who can are available — please consider "
			+ "reaching out to a local support line.";

	/**
	 * The response-context key a refused turn carries, holding the name of the
	 * {@link DecisionGuardrail.Outcome} the turn was refused with:
	 * {@link DecisionGuardrail.Outcome#BLOCK BLOCK} or
	 * {@link DecisionGuardrail.Outcome#SUPPORT SUPPORT}. It marks the response as the
	 * guardrail's own words rather than the model's answer, so an advisor further out
	 * that evaluates or retries a response can leave a refusal alone.
	 */
	public static final String OUTCOME_CONTEXT_KEY = "decision.guardrail.outcome";

	private static final Logger logger = LoggerFactory.getLogger(DecisionGuardrailAdvisor.class);

	private final DecisionModel decisionModel;

	private final @Nullable DecisionOptions options;

	private final @Nullable DecisionGuardrail inputBattery;

	private final @Nullable DecisionGuardrail outputBattery;

	private final String refusal;

	private final String supportMessage;

	private final boolean blockOnReview;

	private final int order;

	private final Scheduler scheduler;

	private DecisionGuardrailAdvisor(Builder builder) {
		this.decisionModel = builder.decisionModel;
		this.options = builder.options;
		this.inputBattery = builder.screensInput
				? (builder.inputBattery != null ? builder.inputBattery : DecisionGuardrail.defaultInputBattery())
				: null;
		this.outputBattery = builder.screensOutput
				? (builder.outputBattery != null ? builder.outputBattery : DecisionGuardrail.defaultOutputBattery())
				: null;
		this.refusal = builder.refusal;
		this.supportMessage = builder.supportMessage;
		this.blockOnReview = builder.blockOnReview;
		this.order = builder.order;
		this.scheduler = builder.scheduler;
	}

	@Override
	public String getName() {
		return "Decision Guardrail Advisor";
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		Assert.notNull(chatClientRequest, "chatClientRequest must not be null");
		Assert.notNull(callAdvisorChain, "callAdvisorChain must not be null");

		// Refused before the chain runs, so the model is never called.
		ChatClientResponse refusedInput = screenInput(chatClientRequest);
		if (refusedInput != null) {
			return refusedInput;
		}

		ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);

		ChatClientResponse refusedOutput = screenOutput(List.of(response));
		return (refusedOutput != null) ? refusedOutput : response;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		Assert.notNull(chatClientRequest, "chatClientRequest must not be null");
		Assert.notNull(streamAdvisorChain, "streamAdvisorChain must not be null");

		if (this.inputBattery == null) {
			return screenedStream(chatClientRequest, streamAdvisorChain);
		}
		// The screening call blocks, so it runs on the scheduler rather than on the
		// subscriber's thread.
		return Mono.fromCallable(() -> Optional.ofNullable(screenInput(chatClientRequest)))
			.subscribeOn(this.scheduler)
			.flatMapMany(refused -> refused.isPresent() ? Flux.just(refused.get())
					: screenedStream(chatClientRequest, streamAdvisorChain));
	}

	/**
	 * The stream of the rest of the chain. Without an output battery it passes through.
	 * With one it is held back until the reply is complete and screened: chunks already
	 * emitted could not be taken back.
	 */
	private Flux<ChatClientResponse> screenedStream(ChatClientRequest request, StreamAdvisorChain chain) {
		Flux<ChatClientResponse> stream = chain.nextStream(request);
		if (this.outputBattery == null) {
			return stream;
		}
		return stream.collectList()
			.flatMapMany(chunks -> Mono.fromCallable(() -> Optional.ofNullable(screenOutput(chunks)))
				.subscribeOn(this.scheduler)
				.flatMapMany(refused -> refused.isPresent() ? Flux.just(refused.get()) : Flux.fromIterable(chunks)));
	}

	/**
	 * Screens the user message.
	 * @return the refusal to return in place of the call, or {@code null} to proceed
	 */
	private @Nullable ChatClientResponse screenInput(ChatClientRequest request) {
		if (this.inputBattery == null) {
			return null;
		}
		String text = request.prompt().getUserMessage().getText();
		if (!StringUtils.hasText(text)) {
			return null;
		}
		DecisionGuardrail.Verdict verdict = this.inputBattery.screen(this.decisionModel, text, this.options);
		if (shouldBlock(verdict)) {
			logger.warn("The decision guardrail blocked the request ({} battery): {}", this.inputBattery.name(),
					verdict.summary());
			// Nothing was generated, so there is no reply whose metadata to keep.
			return refuse(request.context(), null, this.inputBattery, verdict);
		}
		logReview(this.inputBattery, verdict);
		return null;
	}

	/**
	 * Screens every text of the reply that the caller can read.
	 * @param reply the response of a call, or the responses of a stream in order
	 * @return the refusal to return in place of the reply, or {@code null} to let it
	 * through
	 */
	private @Nullable ChatClientResponse screenOutput(List<ChatClientResponse> reply) {
		if (this.outputBattery == null) {
			return null;
		}
		for (String text : ReplyTexts.of(reply)) {
			DecisionGuardrail.Verdict verdict = this.outputBattery.screen(this.decisionModel, text, this.options);
			if (shouldBlock(verdict)) {
				logger.warn("The decision guardrail blocked the response ({} battery): {}", this.outputBattery.name(),
						verdict.summary());
				return refuse(contextOf(reply), lastChatResponse(reply), this.outputBattery, verdict);
			}
			logReview(this.outputBattery, verdict);
		}
		return null;
	}

	private boolean shouldBlock(DecisionGuardrail.Verdict verdict) {
		return verdict.blocked() || (this.blockOnReview && verdict.outcome() == DecisionGuardrail.Outcome.REVIEW);
	}

	private void logReview(DecisionGuardrail battery, DecisionGuardrail.Verdict verdict) {
		if (verdict.outcome() == DecisionGuardrail.Outcome.REVIEW) {
			logger.info("The decision guardrail flagged a turn for review but let it through ({} battery): {}",
					battery.name(), verdict.summary());
		}
	}

	/**
	 * The response that takes the place of a refused turn.
	 * @param context the context to carry on: the request's for a refused input, the
	 * reply's for a refused output
	 * @param replaced the reply the refusal replaces, whose metadata it keeps, or
	 * {@code null} when nothing was generated
	 */
	private ChatClientResponse refuse(Map<String, @Nullable Object> context, @Nullable ChatResponse replaced,
			DecisionGuardrail battery, DecisionGuardrail.Verdict verdict) {
		// Refused on review, the flagged hazards count too, so that a borderline
		// self-harm signal still gets the support message.
		DecisionGuardrail.Outcome refusedWith = this.blockOnReview ? battery.refusalOutcome(verdict)
				: verdict.outcome();
		String text = (refusedWith == DecisionGuardrail.Outcome.SUPPORT) ? this.supportMessage : this.refusal;
		List<Generation> generations = List.of(GuardrailRefusal.generation(text));
		ChatResponse chatResponse = (replaced != null)
				? ChatResponse.builder().from(replaced).generations(generations).build()
				: new ChatResponse(generations);
		Map<String, @Nullable Object> refusedContext = new HashMap<>(context);
		refusedContext.put(OUTCOME_CONTEXT_KEY, refusedWith.name());
		return ChatClientResponse.builder().chatResponse(chatResponse).context(refusedContext).build();
	}

	/** The context of a reply: every entry of its responses, later ones winning. */
	private static Map<String, @Nullable Object> contextOf(List<ChatClientResponse> reply) {
		Map<String, @Nullable Object> context = new HashMap<>();
		reply.forEach(response -> context.putAll(response.context()));
		return context;
	}

	/** The last response of a reply, which is where a stream reports the token usage. */
	private static @Nullable ChatResponse lastChatResponse(List<ChatClientResponse> reply) {
		for (int i = reply.size() - 1; i >= 0; i--) {
			ChatResponse chatResponse = reply.get(i).chatResponse();
			if (chatResponse != null) {
				return chatResponse;
			}
		}
		return null;
	}

	/**
	 * A builder of an advisor that screens both directions with the default batteries, at
	 * {@link #DEFAULT_ORDER}.
	 * @param decisionModel the model the hazards are put to
	 * @return a new builder
	 */
	public static Builder builder(DecisionModel decisionModel) {
		return new Builder(decisionModel);
	}

	/**
	 * An advisor that screens only the user message, with the default input battery, at
	 * {@link #BEFORE_MEMORY_ORDER}. Pair it with {@link #output(DecisionModel)}. For
	 * other settings, the same is
	 * {@code builder(decisionModel).outputBattery(null).order(BEFORE_MEMORY_ORDER)}.
	 * @param decisionModel the model the hazards are put to
	 * @return the advisor
	 */
	public static DecisionGuardrailAdvisor input(DecisionModel decisionModel) {
		return builder(decisionModel).outputBattery(null).order(BEFORE_MEMORY_ORDER).build();
	}

	/**
	 * An advisor that screens only the reply, with the default output battery, at
	 * {@link #DEFAULT_ORDER}. Pair it with {@link #input(DecisionModel)}. For other
	 * settings, the same is {@code builder(decisionModel).inputBattery(null)}.
	 * @param decisionModel the model the hazards are put to
	 * @return the advisor
	 */
	public static DecisionGuardrailAdvisor output(DecisionModel decisionModel) {
		return builder(decisionModel).inputBattery(null).build();
	}

	/**
	 * Builder for {@link DecisionGuardrailAdvisor}.
	 */
	public static final class Builder {

		private final DecisionModel decisionModel;

		private @Nullable DecisionOptions options;

		private @Nullable DecisionGuardrail inputBattery;

		private boolean screensInput = true;

		private @Nullable DecisionGuardrail outputBattery;

		private boolean screensOutput = true;

		private String refusal = DEFAULT_REFUSAL;

		private String supportMessage = DEFAULT_SUPPORT_MESSAGE;

		private boolean blockOnReview;

		private int order = DEFAULT_ORDER;

		private Scheduler scheduler = BaseAdvisor.DEFAULT_SCHEDULER;

		private Builder(DecisionModel decisionModel) {
			Assert.notNull(decisionModel, "decisionModel must not be null");
			this.decisionModel = decisionModel;
		}

		/**
		 * Sets the options sent with every call to the model, such as the model name.
		 * @param options the options; {@code null} for the model's defaults
		 * @return this builder
		 */
		public Builder options(@Nullable DecisionOptions options) {
			this.options = options;
			return this;
		}

		/**
		 * Sets what to ask of the user message.
		 * @param inputBattery the battery, or {@code null} to skip screening the input
		 * @return this builder
		 */
		public Builder inputBattery(@Nullable DecisionGuardrail inputBattery) {
			this.inputBattery = inputBattery;
			this.screensInput = (inputBattery != null);
			return this;
		}

		/**
		 * Sets what to ask of the reply.
		 * @param outputBattery the battery, or {@code null} to skip screening the output
		 * @return this builder
		 */
		public Builder outputBattery(@Nullable DecisionGuardrail outputBattery) {
			this.outputBattery = outputBattery;
			this.screensOutput = (outputBattery != null);
			return this;
		}

		/**
		 * Sets what to say when a turn is blocked.
		 * @param refusal the wording
		 * @return this builder
		 */
		public Builder refusal(String refusal) {
			Assert.hasText(refusal, "refusal must not be empty");
			this.refusal = refusal;
			return this;
		}

		/**
		 * Sets what to say when a turn is routed to support.
		 * @param supportMessage the wording
		 * @return this builder
		 */
		public Builder supportMessage(String supportMessage) {
			Assert.hasText(supportMessage, "supportMessage must not be empty");
			this.supportMessage = supportMessage;
			return this;
		}

		/**
		 * Refuses a flagged-for-review turn too, with the support message when a flagged
		 * hazard routes to support and the refusal otherwise. Off by default: review
		 * exists so a borderline case reaches a human instead of being refused by a
		 * threshold.
		 * @param blockOnReview whether to refuse on review
		 * @return this builder
		 */
		public Builder blockOnReview(boolean blockOnReview) {
			this.blockOnReview = blockOnReview;
			return this;
		}

		/**
		 * Sets where in the chain the advisor runs; {@link #DEFAULT_ORDER} unless set.
		 * {@link #BEFORE_MEMORY_ORDER} is the place for an advisor that screens only the
		 * input.
		 * @param order the order, between {@code HIGHEST_PRECEDENCE} and
		 * {@code LOWEST_PRECEDENCE}
		 * @return this builder
		 */
		public Builder order(int order) {
			Assert.isTrue(order > BaseAdvisor.HIGHEST_PRECEDENCE && order < BaseAdvisor.LOWEST_PRECEDENCE,
					"order must be between HIGHEST_PRECEDENCE and LOWEST_PRECEDENCE");
			this.order = order;
			return this;
		}

		/**
		 * Sets the scheduler the screening calls run on when streaming; the decision
		 * model is called synchronously. {@link BaseAdvisor#DEFAULT_SCHEDULER} unless
		 * set.
		 * @param scheduler the scheduler
		 * @return this builder
		 */
		public Builder scheduler(Scheduler scheduler) {
			Assert.notNull(scheduler, "scheduler must not be null");
			this.scheduler = scheduler;
			return this;
		}

		/**
		 * Builds the advisor.
		 * @return the advisor
		 */
		public DecisionGuardrailAdvisor build() {
			Assert.isTrue(this.screensInput || this.screensOutput,
					"a guardrail that screens neither direction does nothing");
			return new DecisionGuardrailAdvisor(this);
		}

	}

}
