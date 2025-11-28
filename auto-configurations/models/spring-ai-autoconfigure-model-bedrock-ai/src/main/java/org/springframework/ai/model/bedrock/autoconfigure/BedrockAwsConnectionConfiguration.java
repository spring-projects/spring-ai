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

import java.nio.file.Paths;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * {@link Configuration} for AWS connection.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @author Baojun Jiang
 */
@Configuration
@EnableConfigurationProperties(BedrockAwsConnectionProperties.class)
public class BedrockAwsConnectionConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public AwsCredentialsProvider credentialsProvider(BedrockAwsConnectionProperties properties) {
		if (StringUtils.hasText(properties.getAccessKey()) && StringUtils.hasText(properties.getSecretKey())) {
			// Security key
			if (StringUtils.hasText(properties.getSessionToken())) {
				return StaticCredentialsProvider.create(AwsSessionCredentials.create(properties.getAccessKey(),
						properties.getSecretKey(), properties.getSessionToken()));
			}
			return StaticCredentialsProvider
				.create(AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
		}
		else if (properties.getProfile() != null && StringUtils.hasText(properties.getProfile().getName())) {
			// Profile
			ProfileProperties profile = properties.getProfile();
			String configurationPath = profile.getConfigurationPath();
			String credentialsPath = profile.getCredentialsPath();
			boolean hasCredentials = StringUtils.hasText(credentialsPath);
			boolean hasConfig = StringUtils.hasText(configurationPath);
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
			return providerBuilder.profileName(profile.getName()).build();
		}
		else {
			// Default: IAM Role, System Environment, etc.
			return DefaultCredentialsProvider.builder().build();
		}
	}

	@Bean
	@ConditionalOnMissingBean
	public AwsRegionProvider regionProvider(BedrockAwsConnectionProperties properties) {

		if (StringUtils.hasText(properties.getRegion())) {
			return new StaticRegionProvider(properties.getRegion());
		}

		return DefaultAwsRegionProviderChain.builder().build();
	}

	static class StaticRegionProvider implements AwsRegionProvider {

		private final Region region;

		StaticRegionProvider(String region) {
			try {
				this.region = Region.of(region);
			}
			catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("The region '" + region + "' is not a valid region!", e);
			}
		}

		@Override
		public Region getRegion() {
			return this.region;
		}

	}

}
