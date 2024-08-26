# NPETest
This repository contains the source codes for NPETest and other resources inclulding experimental data 
to support the paper "Effective Unit Test Generation for Java Null Pointer Exceptions".

[paper](./ase2024-paper796.pdf): The accepted version 

## Requirements
See [REQUIREMENTS.md](./REQUIREMENTS.md).

## Installation
See [INSTALL.md](./INSTALL.md).

If you want to perform a small experiment we provide, you can skip the installation step. 

## Benchmarks
The directory [benchmarks](./benchmarks) contains all benchmarks.
We conducted all experiments in docker system, and we provide the Dockerfile for each benchmark.
* [metadata](./benchmarks/metadata) : contains the known npe information of each benchmark

The directories below are generated by using script files. For more details, please refer [README](./benchmarks/README.md).
* [dockers](./benchmarks/dockers) : contains Dockerfile files for each benchmark 
* [subject_gits](https://drive.google.com/file/d/11I7m6zamJA5UlWmFlChP6eA76DGozBMb/view?usp=sharing): contains the buggy version of each benchmark.
You can download subject_gits.tar.gz file from the following [link](https://drive.google.com/file/d/11I7m6zamJA5UlWmFlChP6eA76DGozBMb/view?usp=sharing).
  
## Raw Data for tables in the paper.
* [rq1_result](./rq1_result.xlsx) : contains the result tables for rq1
* [rq2_result](./rq2_result) : contains the result tables for rq2

For the raw data, we provide additional google drive links for downloading all datas. 
(The data contains all test cases generated by the tools used in our evaluation; hence the size of the data is too large to upload on github.)

* [NPETest](https://drive.google.com/file/d/1oToD6Ecmq8vyDikLEQRqVIJYOkU4-tvO/view?usp=sharing): Google drive link for downloading resultso of NPETest for all benchmarks, containing the generated test-cases.
* [EvoSuite](https://drive.google.com/file/d/1PKAYyxqsakE2R9706zSuv3-a0sBRzmBr/view?usp=sharing): Google drive link for downloading resultso of EvoSuite with fine-tuned options for all benchmarks, containing the generated test-cases.
* [EvoSuite_Def](https://drive.google.com/file/d/14AJjoDCm-GYPwLrNaM6g0uvzYT9ltHE3/view?usp=sharing): Google drive link for downloading resultso of EvoSuite with default options for all benchmarks, containing the generated test-cases.
* [Randoop_npex](https://drive.google.com/file/d/1mevPl4U9vwRtl0b7bdCu6EpgaMXamx6s/view?usp=sharing): Google drive link for downloading Randoop results for NPEX benchmarks, containing the generated test-cases.
* [Randoop_other](https://drive.google.com/file/d/1i7M7gS0gvp2H9z5BX1ntPx3OQf8PnFcf/view?usp=sharing): Google drive link for downloading Randoop results for Bears, BugSwarm, Defects4J, Genesis benchmarks, containing the generated test-cases.

For Randoop, we only upload the log files for each benchmark listed in tables since including all test cases generated by Randoop is too large (more than 50GB). 

## Performing small experiments

We provide an example instruction which conducts a short experiment running NPETest, EvoSuite, and Randoop on 4 benchmark programs with 5 trials during 5 minutes:
**BungeeCord-1303**, **Feign-9c5a**, **Fastjson-650a**, and **Math-70**.
Note that conducting experiments for all benchmarks (Table 3 in our paper) takes at least 675 hours (5 minutes * 108 benchmarks * 25 trials * 3 tools + additional hours for building benchmarks and tools) on a single core. 
Once the setup instruction is successfully done(or using our VM image), you can perform the small experiments with the following command:

```
./scripts/running_simple.sh
```

If all processes are completely done, users can eventually see the testing process logs below:
```
...
[INFO] Running EVOSUITE(NPE_CLASS)_34e5[subject=NPEX:Feign-9c5a52d6] (xx/xx-xx:xx:xx)...
[INFO] Running EVOSUITE(NPE_CLASS)_241s[subject=NPEX:Feign-9c5a52d6] (xx/xx-xx:xx:xx)...
[INFO] Running EVOSUITE(NPE_CLASS)_362a[subject=NPEX:Feign-9c5a52d6] (xx/xx-xx:xx:xx)...
[INFO] Running EVOSUITE(NPE_CLASS)_f213[subject=NPEX:Feign-9c5a52d6] (xx/xx-xx:xx:xx)...
[INFO] Running EVOSUITE(NPE_CLASS)_a142[subject=NPEX:Feign-9c5a52d6] (xx/xx-xx:xx:xx)...

...
```

When the experiments are done, please enter the following command to obtain the result table and venn-diagram of unique NPEs detected by each tool:
```
./scripts/build_simple_table.sh
```


You can see the tables in "result" directory.

```
cat ./result/simple_result.txt
```




* Note that the instructions above were performed on our VM image.
* If users want to perform a small experiment on their local machine, download [subject_gits] and follow the first instruction in [README.md](./benchmarks/README.md). Then, follow the instruction for "**Performing small experiment**".


## Reproduction of Results in the Paper

We provide a python script to reproduce the results of Table 3 in the paper.
We expect all commands are executed on `~/Workspace/ase2024` directory.

### Generating Table with the existing results. 

As performing all experiments in our paper takes some time (e.g., approximately 700 hours on a single core), 
we provide a script file to reproduce the results of Table 3 in the paper with the experimental results.

If users are not using VM image we provide, users must download the "**raw data**", [NPETest](https://drive.google.com/file/d/1oToD6Ecmq8vyDikLEQRqVIJYOkU4-tvO/view?usp=sharing), [EvoSuite](https://drive.google.com/file/d/1PKAYyxqsakE2R9706zSuv3-a0sBRzmBr/view?usp=sharing), [Randoop_npex](https://drive.google.com/file/d/1mevPl4U9vwRtl0b7bdCu6EpgaMXamx6s/view?usp=sharing), [Randoop_other](https://drive.google.com/file/d/1i7M7gS0gvp2H9z5BX1ntPx3OQf8PnFcf/view?usp=sharing), for tables mentioned above,
and place it in result directory. 

For those who are using VM image, they need to download **raw data** for Randoop only.

More specifically, please follow the commands below.

```
mkdir npetest ./result/
mkdir evosuite ./result/
mkdir randoop ./result/

Unzip the zip files and place the result directory in the corresponding directory.
mv npetest_result ./result/npetest/
mv evosuite_opt_result ./result/evosuite/
...
```

Once all raw data is placed on the appropriate directory, use the following command to get the result table.

```
./scripts/get_main_results.sh
```

You can see the tables in "result" directory.

```
cat ./result/main_result.txt

NPEX:
Tool                    evosuite  npetest  randoop
Project
Activiti-c45d6c3c(0)       0          24        0
Aries_JPA-97cb979d(0)    100          80        0
Avro-a3e05bee(0)         100         100       100
...

```


### Generating Tables from a scratch

Assume that the users follow the installation instructions at [INSTALL.md](./INSTALL.md).

#### Running NPETest on all benchmarks
After all installation is complete, you can run NPETest on all benchmark programs by using the commands below:

```
bash -c 'yes | JOB=2 TIME_BUDGET=300 \
  NPE_CLASS_ONLY=1 \
  PYTHON=python3 \
  TOOL=npetest \
  REPEAT=25 \
  OUTPUT_DIR="~/ase2024/result/npetest/npetest_test" \
  ./run_experiment.sh
```
This command runs NPETest 25 times on all benchmarks built by [experiment](./experiment) process, with a time budget of 5 minutes for each benchmark.

You may change the path for <OUTPUT_DIR>, but the name of parent directory must be "npetest" and the name of the last directory also starts with "npetest_".
For example, the command can be changed as below:

```
bash -c 'yes | JOB=2 TIME_BUDGET=300 \
  NPE_CLASS_ONLY=1 \
  PYTHON=python3 \
  TOOL=npetest \
  REPEAT=25 \
  OUTPUT_DIR="./result/npetest/npetest_mine" \
  ./run_experiment.sh
```

Once all experiments are done, you can make the "result.csv" file which represents the number of NPEs detected by using the following command:

```
./scripts/check_log_file.sh ./result/npetest/npetest_test ./result/npetest_test_result.csv
```
Note that the "csv file" must be inside "result" directory.


#### Running other tools on all benchmarks
You can run EvoSuite and Randoop with the same command used for NPETest as follows:

```
bash -c 'yes | JOB=2 TIME_BUDGET=300 \
  NPE_CLASS_ONLY=1 \
  PYTHON=python3 \
  TOOL=evosuite \
  REPEAT=25 \
  OUTPUT_DIR="~/ase2024/result/evosuite/evosuite_test" \
  ./run_experiment.sh
```

Please remind that you need to generate "result.csv" file for each tool after the experiments are done.
Note that the "csv file" must be inside "result" directory.

```
./scripts/check_log_file.sh ./result/evosuite/evosuite_test ./result/evosuite_test_result.csv
```


#### Generating Table

After generating "XX_result.csv" files for all tools (NPETest, EvoSuite, and Randoop), you need to merge the csv files into one csv file as follows:

```
./scripts/merge_csv_files.sh ./result/merged_result.csv
```
Note that all "XX_result.csv" files must be located in "result" directory.

Then, you can obtain the result table with the following python code.
```
python3 ./scripts/build_table.py ./result/merged_result.csv ./result/final_result.txt
```
This script csollects all the results in the merged csv file and pretty-print the result as a table.



