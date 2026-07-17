#!/usr/bin/env bash
#
# A guided walkthrough of the aminam authorisation engine, against a running
# instance on :8080.  Ported from the two end-to-end stories in
# src/test/java/com/beachape/aminam/integration/authz/UserScenarioTest.java
#
#   make dev        # in one shell
#   ./demo.sh       # in another
#
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
ENV_FILE="${ENV_FILE:-./.demo-env}"
PASSWORD="passw0rd"
BEAT="${BEAT:-0.4}"

readonly TOTAL_ACTS=10

if [ -t 1 ]; then
  C_HEAD=$'\033[36m'
  C_BOLD=$'\033[1m'
  C_DIM=$'\033[2m'
  C_BAD=$'\033[31m'
  C_OFF=$'\033[0m'
else
  C_HEAD='' C_BOLD='' C_DIM='' C_BAD='' C_OFF=''
fi

act_number=0
mismatches=0
status=''
body=''
new_token=''

rule() { printf '%s%s%s\n' "$C_DIM" "────────────────────────────────────────────────────────────────────────" "$C_OFF"; }

act() {
  act_number=$((act_number + 1))
  printf '\n'
  rule
  printf '  %sAct %d of %d - %s%s\n' "$C_HEAD$C_BOLD" "$act_number" "$TOTAL_ACTS" "$1" "$C_OFF"
  rule
  printf '\n'
}

# Prose, indented two spaces.  Each argument is one line.
narrate() {
  local line
  for line in "$@"; do printf '  %s\n' "$line"; done
  printf '\n'
}

reproduce() {
  local line
  printf '\n  %sReproduce:%s\n' "$C_DIM" "$C_OFF"
  for line in "$@"; do printf '    %s%s%s\n' "$C_DIM" "$line" "$C_OFF"; done
}

pause() {
  printf '\n  %s[enter]%s ' "$C_DIM" "$C_OFF"
  read -r _ || true
}

# call METHOD PATH [TOKEN] [BODY] -> sets $status and $body
call() {
  local method="$1" path="$2" token="${3:-}" payload="${4:-}"
  local args=(-s -X "$method" -w $'\n%{http_code}' "$BASE$path")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$payload" ] && args+=(-H 'Content-Type: application/json' -d "$payload")

  local raw
  raw="$(curl "${args[@]}")"
  status="${raw##*$'\n'}"
  body="${raw%$'\n'*}"
}

# show EXPECTED LABEL [JQ_FILTER] - prints the status line and the field that carries the point.
show() {
  local expected="$1" label="$2" filter="${3:-}"
  local detail=''

  if [ -n "$filter" ]; then
    detail="$(printf '%s' "$body" | jq -r "$filter" 2>/dev/null || true)"
  fi

  if [ "$status" = "$expected" ]; then
    printf '  %-58s -> %s' "$label" "$status"
  else
    mismatches=$((mismatches + 1))
    printf '  %-58s -> %s%s MISMATCH (expected %s)%s' "$label" "$C_BAD" "$status" "$expected" "$C_OFF"
  fi
  [ -n "$detail" ] && printf '   %s' "$detail"
  printf '\n'
  sleep "$BEAT"
}

jqr() { printf '%s' "$body" | jq -r "$1"; }

# Records an id or token into the env file so the printed snippets run verbatim.
remember() {
  printf '%s=%q\n' "$1" "$2" >>"$ENV_FILE"
}

# The claims segment of a JWT, as JSON.  base64url is not base64: the two
# gsubs are load-bearing, and jq rejects the token outright without them rather
# than decoding it wrongly.  Missing padding jq handles by itself.
jwt_claims() {
  printf '%s' "$1" | jq -R 'split(".")[1] | gsub("-";"+") | gsub("_";"/") | @base64d | fromjson'
}

# Leaves the login response in $status/$body and its token in $new_token.
# Deliberately not a command substitution: that runs in a subshell, and $status
# would not survive it.
signup_and_login() {
  local username="$1"
  call POST /api/v1/signup '' "$(jq -nc --arg u "$username" --arg p "$PASSWORD" '{username:$u,password:$p}')"
  [ "$status" = "201" ] || { printf '%s  signup failed for %s: %s %s%s\n' "$C_BAD" "$username" "$status" "$body" "$C_OFF"; exit 1; }
  call POST /api/v1/login '' "$(jq -nc --arg u "$username" --arg p "$PASSWORD" '{username:$u,password:$p}')"
  [ "$status" = "200" ] || { printf '%s  login failed for %s: %s %s%s\n' "$C_BAD" "$username" "$status" "$body" "$C_OFF"; exit 1; }
  new_token="$(jqr .token)"
}

