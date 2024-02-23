package org.springframework.ai.reader.pdf.aot;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Set;

/**
 * The PdfReaderRuntimeHints class is responsible for registering runtime hints for PDFBox
 * resources.
 *
 * @author Josh Long
 * @author Christian Tzolov
 * @author Mark Pollack
 */
public class PdfReaderRuntimeHints implements RuntimeHintsRegistrar {

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
