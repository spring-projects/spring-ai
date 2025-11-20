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

package org.springframework.ai.model.replicate.autoconfigure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.replicate.ReplicateChatModel;
import org.springframework.ai.replicate.ReplicateMediaModel;
import org.springframework.ai.replicate.ReplicateStringModel;
import org.springframework.ai.replicate.ReplicateStructuredModel;
import org.springframework.ai.replicate.api.ReplicateApi;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReplicateChatAutoConfiguration}.
 *
 * @author Rene Maierhofer
 */
@EnabledIfEnvironmentVariable(named = "REPLICATE_API_TOKEN", matches = ".+")
class ReplicateChatAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.replicate.api-token=" + System.getenv("REPLICATE_API_TOKEN"))
		.withConfiguration(
				AutoConfigurations.of(RestClientAutoConfiguration.class, ReplicateChatAutoConfiguration.class));

	@Test
	void testReplicateApiBean() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(ReplicateApi.class);
			ReplicateApi api = context.getBean(ReplicateApi.class);
			assertThat(api).isNotNull();
		});
	}

	@Test
	void testReplicateChatModelBean() {
		this.contextRunner.withPropertyValues("spring.ai.replicate.chat.options.model=meta/meta-llama-3-8b-instruct")
			.run(context -> {
				assertThat(context).hasSingleBean(ReplicateChatModel.class);
				ReplicateChatModel chatModel = context.getBean(ReplicateChatModel.class);
				assertThat(chatModel).isNotNull();

				String response = chatModel.call("Say hello");
				assertThat(response).isNotEmpty();
			});
	}

	@Test
	void testReplicateMediaModelBean() {
		this.contextRunner.withPropertyValues("spring.ai.replicate.media.options.model=black-forest-labs/flux-schnell")
			.run(context -> {
				assertThat(context).hasSingleBean(ReplicateMediaModel.class);
				ReplicateMediaModel mediaModel = context.getBean(ReplicateMediaModel.class);
				assertThat(mediaModel).isNotNull();
			});
	}

	@Test
	void testReplicateStringModelBean() {
		this.contextRunner
			.withPropertyValues("spring.ai.replicate.string.options.model=falcons-ai/nsfw_image_detection")
			.run(context -> {
				assertThat(context).hasSingleBean(ReplicateStringModel.class);
				ReplicateStringModel stringModel = context.getBean(ReplicateStringModel.class);
				assertThat(stringModel).isNotNull();
			});
	}

	@Test
	void testReplicateStructuredModelBean() {
		this.contextRunner.withPropertyValues("spring.ai.replicate.structured.options.model=openai/clip")
			.run(context -> {
				assertThat(context).hasSingleBean(ReplicateStructuredModel.class);
				ReplicateStructuredModel structuredModel = context.getBean(ReplicateStructuredModel.class);
				assertThat(structuredModel).isNotNull();
			});
	}

	@Test
	void testAllModelBeansCreated() {
		this.contextRunner
			.withPropertyValues("spring.ai.replicate.chat.options.model=meta/meta-llama-3-8b-instruct",
					"spring.ai.replicate.media.options.model=black-forest-labs/flux-schnell",
					"spring.ai.replicate.string.options.model=falcons-ai/nsfw_image_detection",
					"spring.ai.replicate.structured.options.model=openai/clip")
			.run(context -> {
				assertThat(context).hasSingleBean(ReplicateApi.class);
				assertThat(context).hasSingleBean(ReplicateChatModel.class);
				assertThat(context).hasSingleBean(ReplicateMediaModel.class);
				assertThat(context).hasSingleBean(ReplicateStringModel.class);
				assertThat(context).hasSingleBean(ReplicateStructuredModel.class);
			});
	}

	@Test
	void testChatInputParameters() {
		this.contextRunner
			.withPropertyValues("spring.ai.replicate.chat.options.model=meta/meta-llama-3-8b-instruct",
					"spring.ai.replicate.chat.options.input.temperature=0.7",
					"spring.ai.replicate.chat.options.input.max_tokens=50")
			.run(context -> {
				ReplicateChatModel chatModel = context.getBean(ReplicateChatModel.class);
				assertThat(chatModel).isNotNull();
				Prompt prompt = new Prompt("Write a very long poem.");
				ChatResponse response = chatModel.call(prompt);

				assertThat(response).isNotNull();
				assertThat(response.getResults()).isNotEmpty();
				assertThat(response.getResult().getOutput().getText()).isNotEmpty();
				ChatResponseMetadata metadata = response.getMetadata();
				Usage usage = metadata.getUsage();
				assertThat(usage.getCompletionTokens()).isLessThanOrEqualTo(50);
			});
	}

}
