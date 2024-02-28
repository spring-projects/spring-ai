package org.springframework.ai.watsonx.aot;

import org.springframework.ai.watsonx.api.WatsonxAIApi;
import org.springframework.ai.watsonx.api.WatsonxAIOptions;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;


/**
 * The WatsonxAIRuntimeHints class is responsible for registering runtime hints for Watsonx AI
 * API classes.
 *
 * @author Pablo Sanchidrian Herrera
 * @author John Jario Moreno Rojas
 */
public class WatsonxAIRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        var mcs = MemberCategory.values();
        for (var tr : findJsonAnnotatedClassesInPackage(WatsonxAIApi.class))
            hints.reflection().registerType(tr, mcs);

        for (var tr : findJsonAnnotatedClassesInPackage(WatsonxAIOptions.class))
            hints.reflection().registerType(tr, mcs);

    }

}
