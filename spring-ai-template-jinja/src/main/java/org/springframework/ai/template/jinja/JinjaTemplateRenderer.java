/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.template.jinja;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.JinjavaConfig;
import com.hubspot.jinjava.tree.parse.ExpressionToken;
import com.hubspot.jinjava.tree.parse.Token;
import com.hubspot.jinjava.tree.parse.TokenScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.ValidationMode;
import org.springframework.util.Assert;

/**
 * Renders a template using the Jin-java library.
 *
 * <p>
 * This renderer allows customization of validation behavior.
 *
 * <p>
 * Use the {@link #builder()} to create and configure instances.
 *
 * <p>
 * <b>Thread safety:</b> This class is safe for concurrent use. Each call to
 * {@link #apply(String, Map)} creates a new Jin-java instance, and no mutable state is
 * shared between threads.
 *
 * @author Sun Yuhan
 * @since 1.1.0
 */
public class JinjaTemplateRenderer implements TemplateRenderer {

	private static final Logger logger = LoggerFactory.getLogger(JinjaTemplateRenderer.class);

	private static final String VALIDATION_MESSAGE = "Not all variables were replaced in the template. Missing variable names are: %s.";

	private static final ValidationMode DEFAULT_VALIDATION_MODE = ValidationMode.THROW;

	private final ValidationMode validationMode;

	/**
	 * Constructs a new {@code JinjaTemplateRenderer} with the specified validation mode.
	 * @param validationMode the mode to use for template variable validation; must not be
	 * null
	 */
	protected JinjaTemplateRenderer(ValidationMode validationMode) {
		Assert.notNull(validationMode, "validationMode cannot be null");
		this.validationMode = validationMode;
	}

	@Override
	public String apply(String template, Map<String, Object> variables) {
		Assert.hasText(template, "template cannot be null or empty");
		Assert.notNull(variables, "variables cannot be null");
		Assert.noNullElements(variables.keySet(), "variables keys cannot be null");

		if (this.validationMode != ValidationMode.NONE) {
			validate(template, variables);
		}
		Jinjava jinjava = new Jinjava();
		String rendered;
		try {
			rendered = jinjava.render(template, variables);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("The template string is not valid.", ex);
		}
		return rendered;
	}

	/**
	 * Validates that all required template variables are provided in the model. Returns
	 * the set of missing variables for further handling or logging.
	 * @param template the template to be rendered
	 * @param templateVariables the provided variables
	 * @return set of missing variable names, or empty set if none are missing
	 */
	private Set<String> validate(String template, Map<String, Object> templateVariables) {
		Set<String> templateTokens = getInputVariables(template);
		Set<String> modelKeys = templateVariables.keySet();
		Set<String> missingVariables = new HashSet<>(templateTokens);
		missingVariables.removeAll(modelKeys);

		if (!missingVariables.isEmpty()) {
			if (this.validationMode == ValidationMode.WARN) {
				logger.warn(VALIDATION_MESSAGE.formatted(missingVariables));
			}
			else if (this.validationMode == ValidationMode.THROW) {
				throw new IllegalStateException(VALIDATION_MESSAGE.formatted(missingVariables));
			}
		}
		return missingVariables;
	}

	/**
	 * Retrieve all variables in the template
	 * @param template the template to be rendered
	 * @return set of variable names
	 */
	private Set<String> getInputVariables(String template) {
		Set<String> variables = new HashSet<>();
		JinjavaConfig config = JinjavaConfig.newBuilder().build();
		TokenScanner scanner = new TokenScanner(template, config);

		while (scanner.hasNext()) {
			Token token = scanner.next();
			if (token instanceof ExpressionToken expressionToken) {
				String varName = expressionToken.getExpr().trim();
				variables.add(varName);
			}
		}
		return variables;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for configuring and creating {@link JinjaTemplateRenderer} instances.
	 */
	public static final class Builder {

		private ValidationMode validationMode = DEFAULT_VALIDATION_MODE;

		private Builder() {
		}

		/**
		 * Sets the validation mode to control behavior when the provided variables do not
		 * match the variables required by the template. Default is
		 * {@link ValidationMode#THROW}.
		 * @param validationMode The desired validation mode.
		 * @return This builder instance for chaining.
		 */
		public Builder validationMode(ValidationMode validationMode) {
			this.validationMode = validationMode;
			return this;
		}

		/**
		 * Builds and returns a new {@link JinjaTemplateRenderer} instance with the
		 * configured settings.
		 * @return A configured {@link JinjaTemplateRenderer}.
		 */
		public JinjaTemplateRenderer build() {
			return new JinjaTemplateRenderer(this.validationMode);
		}

	}

}
