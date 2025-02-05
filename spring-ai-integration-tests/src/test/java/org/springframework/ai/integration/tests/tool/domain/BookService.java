/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.integration.tests.tool.domain;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Thomas Vitale
 */
public class BookService {

	private static final Map<Integer, Book> books = new ConcurrentHashMap<>();

	static {
		books.put(1, new Book("His Dark Materials", "Philip Pullman"));
		books.put(2, new Book("Narnia", "C.S. Lewis"));
		books.put(3, new Book("The Hobbit", "J.R.R. Tolkien"));
		books.put(4, new Book("The Lord of The Rings", "J.R.R. Tolkien"));
		books.put(5, new Book("The Silmarillion", "J.R.R. Tolkien"));
	}

	public List<Book> getBooksByAuthor(Author author) {
		return books.values().stream().filter(book -> author.name().equals(book.author())).toList();
	}

	public List<Author> getAuthorsByBook(List<Book> booksToSearch) {
		return books.values()
			.stream()
			.filter(book -> booksToSearch.stream().anyMatch(b -> b.title().equals(book.title())))
			.map(book -> new Author(book.author()))
			.toList();
	}

}
