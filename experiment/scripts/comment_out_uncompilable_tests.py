import argparse
import os
import re
import subprocess
import sys

from commons import *


def extract_compile_error_messages(compile_log, tool, input_class):
    cmd = f'grep -w "{RESULTS_HOME}/{input_class}/{tool}-test/**/.*\.java:[0-9]\+: error:" {compile_log}'
    try:
        output = subprocess.check_output(cmd, shell=True)
        return list(filter(lambda x: x, output.decode("utf-8").split('\n')))
    except:
        # parsing failure
        return None


def calculate_enclosing_method(error_file_path, error_line):
    cmd = f'cat -n {error_file_path} | awk "/public void test/ {{error_line=\$0}} /[[:blank:]]+{error_line}[[:blank:]]+/ {{print error_line}}"'
    try:
        output = subprocess.check_output(cmd, shell=True)
        start_line = int(re.findall(r"\d+", output.decode("utf-8"))[0])
    except:
        # parsing failure
        print(f'{ERR} Failed to extract the starting line of method enclosing {error_file_path}:{error_line} {NC}')
        return None, None

    cmd = f'cat -n {error_file_path} | tac | awk "/[[:blank:]]+}}$/ {{error_line=\$0}} /[[:blank:]]+{error_line}[[:blank:]]+/ {{print error_line}}"'
    try:
        output = subprocess.check_output(cmd, shell=True)
        end_line = int(re.findall(r"\d+", output.decode("utf-8"))[0])
    except:
        # parsing failure
        print(f'{ERR} Failed to extract the last line of method enclosing {error_file_path}:{error_line} {NC}')
        return None, None

    return start_line, end_line


def comment_out_file_with_range(test_file, start_line, end_line):
    failure = False
    print(f'{INFO} Comment out line {start_line}~{end_line} of {test_file} ... {NC}')
    for line in range(start_line, end_line + 1):
        cmd = f'sed -i "{line}s/./\/\//" {test_file}'
        try:
            output = subprocess.call(cmd, shell=True)
        except:
            # sed failure
            print(f'{ERR} sed failure at {test_file}:{line} {NC}')
            failure = True
            break

    if not failure:
        print(f'{INFO} Successfully commented out line {start_line}~{end_line} of {test_file} {NC}')


parser = argparse.ArgumentParser()
parser.add_argument('--result-base', required=True, type=os.path.abspath)

args = parser.parse_args()
result_base = args.result_base
tool = os.path.basename(result_base).split('_')[0]

for benchmark_group in os.listdir(result_base):
    for benchmark_name in os.listdir(os.path.join(result_base, benchmark_group)):
        for experiment_id in os.listdir(os.path.join(result_base, benchmark_group, benchmark_name)):
            for input_class in os.listdir(os.path.join(result_base, benchmark_group, benchmark_name, experiment_id)):
                result_directory = os.path.join(result_base, benchmark_group, benchmark_name, experiment_id, input_class)
                compile_failure_mark = os.path.join(result_directory, '.compile_failure')
                if not os.path.exists(compile_failure_mark):
                    continue

                compile_log = os.path.join(result_directory, 'compile_log.txt')
                error_messages = extract_compile_error_messages(compile_log, tool, input_class)
                if error_messages == None:
                    continue

                start_end_pairs = set()
                for error_message in error_messages:
                    error_line = error_message.split(':')[1]
                    error_file_container_path = error_message.split(':')[0]
                    index = error_file_container_path.find(f'{tool}-test')
                    prefix = error_file_container_path[:index-1]
                    error_file_path = error_file_container_path.replace(prefix, result_directory)
                    start_line, end_line = calculate_enclosing_method(error_file_path, error_line)
                    if start_line and end_line:
                        start_end_pairs.add((error_file_path, start_line - 1, end_line))

                for error_file_path, start_line, end_line in start_end_pairs:
                    comment_out_file_with_range(error_file_path, start_line, end_line)

