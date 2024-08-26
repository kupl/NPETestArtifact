#!/usr/bin/env bash

SCRIPT_DIR="$(cd $(dirname $0) && pwd)"

DOCKERS_DIR="${SCRIPT_DIR}/dockers"
BENCHMARK_LIST="${SCRIPT_DIR}/../benchmarks/benchmark_list.txt"

while IFS= read -r line; do
  benchmark_group=$(echo $line | cut -d':' -f1)
  benchmark_name=$(echo $line | cut -d':' -f2)

  if [[ $benchmark_group =~ ^#.* ]]; then
    continue
  fi

  mkdir -p "${DOCKERS_DIR}/${benchmark_group}/${benchmark_name}"
  DF="${DOCKERS_DIR}/${benchmark_group}/${benchmark_name}/Dockerfile"
  
  echo    "FROM artifact/testing_tools as testing_tools" > $DF
  echo -e "FROM artifact/npetest_benchmark:$(echo $benchmark_name | tr [[:upper:]] [[:lower:]])\n" >> $DF

  echo -e "COPY --from=testing_tools /tools/evosuite/ /tools/evosuite" >> $DF
  echo -e "COPY --from=testing_tools /tools/randoop/ /tools/randoop" >> $DF
  echo -e "COPY --from=testing_tools /tools/npetest/ /tools/npetest" >> $DF

  echo -e "WORKDIR /subject\n" >> $DF
  echo    "ENTRYPOINT [ \"/bin/bash\", \"-l\", \"-c\" ]" >> $DF
  echo    "CMD [ \"/bin/bash\" ]" >> $DF

done < $BENCHMARK_LIST

