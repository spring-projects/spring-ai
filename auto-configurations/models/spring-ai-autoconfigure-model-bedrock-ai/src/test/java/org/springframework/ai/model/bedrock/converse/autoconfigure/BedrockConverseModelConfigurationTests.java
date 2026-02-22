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

package org.springframework.ai.model.bedrock.converse.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.model.bedrock.autoconfigure.BedrockAwsCredentialsAndRegionAutoConfiguration;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link BedrockConverseProxyChatAutoConfiguration}'s conditional enabling
 * of models.
 *
 * @author Ilayaperumal Gopinathan
 * @author Pawel Potaczala
 * @author Issam El-atif
 */
public class BedrockConverseModelConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(SpringAiTestAutoConfigurations.of(BedrockConverseProxyChatAutoConfiguration.class,
				BedrockAwsCredentialsAndRegionAutoConfiguration.class));

	@Test
	void chatModelActivation() {
		this.contextRunner.run(context -> assertThat(context.getBeansOfType(BedrockProxyChatModel.class)).isNotEmpty());

		this.contextRunner.withPropertyValues("spring.ai.model.chat=none").run(context -> {
			assertThat(context.getBeansOfType(BedrockConverseProxyChatProperties.class)).isEmpty();
			assertThat(context.getBeansOfType(BedrockProxyChatModel.class)).isEmpty();
		});

		this.contextRunner.withPropertyValues("spring.ai.model.chat=bedrock-converse").run(context -> {
			assertThat(context.getBeansOfType(BedrockConverseProxyChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(BedrockProxyChatModel.class)).isNotEmpty();
		});
	}

}
