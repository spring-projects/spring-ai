package org.springframework.ai.model;

import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.generator.Module;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.*;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.springframework.core.KotlinDetector;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

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

	private Boolean isNullable(MemberScope<?, ?> member) {
		KProperty<?> kotlinProperty = getKotlinProperty(member);
		if (kotlinProperty != null) {
			return kotlinProperty.getReturnType().isMarkedNullable();
		}
		return null;
	}

	private String getPropertyName(MemberScope<?, ?> member) {
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

	private KProperty<?> getKotlinProperty(MemberScope<?, ?> member) {
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
