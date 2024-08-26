import argparse
import os
import re
import subprocess
import sys
import multiprocessing

from commons import *


def extract_compile_error_messages(compile_log, tool):
    cmd = f'grep -w "{RESULTS_HOME}/{tool}-test/**/.*\.java:[0-9]\+: error:" {compile_log}'
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


def comment_out_given_class(classname):
    result_directory = os.path.join(result_base, classname)
    compile_failure_mark = os.path.join(result_directory, '.compile_failure')
    if not os.path.exists(compile_failure_mark):
        return

    compile_log = os.path.join(result_directory, 'compile_log.txt')
    error_messages = extract_compile_error_messages(compile_log, tool)
    if error_messages == None:
        return

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

# For Logging Comments process... 
# All classname done for comments will be written in {result_base}/comment_output.txt
def comment_out_process(classname):
    comment_out_given_class(classname)

    # cmd = f'echo {classname} >> {result_base}/comment_output.txt'
    try:
        output = subprocess.call(cmd, shell=True)
    except:
        print(f'ERROR')
        failure = True


parser = argparse.ArgumentParser()
parser.add_argument('--result-base', required=True, type=os.path.abspath)
parser.add_argument('--jobs', required=True, type=int)

args = parser.parse_args()
result_base = args.result_base
program = os.path.basename(os.path.dirname(result_base))
tool = os.path.basename(os.path.dirname(os.path.dirname(result_base)))

job_num = args.jobs
class_list = os.listdir(result_base)

pool = multiprocessing.Pool(processes=job_num)
#pool.map(comment_out_given_class, class_list)
pool.map(comment_out_process, class_list)
pool.close()


# procs = []

# while true:
#     if not class_list:
#         break

#     classname = class_list.pop()
#     p = multiprocessing.Process(target=comment_out_given_class, args=(classname, ))
#     p.start()
#     p.join()

# while true:
#     for i in range(job_num): 
#         if not class_list:
#             break
#         classname = class_list.pop()
#         p = multiprocessing.Process(target=comment_out_given_class, args=(classname, ))
#         p.start()
#         procs.append(p)

#     for p in procs:
#         p.join()
        
#     if not class_list:
#         break
