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

package org.springframework.ai.openai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions.Api;
import org.springframework.ai.openai.setup.OpenAiSetup;

/**
 * Decides which OpenAI endpoint a request goes to, and reports the options the chosen one
 * ignores.
 * <p>
 * {@link OpenAiChatOptions} is shared by both endpoints, so a handful of its settings
 * only mean something on one of them. Setting such an option is not an error - a
 * {@code ChatClient} bean configured once is routinely used against both endpoints, and
 * failing a request over an option the other endpoint would have honoured would make the
 * two impossible to swap. Each ignored option is therefore logged once per JVM and
 * dropped.
 *
 * @author Dimitar Proynov
 * @since 2.1.0
 */
final class OpenAiChatApiSelection {

	/**
	 * Chat Completions stopped serving reasoning together with tool calling at this
	 * version, which makes the Responses API the only endpoint that can run a reasoning
	 * model as an agent. {@link Api#AUTO} therefore switches over here rather than at the
	 * next major model family.
	 */
	private static final int RESPONSES_MODEL_MAJOR = 5;

	private static final int RESPONSES_MODEL_MINOR = 4;

	/**
	 * Matches the leading version of an OpenAI model name, e.g. {@code gpt-5.4-mini},
	 * {@code gpt-5.6-luna} or {@code gpt-6}. Deliberately anchored: a Microsoft Foundry
	 * deployment name or a third-party model is left to Chat Completions, which is the
	 * endpoint every OpenAI-compatible provider implements.
	 */
	private static final Pattern GPT_VERSION = Pattern.compile("^gpt-(\\d+)(?:\\.(\\d+))?");

	/**
	 * Options the Responses API has no equivalent for, and the accessor that tells
	 * whether the caller set one.
	 */
	private static final Map<String, Function<OpenAiChatOptions, @Nullable Object>> CHAT_COMPLETIONS_ONLY = chatCompletionsOnlyOptions();

	/**
	 * Options only the Responses API provides. Any of them is enough for {@link Api#AUTO}
	 * to select it.
	 */
	private static final Map<String, Function<OpenAiChatOptions, @Nullable Object>> RESPONSES_ONLY = responsesOnlyOptions();

	private static final Log logger = LogFactory.getLog(OpenAiChatApiSelection.class);

	/**
	 * Warned about once per JVM, not once per request.
	 */
	private static final Set<String> WARNED_OPTIONS = ConcurrentHashMap.newKeySet();

	private OpenAiChatApiSelection() {
	}

	/**
	 * The endpoint the given options amount to, with {@link Api#AUTO} decided.
	 * @param options the options of the request, or of the model
	 * @return either {@link Api#CHAT_COMPLETIONS} or {@link Api#RESPONSES}
	 * @throws IllegalStateException if the Responses API was asked for explicitly but the
	 * configured provider does not serve it
	 */
	static Api resolve(OpenAiChatOptions options) {
		return switch (options.getApi()) {
			case CHAT_COMPLETIONS -> Api.CHAT_COMPLETIONS;
			case RESPONSES -> {
				if (!servesResponses(options)) {
					throw new IllegalStateException("GitHub Models does not support the OpenAI Responses API. "
							+ "Remove spring.ai.openai.chat.api=responses, or point the base URL at a provider that serves it.");
				}
				yield Api.RESPONSES;
			}
			case AUTO -> {
				boolean responses = servesResponses(options)
						&& (isResponsesModel(options.getModel()) || usesResponsesOnlyOption(options));
				yield responses ? Api.RESPONSES : Api.CHAT_COMPLETIONS;
			}
		};
	}

	/**
	 * Log, once per option, every setting the endpoint this request goes to cannot
	 * honour.
	 * @param options the options the request is built from
	 * @param api the resolved endpoint
	 * @param runtimeOptions the options as they arrived, before being merged into
	 * {@code options}, or {@code null} if the request carried none
	 */
	static void warnIgnoredOptions(OpenAiChatOptions options, Api api, @Nullable ChatOptions runtimeOptions) {
		if (runtimeOptions != null && runtimeOptions.getTopK() != null) {
			warnOnce("topK", "The topK option is not supported by OpenAI chat models. Ignoring.");
		}
		Map<String, Function<OpenAiChatOptions, @Nullable Object>> ignored = api == Api.RESPONSES
				? CHAT_COMPLETIONS_ONLY : RESPONSES_ONLY;
		ignored.forEach((option, accessor) -> {
			if (isSet(accessor.apply(options))) {
				warnOnce(option, message(option, api));
			}
		});
	}

