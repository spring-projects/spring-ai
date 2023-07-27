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
import java.util.Optional;

public interface PromptInput {

	// *Output*

	Optional<OutputParser> getOutputParser();


	// *Input*

	// This is the handoff point.  These methods provide the "input", then the
	// "Template" is "rendered" and the output is then used to construct a "Message" that gets sent to the "LLM" model.

	// Maybe should be called String renderAsString()  and renderAsPrompt
	// View in spring mvc has render(Map<String,?> model, HttpServletRequest request, HttpServletResponse response) method.
	String formatAsString(Map<String, Object> inputVariables);

	PromptValue formatAsPrompt(Map<String, Object> inputVariables);

	// Leave out Partial Input Variables for now

}
