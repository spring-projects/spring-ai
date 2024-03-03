package org.springframework.ai.ollama.aot;

import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

/**
 * The OllamaRuntimeHints class is responsible for registering runtime hints for Ollama AI
 * API classes.
 *
 * @author Josh Long
 * @author Christian Tzolov
 * @author Mark Pollack
 */
public class OllamaRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		var mcs = MemberCategory.values();
		for (var tr : findJsonAnnotatedClassesInPackage(OllamaApi.class))
			hints.reflection().registerType(tr, mcs);
		for (var tr : findJsonAnnotatedClassesInPackage(OllamaOptions.class))
			hints.reflection().registerType(tr, mcs);
	}

}
