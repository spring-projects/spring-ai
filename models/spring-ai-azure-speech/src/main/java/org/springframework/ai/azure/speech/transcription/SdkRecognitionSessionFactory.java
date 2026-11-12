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

package org.springframework.ai.azure.speech.transcription;

import java.util.List;
import java.util.concurrent.Future;

import com.microsoft.cognitiveservices.speech.AutoDetectSourceLanguageConfig;
import com.microsoft.cognitiveservices.speech.AutoDetectSourceLanguageResult;
import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.PhraseListGrammar;
import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionResult;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat;
import com.microsoft.cognitiveservices.speech.audio.PullAudioInputStream;
import com.microsoft.cognitiveservices.speech.audio.PullAudioInputStreamCallback;
import org.jspecify.annotations.Nullable;

/**
 * The one class that touches the Azure Speech SDK's recognition types.
 * <p>
 * It creates each object the model registers, subscribes to the recognizer's events, and
 * reduces each event to the plain values the model works with, so no SDK event type or
 * natively backed enumeration reaches the model's own logic.
 *
 * @author Spring AI Contributors
 */
class SdkRecognitionSessionFactory implements RecognitionSessionFactory {

	@Override
	public RecognitionSessionBuilder newSession(RecognitionRequest request, RecognitionListener listener) {
		return new SdkSessionBuilder(request, listener);
	}

	private static final class SdkSessionBuilder implements RecognitionSessionBuilder {

		private final RecognitionRequest request;

		private final RecognitionListener listener;

		private @Nullable AudioStreamFormat streamFormat;

		private @Nullable PullAudioInputStream pullStream;

		private @Nullable AudioConfig audioConfig;

		private @Nullable AutoDetectSourceLanguageConfig languageIdConfig;

		private @Nullable SpeechRecognizer recognizer;

		SdkSessionBuilder(RecognitionRequest request, RecognitionListener listener) {
			this.request = request;
			this.listener = listener;
		}

		@Override
		public AutoCloseable createStreamFormat() {
			AudioStreamFormat format = AudioStreamFormat.getWaveFormatPCM(this.request.sampleRate(), (short) 16,
					(short) 1);
			this.streamFormat = format;
			return format::close;
		}

		@Override
		public AutoCloseable createPullStream(PcmAudioSource source) {
			PullAudioInputStream stream = PullAudioInputStream.create(new SourceCallback(source),
					requireStreamFormat());
			this.pullStream = stream;
			return stream::close;
		}

		@Override
		public AutoCloseable createAudioConfig() {
			AudioConfig config = AudioConfig.fromStreamInput(requirePullStream());
			this.audioConfig = config;
			return config;
		}

		@Override
		public AutoCloseable createLanguageIdConfig() {
			List<String> candidates = this.request.options().getCandidateLanguages();
			AutoDetectSourceLanguageConfig config = AutoDetectSourceLanguageConfig
				.fromLanguages((candidates != null) ? candidates : List.of());
			this.languageIdConfig = config;
			return config;
		}

		@Override
		public RecognizerHandle createRecognizer(SpeechConfig speechConfig) {
			SpeechConfigOptionsApplier.apply(speechConfig, this.request.options());
			AudioConfig audio = requireAudioConfig();
			SpeechRecognizer created = (this.languageIdConfig != null)
					? new SpeechRecognizer(speechConfig, this.languageIdConfig, audio)
					: new SpeechRecognizer(speechConfig, audio);
			subscribe(created);
			this.recognizer = created;
			return new SdkRecognizerHandle(created);
		}

		@Override
		public AutoCloseable createPhraseList() {
			PhraseListGrammar grammar = PhraseListGrammar.fromRecognizer(requireRecognizer());
			List<String> phrases = this.request.options().getPhrases();
			if (phrases != null) {
				for (String phrase : phrases) {
					grammar.addPhrase(phrase);
				}
			}
			return grammar;
		}

