#!/usr/bin/env bash

# console output
GREEN='\033[0;32m'
RED='\033[0;31m'

INFO="${GREEN}[INFO]"
ERR="${RED}[ERR ]"
NC="\033[0m"

SCRIPT_NAME="$(basename $0)"
SCRIPT_DIR="$(cd $(dirname $0); pwd)"

read -p "Are you running the script in \`sudo\` mode? (y/n) " yn
case $yn in
  [yY])
    echo "  ok, it will proceed"
    ;;
  [nN])
    echo ""
    echo -e "${ERR} This script should be run in \`sudo\` mode ${NC}"
    exit
    ;;
  *)
    echo ""
    echo -e "${ERR} Invalid response! ${NC}"
    exit
    ;;
esac

validate_env() {
  env_name=$1
  env_value=$2
  if [[ -z $env_value ]]; then
    echo -e "${ERR} $env_name should be passed (and preserved in \`sudo\` mode) ${NC}"
    echo -e "${ERR} -> \`$env_name=value [...] sudo --preserve-env $0\`"
    exit
  fi
}

validate_env "OUTPUT_DIR" $OUTPUT_DIR
validate_env "REPEAT" $REPEAT
validate_env "TOOL" $TOOL
validate_env "JOB" $JOB
validate_env "NULL" $NULL

if [[ -z $TIME_BUDGET ]]; then
  echo -e "${INFO} TIME_BUDGET is not set -> default one will be used (120s)${NC}"
  TIME_BUDGET=120
fi

if [[ -z $NPE_CLASS_ONLY ]]; then
  echo -e "${INFO} NPE_CLASS_ONLY is not set -> default one will be used (true)${NC}"
  NPE_CLASS_ONLY="--npe-class-only"
elif [[ $NPE_CLASS_ONLY -eq 1 ]]; then
  NPE_CLASS_ONLY="--npe-class-only"
else
  NPE_CLASS_ONLY=""
fi

if [[ -z $CRITERION ]]; then
  echo -e "${INFO} CRITERION is not set -> default one will be used ${NC}"
  CRITERION=""
else 
  CRITERION="--criterion $CRITERION"
fi




if [[ ! $(basename $OUTPUT_DIR) =~ $TOOL* ]]; then
  echo -e "${ERR} The basename of OUTPUT_DIR should have TOOL as a prefix! Passed values are, ${NC}"
  echo -e "${ERR} - OUTPUT_DIR: $OUTPUT_DIR ${NC}"
  echo -e "${ERR} - TOOL: $TOOL ${NC}"
  exit
fi

if [[ -z $PYTHON ]]; then
  echo -e "${INFO} PYTHON is not set -> default one will be used ${NC}"
  echo -e "${INFO} -> $(python3 --version) ${NC}"
  PYTHON=python3
fi

if ! $PYTHON -c 'import pandas' >/dev/null 2>&1 ; then
  echo -e "${ERR} pandas is not available in current python3 env. ${NC}"
  echo -e "${ERR} Two possible ways: ${NC}"
  echo -e "${ERR} 1. install pandas in global python3 env. ${NC}"
  echo -e "${ERR} 2. set PYTHON to python3 executable of venv with pandas ${NC}"
  exit
fi

$PYTHON generate_tests.py -j $JOB --tool $TOOL --null-probability $NULL --output-dir $OUTPUT_DIR --benchmark-list $SCRIPT_DIR/../../benchmarks/benchmark_list.txt --time-budget $TIME_BUDGET --repeat $REPEAT $NPE_CLASS_ONLY $CRITERION && \
  $PYTHON compile_tests.py --result-base $OUTPUT_DIR -j $JOB && \
  $PYTHON comment_out_uncompilable_tests_multi.py --result-base $OUTPUT_DIR --jobs $JOB && \
  $PYTHON compile_tests.py --result-base $OUTPUT_DIR -j $JOB && \
  $PYTHON replay_tests.py --result-base $OUTPUT_DIR -j $JOB && \
  $PYTHON evaluate_tests.py --result-base $OUTPUT_DIR --experiment-count $REPEAT $NPE_CLASS_ONLY

