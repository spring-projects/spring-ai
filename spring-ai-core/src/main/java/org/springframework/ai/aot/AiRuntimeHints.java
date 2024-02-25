package org.springframework.ai.aot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Executable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Native runtime hints. See other modules for their respective native runtime hints.
 *
 * @author Josh Long
 * @author Christian Tzolov
 * @author Mark Pollack
 */
public class AiRuntimeHints {

	private static final Logger log = LoggerFactory.getLogger(AiRuntimeHints.class);

	public static Set<TypeReference> findJsonAnnotatedClassesInPackage(String packageName) {
		var classPathScanningCandidateComponentProvider = new ClassPathScanningCandidateComponentProvider(false);
		var annotationTypeFilter = new AnnotationTypeFilter(JsonInclude.class);
		classPathScanningCandidateComponentProvider.addIncludeFilter((metadataReader, metadataReaderFactory) -> {
			try {
				var clazz = Class.forName(metadataReader.getClassMetadata().getClassName());
				return annotationTypeFilter.match(metadataReader, metadataReaderFactory)
						|| !discoverJacksonAnnotatedTypesFromRootType(clazz).isEmpty();
			}
			catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		});
		return classPathScanningCandidateComponentProvider//
			.findCandidateComponents(packageName)//
			.stream()//
			.map(bd -> TypeReference.of(Objects.requireNonNull(bd.getBeanClassName())))//
			.peek(tr -> {
				if (log.isDebugEnabled())
					log.debug("registering [" + tr.getName() + ']');
			})
			.collect(Collectors.toUnmodifiableSet());

	}

	public static Set<TypeReference> findJsonAnnotatedClassesInPackage(Class<?> packageClass) {
		return findJsonAnnotatedClassesInPackage(packageClass.getPackageName());
	}

	private static boolean hasJacksonAnnotations(Class<?> type) {
		var hasAnnotation = false;
		var annotationsToFind = Set.of(JsonProperty.class, JsonInclude.class);
		for (var annotationToFind : annotationsToFind) {

			if (type.isAnnotationPresent(annotationToFind)) {
				hasAnnotation = true;
			}

			var executables = new HashSet<Executable>();
			executables.addAll(Set.of(type.getMethods()));
			executables.addAll(Set.of(type.getConstructors()));
			executables.addAll(Set.of(type.getDeclaredConstructors()));

			for (var executable : executables) {
				//
				if (executable.isAnnotationPresent(annotationToFind)) {
					hasAnnotation = true;
				}

				///
				for (var p : executable.getParameters()) {
					if (p.isAnnotationPresent(annotationToFind)) {
						hasAnnotation = true;
					}
				}
			}

			if (type.getRecordComponents() != null) {
				for (var r : type.getRecordComponents()) {
					if (r.isAnnotationPresent(annotationToFind)) {
						hasAnnotation = true;
					}
				}
			}

			for (var f : type.getFields()) {
				if (f.isAnnotationPresent(annotationToFind)) {
					hasAnnotation = true;
				}
			}
		}

		return hasAnnotation;
	}

	private static Set<Class<?>> discoverJacksonAnnotatedTypesFromRootType(Class<?> type) {
		var jsonTypes = new HashSet<Class<?>>();
		var classesToInspect = new HashSet<Class<?>>();
		classesToInspect.add(type);
		classesToInspect.addAll(Arrays.asList(type.getNestMembers()));
		for (var n : classesToInspect) {
			if (hasJacksonAnnotations(n)) {
				jsonTypes.add(n);
			}
		}
		return jsonTypes;
	}

}