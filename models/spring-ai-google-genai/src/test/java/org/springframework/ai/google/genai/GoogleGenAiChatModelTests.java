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

package org.springframework.ai.google.genai;

import java.util.List;
import java.util.Optional;

import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.FinishReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.model.Generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GoogleGenAiChatModel}.
 *
 * @author Enrico Molino
 */
@ExtendWith(MockitoExtension.class)
public class GoogleGenAiChatModelTests {

	@Test
	void responseCandidateToGeneration_whenContentIsEmpty_doesNotThrow() {
		GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
			.genAiClient(mock(Client.class))
			.options(GoogleGenAiChatOptions.builder().model("gemini-1.5-pro").build())
			.build();

		Candidate candidate = mock(Candidate.class);
		when(candidate.content()).thenReturn(Optional.empty());
		when(candidate.index()).thenReturn(Optional.of(0));
		FinishReason finishReason = new FinishReason(FinishReason.Known.STOP);
		when(candidate.finishReason()).thenReturn(Optional.of(finishReason));

		List<Generation> result = model.responseCandidateToGeneration(candidate);

		assertThat(result).isNotNull();
		assertThat(result).hasSize(1);
		Generation generation = result.get(0);
		assertThat(generation.getOutput()).isNotNull();
		assertThat(generation.getOutput().getText()).isEmpty();
		assertThat(generation.getOutput().getMetadata().get("candidateIndex")).isEqualTo(0);
		assertThat(generation.getOutput().getMetadata().get("finishReason")).isEqualTo(finishReason);
	}

}
