package com.jucasoliveira.kitchensink.catalog.application;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue 3.4 — the paging half, pinned on its own so a failure names the arithmetic rather than the
 * query that fed it.
 *
 * <p>Legacy anchor: {@code catalog/model/Page.java} and the identical block that appears four times
 * in {@code GenericCatalogDAO.java} (lines 189-203, 246-262, 300-330, 383-395). The legacy did not
 * page in SQL. Every statement ran unbounded against a {@code TYPE_SCROLL_INSENSITIVE} cursor and
 * the DAO then skipped client-side:
 *
 * <pre>
 * if (start &gt;= 0 &amp;&amp; resultSet.absolute(start + 1)) {
 *   boolean hasNext = false;
 *   do { … } while ((hasNext = resultSet.next()) &amp;&amp; (--count &gt; 0));
 *   return new Page(items, start, hasNext);
 * }
 * return Page.EMPTY_PAGE;
 * </pre>
 *
 * <p>That is why {@code CatalogService} slices a {@code List} in memory rather than pushing
 * {@code start}/{@code count} into the port: it is not a shortcut around {@code $skip}/{@code $limit},
 * it is what the 2003 application did. {@code Page} carries no total — only {@code hasNext}, which
 * the do/while learns by calling {@code next()} one row past the page.
 *
 * <p>Three quirks fall out of that loop and out of {@code EMPTY_PAGE}. All three are reproduced
 * deliberately, and each has a test below saying so, because "we kept the off-by-one" is only a
 * defensible answer if it was a decision:
 *
 * <ol>
 * <li>it is a <em>do</em>/while, so a row is added before {@code count} is first tested — asking for
 * zero rows returns one;</li>
 * <li>{@code EMPTY_PAGE} is {@code new Page(EMPTY_LIST, 0, false)} — its {@code start} is 0, not the
 * start that was asked for, so paging past the end loses the Previous link as well as Next;</li>
 * <li>{@code getStartOfPreviousPage()} subtracts the <em>current</em> page's size, so stepping back
 * off a short last page steps back short.</li>
 * </ol>
 */
@Tag("parity")
class CatalogPageTest {

	static final List<String> FIVE = List.of("a", "b", "c", "d", "e");

	@Test
	@DisplayName("the first page is the first count rows, and hasNext is true because one more call to next() would have found a row")
	void a_page_is_count_rows_from_start() {
		CatalogPage<String> page = CatalogPage.of(FIVE, 0, 2);

		assertThat(page.contents()).containsExactly("a", "b");
		assertThat(page.start()).isZero();
		assertThat(page.hasNext()).isTrue();
		assertThat(page.size()).isEqualTo(2);
	}

	@Test
	@DisplayName("category.jsp:129-137 — Next passes startOfNextPage, which is start + size, and lands on the next unread row")
	void the_next_page_starts_where_this_one_ended() {
		CatalogPage<String> first = CatalogPage.of(FIVE, 0, 2);

		assertThat(first.startOfNextPage()).isEqualTo(2);
		assertThat(CatalogPage.of(FIVE, first.startOfNextPage(), 2).contents()).containsExactly("c", "d");
	}

	@Test
	@DisplayName("the last page is short and hasNext is false, because next() ran out before count did")
	void the_last_page_is_short_and_has_no_next() {
		CatalogPage<String> last = CatalogPage.of(FIVE, 4, 2);

		assertThat(last.contents()).containsExactly("e");
		assertThat(last.hasNext()).isFalse();
	}

	@Test
	@DisplayName("Page.java:83 — previousPageAvailable is start > 0 and nothing else; it is not asking whether a previous page has content")
	void previous_is_available_from_any_non_zero_start() {
		assertThat(CatalogPage.of(FIVE, 0, 2).previousPageAvailable()).isFalse();
		assertThat(CatalogPage.of(FIVE, 1, 2).previousPageAvailable()).isTrue();
		assertThat(CatalogPage.of(FIVE, 4, 2).previousPageAvailable()).isTrue();
	}