	/**
	 * Whether the model name is one the Responses API is the right endpoint for, that is
	 * GPT-5.4 or later. Anything unrecognized answers {@code false}: Chat Completions is
	 * the safe default, because it is the endpoint that is always there.
	 */
	private static boolean isResponsesModel(String model) {
		Matcher matcher = GPT_VERSION.matcher(model);
		if (!matcher.find()) {
			return false;
		}
		int major = Integer.parseInt(matcher.group(1));
		int minor = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
		if (major != RESPONSES_MODEL_MAJOR) {
			return major > RESPONSES_MODEL_MAJOR;
		}
		return minor >= RESPONSES_MODEL_MINOR;
	}

	private static boolean usesResponsesOnlyOption(OpenAiChatOptions options) {
		return RESPONSES_ONLY.values().stream().anyMatch(accessor -> isSet(accessor.apply(options)));
	}

	/**
	 * GitHub Models only implements {@code /v1/chat/completions}. Other OpenAI-compatible
	 * providers may or may not serve the Responses API, which Spring AI cannot tell in
	 * advance, so they are taken at their word.
	 */
	private static boolean servesResponses(OpenAiChatOptions options) {
		return OpenAiSetup.detectModelProvider(options.isMicrosoftFoundry(), options.isGitHubModels(),
				options.getBaseUrl(), options.getMicrosoftDeploymentName(),
				options.getMicrosoftFoundryServiceVersion()) != OpenAiSetup.ModelProvider.GITHUB_MODELS;
	}

	private static boolean isSet(@Nullable Object value) {
		if (value instanceof Iterable<?> iterable) {
			return iterable.iterator().hasNext();
		}
		if (value instanceof Map<?, ?> map) {
			return !map.isEmpty();
		}
		return value != null;
	}

	private static String message(String option, Api api) {
		return api == Api.RESPONSES ? "The " + option
				+ " option is not supported by the OpenAI Responses API. Ignoring."
				+ " Set spring.ai.openai.chat.api=chat-completions (or api(Api.CHAT_COMPLETIONS)) if you need it."
				: "The " + option + " option is only supported by the OpenAI Responses API. Ignoring."
						+ " Set spring.ai.openai.chat.api=responses (or api(Api.RESPONSES)) to use it.";
	}

	private static void warnOnce(String option, String message) {
		if (logger.isWarnEnabled() && WARNED_OPTIONS.add(option)) {
			logger.warn(message);
		}
	}

	private static Map<String, Function<OpenAiChatOptions, @Nullable Object>> chatCompletionsOnlyOptions() {
		Map<String, Function<OpenAiChatOptions, @Nullable Object>> options = new LinkedHashMap<>();
		options.put("frequencyPenalty", OpenAiChatOptions::getFrequencyPenalty);
		options.put("presencePenalty", OpenAiChatOptions::getPresencePenalty);
		options.put("stop", OpenAiChatOptions::getStop);
		options.put("logitBias", OpenAiChatOptions::getLogitBias);
		options.put("logprobs", OpenAiChatOptions::getLogprobs);
		options.put("n", OpenAiChatOptions::getN);
		options.put("seed", OpenAiChatOptions::getSeed);
		options.put("store", OpenAiChatOptions::getStore);
		options.put("streamOptions", OpenAiChatOptions::getStreamOptions);
		options.put("outputModalities", OpenAiChatOptions::getOutputModalities);
		options.put("outputAudio", OpenAiChatOptions::getOutputAudio);
		// Deprecated by OpenAI in favour of safetyIdentifier, which the Responses API is
		// the only one of the two to accept
		options.put("user", OpenAiChatOptions::getUser);
		return Map.copyOf(options);
	}

	private static Map<String, Function<OpenAiChatOptions, @Nullable Object>> responsesOnlyOptions() {
		Map<String, Function<OpenAiChatOptions, @Nullable Object>> options = new LinkedHashMap<>();
		options.put("reasoningSummary", OpenAiChatOptions::getReasoningSummary);
		options.put("maxToolCalls", OpenAiChatOptions::getMaxToolCalls);
		options.put("serversideTools", OpenAiChatOptions::getServersideTools);
		options.put("include", OpenAiChatOptions::getInclude);
		options.put("truncation", OpenAiChatOptions::getTruncation);
		return Map.copyOf(options);
	}

}
