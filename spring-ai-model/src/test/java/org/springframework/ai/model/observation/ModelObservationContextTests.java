/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.observation;

import org.junit.jupiter.api.Test;

import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ModelObservationContext}.
 *
 * @author Thomas Vitale
 */
class ModelObservationContextTests {

	@Test
	void whenRequestAndMetadataThenReturn() {
		var observationContext = new ModelObservationContext<String, String>("test request",
				AiOperationMetadata.builder()
					.operationType(AiOperationType.CHAT.value())
					.provider(AiProvider.OLLAMA.value())
					.build());

		assertThat(observationContext).isNotNull();
	}

	@Test
	void whenRequestIsNullThenThrow() {
		assertThatThrownBy(() -> new ModelObservationContext<String, String>(null,
				AiOperationMetadata.builder()
					.operationType(AiOperationType.EMBEDDING.value())
					.provider(AiProvider.OLLAMA.value())
					.build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("request cannot be null");
	}

	@Test
	void whenOperationMetadataIsNullThenThrow() {
		assertThatThrownBy(() -> new ModelObservationContext<String, String>("test request", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("operationMetadata cannot be null");
	}

	@Test
	void whenOperationMetadataIsMissingOperationTypeThenThrow() {
		assertThatThrownBy(() -> new ModelObservationContext<String, String>("test request",
				AiOperationMetadata.builder().provider(AiProvider.OLLAMA.value()).build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("operationType cannot be null or empty");
	}

	@Test
	void whenOperationMetadataIsMissingProviderThenThrow() {
		assertThatThrownBy(() -> new ModelObservationContext<String, String>("test request",
				AiOperationMetadata.builder().operationType(AiOperationType.IMAGE.value()).build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("provider cannot be null or empty");
	}

	@Test
	void whenResponseThenReturn() {
		var observationContext = new ModelObservationContext<String, String>("test request",
				AiOperationMetadata.builder()
					.operationType(AiOperationType.CHAT.value())
					.provider(AiProvider.OLLAMA.value())
					.build());
		observationContext.setResponse("test response");

		assertThat(observationContext).isNotNull();
	}

	@Test
	void whenResponseIsNullThenThrow() {
		var observationContext = new ModelObservationContext<String, String>("test request",
				AiOperationMetadata.builder()
					.operationType(AiOperationType.CHAT.value())
					.provider(AiProvider.OLLAMA.value())
					.build());
		assertThatThrownBy(() -> observationContext.setResponse(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("response cannot be null");
	}

	@Test
	void whenEmptyOperationTypeThenThrow() {
		assertThatThrownBy(() -> new ModelObservationContext<String, String>("test request",
				AiOperationMetadata.builder().operationType("").provider(AiProvider.OLLAMA.value()).build()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void whenEmptyProviderThenThrow() {
		assertThatThrownBy(() -> new ModelObservationContext<String, String>("test request",
				AiOperationMetadata.builder().operationType(AiOperationType.CHAT.value()).provider("").build()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void whenDifferentProvidersThenReturn() {
		var ollamaContext = new ModelObservationContext<String, String>("test request",
				AiOperationMetadata.builder()
					.operationType(AiOperationType.CHAT.value())
					.provider(AiProvider.OLLAMA.value())
					.build());

		var openaiContext = new ModelObservationContext<String, String>("test request",
				AiOperationMetadata.builder()
					.operationType(AiOperationType.CHAT.value())
					.provider(AiProvider.OPENAI.value())
					.build());

		var anthropicContext = new ModelObservationContext<String, String>("test request",
				AiOperationMetadata.builder()
					.operationType(AiOperationType.CHAT.value())
					.provider(AiProvider.ANTHROPIC.value())
					.build());

		assertThat(ollamaContext).isNotNull();
		assertThat(openaiContext).isNotNull();
		assertThat(anthropicContext).isNotNull();
	}

	@Test
	void whenComplexObjectTypesAreUsedThenReturn() {
		var observationContext = new ModelObservationContext<Integer, Boolean>(12345,
				AiOperationMetadata.builder()
					.operationType(AiOperationType.CHAT.value())
					.provider(AiProvider.OLLAMA.value())
					.build());
		observationContext.setResponse(true);

		assertThat(observationContext).isNotNull();
	}

	@Test
	void whenGetRequestThenReturn() {
		var testRequest = "test request content";
		var observationContext = new ModelObservationContext<String, String>(testRequest,
				AiOperationMetadata.builder()
					.operationType(AiOperationType.CHAT.value())
					.provider(AiProvider.OLLAMA.value())
					.build());

		assertThat(observationContext.getRequest()).isEqualTo(testRequest);
	}

	@Test
	void whenGetResponseBeforeSettingThenReturnNull() {
		var observationContext = new ModelObservationContext<String, String>("test request",
				AiOperationMetadata.builder()
					.operationType(AiOperationType.CHAT.value())
					.provider(AiProvider.OLLAMA.value())
					.build());

		assertThat(observationContext.getResponse()).isNull();
	}

	@Test
	void whenGetResponseAfterSettingThenReturn() {
		var testResponse = "test response content";
		var observationContext = new ModelObservationContext<String, String>("test request",
				AiOperationMetadata.builder()
					.operationType(AiOperationType.CHAT.value())
					.provider(AiProvider.OLLAMA.value())
					.build());
		observationContext.setResponse(testResponse);

		assertThat(observationContext.getResponse()).isEqualTo(testResponse);
	}

	@Test
	void whenGetOperationMetadataThenReturn() {
		var metadata = AiOperationMetadata.builder()
			.operationType(AiOperationType.EMBEDDING.value())
			.provider(AiProvider.OPENAI.value())
			.build();
		var observationContext = new ModelObservationContext<String, String>("test request", metadata);

		assertThat(observationContext.getOperationMetadata()).isEqualTo(metadata);
	}

	@Test
	void whenSetResponseMultipleTimesThenLastValueWins() {
		var observationContext = new ModelObservationContext<String, String>("test request",
				AiOperationMetadata.builder()
					.operationType(AiOperationType.CHAT.value())
					.provider(AiProvider.OLLAMA.value())
					.build());

		observationContext.setResponse("first response");
		observationContext.setResponse("second response");
		observationContext.setResponse("final response");

		assertThat(observationContext.getResponse()).isEqualTo("final response");
	}

	@Test
	void whenWhitespaceOnlyOperationTypeThenThrow() {
		assertThatThrownBy(() -> new ModelObservationContext<String, String>("test request",
				AiOperationMetadata.builder().operationType("   ").provider(AiProvider.OLLAMA.value()).build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("operationType cannot be null or empty");
	}

	@Test
	void whenWhitespaceOnlyProviderThenThrow() {
		assertThatThrownBy(() -> new ModelObservationContext<String, String>("test request",
				AiOperationMetadata.builder().operationType(AiOperationType.CHAT.value()).provider("   ").build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("provider cannot be null or empty");
	}

	@Test
	void whenEmptyStringRequestThenReturn() {
		var observationContext = new ModelObservationContext<String, String>("",
				AiOperationMetadata.builder()
					.operationType(AiOperationType.CHAT.value())
					.provider(AiProvider.OLLAMA.value())
					.build());

		assertThat(observationContext).isNotNull();
		assertThat(observationContext.getRequest()).isEqualTo("");
	}

	@Test
	void whenEmptyStringResponseThenReturn() {
		var observationContext = new ModelObservationContext<String, String>("test request",
				AiOperationMetadata.builder()
					.operationType(AiOperationType.CHAT.value())
					.provider(AiProvider.OLLAMA.value())
					.build());
		observationContext.setResponse("");

		assertThat(observationContext.getResponse()).isEqualTo("");
	}

}
