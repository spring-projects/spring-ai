/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.oci.cohere;

import com.oracle.bmc.generativeaiinference.model.CohereTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OCICohereChatOptions}.
 *
 * @author Alexandros Pappas
 */
class OCICohereChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		OCICohereChatOptions options = OCICohereChatOptions.builder()
			.model("test-model")
			.maxTokens(10)
			.compartment("test-compartment")
			.servingMode("test-servingMode")
			.preambleOverride("test-preambleOverride")
			.temperature(0.6)
			.topP(0.6)
			.topK(50)
			.stop(List.of("test"))
			.frequencyPenalty(0.5)
			.presencePenalty(0.5)
			.documents(List.of("doc1", "doc2"))
			.build();

		assertThat(options)
			.extracting("model", "maxTokens", "compartment", "servingMode", "preambleOverride", "temperature", "topP",
					"topK", "stop", "frequencyPenalty", "presencePenalty", "documents")
			.containsExactly("test-model", 10, "test-compartment", "test-servingMode", "test-preambleOverride", 0.6,
					0.6, 50, List.of("test"), 0.5, 0.5, List.of("doc1", "doc2"));
	}

	@Test
	void testCopy() {
		OCICohereChatOptions original = OCICohereChatOptions.builder()
			.model("test-model")
			.maxTokens(10)
			.compartment("test-compartment")
			.servingMode("test-servingMode")
			.preambleOverride("test-preambleOverride")
			.temperature(0.6)
			.topP(0.6)
			.topK(50)
			.stop(List.of("test"))
			.frequencyPenalty(0.5)
			.presencePenalty(0.5)
			.documents(List.of("doc1", "doc2"))
			.tools(List.of(new CohereTool("test-tool", "test-context", Map.of())))
			.build();

		OCICohereChatOptions copied = (OCICohereChatOptions) original.copy();

		assertThat(copied).isNotSameAs(original).isEqualTo(original);
		// Ensure deep copy
		assertThat(copied.getStop()).isNotSameAs(original.getStop());
		assertThat(copied.getDocuments()).isNotSameAs(original.getDocuments());
		assertThat(copied.getTools()).isNotSameAs(original.getTools());
	}

	@Test
	void testSetters() {
		OCICohereChatOptions options = new OCICohereChatOptions();
		options.setModel("test-model");
		options.setMaxTokens(10);
		options.setCompartment("test-compartment");
		options.setServingMode("test-servingMode");
		options.setPreambleOverride("test-preambleOverride");
		options.setTemperature(0.6);
		options.setTopP(0.6);
		options.setTopK(50);
		options.setStop(List.of("test"));
		options.setFrequencyPenalty(0.5);
		options.setPresencePenalty(0.5);
		options.setDocuments(List.of("doc1", "doc2"));

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getMaxTokens()).isEqualTo(10);
		assertThat(options.getCompartment()).isEqualTo("test-compartment");
		assertThat(options.getServingMode()).isEqualTo("test-servingMode");
		assertThat(options.getPreambleOverride()).isEqualTo("test-preambleOverride");
		assertThat(options.getTemperature()).isEqualTo(0.6);
		assertThat(options.getTopP()).isEqualTo(0.6);
		assertThat(options.getTopK()).isEqualTo(50);
		assertThat(options.getStop()).isEqualTo(List.of("test"));
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(options.getPresencePenalty()).isEqualTo(0.5);
		assertThat(options.getDocuments()).isEqualTo(List.of("doc1", "doc2"));
	}

	@Test
	void testDefaultValues() {
		OCICohereChatOptions options = new OCICohereChatOptions();
		assertThat(options.getModel()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getCompartment()).isNull();
		assertThat(options.getServingMode()).isNull();
		assertThat(options.getPreambleOverride()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getTopK()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
		assertThat(options.getDocuments()).isNull();
		assertThat(options.getTools()).isNull();
	}

}
