/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.google.genai.tts.api;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiTtsApiTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void testSingleSpeakerRequestSerialization() throws Exception {
		var voiceConfig = new GeminiTtsApi.VoiceConfig(new GeminiTtsApi.PrebuiltVoiceConfig("Kore"));
		var speechConfig = new GeminiTtsApi.SpeechConfig(voiceConfig, null);
		var generationConfig = new GeminiTtsApi.GenerationConfig(List.of("AUDIO"), speechConfig);
		var content = new GeminiTtsApi.Content(List.of(new GeminiTtsApi.Part("Say cheerfully: Have a wonderful day!")));
		var request = new GeminiTtsApi.GenerateContentRequest(List.of(content), generationConfig);

		String json = this.objectMapper.writeValueAsString(request);

		assertThat(json).contains("\"responseModalities\":[\"AUDIO\"]");
		assertThat(json).contains("\"voiceName\":\"Kore\"");
		assertThat(json).contains("\"text\":\"Say cheerfully: Have a wonderful day!\"");
	}

	@Test
	void testMultiSpeakerRequestSerialization() throws Exception {
		var speaker1Config = GeminiTtsApi.SpeakerVoiceConfig.of("Joe", "Kore");
		var speaker2Config = GeminiTtsApi.SpeakerVoiceConfig.of("Jane", "Puck");
		var multiSpeakerConfig = new GeminiTtsApi.MultiSpeakerVoiceConfig(List.of(speaker1Config, speaker2Config));
		var speechConfig = new GeminiTtsApi.SpeechConfig(null, multiSpeakerConfig);
		var generationConfig = new GeminiTtsApi.GenerationConfig(List.of("AUDIO"), speechConfig);
		var content = new GeminiTtsApi.Content(List.of(new GeminiTtsApi.Part("Joe: Hello!\nJane: Hi there!")));
		var request = new GeminiTtsApi.GenerateContentRequest(List.of(content), generationConfig);

		String json = this.objectMapper.writeValueAsString(request);

		assertThat(json).contains("\"speaker\":\"Joe\"");
		assertThat(json).contains("\"speaker\":\"Jane\"");
		assertThat(json).contains("\"multiSpeakerVoiceConfig\"");
	}

	@Test
	void testResponseDeserialization() throws Exception {
		String responseJson = "{" + "\"candidates\": [{" + "\"content\": {" + "\"parts\": [{" + "\"inlineData\": {"
				+ "\"mimeType\": \"audio/pcm\"," + "\"data\": \"SGVsbG8gV29ybGQ=\"" + "}" + "}]" + "}" + "}]" + "}";

		var response = this.objectMapper.readValue(responseJson, GeminiTtsApi.GenerateContentResponse.class);

		assertThat(response).isNotNull();
		assertThat(response.candidates()).hasSize(1);
		assertThat(response.candidates().get(0).content().parts()).hasSize(1);
		assertThat(response.candidates().get(0).content().parts().get(0).inlineData().data())
			.isEqualTo("SGVsbG8gV29ybGQ=");
	}

}
