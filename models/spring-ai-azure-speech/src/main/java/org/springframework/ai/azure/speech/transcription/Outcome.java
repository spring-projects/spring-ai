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

import org.jspecify.annotations.Nullable;

/**
 * What a transcription operation concluded, before it is published.
 * <p>
 * Exactly one of {@link #error()} and the segment snapshot is meaningful: an error
 * outcome returns no segments, by contract.
 *
 * @param error the failure to raise, or {@code null} for a successful outcome
 * @param segments the final segments, in recognition order
 * @param noMatchCount how many final results matched no speech
 * @author Spring AI Contributors
 */
record Outcome(@Nullable RuntimeException error, List<AzureSpeechSegment> segments, int noMatchCount) {

	Outcome {
		segments = List.copyOf(segments);
	}

	static Outcome success(List<AzureSpeechSegment> segments, int noMatchCount) {
		return new Outcome(null, segments, noMatchCount);
	}

	static Outcome failure(RuntimeException error) {
		return new Outcome(error, List.of(), 0);
	}

	boolean isFailure() {
		return this.error != null;
	}

}
