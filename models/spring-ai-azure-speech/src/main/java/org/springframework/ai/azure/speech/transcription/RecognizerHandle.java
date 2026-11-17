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

import java.util.concurrent.Future;

/**
 * The recognizer, behind the seam.
 * <p>
 * Both action methods submit to the SDK's own asynchronous executor and return
 * immediately; neither blocks the calling thread. The returned {@link Future} completes
 * only after the SDK has finished counting that action as active, which is what makes it
 * safe to {@link #close()} afterwards.
 *
 * @author Spring AI Contributors
 */
interface RecognizerHandle extends AutoCloseable {

	/**
	 * Submits a continuous-recognition start.
	 */
	Future<Void> startContinuousRecognition();

	/**
	 * Submits a continuous-recognition stop.
	 */
	Future<Void> stopContinuousRecognition();

	@Override
	void close() throws Exception;

}
