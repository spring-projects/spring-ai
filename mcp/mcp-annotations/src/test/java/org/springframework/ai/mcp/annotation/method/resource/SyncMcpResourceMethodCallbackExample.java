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

package org.springframework.ai.mcp.annotation.method.resource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.ResourceContents;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.mockito.Mockito;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.adapter.ResourceAdapter;

/**
 * Example demonstrating how to use the {@link SyncMcpResourceMethodCallback} with
 * {@link McpResource} annotations.
 *
 * @author Christian Tzolov
 */
public final class SyncMcpResourceMethodCallbackExample {

	private SyncMcpResourceMethodCallbackExample() {
	}

	/**
	 * Example of how to register resource methods using the McpResourceMethodCallback.
	 */
	public static void main(String[] args) {
		// Create the resource provider
		UserProfileResourceProvider profileProvider = new UserProfileResourceProvider();

		// Map to store the resource handlers
		Map<String, BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult>> resourceHandlers = new HashMap<>();

		// Register all methods annotated with @McpResource
		for (Method method : UserProfileResourceProvider.class.getMethods()) {
			McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

			if (resourceAnnotation != null) {
				try {
					// Create a callback for the method using the Builder pattern
					BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
						.builder()
						.method(method)
						.bean(profileProvider)
						.resource(ResourceAdapter.asResource(resourceAnnotation))
						.build();

					// Register the callback with the URI pattern from the annotation
					String uriPattern = resourceAnnotation.uri();

					resourceHandlers.put(uriPattern, callback);

					// Print information about URI variables if present
					if (uriPattern.contains("{") && uriPattern.contains("}")) {
						System.out.println("  URI Template: " + uriPattern);
						System.out.println("  URI Variables: " + extractUriVariables(uriPattern));
					}

					System.out.println("Registered resource handler for URI pattern: " + uriPattern);
					System.out.println("  Name: " + resourceAnnotation.name());
					System.out.println("  Description: " + resourceAnnotation.description());
					System.out.println("  MIME Type: " + resourceAnnotation.mimeType());
					System.out.println();
				}
				catch (IllegalArgumentException e) {
					System.err
						.println("Failed to create callback for method " + method.getName() + ": " + e.getMessage());
				}
			}
		}

		// Example of using registered handlers
		if (!resourceHandlers.isEmpty()) {
			System.out.println("\nTesting resource handlers:");

			// Test a handler with a ReadResourceRequest
			testHandler(resourceHandlers, "user-profile://john", "Standard handler");

			// Test a handler with URI variables
			testHandler(resourceHandlers, "user-profile://jane", "URI variable handler");

			// Test a handler with multiple URI variables
			testHandler(resourceHandlers, "user-attribute://bob/email", "Multiple URI variables handler");

			// Test a handler with exchange and URI variable
			testHandler(resourceHandlers, "user-profile-exchange://alice", "Exchange with URI variable handler");

			// Test additional handlers
			testHandler(resourceHandlers, "user-status://john", "Status handler");
			testHandler(resourceHandlers, "user-location://jane", "Location handler");
			testHandler(resourceHandlers, "user-connections://bob", "Connections handler");
			testHandler(resourceHandlers, "user-notifications://alice", "Notifications handler");
			testHandler(resourceHandlers, "user-avatar://john", "Avatar handler");
		}
	}

	/**
	 * Helper method to test a resource handler.
	 */
	private static void testHandler(
			Map<String, BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult>> handlers,
			String uri, String description) {

		// Find a handler that matches the URI pattern
		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> handler = null;
		for (Map.Entry<String, BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult>> entry : handlers
			.entrySet()) {
			String pattern = entry.getKey();
			if (uriMatchesPattern(uri, pattern)) {
				handler = entry.getValue();
				System.out.println("\nTesting " + description + " with URI pattern: " + pattern);
				break;
			}
		}

		if (handler != null) {
			try {
				// Create a mock exchange and request
				McpSyncServerExchange exchange = createMockExchange();
				ReadResourceRequest request = new ReadResourceRequest(uri);

				// Execute the handler
				ReadResourceResult result = handler.apply(exchange, request);

				// Print the result
				System.out.println("Resource request result for " + request.uri() + ":");
				for (ResourceContents content : result.contents()) {
					if (content instanceof TextResourceContents) {
						System.out.println("  " + ((TextResourceContents) content).text());
					}
					else {
						System.out.println("  " + content);
					}
				}
			}
			catch (Exception e) {
				System.out.println("Error executing handler: " + e.getMessage());
				e.printStackTrace();
			}
		}
		else {
			System.out.println("\nNo handler found for URI: " + uri);
		}
	}

