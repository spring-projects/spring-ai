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

package org.springframework.ai.bedrock.api;

// @formatter:off

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.EmitFailureHandler;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.ResponseStream;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.Assert;

/**
 * Abstract class for the Bedrock API. It provides the basic functionality to invoke the chat completion model and
 * receive the response for streaming and non-streaming requests.
 * <p>
 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-ids-arns.html
 * <p>
 * https://us-east-1.console.aws.amazon.com/bedrock/home?region=us-east-1#/modelaccess
 *
 * @param <I> The input request type.
 * @param <O> The output response type.
 * @param <SO> The streaming response type. For some models this type can be the same as the output response type.
 *
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters.html">Model Parameters</a>

 * @author Christian Tzolov
 * @author Wei Jiang
 * @since 0.8.0
 */
public abstract class AbstractBedrockApi<I, O, SO> {

	private static final Logger logger = LoggerFactory.getLogger(AbstractBedrockApi.class);

	/**
	 * Default emit failure handler.
	 */
	public static final EmitFailureHandler DEFAULT_EMIT_FAILURE_HANDLER = EmitFailureHandler
	.busyLooping(Duration.ofSeconds(10));


	private final String modelId;
	private final ObjectMapper objectMapper;
	private final Region region;
	private final BedrockRuntimeClient client;
	private final BedrockRuntimeAsyncClient clientStreaming;

	/**
	 * Create a new AbstractBedrockApi instance using default credentials provider and object mapper.
	 *
	 * @param modelId The model id to use.
	 * @param region The AWS region to use.
	 */
	public AbstractBedrockApi(String modelId, String region) {
		this(modelId, ProfileCredentialsProvider.builder().build(), region, ModelOptionsUtils.OBJECT_MAPPER, Duration.ofMinutes(5));
	}
	/**
	 * Create a new AbstractBedrockApi instance using default credentials provider and object mapper.
	 *
	 * @param modelId The model id to use.
	 * @param region The AWS region to use.
	 * @param timeout The timeout to use.
	 */
	public AbstractBedrockApi(String modelId, String region, Duration timeout) {
		this(modelId, ProfileCredentialsProvider.builder().build(), region, ModelOptionsUtils.OBJECT_MAPPER, timeout);
	}

