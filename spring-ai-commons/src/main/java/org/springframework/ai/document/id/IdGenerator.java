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

package org.springframework.ai.document.id;

/**
 * Interface for generating unique document IDs.
 *
 * @author Aliakbar Jafarpour
 * @author Christian Tzolov
 */
public interface IdGenerator {

	/**
	 * Generate a unique ID for the given content. Note: some generator, such as the
	 * random generator might not dependant on or use the content parameters.
	 * @param contents the content to generate an ID for.
	 * @return the generated ID.
	 */
	String generateId(Object... contents);

}
