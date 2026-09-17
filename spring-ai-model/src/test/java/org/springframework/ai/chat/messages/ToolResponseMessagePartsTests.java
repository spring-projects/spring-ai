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

package org.springframework.ai.chat.messages;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link ToolResultPart} view of {@link ToolResponseMessage}: ordered
 * result parts, the derived {@link ToolResponseMessage#getResponses()} view, builder
 * semantics and backward compatibility of the legacy constructor and setter.
 *
 * @author Christian Tzolov
 */
class ToolResponseMessagePartsTests {

	private static final ToolResponse WEATHER = new ToolResponse("call_1", "getWeather", "{\"temp\":20}");

	private static final ToolResponse TIME = new ToolResponse("call_2", "getTime", "12:00");

	private static final Media CHART = Media.builder()
		.mimeType(MimeTypeUtils.IMAGE_PNG)
		.data(new byte[] { 1, 2, 3 })
		.build();

	// -- parts-first construction --

	@Test
	void resultPartsDriveTheResponsesView() {
		ToolResultPart withMedia = new ToolResultPart("call_3", "renderChart",
				List.of(TextPart.of("Here is the chart."), MediaPart.of(CHART)), false, null, Map.of());
		ToolResponseMessage message = ToolResponseMessage.builder()
			.result(ToolResultPart.of(WEATHER))
			.result(withMedia)
			.build();

		assertThat(message.getParts()).containsExactly(ToolResultPart.of(WEATHER), withMedia);
		assertThat(message.getResults()).containsExactly(ToolResultPart.of(WEATHER), withMedia);
		// The legacy view keeps the text and drops what a string cannot carry
		assertThat(message.getResponses()).containsExactly(WEATHER,
				new ToolResponse("call_3", "renderChart", "Here is the chart."));
		assertThat(message.getText()).isEmpty();
		assertThat(message.getMessageType()).isEqualTo(MessageType.TOOL);
	}

	@Test
	void errorFlagAndPayloadSurviveOnTheParts() {
		ToolResultPart failed = new ToolResultPart("call_1", "getWeather", List.of(TextPart.of("boom")), true,
				new OpaquePayload("anthropic", "tool_result", "{}"), Map.of("k", "v"));
		ToolResponseMessage message = ToolResponseMessage.builder().result(failed).build();

		assertThat(message.getResults().get(0).isError()).isTrue();
		assertThat(message.getResults().get(0).payload()).isNotNull();
		assertThat(message.getResponses()).containsExactly(new ToolResponse("call_1", "getWeather", "boom"));
	}

	@Test
	void partsAndResponsesViewsAreUnmodifiable() {
		ToolResponseMessage message = ToolResponseMessage.builder().responses(List.of(WEATHER)).build();

		assertThatThrownBy(() -> message.getParts().add(ToolResultPart.of(TIME)))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> message.getResponses().add(TIME)).isInstanceOf(UnsupportedOperationException.class);
	}

	// -- legacy constructor and setter materialize one text-only result part each --

	@Test
	void legacyResponsesBecomeTextOnlyResultParts() {
		ToolResponseMessage message = ToolResponseMessage.builder().responses(List.of(WEATHER, TIME)).build();

		assertThat(message.getParts()).containsExactly(ToolResultPart.of(WEATHER), ToolResultPart.of(TIME));
		assertThat(message.getResponses()).containsExactly(WEATHER, TIME);
		assertThat(message.getResults().get(0).content()).containsExactly(TextPart.of("{\"temp\":20}"));
		assertThat(message.getResults().get(0).isError()).isFalse();
	}

	@Test
	void emptyMessageHasNoParts() {
		ToolResponseMessage message = ToolResponseMessage.builder().build();

		assertThat(message.getParts()).isEmpty();
		assertThat(message.getResponses()).isEmpty();
		assertThat(message.getText()).isEmpty();
	}

	@Test
	void nullResponseDataRoundTripsAsAResultWithoutContent() {
		// The tool execution code allows a null response text; it must come back as
		// null, not as an empty string
		ToolResponse nullResponse = new ToolResponse("call_1", "getWeather", null);
		ToolResponseMessage message = ToolResponseMessage.builder().responses(List.of(nullResponse)).build();

		assertThat(message.getResults().get(0).content()).isEmpty();
		assertThat(message.getResponses().get(0).responseData()).isNull();
		assertThat(ToolResultPart.of(new ToolResponse("call_1", "getWeather", "")).toToolResponse().responseData())
			.isEmpty();
	}

	@Test
	void explicitPartsComeBeforeLegacyResponses() {
		ToolResponseMessage message = ToolResponseMessage.builder()
			.responses(List.of(TIME))
			.result(ToolResultPart.of(WEATHER))
			.build();

		assertThat(message.getResponses()).containsExactly(WEATHER, TIME);
	}

	@Test
	void legacyProtectedFieldIsStillPopulated() {
		@SuppressWarnings("removal")
		FieldReadingSubclass message = new FieldReadingSubclass(
				ToolResponseMessage.builder().responses(List.of(WEATHER)).build());

		assertThat(message.responsesFromField()).containsExactly(WEATHER);
	}

	// -- equality, copy, toString --

	@Test
	void messagesWithEqualPartsAreEqual() {
		ToolResponseMessage fromResponses = ToolResponseMessage.builder()
			.responses(List.of(WEATHER))
			.metadata(Map.of("k", "v"))
			.build();
		ToolResponseMessage fromParts = ToolResponseMessage.builder()
			.result(ToolResultPart.of(WEATHER))
			.metadata(Map.of("k", "v"))
			.build();
		ToolResponseMessage different = ToolResponseMessage.builder().responses(List.of(TIME)).build();

		assertThat(fromParts).isEqualTo(fromResponses).hasSameHashCodeAs(fromResponses);
		assertThat(fromParts).isNotEqualTo(different);
	}

	@Test
	void mutateKeepsPartsAndMetadata() {
		ToolResultPart withMedia = new ToolResultPart("call_3", "renderChart", List.of(MediaPart.of(CHART)), false,
				null, Map.of());
		ToolResponseMessage original = ToolResponseMessage.builder()
			.result(withMedia)
			.metadata(Map.of("k", "v"))
			.build();

		ToolResponseMessage copy = original.mutate().build();

		assertThat(copy).isEqualTo(original).isNotSameAs(original);
		assertThat(copy.getResults()).containsExactly(withMedia);
	}

	@Test
	void toStringIncludesTheParts() {
		ToolResponseMessage message = ToolResponseMessage.builder().responses(List.of(WEATHER)).build();

		assertThat(message.toString()).contains("ToolResponseMessage").contains("parts=").contains("ToolResultPart");
	}

	@Test
	void nullPartsAreRejected() {
		assertThatThrownBy(() -> ToolResponseMessage.builder().result(null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ToolResponseMessage.builder().results(null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * A subclass reading the deprecated protected {@code responses} field, the way
	 * out-of-tree subclasses could before the parts model.
	 */
	@SuppressWarnings("removal")
	static final class FieldReadingSubclass extends ToolResponseMessage {

		FieldReadingSubclass(ToolResponseMessage source) {
			super(source.getMetadata(), source.getParts());
		}

		List<ToolResponse> responsesFromField() {
			return this.responses;
		}

	}

}
