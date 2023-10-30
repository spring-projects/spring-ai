package org.springframework.ai.autoconfigure.aws;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.experimental.ai.client.AWSBedrockClient;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@AutoConfiguration
@ConditionalOnClass(AWSBedrockClient.class)
@ConditionalOnMissingBean(BedrockRuntimeClient.class)
public class AWSBedrockAutoConfiguration {

	@Bean
	@ConditionalOnBean(AwsCredentialsProvider.class)
	public BedrockRuntimeClient awsBedrockClient(AwsCredentialsProvider provider) {
		return BedrockRuntimeClient.builder().credentialsProvider(provider).build();
	}

	@Bean
	@ConditionalOnMissingBean(AwsCredentialsProvider.class)
	public BedrockRuntimeClient awsBedrockClient() {
		return BedrockRuntimeClient.create();
	}

}
