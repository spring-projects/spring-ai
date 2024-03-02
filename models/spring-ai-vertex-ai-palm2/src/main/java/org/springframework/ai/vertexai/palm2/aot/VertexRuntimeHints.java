package org.springframework.ai.vertexai.palm2.aot;

import org.springframework.ai.vertexai.palm2.api.VertexAiPaLm2Api;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

/**
 * The VertexRuntimeHints class is responsible for registering runtime hints for Vertex AI
 * API classes.
 *
 * @author Josh Long
 * @author Christian Tzolov
 * @author Mark Pollack
 */
public class VertexRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		var mcs = MemberCategory.values();
		for (var tr : findJsonAnnotatedClassesInPackage(VertexAiPaLm2Api.class))
			hints.reflection().registerType(tr, mcs);
	}

}
