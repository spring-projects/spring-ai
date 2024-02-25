package org.springframework.ai.openai.aot;

import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

/**
 * The OpenAiRuntimeHints class is responsible for registering runtime hints for OpenAI
 * API classes.
 *
 * @author Josh Long
 * @author Christian Tzolov
 * @author Mark Pollack
 */
public class OpenAiRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		var mcs = MemberCategory.values();
		for (var tr : findJsonAnnotatedClassesInPackage(OpenAiApi.class))
			hints.reflection().registerType(tr, mcs);
	}

}
