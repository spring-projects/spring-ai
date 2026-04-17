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

package org.springframework.ai.mcp.annotation.provider.sampling;

import java.util.List;
import java.util.function.Function;

import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpSampling;
import org.springframework.ai.mcp.annotation.method.sampling.SamplingTestHelper;
import org.springframework.ai.mcp.annotation.method.sampling.SyncSamplingSpecification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SyncMcpSamplingProvider}.
 *
 * @author Christian Tzolov
 */
public class SyncMcpSamplingProviderTests {

	@Test
	void testGetSamplingHandler() {
		// Create a class with only one valid sampling method
		class SingleValidMethod {

			@McpSampling(clients = "test-client")
			public CreateMessageResult handleSamplingRequest(CreateMessageRequest request) {
				return CreateMessageResult.builder()
					.role(io.modelcontextprotocol.spec.McpSchema.Role.ASSISTANT)
					.content(new TextContent("This is a response to the sampling request"))
					.model("test-model")
					.build();
			}

		}

		SingleValidMethod example = new SingleValidMethod();
		SyncMcpSamplingProvider provider = new SyncMcpSamplingProvider(List.of(example));

		List<SyncSamplingSpecification> samplingSpecs = provider.getSamplingSpecifications();

		Function<CreateMessageRequest, CreateMessageResult> handler = samplingSpecs.get(0).samplingHandler();

		assertThat(handler).isNotNull();

		CreateMessageRequest request = SamplingTestHelper.createSampleRequest();
		CreateMessageResult result = handler.apply(request);

		assertThat(result).isNotNull();
		assertThat(result.content()).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content()).text()).isEqualTo("This is a response to the sampling request");
	}

	@Test
	void testNullSamplingObjects() {
		assertThatThrownBy(() -> new SyncMcpSamplingProvider(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("samplingObjects cannot be null");
	}

}
