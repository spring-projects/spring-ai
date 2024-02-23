package org.springframework.ai.reader.pdf.aot;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;
import static org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection;
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
