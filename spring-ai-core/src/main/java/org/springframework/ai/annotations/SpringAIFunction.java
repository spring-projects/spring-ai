package org.springframework.ai.annotations;

import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation used to define functions for use in
 */
@Bean
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SpringAIFunction {

	String name();

	String description();

	Class classType();

}