# ---------------------------------------------------------------------------
# Act 0 - preflight
# ---------------------------------------------------------------------------

for tool in curl jq; do
  command -v "$tool" >/dev/null 2>&1 || {
    printf '%sThis demo needs %s on PATH.%s\n' "$C_BAD" "$tool" "$C_OFF" >&2
    exit 1
  }
done

# No /q/health in this app (smallrye-health is not a dependency), and
# /q/swagger-ui is dev-mode only.  An anonymous /me proves routing and auth are
# live: it must answer 401, not refuse the connection.
if ! probe="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$BASE/api/v1/me" 2>/dev/null)"; then
  printf '%sNothing is answering at %s.  Start the app first:  make dev%s\n' "$C_BAD" "$BASE" "$C_OFF" >&2
  exit 1
fi
if [ "$probe" != "401" ]; then
  printf '%s%s/api/v1/me answered %s, expected 401.  Is that really aminam?%s\n' "$C_BAD" "$BASE" "$probe" "$C_OFF" >&2
  exit 1
fi

: >"$ENV_FILE"
remember BASE "$BASE"

# Usernames are suffixed because the demo runs repeatedly against one database
# with no cleanup, exactly as TestHelpers.signUp() does it.
suffix="$(date +%s)$$"
LLOYD_USER="lloyd-$suffix"
BOB_USER="bob-$suffix"
LLOYD2_USER="lloyd2-$suffix"
BOB2_USER="bob2-$suffix"

printf '\n  %saminam - a walk through the authorisation engine%s\n\n' "$C_BOLD" "$C_OFF"
printf '  %-12s%s\n' 'Target' "$BASE"
printf '  %-12s%s\n' 'Checks' 'curl ok, jq ok, service answering on :8080'
printf '  %-12s%s\n' 'Env file' "$ENV_FILE  (written as the demo goes; source it at any pause)"
printf '\n'
narrate \
  "Ten acts, ported from UserScenarioTest.java.  Two people: lloyd, who owns" \
  "an org, and bob, who keeps running into its edges."
pause

# ---------------------------------------------------------------------------
# Act 1 - the cast
# ---------------------------------------------------------------------------

act "the cast, and an org nobody asked for"

narrate \
  "Signing up does more than create a user.  It provisions a personal org" \
  "named after the username and seats the user in it as manager, so login" \
  "hands back a token that is already anchored to an active org.  That" \
  "detail is the whole plot of act 3."

signup_and_login "$LLOYD_USER"; LLOYD_TOKEN="$new_token"
show 200 "POST /api/v1/signup + /login   as lloyd" '"token=" + (.token[0:16]) + "..."'
remember LLOYD_LOGIN_TOKEN "$LLOYD_TOKEN"

signup_and_login "$BOB_USER"; BOB_LOGIN_TOKEN="$new_token"
show 200 "POST /api/v1/signup + /login   as bob" '"token=" + (.token[0:16]) + "..."'
remember BOB_LOGIN_TOKEN "$BOB_LOGIN_TOKEN"

call GET /api/v1/me "$BOB_LOGIN_TOKEN"
show 200 "GET  /api/v1/me                as bob" '"username=" + .username + "  org=" + (.org // "null")'

narrate "" "bob's active org is his own.  He has never heard of beachape."

reproduce \
  "curl -s -H \"Authorization: Bearer \$BOB_LOGIN_TOKEN\" \\" \
  "  \"\$BASE/api/v1/me\" | jq"
pause

# ---------------------------------------------------------------------------
# Act 2 - an org, and the claim that carries it
# ---------------------------------------------------------------------------

act "an org, and the claim that carries it"

narrate \
  "lloyd creates beachape.  Creating an org does not make it active in his" \
  "token, so he switches, and switch-org mints a brand new JWT."

call POST /api/v1/orgs "$LLOYD_TOKEN" '{"name":"beachape"}'
show 201 "POST /api/v1/orgs              as lloyd" '"name=" + .name + "  id=" + .id'
BEACHAPE="$(jqr .id)"
remember BEACHAPE "$BEACHAPE"

