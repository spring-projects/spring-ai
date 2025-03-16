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

package org.springframework.ai.chroma.vectorstore;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.converter.PineconeFilterExpressionConverter;

/**
 * Converts {@link Filter.Expression} into Chroma metadata filter expression format.
 * (https://docs.trychroma.com/usage-guide#using-where-filters)
 *
 * @author Christian Tzolov
 */
public class ChromaFilterExpressionConverter extends PineconeFilterExpressionConverter {

}
