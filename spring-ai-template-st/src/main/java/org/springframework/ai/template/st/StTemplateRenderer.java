/*
 * Copyright 2023-present the original author or authors.
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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.antlr.runtime.tree.CommonTree;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.compiler.Compiler;
import org.stringtemplate.v4.compiler.STParser;

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
 * @author Sun Yuhan
 * @author Jewoo Shin
 * @since 1.0.0
 */
public class StTemplateRenderer implements TemplateRenderer {

	private static final Log logger = LogFactory.getLog(StTemplateRenderer.class);

	private static final String VALIDATION_MESSAGE = "Not all variables were replaced in the template. Missing variable names are: %s.";

	private static final Set<String> PREDEFINED_ANONYMOUS_SUBTEMPLATE_ARGUMENTS = Set.of("i", "i0");

	private static final char DEFAULT_START_DELIMITER_TOKEN = '{';

	private static final char DEFAULT_END_DELIMITER_TOKEN = '}';

	private static final ValidationMode DEFAULT_VALIDATION_MODE = ValidationMode.THROW;

	private static final boolean DEFAULT_VALIDATE_ST_FUNCTIONS = false;

	private final char startDelimiterToken;

	private final char endDelimiterToken;

	private final ValidationMode validationMode;

	private final boolean validateStFunctions;

	/**
	 * Constructs a new {@code StTemplateRenderer} with the specified delimiter tokens,
	 * validation mode, and function validation flag.
	 * @param startDelimiterToken the character used to denote the start of a template
	 * variable (e.g., '{')
	 * @param endDelimiterToken the character used to denote the end of a template
	 * variable (e.g., '}')
	 * @param validationMode the mode to use for template variable validation; must not be
	 * null
	 * @param validateStFunctions whether to validate StringTemplate functions in the
	 * template
	 */
	public StTemplateRenderer(char startDelimiterToken, char endDelimiterToken, ValidationMode validationMode,
			boolean validateStFunctions) {
		Assert.notNull(validationMode, "validationMode cannot be null");
		this.startDelimiterToken = startDelimiterToken;
		this.endDelimiterToken = endDelimiterToken;
		this.validationMode = validationMode;
		this.validateStFunctions = validateStFunctions;
	}

	@Override
	public String apply(String template, Map<String, ? extends @Nullable Object> variables) {
		Assert.hasText(template, "template cannot be null or empty");
		Assert.notNull(variables, "variables cannot be null");
		Assert.noNullElements(variables.keySet(), "variables keys cannot be null");

		ST st = createST(template);
		for (Map.Entry<String, ? extends @Nullable Object> entry : variables.entrySet()) {
			st.add(entry.getKey(), entry.getValue());
		}
		if (this.validationMode != ValidationMode.NONE) {
			validate(st, variables);
		}
		return st.render();
	}

	private ST createST(String template) {
		try {
			STGroup group = new STGroup(this.startDelimiterToken, this.endDelimiterToken);
			group.setListener(new CommonsLoggingStErrorListener(logger));
			return new ST(group, template);
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("The template string is not valid.", ex);
		}
	}

