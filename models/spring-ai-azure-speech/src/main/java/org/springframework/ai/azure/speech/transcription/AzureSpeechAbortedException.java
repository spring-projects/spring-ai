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

import java.io.Serial;

/**
 * Internal control-flow signal: the operation was decided before the owner thread reached
 * this step, so the step must not run.
 * <p>
 * It never escapes to a caller; the owner thread catches it and lets the already-decided
 * outcome publish.
 *
 * @author Spring AI Contributors
 */
class AzureSpeechAbortedException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = 1L;

	AzureSpeechAbortedException(String message) {
		super(message);
	}

	@Override
	public synchronized Throwable fillInStackTrace() {
		return this;
	}

}
