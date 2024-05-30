package org.springframework.ai.wenxin.aot;

import org.springframework.ai.wenxin.api.WenxinApi;
import org.springframework.ai.wenxin.api.WenxinAudioApi;
import org.springframework.ai.wenxin.api.WenxinImageApi;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

/**
 * @author lvchzh
 * @date 2024年05月14日 下午4:50
 * @description:
 */
public class WenxinRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {
		var mcs = MemberCategory.values();
		for (var tr : findJsonAnnotatedClassesInPackage(WenxinApi.class)) {
			hints.reflection().registerType(tr, mcs);
		}
		for (var tr : findJsonAnnotatedClassesInPackage(WenxinAudioApi.class)) {
			hints.reflection().registerType(tr, mcs);
		}
		for (var tr : findJsonAnnotatedClassesInPackage(WenxinImageApi.class)) {
			hints.reflection().registerType(tr, mcs);
		}
	}

}
