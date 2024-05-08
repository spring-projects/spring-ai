/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import org.antlr.runtime.Token;
import org.antlr.runtime.TokenStream;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.compiler.STLexer;

import org.springframework.ai.chat.messages.Media;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.parser.OutputParser;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

public class PromptTemplate implements PromptTemplateActions, PromptTemplateMessageActions {

	private ST st;

	private Map<String, Object> dynamicModel = new HashMap<>();

	protected String template;

	protected TemplateFormat templateFormat = TemplateFormat.ST;

	private OutputParser outputParser;

	private StructuredOutputConverter structuredOutputConverter;

	public PromptTemplate(Resource resource) {
		try (InputStream inputStream = resource.getInputStream()) {
			this.template = StreamUtils.copyToString(inputStream, Charset.defaultCharset());
		}
		catch (IOException ex) {
			throw new RuntimeException("Failed to read resource", ex);
		}
		try {
			this.st = new ST(this.template, '{', '}');
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("The template string is not valid.", ex);
		}
	}

	public PromptTemplate(String template) {
		this.template = template;
		// If the template string is not valid, an exception will be thrown
		try {
			this.st = new ST(this.template, '{', '}');
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("The template string is not valid.", ex);
		}
	}

	public PromptTemplate(String template, Map<String, Object> model) {
		this.template = template;
		// If the template string is not valid, an exception will be thrown
		try {
			this.st = new ST(this.template, '{', '}');
			for (Entry<String, Object> entry : model.entrySet()) {
				add(entry.getKey(), entry.getValue());
				dynamicModel.put(entry.getKey(), entry.getValue());
			}
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("The template string is not valid.", ex);
		}
	}

	public PromptTemplate(Resource resource, Map<String, Object> model) {
		try (InputStream inputStream = resource.getInputStream()) {
			this.template = StreamUtils.copyToString(inputStream, Charset.defaultCharset());
		}
		catch (IOException ex) {
			throw new RuntimeException("Failed to read resource", ex);
		}
		// If the template string is not valid, an exception will be thrown
		try {
			this.st = new ST(this.template, '{', '}');
			for (Entry<String, Object> entry : model.entrySet()) {
				add(entry.getKey(), entry.getValue());
				dynamicModel.put(entry.getKey(), entry.getValue());
			}
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("The template string is not valid.", ex);
		}
	}

	/**
	 * @deprecated Use {@link #getOutputConverter()} instead.
	 */
	public OutputParser getOutputParser() {
		return this.outputParser;
	}

	/**
	 * @deprecated Use {@link #setOutputConverter(StructuredOutputConverter)} instead.
	 */
	public void setOutputParser(OutputParser outputParser) {
		Objects.requireNonNull(outputParser, "Output Parser can not be null");
		this.outputParser = outputParser;
	}

	public StructuredOutputConverter getOutputConverter() {
		return this.structuredOutputConverter;
	}

	public void setOutputConverter(StructuredOutputConverter structuredOutputConverter) {
		Objects.requireNonNull(structuredOutputConverter, "Structured Output Converter can not be null");
		this.structuredOutputConverter = structuredOutputConverter;
	}

	public void add(String name, Object value) {
		this.st.add(name, value);
		this.dynamicModel.put(name, value);
	}

	public String getTemplate() {
		return this.template;
	}

	public TemplateFormat getTemplateFormat() {
		return this.templateFormat;
	}

	// Render Methods
	@Override
	public String render() {
		validate(this.dynamicModel);
		return st.render();
	}

	@Override
	public String render(Map<String, Object> model) {
		validate(model);
		for (Entry<String, Object> entry : model.entrySet()) {
			if (st.getAttribute(entry.getKey()) != null) {
				st.remove(entry.getKey());
			}
			if (entry.getValue() instanceof Resource) {
				st.add(entry.getKey(), renderResource((Resource) entry.getValue()));
			}
			else {
				st.add(entry.getKey(), entry.getValue());
			}

		}
		return st.render();
	}

	private String renderResource(Resource resource) {
		try {
			return resource.getContentAsString(Charset.defaultCharset());
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Message createMessage() {
		return new UserMessage(render());
	}

	@Override
	public Message createMessage(List<Media> mediaList) {
		return new UserMessage(render(), mediaList);
	}

	@Override
	public Message createMessage(Map<String, Object> model) {
		return new UserMessage(render(model));
	}

	@Override
	public Prompt create() {
		return new Prompt(render(new HashMap<>()));
	}

	@Override
	public Prompt create(Map<String, Object> model) {
		return new Prompt(render(model));
	}

	public Set<String> getInputVariables() {
		TokenStream tokens = this.st.impl.tokens;
		Set<String> inputVariables = new HashSet<>();
		boolean isInsideList = false;

		for (int i = 0; i < tokens.size(); i++) {
			Token token = tokens.get(i);

			if (token.getType() == STLexer.LDELIM && i + 1 < tokens.size()
					&& tokens.get(i + 1).getType() == STLexer.ID) {
				if (i + 2 < tokens.size() && tokens.get(i + 2).getType() == STLexer.COLON) {
					inputVariables.add(tokens.get(i + 1).getText());
					isInsideList = true;
				}
			}
			else if (token.getType() == STLexer.RDELIM) {
				isInsideList = false;
			}
			else if (!isInsideList && token.getType() == STLexer.ID) {
				inputVariables.add(token.getText());
			}
		}

		return inputVariables;
	}

	private Set<String> getModelKeys(Map<String, Object> model) {
		Set<String> dynamicVariableNames = new HashSet<>(this.dynamicModel.keySet());
		Set<String> modelVariables = new HashSet<>(model.keySet());
		modelVariables.addAll(dynamicVariableNames);
		return modelVariables;
	}

	protected void validate(Map<String, Object> model) {

		Set<String> templateTokens = getInputVariables();
		Set<String> modelKeys = getModelKeys(model);

		// Check if model provides all keys required by the template
		if (!modelKeys.containsAll(templateTokens)) {
			templateTokens.removeAll(modelKeys);
			throw new IllegalStateException(
					"All template variables were not replaced. Missing variable names are " + templateTokens);
		}

		// Check if the template references any keys not provided by the model
		if (!templateTokens.containsAll(modelKeys)) {
			modelKeys.removeAll(templateTokens);
			throw new IllegalStateException(
					"All model variables were not replaced. Missing variable names are " + modelKeys);
		}
	}

}