call POST /api/v1/sessions/switch-org "$LLOYD_TOKEN" "$(jq -nc --arg o "$BEACHAPE" '{org:$o}')"
show 200 "POST /api/v1/sessions/switch-org as lloyd" '"token=" + (.token[0:16]) + "...(new)"'
LLOYD_BEACHAPE="$(jqr .token)"
remember LLOYD_TOKEN "$LLOYD_BEACHAPE"

narrate "" "Inside that token, the claims the engine actually reads:"
printf '  %s%s%s\n' "$C_DIM" "$(jwt_claims "$LLOYD_BEACHAPE" | jq -c '{iss, aud, username, org, mid, jti}')" "$C_OFF"

narrate \
  "" \
  "org is the active organisation and mid the membership within it.  Nothing" \
  "in the request body can forge these: they are signed into the token, and" \
  "every fact the engine evaluates is resolved server-side from them."

reproduce \
  "echo \"\$LLOYD_TOKEN\" | jq -R 'split(\".\")[1] | gsub(\"-\";\"+\") | gsub(\"_\";\"/\") | @base64d | fromjson'" \
  "curl -s -H \"Authorization: Bearer \$LLOYD_TOKEN\" \"\$BASE/api/v1/me\" | jq"
pause

# ---------------------------------------------------------------------------
# Act 3 - an org you are not active in does not exist
# ---------------------------------------------------------------------------

act "an org you are not active in does not exist"

call POST "/api/v1/orgs/$BEACHAPE/members" "$LLOYD_BEACHAPE" \
  "$(jq -nc --arg u "$BOB_USER" '{username:$u,policyIds:["system:viewer"]}')"
show 201 "POST /api/v1/orgs/\$BEACHAPE/members  as lloyd" '"username=" + .username + "  policyIds=" + (.policyIds | tostring)'

narrate \
  "" \
  "bob is a member now.  But he is still carrying the token login gave him," \
  "anchored to his personal org, and authorisation is scoped to the token's" \
  "active org.  From there beachape is not forbidden.  It is invisible."

call GET "/api/v1/orgs/$BEACHAPE/databases" "$BOB_LOGIN_TOKEN"
show 404 "GET  /api/v1/orgs/\$BEACHAPE/databases as bob (personal org)" '"message=\"" + .message + "\""'

narrate \
  "" \
  "404, not 403.  A 403 would have confirmed the org exists.  bob switches," \
  "and the very same URL answers."

call POST /api/v1/sessions/switch-org "$BOB_LOGIN_TOKEN" "$(jq -nc --arg o "$BEACHAPE" '{org:$o}')"
show 200 "POST /api/v1/sessions/switch-org      as bob" '"token=" + (.token[0:16]) + "...(new)"'
BOB_TOKEN="$(jqr .token)"
remember BOB_TOKEN "$BOB_TOKEN"

call GET "/api/v1/orgs/$BEACHAPE/databases" "$BOB_TOKEN"
show 200 "GET  /api/v1/orgs/\$BEACHAPE/databases as bob (beachape)" '"values=" + (.values | map(.name) | tostring)'

reproduce \
  "# the same URL, the two tokens" \
  "curl -s -o /dev/null -w '%{http_code}\\n' -H \"Authorization: Bearer \$BOB_LOGIN_TOKEN\" \\" \
  "  \"\$BASE/api/v1/orgs/\$BEACHAPE/databases\"   # 404" \
  "curl -s -o /dev/null -w '%{http_code}\\n' -H \"Authorization: Bearer \$BOB_TOKEN\" \\" \
  "  \"\$BASE/api/v1/orgs/\$BEACHAPE/databases\"   # 200"
pause

# ---------------------------------------------------------------------------
# Act 4 - a viewer reads, and cannot write
# ---------------------------------------------------------------------------

act "a viewer reads, and cannot write"

narrate \
  "lloyd creates a database.  bob holds system:viewer, a role that lives in" \
  "code rather than in a table, and whose database grants stop at read."

call POST "/api/v1/orgs/$BEACHAPE/databases" "$LLOYD_BEACHAPE" '{"name":"alpha"}'
show 201 "POST /api/v1/orgs/\$BEACHAPE/databases as lloyd" '"name=" + .name'
ALPHA="$(jqr .id)"
remember ALPHA "$ALPHA"

call GET "/api/v1/databases/$ALPHA" "$BOB_TOKEN"
show 200 "GET  /api/v1/databases/\$ALPHA         as bob" '"name=" + .name + "  editable=" + (.editable | tostring)'

narrate \
  "" \
  "editable is not a column.  It is bob's update decision, computed for this" \
  "caller on this request, and returned alongside the row he may read." \
  ""