	@Test
	@DisplayName("QUIRK 3 — Page.java:89 subtracts this page's size, so Previous off the short last page steps back one, not two")
	void previous_steps_back_by_the_current_pages_size() {
		// Page.getStartOfPreviousPage() is max(start - objects.size(), 0). On a full page that is
		// max(start - count, 0) and behaves. On the 1-row last page of a 5-row list it is
		// max(4 - 1, 0) = 3, so Previous shows rows 4 and 5 — one of which the user just read.
		// Reproduced rather than fixed: the accessor is named after the arithmetic, and the
		// screens of 3.5 render exactly what it returns.
		assertThat(CatalogPage.of(FIVE, 4, 2).startOfPreviousPage()).isEqualTo(3);
		assertThat(CatalogPage.of(FIVE, 2, 2).startOfPreviousPage()).isZero();
	}

	@Test
	@DisplayName("QUIRK 1 — GenericCatalogDAO.java:255 is a do/while, so count=0 returns one row rather than none")
	void asking_for_no_rows_returns_one() {
		// do { add } while ((hasNext = rs.next()) && (--count > 0)) — the body runs before count is
		// tested, so 0 and every negative count behave as 1. Math.max(count, 1) is that, exactly.
		assertThat(CatalogPage.of(FIVE, 0, 0).contents()).containsExactly("a");
		assertThat(CatalogPage.of(FIVE, 0, -7).contents()).containsExactly("a");
	}

	@Test
	@DisplayName("QUIRK 2 — start past the end is EMPTY_PAGE, whose start is 0, so the user loses Previous as well as Next")
	void paging_past_the_end_strands_the_reader() {
		// rs.absolute(start + 1) returns false past the last row and the DAO returns the shared
		// Page.EMPTY_PAGE constant — which was built with start 0 and knows nothing of the start
		// that was asked for. previousPageAvailable() is therefore false and category.jsp renders
		// neither link: a dead end, reachable by hand-editing the query string.
		CatalogPage<String> past = CatalogPage.of(FIVE, 5, 2);

		assertThat(past.contents()).isEmpty();
		assertThat(past.start()).isZero();
		assertThat(past.previousPageAvailable()).isFalse();
		assertThat(past.hasNext()).isFalse();
	}

	@Test
	@DisplayName("start < 0 is rejected before the cursor is touched (GenericCatalogDAO.java:246: start >= 0 && …)")
	void a_negative_start_is_an_empty_page() {
		assertThat(CatalogPage.of(FIVE, -1, 2).contents()).isEmpty();
	}

	@Test
	@DisplayName("an empty list is an empty page whatever the start, without an index out of bounds on the way")
	void an_empty_list_pages_to_an_empty_page() {
		assertThat(CatalogPage.of(List.<String>of(), 0, 2).contents()).isEmpty();
		assertThat(CatalogPage.of(List.<String>of(), 3, 2).contents()).isEmpty();
	}

	@Test
	@DisplayName("count larger than the list returns the whole list and no next page")
	void a_count_past_the_end_is_clamped() {
		CatalogPage<String> page = CatalogPage.of(FIVE, 0, 99);

		assertThat(page.contents()).containsExactlyElementsOf(FIVE);
		assertThat(page.hasNext()).isFalse();
		assertThat(page.startOfNextPage()).isEqualTo(5);
	}

	@Test
	@DisplayName("the contents are a copy, so a caller cannot write through the page into the list it was cut from")
	void the_page_does_not_alias_its_source() {
		List<String> mutable = new ArrayList<>(FIVE);
		CatalogPage<String> page = CatalogPage.of(mutable, 0, 2);
		mutable.set(0, "z");

		assertThat(page.contents()).containsExactly("a", "b");
	}

	@Test
	@DisplayName("walking every page reaches every row exactly once, which is the only property paging really owes anyone")
	void the_pages_partition_the_list() {
		List<String> seen = new ArrayList<>();
		int start = 0;
		CatalogPage<String> page;
		do {
			page = CatalogPage.of(FIVE, start, 2);
			seen.addAll(page.contents());
			start = page.startOfNextPage();
		}
		while (page.hasNext());

		assertThat(seen).containsExactlyElementsOf(FIVE);
	}

}
