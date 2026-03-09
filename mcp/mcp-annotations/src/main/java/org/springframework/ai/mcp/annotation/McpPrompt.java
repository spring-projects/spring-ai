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

package org.springframework.ai.mcp.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;
import org.springframework.ai.mcp.annotation.context.MetaProvider;

/**
 * Marks a method as a MCP Prompt.
 *
 * @author Christian Tzolov
 * @author Vadzim Shurmialiou
 * @author Craig Walls
 */
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpPrompt {

	/**
	 * Unique identifier for the prompt
	 */
	String name() default "";

	/**
	 * Optional human-readable name of the prompt for display purposes.
	 */
	String title() default "";

	/**
	 * Optional human-readable description.
	 */
	String description() default "";

	/**
	 * Optional meta provider class that implements the MetaProvider interface. Used to
	 * provide additional metadata for the prompt. Defaults to {@link DefaultMetaProvider
	 * DefaultMetaProvider.class} if not specified.
	 */
	Class<? extends MetaProvider> metaProvider() default DefaultMetaProvider.class;

}
