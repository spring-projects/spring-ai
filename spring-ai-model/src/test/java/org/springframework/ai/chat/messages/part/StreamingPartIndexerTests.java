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

package org.springframework.ai.chat.messages.part;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StreamingPartIndexer}.
 *
 * @author Dimitar Proynov
 */
class StreamingPartIndexerTests {

	private final StreamingPartIndexer indexer = new StreamingPartIndexer();

	@Test
	void consecutiveDeltasOfOneKindShareAPartialIndex() {
		assertThat(this.indexer.stamp(ReasoningPart.of("Think ")))
			.isEqualTo(StreamingParts.partial(ReasoningPart.of("Think "), 0));
		assertThat(this.indexer.stamp(ReasoningPart.of("more.")))
			.isEqualTo(StreamingParts.partial(ReasoningPart.of("more."), 0));
		assertThat(this.indexer.stamp(TextPart.of("Hel"))).isEqualTo(StreamingParts.partial(TextPart.of("Hel"), 1));
		assertThat(this.indexer.stamp(TextPart.of("lo"))).isEqualTo(StreamingParts.partial(TextPart.of("lo"), 1));
	}

	@Test
	void switchingDeltaKindStartsTheNextIndex() {
		assertThat(StreamingParts.partIndex(this.indexer.stamp(ReasoningPart.of("a")))).isZero();
		assertThat(StreamingParts.partIndex(this.indexer.stamp(TextPart.of("b")))).isEqualTo(1);
		assertThat(StreamingParts.partIndex(this.indexer.stamp(ReasoningPart.of("c")))).isEqualTo(2);
	}

	@Test
	void otherPartsAreCompleteWithAnIndexOfTheirOwnAndEndTheRun() {
		ToolCallPart first = ToolCallPart.of(new ToolCall("call_1", "function", "getWeather", "{}"));
		ToolCallPart second = ToolCallPart.of(new ToolCall("call_2", "function", "getTime", "{}"));

		this.indexer.stamp(TextPart.of("Let me check."));

		assertThat(this.indexer.stamp(first)).isEqualTo(StreamingParts.complete(first, 1));
		assertThat(this.indexer.stamp(second)).isEqualTo(StreamingParts.complete(second, 2));
		assertThat(this.indexer.stamp(TextPart.of("Done"))).isEqualTo(StreamingParts.partial(TextPart.of("Done"), 3));
	}

	@Test
	void mediaIsStampedComplete() {
		MediaPart media = MediaPart.of(Media.builder()
			.mimeType(MimeTypeUtils.IMAGE_PNG)
			.data(new ByteArrayResource(new byte[] { 1, 2, 3 }))
			.build());

		MessagePart stamped = this.indexer.stamp(media);

		assertThat(StreamingParts.partIndex(stamped)).isZero();
		assertThat(StreamingParts.isPartial(stamped)).isFalse();
	}

	@Test
	void emptyTextWithoutPayloadIsLeftUnindexedAndDoesNotEndTheRun() {
		this.indexer.stamp(ReasoningPart.of("Step one."));

		TextPart placeholder = TextPart.of("");
		assertThat(this.indexer.stamp(placeholder)).isSameAs(placeholder);
		assertThat(this.indexer.stamp(ReasoningPart.of("Step two.")))
			.isEqualTo(StreamingParts.partial(ReasoningPart.of("Step two."), 0));
	}

	@Test
	void emptyTextWithPayloadIsIndexed() {
		TextPart signed = new TextPart("", new OpaquePayload("google", "thought_signature", "sig"), Map.of());

		assertThat(this.indexer.stamp(signed)).isEqualTo(StreamingParts.partial(signed, 0));
	}

	@Test
	void stampingKeepsTheOtherAttributesAndThePayload() {
		OpaquePayload payload = new OpaquePayload("google", "thought_signature", "sig");
		ReasoningPart part = new ReasoningPart("thought", null, payload, Map.of("itemId", "rs_1"));

		MessagePart stamped = this.indexer.stamp(part);

		assertThat(stamped.payload()).isEqualTo(payload);
		assertThat(stamped.attributes()).containsEntry("itemId", "rs_1")
			.containsEntry(StreamingParts.PART_INDEX_ATTRIBUTE, "0")
			.containsEntry(StreamingParts.PARTIAL_ATTRIBUTE, "true");
	}

	@Test
	void stampedChunksAggregateToTheOrderedParts() {
		ToolCallPart toolCall = ToolCallPart.of(new ToolCall("call_1", "function", "getWeather", "{}"));
		Flux<ChatResponse> chunks = Flux
			.just(ReasoningPart.of("Think "), ReasoningPart.of("more."), TextPart.of(""), TextPart.of("Hel"),
					TextPart.of("lo"), toolCall)
			.map(this.indexer::stamp)
			.map(part -> new ChatResponse(List.of(new Generation(AssistantMessage.builder().part(part).build()))));

		AtomicReference<ChatResponse> aggregated = new AtomicReference<>();
		new MessageAggregator().aggregate(chunks, aggregated::set).blockLast();

		assertThat(aggregated.get().getResult().getOutput().getParts()).containsExactly(ReasoningPart.of("Think more."),
				TextPart.of("Hello"), toolCall);
	}

}
