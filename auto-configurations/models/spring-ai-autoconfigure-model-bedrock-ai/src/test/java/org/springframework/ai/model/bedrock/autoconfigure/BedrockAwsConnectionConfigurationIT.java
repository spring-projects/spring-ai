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

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Wei Jiang
 * @author Mark Pollack
 * @since 1.0.0
 */
@RequiresAwsCredentials
public class BedrockAwsConnectionConfigurationIT {

	@Test
	public void autoConfigureAWSCredentialAndRegionProvider() {
		BedrockTestUtils.getContextRunner()
			.withConfiguration(AutoConfigurations.of(TestAutoConfiguration.class))
			.run(context -> {
				var awsCredentialsProvider = context.getBean(AwsCredentialsProvider.class);
				var awsRegionProvider = context.getBean(AwsRegionProvider.class);

				assertThat(awsCredentialsProvider).isNotNull();
				assertThat(awsRegionProvider).isNotNull();

				var credentials = awsCredentialsProvider.resolveCredentials();
				assertThat(credentials).isNotNull();
				assertThat(credentials.accessKeyId()).isEqualTo(System.getenv("AWS_ACCESS_KEY_ID"));
				assertThat(credentials.secretAccessKey()).isEqualTo(System.getenv("AWS_SECRET_ACCESS_KEY"));

				assertThat(awsRegionProvider.getRegion()).isEqualTo(Region.US_EAST_1);
			});
	}

	@Test
	public void autoConfigureWithCustomAWSCredentialAndRegionProvider() {
		BedrockTestUtils.getContextRunner()
			.withConfiguration(AutoConfigurations.of(TestAutoConfiguration.class,
					CustomAwsCredentialsProviderAutoConfiguration.class,
					CustomAwsRegionProviderAutoConfiguration.class))
			.run(context -> {
				var awsCredentialsProvider = context.getBean(AwsCredentialsProvider.class);
				var awsRegionProvider = context.getBean(AwsRegionProvider.class);

				assertThat(awsCredentialsProvider).isNotNull();
				assertThat(awsRegionProvider).isNotNull();

				var credentials = awsCredentialsProvider.resolveCredentials();
				assertThat(credentials).isNotNull();
				assertThat(credentials.accessKeyId()).isEqualTo("CUSTOM_ACCESS_KEY");
				assertThat(credentials.secretAccessKey()).isEqualTo("CUSTOM_SECRET_ACCESS_KEY");

				assertThat(awsRegionProvider.getRegion()).isEqualTo(Region.AWS_GLOBAL);
			});
	}

	@Test
	public void autoConfigureWithCustomAWSProfileCredentialAndRegionProvider() {
		BedrockTestUtils.getContextRunner()
			.withConfiguration(AutoConfigurations.of(TestAutoConfiguration.class,
					CustomAwsProfileCredentialsProviderAutoConfiguration.class,
					CustomAwsRegionProviderAutoConfiguration.class))
			.run(context -> {
				var awsCredentialsProvider = context.getBean(AwsCredentialsProvider.class);
				var awsRegionProvider = context.getBean(AwsRegionProvider.class);

				assertThat(awsCredentialsProvider).isNotNull();
				assertThat(awsRegionProvider).isNotNull();

				assertThat(awsCredentialsProvider).isInstanceOf(ProfileCredentialsProvider.class);
				// aws sdk2.x does not provide method to get profileName, use reflection
				// to get
				Field field = ProfileCredentialsProvider.class.getDeclaredField("profileName");
				field.setAccessible(true);
				assertThat(field.get(awsCredentialsProvider)).isEqualTo("CUSTOM_PROFILE_NAME");

				assertThat(awsRegionProvider.getRegion()).isEqualTo(Region.AWS_GLOBAL);
			});
	}

	@EnableConfigurationProperties(BedrockAwsConnectionProperties.class)
	@Import(BedrockAwsConnectionConfiguration.class)
	static class TestAutoConfiguration {

	}

	@AutoConfiguration
	static class CustomAwsProfileCredentialsProviderAutoConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public AwsCredentialsProvider credentialsProvider() {
			String credentialsPath = "CUSTOM_CREDENTIALS_PATH";
			String configurationPath = "CUSTOM_CONFIGURATION_PATH";
			boolean hasCredentials = Files.exists(Paths.get(credentialsPath));
			boolean hasConfig = Files.exists(Paths.get(configurationPath));
			ProfileCredentialsProvider.Builder providerBuilder = ProfileCredentialsProvider.builder();
			if (hasCredentials || hasConfig) {
				ProfileFile.Aggregator aggregator = ProfileFile.aggregator();
				if (hasCredentials) {
					ProfileFile profileFile = ProfileFile.builder()
						.content(Paths.get(credentialsPath))
						.type(ProfileFile.Type.CREDENTIALS)
						.build();
					aggregator.addFile(profileFile);
				}
				if (hasConfig) {
					ProfileFile configFile = ProfileFile.builder()
						.content(Paths.get(configurationPath))
						.type(ProfileFile.Type.CONFIGURATION)
						.build();
					aggregator.addFile(configFile);
				}
				ProfileFile aggregatedProfileFile = aggregator.build();
				providerBuilder.profileFile(aggregatedProfileFile);
			}
			return providerBuilder.profileName("CUSTOM_PROFILE_NAME").build();
		}

	}

	@AutoConfiguration
	static class CustomAwsCredentialsProviderAutoConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public AwsCredentialsProvider credentialsProvider() {
			return new AwsCredentialsProvider() {

				@Override
				public AwsCredentials resolveCredentials() {
					return new AwsCredentials() {

						@Override
						public String accessKeyId() {
							return "CUSTOM_ACCESS_KEY";
						}

						@Override
						public String secretAccessKey() {
							return "CUSTOM_SECRET_ACCESS_KEY";
						}

					};
				}

			};
		}

	}

	@AutoConfiguration
	static class CustomAwsRegionProviderAutoConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public AwsRegionProvider regionProvider() {
			return new AwsRegionProvider() {

				@Override
				public Region getRegion() {
					return Region.AWS_GLOBAL;
				}

			};
		}

	}

}