call DELETE "/api/v1/databases/$ALPHA" "$BOB_TOKEN"
show 403 "DELETE /api/v1/databases/\$ALPHA       as bob" '"message=\"" + .message + "\""'

narrate \
  "" \
  "403 here, not 404: bob may see alpha, so denying the delete tells him" \
  "nothing he did not already know."

reproduce \
  "curl -s -H \"Authorization: Bearer \$BOB_TOKEN\" \\" \
  "  \"\$BASE/api/v1/databases/\$ALPHA\" | jq '{name, editable}'"
pause

# ---------------------------------------------------------------------------
# Act 5 - a viewer cannot promote itself
# ---------------------------------------------------------------------------

act "a viewer cannot promote itself"

narrate \
  "The obvious attack: bob holds a token, and the endpoint that grants roles" \
  "takes a username.  He is a member, and he is the username in the path." \
  "He asks for system:manager."

call PUT "/api/v1/orgs/$BEACHAPE/members/$BOB_USER/policies" "$BOB_TOKEN" \
  '{"policyIds":["system:manager"]}'
show 403 "PUT  /api/v1/orgs/\$BEACHAPE/members/bob/policies as bob" '"message=\"" + .message + "\""'

narrate \
  "" \
  "Granting a role is itself a governed action: attaching a policy to a" \
  "membership needs membership:attach, which viewer does not carry.  The" \
  "permission system defends its own edges rather than trusting a caller" \
  "not to ask."

reproduce \
  "curl -s -X PUT -H \"Authorization: Bearer \$BOB_TOKEN\" \\" \
  "  -H 'Content-Type: application/json' -d '{\"policyIds\":[\"system:manager\"]}' \\" \
  "  \"\$BASE/api/v1/orgs/\$BEACHAPE/members/$BOB_USER/policies\" | jq"
pause

# ---------------------------------------------------------------------------
# Act 6 - an explicit DENY beats a wildcard ALLOW
# ---------------------------------------------------------------------------

act "an explicit DENY beats a wildcard ALLOW"

call POST "/api/v1/orgs/$BEACHAPE/databases" "$LLOYD_BEACHAPE" '{"name":"beta"}'
BETA="$(jqr .id)"
remember BETA "$BETA"
call POST "/api/v1/orgs/$BEACHAPE/databases" "$LLOYD_BEACHAPE" '{"name":"gamma"}'
GAMMA="$(jqr .id)"
remember GAMMA "$GAMMA"

narrate \
  "Two more databases exist: beta and gamma.  lloyd writes a custom policy -" \
  "org-owned data this time, not a system role - saying ALLOW update and" \
  "delete on every database, DENY update and delete on gamma."

deny_override_policy="$(jq -nc --arg gamma "$GAMMA" '{
  name: "edit-and-delete-all-except-gamma",
  statements: [
    {
      effect: "DENY",
      actions: [{type:"DATABASE",verb:"UPDATE"},{type:"DATABASE",verb:"DELETE"}],
      resources: [{type:"DATABASE", id:$gamma}]
    },
    {
      effect: "ALLOW",
      actions: [{type:"DATABASE",verb:"UPDATE"},{type:"DATABASE",verb:"DELETE"}],
      resources: [{type:"DATABASE", id:null}]
    }
  ]
}')"

call POST "/api/v1/orgs/$BEACHAPE/policies" "$LLOYD_BEACHAPE" "$deny_override_policy"
show 201 "POST /api/v1/orgs/\$BEACHAPE/policies  as lloyd" '"name=" + .name'
DENY_OVERRIDE="$(jqr .id)"
remember DENY_OVERRIDE "$DENY_OVERRIDE"

call PUT "/api/v1/orgs/$BEACHAPE/members/$BOB_USER/policies" "$LLOYD_BEACHAPE" \
  "$(jq -nc --arg p "$DENY_OVERRIDE" '{policyIds:["system:viewer",$p]}')"
show 200 "PUT  /api/v1/orgs/\$BEACHAPE/members/bob/policies as lloyd" '"policyIds=" + (.policyIds | length | tostring) + " attached"'

narrate \
  "" \
  "The evaluation follows AWS IAM: a matched DENY wins over any ALLOW, and" \
  "no match at all is still a deny.  Note the ordering in the document is" \
  "irrelevant - DENY wins because it is a DENY, not because it came first." \
  ""

