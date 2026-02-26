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

package org.springframework.ai.template.mustache;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.ValidationMode;
import org.springframework.util.Assert;

/**
 * Renders a template using the Mustache templating engine.
 *
 * <p>
 * This renderer supports Mustache syntax including:
 * <ul>
 * <li>Variable interpolation: {@code {{variable}}}</li>
 * <li>Sections (loops/conditionals): {@code {{#items}}...{{/items}}}</li>
 * <li>Inverted sections: {@code {{^items}}...{{/items}}}</li>
 * <li>Dot notation for nested properties: {@code {{object.property}}}</li>
 * <li>Comments: {@code {{! comment }}}</li>
 * </ul>
 *
 * <p>
 * Use the {@link #builder()} to create and configure instances.
 *
 * <p>
 * <b>Thread safety:</b> This class is safe for concurrent use. The underlying
 * {@link MustacheFactory} is thread-safe, and each call to {@link #apply(String, Map)}
 * compiles and executes a new Mustache template instance.
 *
 * @author Hyunjoon Park
 * @since 2.0.0
 * @see <a href="https://mustache.github.io/">Mustache Template Language</a>
 * @see <a href="https://github.com/spullara/mustache.java">Mustache.java</a>
 */
public class MustacheTemplateRenderer implements TemplateRenderer {

	private static final Logger logger = LoggerFactory.getLogger(MustacheTemplateRenderer.class);

	private static final String VALIDATION_MESSAGE = "Not all variables were replaced in the template. Missing variable names are: %s.";

	private static final ValidationMode DEFAULT_VALIDATION_MODE = ValidationMode.THROW;

	/**
	 * Pattern to extract section tags ({{#var}} or {{^var}}) from Mustache template.
	 */
	private static final Pattern SECTION_TAG_PATTERN = Pattern.compile("\\{\\{[#^]([a-zA-Z_][a-zA-Z0-9_]*)\\}\\}");

	/**
	 * Pattern to remove section blocks ({{#var}}...{{/var}} or {{^var}}...{{/var}}).
	 */
	private static final Pattern SECTION_BLOCK_PATTERN = Pattern
		.compile("\\{\\{[#^]([a-zA-Z_][a-zA-Z0-9_]*)\\}\\}.*?\\{\\{/\\1\\}\\}", Pattern.DOTALL);

	/**
	 * Pattern to extract simple variable tags ({{var}} or {{var.prop}}).
	 */
	private static final Pattern VARIABLE_PATTERN = Pattern
		.compile("\\{\\{([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*)\\}\\}");

	private final MustacheFactory mustacheFactory;

	private final ValidationMode validationMode;

	/**
	 * Constructs a new {@code MustacheTemplateRenderer} with the specified configuration.
	 * @param mustacheFactory the factory to use for compiling templates
	 * @param validationMode the mode to use for template variable validation
	 */
	MustacheTemplateRenderer(MustacheFactory mustacheFactory, ValidationMode validationMode) {
		Assert.notNull(mustacheFactory, "mustacheFactory cannot be null");
		Assert.notNull(validationMode, "validationMode cannot be null");
		this.mustacheFactory = mustacheFactory;
		this.validationMode = validationMode;
	}

	@Override
	public String apply(String template, Map<String, ? extends @Nullable Object> variables) {
		Assert.hasText(template, "template cannot be null or empty");
		Assert.notNull(variables, "variables cannot be null");
		Assert.noNullElements(variables.keySet(), "variables keys cannot be null");

		if (this.validationMode != ValidationMode.NONE) {
			validate(template, variables);
		}

		try {
			Mustache mustache = this.mustacheFactory.compile(new StringReader(template), "template");
			StringWriter writer = new StringWriter();
			mustache.execute(writer, variables).flush();
			return writer.toString();
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Failed to render Mustache template.", ex);
		}
	}

	/**
	 * Validates that all required template variables are provided in the variables map.
	 * @param template the template string
	 * @param variables the provided variables
	 * @return set of missing variable names, or empty set if none are missing
	 */
	private Set<String> validate(String template, Map<String, ? extends @Nullable Object> variables) {
		Set<String> templateVariables = extractVariables(template);
		Set<String> providedKeys = variables.keySet();
		Set<String> missingVariables = new HashSet<>(templateVariables);
		missingVariables.removeAll(providedKeys);

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
	 * Extracts top-level variable names from a Mustache template. For dot notation like
	 * {{user.name}}, only the top-level variable (user) is extracted. Variables inside
	 * section blocks are excluded as they reference properties of the section variable.
	 * @param template the Mustache template string
	 * @return set of top-level variable names
	 */
	private Set<String> extractVariables(String template) {
		Set<String> variables = new HashSet<>();

		// 1. Extract variables from section tags ({{#var}}, {{^var}})
		Matcher sectionMatcher = SECTION_TAG_PATTERN.matcher(template);
		while (sectionMatcher.find()) {
			variables.add(sectionMatcher.group(1));
		}

		// 2. Remove section blocks to avoid extracting inner variables
		String templateWithoutSections = removeSectionBlocks(template);

		// 3. Extract remaining variables from outside sections
		Matcher varMatcher = VARIABLE_PATTERN.matcher(templateWithoutSections);
		while (varMatcher.find()) {
			String fullPath = varMatcher.group(1);
			// Extract only the top-level variable name (before the first dot)
			String topLevelVariable = fullPath.contains(".") ? fullPath.substring(0, fullPath.indexOf('.')) : fullPath;
			variables.add(topLevelVariable);
		}

		return variables;
	}

	/**
	 * Removes section blocks from the template to avoid extracting inner variables.
	 * Handles nested sections by repeatedly removing innermost sections first.
	 * @param template the Mustache template string
	 * @return template with all section blocks removed
	 */
	private String removeSectionBlocks(String template) {
		String result = template;
		String previous;
		do {
			previous = result;
			result = SECTION_BLOCK_PATTERN.matcher(result).replaceAll("");
		}
		while (!result.equals(previous));
		return result;
	}

	/**
	 * Creates a new builder for {@link MustacheTemplateRenderer}.
	 * @return a new builder instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for configuring and creating {@link MustacheTemplateRenderer} instances.
	 */
	public static final class Builder {

		private MustacheFactory mustacheFactory = new DefaultMustacheFactory();

		private ValidationMode validationMode = DEFAULT_VALIDATION_MODE;

		private Builder() {
		}

		/**
		 * Sets a custom {@link MustacheFactory} for template compilation.
		 * <p>
		 * By default, a {@link DefaultMustacheFactory} is used.
		 * @param mustacheFactory the factory to use
		 * @return this builder instance for chaining
		 */
		public Builder mustacheFactory(MustacheFactory mustacheFactory) {
			Assert.notNull(mustacheFactory, "mustacheFactory cannot be null");
			this.mustacheFactory = mustacheFactory;
			return this;
		}

		/**
		 * Sets the validation mode to control behavior when the provided variables do not
		 * match the variables required by the template.
		 * <p>
		 * Default is {@link ValidationMode#THROW}.
		 * @param validationMode the desired validation mode
		 * @return this builder instance for chaining
		 */
		public Builder validationMode(ValidationMode validationMode) {
			Assert.notNull(validationMode, "validationMode cannot be null");
			this.validationMode = validationMode;
			return this;
		}

		/**
		 * Builds and returns a new {@link MustacheTemplateRenderer} instance with the
		 * configured settings.
		 * @return a configured {@link MustacheTemplateRenderer}
		 */
		public MustacheTemplateRenderer build() {
			return new MustacheTemplateRenderer(this.mustacheFactory, this.validationMode);
		}

	}

}