	/**
	 * Create a simple mock exchange for testing.
	 */
	private static McpSyncServerExchange createMockExchange() {
		// For testing purposes, we'll just pass null for the exchange
		// This works because our resource methods don't actually use the exchange
		return Mockito.mock(McpSyncServerExchange.class);
		// return null;
	}

	/**
	 * Extract URI variable names from a URI template.
	 */
	private static List<String> extractUriVariables(String uriTemplate) {
		List<String> variables = new ArrayList<>();
		Pattern pattern = Pattern.compile("\\{([^/]+?)\\}");
		Matcher matcher = pattern.matcher(uriTemplate);

		while (matcher.find()) {
			variables.add(matcher.group(1));
		}

		return variables;
	}

	/**
	 * Check if a URI matches a pattern with variables.
	 */
	private static boolean uriMatchesPattern(String uri, String pattern) {
		// If the pattern doesn't contain variables, do a direct comparison
		if (!pattern.contains("{")) {
			return uri.equals(pattern);
		}

		// Convert the pattern to a regex
		String regex = pattern.replaceAll("\\{[^/]+?\\}", "([^/]+?)");
		regex = regex.replace("/", "\\/");

		// Check if the URI matches the regex
		return Pattern.compile(regex).matcher(uri).matches();
	}

	/**
	 * A sample resource provider class with methods annotated with {@link McpResource}.
	 */
	public static class UserProfileResourceProvider {

		private final Map<String, Map<String, String>> userProfiles = new HashMap<>();

		public UserProfileResourceProvider() {
			// Initialize with some sample data
			Map<String, String> johnProfile = new HashMap<>();
			johnProfile.put("name", "John Smith");
			johnProfile.put("email", "john.smith@example.com");
			johnProfile.put("age", "32");
			johnProfile.put("location", "New York");

			Map<String, String> janeProfile = new HashMap<>();
			janeProfile.put("name", "Jane Doe");
			janeProfile.put("email", "jane.doe@example.com");
			janeProfile.put("age", "28");
			janeProfile.put("location", "London");

			Map<String, String> bobProfile = new HashMap<>();
			bobProfile.put("name", "Bob Johnson");
			bobProfile.put("email", "bob.johnson@example.com");
			bobProfile.put("age", "45");
			bobProfile.put("location", "Tokyo");

			Map<String, String> aliceProfile = new HashMap<>();
			aliceProfile.put("name", "Alice Brown");
			aliceProfile.put("email", "alice.brown@example.com");
			aliceProfile.put("age", "36");
			aliceProfile.put("location", "Sydney");

			this.userProfiles.put("john", johnProfile);
			this.userProfiles.put("jane", janeProfile);
			this.userProfiles.put("bob", bobProfile);
			this.userProfiles.put("alice", aliceProfile);
		}

		/**
		 * Resource method that takes a ReadResourceRequest parameter and URI variable.
		 */
		@McpResource(uri = "user-profile://{username}", name = "User Profile",
				description = "Provides user profile information for a specific user")
		public ReadResourceResult getUserProfile(ReadResourceRequest request, String username) {
			String profileInfo = formatProfileInfo(
					this.userProfiles.getOrDefault(username.toLowerCase(), new HashMap<>()));

			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", profileInfo)));
		}

		/**
		 * Resource method that takes URI variables directly as parameters. The URI
		 * template in the annotation defines the variables that will be extracted.
		 */
		@McpResource(uri = "user-profile://{username}", name = "User Details",
				description = "Provides user details for a specific user using URI variables")
		public ReadResourceResult getUserDetails(String username) {
			String profileInfo = formatProfileInfo(
					this.userProfiles.getOrDefault(username.toLowerCase(), new HashMap<>()));

			return new ReadResourceResult(
					List.of(new TextResourceContents("user-profile://" + username, "text/plain", profileInfo)));
		}

		/**
		 * Resource method that takes multiple URI variables as parameters.
		 */
		@McpResource(uri = "user-attribute://{username}/{attribute}", name = "User Attribute",
				description = "Provides a specific attribute from a user's profile")
		public ReadResourceResult getUserAttribute(String username, String attribute) {
			Map<String, String> profile = this.userProfiles.getOrDefault(username.toLowerCase(), new HashMap<>());
			String attributeValue = profile.getOrDefault(attribute, "Attribute not found");

			return new ReadResourceResult(
					List.of(new TextResourceContents("user-attribute://" + username + "/" + attribute, "text/plain",
							username + "'s " + attribute + ": " + attributeValue)));
		}

