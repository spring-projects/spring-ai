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

package org.springframework.ai.vectorstore;

/**
 * https://www.elastic.co/guide/en/elasticsearch/reference/master/dense-vector.html
 * max_inner_product is currently not supported because the distance value is not
 * normalized and would not comply with the requirement of being between 0 and 1
 *
 * @author Laura Trotta
 * @since 1.0.0
 */
public enum SimilarityFunction {

	l2_norm, dot_product, cosine

}
