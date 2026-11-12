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

package org.springframework.ai.mistralai.api;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.mistralai.api.MistralAiAudioApi.TimestampGranularity;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TranscriptionRequest;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TranscriptionResponse;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TranscriptionStreamEvent;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link MistralAiAudioApi}.
 *
 * @author Thomas Vitale
 */
class MistralAiAudioApiTests {

	private static final String TRANSCRIPTION_RESPONSE = """
			{
				"model": "voxtral-mini-transcribe-3",
				"text": "Thanks for calling Northwind Support. Hi Daniel.",
				"language": null,
				"segments": [
					{"type": "transcription_segment", "text": "Thanks for calling Northwind Support.", "start": 0.0, "end": 1.6, "speaker_id": "speaker_1"},
					{"type": "transcription_segment", "text": " Hi Daniel.", "start": 4.1, "end": 4.8, "speaker_id": "speaker_2"}
				],
				"usage": {
					"prompt_audio_seconds": 34,
					"prompt_tokens": 3,
					"total_tokens": 879,
					"completion_tokens": 126,
					"prompt_tokens_details": {"cached_tokens": 1, "audio_tokens": 750},
					"service_tier": "standard"
				},
				"finish_reason": null
			}
			""";

	private static final String TRANSCRIPTION_STREAM = """
			event: transcription.language
			data: {"type":"transcription.language","audio_language":"en"}

			event: transcription.text.delta
			data: {"type":"transcription.text.delta","text":"Thanks"}

			event: transcription.text.delta
			data: {"type":"transcription.text.delta","text":" for calling."}

			event: transcription.done
			data: {"model":"voxtral-mini-transcribe-3","text":"Thanks for calling.","language":null,"segments":[],"usage":{"prompt_audio_seconds":2,"prompt_tokens":3,"total_tokens":20,"completion_tokens":5},"finish_reason":null,"type":"transcription.done"}

			""";

	@Test
	void createTranscriptionSendsMultipartForm() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		MistralAiAudioApi api = MistralAiAudioApi.builder()
			.apiKey("test-key")
			.restClientBuilder(restClientBuilder)
			.build();

