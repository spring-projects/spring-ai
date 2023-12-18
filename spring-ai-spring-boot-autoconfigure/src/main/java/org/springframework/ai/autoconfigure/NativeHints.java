package org.springframework.ai.autoconfigure;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vertex.api.VertexAiApi;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/***
 * Native hints
 *
 * @author Josh Long
 */
public class NativeHints implements RuntimeHintsRegistrar {

	static final Logger log = LoggerFactory.getLogger(NativeHints.class);

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {

		for (var h : Set.of(new VertexAiHints(), new OpenAiHints(), new PdfReaderHints(), new KnuddelsHints()))
			h.registerHints(hints, classLoader);

		hints.resources().registerResource(new ClassPathResource("embedding/embedding-model-dimensions.properties"));
	}

	private static Set<TypeReference> findJsonAnnotatedClasses(Class<?> packageClass) {
		var packageName = packageClass.getPackageName();
		var classPathScanningCandidateComponentProvider = new ClassPathScanningCandidateComponentProvider(false);
		classPathScanningCandidateComponentProvider.addIncludeFilter(new AnnotationTypeFilter(JsonInclude.class));
		return classPathScanningCandidateComponentProvider.findCandidateComponents(packageName)
			.stream()
			.map(bd -> TypeReference.of(Objects.requireNonNull(bd.getBeanClassName())))
			.peek(tr -> {
				if (log.isDebugEnabled())
					log.debug("registering [" + tr.getName() + ']');
			})
			.collect(Collectors.toUnmodifiableSet());
	}

	static class VertexAiHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			var mcs = MemberCategory.values();
			for (var tr : findJsonAnnotatedClasses(VertexAiApi.class))
				hints.reflection().registerType(tr, mcs);
		}

	}

	static class OpenAiHints implements RuntimeHintsRegistrar {

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