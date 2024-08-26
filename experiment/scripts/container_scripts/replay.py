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
from utils import *


def extract_test_classes(tool, input_class_name):
    test_classes = []
    test_directory = os.path.join(RESULTS_HOME, input_class_name, f'{tool}-testclasses')
    for root, dirs, files in os.walk(test_directory):
        for file in files:
            if (file.endswith('.class')
                    and not file.endswith('RegressionTest.class')
                    and not file.endswith('ErrorTest.class')
                    and not file.endswith('scaffolding.class')):
                fullpath = os.path.join(root, file)
                test_class_name = fullpath[len(test_directory) + 1:].rstrip('.class').replace('/', '.')
                test_classes.append(test_class_name)
    return test_classes
    

if __name__ == '__main__':

    parser = argparse.ArgumentParser()
    parser.add_argument('--tool', required=True,
            choices=['evosuite', 'evosuiteRandom','randoop', 'npetest', 'npetestbase'])
    parser.add_argument('--benchmark-group', required=True, type=str)
    parser.add_argument('--benchmark-name', required=True, type=str)

    args = parser.parse_args()
    tool = args.tool
    benchmark_group = args.benchmark_group
    benchmark_name = args.benchmark_name
    
    benchmark_metadata = os.path.join(METADATA_DIR, benchmark_group, benchmark_name, 'npetest.json')

    with open(benchmark_metadata, 'r') as f:
        metadata = json.load(f)
        input_classes = metadata['experiment_config']['input_classes']
        java_version = metadata['build_info']['java_version']

    java_home = get_jdk(java_version)
    for input_class in input_classes:
        input_class_name = input_class['class_name']
        base_directory = os.path.join(RESULTS_HOME, input_class_name)
        compile_classpath = setup_compile_classpath(tool, benchmark_group, benchmark_name, input_class)
        test_classpath = os.path.join(base_directory, f'{tool}-testclasses')
        classpath = f'{compile_classpath}:{test_classpath}'
        test_classes = extract_test_classes(tool, input_class_name)
        if not test_classes:
            # nothing is generated
            continue

        replay_log_base = os.path.join(base_directory, 'replay_logs')
        os.makedirs(replay_log_base, exist_ok=True)

        test_replay_directories = []
        replay_commands = [] 
        replay_log_files = []
        for test_class in test_classes:
            test_replay_directory = os.path.join(replay_log_base, test_class)
            os.makedirs(test_replay_directory, exist_ok=True)
            test_replay_directories.append(test_replay_directory)

            replay_command = f'JAVA_HOME={java_home} {java_home}/bin/java -classpath {classpath} org.junit.runner.JUnitCore {test_class}'
            replay_log_file = os.path.join(test_replay_directory, 'replay_log.txt')
            replay_commands.append(replay_command)
            replay_log_files.append(replay_log_file)


        os.chdir('/tmp')
        for test_replay_directory, replay_command, replay_log_file in zip(test_replay_directories, replay_commands, replay_log_files):
            with open(replay_log_file, 'w') as logger:
                logger.write(replay_command + '\n')
                try:
                    subprocess.check_call(replay_command, shell=True, stdout=logger, stderr=subprocess.DEVNULL)
                except subprocess.CalledProcessError:
                    failure = os.path.join(test_replay_directory, '.test_failure')
                    open(failure, 'w').close()

