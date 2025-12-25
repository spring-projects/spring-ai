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

package org.springframework.ai.chat.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

/**
 * A template for creating prompts. It allows you to define a template string with
 * placeholders for variables, and then render the template with specific values for those
 * variables.
 */
public class PromptTemplate implements PromptTemplateActions, PromptTemplateMessageActions {

	private static final Logger log = LoggerFactory.getLogger(PromptTemplate.class);

	private static final TemplateRenderer DEFAULT_TEMPLATE_RENDERER = StTemplateRenderer.builder().build();

	/**
	 * If you're subclassing this class, re-consider using the built-in implementation
	 * together with the new PromptTemplateRenderer interface, designed to give you more
	 * flexibility and control over the rendering process.
	 */
	private final String template;

	private final Map<String, Object> variables = new HashMap<>();

	private final TemplateRenderer renderer;

	public PromptTemplate(Resource resource) {
		this(resource, new HashMap<>(), DEFAULT_TEMPLATE_RENDERER);
	}

	public PromptTemplate(String template) {
		this(template, new HashMap<>(), DEFAULT_TEMPLATE_RENDERER);
	}

	PromptTemplate(String template, Map<String, Object> variables, TemplateRenderer renderer) {
		Assert.hasText(template, "template cannot be null or empty");
		Assert.notNull(variables, "variables cannot be null");
		Assert.noNullElements(variables.keySet(), "variables keys cannot be null");
		Assert.notNull(renderer, "renderer cannot be null");

		this.template = template;
		this.variables.putAll(variables);
		this.renderer = renderer;
	}

	PromptTemplate(Resource resource, Map<String, Object> variables, TemplateRenderer renderer) {
		Assert.notNull(resource, "resource cannot be null");
		Assert.notNull(variables, "variables cannot be null");
		Assert.noNullElements(variables.keySet(), "variables keys cannot be null");
		Assert.notNull(renderer, "renderer cannot be null");

		try (InputStream inputStream = resource.getInputStream()) {
			this.template = StreamUtils.copyToString(inputStream, Charset.defaultCharset());
			Assert.hasText(this.template, "template cannot be null or empty");
		}
		catch (IOException ex) {
			throw new RuntimeException("Failed to read resource", ex);
		}
		this.variables.putAll(variables);
		this.renderer = renderer;
	}

	public void add(String name, Object value) {
		this.variables.put(name, value);
	}

	public String getTemplate() {
		return this.template;
	}

	// From PromptTemplateStringActions.

	@Override
	public String render() {
		// Process internal variables to handle Resources before rendering
		Map<String, @Nullable Object> processedVariables = new HashMap<>();
		for (Entry<String, Object> entry : this.variables.entrySet()) {
			if (entry.getValue() instanceof Resource resource) {
				processedVariables.put(entry.getKey(), renderResource(resource));
			}
			else {
				processedVariables.put(entry.getKey(), entry.getValue());
			}
		}
		return this.renderer.apply(this.template, processedVariables);
	}

	@Override
	public String render(Map<String, Object> additionalVariables) {
		Map<String, @Nullable Object> combinedVariables = new HashMap<>();
		Map<String, Object> mergedVariables = new HashMap<>(this.variables);
		// variables + additionalVariables => mergedVariables
		if (additionalVariables != null && !additionalVariables.isEmpty()) {
			mergedVariables.putAll(additionalVariables);
		}

		for (Entry<String, Object> entry : mergedVariables.entrySet()) {
			if (entry.getValue() instanceof Resource resource) {
				combinedVariables.put(entry.getKey(), renderResource(resource));
			}
			else {
				combinedVariables.put(entry.getKey(), entry.getValue());
			}
		}

		return this.renderer.apply(this.template, combinedVariables);
	}

	private String renderResource(Resource resource) {
		if (resource == null) {
			return "";
		}

		try {
			// Handle ByteArrayResource specially
			if (resource instanceof ByteArrayResource byteArrayResource) {
				return new String(byteArrayResource.getByteArray(), StandardCharsets.UTF_8);
			}
			// If the resource exists but is empty
			if (!resource.exists() || resource.contentLength() == 0) {
				return "";
			}
			// For other Resource types or as fallback
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			log.warn("Failed to render resource: {}", resource.getDescription(), e);
			return "[Unable to render resource: " + resource.getDescription() + "]";
		}
	}

	// From PromptTemplateMessageActions.

	@Override
	public Message createMessage() {
		return new UserMessage(render());
	}

	@Override
	public Message createMessage(List<Media> mediaList) {
		return UserMessage.builder().text(render()).media(mediaList).build();
	}

	@Override
	public Message createMessage(Map<String, Object> additionalVariables) {
		return new UserMessage(render(additionalVariables));
	}

	// From PromptTemplateActions.

	@Override
	public Prompt create() {
		return new Prompt(render(new HashMap<>()));
	}

	@Override
	public Prompt create(ChatOptions modelOptions) {
		return Prompt.builder().content(render(new HashMap<>())).chatOptions(modelOptions).build();
	}

	@Override
	public Prompt create(Map<String, Object> additionalVariables) {
		return new Prompt(render(additionalVariables));
	}

	@Override
	public Prompt create(Map<String, Object> additionalVariables, ChatOptions modelOptions) {
		return Prompt.builder().content(render(additionalVariables)).chatOptions(modelOptions).build();
	}

	public Builder mutate() {
		return new Builder().template(this.template).variables(this.variables).renderer(this.renderer);
	}

	// Builder

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		protected @Nullable String template;

		protected @Nullable Resource resource;

		protected Map<String, Object> variables = new HashMap<>();

		protected TemplateRenderer renderer = DEFAULT_TEMPLATE_RENDERER;

		protected Builder() {
		}

		public Builder template(String template) {
			Assert.hasText(template, "template cannot be null or empty");
			this.template = template;
			return this;
		}

		public Builder resource(Resource resource) {
			Assert.notNull(resource, "resource cannot be null");
			this.resource = resource;
			return this;
		}

		public Builder variables(Map<String, Object> variables) {
			Assert.notNull(variables, "variables cannot be null");
			Assert.noNullElements(variables.keySet(), "variables keys cannot be null");
			this.variables = variables;
			return this;
		}

		public Builder renderer(TemplateRenderer renderer) {
			Assert.notNull(renderer, "renderer cannot be null");
			this.renderer = renderer;
			return this;
		}

		public PromptTemplate build() {
			if (this.template != null && this.resource != null) {
				throw new IllegalArgumentException("Only one of template or resource can be set");
			}
			else if (this.resource != null) {
				return new PromptTemplate(this.resource, this.variables, this.renderer);
			}
			else if (this.template != null) {
				return new PromptTemplate(this.template, this.variables, this.renderer);
			}
			else {
				throw new IllegalStateException("Neither template nor resource is set");
			}
		}

	}

}
