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

package org.springframework.ai.azure.openai.aot;

import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatChoice;

import org.springframework.ai.aot.AiRuntimeHints;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * @author Christian Tzolov
 */
public class AzureOpenAiRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {

		var mcs = MemberCategory.values();

		hints.reflection().registerType(OpenAIClient.class, mcs);
		hints.reflection().registerType(OpenAIAsyncClient.class, mcs);

		// Register all com.azure.ai.openai.models.* classes
		AiRuntimeHints
			.findClassesInPackage(ChatChoice.class.getPackageName(), (metadataReader, metadataReaderFactory) -> true)
			.forEach(clazz -> hints.reflection().registerType(clazz, mcs));

		hints.proxies().registerJdkProxy(com.azure.ai.openai.implementation.OpenAIClientImpl.OpenAIClientService.class);

		try {
			var resolver = new PathMatchingResourcePatternResolver();
			for (var resourceMatch : resolver.getResources("/azure-ai-openai.properties"))
				hints.resources().registerResource(resourceMatch);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
