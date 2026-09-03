The old app did not have pages so much as it had *screens*. A request for `category.screen` went to
a servlet that looked the name up in `screendefinitions_en_US.xml`, found five parameters — a title,
a banner, a sidebar, a body and a footer — and stitched the named JSPs into `template.jsp` around
the one that varied. I kept the composition and dropped the lookup. `layout.html` is the template,
with the same slots; the four screens are four files that hand it a title and their own body; and
the screen names are the URLs of controller methods, so the indirection through an XML file that
existed to let you rename a page without touching a link is gone, along with the servlet that read
it. The layout has four slots, not the six Pet Store used. Its own framework template had exactly
four — `waf/src/docroot/template.jsp:42-56` — and the two extra ones in the application's copy,
`mylist` and `advicebanner`, are fed by the cart and the recommendation engine, which are T3 and
unbuilt. I left them out rather than rendering them empty, because an empty slot looks like data
that failed to arrive rather than a thing that was never migrated, and the test asserts their
absence for that reason.

The URLs changed shape: `category.screen?category_id=BIRDS` is now `/catalog/categories/BIRDS`,
and the same for products and items. I could have kept the old strings and had literal URL parity,
but the `.screen` suffix *is* the WAF artefact I am migrating away from, and the issue's parity
claim is about how the page is composed, not about what it is called. An id that does not exist is
now a 404. The old code returned null, the JSP looped over nothing, and you got a heading with an
empty table under it — a 200 that means "no such category" is not something a client can be built
on. The front page lost its image map. `main.jsp` had six `<area>` hotspots over `splash.gif`, one
per category except birds, which had two; the image stays and the destinations are text links now,
generated from the same category query that feeds the sidebar. That is a small behaviour change
worth naming: the map was hardcoded HTML while the sidebar was a live query, so a category added to
the database used to appear in one place and not the other, and now it appears in both. Three things
in the banner did not survive the crossing — the search box belongs to 3.6, the cart and sign-in
links to T3, and the three locale flags had nowhere to post to once `changelocale.do` was gone.

Locale was the one place I had to invent rather than translate. Every catalog JSP opened by setting
`en_US` on the bean by hand and the flags in the banner switched a session attribute, so the browser's
own language preference was never consulted. The controller reads the request locale now and accepts
the same three the flags offered — `en_US`, `ja_JP`, `zh_CN` — falling back to `en_US` for anything
else, which is where a shopper with a French browser would have landed anyway. Prices moved earlier
in the pipeline for a duller reason: the domain carries them as the strings the legacy XML held, and
`fmt:formatNumber type="currency"` used to do the formatting in the page, so something has to parse
them on the way out or nothing does. That happens in `ItemView`, with the same locale, which is also
where the item screen picks up the product it belongs to — the legacy read the attribute and the
product name off one denormalized bean, and the aggregates keep them apart, so the item screen is
the one page that needs two lookups. The last omission is the page cache: `category.jsp`,
`product.jsp` and `sidebar.jsp` were each wrapped in a `<waf:cache duration="300000">` that held the
rendered fragment for five minutes. I did not replace it. The catalogue is twenty-eight items read
out of memory, so the cache would be protecting nothing, and a five-minute stale window is a thing
you should have to ask for rather than inherit.
