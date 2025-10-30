/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp.client.common.autoconfigure.annotations;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A simple {@link java.util.List} backed by a {@link Supplier} of lists. Each access
 * reads from the supplier, so the contents reflect the supplier's current state.
 */
/**
 * @author Kuntal Maity
 */
final class SupplierBackedList<T> extends AbstractList<T> {

	private final Supplier<List<T>> supplier;

	SupplierBackedList(Supplier<List<T>> supplier) {
		this.supplier = Objects.requireNonNull(supplier, "supplier must not be null");
	}

	@Override
	public T get(int index) {
		return this.supplier.get().get(index);
	}

	@Override
	public int size() {
		return this.supplier.get().size();
	}

	@Override
	public Iterator<T> iterator() {
		// Iterate over a snapshot for iteration consistency
		return List.copyOf(this.supplier.get()).iterator();
	}

	@Override
	public Spliterator<T> spliterator() {
		return Spliterators.spliterator(iterator(), size(), Spliterator.ORDERED | Spliterator.SIZED);
	}

	@Override
	public Stream<T> stream() {
		return StreamSupport.stream(spliterator(), false);
	}

}
