#!/bin/bash -e

sbt --client reload

version=$(sbt --client 'show rootJVM/version' 2>/dev/null \
  | sed 's/\x1B\[[0-9;]*[a-zA-Z]//g' \
  | grep '\[info\] *' \
  | sed -n 's/.*\[info\] *//p' \
  | tail -n 1 \
  | sed $'s/^[[:space:][:cntrl:]]*//;s/[[:space:][:cntrl:]]*$//')

echo "Project version: $version"

rm -f argon-async-util-*.zip
sbt --client clean
sbt --client publishSigned

pushd jvm/target/repo
zip -r "../../../argon-async-util-$version.zip" .
popd

pushd js/target/repo
zip -ur "../../../argon-async-util-$version.zip" .
popd
