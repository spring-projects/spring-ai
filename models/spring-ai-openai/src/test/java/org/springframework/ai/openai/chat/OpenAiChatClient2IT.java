/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.openai.chat;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@SpringBootTest(classes = OpenAiChatClient2IT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiChatClient2IT {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private OpenAiChatClient openAiChatClient;

	@Test
	void responseFormatTest() throws JsonMappingException, JsonProcessingException {

		// 400 - ResponseError[error=Error[message='json' is not one of ['json_object',
		// 'text'] -
		// 'response_format.type', type=invalid_request_error, param=null, code=null]]

		// 400 - ResponseError[error=Error[message='messages' must contain the word 'json'
		// in some form, to use
		// 'response_format' of type 'json_object'., type=invalid_request_error,
		// param=messages, code=null]]

		Prompt prompt = new Prompt("List 8 planets. Use JSON response",
				OpenAiChatOptions.builder()
					.withResponseFormat(new ChatCompletionRequest.ResponseFormat("json_object"))
					.build());

		ChatResponse response = this.openAiChatClient.call(prompt);

		assertThat(response).isNotNull();

		String content = response.getResult().getOutput().getContent();

		logger.info("Response content: {}", content);

		assertThat(isValidJson(content)).isTrue();
	}

	private static ObjectMapper MAPPER = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

	public static boolean isValidJson(String json) {
		try {
			MAPPER.readTree(json);
		}
		catch (JacksonException e) {
			return false;
		}
		return true;
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiApi chatCompletionApi() {
			return new OpenAiApi(System.getenv("OPENAI_API_KEY"));
		}

		@Bean
		public OpenAiChatClient openAiClient(OpenAiApi openAiApi) {
			return new OpenAiChatClient(openAiApi);
		}

	}

}
