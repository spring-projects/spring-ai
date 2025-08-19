package org.springframework.ai.cohere.aot;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

/**
 * The CohereRuntimeHints class is responsible for registering runtime hints for Cohere AI
 * API classes.
 *
 * @author Ricken Bazolo
 */
public class CohereRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {
		var mcs = MemberCategory.values();

		for (var tr : findJsonAnnotatedClassesInPackage("org.springframework.ai.cohere")) {
			hints.reflection().registerType(tr, mcs);
		}
	}

}
