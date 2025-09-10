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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.oracle.bmc.generativeaiinference.model.CohereTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OCICohereChatOptions}.
 *
 * @author Alexandros Pappas
 */
class OCICohereChatOptionsTests {

	private OCICohereChatOptions options;

	@BeforeEach
	void setUp() {
		this.options = new OCICohereChatOptions();
	}

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
	void testBuilderWithMinimalFields() {
		OCICohereChatOptions options = OCICohereChatOptions.builder().model("minimal-model").build();

		assertThat(options.getModel()).isEqualTo("minimal-model");
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getTemperature()).isNull();
	}

	@Test
	void testBuilderWithNullValues() {
		OCICohereChatOptions options = OCICohereChatOptions.builder()
			.model(null)
			.maxTokens(null)
			.temperature(null)
			.stop(null)
			.documents(null)
			.tools(null)
			.build();

		assertThat(options.getModel()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getDocuments()).isNull();
		assertThat(options.getTools()).isNull();
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
	void testCopyWithNullValues() {
		OCICohereChatOptions original = new OCICohereChatOptions();
		OCICohereChatOptions copied = (OCICohereChatOptions) original.copy();

		assertThat(copied).isNotSameAs(original).isEqualTo(original);
		assertThat(copied.getModel()).isNull();
		assertThat(copied.getStop()).isNull();
		assertThat(copied.getDocuments()).isNull();
		assertThat(copied.getTools()).isNull();
	}

	@Test
	void testSetters() {
		this.options.setModel("test-model");
		this.options.setMaxTokens(10);
		this.options.setCompartment("test-compartment");
		this.options.setServingMode("test-servingMode");
		this.options.setPreambleOverride("test-preambleOverride");
		this.options.setTemperature(0.6);
		this.options.setTopP(0.6);
		this.options.setTopK(50);
		this.options.setStop(List.of("test"));
		this.options.setFrequencyPenalty(0.5);
		this.options.setPresencePenalty(0.5);
		this.options.setDocuments(List.of("doc1", "doc2"));

		assertThat(this.options.getModel()).isEqualTo("test-model");
		assertThat(this.options.getMaxTokens()).isEqualTo(10);
		assertThat(this.options.getCompartment()).isEqualTo("test-compartment");
		assertThat(this.options.getServingMode()).isEqualTo("test-servingMode");
		assertThat(this.options.getPreambleOverride()).isEqualTo("test-preambleOverride");
		assertThat(this.options.getTemperature()).isEqualTo(0.6);
		assertThat(this.options.getTopP()).isEqualTo(0.6);
		assertThat(this.options.getTopK()).isEqualTo(50);
		assertThat(this.options.getStop()).isEqualTo(List.of("test"));
		assertThat(this.options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(this.options.getPresencePenalty()).isEqualTo(0.5);
		assertThat(this.options.getDocuments()).isEqualTo(List.of("doc1", "doc2"));
	}

	@Test
	void testDefaultValues() {
		assertThat(this.options.getModel()).isNull();
		assertThat(this.options.getMaxTokens()).isNull();
		assertThat(this.options.getCompartment()).isNull();
		assertThat(this.options.getServingMode()).isNull();
		assertThat(this.options.getPreambleOverride()).isNull();
		assertThat(this.options.getTemperature()).isNull();
		assertThat(this.options.getTopP()).isNull();
		assertThat(this.options.getTopK()).isNull();
		assertThat(this.options.getStop()).isNull();
		assertThat(this.options.getFrequencyPenalty()).isNull();
		assertThat(this.options.getPresencePenalty()).isNull();
		assertThat(this.options.getDocuments()).isNull();
		assertThat(this.options.getTools()).isNull();
	}

	@Test
	void testBoundaryValues() {
		this.options.setMaxTokens(0);
		this.options.setTemperature(0.0);
		this.options.setTopP(0.0);
		this.options.setTopK(1);
		this.options.setFrequencyPenalty(0.0);
		this.options.setPresencePenalty(0.0);

		assertThat(this.options.getMaxTokens()).isEqualTo(0);
		assertThat(this.options.getTemperature()).isEqualTo(0.0);
		assertThat(this.options.getTopP()).isEqualTo(0.0);
		assertThat(this.options.getTopK()).isEqualTo(1);
		assertThat(this.options.getFrequencyPenalty()).isEqualTo(0.0);
		assertThat(this.options.getPresencePenalty()).isEqualTo(0.0);
	}

	@Test
	void testMaximumBoundaryValues() {
		this.options.setMaxTokens(Integer.MAX_VALUE);
		this.options.setTemperature(1.0);
		this.options.setTopP(1.0);
		this.options.setTopK(Integer.MAX_VALUE);
		this.options.setFrequencyPenalty(1.0);
		this.options.setPresencePenalty(1.0);

		assertThat(this.options.getMaxTokens()).isEqualTo(Integer.MAX_VALUE);
		assertThat(this.options.getTemperature()).isEqualTo(1.0);
		assertThat(this.options.getTopP()).isEqualTo(1.0);
		assertThat(this.options.getTopK()).isEqualTo(Integer.MAX_VALUE);
		assertThat(this.options.getFrequencyPenalty()).isEqualTo(1.0);
		assertThat(this.options.getPresencePenalty()).isEqualTo(1.0);
	}

	@Test
	void testEmptyCollections() {
		this.options.setStop(Collections.emptyList());
		this.options.setDocuments(Collections.emptyList());
		this.options.setTools(Collections.emptyList());

		assertThat(this.options.getStop()).isEmpty();
		assertThat(this.options.getDocuments()).isEmpty();
		assertThat(this.options.getTools()).isEmpty();
	}

	@Test
	void testMultipleSetterCalls() {
		this.options.setModel("first-model");
		this.options.setModel("second-model");
		this.options.setMaxTokens(50);
		this.options.setMaxTokens(100);

		assertThat(this.options.getModel()).isEqualTo("second-model");
		assertThat(this.options.getMaxTokens()).isEqualTo(100);
	}

	@Test
	void testNullSetters() {
		// Set values first
		this.options.setModel("test-model");
		this.options.setMaxTokens(100);
		this.options.setStop(List.of("test"));

		// Then set to null
		this.options.setModel(null);
		this.options.setMaxTokens(null);
		this.options.setStop(null);

		assertThat(this.options.getModel()).isNull();
		assertThat(this.options.getMaxTokens()).isNull();
		assertThat(this.options.getStop()).isNull();
	}

}
