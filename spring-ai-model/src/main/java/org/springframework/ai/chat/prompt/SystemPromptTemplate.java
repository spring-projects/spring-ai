/*
 * Copyright 2023-2024 the original author or authors.
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

import java.util.Map;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

public class SystemPromptTemplate extends PromptTemplate {

	public SystemPromptTemplate(String template) {
		super(template);
	}

	public SystemPromptTemplate(Resource resource) {
		super(resource);
	}

	private SystemPromptTemplate(String template, Map<String, Object> variables, TemplateRenderer renderer) {
		super(template, variables, renderer);
	}

	private SystemPromptTemplate(Resource resource, Map<String, Object> variables, TemplateRenderer renderer) {
		super(resource, variables, renderer);
	}

	@Override
	public Message createMessage() {
		return new SystemMessage(render());
	}

	@Override
	public Message createMessage(Map<String, Object> model) {
		return new SystemMessage(render(model));
	}

	@Override
	public Prompt create() {
		return new Prompt(new SystemMessage(render()));
	}

	@Override
	public Prompt create(Map<String, Object> model) {
		return new Prompt(new SystemMessage(render(model)));
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder extends PromptTemplate.Builder {

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

		@Override
		public SystemPromptTemplate build() {
			if (this.template != null && this.resource != null) {
				throw new IllegalArgumentException("Only one of template or resource can be set");
			}
			else if (this.resource != null) {
				return new SystemPromptTemplate(this.resource, this.variables, this.renderer);
			}
			else if (this.template != null) {
				return new SystemPromptTemplate(this.template, this.variables, this.renderer);
			}
			else {
				throw new IllegalStateException("Neither template nor resource is set");
			}
		}

	}

}
