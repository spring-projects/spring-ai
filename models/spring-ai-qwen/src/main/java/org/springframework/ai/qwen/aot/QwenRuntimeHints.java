package org.springframework.ai.qwen.aot;

import org.springframework.ai.aot.AiRuntimeHints;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

public class QwenRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {
		var mcs = MemberCategory.values();
		AiRuntimeHints
			.findClassesInPackage(com.alibaba.dashscope.Version.class.getPackageName(),
					(metadataReader, metadataReaderFactory) -> true)
			.forEach(clazz -> hints.reflection().registerType(clazz, mcs));
	}

}
