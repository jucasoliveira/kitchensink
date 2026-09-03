#!/usr/bin/env python3
"""Issue 2.1 - extract the legacy catalog seed into the document-shaped fixture the app loads.

Reads  petstore1.3.1_02/src/apps/petstore/src/docroot/populate/Populate-UTF8.xml  (read-only
reference) and writes  src/main/resources/seed/catalog.json.

This is the "relational -> document" transform ADR-0005 calls a miniature of a real migration:
the six legacy tables (category, category_details, product, product_details, item, item_details;
PopulateSQL.xml:51-165) become three collections, each entity carrying its per-locale detail rows
embedded under `details`. The one rule of the legacy loader that shapes the data is kept:
`XMLDBHandler.java:179-180` stores xml:lang="en-US" as "en_US", the Locale.toString() form the
catalog DAO queries with.

The output is deterministic (seed order, fixed key order, sorted nothing) so a re-run on an
unchanged reference tree produces a byte-identical file; `git diff` on the fixture then means the
transform changed, not the data. Run it, commit the result, never hand-edit the JSON.

    python3 scripts/extract-catalog-seed.py            # writes src/main/resources/seed/catalog.json
    python3 scripts/extract-catalog-seed.py -o -       # prints to stdout
"""
import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LEGACY_SEED = ROOT / "petstore1.3.1_02/src/apps/petstore/src/docroot/populate/Populate-UTF8.xml"
DEFAULT_OUT = ROOT / "src/main/resources/seed/catalog.json"
XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"


def locale(details):
    # XMLDBHandler.java:179-180 - normalizeValue() rewrites '-' to '_' for xml:lang only.
    return details.get(XML_LANG).replace("-", "_")


def text(element, tag):
    # The *_details columns image/descn are nullable (PopulateSQL.xml:67-68); absent stays absent.
    child = element.find(tag)
    return None if child is None or child.text is None else child.text.strip()


def strip_none(mapping):
    return {k: v for k, v in mapping.items() if v is not None}


def named_details(entity, tag):
    # category_details / product_details: (id, name, image, descn, locale) -> keyed by locale.
    return {
        locale(d): strip_none({"name": text(d, "Name"), "image": text(d, "Image"), "description": text(d, "Description")})
        for d in entity.findall(tag)
    }


def item_details(entity):
    # item_details: (itemid, listprice, unitcost, locale, attr1..attr5, image, descn) - PopulateSQL.xml:146-165.
    # Prices stay as the seed's strings; 3.1 decides the numeric type (BigDecimal vs Decimal128).
    return {
        locale(d): strip_none({
            "listPrice": text(d, "ListPrice"),
            "unitCost": text(d, "UnitCost"),
            "attributes": [a.text.strip() for a in d.findall("Attribute") if a.text and a.text.strip()],
            "image": text(d, "Image"),
            "description": text(d, "Description"),
        })
        for d in entity.findall("ItemDetails")
    }


def extract(populate_xml):
    catalog = ET.parse(populate_xml).getroot().find("Catalog")
    if catalog is None:
        sys.exit(f"no <Catalog> element in {populate_xml}")
    return {
        "categories": [
            {"_id": c.get("id"), "details": named_details(c, "CategoryDetails")}
            for c in catalog.find("Categories").findall("Category")
        ],
        "products": [
            {"_id": p.get("id"), "categoryId": p.get("category"), "details": named_details(p, "ProductDetails")}
            for p in catalog.find("Products").findall("Product")
        ],
        "items": [
            {"_id": i.get("id"), "productId": i.get("product"), "details": item_details(i)}
            for i in catalog.find("Items").findall("Item")
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("-i", "--input", type=Path, default=LEGACY_SEED, help="legacy Populate-UTF8.xml")
    parser.add_argument("-o", "--output", default=str(DEFAULT_OUT), help="fixture path, or - for stdout")
    args = parser.parse_args()

    fixture = extract(args.input)
    rendered = json.dumps(fixture, ensure_ascii=False, indent=2) + "\n"

    if args.output == "-":
        sys.stdout.write(rendered)
    else:
        out = Path(args.output)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(rendered, encoding="utf-8")
        print(f"wrote {out.relative_to(ROOT) if out.is_relative_to(ROOT) else out}", file=sys.stderr)

    per_locale = lambda rows: {  # noqa: E731 - one-line report helper
        loc: sum(1 for r in rows if loc in r["details"]) for loc in ("en_US", "ja_JP", "zh_CN")
    }
    for name in ("categories", "products", "items"):
        rows = fixture[name]
        print(f"{name:10s} {len(rows):3d}  per locale {per_locale(rows)}", file=sys.stderr)


if __name__ == "__main__":
    main()
