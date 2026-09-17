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

import java.util.List;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.RequestOptions;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseStreamEvent;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.util.Assert;

/**
 * Internal implementation of {@link OpenAiChatModel} over OpenAI's {@code /v1/responses}
 * endpoint: it builds the request from a {@link Prompt}, calls the OpenAI SDK, and maps
 * what comes back onto a {@link ChatResponse}.
 * <p>
 * Not API. It implements {@link ChatModel} only so that {@link OpenAiChatModel} can
 * delegate to it, and nothing outside {@link OpenAiChatModel} may hold one: the
 * {@link Prompt} it is handed must already carry fully merged {@link OpenAiChatOptions},
 * and resolving those options, reporting observations and choosing between this class and
 * {@link OpenAiCompletionsChatModel} all belong to {@link OpenAiChatModel}. Nothing here
 * reports an observation of its own.
 * <p>
 * {@code /v1/responses} is the only way to combine reasoning with tool calling on GPT-5.4
 * and later, which Chat Completions no longer supports. It is used
 * <strong>statelessly</strong> here by design: every call sends the whole {@link Prompt}
 * and OpenAI is never asked to remember the conversation, so
 * {@link org.springframework.ai.chat.memory.ChatMemory ChatMemory}, advisors and RAG all
 * keep working exactly as they do against Chat Completions. The stateful modes -
 * {@code previous_response_id}, {@code conversation}, {@code background} - are
 * deliberately not supported.
 * <p>
 * What this endpoint cannot do - audio, {@code n}, stop sequences, frequency and presence
 * penalties, {@code seed}, {@code logit_bias} - and the caveat that reasoning continuity
 * across turns depends on the chat memory repository in use, since none of them persists
 * {@link org.springframework.ai.chat.messages.part.MessagePart message parts} yet, are
 * covered in the reference documentation.
 *
 * @author Dimitar Proynov
 */
final class OpenAiResponsesChatModel implements ChatModel {

	private final OpenAIClient openAiClient;

	private final OpenAIClientAsync openAiClientAsync;

	private final ToolCallingManager toolCallingManager;

	OpenAiResponsesChatModel(OpenAIClient openAiClient, OpenAIClientAsync openAiClientAsync,
			ToolCallingManager toolCallingManager) {
		this.openAiClient = openAiClient;
		this.openAiClientAsync = openAiClientAsync;
		this.toolCallingManager = toolCallingManager;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		ResponseCreateParams request = ResponsesRequestBuilder.build(prompt, this.toolCallingManager);
		RequestOptions requestOptions = requestOptions(options(prompt));

		Response response = this.openAiClient.responses().create(request, requestOptions);
		if (isFailed(response)) {
			throw ResponsesItemMapper.failure(response);
		}

		Generation generation = ResponsesItemMapper.toGeneration(response);
		Usage usage = ResponsesItemMapper.toUsage(response);
		return new ChatResponse(List.of(generation), ResponsesItemMapper.toResponseMetadata(response, usage));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		ResponseCreateParams request = ResponsesRequestBuilder.build(prompt, this.toolCallingManager);
		RequestOptions requestOptions = requestOptions(options(prompt));

		Flux<ResponseStreamEvent> events = Flux.create(sink -> {
			AsyncStreamResponse<ResponseStreamEvent> response = this.openAiClientAsync.responses()
				.createStreaming(request, requestOptions);
			sink.onDispose(response::close);
			response.subscribe(sink::next).onCompleteFuture().whenComplete((unused, throwable) -> {
				if (throwable != null) {
					sink.error(throwable);
				}
				else {
					sink.complete();
				}
			});
		});

		// One assembler per subscription: it accumulates the transcript and the running
		// reasoning summary for this stream only.
		ResponsesStreamAssembler assembler = new ResponsesStreamAssembler();
		return events.concatMapIterable(assembler::apply);
	}

	private static boolean isFailed(Response response) {
		return response.status().filter(status -> ResponseStatus.FAILED.equals(status)).isPresent();
	}

	private static OpenAiChatOptions options(Prompt prompt) {
		OpenAiChatOptions options = (OpenAiChatOptions) prompt.getOptions();
		Assert.state(options != null, "Prompt options must be OpenAiChatOptions type");
		return options;
	}

	/**
	 * Per-request SDK options. Only the timeout is set here; everything else about the
	 * transport is configured on the client, once.
	 */
	private static RequestOptions requestOptions(OpenAiChatOptions options) {
		RequestOptions.Builder requestOptions = RequestOptions.builder();
		if (options.getTimeout() != null) {
			requestOptions.timeout(options.getTimeout());
		}
		return requestOptions.build();
	}

}