	/**
	 * Create a new AbstractBedrockApi instance using the provided credentials provider, region and object mapper.
	 *
	 * @param modelId The model id to use.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 */
	public AbstractBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper) {
		this(modelId, credentialsProvider, region, objectMapper, Duration.ofMinutes(5));
	}

	/**
	 * Create a new AbstractBedrockApi instance using the provided credentials provider, region and object mapper.
	 *
	 * @param modelId The model id to use.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 * @param timeout Configure the amount of time to allow the client to complete the execution of an API call.
	 * This timeout covers the entire client execution except for marshalling. This includes request handler execution,
	 * all HTTP requests including retries, unmarshalling, etc. This value should always be positive, if present.
	 */
	public AbstractBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, String region,
			ObjectMapper objectMapper, Duration timeout) {
		this(modelId, credentialsProvider, Region.of(region), objectMapper, timeout);
	}

	/**
	 * Create a new AbstractBedrockApi instance using the provided credentials provider, region and object mapper.
	 *
	 * @param modelId The model id to use.
	 * @param credentialsProvider The credentials provider to connect to AWS.
	 * @param region The AWS region to use.
	 * @param objectMapper The object mapper to use for JSON serialization and deserialization.
	 * @param timeout Configure the amount of time to allow the client to complete the execution of an API call.
	 * This timeout covers the entire client execution except for marshalling. This includes request handler execution,
	 * all HTTP requests including retries, unmarshalling, etc. This value should always be positive, if present.
	 */
	public AbstractBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, Region region,
			ObjectMapper objectMapper, Duration timeout) {

		Assert.hasText(modelId, "Model id must not be empty");
		Assert.notNull(credentialsProvider, "Credentials provider must not be null");
		Assert.notNull(region, "Region must not be empty");
		Assert.notNull(objectMapper, "Object mapper must not be null");
		Assert.notNull(timeout, "Timeout must not be null");

		this.modelId = modelId;
		this.objectMapper = objectMapper;
		this.region = region;


		this.client = BedrockRuntimeClient.builder()
				.region(this.region)
				.credentialsProvider(credentialsProvider)
				.overrideConfiguration(c -> c.apiCallTimeout(timeout))
				.build();

		this.clientStreaming = BedrockRuntimeAsyncClient.builder()
				.region(this.region)
				.credentialsProvider(credentialsProvider)
				.overrideConfiguration(c -> c.apiCallTimeout(timeout))
				.build();
	}

	/**
	 * Get the model id.
	 * @return The model id.
	 */
	public String getModelId() {
		return this.modelId;
	}

	/**
	 * Get the AWS region.
	 * @return The AWS region.
	 */
	public Region getRegion() {
		return this.region;
	}

	/**
	 * Compute the embedding for the given text.
	 *
	 * @param request The embedding request.
	 * @return Returns the embedding response.
	 */
	protected O embedding(I request) {
		throw new UnsupportedOperationException("Embedding is not supported for this model: " + this.modelId);
	}

	/**
	 * Chat completion invocation.
	 *
	 * @param request The chat completion request.
	 * @return The chat completion response.
	 */
	protected O chatCompletion(I request) {
		throw new UnsupportedOperationException("Chat completion is not supported for this model: " + this.modelId);
	}

	/**
	 * Chat completion invocation with streaming response.
	 *
	 * @param request The chat completion request.
	 * @return The chat completion response stream.
	 */
	protected Flux<SO> chatCompletionStream(I request) {
		throw new UnsupportedOperationException(
				"Streaming chat completion is not supported for this model: " + this.modelId);
	}

	/**
	 * Internal method to invoke the model and return the response.
	 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters.html
	 * https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InvokeModel.html
	 * https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/bedrockruntime/BedrockRuntimeClient.html#invokeModel
	 *
	 * @param request Model invocation request.
	 * @param clazz The response class type
	 * @return The model invocation response.
	 *
	 */
	protected O internalInvocation(I request, Class<O> clazz) {

		SdkBytes body;
		try {
			body = SdkBytes.fromUtf8String(this.objectMapper.writeValueAsString(request));
		}
		catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Invalid JSON format for the input request: " + request, e);
		}

		InvokeModelRequest invokeRequest = InvokeModelRequest.builder()
				.modelId(this.modelId)
				.body(body)
				.build();

		InvokeModelResponse response = this.client.invokeModel(invokeRequest);

		String responseBody = response.body().asString(StandardCharsets.UTF_8);

		try {
			return this.objectMapper.readValue(responseBody, clazz);
		}
		catch (JsonProcessingException | UncheckedIOException e) {

			throw new IllegalArgumentException("Invalid JSON format for the response: " + responseBody, e);
		}
	}

	/**
	 * Internal method to invoke the model and return the response stream.
	 *
	 * @param request Model invocation request.
	 * @param clazz Response class type.
	 * @return The model invocation response stream.
	 */
	protected Flux<SO> internalInvocationStream(I request, Class<SO> clazz) {

		// final Sinks.Many<SO> eventSink = Sinks.many().unicast().onBackpressureError();
		final Sinks.Many<SO> eventSink = Sinks.many().multicast().onBackpressureBuffer();

		SdkBytes body;
		try {
			body = SdkBytes.fromUtf8String(this.objectMapper.writeValueAsString(request));
		}
		catch (JsonProcessingException e) {
			eventSink.emitError(e, DEFAULT_EMIT_FAILURE_HANDLER);
			return eventSink.asFlux();
		}

		InvokeModelWithResponseStreamRequest invokeRequest = InvokeModelWithResponseStreamRequest.builder()
				.modelId(this.modelId)
				.body(body)
				.build();

		InvokeModelWithResponseStreamResponseHandler.Visitor visitor = InvokeModelWithResponseStreamResponseHandler.Visitor
				.builder()
				.onChunk(chunk -> {
					try {
						logger.debug("Received chunk: " + chunk.bytes().asString(StandardCharsets.UTF_8));
						SO response = this.objectMapper.readValue(chunk.bytes().asByteArray(), clazz);
						eventSink.emitNext(response, DEFAULT_EMIT_FAILURE_HANDLER);
					}
					catch (Exception e) {
						logger.error("Failed to unmarshall", e);
						eventSink.emitError(e, DEFAULT_EMIT_FAILURE_HANDLER);
					}
				})
				.onDefault(event -> {
					logger.error("Unknown or unhandled event: " + event.toString());
					eventSink.emitError(new Throwable("Unknown or unhandled event: " + event.toString()), DEFAULT_EMIT_FAILURE_HANDLER);
				})
				.build();

		InvokeModelWithResponseStreamResponseHandler responseHandler = InvokeModelWithResponseStreamResponseHandler
				.builder()
				.onComplete(
						() -> {
							eventSink.emitComplete(DEFAULT_EMIT_FAILURE_HANDLER);
							logger.info("Completed streaming response.");
						})
				.onError(error -> {
					logger.error("\n\nError streaming response: " + error.getMessage());
					eventSink.emitError(error, DEFAULT_EMIT_FAILURE_HANDLER);
				})
				.onEventStream(stream -> stream.subscribe(
						(ResponseStream e) -> e.accept(visitor)))
				.build();

		this.clientStreaming.invokeModelWithResponseStream(invokeRequest, responseHandler);

		return eventSink.asFlux();
	}

	/**
	 * Encapsulates the metrics about the model invocation.
	 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-claude.html
	 *
	 * @param inputTokenCount The number of tokens in the input prompt.
	 * @param firstByteLatency The time in milliseconds between the request being sent and the first byte of the
	 * response being received.
	 * @param outputTokenCount The number of tokens in the generated text.
	 * @param invocationLatency The time in milliseconds between the request being sent and the response being received.
	 */
	@JsonInclude(Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AmazonBedrockInvocationMetrics(
			@JsonProperty("inputTokenCount") Long inputTokenCount,
			@JsonProperty("firstByteLatency") Long firstByteLatency,
			@JsonProperty("outputTokenCount") Long outputTokenCount,
			@JsonProperty("invocationLatency") Long invocationLatency) {
	}
}
// @formatter:on
