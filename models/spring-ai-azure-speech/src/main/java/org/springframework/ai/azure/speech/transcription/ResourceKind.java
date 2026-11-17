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

/**
 * The kinds of resource one transcription operation can own.
 * <p>
 * Declaration order is acquisition order. The cleanup chain walks the registry in strict
 * reverse registration order, so a resource is always released before whatever it was
 * built from.
 *
 * @author Spring AI Contributors
 */
enum ResourceKind {

	/**
	 * The audio {@code InputStream} opened from the prompt's resource.
	 */
	SOURCE,

	/**
	 * The {@code SpeechConfig} produced by the configured factory, whose ownership
	 * transfers to the model.
	 */
	SPEECH_CONFIG,

	/**
	 * The {@code AudioStreamFormat} describing the PCM payload.
	 */
	STREAM_FORMAT,

	/**
	 * The {@code PullAudioInputStream} the SDK reads samples from.
	 */
	PULL_STREAM,

	/**
	 * The {@code AudioConfig} wrapping the pull stream.
	 */
	AUDIO_CONFIG,

	/**
	 * The {@code AutoDetectSourceLanguageConfig}, only when language identification is
	 * configured.
	 */
	LID_CONFIG,

	/**
	 * The {@code SpeechRecognizer}. The only kind whose close is gated on outstanding
	 * start and stop actions.
	 */
	RECOGNIZER,

	/**
	 * The {@code PhraseListGrammar}, only when phrase hints are configured.
	 */
	PHRASE_LIST

}
