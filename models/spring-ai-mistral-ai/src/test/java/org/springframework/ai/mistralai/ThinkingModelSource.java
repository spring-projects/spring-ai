/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.mistralai;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.params.provider.EnumSource;

import org.springframework.ai.mistralai.api.MistralAiApi.ChatModel;

/**
 * @author Nicolas Krier
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@EnumSource(value = ChatModel.class, names = { "MAGISTRAL_MEDIUM", "MISTRAL_MEDIUM", "MISTRAL_SMALL" })
public @interface ThinkingModelSource {

}