	/**
	 * Validates that all required template variables are provided in the model. Returns
	 * the set of missing variables for further handling or logging.
	 * @param st the StringTemplate instance
	 * @param templateVariables the provided variables
	 * @return set of missing variable names, or empty set if none are missing
	 */
	private Set<String> validate(ST st, Map<String, ? extends @Nullable Object> templateVariables) {
		Set<String> templateTokens = getInputVariables(st);
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

	private Set<String> getInputVariables(ST st) {
		Set<String> inputVariables = new HashSet<>();
		collectInputVariables(st.impl.ast, inputVariables, Set.of());
		return inputVariables;
	}

	private void collectInputVariables(CommonTree tree, Set<String> inputVariables, Set<String> localVariables) {
		if (tree == null) {
			return;
		}

		switch (tree.getType()) {
			case STParser.ID -> addInputVariable(tree.getText(), inputVariables, localVariables);
			case STParser.SUBTEMPLATE -> collectSubtemplateInputVariables(tree, inputVariables, localVariables);
			case STParser.ARGS -> {
				// Formal arguments are local to the anonymous subtemplate.
			}
			case STParser.PROP -> collectChildInputVariables(tree, 0, inputVariables, localVariables);
			case STParser.PROP_IND -> collectChildrenInputVariables(tree, inputVariables, localVariables);
			case STParser.EXEC_FUNC, STParser.INCLUDE, STParser.INCLUDE_SUPER, STParser.INCLUDE_REGION,
					STParser.INCLUDE_SUPER_REGION ->
				collectChildrenInputVariables(tree, 1, inputVariables, localVariables);
			case STParser.EQUALS -> collectChildrenInputVariables(tree, 1, inputVariables, localVariables);
			case STParser.OPTIONS -> collectOptionsInputVariables(tree, inputVariables, localVariables);
			default -> collectChildrenInputVariables(tree, inputVariables, localVariables);
		}
	}

	private void collectSubtemplateInputVariables(CommonTree tree, Set<String> inputVariables,
			Set<String> localVariables) {
		Set<String> subtemplateLocalVariables = new HashSet<>(localVariables);
		subtemplateLocalVariables.addAll(PREDEFINED_ANONYMOUS_SUBTEMPLATE_ARGUMENTS);

		for (int i = 0; i < tree.getChildCount(); i++) {
			CommonTree child = (CommonTree) tree.getChild(i);
			if (child.getType() == STParser.ARGS && child.getChildCount() > 0) {
				subtemplateLocalVariables.add(child.getChild(0).getText());
			}
		}

		for (int i = 0; i < tree.getChildCount(); i++) {
			CommonTree child = (CommonTree) tree.getChild(i);
			if (child.getType() != STParser.ARGS) {
				collectInputVariables(child, inputVariables, subtemplateLocalVariables);
			}
		}
	}

	private void collectOptionsInputVariables(CommonTree tree, Set<String> inputVariables, Set<String> localVariables) {
		for (int i = 0; i < tree.getChildCount(); i++) {
			CommonTree child = (CommonTree) tree.getChild(i);
			if (child.getType() == STParser.EQUALS) {
				collectInputVariables(child, inputVariables, localVariables);
			}
		}
	}

	private void collectChildrenInputVariables(CommonTree tree, Set<String> inputVariables,
			Set<String> localVariables) {
		collectChildrenInputVariables(tree, 0, inputVariables, localVariables);
	}

	private void collectChildrenInputVariables(CommonTree tree, int start, Set<String> inputVariables,
			Set<String> localVariables) {
		for (int i = start; i < tree.getChildCount(); i++) {
			collectChildInputVariables(tree, i, inputVariables, localVariables);
		}
	}

	private void collectChildInputVariables(CommonTree tree, int childIndex, Set<String> inputVariables,
			Set<String> localVariables) {
		if (childIndex < tree.getChildCount()) {
			collectInputVariables((CommonTree) tree.getChild(childIndex), inputVariables, localVariables);
		}
	}

	private void addInputVariable(String variableName, Set<String> inputVariables, Set<String> localVariables) {
		if (localVariables.contains(variableName)) {
			return;
		}
		if (!Compiler.funcs.containsKey(variableName) || this.validateStFunctions) {
			inputVariables.add(variableName);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for configuring and creating {@link StTemplateRenderer} instances.
	 */
	public static final class Builder {

		private char startDelimiterToken = DEFAULT_START_DELIMITER_TOKEN;

		private char endDelimiterToken = DEFAULT_END_DELIMITER_TOKEN;

		private ValidationMode validationMode = DEFAULT_VALIDATION_MODE;

		private boolean validateStFunctions = DEFAULT_VALIDATE_ST_FUNCTIONS;

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
		 * Configures the renderer to support StringTemplate's built-in functions during
		 * validation.
		 * <p>
		 * When enabled (set to true), identifiers in the template that match known ST
		 * function names (e.g., "first", "rest", "length") will not be treated as
		 * required input variables during validation.
		 * <p>
		 * When disabled (default, false), these identifiers are treated like regular
		 * variables and must be provided in the input map if validation is enabled
		 * ({@link ValidationMode#WARN} or {@link ValidationMode#THROW}).
		 * @return This builder instance for chaining.
		 */
		public Builder validateStFunctions() {
			this.validateStFunctions = true;
			return this;
		}

		/**
		 * Builds and returns a new {@link StTemplateRenderer} instance with the
		 * configured settings.
		 * @return A configured {@link StTemplateRenderer}.
		 */
		public StTemplateRenderer build() {
			return new StTemplateRenderer(this.startDelimiterToken, this.endDelimiterToken, this.validationMode,
					this.validateStFunctions);
		}

	}

}