call GET "/api/v1/orgs/$BEACHAPE/databases" "$BOB_TOKEN"
show 200 "GET  /api/v1/orgs/\$BEACHAPE/databases as bob" '"name=" + (.values | map(.name) | tostring)'
printf '  %-58s          %s\n' '' "editable=$(printf '%s' "$body" | jq -c '[.values[].editable]')"

narrate \
  "" \
  "That flag is the same update decision the edit is about to run, so it" \
  "cannot disagree with what happens next:" \
  ""

call PUT "/api/v1/databases/$ALPHA" "$BOB_TOKEN" '{"name":"alpha-edited"}'
show 200 "PUT  /api/v1/databases/\$ALPHA         as bob" '"name=" + .name'
call PUT "/api/v1/databases/$BETA" "$BOB_TOKEN" '{"name":"beta-edited"}'
show 200 "PUT  /api/v1/databases/\$BETA          as bob" '"name=" + .name'
call PUT "/api/v1/databases/$GAMMA" "$BOB_TOKEN" '{"name":"gamma-edited"}'
show 403 "PUT  /api/v1/databases/\$GAMMA         as bob" '"message=\"" + .message + "\""'

printf '\n'
call DELETE "/api/v1/databases/$ALPHA" "$BOB_TOKEN"
show 200 "DELETE /api/v1/databases/\$ALPHA       as bob" ''
call DELETE "/api/v1/databases/$BETA" "$BOB_TOKEN"
show 200 "DELETE /api/v1/databases/\$BETA        as bob" ''
call DELETE "/api/v1/databases/$GAMMA" "$BOB_TOKEN"
show 403 "DELETE /api/v1/databases/\$GAMMA       as bob" '"message=\"" + .message + "\""'

reproduce \
  "curl -s -H \"Authorization: Bearer \$BOB_TOKEN\" \\" \
  "  \"\$BASE/api/v1/orgs/\$BEACHAPE/databases\" | jq '.values[] | {name, editable}'" \
  "curl -s \"\$BASE/api/v1/orgs/\$BEACHAPE/policies/\$DENY_OVERRIDE\" \\" \
  "  -H \"Authorization: Bearer \$LLOYD_TOKEN\" | jq '.statements'"
pause

# ---------------------------------------------------------------------------
# Act 7 - conditions, in CEL
# ---------------------------------------------------------------------------

act "conditions, in CEL"

call POST "/api/v1/orgs/$BEACHAPE/databases" "$LLOYD_BEACHAPE" '{"name":"report-open"}'
REPORT_OPEN="$(jqr .id)"
remember REPORT_OPEN "$REPORT_OPEN"
call POST "/api/v1/orgs/$BEACHAPE/databases" "$LLOYD_BEACHAPE" '{"name":"report-locked"}'
REPORT_LOCKED="$(jqr .id)"
remember REPORT_LOCKED "$REPORT_LOCKED"
call POST "/api/v1/orgs/$BEACHAPE/databases" "$LLOYD_BEACHAPE" '{"name":"ledger"}'
LEDGER="$(jqr .id)"
remember LEDGER "$LEDGER"

narrate \
  "Three new databases: report-open, report-locked, ledger.  A grant need not" \
  "name a resource by id.  It can carry a CEL expression evaluated against" \
  "facts the server resolved, never against anything the request supplied." \
  "" \
  "  ALLOW update on any database  where resource.name.startsWith('report-')" \
  "  DENY  update on report-locked" \
  ""

# The CEL string is passed with --arg rather than inlined: it contains single
# quotes, which would close the shell's quoting around the jq program and
# silently mangle the condition into something the server rejects.
report_editors_policy="$(jq -nc \
  --arg locked "$REPORT_LOCKED" \
  --arg cond "resource.name.startsWith('report-')" '{
  name: "report-editors",
  statements: [
    {
      effect: "ALLOW",
      actions: [{type:"DATABASE",verb:"UPDATE"}],
      resources: [{type:"DATABASE", id:null}],
      condition: $cond
    },
    {
      effect: "DENY",
      actions: [{type:"DATABASE",verb:"UPDATE"}],
      resources: [{type:"DATABASE", id:$locked}]
    }
  ]
}')"

call POST "/api/v1/orgs/$BEACHAPE/policies" "$LLOYD_BEACHAPE" "$report_editors_policy"
show 201 "POST /api/v1/orgs/\$BEACHAPE/policies  as lloyd" '"name=" + .name'
REPORT_EDITORS="$(jqr .id)"
remember REPORT_EDITORS "$REPORT_EDITORS"

