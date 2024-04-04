/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.reader.pdf.aot;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;

import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.resource;

class PdfReaderRuntimeHintsTests {

	@Test
	void registerHints() {
		RuntimeHints runtimeHints = new RuntimeHints();
		PdfReaderRuntimeHints pdfReaderRuntimeHints = new PdfReaderRuntimeHints();
		pdfReaderRuntimeHints.registerHints(runtimeHints, null);

		Assertions.assertThat(runtimeHints)
			.matches(resource().forResource("/org/apache/pdfbox/resources/glyphlist/zapfdingbats.txt"));
		Assertions.assertThat(runtimeHints)
			.matches(resource().forResource("/org/apache/pdfbox/resources/glyphlist/glyphlist.txt"));
		// Assertions.assertThat(runtimeHints).matches(resource().forResource("/org/apache/pdfbox/resources/afm/**"));
		// Assertions.assertThat(runtimeHints).matches(resource().forResource("/org/apache/pdfbox/resources/glyphlist/**"));
		// Assertions.assertThat(runtimeHints).matches(resource().forResource("/org/apache/pdfbox/resources/icc/**"));
		// Assertions.assertThat(runtimeHints).matches(resource().forResource("/org/apache/pdfbox/resources/text/**"));
		// Assertions.assertThat(runtimeHints).matches(resource().forResource("/org/apache/pdfbox/resources/ttf/**"));
		Assertions.assertThat(runtimeHints)
			.matches(resource().forResource("/org/apache/pdfbox/resources/version.properties"));
	}

}
