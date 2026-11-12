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
 * Named points at which a test can hold the owner thread, so that interleavings which are
 * otherwise a few instructions wide can be driven deterministically.
 * <p>
 * Each point widens a window the production code genuinely has; none creates a state the
 * production code cannot enter. In production every point is a no-op, because no
 * {@link PauseController} is installed.
 *
 * @author Spring AI Contributors
 */
enum PausePoint {

	/**
	 * After the recognizer is registered and before its start action is reserved.
	 */
	BEFORE_RESERVE_START,

	/**
	 * After the start action is reserved and before it is submitted to the SDK.
	 */
	AFTER_RESERVE_START,

	/**
	 * Inside the tracked start wait, after the start future has reached a terminal state
	 * and before the step decrements the in-flight count.
	 */
	AFTER_START_FUTURE_TERMINAL

}
