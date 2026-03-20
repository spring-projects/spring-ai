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

package org.springframework.ai.mcp.annotation.method.progress;

import java.util.function.Consumer;

import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;

import org.springframework.ai.mcp.annotation.McpProgress;

/**
 * Example demonstrating the usage of {@link SyncMcpProgressMethodCallback}.
 *
 * @author Christian Tzolov
 */
public final class SyncMcpProgressMethodCallbackExample {

	private SyncMcpProgressMethodCallbackExample() {
	}

	public static void main(String[] args) throws Exception {
		// Create the service instance
		ProgressService service = new ProgressService();

		// Build the callback for the notification method
		Consumer<ProgressNotification> notificationCallback = SyncMcpProgressMethodCallback.builder()
			.method(ProgressService.class.getMethod("handleProgressNotification", ProgressNotification.class))
			.bean(service)
			.build();

		// Build the callback for the params method
		Consumer<ProgressNotification> paramsCallback = SyncMcpProgressMethodCallback.builder()
			.method(ProgressService.class.getMethod("handleProgressWithParams", Double.class, String.class,
					String.class))
			.bean(service)
			.build();

		// Build the callback for the primitive method
		Consumer<ProgressNotification> primitiveCallback = SyncMcpProgressMethodCallback.builder()
			.method(ProgressService.class.getMethod("handleProgressPrimitive", double.class, String.class,
					String.class))
			.bean(service)
			.build();

		// Simulate progress notifications
		System.out.println("=== Progress Notification Example ===");

		// Start of operation
		ProgressNotification startNotification = new ProgressNotification("task-001", 0.0, 100.0,
				"Starting operation...");
		notificationCallback.accept(startNotification);

		// Progress updates
		ProgressNotification progressNotification1 = new ProgressNotification("task-001", 0.25, 100.0,
				"Processing batch 1...");
		paramsCallback.accept(progressNotification1);

		ProgressNotification progressNotification2 = new ProgressNotification("task-001", 0.5, 100.0,
				"Halfway through...");
		primitiveCallback.accept(progressNotification2);

		ProgressNotification progressNotification3 = new ProgressNotification("task-001", 0.75, 100.0,
				"Processing batch 3...");
		notificationCallback.accept(progressNotification3);

		// Completion
		ProgressNotification completeNotification = new ProgressNotification("task-001", 1.0, 100.0,
				"Operation completed successfully!");
		notificationCallback.accept(completeNotification);

		System.out.printf("%nTotal notifications handled: %d%n", service.getNotificationCount());
	}

	/**
	 * Example service that handles progress notifications.
	 */
	public static class ProgressService {

		private int notificationCount = 0;

		/**
		 * Handle progress notification with the full notification object.
		 * @param notification the progress notification
		 */
		@McpProgress(clients = "my-client-id")
		public void handleProgressNotification(ProgressNotification notification) {
			this.notificationCount++;
			System.out.printf("Progress Update #%d: Token=%s, Progress=%.2f%%, Total=%.0f, Message=%s%n",
					this.notificationCount, notification.progressToken(), notification.progress() * 100,
					notification.total(), notification.message());
		}

		/**
		 * Handle progress notification with individual parameters.
		 * @param progress the progress value (0.0 to 1.0)
		 * @param progressToken the progress token identifier
		 * @param total the total value as string
		 */
		@McpProgress(clients = "my-client-id")
		public void handleProgressWithParams(Double progress, String progressToken, String total) {
			System.out.printf("Progress: %.2f%% for token %s (Total: %s)%n", progress * 100, progressToken, total);
		}

		/**
		 * Handle progress with primitive double.
		 * @param progress the progress value (0.0 to 1.0)
		 * @param progressToken the progress token identifier
		 * @param total the total value as string
		 */
		@McpProgress(clients = "my-client-id")
		public void handleProgressPrimitive(double progress, String progressToken, String total) {
			System.out.printf("Processing: %.1f%% complete (Token: %s)%n", progress * 100, progressToken);
		}

		public int getNotificationCount() {
			return this.notificationCount;
		}

	}

}