		server.expect(requestTo("https://api.mistral.ai/v1/audio/transcriptions"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
			.andExpect(header(HttpHeaders.CONTENT_TYPE, containsString(MediaType.MULTIPART_FORM_DATA_VALUE)))
			.andExpect(content().string(allOf(containsString("name=\"model\""),
					containsString("voxtral-mini-transcribe-3"), containsString("name=\"file\"; filename=\"audio\""),
					containsString("audio-bytes"), containsString("name=\"language\""),
					containsString("name=\"temperature\""), containsString("name=\"diarize\""),
					containsString("name=\"timestamp_granularities\""), containsString("segment"),
					containsString("name=\"context_bias\""), containsString("Northwind"), containsString("Quillara"),
					not(containsString("name=\"stream\"")), not(containsString("name=\"file_url\"")))))
			.andRespond(withSuccess(TRANSCRIPTION_RESPONSE, MediaType.APPLICATION_JSON));

		TranscriptionRequest request = TranscriptionRequest.builder()
			.model(MistralAiAudioApi.TranscriptionModel.VOXTRAL_MINI_TRANSCRIBE_3.getValue())
			.file(new ByteArrayResource("audio-bytes".getBytes()))
			.language("en")
			.temperature(0.2)
			.diarize(true)
			.timestampGranularities(List.of(TimestampGranularity.SEGMENT))
			.contextBias(List.of("Northwind", "Quillara"))
			.build();

		TranscriptionResponse response = api.createTranscription(request).getBody();

		server.verify();
		assertThat(response).isNotNull();
		assertThat(response.model()).isEqualTo("voxtral-mini-transcribe-3");
		assertThat(response.text()).isEqualTo("Thanks for calling Northwind Support. Hi Daniel.");
		assertThat(response.language()).isNull();
		assertThat(response.segments()).hasSize(2);
		assertThat(response.segments().get(1).text()).isEqualTo(" Hi Daniel.");
		assertThat(response.segments().get(1).start()).isEqualTo(4.1);
		assertThat(response.segments().get(1).end()).isEqualTo(4.8);
		assertThat(response.segments().get(1).speakerId()).isEqualTo("speaker_2");
		assertThat(response.usage()).isNotNull();
		assertThat(response.usage().promptAudioSeconds()).isEqualTo(34);
		assertThat(response.usage().promptTokens()).isEqualTo(3);
		assertThat(response.usage().completionTokens()).isEqualTo(126);
		assertThat(response.usage().totalTokens()).isEqualTo(879);
	}

	@Test
	void createTranscriptionStreamParsesServerSentEvents() {
		AtomicReference<ClientRequest> sentRequest = new AtomicReference<>();
		WebClient.Builder webClientBuilder = WebClient.builder().exchangeFunction(request -> {
			sentRequest.set(request);
			return Mono.just(ClientResponse.create(HttpStatus.OK)
				.header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
				.body(TRANSCRIPTION_STREAM)
				.build());
		});
		MistralAiAudioApi api = MistralAiAudioApi.builder()
			.apiKey("test-key")
			.webClientBuilder(webClientBuilder)
			.build();

		TranscriptionRequest request = TranscriptionRequest.builder()
			.model(MistralAiAudioApi.TranscriptionModel.VOXTRAL_MINI_LATEST.getValue())
			.fileUrl("https://example.com/audio.mp3")
			.stream(true)
			.build();

		List<TranscriptionStreamEvent> events = api.createTranscriptionStream(request)
			.collectList()
			.block(Duration.ofSeconds(5));

		assertThat(sentRequest.get().url().toString()).isEqualTo("https://api.mistral.ai/v1/audio/transcriptions");
		assertThat(sentRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-key");
		assertThat(sentRequest.get().headers().getAccept()).containsExactly(MediaType.TEXT_EVENT_STREAM);
		assertThat(events).extracting(TranscriptionStreamEvent::type)
			.containsExactly(TranscriptionStreamEvent.LANGUAGE, TranscriptionStreamEvent.TEXT_DELTA,
					TranscriptionStreamEvent.TEXT_DELTA, TranscriptionStreamEvent.DONE);
		assertThat(events.get(0).audioLanguage()).isEqualTo("en");
		assertThat(events.get(1).text()).isEqualTo("Thanks");
		assertThat(events.get(2).text()).isEqualTo(" for calling.");
		TranscriptionStreamEvent done = events.get(3);
		assertThat(done.model()).isEqualTo("voxtral-mini-transcribe-3");
		assertThat(done.text()).isEqualTo("Thanks for calling.");
		assertThat(done.usage()).isNotNull();
		assertThat(done.usage().totalTokens()).isEqualTo(20);
	}

	@Test
	void createTranscriptionSendsReadOnceAudioWithoutFilename() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
		MistralAiAudioApi api = MistralAiAudioApi.builder()
			.apiKey("test-key")
			.restClientBuilder(restClientBuilder)
			.build();

		server.expect(requestTo("https://api.mistral.ai/v1/audio/transcriptions"))
			.andExpect(content()
				.string(allOf(containsString("name=\"file\"; filename=\"audio\""), containsString("audio-bytes"))))
			.andRespond(withSuccess(TRANSCRIPTION_RESPONSE, MediaType.APPLICATION_JSON));

		TranscriptionRequest request = TranscriptionRequest.builder()
			.model("voxtral-mini-latest")
			.file(new InputStreamResource(new ByteArrayInputStream("audio-bytes".getBytes())))
			.build();

		api.createTranscription(request);

		server.verify();
	}

	@Test
	void createTranscriptionStreamSendsReadOnceAudioWithoutFilename() {
		AtomicReference<String> sentBody = new AtomicReference<>();
		WebClient.Builder webClientBuilder = WebClient.builder().exchangeFunction(request -> {
			MockClientHttpRequest httpRequest = new MockClientHttpRequest(request.method(), request.url());
			return request.writeTo(httpRequest, ExchangeStrategies.withDefaults())
				.then(Mono.defer(httpRequest::getBodyAsString))
				.doOnNext(sentBody::set)
				.thenReturn(ClientResponse.create(HttpStatus.OK)
					.header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
					.body(TRANSCRIPTION_STREAM)
					.build());
		});
		MistralAiAudioApi api = MistralAiAudioApi.builder()
			.apiKey("test-key")
			.webClientBuilder(webClientBuilder)
			.build();

		TranscriptionRequest request = TranscriptionRequest.builder()
			.model("voxtral-mini-latest")
			.file(new InputStreamResource(new ByteArrayInputStream("audio-bytes".getBytes())))
			.stream(true)
			.build();

		api.createTranscriptionStream(request).blockLast(Duration.ofSeconds(5));

		assertThat(sentBody.get()).contains("name=\"file\"; filename=\"audio\"", "audio-bytes", "name=\"stream\"");
	}

	@Test
	void createTranscriptionRejectsStreamingRequest() {
		MistralAiAudioApi api = MistralAiAudioApi.builder().apiKey("test-key").build();
		TranscriptionRequest request = TranscriptionRequest.builder()
			.model("voxtral-mini-latest")
			.fileId("file-id")
			.stream(true)
			.build();

		assertThatIllegalArgumentException().isThrownBy(() -> api.createTranscription(request))
			.withMessageContaining("stream");
	}

	@Test
	void createTranscriptionStreamRejectsNonStreamingRequest() {
		MistralAiAudioApi api = MistralAiAudioApi.builder().apiKey("test-key").build();
		TranscriptionRequest request = TranscriptionRequest.builder()
			.model("voxtral-mini-latest")
			.fileId("file-id")
			.build();

		assertThatIllegalArgumentException().isThrownBy(() -> api.createTranscriptionStream(request))
			.withMessageContaining("stream");
	}

	@Test
	void transcriptionRequestRequiresExactlyOneAudioSource() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> TranscriptionRequest.builder().model("voxtral-mini-latest").build())
			.withMessageContaining("Exactly one of file, fileUrl and fileId");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> TranscriptionRequest.builder()
				.model("voxtral-mini-latest")
				.fileUrl("https://example.com/audio.mp3")
				.fileId("file-id")
				.build())
			.withMessageContaining("Exactly one of file, fileUrl and fileId");
	}

}
