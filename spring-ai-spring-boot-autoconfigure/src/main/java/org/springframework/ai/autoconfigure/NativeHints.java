package org.springframework.ai.autoconfigure;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import java.io.IOException;
import java.util.Set;

import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public class NativeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		new KnuddelsHints().registerHints(hints, classLoader);
		new PdfReaderHints().registerHints(hints, classLoader);
		hints.resources().registerResource(new ClassPathResource("embedding/embedding-model-dimensions.properties"));
	}

	static class KnuddelsHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			hints.resources().registerResource(new ClassPathResource("/com/knuddels/jtokkit/cl100k_base.tiktoken"));
		}

	}

	static class PdfReaderHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			try {

				var resolver = new PathMatchingResourcePatternResolver();

				var patterns = Set.of("/org/apache/pdfbox/resources/glyphlist/zapfdingbats.txt",
						"/org/apache/pdfbox/resources/glyphlist/glyphlist.txt", "/org/apache/fontbox/cmap/**",
						"/org/apache/pdfbox/resources/afm/**", "/org/apache/pdfbox/resources/glyphlist/**",
						"/org/apache/pdfbox/resources/icc/**", "/org/apache/pdfbox/resources/text/**",
						"/org/apache/pdfbox/resources/ttf/**", "/org/apache/pdfbox/resources/version.properties");

				for (var pattern : patterns)
					for (var resourceMatch : resolver.getResources(pattern))
						hints.resources().registerResource(resourceMatch);

			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}

		}

	}

}