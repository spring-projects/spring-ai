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

package org.springframework.ai.systemone.api.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.systemone.api.question.Noul;

/**
 * The answer to a {@link Noul}: a single truth value between {@code 0} and {@code 1},
 * where {@code 1} means yes, {@code 0} means no and {@code 0.5} means undecided.
 *
 * <p>
 * Noul answers deliberately carry no confidence statistic; the value itself already
 * expresses how sure the model is, so it is thresholded directly.
 *
 * @param value the truth value, between {@code 0} and {@code 1}
 * @author Christian Tzolov
 * @since 2.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NoulAnswer(@JsonProperty("noul") double value) implements Answer {

	@Override
	public AnswerType type() {
		return AnswerType.NOUL;
	}

	/**
	 * Tests the truth value against a threshold.
	 * @param threshold the inclusive lower bound above which the answer counts as yes
	 * @return {@code true} when {@code value >= threshold}
	 */
	public boolean isTrue(double threshold) {
		return this.value >= threshold;
	}

	/**
	 * @return {@code true} when the truth value is above {@code 0.5}
	 */
	public boolean isTrue() {
		return isTrue(0.5d);
	}
}
