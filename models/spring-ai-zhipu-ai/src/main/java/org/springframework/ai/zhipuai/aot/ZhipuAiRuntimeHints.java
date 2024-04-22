package org.springframework.ai.zhipuai.aot;

import org.springframework.ai.zhipuai.api.ZhipuAiApi;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

/**
 * The ZhipuAiRuntimeHints class is responsible for registering runtime hints for Zhipu AI
 * API classes.
 *
 * @author Ricken Bazolo
 */
public class ZhipuAiRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {
		var mcs = MemberCategory.values();
		for (var tr : findJsonAnnotatedClassesInPackage(ZhipuAiApi.class))
			hints.reflection().registerType(tr, mcs);
	}

}
