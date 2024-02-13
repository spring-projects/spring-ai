package org.springframework.ai.aot;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Native runtime hints. See other modules for their respective native runtime hints.
 *
 * @author Josh Long
 * @author Christian Tzolov
 * @author Mark Pollack
 */
public class AiRuntimeHints implements RuntimeHintsRegistrar {

	static final Logger log = LoggerFactory.getLogger(AiRuntimeHints.class);

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {

		for (var h : Set.of(new KnuddelsHints()))
			h.registerHints(hints, classLoader);

		hints.resources().registerResource(new ClassPathResource("embedding/embedding-model-dimensions.properties"));
	}

	static class KnuddelsHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			try {
				hints.resources().registerResource(new ClassPathResource("/com/knuddels/jtokkit/cl100k_base.tiktoken"));
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

	}

	public static Set<TypeReference> findJsonAnnotatedClasses(Class<?> packageClass) {
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

}
