#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/.."
./build.sh >/dev/null || { echo "build failed" >&2; exit 1; }
. tools/smoke-lib.sh

dir="$(mktemp -d)"
cat >"$dir/output.xml" <<'XML'
<robot><statistics><total><stat pass="8" fail="1" skip="2">All Tests</stat></total></statistics></robot>
XML
printf '{"pattern": "**/output.xml", "skipMarkingUnstable": "true"}' >"$dir/input.json"
run "$dir" >/dev/null 2>&1 || fail "run failed: $(cat "$dir/output.json")"
[ "$(output "$dir" total)" = "11" ] || fail "total: $(cat "$dir/output.json")"
[ "$(output "$dir" failed)" = "1" ] || fail "failed"
[ "$(output "$dir" skipped)" = "2" ] || fail "skipped"
[ -f "$dir/out/robot/index.html" ] || fail "report"

dir="$(mktemp -d)"; printf '<robot></robot>' >"$dir/output.xml"
printf '{"pattern": "**/output.xml"}' >"$dir/input.json"
run "$dir" >/dev/null 2>&1 && fail "no statistics must fail"

echo "smoke ok: robot totals, report, empty statistics"
