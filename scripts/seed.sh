#!/usr/bin/env bash
# Issue 2.1 - the one command that reloads the catalog seed.
#
# Legacy twin: GET /Populate?forcefully=true (PopulateServlet.java:113-118, :158-162), which
# dropped, recreated and refilled the catalog tables from Populate-UTF8.xml. Here the flag turns
# on CatalogSeeder, which drops the three catalog collections and inserts
# src/main/resources/seed/catalog.json, then the app keeps running - seeded, ready to demo.
# Needs the MongoDB from scripts/dev-up.sh (or MONGODB_URI) to be reachable.
set -euo pipefail
cd "$(dirname "$0")/.."
exec ./mvnw spring-boot:run -Dspring-boot.run.arguments=--kitchensink.seed.catalog=true "$@"
