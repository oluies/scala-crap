#!/bin/bash
# End-to-end checks of the built jar: that it is packaged correctly, and its
# real process exit codes. Usage: cli_test.sh <jar>
set -uo pipefail
jar=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
source=$work/src/O.scala
report=$work/out/scoverage/xmlReportAll.dest/scoverage.xml
edn=$work/.metrics/crap-scala.edn
mkdir -p "$(dirname "$source")" "$(dirname "$report")"
failures=0

expect() {
  local description=$1 expected=$2 actual
  shift 2
  java -jar "$jar" --root "$work" "$@" >/dev/null 2>&1
  actual=$?
  if [[ $actual == "$expected" ]]; then echo "ok    $description"
  else echo "FAIL  $description: exit $actual, expected $expected"; failures=1; fi
}

# a report of one statement inside m; the source is older than the report
reset_project() {
  printf 'object O { def m(y: Boolean) = if (y) 1 else 2 }\n' > "$source"
  printf '<s><statement source="%s" start="35" invocation-count="%s" ignored="false"/></s>\n' "$(cd "$work" && pwd -P)/src/O.scala" "$1" > "$report"
  touch -t 202001010000 "$source"
  touch -t 202101010000 "$report"
}

reset_project 1
expect "--help" 0 --help
expect "unknown option" 1 --nope
expect "within the threshold" 0 --threshold 2
if grep -q ':complexity 2, :coverage 100.0000, :crap 2.0000' "$edn"; then echo "ok    EDN output"
else echo "FAIL  EDN output: $(cat "$edn")"; failures=1; fi

reset_project 0
expect "above the threshold" 2 --threshold 5
expect "at the threshold" 0 --threshold 6

reset_project 1
touch -t 202201010000 "$source"
expect "stale report" 3 --threshold 100
expect "stale report allowed" 0 --threshold 100 --allow-stale

reset_project 1
rm "$report"
expect "missing report" 3 --threshold 100
expect "missing report without a gate" 0

reset_project 1
printf 'enum E:\n  case A\n  def f(x: Int): Int = if x > 0 then 1 else 2\n' > "$work/src/Three.scala"
touch -t 202001010000 "$work/src/Three.scala"
expect "Scala 3 source" 0 --threshold 100
rm "$work/src/Three.scala"

printf 'object {\n' > "$work/src/Bad.scala"
touch -t 202001010000 "$work/src/Bad.scala"
expect "unparseable source" 3 --threshold 100

exit $failures
