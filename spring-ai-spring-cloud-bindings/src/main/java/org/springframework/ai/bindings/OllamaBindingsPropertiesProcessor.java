package org.springframework.ai.bindings;

import org.springframework.cloud.bindings.Binding;
import org.springframework.cloud.bindings.Bindings;
import org.springframework.cloud.bindings.boot.BindingsPropertiesProcessor;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * An implementation of {@link BindingsPropertiesProcessor} that detects {@link Binding}s
 * of type: {@value TYPE}.
 *
 * @author Thomas Vitale
 */
public class OllamaBindingsPropertiesProcessor implements BindingsPropertiesProcessor {

	/**
	 * The {@link Binding} type that this processor is interested in: {@value}.
	 **/
	public static final String TYPE = "ollama";

	@Override
	public void process(Environment environment, Bindings bindings, Map<String, Object> properties) {
		if (!BindingsValidator.isTypeEnabled(environment, TYPE)) {
			return;
		}

		bindings.filterBindings(TYPE).forEach(binding -> {
			properties.put("spring.ai.ollama.base-url", binding.getSecret().get("uri"));
		});
	}

}
