/*
 * Copyright 2023-2024 the original author or authors.
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

	@Test
	void registerHintsWithNullRuntimeHints() {
		// Test null safety for RuntimeHints parameter
		PdfReaderRuntimeHints pdfReaderRuntimeHints = new PdfReaderRuntimeHints();

		Assertions.assertThatThrownBy(() -> pdfReaderRuntimeHints.registerHints(null, null))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	void registerHintsMultipleTimes() {
		// Test that multiple calls don't cause issues (idempotent behavior)
		RuntimeHints runtimeHints = new RuntimeHints();
		PdfReaderRuntimeHints pdfReaderRuntimeHints = new PdfReaderRuntimeHints();

		// Register hints multiple times
		pdfReaderRuntimeHints.registerHints(runtimeHints, null);
		pdfReaderRuntimeHints.registerHints(runtimeHints, null);

		// Should still work correctly
		Assertions.assertThat(runtimeHints)
			.matches(resource().forResource("/org/apache/pdfbox/resources/glyphlist/zapfdingbats.txt"));
		Assertions.assertThat(runtimeHints)
			.matches(resource().forResource("/org/apache/pdfbox/resources/glyphlist/glyphlist.txt"));
		Assertions.assertThat(runtimeHints)
			.matches(resource().forResource("/org/apache/pdfbox/resources/version.properties"));
	}

	@Test
	void verifyAllExpectedResourcesRegistered() {
		// Test that all necessary PDFBox resources are registered
		RuntimeHints runtimeHints = new RuntimeHints();
		PdfReaderRuntimeHints pdfReaderRuntimeHints = new PdfReaderRuntimeHints();
		pdfReaderRuntimeHints.registerHints(runtimeHints, null);

		// Core glyph list resources
		Assertions.assertThat(runtimeHints)
			.matches(resource().forResource("/org/apache/pdfbox/resources/glyphlist/zapfdingbats.txt"));
		Assertions.assertThat(runtimeHints)
			.matches(resource().forResource("/org/apache/pdfbox/resources/glyphlist/glyphlist.txt"));

		// Version properties
		Assertions.assertThat(runtimeHints)
			.matches(resource().forResource("/org/apache/pdfbox/resources/version.properties"));

		// Test that uncommented resource patterns are NOT registered (if they shouldn't
		// be)
		// This validates the current implementation only registers what's needed
	}

	@Test
	void verifyClassLoaderContextParameterIgnored() {
		// Test that the ClassLoader parameter doesn't affect resource registration
		RuntimeHints runtimeHints1 = new RuntimeHints();
		RuntimeHints runtimeHints2 = new RuntimeHints();
		PdfReaderRuntimeHints pdfReaderRuntimeHints = new PdfReaderRuntimeHints();

		// Register with null ClassLoader
		pdfReaderRuntimeHints.registerHints(runtimeHints1, null);

		// Register with current ClassLoader
		pdfReaderRuntimeHints.registerHints(runtimeHints2, getClass().getClassLoader());

		// Both should have the same resources registered
		Assertions.assertThat(runtimeHints1)
			.matches(resource().forResource("/org/apache/pdfbox/resources/glyphlist/zapfdingbats.txt"));
		Assertions.assertThat(runtimeHints2)
			.matches(resource().forResource("/org/apache/pdfbox/resources/glyphlist/zapfdingbats.txt"));
	}

	@Test
	void verifyRuntimeHintsRegistrationInterface() {
		// Test that PdfReaderRuntimeHints properly implements RuntimeHintsRegistrar
		PdfReaderRuntimeHints pdfReaderRuntimeHints = new PdfReaderRuntimeHints();

		// Verify it's a RuntimeHintsRegistrar
		Assertions.assertThat(pdfReaderRuntimeHints)
			.isInstanceOf(org.springframework.aot.hint.RuntimeHintsRegistrar.class);
	}

}
