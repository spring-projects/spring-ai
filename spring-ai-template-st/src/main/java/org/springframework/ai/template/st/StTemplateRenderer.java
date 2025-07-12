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

package org.springframework.ai.template.st;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.runtime.Token;
import org.antlr.runtime.TokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.compiler.Compiler;
import org.stringtemplate.v4.compiler.STLexer;

import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.ValidationMode;
import org.springframework.util.Assert;

/**
 * Renders a template using the StringTemplate (ST) v4 library.
 *
 * <p>
 * This renderer allows customization of delimiters, validation behavior when template
 * variables are missing, and how StringTemplate's built-in functions are handled during
 * validation.
 *
 * <p>
 * Use the {@link #builder()} to create and configure instances.
 *
 * <p>
 * <b>Thread safety:</b> This class is safe for concurrent use. Each call to
 * {@link #apply(String, Map)} creates a new StringTemplate instance, and no mutable state
 * is shared between threads.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class StTemplateRenderer implements TemplateRenderer {

	private static final Logger logger = LoggerFactory.getLogger(StTemplateRenderer.class);

	private static final String VALIDATION_MESSAGE = "Not all variables were replaced in the template. Missing variable names are: %s.";

	private static final String DEFAULT_START_DELIMITER = "{";

	private static final String DEFAULT_END_DELIMITER = "}";

	private static final char INTERNAL_START_DELIMITER = '{';

	private static final char INTERNAL_END_DELIMITER = '}';

	/** Default validation mode: throw an exception if variables are missing */
	private static final ValidationMode DEFAULT_VALIDATION_MODE = ValidationMode.THROW;

	/** Default behavior: do not validate ST built-in functions */
	private static final boolean DEFAULT_VALIDATE_ST_FUNCTIONS = false;

	private final String startDelimiterToken;

	private final String endDelimiterToken;

	private final ValidationMode validationMode;

	private final boolean validateStFunctions;

	/**
	 * Constructs a StTemplateRenderer with custom delimiters, validation mode, and
	 * function validation flag.
	 * @param startDelimiterToken Multi-character start delimiter (non-null/non-empty)
	 * @param endDelimiterToken Multi-character end delimiter (non-null/non-empty)
	 * @param validationMode Mode for handling missing variables (non-null)
	 * @param validateStFunctions Whether to treat ST built-in functions as variables
	 */
	public StTemplateRenderer(String startDelimiterToken, String endDelimiterToken, ValidationMode validationMode,
			boolean validateStFunctions) {
		Assert.notNull(validationMode, "validationMode must not be null");
		Assert.hasText(startDelimiterToken, "startDelimiterToken must not be null or empty");
		Assert.hasText(endDelimiterToken, "endDelimiterToken must not be null or empty");

		this.startDelimiterToken = startDelimiterToken;
		this.endDelimiterToken = endDelimiterToken;
		this.validationMode = validationMode;
		this.validateStFunctions = validateStFunctions;
	}

	/**
	 * Renders the template by first converting custom delimiters to ST's native format,
	 * then replacing variables.
	 * @param template Template string with variables (non-null/non-empty)
	 * @param variables Map of variable names to values (non-null, keys must not be null)
	 * @return Rendered string with variables replaced
	 */
	@Override
	public String apply(String template, Map<String, Object> variables) {
		Assert.hasText(template, "template must not be null or empty");
		Assert.notNull(variables, "variables must not be null");
		Assert.noNullElements(variables.keySet(), "variables keys must not contain null");

		try {
			// Convert custom delimiters (e.g., <name>) to ST's native format ({name})
			String processedTemplate = preprocessTemplate(template);
			ST st = new ST(processedTemplate, INTERNAL_START_DELIMITER, INTERNAL_END_DELIMITER);

			// Add variables to the template
			variables.forEach(st::add);

			// Validate variable completeness if enabled
			if (validationMode != ValidationMode.NONE) {
				validate(st, variables);
			}

			// Render directly (no post-processing needed)
			return st.render();
		}
		catch (Exception e) {
			logger.error("Template rendering failed for template: {}", template, e);
			throw new RuntimeException("Failed to render template", e);
		}
	}

	/**
	 * Converts custom delimiter-wrapped variables (e.g., <name>) to ST's native format
	 * ({name}).
	 */
	private String preprocessTemplate(String template) {
		// Escape special regex characters in delimiters
		String escapedStart = Pattern.quote(startDelimiterToken);
		String escapedEnd = Pattern.quote(endDelimiterToken);

		// Regex pattern to match custom variables (e.g., <name> or {{name}})
		// Group 1 captures the variable name (letters, numbers, underscores)
		String variablePattern = escapedStart + "([a-zA-Z_][a-zA-Z0-9_]*)" + escapedEnd;

		// Replace with ST's native format (e.g., {name})
		return template.replaceAll(variablePattern, "{$1}");
	}

	/**
	 * Validates that all template variables have been provided in the variables map.
	 */
	private void validate(ST st, Map<String, Object> templateVariables) {
		Set<String> templateTokens = getInputVariables(st);
		Set<String> modelKeys = templateVariables != null ? templateVariables.keySet() : Collections.emptySet();
		Set<String> missingVariables = new HashSet<>(templateTokens);
		missingVariables.removeAll(modelKeys);

		if (!missingVariables.isEmpty()) {
			String message = VALIDATION_MESSAGE.formatted(missingVariables);
			if (validationMode == ValidationMode.WARN) {
				logger.warn(message);
			}
			else if (validationMode == ValidationMode.THROW) {
				throw new IllegalStateException(message);
			}
		}
	}

	/**
	 * Extracts variable names from the template using ST's token stream and regex
	 * validation.
	 */
	private Set<String> getInputVariables(ST st) {
		Set<String> inputVariables = new HashSet<>();
		TokenStream tokens = st.impl.tokens;
		boolean isInsideList = false;

		// Primary token-based extraction
		for (int i = 0; i < tokens.size(); i++) {
			Token token = tokens.get(i);

			// Handle list variables (e.g., {items; separator=", "})
			if (token.getType() == STLexer.LDELIM && i + 1 < tokens.size()
					&& tokens.get(i + 1).getType() == STLexer.ID) {
				if (i + 2 < tokens.size() && tokens.get(i + 2).getType() == STLexer.COLON) {
					String text = tokens.get(i + 1).getText();
					if (!Compiler.funcs.containsKey(text) || validateStFunctions) {
						inputVariables.add(text);
						isInsideList = true;
					}
				}
			}
			else if (token.getType() == STLexer.RDELIM) {
				isInsideList = false;
			}
			// Handle regular variables (exclude functions/properties)
			else if (!isInsideList && token.getType() == STLexer.ID) {
				boolean isFunctionCall = (i + 1 < tokens.size() && tokens.get(i + 1).getType() == STLexer.LPAREN);
				boolean isDotProperty = (i > 0 && tokens.get(i - 1).getType() == STLexer.DOT);
				if (!isFunctionCall && (!Compiler.funcs.containsKey(token.getText()) || validateStFunctions
						|| !(isDotProperty && Compiler.funcs.containsKey(token.getText())))) {
					inputVariables.add(token.getText());
				}
			}
		}

		// Secondary regex check to catch edge cases
		Pattern varPattern = Pattern.compile(Pattern.quote(String.valueOf(INTERNAL_START_DELIMITER))
				+ "([a-zA-Z_][a-zA-Z0-9_]*)" + Pattern.quote(String.valueOf(INTERNAL_END_DELIMITER)));
		Matcher matcher = varPattern.matcher(st.impl.template);
		while (matcher.find()) {
			inputVariables.add(matcher.group(1));
		}

		return inputVariables;
	}

	/**
	 * Creates a builder for configuring StTemplateRenderer instances.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for fluent configuration of StTemplateRenderer.
	 */
	public static final class Builder {

		private String startDelimiterToken = DEFAULT_START_DELIMITER;

		private String endDelimiterToken = DEFAULT_END_DELIMITER;

		private ValidationMode validationMode = DEFAULT_VALIDATION_MODE;

		private boolean validateStFunctions = DEFAULT_VALIDATE_ST_FUNCTIONS;

		private Builder() {
		}

		/**
		 * Sets the multi-character start delimiter (e.g., "{{" or "<").
		 */
		public Builder startDelimiterToken(String startDelimiterToken) {
			this.startDelimiterToken = startDelimiterToken;
			return this;
		}

		/**
		 * Sets the multi-character end delimiter (e.g., "}}" or ">").
		 */
		public Builder endDelimiterToken(String endDelimiterToken) {
			this.endDelimiterToken = endDelimiterToken;
			return this;
		}

		/**
		 * Sets the validation mode for missing variables.
		 */
		public Builder validationMode(ValidationMode validationMode) {
			this.validationMode = validationMode;
			return this;
		}

		/**
		 * Enables validation of ST built-in functions (treats them as variables).
		 */
		public Builder validateStFunctions() {
			this.validateStFunctions = true;
			return this;
		}

		/**
		 * Builds the configured StTemplateRenderer instance.
		 */
		public StTemplateRenderer build() {
			return new StTemplateRenderer(startDelimiterToken, endDelimiterToken, validationMode, validateStFunctions);
		}

	}

}
