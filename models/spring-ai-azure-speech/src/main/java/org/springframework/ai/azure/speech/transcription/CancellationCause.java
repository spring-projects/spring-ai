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
 * Why the service reported a cancellation, reduced to the three cases the model treats
 * differently.
 *
 * @author Spring AI Contributors
 */
enum CancellationCause {

	/**
	 * The audio ran out. An ordinary, successful end of session.
	 */
	END_OF_STREAM,

	/**
	 * The service reported a failure. Always decisive, and always an error outcome.
	 */
	ERROR,

	/**
	 * Recognition was stopped by a caller. Successful when the model itself asked for the
	 * stop, an error otherwise.
	 */
	CANCELLED_BY_USER

}
