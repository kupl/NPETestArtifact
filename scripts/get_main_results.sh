#!/usr/bin/env bash

# GENERATE csv files for each tool and benchmark (# of found known NPEs)
current=$(pwd)

cp ${current}/benchmarks/benchmark_list_ori.txt ${current}/benchmarks/benchmark_list.txt
rm ${current}/result/*.csv

${current}/scripts/check_log_file.sh ./result/evosuite/evosuite_opt_result ./result/evosuite_result.csv
${current}/scripts/check_log_file.sh ./result/npetest/npetest_result ./result/npetest_result.csv
${current}/scripts/check_log_file.sh ./result/randoop/randoop_result ./result/randoop_result.csv

${current}/scripts/merge_csv_files.sh ${current}/result/main_merge.csv

python3 ${current}/scripts/build_table.py ${current}/result/main_merge.csv ${current}/result/main_result.txt
