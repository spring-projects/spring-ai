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

package org.springframework.ai.vectorstore.observation;

import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.document.Document;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link VectorStoreQueryResponseObservationHandler}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 */
@ExtendWith(OutputCaptureExtension.class)
class VectorStoreQueryResponseObservationHandlerTests {

	private final VectorStoreQueryResponseObservationHandler observationHandler = new VectorStoreQueryResponseObservationHandler();

	@Test
	void whenNotSupportedObservationContextThenReturnFalse() {
		var context = new Observation.Context();
		assertThat(this.observationHandler.supportsContext(context)).isFalse();
	}

	@Test
	void whenSupportedObservationContextThenReturnTrue() {
		var context = VectorStoreObservationContext.builder("db", VectorStoreObservationContext.Operation.ADD).build();
		assertThat(this.observationHandler.supportsContext(context)).isTrue();
	}

	@Test
	void whenEmptyQueryResponseThenOutputNothing(CapturedOutput output) {
		var context = VectorStoreObservationContext.builder("db", VectorStoreObservationContext.Operation.ADD).build();
		observationHandler.onStop(context);
		assertThat(output).contains("""
				Vector Store Query Response:
				[]
				""");
	}

	@Test
	void whenNonEmptyQueryResponseThenOutputIt(CapturedOutput output) {
		var context = VectorStoreObservationContext.builder("db", VectorStoreObservationContext.Operation.ADD).build();
		context.setQueryResponse(List.of(new Document("doc1"), new Document("doc2")));
		observationHandler.onStop(context);
		assertThat(output).contains("""
				Vector Store Query Response:
				["doc1", "doc2"]
				""");
	}

}
