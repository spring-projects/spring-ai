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
package org.springframework.ai.wordlift;

import org.springframework.ai.wordlift.api.WordLiftApi;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@SpringBootConfiguration
public class WordLiftTestConfiguration {

//	@Bean
//	public WordLiftChatModel wordliftChatModel() {
//		String apiKey = System.getenv("WORDLIFT_API_KEY");
//		if (!StringUtils.hasText(apiKey)) {
//			throw new IllegalArgumentException(
//					"You must provide an API key.  Put it in an environment variable under the name WORDLIFT_API_KEY");
//		}
//		// Created aws-mistral-7b-instruct and update the WORDLIFT_CHAT_URL
//		WordLiftChatModel wordliftChatModel = new WordLiftChatModel(
//				new WordLiftApi(
//						System.getenv("WORDLIFT_CHAT_URL"),
//						apiKey)
//		);
//		return wordliftChatModel;
//	}

}
