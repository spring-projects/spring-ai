/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.wavespeed;

import java.time.Duration;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.wavespeed.api.WaveSpeedApi;
import org.springframework.ai.wavespeed.api.WaveSpeedImageOptions;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link WaveSpeedImageModel} using a mock WaveSpeed API server.
 *
 * @author Zeyi Cheng
 */
class WaveSpeedImageModelTests {

	private MockWebServer server;

	private WaveSpeedImageModel imageModel;

	@BeforeEach
	void setUp() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
		WaveSpeedApi api = new WaveSpeedApi("test-api-key", this.server.url("/").toString(), RestClient.builder(),
				Duration.ofMillis(10), Duration.ofSeconds(5));
		this.imageModel = new WaveSpeedImageModel(api,
				WaveSpeedImageOptions.builder().model("bytedance/seedream-v5.0-pro").build());
	}

	@AfterEach
	void tearDown() throws Exception {
		this.server.shutdown();
	}

	@Test
	void callReturnsImageUrlAfterPolling() throws Exception {
		this.server.enqueue(jsonResponse("""
				{"code":200,"message":"success","data":{"id":"pred-123","status":"created"}}"""));
		this.server.enqueue(jsonResponse("""
				{"code":200,"message":"success","data":{"id":"pred-123","status":"processing"}}"""));
		this.server.enqueue(jsonResponse("""
				{"code":200,"message":"success","data":{"id":"pred-123","status":"completed",
				"outputs":["https://example.org/image.png"]}}"""));

		ImageResponse response = this.imageModel.call(new ImagePrompt("A sunrise over snowy mountains"));

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput().getUrl()).isEqualTo("https://example.org/image.png");

		RecordedRequest submitRequest = this.server.takeRequest();
		assertThat(submitRequest.getPath()).isEqualTo("/api/v3/bytedance/seedream-v5.0-pro");
		assertThat(submitRequest.getHeader("Authorization")).isEqualTo("Bearer test-api-key");
		assertThat(submitRequest.getHeader("X-Client-Name")).isEqualTo("spring-ai-wavespeed");
		assertThat(submitRequest.getBody().readUtf8()).contains("A sunrise over snowy mountains");

		RecordedRequest pollRequest = this.server.takeRequest();
		assertThat(pollRequest.getPath()).isEqualTo("/api/v3/predictions/pred-123/result");
	}

	@Test
	void callSendsSizeAndSeed() throws Exception {
		this.server.enqueue(jsonResponse("""
				{"code":200,"message":"success","data":{"id":"pred-456","status":"completed",
				"outputs":["https://example.org/other.png"]}}"""));
		this.server.enqueue(jsonResponse("""
				{"code":200,"message":"success","data":{"id":"pred-456","status":"completed",
				"outputs":["https://example.org/other.png"]}}"""));

		WaveSpeedImageOptions runtimeOptions = WaveSpeedImageOptions.builder().size("2048*2048").seed(42).build();
		this.imageModel.call(new ImagePrompt("A red bicycle", runtimeOptions));

		RecordedRequest submitRequest = this.server.takeRequest();
		String body = submitRequest.getBody().readUtf8();
		assertThat(body).contains("\"size\":\"2048*2048\"");
		assertThat(body).contains("\"seed\":42");
	}

	@Test
	void callThrowsOnFailedPrediction() {
		this.server.enqueue(jsonResponse("""
				{"code":200,"message":"success","data":{"id":"pred-789","status":"created"}}"""));
		this.server.enqueue(jsonResponse("""
				{"code":200,"message":"success","data":{"id":"pred-789","status":"failed","error":"NSFW content"}}"""));

		assertThatThrownBy(() -> this.imageModel.call(new ImagePrompt("A test prompt")))
			.isInstanceOf(NonTransientAiException.class)
			.hasMessageContaining("failed")
			.hasMessageContaining("NSFW content");
	}

	@Test
	void callThrowsOnErrorEnvelope() {
		this.server.enqueue(jsonResponse("""
				{"code":401,"message":"invalid api key"}"""));

		assertThatThrownBy(() -> this.imageModel.call(new ImagePrompt("A test prompt")))
			.isInstanceOf(NonTransientAiException.class)
			.hasMessageContaining("invalid api key");
	}

	private static MockResponse jsonResponse(String body) {
		return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
	}

}
