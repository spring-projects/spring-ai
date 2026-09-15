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

package org.springframework.ai.openai.responses;

/**
 * Metadata keys published by {@link OpenAiResponsesChatModel}, and the part attributes it
 * uses.
 * <p>
 * There are two groups. What a turn <em>reports</em> is published on the
 * {@link org.springframework.ai.chat.metadata.ChatGenerationMetadata generation} or
 * {@link org.springframework.ai.chat.metadata.ChatResponseMetadata response} metadata;
 * what a turn needs to be <em>replayed</em> travels in the
 * {@link org.springframework.ai.chat.messages.part.MessagePart part} attributes and
 * payloads.
 * <p>
 * The assistant message carries no metadata of its own. What a turn reports beyond its
 * parts describes one response rather than the message, and message metadata is persisted
 * by some chat memory repositories and dropped by others, so nothing that matters may
 * live there.
 * <p>
 * The {@code openai.responses.*} keys are specific to this API. {@link #REFUSAL},
 * {@link #ANNOTATIONS} and {@link #REASONING_CONTENT} keep the unprefixed names
 * {@code OpenAiChatModel} publishes on its assistant message, so code moving between the
 * two beans only has to change where it reads them, not what it looks up.
 *
 * @author Dimitar Proynov
 * @since 2.1.0
 */
public final class OpenAiResponsesMetadata {

	/**
	 * The {@link org.springframework.ai.chat.messages.part.OpaquePayload#provider()
	 * provider} of every payload and every
	 * {@link org.springframework.ai.chat.messages.part.UnknownPart} this model produces,
	 * and the only one it replays.
	 * <p>
	 * The endpoint, not the vendor. Chat Completions and Responses are two OpenAI APIs
	 * whose replay payloads are not interchangeable, and a conversation can move between
	 * them, so naming the endpoint is what keeps either one from replaying the other's
	 * data if their handling of payload-carrying parts ever converges.
	 */
	public static final String PROVIDER = "openai.responses";

	/**
	 * The {@link org.springframework.ai.chat.messages.part.OpaquePayload#kind() kind} of
	 * the payload carried by a reasoning part: the {@code encrypted_content} of a
	 * {@code reasoning} item, which has to travel back verbatim for the model to keep its
	 * train of thought across a tool call or a follow-up turn.
	 */
	public static final String ENCRYPTED_CONTENT_KIND = "encrypted_content";

	/**
	 * Part attribute holding the {@code id} of the output item a part was mapped from,
	 * e.g. {@code rs_...} for a reasoning item or {@code fc_...} for a function call.
	 * <p>
	 * This is the <em>item</em> id, which addresses the item's position in the
	 * transcript, never the {@code call_id} that pairs a call with its result. A
	 * reasoning part cannot be replayed without it.
	 */
	public static final String ITEM_ID_ATTRIBUTE = "itemId";

	/**
	 * Part attribute holding the {@code status} of the output item a part was mapped
	 * from: {@code in_progress}, {@code completed} or {@code incomplete}.
	 */
	public static final String ITEM_STATUS_ATTRIBUTE = "itemStatus";

	/**
	 * Part attribute holding the {@code phase} of an assistant message item
	 * ({@code commentary} or {@code final_answer}), which GPT-5.5 and later return to
	 * mark whether a message was a final answer or an intermediate update.
	 * <p>
	 * Replayed so the model does not mistake a prior intermediate update for a final
	 * answer.
	 */
	public static final String PHASE_ATTRIBUTE = "phase";

	/**
	 * The response status: {@code completed}, {@code incomplete}, {@code failed}, ...
	 */
	public static final String STATUS = "openai.responses.status";

	/**
	 * Why an {@code incomplete} response stopped: {@code max_output_tokens} or
	 * {@code content_filter}.
	 */
	public static final String INCOMPLETE_REASON = "openai.responses.incomplete_reason";

	/**
	 * Summary of the tools OpenAI executed server-side within the request, as a list of
	 * {@code {type, id, status}} maps. These are never surfaced as tool calls.
	 * <p>
	 * The summary describes activity inside a single request, so it is neither replayed on
	 * the next turn nor worth persisting with the conversation. The authoritative copy of
	 * each item is the {@link org.springframework.ai.chat.messages.part.UnknownPart} it was
	 * mapped to, which holds the item verbatim and is replayed as-is.
	 */
	public static final String HOSTED_TOOL_CALLS = "openai.responses.hosted_tool_calls";

	/**
	 * The response creation timestamp, in seconds since the epoch.
	 */
	public static final String CREATED_AT = "openai.responses.created_at";

	/**
	 * The model's refusal text, or an empty string.
	 * <p>
	 * There is no refusal part type, so this is the only place a refusal surfaces. On a
	 * stream it is the running total, published on every chunk that extends it, which is
	 * what lets it survive aggregation.
	 */
	public static final String REFUSAL = "refusal";

	/**
	 * Citations attached to the generated text, e.g. from web or file search.
	 * <p>
	 * The key matches {@code OpenAiChatModel}, but the value shape does not: this model
	 * publishes a {@code List<Map<String, Object>>}, whereas {@code OpenAiChatModel}
	 * publishes the OpenAI SDK's own annotation objects. Code that casts the entries has
	 * to be adjusted when swapping one bean for the other.
	 */
	public static final String ANNOTATIONS = "annotations";

	/**
	 * Reasoning summary text, one line per reasoning item. OpenAI never returns raw
	 * reasoning text, only summaries and an encrypted blob.
	 * <p>
	 * Published under the key {@code OpenAiChatModel} and DeepSeek surface reasoning
	 * under. It is a flattened view: the authoritative form is the ordered
	 * {@link org.springframework.ai.chat.messages.part.ReasoningPart}s on the assistant
	 * message, which keep each item separate, in place among the tool calls it justified,
	 * and carry the encrypted content needed to replay it.
	 */
	public static final String REASONING_CONTENT = "reasoningContent";

	private OpenAiResponsesMetadata() {
	}

}
