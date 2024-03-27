package org.springframework.ai.bindings;

import org.springframework.cloud.bindings.Binding;
import org.springframework.cloud.bindings.Bindings;
import org.springframework.cloud.bindings.boot.BindingsPropertiesProcessor;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.util.Map;

/**
 * An implementation of {@link BindingsPropertiesProcessor} that detects {@link Binding}s
 * of type: {@value TYPE}.
 */
public class WeaviateBindingsPropertiesProcessor implements BindingsPropertiesProcessor {

	/**
	 * The {@link Binding} type that this processor is interested in: {@value}.
	 **/
	public static final String TYPE = "weaviate";

	@Override
	public void process(Environment environment, Bindings bindings, Map<String, Object> properties) {
		if (!BindingsValidator.isTypeEnabled(environment, TYPE)) {
			return;
		}

		bindings.filterBindings(TYPE).forEach(binding -> {
			var uri = URI.create(binding.getSecret().get("uri"));
			properties.put("spring.ai.vectorstore.weaviate.scheme", uri.getScheme());
			properties.put("spring.ai.vectorstore.weaviate.host", "%s:%s".formatted(uri.getHost(), uri.getPort()));
			properties.put("spring.ai.vectorstore.weaviate.api-key", binding.getSecret().get("api-key"));
		});
	}

}
