/*
 * Copyright 2023-2026 the original author or authors.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.auth.StsWebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * {@link AutoConfiguration} for AWS connection.
 *
 * @author Christian Tzolov
 * @author Wei Jiang
 * @author Baojun Jiang
 * @author Matej Nedic
 */
@AutoConfiguration
@EnableConfigurationProperties(BedrockAwsConnectionProperties.class)
public class BedrockAwsCredentialsAndRegionAutoConfiguration {

	private static final String STS_ASSUME_ROLE_CREDENTIALS_PROVIDER = "software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider";

	private static final String STS_WEB_IDENTITY_TOKEN_FILE_CREDENTIALS_PROVIDER = "software.amazon.awssdk.services.sts.auth.StsWebIdentityTokenFileCredentialsProvider";

	@Bean
	@ConditionalOnMissingBean
	public AwsRegionProvider regionProvider(BedrockAwsConnectionProperties properties) {

		if (StringUtils.hasText(properties.getRegion())) {
			return new StaticRegionProvider(properties.getRegion());
		}

		return DefaultAwsRegionProviderChain.builder().build();
	}

	@Bean
	@ConditionalOnMissingBean
	public AwsCredentialsProvider credentialsProvider(BedrockAwsConnectionProperties properties,
			AwsRegionProvider regionProvider) {
		final List<AwsCredentialsProvider> providers = new ArrayList<>();

		if (properties.getProfile() != null && StringUtils.hasText(properties.getProfile().getName())) {
			providers.add(createProfileCredentialsProvider(properties.getProfile()));
		}

		if (properties.isInstanceProfile()) {
			providers.add(InstanceProfileCredentialsProvider.create());
		}

		if (StringUtils.hasText(properties.getAccessKey()) && StringUtils.hasText(properties.getSecretKey())) {
			providers.add(createStaticCredentialsProvider(properties));
		}
		StsProperties stsProperties = properties.getStsProperties();
		if (isWebIdentityConfigured(stsProperties)
				&& ClassUtils.isPresent(STS_WEB_IDENTITY_TOKEN_FILE_CREDENTIALS_PROVIDER, null)) {
			providers.add(createStsWebIdentityCredentialsProvider(stsProperties.getWebIdentity(), regionProvider));
		}

		if (isAssumeRoleConfigured(stsProperties) && ClassUtils.isPresent(STS_ASSUME_ROLE_CREDENTIALS_PROVIDER, null)
				&& !providers.isEmpty()) {
			providers.add(createStsAssumeRoleCredentialsProvider(stsProperties.getAssumeRole(), regionProvider,
					AwsCredentialsProviderChain.builder().credentialsProviders(providers).build()));
		}
		if (providers.isEmpty()) {
			return DefaultCredentialsProvider.builder().build();
		}

		Collections.reverse(providers);
		return AwsCredentialsProviderChain.builder().credentialsProviders(providers).build();
	}

	private AwsCredentialsProvider createProfileCredentialsProvider(ProfileProperties profile) {
		boolean hasConfig = StringUtils.hasText(profile.getConfigurationPath());
		boolean hasCredentials = StringUtils.hasText(profile.getCredentialsPath());

		if (!hasConfig && !hasCredentials) {
			return ProfileCredentialsProvider.builder().profileName(profile.getName()).build();
		}

		ProfileFile.Aggregator aggregator = ProfileFile.aggregator();
		if (hasConfig) {
			aggregator.addFile(ProfileFile.builder()
				.content(Paths.get(profile.getConfigurationPath()))
				.type(ProfileFile.Type.CONFIGURATION)
				.build());
		}
		if (hasCredentials) {
			aggregator.addFile(ProfileFile.builder()
				.content(Paths.get(profile.getCredentialsPath()))
				.type(ProfileFile.Type.CREDENTIALS)
				.build());
		}
		return ProfileCredentialsProvider.builder()
			.profileName(profile.getName())
			.profileFile(aggregator.build())
			.build();
	}

	private AwsCredentialsProvider createStaticCredentialsProvider(BedrockAwsConnectionProperties properties) {
		if (StringUtils.hasText(properties.getSessionToken())) {
			return StaticCredentialsProvider.create(AwsSessionCredentials.create(properties.getAccessKey(),
					properties.getSecretKey(), properties.getSessionToken()));
		}
		else {
			return StaticCredentialsProvider
				.create(AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
		}
	}

	private static boolean isAssumeRoleConfigured(@Nullable StsProperties sts) {
		return sts != null && sts.getAssumeRole() != null && StringUtils.hasText(sts.getAssumeRole().getRoleArn())
				&& StringUtils.hasText(sts.getAssumeRole().getRoleSessionName());
	}

	private static boolean isWebIdentityConfigured(@Nullable StsProperties sts) {
		return sts != null && sts.getWebIdentity() != null
				&& StringUtils.hasText(sts.getWebIdentity().getWebIdentityTokenFile())
				&& StringUtils.hasText(sts.getWebIdentity().getRoleArn());
	}

	private AwsCredentialsProvider createStsAssumeRoleCredentialsProvider(StsProperties.AssumeRole assumeRole,
			AwsRegionProvider regionProvider, AwsCredentialsProvider sourceCredentials) {
		return StsAssumeRoleCredentialsProvider.builder()
			.refreshRequest(AssumeRoleRequest.builder()
				.roleArn(assumeRole.getRoleArn())
				.externalId(assumeRole.getExternalId())
				.roleSessionName(assumeRole.getRoleSessionName())
				.durationSeconds(assumeRole.getDurationSeconds())
				.build())
			.stsClient(StsClient.builder()
				.credentialsProvider(sourceCredentials)
				.region(regionProvider.getRegion())
				.build())
			.build();
	}

	private AwsCredentialsProvider createStsWebIdentityCredentialsProvider(StsProperties.WebIdentity webIdentity,
			AwsRegionProvider regionProvider) {
		StsWebIdentityTokenFileCredentialsProvider.Builder builder = StsWebIdentityTokenFileCredentialsProvider
			.builder()
			.stsClient(StsClient.builder()
				.credentialsProvider(AnonymousCredentialsProvider.create())
				.region(regionProvider.getRegion())
				.build())
			.roleArn(webIdentity.getRoleArn())
			.webIdentityTokenFile(Paths.get(webIdentity.getWebIdentityTokenFile()))
			.asyncCredentialUpdateEnabled(webIdentity.isAsyncCredentialUpdateEnabled());
		if (StringUtils.hasText(webIdentity.getRoleSessionName())) {
			builder.roleSessionName(webIdentity.getRoleSessionName());
		}
		return builder.build();
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
