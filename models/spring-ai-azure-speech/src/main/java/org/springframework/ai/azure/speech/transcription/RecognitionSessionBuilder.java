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

import com.microsoft.cognitiveservices.speech.SpeechConfig;

/**
 * Creates one session's SDK objects, one step at a time, in acquisition order.
 * <p>
 * Each step returns the handle for the object it created and remembers that object for
 * the steps that build on it, so the model registers each handle independently without
 * having to know how the objects relate. Every step may block, so the model calls each
 * one through its own admission wrapper.
 *
 * @author Spring AI Contributors
 */
interface RecognitionSessionBuilder {

	/**
	 * Creates the audio stream format for the request's sample rate.
	 */
	AutoCloseable createStreamFormat();

	/**
	 * Creates the pull stream the SDK reads samples from.
	 * @param source the model-owned PCM payload
	 */
	AutoCloseable createPullStream(PcmAudioSource source);

	/**
	 * Creates the audio configuration wrapping the pull stream.
	 */
	AutoCloseable createAudioConfig();

	/**
	 * Creates the automatic language identification configuration. Called only when the
	 * request configures candidate languages.
	 */
	AutoCloseable createLanguageIdConfig();

	/**
	 * Creates the recognizer and subscribes the listener to its events.
	 * @param speechConfig the configuration this operation owns
	 */
	RecognizerHandle createRecognizer(SpeechConfig speechConfig);

	/**
	 * Attaches the request's phrase hints to the recognizer. Called only when the request
	 * configures phrases.
	 */
	AutoCloseable createPhraseList();

}