		private void subscribe(SpeechRecognizer target) {
			boolean languageIdentification = this.request.options().isLanguageIdentificationEnabled();
			boolean detailed = this.request.options().isDetailedOutput();
			boolean wordTimestamps = this.request.options().isWordLevelTimestampsEnabled();

			target.recognized.addEventListener((sender, event) -> {
				SpeechRecognitionResult result = event.getResult();
				if (result.getReason() == ResultReason.NoMatch) {
					this.listener.noMatch();
					return;
				}
				if (result.getReason() != ResultReason.RecognizedSpeech) {
					return;
				}
				this.listener.recognized(toSegment(result, languageIdentification, detailed, wordTimestamps));
			});
			target.canceled.addEventListener((sender, event) -> {
				CancellationReason reason = event.getReason();
				if (reason == CancellationReason.EndOfStream) {
					this.listener.canceled(CancellationEvent.endOfStream(String.valueOf(reason)));
				}
				else if (reason == CancellationReason.CancelledByUser) {
					this.listener.canceled(CancellationEvent.cancelledByUser(String.valueOf(reason)));
				}
				else {
					this.listener.canceled(CancellationEvent.error(String.valueOf(reason),
							String.valueOf(event.getErrorCode()), event.getErrorDetails()));
				}
			});
			target.sessionStopped.addEventListener((sender, event) -> this.listener.sessionStopped());
		}

		/**
		 * Reads the plain values off an SDK result and hands them to the mapper, which is
		 * where the option-conditioned choice of optional fields is made and tested.
		 */
		private static AzureSpeechSegment toSegment(SpeechRecognitionResult result, boolean languageIdentification,
				boolean detailed, boolean wordTimestamps) {
			String json = result.getProperties().getProperty(PropertyId.SpeechServiceResponse_JsonResult);
			return AzureSpeechResultMapper.toSegment(result.getText(), result.getResultId(),
					AzureSpeechResultMapper.toDuration(result.getOffset()),
					AzureSpeechResultMapper.toDuration(result.getDuration()), json,
					() -> AutoDetectSourceLanguageResult.fromResult(result).getLanguage(), languageIdentification,
					wordTimestamps, detailed);
		}

		private AudioStreamFormat requireStreamFormat() {
			AudioStreamFormat format = this.streamFormat;
			if (format == null) {
				throw new IllegalStateException("the stream format must be created before the pull stream");
			}
			return format;
		}

		private PullAudioInputStream requirePullStream() {
			PullAudioInputStream stream = this.pullStream;
			if (stream == null) {
				throw new IllegalStateException("the pull stream must be created before the audio configuration");
			}
			return stream;
		}

		private AudioConfig requireAudioConfig() {
			AudioConfig config = this.audioConfig;
			if (config == null) {
				throw new IllegalStateException("the audio configuration must be created before the recognizer");
			}
			return config;
		}

		private SpeechRecognizer requireRecognizer() {
			SpeechRecognizer target = this.recognizer;
			if (target == null) {
				throw new IllegalStateException("the recognizer must be created before the phrase list");
			}
			return target;
		}

	}

	/**
	 * Bridges the SDK's pull callback to the model's own byte-counting audio source.
	 */
	private static final class SourceCallback extends PullAudioInputStreamCallback {

		private final PcmAudioSource source;

		SourceCallback(PcmAudioSource source) {
			this.source = source;
		}

		@Override
		public int read(byte[] dataBuffer) {
			return this.source.read(dataBuffer);
		}

		@Override
		public void close() {
			this.source.close();
		}

	}

	private record SdkRecognizerHandle(SpeechRecognizer recognizer) implements RecognizerHandle {

		@Override
		public Future<Void> startContinuousRecognition() {
			return this.recognizer.startContinuousRecognitionAsync();
		}

		@Override
		public Future<Void> stopContinuousRecognition() {
			return this.recognizer.stopContinuousRecognitionAsync();
		}

		@Override
		public void close() {
			this.recognizer.close();
		}

	}

}
