package org.springframework.ai.solar.aot;

import static org.springframework.ai.aot.AiRuntimeHints.*;

import org.springframework.ai.solar.api.SolarApi;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * The SolarRuntimeHints class is responsible for registering runtime hints for Solar API
 * classes.
 *
 * @author Seungheon Ji
 */
public class SolarRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {
		var mcs = MemberCategory.values();
		for (var tr : findJsonAnnotatedClassesInPackage(SolarApi.class)) {
			hints.reflection().registerType(tr, mcs);
		}
	}

}