		/**
		 * Resource method that takes an exchange and URI variables.
		 */
		@McpResource(uri = "user-profile-exchange://{username}", name = "User Profile with Exchange",
				description = "Provides user profile information with server exchange context")
		public ReadResourceResult getProfileWithExchange(McpSyncServerExchange exchange, String username) {
			String profileInfo = formatProfileInfo(
					this.userProfiles.getOrDefault(username.toLowerCase(), new HashMap<>()));

			return new ReadResourceResult(List.of(new TextResourceContents("user-profile-exchange://" + username,
					"text/plain", "Profile with exchange for " + username + ": " + profileInfo)));
		}

		/**
		 * Resource method that takes a String URI variable parameter.
		 */
		@McpResource(uri = "user-connections://{username}", name = "User Connections",
				description = "Provides a list of connections for a specific user")
		public List<String> getUserConnections(String username) {
			// Generate a simple list of connections based on username
			return List.of(username + " is connected with Alice", username + " is connected with Bob",
					username + " is connected with Charlie");
		}

		/**
		 * Resource method that takes both McpSyncServerExchange, ReadResourceRequest and
		 * URI variable parameters.
		 */
		@McpResource(uri = "user-notifications://{username}", name = "User Notifications",
				description = "Provides notifications for a specific user")
		public List<ResourceContents> getUserNotifications(McpSyncServerExchange exchange, ReadResourceRequest request,
				String username) {
			// Generate notifications based on username
			String notifications = generateNotifications(username);

			return List.of(new TextResourceContents(request.uri(), "text/plain", notifications));
		}

		/**
		 * Resource method that returns a single ResourceContents with TEXT content type.
		 */
		@McpResource(uri = "user-status://{username}", name = "User Status",
				description = "Provides the current status for a specific user")
		public ResourceContents getUserStatus(ReadResourceRequest request, String username) {
			// Generate a simple status based on username
			String status = generateUserStatus(username);

			return new TextResourceContents(request.uri(), "text/plain", status);
		}

		/**
		 * Resource method that returns a single String with TEXT content type.
		 */
		@McpResource(uri = "user-location://{username}", name = "User Location",
				description = "Provides the current location for a specific user")
		public String getUserLocation(String username) {
			Map<String, String> profile = this.userProfiles.getOrDefault(username.toLowerCase(), new HashMap<>());

			// Extract location from profile data
			return profile.getOrDefault("location", "Location not available");
		}

		/**
		 * Resource method that returns a single String with BLOB content type. This
		 * demonstrates how a String can be treated as binary data.
		 */
		@McpResource(uri = "user-avatar://{username}", name = "User Avatar",
				description = "Provides a base64-encoded avatar image for a specific user", mimeType = "image/png")
		public String getUserAvatar(ReadResourceRequest request, String username) {
			// In a real implementation, this would be a base64-encoded image
			// For this example, we're just returning a placeholder string
			return "base64-encoded-avatar-image-for-" + username;
		}

		private String extractUsernameFromUri(String uri) {
			// Extract username from URI with custom schema (e.g., "user-profile://john")
			if (uri.contains("://")) {
				String[] schemaParts = uri.split("://");
				if (schemaParts.length > 1) {
					// Handle potential additional path segments after the username
					String[] pathParts = schemaParts[1].split("/");
					return pathParts[0].toLowerCase();
				}
			}
			// Fallback for old URI format or unexpected formats
			String[] parts = uri.split("/");
			return parts.length > 2 ? parts[2].toLowerCase() : "unknown";
		}

		private String formatProfileInfo(Map<String, String> profile) {
			if (profile.isEmpty()) {
				return "User profile not found";
			}

			StringBuilder sb = new StringBuilder();
			for (Map.Entry<String, String> entry : profile.entrySet()) {
				sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
			}
			return sb.toString().trim();
		}

		private String generateNotifications(String username) {
			// Simple logic to generate notifications
			return "You have 3 new messages\n" + "2 people viewed your profile\n" + "You have 1 new connection request";
		}

		private String generateUserStatus(String username) {
			// Simple logic to generate a status
			if (username.equals("john")) {
				return "🟢 Online";
			}
			else if (username.equals("jane")) {
				return "🟠 Away";
			}
			else if (username.equals("bob")) {
				return "⚪ Offline";
			}
			else if (username.equals("alice")) {
				return "🔴 Busy";
			}
			else {
				return "⚪ Offline";
			}
		}

	}

}
