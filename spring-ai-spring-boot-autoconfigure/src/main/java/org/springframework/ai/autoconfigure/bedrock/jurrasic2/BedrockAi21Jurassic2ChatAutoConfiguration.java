/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.autoconfigure.bedrock.jurrasic2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionConfiguration;
import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionProperties;
import org.springframework.ai.bedrock.jurassic2.BedrockAi21Jurassic2ChatClient;
import org.springframework.ai.bedrock.jurassic2.api.Ai21Jurassic2ChatBedrockApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

/**
 * {@link AutoConfiguration Auto-configuration} for Bedrock Jurassic2 Chat Client.
 *
 * @author Ahmed Yousri
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(Ai21Jurassic2ChatBedrockApi.class)
@EnableConfigurationProperties({ BedrockAi21Jurassic2ChatProperties.class, BedrockAwsConnectionProperties.class })
@ConditionalOnProperty(prefix = BedrockAi21Jurassic2ChatProperties.CONFIG_PREFIX, name = "enabled",
		havingValue = "true")
@Import(BedrockAwsConnectionConfiguration.class)
public class BedrockAi21Jurassic2ChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public Ai21Jurassic2ChatBedrockApi ai21Jurassic2ChatBedrockApi(AwsCredentialsProvider credentialsProvider,
			BedrockAi21Jurassic2ChatProperties properties, BedrockAwsConnectionProperties awsProperties) {
		return new Ai21Jurassic2ChatBedrockApi(properties.getModel(), credentialsProvider, awsProperties.getRegion(),
				new ObjectMapper(), awsProperties.getTimeout());
	}

	@Bean
	public BedrockAi21Jurassic2ChatClient jurassic2ChatClient(Ai21Jurassic2ChatBedrockApi ai21Jurassic2ChatBedrockApi,
			BedrockAi21Jurassic2ChatProperties properties) {

		return BedrockAi21Jurassic2ChatClient.builder(ai21Jurassic2ChatBedrockApi)
			.withOptions(properties.getOptions())
			.build();
	}

}