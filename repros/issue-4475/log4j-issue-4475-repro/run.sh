#!/usr/bin/env bash
# Validate the reporter's config against the published schema, then load it with Log4j.
#   ./run.sh            # Log4j 2.26.1
#   ./run.sh 2.25.5     # any other release
set -uo pipefail
cd "$(dirname "$0")"
VERSION="${1:-2.26.1}"
XSD=../output/log4j-config-2.xsd
curl -sS -D ../output/xsd-headers.txt -o "$XSD" https://logging.apache.org/xml/ns/log4j-config-2.xsd
grep -i '^last-modified' ../output/xsd-headers.txt
for f in log4j2.xml log4j2-no-additivity.xml log4j2-root-additivity.xml; do
  echo "== xmllint $f"
  xmllint --noout --schema "$XSD" "src/main/resources/$f" 2>&1
done
echo "== Log4j $VERSION loads log4j2.xml"
mvn -q -Dlog4j.version="$VERSION" compile exec:exec 2>&1
