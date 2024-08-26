import argparse
import os
import shutil
import subprocess
import datetime
import random
import time

from multiprocessing.pool import ThreadPool
from subprocess import TimeoutExpired

from commons import *
from tools import *

def build_job(tool, experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only, criterion):
    if 'evosuiteRandom' in tool:
        return EvoSuiteRandom(experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only, criterion)
    elif 'evosuite' in tool:
        return EvoSuite(experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only, criterion)
    elif tool == 'randoop':
        return Randoop(experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only)
    elif tool == 'npetestbase':
        return NPETestbase(experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only)
    elif tool == 'npetest':
        return NPETest(experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only)


if __name__ == '__main__':

    parser = argparse.ArgumentParser()
    parser.add_argument('--tool', required=True,
            choices=['evosuite', 'evosuiteRandom', 'randoop', 'npetest', 'npetestbase'])
    parser.add_argument('--null-probability', required=True, type=float)
    parser.add_argument('--time-budget', required=False, default=180, type=int)
    parser.add_argument('--benchmark-group', required=True, type=str)
    parser.add_argument('--benchmark-name', required=True, type=str)
    parser.add_argument('--experiment-id', required=True, type=str)
    parser.add_argument('--npe-class-only', required=False, action='store_true')
    parser.add_argument('--criterion', required=False, type=str)

    args = parser.parse_args()
    tool = args.tool
    null_probability = args.null_probability
    time_budget = args.time_budget
    benchmark_group = args.benchmark_group
    benchmark_name = args.benchmark_name
    experiment_id = args.experiment_id
    npe_class_only = args.npe_class_only
    criterion = args.criterion

    test_generator = build_job(tool, experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only, criterion)
    test_generator.setup_commands()

    test_generator_commands = test_generator.test_generator_commands
    test_generation_log_files = test_generator.test_generation_log_files

    for command, log_file in zip(test_generator_commands, test_generation_log_files):

        with open(log_file, 'a') as logger:
            logger.write(command + '\n')
            start = datetime.datetime.now().timestamp()
            p = subprocess.Popen(command, shell=True, stdout=logger, stderr=subprocess.STDOUT)
            # If commands run more than two times of assigned budget, stop
            try:
                p.wait(timeout=10.0*test_generator.time_budget)
            except TimeoutExpired:
                p.kill()
            end = datetime.datetime.now().timestamp()
            logger.write(f'Actual Running Time: {end - start:.2f}\n')


