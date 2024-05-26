package org.springframework.ai.qwen.aot;

import org.springframework.ai.qwen.api.QWenAiApi;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

/**
 * The QWenAiRuntimeHints class is responsible for registering runtime hints for QWen AI
 * API classes.
 *
 * @author wb04307201
 */
public class QWenAiRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        var mcs = MemberCategory.values();
        for (var tr : findJsonAnnotatedClassesInPackage(QWenAiApi.class))
            hints.reflection().registerType(tr, mcs);
    }
}
