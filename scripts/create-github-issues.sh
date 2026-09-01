#!/usr/bin/env bash
# Materialise scripts/backlog.json as GitHub issues: labels, one milestone,
# 8 epic issues, 45 sub-issues linked as real GitHub sub-issues (GraphQL addSubIssue),
# with a task-list fallback in the epic body if sub-issues are unavailable.
#
#   ./scripts/create-github-issues.sh --dry-run    # print what would happen
#   ./scripts/create-github-issues.sh              # create / resume
#
# IDEMPOTENT: an issue whose exact title already exists is reused, not recreated,
# so the script is safe to re-run after a partial failure.
set -euo pipefail

DRY=0
[[ "${1:-}" == "--dry-run" ]] && DRY=1

cd "$(dirname "$0")/.."
BACKLOG=scripts/backlog.json
command -v gh >/dev/null || { echo "gh CLI not found"; exit 1; }
command -v jq >/dev/null || { echo "jq not found"; exit 1; }
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)
echo "repo: $REPO   dry-run: $DRY"

run() { if [[ $DRY == 1 ]]; then echo "  DRY: $*"; else "$@"; fi }

# ---------- labels ----------
echo "== labels"
jq -r '.labels[] | [.name,.color,.description] | @tsv' "$BACKLOG" |
while IFS=$'\t' read -r name color desc; do
  echo "-- $name"
  run gh label create "$name" --color "$color" --description "$desc" --force
done

# ---------- milestone (resolved to a NUMBER; title lookup is not reliable) ----------
MS=$(jq -r .milestone "$BACKLOG")
MS_DESC=$(jq -r .milestone_description "$BACKLOG")
MS_NUM=""
if [[ $DRY == 0 ]]; then
  MS_NUM=$(gh api "repos/$REPO/milestones?state=all" \
             -q "[.[] | select(.description == \"$MS_DESC\")] | first | .number" 2>/dev/null || true)
  if [[ -z "$MS_NUM" || "$MS_NUM" == "null" ]]; then
    MS_NUM=$(gh api "repos/$REPO/milestones" -f title="$MS" -f description="$MS_DESC" -q .number)
    echo "== milestone created: #$MS_NUM"
  else
    echo "== milestone reused: #$MS_NUM"
  fi
else
  echo "== milestone: $MS"
fi

# ---------- existing issues, for idempotent resume ----------
EXISTING=$(mktemp)
[[ $DRY == 0 ]] && gh issue list --state all --limit 500 --json number,title > "$EXISTING" || echo '[]' > "$EXISTING"
trap 'rm -f "$EXISTING"' EXIT

find_issue() { # $1 exact title -> issue number, or empty
  jq -r --arg t "$1" '[.[] | select(.title == $t) | .number] | first // empty' "$EXISTING"
}

create_issue() { # $1 title, $2 body, $3 comma-separated labels -> issue number
  local args=(-X POST "repos/$REPO/issues" -f title="$1" -f body="$2" -F milestone="$MS_NUM")
  local l; IFS=, read -ra l <<<"$3"
  for x in "${l[@]}"; do args+=(-f "labels[]=$x"); done
  gh api "${args[@]}" -q .number
}

node_id() { gh issue view "$1" --json id -q .id; }

link_sub() { # $1 parent node id, $2 child node id
  gh api graphql -H "GraphQL-Features: sub_issues" \
    -f query='mutation($p:ID!,$c:ID!){addSubIssue(input:{issueId:$p,subIssueId:$c}){clientMutationId}}' \
    -F p="$1" -F c="$2" >/dev/null 2>&1
}

# ---------- epics + sub-issues ----------
EPIC_COUNT=$(jq '.epics | length' "$BACKLOG")
for i in $(seq 0 $((EPIC_COUNT-1))); do
  E=$(jq ".epics[$i]" "$BACKLOG")
  EID=$(jq -r .id <<<"$E"); ETITLE=$(jq -r .title <<<"$E")
  ESUM=$(jq -r .summary <<<"$E")
  ELABELS=$(jq -r '.labels | join(",")' <<<"$E")
  EHOURS=$(jq '[.subs[].estimate_h] | add' <<<"$E")
  EFULL="[$EID] $ETITLE"

  BODY=$(printf '%s\n\n**Estimate:** %sh\n\n**Sub-issues**\n' "$ESUM" "$EHOURS")
  while IFS=$'\t' read -r _ stitle sest; do
    BODY+=$(printf -- '\n- [ ] %s (%sh)' "$stitle" "$sest")
  done < <(jq -r '.subs[] | [.id,.title,.estimate_h] | @tsv' <<<"$E")
  BODY+=$'\n\nPlan: `docs/03-migration-plan.md` · Backlog: `docs/04-work-breakdown.md` · Decisions: `docs/adr/`'

  echo "== epic $EID — $ETITLE"
  PARENT=""
  if [[ $DRY == 1 ]]; then
    echo "  DRY: create \"$EFULL\" [$ELABELS]"
  else
    N=$(find_issue "$EFULL")
    if [[ -n "$N" ]]; then echo "   = #$N (exists)"; else
      N=$(create_issue "$EFULL" "$BODY" "$ELABELS"); echo "   -> #$N"
    fi
    PARENT=$(node_id "$N")
  fi

  SUB_COUNT=$(jq '.subs | length' <<<"$E")
  for j in $(seq 0 $((SUB_COUNT-1))); do
    S=$(jq ".subs[$j]" <<<"$E")
    STITLE=$(jq -r .title <<<"$S")
    SBODY=$(jq -r '"**Epic:** '"$EID"' — '"$ETITLE"'\n\n**Legacy anchor:** " + .legacy_anchor + "\n\n**Acceptance criteria:** " + .acceptance + "\n\n**Estimate:** " + (.estimate_h|tostring) + "h\n\n---\nDefinition of done: `docs/03-migration-plan.md` §4."' <<<"$S")
    SLABELS=$(jq -r '.labels | join(",")' <<<"$S")
    if [[ $DRY == 1 ]]; then
      echo "  DRY: sub  $STITLE   [$SLABELS]"
    else
      SN=$(find_issue "$STITLE")
      if [[ -n "$SN" ]]; then MARK="="; else SN=$(create_issue "$STITLE" "$SBODY" "$SLABELS"); MARK="->"; fi
      if link_sub "$PARENT" "$(node_id "$SN")"; then echo "   $MARK #$SN linked"; else echo "   $MARK #$SN (already linked or sub-issues unavailable)"; fi
    fi
  done
done

echo "done."
