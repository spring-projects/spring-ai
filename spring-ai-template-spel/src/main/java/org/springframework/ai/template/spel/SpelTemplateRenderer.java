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

package org.springframework.ai.template.spel;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.ValidationMode;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.common.CompositeStringExpression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.DataBindingPropertyAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.Assert;

/**
 * Renders a template using the Spring SpEL.
 *
 * <p>
 * This renderer allows customization of delimiters, validation behavior when template
 * variables are missing.
 *
 * <p>
 * Use the {@link #builder()} to create and configure instances.
 *
 * <p>
 * <b>Thread safety:</b> This class is safe for concurrent use. Each call to
 * {@link #apply(String, Map)} creates a new SpEL Expression instance, and no mutable
 * state is shared between threads.
 *
 * @author Yanming Zhou
 * @since 1.1.0
 */
public class SpelTemplateRenderer implements TemplateRenderer {

	private static final Logger logger = LoggerFactory.getLogger(SpelTemplateRenderer.class);

	private static final String VALIDATION_MESSAGE = "Not all variables were replaced in the template. Missing variable names are: %s.";

	private static final char DEFAULT_START_DELIMITER_TOKEN = '{';

	private static final char DEFAULT_END_DELIMITER_TOKEN = '}';

	private static final ValidationMode DEFAULT_VALIDATION_MODE = ValidationMode.THROW;

	private final char startDelimiterToken;

	private final char endDelimiterToken;

	private final ValidationMode validationMode;

	private final EvaluationContext evaluationContext;

	/**
	 * Constructs a new {@code SpelTemplateRenderer} with the specified delimiter tokens,
	 * validation mode.
	 * @param startDelimiterToken the character used to denote the start of a template
	 * variable (e.g., '{')
	 * @param endDelimiterToken the character used to denote the end of a template
	 * variable (e.g., '}')
	 * @param validationMode the mode to use for template variable validation; must not be
	 * null template
	 */
	protected SpelTemplateRenderer(char startDelimiterToken, char endDelimiterToken, ValidationMode validationMode) {
		Assert.notNull(validationMode, "validationMode cannot be null");
		this.startDelimiterToken = startDelimiterToken;
		this.endDelimiterToken = endDelimiterToken;
		this.validationMode = validationMode;

		StandardEvaluationContext ctx = new StandardEvaluationContext();
		ctx.setPropertyAccessors(List.of(new MapAccessor(false), DataBindingPropertyAccessor.forReadOnlyAccess()));
		this.evaluationContext = ctx;
	}

	@Override
	public String apply(String template, Map<String, Object> variables) {
		Assert.hasText(template, "template cannot be null or empty");
		Assert.notNull(variables, "variables cannot be null");
		Assert.noNullElements(variables.keySet(), "variables keys cannot be null");

		Expression expression = parseExpression(template);

		if (this.validationMode != ValidationMode.NONE) {
			validate(expression, variables);
		}

		return String.valueOf(expression.getValue(this.evaluationContext, variables));
	}

	private Expression parseExpression(String template) {
		SpelExpressionParser parser = new SpelExpressionParser();
		return parser.parseExpression(template, new TemplateParserContext(String.valueOf(this.startDelimiterToken),
				String.valueOf(this.endDelimiterToken)));
	}

	/**
	 * Validates that all required template variables are provided in the model. Returns
	 * the set of missing variables for further handling or logging.
	 * @param expression the Expression instance
	 * @param templateVariables the provided variables
	 * @return set of missing variable names, or empty set if none are missing
	 */
	private Set<String> validate(Expression expression, Map<String, Object> templateVariables) {
		Set<String> templateTokens = getInputVariables(expression);
		Set<String> modelKeys = templateVariables.keySet();
		Set<String> missingVariables = new LinkedHashSet<>(templateTokens);
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

	public Set<String> getInputVariables(Expression expression) {
		Set<String> inputVariables = new LinkedHashSet<>();
		if (expression instanceof CompositeStringExpression cse) {
			for (Expression ex : cse.getExpressions()) {
				if (ex instanceof SpelExpression se) {
					inputVariables.add(se.getExpressionString());
				}
			}
		}

		return inputVariables;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for configuring and creating {@link SpelTemplateRenderer} instances.
	 */
	public static final class Builder {

		private char startDelimiterToken = DEFAULT_START_DELIMITER_TOKEN;

		private char endDelimiterToken = DEFAULT_END_DELIMITER_TOKEN;

		private ValidationMode validationMode = DEFAULT_VALIDATION_MODE;

		private Builder() {
		}

		/**
		 * Sets the character used as the start delimiter for template expressions.
		 * Default is '{'.
		 * @param startDelimiterToken The start delimiter character.
		 * @return This builder instance for chaining.
		 */
		public Builder startDelimiterToken(char startDelimiterToken) {
			this.startDelimiterToken = startDelimiterToken;
			return this;
		}

		/**
		 * Sets the character used as the end delimiter for template expressions. Default
		 * is '}'.
		 * @param endDelimiterToken The end delimiter character.
		 * @return This builder instance for chaining.
		 */
		public Builder endDelimiterToken(char endDelimiterToken) {
			this.endDelimiterToken = endDelimiterToken;
			return this;
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
		 * Builds and returns a new {@link SpelTemplateRenderer} instance with the
		 * configured settings.
		 * @return A configured {@link SpelTemplateRenderer}.
		 */
		public SpelTemplateRenderer build() {
			return new SpelTemplateRenderer(this.startDelimiterToken, this.endDelimiterToken, this.validationMode);
		}

	}

}
