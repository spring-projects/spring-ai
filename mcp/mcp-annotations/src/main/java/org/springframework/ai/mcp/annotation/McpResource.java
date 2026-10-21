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
 * <p>
 * For MCP App UI resources (SEP-1865, {@code ui://} URIs) the {@link #csp()},
 * {@link #permissions()}, {@link #domain()} and {@link #prefersBorder()} attributes
 * declare the UI resource metadata: the Content Security Policy of the UI, the sandbox
 * permissions it requests, the dedicated origin for the view and the visual boundary
 * preference. Per the specification this metadata is read by hosts from the UI resource
 * ({@code _meta.ui} of the {@code resources/read} content item; the current draft also
 * reads it from the {@code resources/list} entry), not from the tool. It is attached to
 * the read contents automatically only when the method returns {@code String} or
 * {@code List<String>} (see {@link #csp()}).
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

	/**
	 * Content Security Policy for the MCP App UI resource (SEP-1865). Declares which
	 * external origins the UI iframe is allowed to access. Hosts MUST NOT allow
	 * undeclared origins, SHOULD warn users when the UI requires external domain access
	 * and SHOULD log CSP configurations for security review. Opening links
	 * ({@code ui/open-link}) is host-mediated and is not governed by the CSP.
	 *
	 * <p>
	 * The CSP is emitted as {@code _meta.ui.csp} on the listed resource
	 * ({@code resources/list}) and on the content items returned by
	 * {@code resources/read}. Attached automatically to the read contents when the method
	 * returns {@code String} or {@code List<String>}; if it returns
	 * {@code ResourceContents} or {@code ReadResourceResult}, the method must set
	 * {@code _meta.ui.csp} on the contents itself. Only non-empty fields are emitted, and
	 * {@code csp} is omitted entirely when all fields are empty. It is merged with any
	 * {@code _meta} supplied by {@link #metaProvider()}; on conflict the
	 * {@code metaProvider} value wins (a {@code ui.csp} provided by the
	 * {@code metaProvider} replaces this attribute's CSP as a whole). Only relevant for
	 * {@code ui://} resources.
	 * @return the CSP declaration; the default declares nothing
	 */
	McpCsp csp() default @McpCsp;

	/**
	 * Sandbox permissions requested by the MCP App UI resource (SEP-1865). Servers
	 * declare which browser capabilities their UI needs. Hosts MAY honor these by setting
	 * appropriate iframe {@code allow} attributes. Apps SHOULD NOT assume permissions are
	 * granted; use JS feature detection as fallback.
	 *
	 * <p>
	 * The permissions are emitted as {@code _meta.ui.permissions} on the listed resource
	 * ({@code resources/list}) and on the content items returned by
	 * {@code resources/read}, each requested permission mapped to an empty object, e.g.
	 * {@code {"camera": {}, "clipboardWrite": {}}}. Duplicates are ignored, and
	 * {@code permissions} is omitted when none is requested. As with {@link #csp()}, it
	 * is attached automatically to the read contents only when the method returns
	 * {@code String} or {@code List<String>}. A {@code ui.permissions} supplied by the
	 * {@link #metaProvider()} replaces this attribute's permissions as a whole (the merge
	 * is per {@code ui} key, not per permission). Only relevant for {@code ui://}
	 * resources.
	 * @return the requested sandbox permissions; the default requests none
	 */
	McpPermission[] permissions() default {};

	/**
	 * Dedicated origin for the MCP App UI resource view (SEP-1865). Optional domain for
	 * the view's sandbox origin. Useful when views need stable, dedicated origins for
	 * OAuth callbacks, CORS policies, or API key allowlists. If omitted, the host uses
	 * the default sandbox origin (typically per-conversation).
	 *
	 * <p>
	 * The format and validation rules for this field are host-dependent: consult the
	 * host-specific documentation for the expected domain format. Common patterns include
	 * hash-based subdomains (e.g. {@code a904794854a047f6.claudemcpcontent.com}) and
	 * URL-derived subdomains (e.g. {@code www-example-com.oaiusercontent.com}). The value
	 * is therefore not validated and is emitted verbatim.
	 *
	 * <p>
	 * The domain is emitted as {@code _meta.ui.domain} on the listed resource
	 * ({@code resources/list}) and on the content items returned by
	 * {@code resources/read}, and is omitted when blank. As with {@link #csp()}, it is
	 * attached automatically to the read contents only when the method returns
	 * {@code String} or {@code List<String>}. A {@code ui.domain} supplied by the
	 * {@link #metaProvider()} wins over this attribute. Only relevant for {@code ui://}
	 * resources.
	 * @return the dedicated sandbox origin; the default declares none
	 */
	String domain() default "";

	/**
	 * Visual boundary preference of the MCP App UI resource (SEP-1865): whether a visible
	 * border and background is provided by the host. Specifying an explicit value for
	 * this is recommended because hosts' defaults may vary.
	 *
	 * <p>
	 * The preference is emitted as the boolean {@code _meta.ui.prefersBorder} on the
	 * listed resource ({@code resources/list}) and on the content items returned by
	 * {@code resources/read}: {@link PrefersBorder#TRUE} and {@link PrefersBorder#FALSE}
	 * are emitted as {@code true} and {@code false}, while {@link PrefersBorder#UNSET}
	 * omits the field so the host decides. As with {@link #csp()}, it is attached
	 * automatically to the read contents only when the method returns {@code String} or
	 * {@code List<String>}. A {@code ui.prefersBorder} supplied by the
	 * {@link #metaProvider()} wins over this attribute. Only relevant for {@code ui://}
	 * resources.
	 * @return the visual boundary preference; the default leaves it to the host
	 */
	PrefersBorder prefersBorder() default PrefersBorder.UNSET;

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
