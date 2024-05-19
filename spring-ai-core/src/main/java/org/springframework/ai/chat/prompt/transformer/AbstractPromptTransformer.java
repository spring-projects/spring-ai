/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.chat.prompt.transformer;

/**
 * AbstractPromptTransformer is an abstract class that provides a base implementation of
 * the PromptTransformer interface. It includes a name field and corresponding accessor
 * methods, as well as a default implementation for the transform method.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @since 1.0.0 M1
 */
public abstract class AbstractPromptTransformer implements PromptTransformer {

	private String name = getClass().getSimpleName();

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}
