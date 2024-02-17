package org.springframework.ai.autoconfigure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi;
import org.springframework.ai.bedrock.jurassic2.api.Ai21Jurassic2ChatBedrockApi;
import org.springframework.ai.bedrock.llama2.api.Llama2ChatBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
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
import java.lang.reflect.Executable;
import java.util.*;
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

		/*
		 * These hints register resources, NOT types, so no ClassNotFoundExceptions to
		 * worry about. If they break, we just eat the exception and move on. These 3
		 * things don't have autoconfigs, either, so it makes sense to stash them here.
		 * hints for every other kind of thing should be registered on the appropriate
		 * autoconfig, guarded by @ConditionalOnClass checks
		 */

		for (var h : Set.of(new PdfReaderHints(), new KnuddelsHints(), new SpringAiCoreHints())) {
			try {
				h.registerHints(hints, classLoader);
			}
			catch (Throwable throwable) {
				// don't care.
			}
		}

	}

	public static class KnuddelsHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			hints.resources().registerResource(new ClassPathResource("/com/knuddels/jtokkit/cl100k_base.tiktoken"));
		}

	}

	public static class PdfReaderHints implements RuntimeHintsRegistrar {

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

	public static class SpringAiCoreHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			for (var r : Set.of("antlr4/org/springframework/ai/vectorstore/filter/antlr4/Filters.g4",
					"embedding/embedding-model-dimensions.properties"))
				hints.resources().registerResource(new ClassPathResource(r));
		}

	}

	public static class StabilityAiHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			var mcs = MemberCategory.values();
			for (var tr : findJsonAnnotatedClasses(StabilityAiHints.class))
				hints.reflection().registerType(tr, mcs);

		}

	}

	private static boolean hasJacksonAnnotations(Class<?> type) {
		var hasAnnotation = false;
		var annotationsToFind = Set.of(JsonProperty.class, JsonInclude.class);

		for (var annotationToFind : annotationsToFind) {

			if (type.isAnnotationPresent(annotationToFind)) {
				hasAnnotation = true;
			}

			var executables = new HashSet<Executable>();
			executables.addAll(List.of(type.getMethods()));
			executables.addAll(List.of(type.getConstructors()));
			executables.addAll(List.of(type.getDeclaredConstructors()));

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

	private static Set<TypeReference> findJsonAnnotatedClasses(Class<?> packageClass) {
		var packageName = packageClass.getPackageName();
		var classPathScanningCandidateComponentProvider = new ClassPathScanningCandidateComponentProvider(false);
		classPathScanningCandidateComponentProvider.addIncludeFilter(new AnnotationTypeFilter(JsonInclude.class));
		classPathScanningCandidateComponentProvider.addIncludeFilter((metadataReader, metadataReaderFactory) -> {
			try {
				var clazz = Class.forName(metadataReader.getClassMetadata().getClassName());
				return (!discoverJacksonAnnotatedTypesFromRootType(clazz).isEmpty());
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

	public static class VertexAiHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			var mcs = MemberCategory.values();
			for (var tr : findJsonAnnotatedClasses(VertexAiApi.class))
				hints.reflection().registerType(tr, mcs);
		}

	}

	public static class OllamaHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			var mcs = MemberCategory.values();
			for (var tr : findJsonAnnotatedClasses(OllamaApi.class))
				hints.reflection().registerType(tr, mcs);
			for (var tr : findJsonAnnotatedClasses(OllamaOptions.class))
				hints.reflection().registerType(tr, mcs);
		}

	}

	public static class BedrockAiHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			var mcs = MemberCategory.values();
			for (var tr : findJsonAnnotatedClasses(Ai21Jurassic2ChatBedrockApi.class))
				hints.reflection().registerType(tr, mcs);
			for (var tr : findJsonAnnotatedClasses(CohereChatBedrockApi.class))
				hints.reflection().registerType(tr, mcs);
			for (var tr : findJsonAnnotatedClasses(CohereEmbeddingBedrockApi.class))
				hints.reflection().registerType(tr, mcs);
			for (var tr : findJsonAnnotatedClasses(Llama2ChatBedrockApi.class))
				hints.reflection().registerType(tr, mcs);
			for (var tr : findJsonAnnotatedClasses(TitanChatBedrockApi.class))
				hints.reflection().registerType(tr, mcs);
			for (var tr : findJsonAnnotatedClasses(TitanEmbeddingBedrockApi.class))
				hints.reflection().registerType(tr, mcs);
			for (var tr : findJsonAnnotatedClasses(AnthropicChatBedrockApi.class))
				hints.reflection().registerType(tr, mcs);
		}

	}

	public static class OpenAiHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			var mcs = MemberCategory.values();
			for (var tr : findJsonAnnotatedClasses(OpenAiApi.class))
				hints.reflection().registerType(tr, mcs);
		}

	}

}