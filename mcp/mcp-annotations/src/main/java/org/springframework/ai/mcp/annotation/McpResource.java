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

import io.modelcontextprotocol.spec.McpSchema.Role;

import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;
import org.springframework.ai.mcp.annotation.context.MetaProvider;

/**
 * Marks a method as a MCP Resource.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Vadzim Shurmialiou
 * @author Craig Walls
 */
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpResource {

	/**
	 * Intended for programmatic or logical use, but used as a display name in past specs
	 * or fallback (if title isn’t present).
	 */
	String name() default "";

	/**
	 * Optional human-readable name of the prompt for display purposes.
	 */
	String title() default "";

	/**
	 * the URI of the resource.
	 */
	String uri() default "";

	/**
	 * A description of what this resource represents. This can be used by clients to
	 * improve the LLM's understanding of available resources. It can be thought of like a
	 * "hint" to the model.
	 */
	String description() default "";

	/**
	 * The MIME type of this resource, if known.
	 */
	String mimeType() default "text/plain";

	/**
	 * Optional annotations for the client. Note: The default annotations value is
	 * ignored.
	 */
	McpAnnotations annotations() default @McpAnnotations(audience = { Role.USER }, lastModified = "", priority = 0.5);

	/**
	 * Optional meta provider class that supplies data for "_meta" field for this resource
	 * declaration. Defaults to {@link DefaultMetaProvider} implementation.
	 * @return the meta provider class to use for this resource
	 */
	Class<? extends MetaProvider> metaProvider() default DefaultMetaProvider.class;

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.ANNOTATION_TYPE)
	public @interface McpAnnotations {

		/**
		 * Describes who the intended customer of this object or data is. It can include
		 * multiple entries to indicate content useful for multiple audiences (e.g.,
		 * [“user”, “assistant”]).
		 */
		Role[] audience();

		/**
		 * The date and time (in ISO 8601 format) when the resource was last modified.
		 */
		String lastModified() default "";

		/**
		 * Describes how important this data is for operating the server.
		 *
		 * A value of 1 means “most important,” and indicates that the data is effectively
		 * required, while 0 means “least important,” and indicates that the data is
		 * entirely optional.
		 */
		double priority();

	}

}
