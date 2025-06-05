/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.transformer.splitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * @author Christian Tzolov
 * @author Jiwoo Kim
 */
public class TextSplitterTests {

	static TextSplitter testTextSplitter = new TextSplitter() {

		@Override
		protected List<String> splitText(String text) {
			int chuckSize = text.length() / 2;

			List<String> chunks = new ArrayList<>();

			chunks.add(text.substring(0, chuckSize));
			chunks.add(text.substring(chuckSize));

			return chunks;
		}
	};

	@Test
	public void testSplitText() {

		var contentFormatter1 = DefaultContentFormatter.defaultConfig();
		var contentFormatter2 = DefaultContentFormatter.defaultConfig();

		assertThat(contentFormatter1).isNotSameAs(contentFormatter2);

		var doc1 = new Document("In the end, writing arises when man realizes that memory is not enough.",
				Map.of("key1", "value1", "key2", "value2"));
		doc1.setContentFormatter(contentFormatter1);

		var doc2 = new Document("The most oppressive thing about the labyrinth is that you are constantly "
				+ "being forced to choose. It isn’t the lack of an exit, but the abundance of exits that is so disorienting.",
				Map.of("key2", "value22", "key3", "value3"));
		doc2.setContentFormatter(contentFormatter2);

		List<Document> chunks = testTextSplitter.apply(List.of(doc1, doc2));

		assertThat(testTextSplitter.isCopyContentFormatter()).isTrue();

		assertThat(chunks).hasSize(4);

		// Doc1 chunks:
		assertThat(chunks.get(0).getText()).isEqualTo("In the end, writing arises when man");
		assertThat(chunks.get(1).getText()).isEqualTo(" realizes that memory is not enough.");

		// Doc2 chunks:
		assertThat(chunks.get(2).getText())
			.isEqualTo("The most oppressive thing about the labyrinth is that you are constantly being forced to ");
		assertThat(chunks.get(3).getText())
			.isEqualTo("choose. It isn’t the lack of an exit, but the abundance of exits that is so disorienting.");

		// Verify that the same, merged metadata is copied to all chunks.
		assertThat(chunks.get(0).getMetadata()).isEqualTo(chunks.get(1).getMetadata());
		assertThat(chunks.get(2).getMetadata()).isEqualTo(chunks.get(3).getMetadata());
		assertThat(chunks.get(0).getMetadata()).containsKeys("key1", "key2").doesNotContainKeys("key3");
		assertThat(chunks.get(2).getMetadata()).containsKeys("key2", "key3").doesNotContainKeys("key1");

		// Verify that the content formatters are copied from the parents to the chunks.
		// doc1 -> chunk0, chunk1 and doc2 -> chunk2, chunk3
		assertThat(chunks.get(0).getContentFormatter()).isSameAs(contentFormatter1);
		assertThat(chunks.get(1).getContentFormatter()).isSameAs(contentFormatter1);

		assertThat(chunks.get(2).getContentFormatter()).isSameAs(contentFormatter2);
		assertThat(chunks.get(3).getContentFormatter()).isSameAs(contentFormatter2);

		// Disable copy content formatters
		testTextSplitter.setCopyContentFormatter(false);
		chunks = testTextSplitter.apply(List.of(doc1, doc2));

		assertThat(chunks.get(0).getContentFormatter()).isNotSameAs(contentFormatter1);
		assertThat(chunks.get(1).getContentFormatter()).isNotSameAs(contentFormatter1);

		assertThat(chunks.get(2).getContentFormatter()).isNotSameAs(contentFormatter2);
		assertThat(chunks.get(3).getContentFormatter()).isNotSameAs(contentFormatter2);

	}

