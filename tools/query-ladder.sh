#!/bin/sh
#
# Copyright 2022-2026 benelog GmbH & Co. KG
#
#     Licensed under the Apache License, Version 2.0 (the "License");
#     you may not use this file except in compliance with the License.
#     You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#     Unless required by applicable law or agreed to in writing, software
#     distributed under the License is distributed on an "AS IS" BASIS,
#     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#     See the License for the specific language governing permissions and
#     limitations under the License.
#

# Part of OpenEPCIS. Ask an EPCIS repository the same questions, in the same
# order, every time — so that "the query side is broken" can be replaced by a
# line and a number.
#
#   TOKEN=... tools/query-ladder.sh http://localhost:8080 [eventID] [epc]
#
# Every URL here is quoted. That is not style: an unquoted query string is how
# GE_recordTime=2026-08-27T00:00:00.000Z&perPage=3 once arrived at the server as
# a single parameter value and came back as a 500 that looked like the
# repository's fault.
set -eu

BASE=${1:-${EPCIS_BASE:-http://localhost:8080}}
EVENT_ID=${2:-${EPCIS_EVENT_ID:-}}
EPC=${3:-${EPCIS_EPC:-}}
: "${TOKEN:?set TOKEN to an access token holding the query role}"

BASE=$(printf '%s' "$BASE" | sed 's#/*$##')

# One rung: describe it, call it, print the status and how many events came back.
rung() {
    what=$1
    url=$2
    body=$(mktemp)
    status=$(curl -s -o "$body" -w '%{http_code}' \
        -H "Accept: application/json" \
        -H "Authorization: Bearer $TOKEN" \
        "$url" || echo "000")
    count=$(python3 -c '
import json,sys
try:
    d = json.load(open(sys.argv[1]))
except Exception:
    print("-"); raise SystemExit
body = d.get("epcisBody") or {}
events = body.get("eventList")
if events is None:
    events = ((body.get("queryResults") or {}).get("resultsBody") or {}).get("eventList")
print(len(events) if events is not None else "-")
' "$body" 2>/dev/null || echo "-")
    printf '%-58s  %s  events=%s\n' "$what" "$status" "$count"
    # A repository that redirects a machine client is answering a different
    # question than the one asked; say so rather than counting zero events.
    if [ "$status" = "302" ] || [ "$status" = "303" ]; then
        printf '%-58s  %s\n' "  ^ redirected — the request was not authenticated" ""
    fi
    if [ "$status" -ge 400 ] 2>/dev/null; then
        head -c 300 "$body" | tr -d '\n' | sed 's/^/  ^ /'
        echo
    fi
    rm -f "$body"
}

echo "EPCIS query ladder against $BASE"
echo

# 0. What this identity is. The roles sit flat under "roles" on this realm, not
#    under realm_access — looking in the wrong place diagnoses the wrong thing.
python3 - "$TOKEN" <<'PY'
import base64, json, sys
payload = sys.argv[1].split(".")[1]
payload += "=" * (-len(payload) % 4)
claims = json.loads(base64.urlsafe_b64decode(payload))
roles = claims.get("roles") or claims.get("realm_access", {}).get("roles") or []
print("identity : %s" % claims.get("preferred_username", claims.get("sub", "?")))
print("roles    : %s" % ", ".join(sorted(roles)) if roles else "roles    : none found")
for needed in ("query", "capture"):
    print("  %-8s %s" % (needed, "yes" if needed in roles else "NO — this rung will read empty, not 403"))
PY
echo

rung "1. anything at all"                 "$BASE/events?perPage=1"
rung "2. a whole page"                    "$BASE/events?perPage=20"
rung "3. by record time (last 24h)"       "$BASE/events?perPage=20&GE_recordTime=$(date -u -d '24 hours ago' '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || date -u -v-24H '+%Y-%m-%dT%H:%M:%SZ')"
rung "4. by event time (last 24h)"        "$BASE/events?perPage=20&GE_eventTime=$(date -u -d '24 hours ago' '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || date -u -v-24H '+%Y-%m-%dT%H:%M:%SZ')"

if [ -n "$EVENT_ID" ]; then
    # The most direct question there is: does this document exist for this
    # identity? An empty list can mean many things; a 404 here means one.
    rung "5. one event by id"             "$BASE/events/$(python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$EVENT_ID")"
fi

if [ -n "$EPC" ]; then
    ENC=$(python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$EPC")
    # Instance-level and class-level are different parameters. A GTIN with a
    # wildcard handed to MATCH_epc matches nothing and says nothing about why.
    rung "6. MATCH_epc, full instance URI"  "$BASE/events?perPage=20&MATCH_epc=$ENC"
    rung "7. MATCH_epcClass, class URI"     "$BASE/events?perPage=20&MATCH_epcClass=$ENC"
    rung "8. the resource path DDM uses"    "$BASE/epcs/$ENC/events?perPage=20"
fi

echo
echo "Then the decider: capture one document with"
echo "  GS1-Capture-Error-Behaviour: proceed"
echo "and run rung 1 again straight away. If the 'proceed' event is found and an"
echo "ordinary one is not, the asynchronous visibility update is the cause and"
echo "everything else follows from that. If neither is found, it is the"
echo "document-level security or the index pattern, and the next look has to be"
echo "inside OpenSearch: metadata.visible and metadata.capturedBy.defaultGroup"
echo "against the pattern the read path builds."
