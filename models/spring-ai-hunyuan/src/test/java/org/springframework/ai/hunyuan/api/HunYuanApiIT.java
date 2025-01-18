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

package org.springframework.ai.hunyuan.api;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletion;
import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletionChunk;
import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletionMessage;
import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletionMessage.Role;
import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletionRequest;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 */

@EnabledIfEnvironmentVariable(named = "HUNYUAN_SECRET_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "HUNYUAN_SECRET_KEY", matches = ".+")
public class HunYuanApiIT {
	private static final Logger logger = LoggerFactory.getLogger(HunYuanApiIT.class);

	HunYuanApi hunyuanApi = new HunYuanApi(System.getenv("HUNYUAN_SECRET_ID"),System.getenv("HUNYUAN_SECRET_KEY"));

	@Test
	void chatCompletionEntity() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello World！", Role.user);
		ResponseEntity<HunYuanApi.ChatCompletionResponse> response = this.hunyuanApi.chatCompletionEntity(new ChatCompletionRequest(
				List.of(chatCompletionMessage), HunYuanApi.ChatModel.HUNYUAN_PRO.getValue(), 0.8,false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().response()).isNotNull();
		logger.info(response.getBody().response().toString());
//		System.out.println(response.getBody().response().errorMsg().message());
	}
	@Test
	void chatCompletionEntityByEnhance() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Why is the price of gold rising？", Role.user);
		ResponseEntity<HunYuanApi.ChatCompletionResponse> response = this.hunyuanApi.chatCompletionEntity(new ChatCompletionRequest(
				List.of(chatCompletionMessage), HunYuanApi.ChatModel.HUNYUAN_PRO.getValue(), false,false,true,true,true,true));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().response()).isNotNull();
		logger.info(response.getBody().response().toString());
//		System.out.println(response.getBody().response().errorMsg().message());
	}
	@Test
	void chatCompletionEntityWithSystemMessage() {
		ChatCompletionMessage userMessage = new ChatCompletionMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did?", Role.user);
		ChatCompletionMessage systemMessage = new ChatCompletionMessage("""
				You are an AI assistant that helps people find information.
				Your name is Bob.
				You should reply to the user's request with your name and also in the style of a pirate.
					""", Role.system);

		ResponseEntity<HunYuanApi.ChatCompletionResponse> response = this.hunyuanApi.chatCompletionEntity(new ChatCompletionRequest(
				List.of(systemMessage, userMessage), HunYuanApi.ChatModel.HUNYUAN_PRO.getValue(), 0.8, false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().response()).isNotNull();
//		System.out.println(response.getBody().response().choices().get(0).message().content());
	}
	@Test
	void chatCompletionEntityWithPicture() {
		ChatCompletionMessage userMessage = new ChatCompletionMessage(
				Role.user,
				List.of(new ChatCompletionMessage.ChatContent("text", "Which company's logo is in the picture below?"),
						new ChatCompletionMessage.ChatContent("image_url", new ChatCompletionMessage.ImageUrl("https://cloudcache.tencent-cloud.com/qcloud/ui/portal-set/build/About/images/bg-product-series_87d.png"))));
		ResponseEntity<HunYuanApi.ChatCompletionResponse> response = this.hunyuanApi.chatCompletionEntity(new ChatCompletionRequest(
				List.of(userMessage), HunYuanApi.ChatModel.HUNYUAN_TURBO_VISION.getValue(), 0.8, false));

		logger.info(response.getBody().response().toString());
		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().response()).isNotNull();
//		System.out.println(response.getBody().response().choices().get(0).message().content());
	}
	@Test
	void chatCompletionEntityWithNativePicture() {
		String imageInfo = "data:image/jpeg;base64,";
		// 读取图片文件
		var imageData = new ClassPathResource("/img.png");
		try(InputStream inputStream = imageData.getInputStream()) {
			byte[] imageBytes = inputStream.readAllBytes();
			// 使用Base64编码图片字节数据
			String encodedImage = Base64.getEncoder().encodeToString(imageBytes);
			// 输出编码后的字符串
			imageInfo += encodedImage;
		} catch (IOException e) {
			e.printStackTrace();
		}
		ChatCompletionMessage userMessage = new ChatCompletionMessage(
				Role.user,
				List.of(new ChatCompletionMessage.ChatContent("text", "Which company's logo is in the picture below?"),
						new ChatCompletionMessage.ChatContent("image_url", new ChatCompletionMessage.ImageUrl(imageInfo))));
		ResponseEntity<HunYuanApi.ChatCompletionResponse> response = this.hunyuanApi.chatCompletionEntity(new ChatCompletionRequest(
				List.of(userMessage), HunYuanApi.ChatModel.HUNYUAN_TURBO_VISION.getValue(), 0.8, false));

		logger.info(response.getBody().response().toString());
		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().response()).isNotNull();
	}
	@Test
	void chatCompletionStream() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.user);
		Flux<ChatCompletionChunk> response = this.hunyuanApi.chatCompletionStream(new ChatCompletionRequest(
				List.of(chatCompletionMessage), HunYuanApi.ChatModel.HUNYUAN_PRO.getValue(), 0.8, true));

		assertThat(response).isNotNull();
		assertThat(response.collectList().block()).isNotNull();
		logger.info(ModelOptionsUtils.toJsonString(response.collectList().block()));
	}

	@Test
	void chatCompletionStreamWithSystemMessage() {
		ChatCompletionMessage userMessage = new ChatCompletionMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did?", Role.user);
		ChatCompletionMessage systemMessage = new ChatCompletionMessage("""
				You are an AI assistant that helps people find information.
				Your name is Bob.
				You should reply to the user's request with your name and also in the style of a pirate.
					""", Role.system);
		Flux<ChatCompletionChunk> response = this.hunyuanApi.chatCompletionStream(new ChatCompletionRequest(
				List.of(systemMessage, userMessage), HunYuanApi.ChatModel.HUNYUAN_PRO.getValue(), 0.8, true));

		assertThat(response).isNotNull();
		assertThat(response.collectList().block()).isNotNull();
		logger.info(ModelOptionsUtils.toJsonString(response.collectList().block()));
	}

	@Test
	void chatCompletionStreamWithPicture() {
		ChatCompletionMessage userMessage = new ChatCompletionMessage(
				Role.user,
				List.of(new ChatCompletionMessage.ChatContent("text", "Which company's logo is in the picture below?"),
						new ChatCompletionMessage.ChatContent("image_url", new ChatCompletionMessage.ImageUrl("https://cloudcache.tencent-cloud.com/qcloud/ui/portal-set/build/About/images/bg-product-series_87d.png"))));
		Flux<ChatCompletionChunk> response = this.hunyuanApi.chatCompletionStream(new ChatCompletionRequest(
				List.of(userMessage), HunYuanApi.ChatModel.HUNYUAN_TURBO_VISION.getValue(), 0.8, true));

		assertThat(response).isNotNull();
		assertThat(response.collectList().block()).isNotNull();
		logger.info(ModelOptionsUtils.toJsonString(response.collectList().block()));
	}

	@Test
	void chatCompletionStreamWithNativePicture() {
		String imageInfo = "data:image/jpeg;base64,";
		// 读取图片文件
		var imageData = new ClassPathResource("/img.png");
		try(InputStream inputStream = imageData.getInputStream()) {
			byte[] imageBytes = inputStream.readAllBytes();
			// 使用Base64编码图片字节数据
			String encodedImage = Base64.getEncoder().encodeToString(imageBytes);
			// 输出编码后的字符串
			imageInfo += encodedImage;
		} catch (IOException e) {
			e.printStackTrace();
		}
		ChatCompletionMessage userMessage = new ChatCompletionMessage(
				Role.user,
				List.of(new ChatCompletionMessage.ChatContent("text", "Which company's logo is in the picture below?"),
						new ChatCompletionMessage.ChatContent("image_url", new ChatCompletionMessage.ImageUrl(imageInfo))));
		Flux<ChatCompletionChunk> response = this.hunyuanApi.chatCompletionStream(new ChatCompletionRequest(
				List.of(userMessage), HunYuanApi.ChatModel.HUNYUAN_TURBO_VISION.getValue(), 0.8, true));

		assertThat(response).isNotNull();
		assertThat(response.collectList().block()).isNotNull();
		logger.info(ModelOptionsUtils.toJsonString(response.collectList().block()));
	}

}
