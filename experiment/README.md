# Setup Docker Environment

The experimental environment will be setup on top of prepared docker image where all required JDKs, Maven, and test case generators (EvoSuite, Randoop, NPETest) are installed.

## Generate Dockerfiles for Each Subject Program
```bash
./generate_dockerfiles

### output
# dockers
# |
# |-- Bears
# |   |-- Bears-121
# |   |-- ...
# |   `-- Bears-88
# |-- NPEX
# |   |-- ...
# .   |-- ...
# .    `-- ZooKeeper-ef3649f5
```

## Build Docker Images

```bash
python3 build_docker_parallel.py -j <thread_count>
```

Now all environments are prepared as below.
```bash
docker images --filter-reference="*/npetest_evaluation:*"

# REPOSITORY           TAG       ...
# npetest_evaluation   csv-9     ...
# ...
```

# Experiments
Each executable script in `scripts` directory is dedicated to a specific step in experiments, the name of which is self-explanatory.

## End-to-end Script (Solely use tool on benchmarks)
You might not need to understand all of the scripts in detail. Running `run_experiment.sh` will be enough. Given a testing tool, it will generate tests, and replays them.
Usage:
```
bash -c 'yes | \
  JOB=10 \
  TIME_BUDGET=120 \
  NPE_CLASS_ONLY=1 \
  PYTHON=python3 \
  TOOL=npetest \
  REPEAT=20 \
  OUTPUT_DIR="${HOME}/ase2024/result/npetest/npetest_[NAME FOR EXPERIMENT]" \
  ./run_experiment.sh
```
* TOOL : This option takes one of the following options: "npetest", "evosuite", and "randoop".
* OUTPUT_DIR : This option must follow the **TOOL**. For example, if we run evosuite, OUTPUT_DIR will be as follows:
 ```
  OUTPUT_DIR="${HOME}/ase2024/result/evosuite/evosuite_[NAME FOR EXPERIMENT]"
 ```
