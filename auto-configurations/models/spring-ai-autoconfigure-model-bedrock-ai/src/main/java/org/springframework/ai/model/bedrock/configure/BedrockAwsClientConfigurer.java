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

package org.springframework.ai.model.bedrock.configure;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;

import org.springframework.ai.model.bedrock.autoconfigure.AsyncClientProperties;
import org.springframework.ai.model.bedrock.autoconfigure.BedrockAwsConnectionProperties;
import org.springframework.ai.model.bedrock.autoconfigure.SyncClientProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.util.Assert;

/**
 * Configures AWS SDK client builders with connection properties.
 *
 * @author Matej Nedic
 */
public final class BedrockAwsClientConfigurer {

	private BedrockAwsClientConfigurer() {
	}

	public static <T extends AwsClientBuilder<T, ?>> T configure(T builder,
			BedrockAwsConnectionProperties bedrockAwsConnectionProperties, AwsRegionProvider awsRegionProvider,
			AwsCredentialsProvider awsCredentialsProvider) {
		Assert.notNull(builder, "builder is required");
		Assert.notNull(bedrockAwsConnectionProperties, "bedrockAwsConnectionProperties are required");
		Assert.notNull(awsRegionProvider, "awsRegionProvider are required");
		Assert.notNull(awsCredentialsProvider, "awsCredentialsProvider are required");

		PropertyMapper propertyMapper = PropertyMapper.get();
		builder.credentialsProvider(awsCredentialsProvider);
		builder.region(awsRegionProvider.getRegion());

		propertyMapper.from(bedrockAwsConnectionProperties::getEndpoint).to(builder::endpointOverride);
		propertyMapper.from(bedrockAwsConnectionProperties::getDefaultsMode).to(builder::defaultsMode);
		propertyMapper.from(bedrockAwsConnectionProperties::getFipsEnabled).to(builder::fipsEnabled);
		propertyMapper.from(bedrockAwsConnectionProperties::getDualstackEnabled).to(builder::dualstackEnabled);
		propertyMapper.from(bedrockAwsConnectionProperties::getTimeout)
			.to(timeout -> builder.overrideConfiguration(c -> c.apiCallTimeout(timeout)));

		return builder;
	}

	public static void configureSyncHttpClient(BedrockRuntimeClientBuilder builder,
			BedrockAwsConnectionProperties bedrockAwsConnectionProperties) {
		SyncClientProperties properties = bedrockAwsConnectionProperties.getSyncClient();
		if (properties != null) {
			var httpClientBuilder = ApacheHttpClient.builder();
			PropertyMapper propertyMapper = PropertyMapper.get();
			propertyMapper.from(properties::getConnectionAcquisitionTimeout)
				.to(httpClientBuilder::connectionAcquisitionTimeout);
			propertyMapper.from(properties::getConnectionTimeout).to(httpClientBuilder::connectionTimeout);
			propertyMapper.from(properties::getSocketTimeout).to(httpClientBuilder::socketTimeout);
			builder.httpClientBuilder(httpClientBuilder);
		}
	}

	public static void configureAsyncHttpClient(BedrockRuntimeAsyncClientBuilder builder,
			BedrockAwsConnectionProperties bedrockAwsConnectionProperties) {
		AsyncClientProperties properties = bedrockAwsConnectionProperties.getAsyncClient();
		if (properties != null) {
			var httpClientBuilder = NettyNioAsyncHttpClient.builder();
			PropertyMapper propertyMapper = PropertyMapper.get();
			propertyMapper.from(properties.isTcpKeepAlive()).to(httpClientBuilder::tcpKeepAlive);
			propertyMapper.from(properties.getReadTimeout()).to(httpClientBuilder::readTimeout);
			propertyMapper.from(properties.getConnectionTimeout()).to(httpClientBuilder::connectionTimeout);
			propertyMapper.from(properties.getConnectionAcquisitionTimeout())
				.to(httpClientBuilder::connectionAcquisitionTimeout);
			propertyMapper.from(properties.getMaxConcurrency()).to(httpClientBuilder::maxConcurrency);
			builder.httpClientBuilder(httpClientBuilder);
		}
	}

}
