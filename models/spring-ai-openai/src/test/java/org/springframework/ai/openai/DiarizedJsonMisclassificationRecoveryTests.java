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

package org.springframework.ai.openai;

import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionDiarizedSegment;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DiarizedJsonMisclassificationRecovery}.
 *
 * @author Christian Tzolov
 */
class DiarizedJsonMisclassificationRecoveryTests {

	// Shape observed from the real OpenAI API when a diarized_json request is
	// misclassified by the SDK as a plain Transcription (see openai-java#802): the
	// entire raw response body ends up as Transcription#text().
	private static final String RAW_DIARIZED_JSON = """
			{"text":"And so, my fellow Americans, ask not what your country can do for you.",\
			"segments":[\
			{"type":"transcript.text.segment","text":"And so, my fellow Americans,","speaker":"A","start":0.0,"end":2.1,"id":"seg_0"},\
			{"type":"transcript.text.segment","text":"ask not what your country can do for you.","speaker":"A","start":2.8,"end":10.3,"id":"seg_1"}\
			],\
			"usage":{"type":"tokens","total_tokens":432,"input_tokens":110,"output_tokens":322}}""";

	@Test
	void recoversTextSegmentsAndUsage() {
		Transcription candidate = Transcription.builder().text(RAW_DIARIZED_JSON).build();

		DiarizedJsonMisclassificationRecovery.Recovered recovered = DiarizedJsonMisclassificationRecovery
			.tryRecover(candidate);

		assertThat(recovered).isNotNull();
		assertThat(recovered.text())
			.isEqualTo("And so, my fellow Americans, ask not what your country can do for you.");
		assertThat(recovered.segments()).hasSize(2);

		TranscriptionDiarizedSegment first = recovered.segments().get(0);
		assertThat(first.id()).isEqualTo("seg_0");
		assertThat(first.speaker()).isEqualTo("A");
		assertThat(first.start()).isEqualTo(0.0);
		assertThat(first.end()).isEqualTo(2.1, Offset.offset(0.001));
		assertThat(first.text()).isEqualTo("And so, my fellow Americans,");

		assertThat(recovered.usage()).isNotNull();
		assertThat(recovered.usage()).containsEntry("total_tokens", 432);
	}

	@Test
	void returnsNullForPlainText() {
		Transcription candidate = Transcription.builder().text("Hello world").build();

		assertThat(DiarizedJsonMisclassificationRecovery.tryRecover(candidate)).isNull();
	}

	@Test
	void returnsNullForJsonWithoutTextField() {
		Transcription candidate = Transcription.builder().text("{\"foo\":\"bar\"}").build();

		assertThat(DiarizedJsonMisclassificationRecovery.tryRecover(candidate)).isNull();
	}

	@Test
	void returnsNullForNonObjectJson() {
		Transcription candidate = Transcription.builder().text("[1,2,3]").build();

		assertThat(DiarizedJsonMisclassificationRecovery.tryRecover(candidate)).isNull();
	}

	@Test
	void skipsSegmentsMissingRequiredFields() {
		String rawJson = """
				{"text":"Hello world","segments":[{"speaker":"A","start":0.0,"end":1.0,"text":"missing id"}]}""";
		Transcription candidate = Transcription.builder().text(rawJson).build();

		DiarizedJsonMisclassificationRecovery.Recovered recovered = DiarizedJsonMisclassificationRecovery
			.tryRecover(candidate);

		assertThat(recovered).isNotNull();
		assertThat(recovered.segments()).isEmpty();
	}

	@Test
	void handlesMissingUsage() {
		String rawJson = """
				{"text":"Hello world","segments":[]}""";
		Transcription candidate = Transcription.builder().text(rawJson).build();

		DiarizedJsonMisclassificationRecovery.Recovered recovered = DiarizedJsonMisclassificationRecovery
			.tryRecover(candidate);

		assertThat(recovered).isNotNull();
		assertThat(recovered.usage()).isNull();
	}

}
