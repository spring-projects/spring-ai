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
// @formatter:off
package org.springframework.ai.bedrock.api;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;
import reactor.core.publisher.Sinks.EmitResult;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;

/**
 * Amazon Bedrock Converse API, It provides the basic functionality to invoke the Bedrock
 * AI model and receive the response for streaming and non-streaming requests.
 * The Converse API doesn't support any embedding models (such as Titan Embeddings G1 - Text)
 * or image generation models (such as Stability AI).
 *
 * <p>
 * https://docs.aws.amazon.com/bedrock/latest/userguide/conversation-inference.html
 * <p>
 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html
 * <p>
 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ConverseStream.html
 * <p>
 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-ids.html
 * <p>
 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters.html
 *
 * @author Wei Jiang
 * @since 1.0.0
 */
public class BedrockConverseApi {

	private static final Logger logger = LoggerFactory.getLogger(BedrockConverseApi.class);

	private final BedrockRuntimeClient bedrockRuntimeClient;

	private final BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;

	private final RetryTemplate retryTemplate;

	/**
	 * Create a new BedrockConverseApi instance using default credentials provider.
	 *
	 * @param region The AWS region to use.
	 */
	public BedrockConverseApi(String region) {
		this(ProfileCredentialsProvider.builder().build(), region, Duration.ofMinutes(5));
	}

	/**
	 * Create a new BedrockConverseApi instance using default credentials provider.
	 *
	 * @param region The AWS region to use.
	 * @param timeout The timeout to use.
	 */
	public BedrockConverseApi(String region, Duration timeout) {
		this(ProfileCredentialsProvider.builder().build(), region, timeout);
	}

	/**
	 * Create a new BedrockConverseApi instance using the provided credentials provider,
	 * region.
	 *
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 */
	public BedrockConverseApi(AwsCredentialsProvider credentialsProvider, String region) {
		this(credentialsProvider, region, Duration.ofMinutes(5));
	}

	/**
	 * Create a new BedrockConverseApi instance using the provided credentials provider,
	 * region.
	 *
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param timeout Configure the amount of time to allow the client to complete the
	 * execution of an API call. This timeout covers the entire client execution except
	 * for marshalling. This includes request handler execution, all HTTP requests
	 * including retries, unmarshalling, etc. This value should always be positive, if
	 * present.
	 */
	public BedrockConverseApi(AwsCredentialsProvider credentialsProvider, String region, Duration timeout) {
		this(credentialsProvider, Region.of(region), timeout);
	}

