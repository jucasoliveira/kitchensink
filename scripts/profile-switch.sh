#!/usr/bin/env bash
# Issue 7.4 — the profile-switch demo, as a command rather than a claim.
#
# ADR-0005 puts a repository port in the application layer and two adapters behind it, and the
# whole MongoDB stretch goal rests on that being true rather than merely intended. The evidence is
# not "it compiles under both profiles" — it is that the SAME test suite, one file, passes against
# a document store and against a relational one, and answers the same questions in both.
#
#   ./scripts/profile-switch.sh
#
# runs each contract suite twice, once per profile, extracts the test-method names Surefire
# actually executed, and diffs them. A silent diff is the claim. Anything else prints what moved.
#
# --mongo / --jpa run one side only (issue 7.4: "degrades to Mongo-only if the schedule slips").
set -euo pipefail

cd "$(dirname "$0")/.."
OUT="${TMPDIR:-/tmp}/kitchensink-profile-switch"
rm -rf "$OUT"; mkdir -p "$OUT"

# Each row is: <label> <mongo-side test class> <jpa-side test class>
# Both sides of a row are subclasses of the same abstract contract, which is the point: the
# assertions live once, in a class neither store can see.
PAIRS=(
  "customer  CustomerMongoRoundTripTest  JpaCustomerRepositoryTest"
  "catalog   MongoCatalogRepositoryTest  JpaCatalogRepositoryTest"
  "search    MongoCatalogSearchTest      JpaCatalogSearchTest"
)

# pom.xml redirects the build to ${java.io.tmpdir}/kitchensink/target, so Surefire's reports are
# NOT under ./target. Deleting the wrong directory is not a harmless mistake here: the second run
# would read the first run's reports and the comparison would report perfect agreement between a
# profile and itself. Ask Maven where it actually is rather than reconstructing the path.
REPORTS="$(./mvnw -q -o help:evaluate -Dexpression=project.build.directory -DforceStdout)/surefire-reports"
echo "==> surefire reports: $REPORTS"

run_side() {   # $1=side  $2=comma-separated classes  $3.. = extra mvn args
  local side=$1 classes=$2; shift 2
  echo "==> running $side: $classes"
  rm -rf "$REPORTS"
  ./mvnw -q -o test -Dtest="$classes" -DfailIfNoTests=false "$@" >"$OUT/$side.log" 2>&1 || {
    echo "FAILED: $side — see $OUT/$side.log"; tail -30 "$OUT/$side.log"; return 1
  }
  python3 scripts/extract-test-names.py "$OUT/$side.names" "$REPORTS"
}

want_mongo=1; want_jpa=1
case "${1:-}" in
  --mongo) want_jpa=0 ;;
  --jpa)   want_mongo=0 ;;
  "")      ;;
  *) echo "usage: $0 [--mongo|--jpa]" >&2; exit 2 ;;
esac

mongo_classes=""; jpa_classes=""
for row in "${PAIRS[@]}"; do
  read -r _ m j <<<"$row"
  mongo_classes="${mongo_classes:+$mongo_classes,}$m"
  jpa_classes="${jpa_classes:+$jpa_classes,}$j"
done

(( want_mongo )) && run_side mongo "$mongo_classes"
(( want_jpa ))   && run_side jpa   "$jpa_classes" -Dspring.profiles.active=jpa

if (( want_mongo && want_jpa )); then
  echo
  echo "==> comparing the two runs"
  python3 scripts/compare-profile-runs.py "$OUT/mongo.names" "$OUT/jpa.names"
else
  echo
  echo "one side only — nothing to compare. Contract methods executed:"
  cat "$OUT"/*.names
fi
