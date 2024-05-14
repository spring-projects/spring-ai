/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.prompt.transformer;

/**
 * Responsible for transforming a Prompt. The PromptContext contains the necessary data to
 * make the transformation
 *
 * Implementations may retrieve data and modify the Prompt object in the PromptContext as
 * needed.
 *
 * @author Mark Pollack
 * @since 1.0 M1
 */
@FunctionalInterface
public interface PromptTransformer {

	/**
	 * Transforms the given PromptContext.
	 * @param context the PromptContext to transform
	 * @return the transformed PromptContext
	 */
	PromptContext transform(PromptContext context);

}