	@Test
	public void pageNoChunkSplit() {
		// given
		var doc1 = new Document("1In the end, writing arises when man realizes that memory is not enough."
				+ "1The most oppressive thing about the labyrinth is that you are constantly "
				+ "1being forced to choose. It isn’t the lack of an exit, but the abundance of exits that is so disorienting.",
				Map.of("file_name", "sample1.pdf", "page_number", 1));

		var doc2 = new Document("2In the end, writing arises when man realizes that memory is not enough."
				+ "2The most oppressive thing about the labyrinth is that you are constantly "
				+ "2being forced to choose. It isn’t the lack of an exit, but the abundance of exits that is so disorienting.",
				Map.of("file_name", "sample1.pdf", "page_number", 2));

		var doc3 = new Document("3In the end, writing arises when man realizes that memory is not enough."
				+ "3The most oppressive thing about the labyrinth is that you are constantly "
				+ "3being forced to choose. It isn’t the lack of an exit, but the abundance of exits that is so disorienting.",
				Map.of("file_name", "sample1.pdf", "page_number", 3));

		var doc4 = new Document("4In the end, writing arises when man realizes that memory is not enough."
				+ "4The most oppressive thing about the labyrinth is that you are constantly "
				+ "4being forced to choose. It isn’t the lack of an exit, but the abundance of exits that is so disorienting.",
				Map.of("file_name", "sample1.pdf", "page_number", 4));

		var tokenTextSplitter = new TokenTextSplitter();

		// when
		List<Document> splitedDocument = tokenTextSplitter.apply(List.of(doc1, doc2, doc3, doc4));

		// then
		assertAll(() -> assertThat(splitedDocument).isNotNull(), () -> assertThat(splitedDocument).isNotEmpty(),
				() -> assertThat(splitedDocument).hasSize(4),
				() -> assertThat(splitedDocument.get(0).getMetadata().get("page_number")).isEqualTo(1),
				() -> assertThat(splitedDocument.get(1).getMetadata().get("page_number")).isEqualTo(2),
				() -> assertThat(splitedDocument.get(2).getMetadata().get("page_number")).isEqualTo(3),
				() -> assertThat(splitedDocument.get(3).getMetadata().get("page_number")).isEqualTo(4));
	}

