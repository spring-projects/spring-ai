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

package org.springframework.ai.openai.audio.transcription;

import java.util.List;
import java.util.Map;

import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenAiAudioTranscriptionOptions}.
 *
 * @author guan xu
 */
class OpenAiAudioTranscriptionOptionsTests {

	@Test
	void genericAudioTranscriptionOptionsAreMerged() {
		AudioTranscriptionOptions source = new AudioTranscriptionOptions() {
			@Override
			public String getModel() {
				return "generic-model";
			}
		};

		OpenAiAudioTranscriptionOptions merged = OpenAiAudioTranscriptionOptions.builder().merge(source).build();

		assertThat(merged.getModel()).isEqualTo("generic-model");
	}

	@Test
	void testOptionsBuilderMergeCustomHeadersAndTimestampGranularities() {
		OpenAiAudioTranscriptionOptions defaultOptions = OpenAiAudioTranscriptionOptions.builder()
			.customHeaders(Map.of("default-header", "default-value"))
			.timestampGranularities(List.of(TranscriptionCreateParams.TimestampGranularity.WORD))
			.build();

		OpenAiAudioTranscriptionOptions requestOptions = OpenAiAudioTranscriptionOptions.builder()
			.customHeaders(Map.of("merged-header1", "merged-value1", "merged-header2", "merged-value2"))
			.timestampGranularities(List.of(TranscriptionCreateParams.TimestampGranularity.SEGMENT))
			.build();

		OpenAiAudioTranscriptionOptions merged = OpenAiAudioTranscriptionOptions.builder()
			.from(defaultOptions)
			.merge(requestOptions)
			.build();

		assertThat(merged.getCustomHeaders()).containsExactlyInAnyOrderEntriesOf(Map.of("default-header",
				"default-value", "merged-header1", "merged-value1", "merged-header2", "merged-value2"));
		assertThat(merged.getTimestampGranularities()).containsExactly(
				TranscriptionCreateParams.TimestampGranularity.WORD,
				TranscriptionCreateParams.TimestampGranularity.SEGMENT);
	}

}
