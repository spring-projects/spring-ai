package org.springframework.ai.autoconfigure;

import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.fasterxml.jackson.annotation.JsonInclude;

/***
 * Native hints
 *
 * @author Josh Long
 */
public class NativeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		new KnuddelsHints().registerHints(hints, classLoader);
		new PdfReaderHints().registerHints(hints, classLoader);
		new OpenAiHints().registerHints(hints, classLoader);
		hints.resources().registerResource(new ClassPathResource("embedding/embedding-model-dimensions.properties"));
	}

	static class OpenAiHints implements RuntimeHintsRegistrar {

		private static Set<TypeReference> findJsonAnnotatedClasses(Class<?> packageClass) {
			var packageName = packageClass.getPackageName();
			var classPathScanningCandidateComponentProvider = new ClassPathScanningCandidateComponentProvider(false);
			classPathScanningCandidateComponentProvider.addIncludeFilter(new AnnotationTypeFilter(JsonInclude.class));
			return classPathScanningCandidateComponentProvider.findCandidateComponents(packageName)
				.stream()
				.map(bd -> TypeReference.of(Objects.requireNonNull(bd.getBeanClassName())))
				.collect(Collectors.toUnmodifiableSet());
		}

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			var mcs = MemberCategory.values();
			for (var tr : findJsonAnnotatedClasses(OpenAiApi.class))
				hints.reflection().registerType(tr, mcs);
		}

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