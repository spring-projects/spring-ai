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
 * How far a registered resource has got towards release.
 * <p>
 * There are deliberately only two things that matter — not settled, and settled — plus
 * the two ways settling can turn out. A close task that has been submitted but has not
 * started, one the chain has not reached, and one actively blocked inside {@code close()}
 * are all {@link #PENDING}: nothing in the acceptance criteria depends on telling them
 * apart.
 *
 * @author Spring AI Contributors
 */
enum CloseState {

	/**
	 * The close attempt has not concluded.
	 */
	PENDING,

	/**
	 * {@code close()} returned normally.
	 */
	CLOSED,

	/**
	 * The close attempt concluded without releasing the resource.
	 */
	FAILED

}
