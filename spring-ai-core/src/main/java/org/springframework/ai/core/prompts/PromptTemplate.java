/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.core.prompts;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.antlr.runtime.Token;
import org.antlr.runtime.TokenStream;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.compiler.STLexer;

public class PromptTemplate extends AbstractPromptTemplate implements PromptTemplateInput {

	private String template;

	private TemplateFormat templateFormat = TemplateFormat.FSTRING;

	public PromptTemplate(String template) {
		super();
		this.template = template;
	}

	public PromptTemplate(String template, TemplateFormat templateFormat) {
		super();
		this.template = template;
		this.templateFormat = templateFormat;
	}


	public PromptTemplate(String template, OutputParser outputParser) {
		super(outputParser);
		this.template = template;
	}

	public PromptTemplate(String template, OutputParser outputParser, TemplateFormat templateFormat) {
		super(outputParser);
		this.template = template;
		this.templateFormat = templateFormat;
	}

	@Override
	public String formatAsString(Map<String, Object> inputVariables) {
		validate();
		// Only "F-String" for now
		ST st = new ST(this.template, '{', '}');
		for (Entry<String, Object> stringObjectEntry : inputVariables.entrySet()) {
			st.add(stringObjectEntry.getKey(), stringObjectEntry.getValue());
		}
		return st.render();
	}

	@Override
	public String getTemplate() {
		return this.template;
	}

	@Override
	public TemplateFormat getTemplateFormat() {
		return this.templateFormat;
	}

	protected Set<String> getInputVariables() {
		ST st = new ST(this.template, '{', '}');
		TokenStream tokens = st.impl.tokens;
		return IntStream.range(0, tokens.range())
			.mapToObj(tokens::get)
			.filter(token -> token.getType() == STLexer.ID)
			.map(Token::getText)
			.collect(Collectors.toSet());
	}

	public void validate() {
		try {
			ST st = new ST(this.template, '{', '}');
			// TODO is doing this test even necessary, if it parsed correctly in the ctor, there should be no issues.
			Set<String> inputVariables = getInputVariables();
			for (String inputVariable : inputVariables) {
				st.add(inputVariable, "foo");
			}
			st.render();
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("The template string is not valid.", ex);
		}
	}

}
