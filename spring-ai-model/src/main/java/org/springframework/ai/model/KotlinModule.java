/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.ai.model;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import com.github.victools.jsonschema.generator.FieldScope;
import com.github.victools.jsonschema.generator.MemberScope;
import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigPart;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KProperty;
import kotlin.reflect.KType;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.jspecify.annotations.Nullable;

import org.springframework.core.KotlinDetector;

public class KotlinModule implements Module {

	@Override
	public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
		SchemaGeneratorConfigPart<FieldScope> fieldConfigPart = builder.forFields();
		// SchemaGeneratorConfigPart<MethodScope> methodConfigPart = builder.forMethods();

		this.applyToConfigBuilderPart(fieldConfigPart);
		// this.applyToConfigBuilderPart(methodConfigPart);
	}

	private void applyToConfigBuilderPart(SchemaGeneratorConfigPart<?> configPart) {
		configPart.withNullableCheck(this::isNullable);
		configPart.withPropertyNameOverrideResolver(this::getPropertyName);
		configPart.withRequiredCheck(this::isRequired);
		configPart.withIgnoreCheck(this::shouldIgnore);
	}

	private @Nullable Boolean isNullable(MemberScope<?, ?> member) {
		KProperty<?> kotlinProperty = getKotlinProperty(member);
		if (kotlinProperty != null) {
			return kotlinProperty.getReturnType().isMarkedNullable();
		}
		return null;
	}

	private @Nullable String getPropertyName(MemberScope<?, ?> member) {
		KProperty<?> kotlinProperty = getKotlinProperty(member);
		if (kotlinProperty != null) {
			return kotlinProperty.getName();
		}
		return null;
	}

	private boolean isRequired(MemberScope<?, ?> member) {
		KProperty<?> kotlinProperty = getKotlinProperty(member);
		if (kotlinProperty != null) {
			KType returnType = kotlinProperty.getReturnType();
			boolean isNonNullable = !returnType.isMarkedNullable();

			Class<?> declaringClass = member.getDeclaringType().getErasedType();
			KClass<?> kotlinClass = JvmClassMappingKt.getKotlinClass(declaringClass);

			Set<String> constructorParamsWithoutDefault = getConstructorParametersWithoutDefault(kotlinClass);

			boolean isInConstructor = constructorParamsWithoutDefault.contains(kotlinProperty.getName());

			return isNonNullable && isInConstructor;
		}

		return false;
	}

	private boolean shouldIgnore(MemberScope<?, ?> member) {
		return member.getRawMember().isSynthetic(); // Ignore generated properties/methods
	}

	private @Nullable KProperty<?> getKotlinProperty(MemberScope<?, ?> member) {
		Class<?> declaringClass = member.getDeclaringType().getErasedType();
		if (KotlinDetector.isKotlinType(declaringClass)) {
			KClass<?> kotlinClass = JvmClassMappingKt.getKotlinClass(declaringClass);
			for (KProperty<?> prop : KClasses.getMemberProperties(kotlinClass)) {
				Field javaField = ReflectJvmMapping.getJavaField(prop);
				if (javaField != null && javaField.equals(member.getRawMember())) {
					return prop;
				}
			}
		}
		return null;
	}

	private Set<String> getConstructorParametersWithoutDefault(KClass<?> kotlinClass) {
		Set<String> paramsWithoutDefault = new HashSet<>();
		KFunction<?> primaryConstructor = KClasses.getPrimaryConstructor(kotlinClass);
		if (primaryConstructor != null) {
			primaryConstructor.getParameters().forEach(param -> {
				if (param.getKind() != KParameter.Kind.INSTANCE && !param.isOptional()) {
					String name = param.getName();
					if (name != null) {
						paramsWithoutDefault.add(name);
					}
				}
			});
		}

		return paramsWithoutDefault;
	}

}
