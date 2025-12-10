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

package org.springframework.ai.bedrock.aot;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.util.ClassUtils;

/**
 * The BedrockRuntimeHints class is responsible for registering runtime hints for Bedrock
 * AI API classes.
 *
 * @author Josh Long
 * @author Christian Tzolov
 * @author Mark Pollack
 * @author Wei Jiang
 */
public class BedrockRuntimeHints implements RuntimeHintsRegistrar {

	private final String rootPackage = "software.amazon.awssdk";

	private final Logger log = LoggerFactory.getLogger(BedrockRuntimeHints.class);

	private final MemberCategory[] memberCategories = MemberCategory.values();

	private final Collection<TypeReference> allClasses;

	private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

	BedrockRuntimeHints() {
		this.allClasses = this.find(this.rootPackage);
	}

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		try {
			this.registerBedrockRuntimeService(hints);
			this.registerSerializationClasses(hints);
			this.registerResources(hints);
		} //
		catch (Throwable ex) {
			this.log.warn("error when registering Bedrock types", ex);
		}
	}

	private void registerBedrockRuntimeService(RuntimeHints hints) {
		var pkg = this.rootPackage + ".services.bedrockruntime";
		var all = new HashSet<TypeReference>();
		for (var clzz : this.allClasses) {
			if (clzz.getName().contains("Bedrock") && clzz.getName().contains("Client")) {
				all.add(clzz);
			}
		}
		var modelPkg = pkg + ".model";
		all.addAll(this.find(modelPkg));
		all.forEach(tr -> hints.reflection().registerType(tr, this.memberCategories));
	}

	private void registerSerializationClasses(RuntimeHints hints) {
		for (var c : this.allClasses) {
			try {
				var serializableClass = ClassUtils.forName(c.getName(), getClass().getClassLoader());
				if (Serializable.class.isAssignableFrom(serializableClass)) {
					hints.reflection().registerType(serializableClass, this.memberCategories);
					hints.serialization().registerType(c);
				}
			} //
			catch (Throwable e) {
				//
			}
		}
	}

	private void registerResources(RuntimeHints hints) throws Exception {
		for (var resource : this.resolver.getResources("classpath*:software/amazon/awssdk/**/*.interceptors")) {
			hints.resources().registerResource(resource);
		}
		for (var resource : this.resolver.getResources("classpath*:software/amazon/awssdk/**/*.json")) {
			hints.resources().registerResource(resource);
		}
	}

	protected List<TypeReference> find(String packageName) {
		var scanner = new ClassPathScanningCandidateComponentProvider(false) {
			@Override
			protected boolean isCandidateComponent(MetadataReader metadataReader) throws IOException {
				return true;
			}

			@Override
			protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
				return true;
			}
		};
		return scanner //
			.findCandidateComponents(packageName) //
			.stream()//
			.map(BeanDefinition::getBeanClassName) //
			.filter(Objects::nonNull) //
			.filter(x -> !x.contains("package-info"))
			.map(TypeReference::of) //
			.toList();
	}

}