	@Test
	public void pageWithChunkSplit() {
		// given

		var doc1 = new Document("1In the end, writing arises when man realizes that memory is not enough."
				+ "1The most oppressive thing about the labyrinth is that you are constantly "
				+ "1being forced to choose. It isn’t the lack of an exit, but the abundance of exits that is so disorienting.",
				Map.of("file_name", "sample1.pdf", "page_number", 1));

		var doc2 = new Document(
				"levels, their care  providers,   legal  representatives    and   families  get the  right home    and                                                                                               \n"
						+ "                  community-based       support   and   services  at the  right time,  in the  right place.  Please   click here  to                                                                                  \n"
						+ "                  go to Community      Living  Connections.                                                                                                                                                           \n"
						+ "\n"
						+ "                  I  am  trying   to  register    as  a consumer,       but   Carina    will  not   recognize      me   or  my                                                                                        \n"
						+ "                  information.       What    should     I do?                                                                                                                                                         \n"
						+ "\n"
						+ "                  Please  double   check   your  form   entries  including   the spelling  of your   name   and  your                                                                                                 \n"
						+ "                  ProviderOne     number,    or last four  digits of your  social  security  number    and   date  of birth. Please                                                                                   \n"
						+ "                  use the  name    you  have  on  file with  the  Department     of Social  and  Health   Services   (DSHS).  Also                                                                                    \n"
						+ "                  make   sure  you  have   a current  or  pending   assessment     with  DSHS.                                                                                                                        \n"
						+ "\n"
						+ "                  If  you  are  having  trouble registering,   please  contact   us or  call  us  at  1-855-796-0605.                                                                                                 \n"
						+ "\n"
						+ "                  The   Home     Care    Referral    Registry     has  been    absorbed       by  Consumer        Direct   Care                                                                                       \n"
						+ "                  Network      Washington        (CDWA).       Who    can   help   me    find  care   on   Carina?                                                                                                    \n"
						+ "\n"
						+ "                  Consumer     Direct  Care  Network    Washington      (CDWA)    has  taken  over  from   the  Home    Care                                                                                          \n"
						+ "                  Referral  Registry  (HCRR).   CDWA     is  responsible  for assisting  consumers     and   Individual  Providers                                                                                    \n"
						+ "                  (IPs) to use  Carina  to find  matches.    CDWA    staff are  available  across   the state  to  assist                                                                                             \n"
						+ "                  consumers    to  sign up  in the  Carina  system    and  help  IPs get  (re)contracted    or hired  to work.                                                                                        \n"
						+ "\n"
						+ "                  What    are   some    good     interview     questions      I should    ask   providers?                                                                                                            \n"
						+ "\n"
						+ "                  Your  approach    to the  interview   is important,   you   are offering   a job to  someone    who   is looking                                                                                    \n"
						+ "                  for work.  The  person    you  interview   may   be  nervous.   Put  them   at ease,  call them   by their  first                                                                                   \n"
						+ "                  name,   maintain   eye  contact   and  tell them   a little  about yourself.   Read  more    tips and  specific                                                                                     \n"
						+ "                  interview   questions   in our  blog:  What   to Ask  Potential   Providers.                                                                                                                        \n"
						+ "\n"
						+ "                  I  am  ready    to  hire  a  home     care   provider!                                                                                                                                              \n"
						+ "\n"
						+ "                  You  found   an Individual   Provider   (IP) that  you  would   like to hire?  That  is exciting!  In order  for                                                                                    \n"
						+ "                  them   to start working,   contact   Consumer     Direct  Care   Network    Washington     (CDWA)     and  request                                                                                  \n"
						+ "                  authorization.   They   cannot   start work   before   you  have   received   an  Okay  to  Work   from   CDWA.                                                                                     \n"
						+ "\n"
						+ "                  Consumers     should   continue   to work   with  their  case  manager,    who   will help  you  create  a  Plan of                                                                                 \n"
						+ "                  Care  and  access   needed   services.\n"
						+ "Once   you  have  decided    on an  IP to work   with,  they  should\n" + "\n",
				Map.of("file_name", "sample1.pdf", "page_number", 2));

		var doc3 = new Document("3In the end, writing arises when man realizes that memory is not enough."
				+ "3The most oppressive thing about the labyrinth is that you are constantly "
				+ "3being forced to choose. It isn’t the lack of an exit, but the abundance of exits that is so disorienting.",
				Map.of("file_name", "sample1.pdf", "page_number", 3));

		var tokenTextSplitter = new TokenTextSplitter();

		// when
		List<Document> splitedDocument = tokenTextSplitter.apply(List.of(doc1, doc2, doc3));

		// then
		assertAll(() -> assertThat(splitedDocument).isNotNull(), () -> assertThat(splitedDocument).isNotEmpty(),
				() -> assertThat(splitedDocument).hasSize(4),
				() -> assertThat(splitedDocument.get(0).getMetadata().get("page_number")).isEqualTo(1),
				() -> assertThat(splitedDocument.get(1).getMetadata().get("page_number")).isEqualTo(2),
				() -> assertThat(splitedDocument.get(2).getMetadata().get("page_number")).isEqualTo(2),
				() -> assertThat(splitedDocument.get(3).getMetadata().get("page_number")).isEqualTo(3));
	}

	@Test
	public void testSplitTextWithNullMetadata() {

		var contentFormatter = DefaultContentFormatter.defaultConfig();

		var doc = new Document("In the end, writing arises when man realizes that memory is not enough.");

		doc.getMetadata().put("key1", "value1");
		doc.getMetadata().put("key2", null);

		doc.setContentFormatter(contentFormatter);

		List<Document> chunks = testTextSplitter.apply(List.of(doc));

		assertThat(testTextSplitter.isCopyContentFormatter()).isTrue();

		assertThat(chunks).hasSize(2);

		// Doc chunks:
		assertThat(chunks.get(0).getText()).isEqualTo("In the end, writing arises when man");
		assertThat(chunks.get(1).getText()).isEqualTo(" realizes that memory is not enough.");

		// Verify that the same, merged metadata is copied to all chunks.
		assertThat(chunks.get(0).getMetadata()).isEqualTo(chunks.get(1).getMetadata());
		assertThat(chunks.get(1).getMetadata()).containsKeys("key1");

		// Verify that the content formatters are copied from the parents to the chunks.
		assertThat(chunks.get(0).getContentFormatter()).isSameAs(contentFormatter);
		assertThat(chunks.get(1).getContentFormatter()).isSameAs(contentFormatter);
	}

}