call PUT "/api/v1/orgs/$BEACHAPE/members/$BOB_USER/policies" "$LLOYD_BEACHAPE" \
  "$(jq -nc --arg p "$REPORT_EDITORS" '{policyIds:["system:viewer",$p]}')"
show 200 "PUT  /api/v1/orgs/\$BEACHAPE/members/bob/policies as lloyd" '"policyIds=" + (.policyIds | length | tostring) + " attached"'

narrate "" "Condition satisfied, nothing denying it.  The flag says yes; the edit lands." ""
call GET "/api/v1/databases/$REPORT_OPEN" "$BOB_TOKEN"
show 200 "GET  /api/v1/databases/\$REPORT_OPEN   as bob" '"name=" + .name + "  editable=" + (.editable | tostring)'
call PUT "/api/v1/databases/$REPORT_OPEN" "$BOB_TOKEN" '{"name":"report-open-edited"}'
show 200 "PUT  /api/v1/databases/\$REPORT_OPEN   as bob" '"name=" + .name'

narrate "" "Condition satisfied too, but an explicit DENY still overrides it." ""
call GET "/api/v1/databases/$REPORT_LOCKED" "$BOB_TOKEN"
show 200 "GET  /api/v1/databases/\$REPORT_LOCKED as bob" '"name=" + .name + "  editable=" + (.editable | tostring)'
call PUT "/api/v1/databases/$REPORT_LOCKED" "$BOB_TOKEN" '{"name":"report-locked-edited"}'
show 403 "PUT  /api/v1/databases/\$REPORT_LOCKED as bob" '"message=\"" + .message + "\""'

narrate "" "Condition false.  The viewer read still applies, but no update grant matches." ""
call GET "/api/v1/databases/$LEDGER" "$BOB_TOKEN"
show 200 "GET  /api/v1/databases/\$LEDGER        as bob" '"name=" + .name + "  editable=" + (.editable | tostring)'
call PUT "/api/v1/databases/$LEDGER" "$BOB_TOKEN" '{"name":"ledger-edited"}'
show 403 "PUT  /api/v1/databases/\$LEDGER        as bob" '"message=\"" + .message + "\""'

narrate \
  "" \
  "A CEL error evaluates to false rather than throwing, so a broken condition" \
  "grants nothing instead of granting everything."

reproduce \
  "curl -s -H \"Authorization: Bearer \$BOB_TOKEN\" \\" \
  "  \"\$BASE/api/v1/orgs/\$BEACHAPE/databases\" | jq '.values[] | {name, editable}'"
pause

# ---------------------------------------------------------------------------
# Act 8 - logout revokes a token that is still valid
# ---------------------------------------------------------------------------

act "logout revokes a token that is still perfectly valid"

exp_epoch="$(jwt_claims "$BOB_TOKEN" | jq -r .exp)"
now_epoch="$(date +%s)"
narrate \
  "bob's JWT is signed, unexpired and structurally impeccable: it has" \
  "$(( (exp_epoch - now_epoch) / 60 )) minutes left on it.  Nothing about the token itself will change." \
  ""

call GET /api/v1/me "$BOB_TOKEN"
show 200 "GET  /api/v1/me                       as bob" '"username=" + .username'

call POST /api/v1/logout "$BOB_TOKEN"
show 200 "POST /api/v1/logout                   as bob" ''

call GET /api/v1/me "$BOB_TOKEN"
show 401 "GET  /api/v1/me                       as bob (same token)" '"message=\"" + .message + "\""'

narrate \
  "" \
  "Same bytes, same signature, same expiry, now rejected.  Logout writes the" \
  "token's jti to a Redis blocklist for its remaining lifetime, so revocation" \
  "is immediate rather than waiting out the clock.  This is the price of" \
  "stateless tokens, paid deliberately."

reproduce \
  "echo \"\$BOB_TOKEN\" | jq -R 'split(\".\")[1] | gsub(\"-\";\"+\") | gsub(\"_\";\"/\") | @base64d | fromjson | {jti, exp}'" \
  "curl -s -o /dev/null -w '%{http_code}\\n' -H \"Authorization: Bearer \$BOB_TOKEN\" \\" \
  "  \"\$BASE/api/v1/me\"   # 401, and it will stay 401"
pause

# ---------------------------------------------------------------------------
# Act 9 - a policy that confines each member to what they made
# ---------------------------------------------------------------------------

act "a policy that confines each member to what they made"