	/**
	 * Create a new BedrockConverseApi instance using the provided credentials provider,
	 * region.
	 *
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param timeout Configure the amount of time to allow the client to complete the
	 * execution of an API call. This timeout covers the entire client execution except
	 * for marshalling. This includes request handler execution, all HTTP requests
	 * including retries, unmarshalling, etc. This value should always be positive, if
	 * present.
	 */
	public BedrockConverseApi(AwsCredentialsProvider credentialsProvider, Region region, Duration timeout) {
		this(credentialsProvider, region, timeout, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Create a new BedrockConverseApi instance using the provided credentials provider,
	 * region
	 *
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param timeout Configure the amount of time to allow the client to complete the
	 * execution of an API call. This timeout covers the entire client execution except
	 * for marshalling. This includes request handler execution, all HTTP requests
	 * including retries, unmarshalling, etc. This value should always be positive, if
	 * present.
	 * @param retryTemplate The retry template used to retry the Amazon Bedrock Converse
	 * API calls.
	 */
	public BedrockConverseApi(AwsCredentialsProvider credentialsProvider, Region region, Duration timeout,
			RetryTemplate retryTemplate) {
		this(BedrockRuntimeClient.builder()
				.region(region)
				.credentialsProvider(credentialsProvider)
				.overrideConfiguration(c -> c.apiCallTimeout(timeout))
				.build(), BedrockRuntimeAsyncClient.builder()
				.region(region)
				.credentialsProvider(credentialsProvider)
				.overrideConfiguration(c -> c.apiCallTimeout(timeout))
				.build(), retryTemplate);
	}

	/**
	 * Create a new BedrockConverseApi instance using the provided AWS Bedrock clients and the RetryTemplate.
	 *
	 * @param bedrockRuntimeClient The AWS BedrockRuntimeClient instance.
	 * @param bedrockRuntimeAsyncClient The AWS BedrockRuntimeAsyncClient instance.
	 * @param retryTemplate The retry template used to retry the Amazon Bedrock Converse
	 * API calls.
	 */
	public BedrockConverseApi(BedrockRuntimeClient bedrockRuntimeClient, BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient,
			RetryTemplate retryTemplate) {
		Assert.notNull(bedrockRuntimeClient, "bedrockRuntimeClient must not be null");
		Assert.notNull(bedrockRuntimeAsyncClient, "bedrockRuntimeAsyncClient must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");

		this.bedrockRuntimeClient = bedrockRuntimeClient;
		this.bedrockRuntimeAsyncClient = bedrockRuntimeAsyncClient;
		this.retryTemplate = retryTemplate;
	}

	/**
	 * Invoke the model and return the response.
	 *
	 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters.html
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html
	 * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/bedrockruntime/BedrockRuntimeClient.html#converse
	 * @param bedrockConverseRequest Model invocation request.
	 * @return The model invocation response.
	 */
	public ChatResponse converse(BedrockConverseRequest bedrockConverseRequest) {
		Assert.notNull(bedrockConverseRequest, "'bedrockConverseRequest' must not be null");

		ConverseRequest converseRequest = BedrockConverseApiUtils.createConverseRequest(bedrockConverseRequest);

		ConverseResponse converseResponse = converse(converseRequest);

		return BedrockConverseApiUtils.convertConverseResponse(converseResponse);
	}

	/**
	 * Invoke the model and return the response.
	 *
	 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters.html
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html
	 * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/bedrockruntime/BedrockRuntimeClient.html#converse
	 * @param converseRequest Model invocation request.
	 * @return The model invocation response.
	 */
	public ConverseResponse converse(ConverseRequest converseRequest) {
		Assert.notNull(converseRequest, "'converseRequest' must not be null");

		return this.retryTemplate.execute(ctx -> {
			return bedrockRuntimeClient.converse(converseRequest);
		});
	}

	/**
	 * Invoke the model and return the response stream.
	 *
	 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters.html
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html
	 * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/bedrockruntime/BedrockRuntimeAsyncClient.html#converseStream
	 * @param bedrockConverseRequest Model invocation request.
	 * @return The model invocation response stream.
	 */
	public Flux<ChatResponse> converseStream(BedrockConverseRequest bedrockConverseRequest) {
		Assert.notNull(bedrockConverseRequest, "'bedrockConverseRequest' must not be null");

		ConverseStreamRequest converseStreamRequest = BedrockConverseApiUtils
			.createConverseStreamRequest(bedrockConverseRequest);

		return converseStream(converseStreamRequest)
			.map(output -> BedrockConverseApiUtils.convertConverseStreamOutput(output));

	}

	/**
	 * Invoke the model and return the response stream.
	 *
	 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters.html
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_Converse.html
	 * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/bedrockruntime/BedrockRuntimeAsyncClient.html#converseStream
	 * @param converseStreamRequest Model invocation request.
	 * @return The model invocation response stream.
	 */
	public Flux<ConverseStreamOutput> converseStream(ConverseStreamRequest converseStreamRequest) {
		Assert.notNull(converseStreamRequest, "'converseStreamRequest' must not be null");

		return this.retryTemplate.execute(ctx -> {
			Sinks.Many<ConverseStreamOutput> eventSink = Sinks.many().multicast().onBackpressureBuffer();

			ConverseStreamResponseHandler.Visitor visitor = ConverseStreamResponseHandler.Visitor.builder()
				.onDefault((output) -> {
					logger.debug("Received converse stream output:{}", output);
					eventSink.tryEmitNext(output);
				})
				.build();

			ConverseStreamResponseHandler responseHandler = ConverseStreamResponseHandler.builder()
				.onEventStream(stream -> stream.subscribe((e) -> e.accept(visitor)))
				.onComplete(() -> {
					EmitResult emitResult = eventSink.tryEmitComplete();

					while (!emitResult.isSuccess()) {
						logger.debug("Emitting complete:{}", emitResult);
						emitResult = eventSink.tryEmitComplete();
					}

					eventSink.emitComplete(EmitFailureHandler.busyLooping(Duration.ofSeconds(3)));
					logger.debug("Completed streaming response.");
				})
				.onError((error) -> {
					logger.error("Error handling Bedrock converse stream response", error);
					eventSink.tryEmitError(error);
				})
				.build();

			bedrockRuntimeAsyncClient.converseStream(converseStreamRequest, responseHandler);

			return eventSink.asFlux();
		});
	}

	/**
	 * BedrockConverseRequest encapsulates the request parameters for the Amazon Bedrock
	 * Converse Api.
	 *
	 * @param modelId The Amazon Bedrock Model Id.
	 * @param messages The messages that you want to send to the model.
	 * @param systemMessages The system prompt to pass to the model.
	 * @param additionalModelRequestFields Additional inference parameters that the model
	 * supports, beyond the base set of inference parameters that Converse supports in the
	 * inferenceConfig field.
	 * @param toolConfiguration Configuration information for the tools that the model can
	 * use when generating a response.
	 */
	public record BedrockConverseRequest(String modelId, List<Message> messages,
			List<SystemContentBlock> systemMessages, Document additionalModelRequestFields,
			ToolConfiguration toolConfiguration) {

		public BedrockConverseRequest(String modelId, List<Message> messages, List<SystemContentBlock> systemMessages,
				Document additionalModelRequestFields) {
			this(modelId, messages, systemMessages, additionalModelRequestFields, null);
		}

		public static Builder from(BedrockConverseRequest request) {
			return new Builder(request);
		}

		public static class Builder {

			private String modelId;

			private List<Message> messages;

			private List<SystemContentBlock> systemMessages;

			private Document additionalModelRequestFields;

			private ToolConfiguration toolConfiguration;

			private Builder(BedrockConverseRequest request) {
				this.modelId = request.modelId();
				this.messages = request.messages();
				this.systemMessages = request.systemMessages();
				this.additionalModelRequestFields = request.additionalModelRequestFields();
				this.toolConfiguration = request.toolConfiguration();
			}

			public Builder withModelId(String modelId) {
				this.modelId = modelId;
				return this;
			}

			public Builder withMessages(List<Message> messages) {
				this.messages = messages;
				return this;
			}

			public Builder withSystemMessages(List<SystemContentBlock> systemMessages) {
				this.systemMessages = systemMessages;
				return this;
			}

			public Builder withAdditionalModelRequestFields(Document additionalModelRequestFields) {
				this.additionalModelRequestFields = additionalModelRequestFields;
				return this;
			}

			public Builder withToolConfiguration(ToolConfiguration toolConfiguration) {
				this.toolConfiguration = toolConfiguration;
				return this;
			}

			public BedrockConverseRequest build() {
				return new BedrockConverseRequest(modelId, messages, systemMessages, additionalModelRequestFields,
						toolConfiguration);
			}

		}

	}

}
//@formatter:on
