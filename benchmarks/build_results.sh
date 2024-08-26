#!/usr/bin/env bash

i=1
for img in $(docker images --filter=reference='jiseongg301/npetest_benchmark:*' -aq); do
  echo "-------"
  echo -n "$i. "
  docker run --rm $img 'find /tmp/build_logs -name build_result.json; jq -r . /tmp/build_logs/**/**/build_result.json'
  i=$((i+1))
done

