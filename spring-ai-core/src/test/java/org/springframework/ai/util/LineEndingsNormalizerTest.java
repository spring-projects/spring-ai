/*
 * Copyright 2023 the original author or authors.
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
package org.springframework.ai.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.springframework.ai.util.LineEndingsNormalizer.requiresNormalization;
import static org.springframework.ai.util.StandardLineEnding.CR;
import static org.springframework.ai.util.StandardLineEnding.CRLF;
import static org.springframework.ai.util.StandardLineEnding.LF;

/**
 * @author Kirk Lund
 */
@ExtendWith(MockitoExtension.class)
class LineEndingsNormalizerTest {

	@Test
	void requiresNormalization_forLineEndingCR_returnsTrue_onWindows() {
		assumeThat(System.lineSeparator()).isEqualTo(CRLF.lineSeparator());

		boolean requiresNormalization = requiresNormalization(CR);

		assertThat(requiresNormalization).isTrue();
	}

	@Test
	void requiresNormalization_forLineEndingLF_returnsTrue_onWindows() {
		assumeThat(System.lineSeparator()).isEqualTo(CRLF.lineSeparator());

		boolean requiresNormalization = requiresNormalization(LF);

		assertThat(requiresNormalization).isTrue();
	}

	@Test
	void requiresNormalization_forLineEndingCRLF_returnsFalse_onWindows() {
		assumeThat(System.lineSeparator()).isEqualTo(CRLF.lineSeparator());

		boolean requiresNormalization = requiresNormalization(CRLF);

		assertThat(requiresNormalization).isFalse();
	}

	@Test
	void requiresNormalization_withExplicitCurrentAndNormalizedEndings() {
		for (StandardLineEnding current : StandardLineEnding.values()) {
			for (StandardLineEnding normalized : StandardLineEnding.values()) {
				boolean requiresNormalization = requiresNormalization(current, normalized);
				assertThat(requiresNormalization).isEqualTo(!current.equals(normalized));
			}
		}
	}

}