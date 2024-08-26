package org.springframework.ai.vertexai.anthropic.aot;

import org.springframework.ai.vertexai.anthropic.VertexAiAnthropicChatModel;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

/**
 * The VertexAiAnthropicRuntimeHints class is responsible for registering runtime hints
 * for Vertex AI Anthropic API classes.
 *
 * @author Alessio Bertazzo
 * @since 1.0.0
 */
public class VertexAiAnthropicRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		var mcs = MemberCategory.values();
		for (var tr : findJsonAnnotatedClassesInPackage(VertexAiAnthropicChatModel.class)) {
			hints.reflection().registerType(tr, mcs);
		}
	}

}