signup_and_login "$LLOYD2_USER"; LLOYD2_TOKEN="$new_token"
signup_and_login "$BOB2_USER";   BOB2_LOGIN="$new_token"

call POST /api/v1/orgs "$LLOYD2_TOKEN" '{"name":"acme"}'
ACME="$(jqr .id)"
remember ACME "$ACME"
call POST /api/v1/sessions/switch-org "$LLOYD2_TOKEN" "$(jq -nc --arg o "$ACME" '{org:$o}')"
LLOYD2_ACME="$(jqr .token)"
remember LLOYD2_TOKEN "$LLOYD2_ACME"

narrate \
  "A fresh org, acme, and a policy with no ids in it at all:" \
  "" \
  "  ALLOW org:read" \
  "  ALLOW database:create                    (unconditioned)" \
  "  ALLOW database:read, update, delete      where resource.created_by == principal.id" \
  "" \
  "Create stays unconditioned on purpose.  A database being created has no" \
  "facts yet, so an ownership condition could never hold on it.  Ownership" \
  "gates what happens afterwards."

own_databases_policy='{
  "name": "own-databases",
  "statements": [
    {
      "effect": "ALLOW",
      "actions": [{"type":"ORG","verb":"READ"}],
      "resources": [{"type":"ORG","id":null}]
    },
    {
      "effect": "ALLOW",
      "actions": [{"type":"DATABASE","verb":"CREATE"}],
      "resources": [{"type":"DATABASE","id":null}]
    },
    {
      "effect": "ALLOW",
      "actions": [
        {"type":"DATABASE","verb":"READ"},
        {"type":"DATABASE","verb":"UPDATE"},
        {"type":"DATABASE","verb":"DELETE"}
      ],
      "resources": [{"type":"DATABASE","id":null}],
      "condition": "resource.created_by == principal.id"
    }
  ]
}'

call POST "/api/v1/orgs/$ACME/policies" "$LLOYD2_ACME" "$own_databases_policy"
show 201 "POST /api/v1/orgs/\$ACME/policies      as lloyd" '"name=" + .name'
OWN_DATABASES="$(jqr .id)"
remember OWN_DATABASES "$OWN_DATABASES"

call POST "/api/v1/orgs/$ACME/members" "$LLOYD2_ACME" \
  "$(jq -nc --arg u "$BOB2_USER" --arg p "$OWN_DATABASES" '{username:$u,policyIds:[$p]}')"
show 201 "POST /api/v1/orgs/\$ACME/members       as lloyd" '"policyIds=" + (.policyIds | tostring)'

narrate \
  "" \
  "bob holds that policy alone.  Deliberately no system:viewer: its" \
  "unconditional database:read would let him see everything and defeat the" \
  "point entirely."

call POST /api/v1/sessions/switch-org "$BOB2_LOGIN" "$(jq -nc --arg o "$ACME" '{org:$o}')"
BOB2_TOKEN="$(jqr .token)"
remember BOB2_TOKEN "$BOB2_TOKEN"

call POST "/api/v1/orgs/$ACME/databases" "$LLOYD2_ACME" '{"name":"ledger"}'
show 201 "POST /api/v1/orgs/\$ACME/databases     as lloyd" '"name=" + .name'
ACME_LEDGER="$(jqr .id)"
remember ACME_LEDGER "$ACME_LEDGER"

call POST "/api/v1/orgs/$ACME/databases" "$BOB2_TOKEN" '{"name":"bob-notes"}'
show 201 "POST /api/v1/orgs/\$ACME/databases     as bob" '"name=" + .name'
BOB_NOTES="$(jqr .id)"
remember BOB_NOTES "$BOB_NOTES"

narrate "" "His own row: the condition holds, so read, update and delete all work." ""

call GET "/api/v1/databases/$BOB_NOTES" "$BOB2_TOKEN"
show 200 "GET  /api/v1/databases/\$BOB_NOTES     as bob" '"name=" + .name + "  editable=" + (.editable | tostring) + "  createdBy=" + (.createdBy[0:8]) + "..."'

call PUT "/api/v1/databases/$BOB_NOTES" "$BOB2_TOKEN" '{"name":"bob-notes-edited"}'
show 200 "PUT  /api/v1/databases/\$BOB_NOTES     as bob" '"name=" + .name'

reproduce \
  "curl -s -H \"Authorization: Bearer \$BOB2_TOKEN\" \\" \
  "  \"\$BASE/api/v1/databases/\$BOB_NOTES\" | jq '{name, editable, createdBy}'"
