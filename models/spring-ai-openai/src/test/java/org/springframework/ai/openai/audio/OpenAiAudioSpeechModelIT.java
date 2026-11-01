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

package org.springframework.ai.openai.audio;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.metadata.OpenAiAudioSpeechResponseMetadata;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OpenAiAudioSpeechModel.
 *
 * @author Ahmed Yousri
 * @author Jonghoon Park
 * @author Ilayaperumal Gopinathan
 * @author Sebastien Deleuze
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiAudioSpeechModelIT {

	@Test
	void testSimpleSpeechGeneration() {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Hello world");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);

		Speech speech = response.getResult();
		assertThat(speech).isNotNull();
		assertThat(speech.getOutput()).isNotEmpty();
	}

	@Test
	void testCustomOptions() {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("tts-1-hd")
			.voice(OpenAiAudioSpeechOptions.Voice.NOVA)
			.responseFormat(OpenAiAudioSpeechOptions.AudioResponseFormat.OPUS)
			.speed(1.5)
			.build();

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().options(options).build();

		// Verify that the custom options were set on the model
		OpenAiAudioSpeechOptions modelOptions = (OpenAiAudioSpeechOptions) model.getOptions();
		assertThat(modelOptions.getModel()).isEqualTo("tts-1-hd");
		assertThat(modelOptions.getVoice()).isEqualTo("nova");
		assertThat(modelOptions.getResponseFormat()).isEqualTo("opus");
		assertThat(modelOptions.getSpeed()).isEqualTo(1.5);

		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Testing custom options");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	@Test
	void testNewVoiceOptions() {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("gpt-4o-mini-tts")
			.voice(OpenAiAudioSpeechOptions.Voice.BALLAD)
			.build();

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().options(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Testing new voice");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	@Test
	void testNewFormatOptions() {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("gpt-4o-mini-tts")
			.voice(OpenAiAudioSpeechOptions.Voice.ALLOY)
			.responseFormat(OpenAiAudioSpeechOptions.AudioResponseFormat.WAV)
			.build();

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().options(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Testing WAV format");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	@Test
	void testSimpleStringInput() {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().build();
		byte[] audioBytes = model.call("Today is a wonderful day to build something people love!");

		assertThat(audioBytes).isNotEmpty();
	}

	@Test
	void testStreamingBehavior() {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().build();
		// Long enough input that the audio response spans multiple 8KB chunks.
		TextToSpeechPrompt prompt = new TextToSpeechPrompt(
				"Today is a wonderful day to build something people love! " + "Streaming lets us start playing "
						+ "audio back before the whole response has finished generating, which matters a lot "
						+ "for real-time voice experiences.");

		Flux<TextToSpeechResponse> responseFlux = model.stream(prompt);

		assertThat(responseFlux).isNotNull();
		List<TextToSpeechResponse> responses = responseFlux.collectList().block(Duration.ofSeconds(30));
		assertThat(responses).isNotNull();

		// The audio is now delivered as multiple chunks as they arrive from OpenAI,
		// rather than as a single, fully-buffered response.
		assertThat(responses.size()).isGreaterThan(1);

		int totalBytes = 0;
		for (TextToSpeechResponse response : responses) {
			byte[] chunk = response.getResult().getOutput();
			assertThat(chunk).isNotEmpty();
			totalBytes += chunk.length;
		}
		assertThat(totalBytes).isGreaterThan(8192);
	}

	@Test
	void testStreamAsInputStream() throws IOException {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().build();
		String text = "Today is a wonderful day to build something people love! Bridging a reactive "
				+ "stream into a blocking InputStream lets us hand audio off to APIs that only "
				+ "understand java.io, without buffering the whole response in memory first.";

		Flux<byte[]> audioByteFlux = model.stream(text);

		int totalBytes = 0;
		try (InputStream audioInputStream = new FluxInputStream(audioByteFlux)) {
			byte[] buffer = new byte[4096];
			int bytesRead;
			while ((bytesRead = audioInputStream.read(buffer)) != -1) {
				assertThat(bytesRead).isGreaterThan(0);
				totalBytes += bytesRead;
			}
		}

		// 8192 is the model's internal streaming chunk size; a total larger than
		// that confirms multiple chunks were actually bridged through the
		// InputStream rather than a single, fully-buffered response.
		assertThat(totalBytes).isGreaterThan(8192);
	}

	@Test
	void testStreamAsInputStreamEarlyClose() throws IOException {
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().build();
		Flux<byte[]> audioByteFlux = model
			.stream("Today is a wonderful day to build something people love! " + "This sentence is long enough "
					+ "that its audio spans several streamed chunks, so closing early actually "
					+ "cancels an in-flight stream instead of one that had already finished.");

		// Reading only the first few bytes and closing early must not hang or
		// throw: FluxInputStream#close() cancels the upstream subscription rather
		// than draining it, which is what lets the underlying OpenAI HTTP
		// connection be released immediately instead of waiting for EOF.
		try (InputStream audioInputStream = new FluxInputStream(audioByteFlux)) {
			byte[] firstBytes = audioInputStream.readNBytes(16);
			assertThat(firstBytes).hasSize(16);
		}
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "alloy", "echo", "fable", "onyx", "nova", "shimmer", "sage", "coral", "ash" })
	void testAllVoices(String voice) {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("gpt-4o-mini-tts")
			.voice(voice)
			.build();

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().options(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Today is a wonderful day to build something people love!");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	@Test
	void testInstructionsOption() {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("gpt-4o-mini-tts")
			.voice(OpenAiAudioSpeechOptions.Voice.VERSE)
			.instructions("Friendly; warm tone; natural pauses; ~1.1x feel")
			.build();

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().options(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Today is a wonderful day to build something people love!");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	@Test
	void testRateLimitMetadata() {
		// Verify that SDK extracts rate limit metadata from response headers
		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Today is a wonderful day to build something people love!");

		TextToSpeechResponse response = model.call(prompt);
		OpenAiAudioSpeechResponseMetadata metadata = (OpenAiAudioSpeechResponseMetadata) response.getMetadata();

		// Metadata should be present with rate limit information
		assertThat(metadata).isNotNull();
		assertThat(metadata.getRateLimit()).isNotNull();

		// Rate limit values should be populated from response headers
		boolean hasRateLimitData = metadata.getRateLimit().getRequestsLimit() != null
				|| metadata.getRateLimit().getTokensLimit() != null;
		assertThat(hasRateLimitData).isTrue();
	}

	@Test
	void testTts1Model() {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("tts-1")
			.voice(OpenAiAudioSpeechOptions.Voice.ALLOY)
			.responseFormat(OpenAiAudioSpeechOptions.AudioResponseFormat.WAV)
			.speed(1.0)
			.build();

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().options(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Today is a wonderful day to build something people love!");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	@Test
	void testTts1HdModel() {
		OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
			.model("tts-1-hd")
			.voice(OpenAiAudioSpeechOptions.Voice.SHIMMER)
			.responseFormat(OpenAiAudioSpeechOptions.AudioResponseFormat.OPUS)
			.speed(1.0)
			.build();

		OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().options(options).build();
		TextToSpeechPrompt prompt = new TextToSpeechPrompt("Testing high definition audio model");

		TextToSpeechResponse response = model.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput()).isNotEmpty();
	}

	/**
	 * Bridges a {@code Flux<byte[]>} into a blocking {@link InputStream}, for handing
	 * streamed audio off to APIs that only understand {@code java.io}.
	 * <p>
	 * Two practices matter here that a naive {@code Flux#toIterable()}-based bridge would
	 * not give us:
	 * <ul>
	 * <li>Backpressure: exactly one chunk is requested at a time (see
	 * {@code hookOnNext}), so we never buffer further ahead than the {@code InputStream}
	 * consumer actually reads.</li>
	 * <li>Cancellable {@link #close()}: closing the stream before it reaches EOF disposes
	 * the subscriber, which cancels the upstream {@link Flux}. For
	 * {@link OpenAiAudioSpeechModel}, that cancellation propagates down to the
	 * {@code Flux.generate} state disposer and closes the underlying OpenAI HTTP
	 * response/connection immediately, instead of leaking it until the response would
	 * otherwise have completed.</li>
	 * </ul>
	 */
	private static final class FluxInputStream extends InputStream {

		private static final byte[] END_OF_STREAM = new byte[0];

		private final BlockingQueue<Object> queue = new ArrayBlockingQueue<>(1);

		private final BaseSubscriber<byte[]> subscriber;

		private byte[] current = END_OF_STREAM;

		private int position = 0;

		private boolean finished = false;

		FluxInputStream(Flux<byte[]> source) {
			this.subscriber = new BaseSubscriber<byte[]>() {

				@Override
				protected void hookOnSubscribe(Subscription subscription) {
					request(1);
				}

				@Override
				protected void hookOnNext(byte[] chunk) {
					offer(chunk);
					request(1);
				}

				@Override
				protected void hookOnComplete() {
					offer(END_OF_STREAM);
				}

				@Override
				protected void hookOnError(Throwable error) {
					offer(error);
				}

			};
			source.subscribe(this.subscriber);
		}

		private void offer(Object item) {
			try {
				this.queue.put(item);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		@Override
		public int read() throws IOException {
			if (!ensureChunkAvailable()) {
				return -1;
			}
			return this.current[this.position++] & 0xFF;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (len == 0) {
				return 0;
			}
			if (!ensureChunkAvailable()) {
				return -1;
			}
			int available = this.current.length - this.position;
			int toCopy = Math.min(available, len);
			System.arraycopy(this.current, this.position, b, off, toCopy);
			this.position += toCopy;
			return toCopy;
		}

		private boolean ensureChunkAvailable() throws IOException {
			while (this.position >= this.current.length) {
				if (this.finished) {
					return false;
				}
				Object item;
				try {
					item = this.queue.take();
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IOException("Interrupted while waiting for the next audio chunk", e);
				}
				if (item == END_OF_STREAM) {
					this.finished = true;
					return false;
				}
				if (item instanceof Throwable error) {
					this.finished = true;
					throw new IOException("Audio stream failed", error);
				}
				this.current = (byte[]) item;
				this.position = 0;
			}
			return true;
		}

		@Override
		public void close() {
			this.subscriber.dispose();
		}

	}

}
