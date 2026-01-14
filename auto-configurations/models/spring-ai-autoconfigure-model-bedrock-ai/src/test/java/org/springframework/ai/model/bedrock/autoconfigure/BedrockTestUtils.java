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

package org.springframework.ai.model.bedrock.autoconfigure;

import software.amazon.awssdk.regions.Region;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class BedrockTestUtils {

	private BedrockTestUtils() {
	} // Prevent instantiation

	public static ApplicationContextRunner getContextRunner() {
		return new ApplicationContextRunner()
			.withPropertyValues("spring.ai.bedrock.aws.access-key=" + System.getenv("AWS_ACCESS_KEY_ID"),
					"spring.ai.bedrock.aws.secret-key=" + System.getenv("AWS_SECRET_ACCESS_KEY"),
					"spring.ai.bedrock.aws.session-token=" + System.getenv("AWS_SESSION_TOKEN"),
					"spring.ai.bedrock.aws.region=" + Region.US_EAST_1.id())
			.withUserConfiguration(Config.class);
	}

	public static ApplicationContextRunner getContextRunnerWithUserConfiguration() {
		return new ApplicationContextRunner().withUserConfiguration(Config.class);
	}

	@Configuration
	static class Config {

		@Bean
		public JsonMapper jsonMapper() {
			return new JsonMapper();
		}

	}

}
