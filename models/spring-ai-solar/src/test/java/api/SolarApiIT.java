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

package api;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.springframework.ai.ResourceUtils;
import org.springframework.ai.solar.api.SolarApi;
import org.springframework.http.ResponseEntity;
import org.stringtemplate.v4.ST;

import reactor.core.publisher.Flux;

/**
 * @author Seunghyeon Ji
 */
@EnabledIfEnvironmentVariables({ @EnabledIfEnvironmentVariable(named = "SOLAR_API_KEY", matches = ".+") })
public class SolarApiIT {

	SolarApi solarApi = new SolarApi(System.getenv("SOLAR_API_KEY"));

	@Test
	void chatCompletionEntity() {
		SolarApi.ChatCompletionMessage chatCompletionMessage = new SolarApi.ChatCompletionMessage("Hello world",
				SolarApi.ChatCompletionMessage.Role.USER);
		ResponseEntity<SolarApi.ChatCompletion> response = this.solarApi.chatCompletionEntity(
				new SolarApi.ChatCompletionRequest(List.of(chatCompletionMessage), SolarApi.DEFAULT_CHAT_MODEL, false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void embeddings() {
		ResponseEntity<SolarApi.EmbeddingList> response = this.solarApi
			.embeddings(new SolarApi.EmbeddingRequest("Hello world"));

		assertThat(response).isNotNull();
		assertThat(Objects.requireNonNull(response.getBody()).data()).hasSize(1);
		assertThat(response.getBody().data().get(0).embedding()).hasSize(4096);
	}

}
