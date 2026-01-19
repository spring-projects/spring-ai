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

import java.io.IOException;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

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
	public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
		try {

			var resolver = new PathMatchingResourcePatternResolver();

			var patterns = Set.of("/org/apache/pdfbox/resources/glyphlist/zapfdingbats.txt",
					"/org/apache/pdfbox/resources/glyphlist/glyphlist.txt", "/org/apache/fontbox/cmap/**",
					"/org/apache/pdfbox/resources/afm/**", "/org/apache/pdfbox/resources/glyphlist/**",
					"/org/apache/pdfbox/resources/icc/**", "/org/apache/pdfbox/resources/text/**",
					"/org/apache/pdfbox/resources/ttf/**", "/org/apache/pdfbox/resources/version.properties");

			for (var pattern : patterns) {
				for (var resourceMatch : resolver.getResources(pattern)) {
					hints.resources().registerResource(resourceMatch);
				}
			}

		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

}