pause

# ---------------------------------------------------------------------------
# Act 10 - a denied read is 404 on every verb, not just read
# ---------------------------------------------------------------------------

act "a denied read is 404 on every verb, not just read"

narrate \
  "lloyd's ledger fails bob's condition: he did not create it.  The" \
  "interesting part is not the read.  It is what update and delete do."

call GET "/api/v1/databases/$ACME_LEDGER" "$BOB2_TOKEN"
show 404 "GET    /api/v1/databases/\$ACME_LEDGER as bob" '"message=\"" + .message + "\""'
call PUT "/api/v1/databases/$ACME_LEDGER" "$BOB2_TOKEN" '{"name":"ledger-edited"}'
show 404 "PUT    /api/v1/databases/\$ACME_LEDGER as bob" '"message=\"" + .message + "\""'
call DELETE "/api/v1/databases/$ACME_LEDGER" "$BOB2_TOKEN"
show 404 "DELETE /api/v1/databases/\$ACME_LEDGER as bob" '"message=\"" + .message + "\""'

narrate \
  "" \
  "404 on all three, never 403.  Every gate's visibility is decided before" \
  "any permit runs, so an unwritable row bob cannot see never leaks a 403" \
  "that would confirm it exists.  Compare act 4: alpha was visible to bob," \
  "so its denied delete was a 403.  Same engine, different question." \
  "" \
  "Same rows, two callers, two different worlds:" \
  ""

call GET "/api/v1/orgs/$ACME/databases" "$BOB2_TOKEN"
show 200 "GET  /api/v1/orgs/\$ACME/databases     as bob" '"values=" + (.values | map(.name) | tostring)'
call GET "/api/v1/orgs/$ACME/databases" "$LLOYD2_ACME"
show 200 "GET  /api/v1/orgs/\$ACME/databases     as lloyd" '"values=" + (.values | map(.name) | tostring)'

narrate \
  "" \
  "The list is filtered, not flagged: rows bob cannot read are absent, not" \
  "present-and-locked.  And a rename does not transfer authorship, so bob" \
  "still owns what he renamed." \
  ""

call DELETE "/api/v1/databases/$BOB_NOTES" "$BOB2_TOKEN"
show 200 "DELETE /api/v1/databases/\$BOB_NOTES   as bob" ''

reproduce \
  "curl -s -o /dev/null -w '%{http_code}\\n' -X DELETE \\" \
  "  -H \"Authorization: Bearer \$BOB2_TOKEN\" \"\$BASE/api/v1/databases/\$ACME_LEDGER\"" \
  "curl -s -H \"Authorization: Bearer \$BOB2_TOKEN\" \\" \
  "  \"\$BASE/api/v1/orgs/\$ACME/databases\" | jq '.values[].name'"
pause

# ---------------------------------------------------------------------------
# Curtain
# ---------------------------------------------------------------------------

printf '\n'
rule
if [ "$mismatches" -eq 0 ]; then
  printf '  %sDone.  %d acts, 0 mismatches.%s\n' "$C_BOLD" "$TOTAL_ACTS" "$C_OFF"
else
  printf '  %sDone.  %d acts, %d MISMATCHES - the engine did not behave as narrated.%s\n' \
    "$C_BAD$C_BOLD" "$TOTAL_ACTS" "$mismatches" "$C_OFF"
fi
rule
printf '\n'
narrate \
  "Tokens and ids from this run are in $ENV_FILE, written as the demo went." \
  "At any [enter] pause, source it in another shell and that act's snippet runs" \
  "verbatim against live state:" \
  "" \
  "  source $ENV_FILE" \
  "" \
  "Past the curtain some of it is spent, by design: act 8 revoked \$BOB_TOKEN," \
  "and acts 6 and 10 deleted databases.  \$LLOYD_TOKEN, \$LLOYD2_TOKEN and" \
  "\$BOB2_TOKEN are still live." \
  "" \
  "The same two stories, in Java:" \
  "  src/test/java/com/beachape/aminam/integration/authz/UserScenarioTest.java" \
  "" \
  "The engine that decided all of it:" \
  "  src/main/java/com/beachape/aminam/domain/authz/services/DefaultPolicyEngine.java   (pure, no I/O)" \
  "  src/main/java/com/beachape/aminam/domain/authz/services/AuthorisationService.java  (gathers the facts)"

[ "$mismatches" -eq 0 ] || exit 1
