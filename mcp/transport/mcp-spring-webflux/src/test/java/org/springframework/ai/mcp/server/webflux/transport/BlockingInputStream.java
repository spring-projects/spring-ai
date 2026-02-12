/*
 * Copyright 2026-2026 the original author or authors.
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

package org.springframework.ai.mcp.server.webflux.transport;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BlockingInputStream extends InputStream {

	private final BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();

	private volatile boolean completed = false;

	private volatile boolean closed = false;

	@Override
	public int read() throws IOException {
		if (this.closed) {
			throw new IOException("Stream is closed");
		}

		try {
			Integer value = this.queue.poll();
			if (value == null) {
				if (this.completed) {
					return -1;
				}
				value = this.queue.take(); // Blocks until data is available
				if (value == null && this.completed) {
					return -1;
				}
			}
			return value;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Read interrupted", e);
		}
	}

	public void write(int b) {
		if (!this.closed && !this.completed) {
			this.queue.offer(b);
		}
	}

	public void write(byte[] data) {
		if (!this.closed && !this.completed) {
			for (byte b : data) {
				this.queue.offer((int) b & 0xFF);
			}
		}
	}

	public void complete() {
		this.completed = true;
	}

	@Override
	public void close() {
		this.closed = true;
		this.completed = true;
		this.queue.clear();
	}

}
