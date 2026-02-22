/*
 * Copyright 2025-2026 the original author or authors.
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

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BedrockAwsCredentialsAndRegionAutoConfiguration}.
 *
 * @author Matej Nedic
 */
@RequiresAwsCredentials
class BedrockAwsCredentialsAndRegionAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(BedrockAwsCredentialsAndRegionAutoConfiguration.class));

	@Test
	void defaultCredentialsProviderWhenNoPropertiesSet() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(AwsCredentialsProvider.class);
			assertThat(context.getBean(AwsCredentialsProvider.class)).isInstanceOf(DefaultCredentialsProvider.class);
		});
	}

	@Test
	void staticCredentialsProviderWhenAccessKeyAndSecretKeySet() {
		this.contextRunner
			.withPropertyValues("spring.ai.bedrock.aws.access-key=testKey",
					"spring.ai.bedrock.aws.secret-key=testSecret")
			.run(context -> {
				assertThat(context).hasSingleBean(AwsCredentialsProvider.class);
				AwsCredentialsProvider provider = context.getBean(AwsCredentialsProvider.class);
				AwsCredentials credentials = provider.resolveCredentials();
				assertThat(credentials).isInstanceOf(AwsBasicCredentials.class);
				assertThat(credentials.accessKeyId()).isEqualTo("testKey");
				assertThat(credentials.secretAccessKey()).isEqualTo("testSecret");
			});
	}

	@Test
	void sessionCredentialsProviderWhenSessionTokenSet() {
		this.contextRunner
			.withPropertyValues("spring.ai.bedrock.aws.access-key=testKey",
					"spring.ai.bedrock.aws.secret-key=testSecret", "spring.ai.bedrock.aws.session-token=testToken")
			.run(context -> {
				AwsCredentials credentials = context.getBean(AwsCredentialsProvider.class).resolveCredentials();
				assertThat(credentials).isInstanceOf(AwsSessionCredentials.class);
				assertThat(((AwsSessionCredentials) credentials).sessionToken()).isEqualTo("testToken");
			});
	}

	@Test
	void staticRegionProviderWhenRegionSet() {
		this.contextRunner.withPropertyValues("spring.ai.bedrock.aws.region=eu-west-1").run(context -> {
			assertThat(context).hasSingleBean(AwsRegionProvider.class);
			assertThat(context.getBean(AwsRegionProvider.class).getRegion()).isEqualTo(Region.EU_WEST_1);
		});
	}

	@Test
	void defaultRegionProviderWhenNoRegionSet() {
		this.contextRunner.withPropertyValues("spring.ai.bedrock.aws.region=").run(context -> {
			assertThat(context).hasSingleBean(AwsRegionProvider.class);
			assertThat(context.getBean(AwsRegionProvider.class)).isInstanceOf(DefaultAwsRegionProviderChain.class);
		});
	}

	@Test
	void customCredentialsProviderTakesPrecedence() {
		this.contextRunner.withUserConfiguration(CustomCredentialsConfig.class).run(context -> {
			assertThat(context).hasSingleBean(AwsCredentialsProvider.class);
			assertThat(context.getBean(AwsCredentialsProvider.class)).isInstanceOf(AnonymousCredentialsProvider.class);
		});
	}

	@Test
	void customRegionProviderTakesPrecedence() {
		this.contextRunner.withUserConfiguration(CustomRegionConfig.class).run(context -> {
			assertThat(context).hasSingleBean(AwsRegionProvider.class);
			assertThat(context.getBean(AwsRegionProvider.class).getRegion()).isEqualTo(Region.EU_CENTRAL_1);
		});
	}

	@Configuration
	static class CustomCredentialsConfig {

		@Bean
		AwsCredentialsProvider credentialsProvider() {
			return AnonymousCredentialsProvider.create();
		}

	}

	@Configuration
	static class CustomRegionConfig {

		@Bean
		AwsRegionProvider regionProvider() {
			return () -> Region.EU_CENTRAL_1;
		}

	}

}
