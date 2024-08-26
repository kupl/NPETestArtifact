#!/usr/bin/env bash

current=$(pwd)

echo ${current}

# BUILD DOCKER IMAGES FOR EACH TOOL
cd ${current}/tools_docker && ./build_tool_docker.sh

# BUILD DOCKER IMAGES FOR BENCHMARKS
# Remember to download subject_gits.tar.gz first and place it inside benchmarks directory
cd ${current}/benchmarks

tar -xvf subject_gits.tar.gz

./generate_dockerfile.sh

python3 ./build_benchmark_parallel.py -j 2 --testing-type 1

# BUILD DOCKER IMAGES FOR TESTING
cd ${current}/experiment
./generate_dockerfile.sh
python3 ./build_docker_parallel.py -j 2

# RUNNING EXPERIMENTS
cd ${current}/experiment/scripts
bash -c 'yes | NULL=0.1 JOB=2 TIME_BUDGET=300 NPE_CLASS_ONLY=1 PYTHON=python3 TOOL=evosuite REPEAT=5 OUTPUT_DIR=/home/npetest/ase2024/result/evosuite/evosuite_simple ./run_experiment.sh'
bash -c 'yes | NULL=0.1 JOB=2 TIME_BUDGET=300 NPE_CLASS_ONLY=1 PYTHON=python3 TOOL=npetest REPEAT=5 OUTPUT_DIR=/home/npetest/ase2024/result/npetest/npetest_simple ./run_experiment.sh'
bash -c 'yes | NULL=0.1 JOB=2 TIME_BUDGET=300 NPE_CLASS_ONLY=1 PYTHON=python3 TOOL=randoop REPEAT=5 OUTPUT_DIR=/home/npetest/ase2024/result/randoop/randoop_simple ./run_experiment.sh'
