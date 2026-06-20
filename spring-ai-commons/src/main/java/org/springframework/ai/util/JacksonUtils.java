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

package org.springframework.ai.util;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.util.Assert;

/**
 * Utility methods for Jackson.
 *
 * <p>
 * The default {@link JsonMapper} returned by {@link #getDefaultJsonMapper} can be
 * customized via the automatic discovery of services of type {@link JacksonModule} by JDK
 * {@link java.util.ServiceLoader} facility, or replaced wholesale via
 * {@link #setDefaultJsonMapper}.
 *
 * @author Sebastien Deleuze
 */
public abstract class JacksonUtils {

	private static final AtomicReference<JsonMapper> defaultJsonMapper = new AtomicReference<>(
			buildDefaultJsonMapper());

	private static JsonMapper buildDefaultJsonMapper() {
		return JsonMapper.builder()
			.disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
			.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
			.addModules(JacksonUtils.instantiateAvailableModules())
			.build();
	}

	/**
	 * Return the Jackson modules found by {@link MapperBuilder#findModules(ClassLoader)}
	 * using JDK {@link java.util.ServiceLoader} facility.
	 * @return The list of instantiated modules.
	 */
	public static List<JacksonModule> instantiateAvailableModules() {
		return MapperBuilder.findModules(JacksonUtils.class.getClassLoader());
	}

	/**
	 * Returns the default Jackson {@link JsonMapper} instance. By default it disables
	 * {@link DeserializationFeature#FAIL_ON_TRAILING_TOKENS} and configures the Jackson
	 * modules found by {@link #instantiateAvailableModules}. The instance can be replaced
	 * via {@link #setDefaultJsonMapper}.
	 * @since 2.0.0
	 */
	public static JsonMapper getDefaultJsonMapper() {
		return defaultJsonMapper.get();
	}

	/**
	 * Replaces the default {@link JsonMapper} returned by {@link #getDefaultJsonMapper}.
	 * Spring AI's tool-calling autoconfiguration wires a user-overridable named
	 * {@code springAiJsonMapper} bean here, so applications can enable Jackson features
	 * (for example {@code JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS}) without forking
	 * Spring AI. {@link JsonHelper} instances created with the no-argument constructor
	 * pick up the new mapper on their next call.
	 * @since 2.0.0
	 */
	public static void setDefaultJsonMapper(JsonMapper jsonMapper) {
		Assert.notNull(jsonMapper, "jsonMapper cannot be null");
		defaultJsonMapper.set(jsonMapper);
	}

}
