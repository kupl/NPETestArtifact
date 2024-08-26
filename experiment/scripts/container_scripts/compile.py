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

def extract_test_java_files(tool, input_class_name):
    test_java_files = []
    target_directory = os.path.join(RESULTS_HOME, input_class_name, f'{tool}-test')
    for root, dirs, files in os.walk(target_directory):
        for file in files:
            if (file.endswith('.java')
                and not file.endswith('RegressionTest.java')
                and not file.endswith('ErrorTest.java')):
                test_java_files.append(os.path.join(root, file))
    return test_java_files
    

if __name__ == '__main__':

    parser = argparse.ArgumentParser()
    parser.add_argument('--tool', required=True,
            choices=['evosuite', 'evosuiteRandom', 'randoop', 'npetest', 'npetestbase'])
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
        classpath = setup_compile_classpath(tool, benchmark_group, benchmark_name, input_class)
        test_java_files = extract_test_java_files(tool, input_class_name)
        if not test_java_files:
            # nothing is generated
            continue

        compile_target_directory = os.path.join(base_directory, f'{tool}-testclasses')
        os.makedirs(compile_target_directory, exist_ok=True)
        test_java_files_concat = ' '.join(test_java_files)
        compile_command = f'JAVA_HOME={java_home} {java_home}/bin/javac -nowarn -Xmaxerrs 10000 -classpath {classpath} {test_java_files_concat} -d {compile_target_directory}'
                
        compile_log_file = os.path.join(base_directory, 'compile_log.txt')

        with open(compile_log_file, 'w') as logger:
            logger.write(compile_command + '\n')
            failure = os.path.join(base_directory, '.compile_failure')
            try:
                subprocess.check_call(compile_command, shell=True, stdout=logger, stderr=subprocess.STDOUT)
                if os.path.exists(failure):
                    resolved_failure = os.path.join(base_directory, '.compile_failure_resolved')
                    open(resolved_failure, 'w').close()
            except subprocess.CalledProcessError:
                if os.path.exists(failure):
                    unresolved_failure = os.path.join(base_directory, '.compile_failure_unresolved')
                    open(unresolved_failure, 'w').close()
                else:
                    open(failure, 'w').close()

