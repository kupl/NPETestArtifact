import argparse
import datetime
import os
import random
import sys
import uuid

from multiprocessing.pool import ThreadPool

from commons import *

SCRIPT_DIR = os.path.dirname(os.path.realpath(__file__))

class Command:
    def __init__(self, tool, subject, host_result_base, null_probability, time_budget, npe_class_only, criterion):
        self.tool = tool
        self.subject = subject
        self.benchmark_group = subject.split(':')[0]
        self.benchmark_name = subject.split(':')[1]
        self.npe_class_only = npe_class_only
        self.criterion = criterion

        self.docker_img = f'artifact/npetest_evaluation:{self.benchmark_name.lower()}'
        self.experiment_id = uuid.uuid4().hex
        self.result_directory = os.path.join(host_result_base, self.benchmark_group, self.benchmark_name, self.experiment_id)

        tool_cmd = 'python3 /container_scripts/run_tool.py'
        tool_cmd = f'{tool_cmd} --tool {tool}'
        tool_cmd = f'{tool_cmd} --null-probability {null_probability}'
        tool_cmd = f'{tool_cmd} --time-budget {time_budget}'
        tool_cmd = f'{tool_cmd} --benchmark-group {self.benchmark_group}'
        tool_cmd = f'{tool_cmd} --benchmark-name {self.benchmark_name}'
        tool_cmd = f'{tool_cmd} --experiment-id {self.experiment_id}'
        tool_cmd = f'{tool_cmd} {"--npe-class-only" if self.npe_class_only else ""}'
        if self.criterion:
            tool_cmd = f'{tool_cmd} --criterion {self.criterion}'

        cmd = 'docker run'
        cmd = f'{cmd} --ulimit nofile=65536:65536'
        cmd = f'{cmd} -v {os.path.join(SCRIPT_DIR, "container_scripts")}:/container_scripts'
        cmd = f'{cmd} -v {self.result_directory}:{RESULTS_HOME}'
        cmd = f'{cmd} --rm {self.docker_img}'
        cmd = f'{cmd} "{tool_cmd}"'
        
        self.cmd = cmd

    def __repr__(self):
        mode = 'NPE_CLASS' if self.npe_class_only else 'RELEVANT_CLASSES'
        return f'{self.tool.upper()}({mode})_{self.experiment_id[0:4]}[subject={self.subject}]'


def execute_command(command: Command):
    print(f'{INFO} Running {command} ({get_time()})... {NC}')
    os.system(command.cmd)

if __name__ == '__main__':

    parser = argparse.ArgumentParser()
    parser.add_argument('--time-budget', required=False, default=180, type=int)
    parser.add_argument('--repeat', required=False, default=1, type=int)
    parser.add_argument('-j', '--job-count', required=True, type=int)
    parser.add_argument('--tool', required=True,
            choices=['evosuite', 'evosuiteRandom', 'randoop', 'npetest', 'npetestbase'])
    parser.add_argument('--null-probability', required=True, type=float)
    parser.add_argument('--result-home', required=False, type=os.path.abspath)
    parser.add_argument('--benchmark-list', required=True, type=os.path.abspath)
    parser.add_argument('--npe-class-only', required=False, action='store_true')
    parser.add_argument('--criterion', required=False, type=str)
    parser.add_argument('--output-dir', required=False, type=os.path.abspath)

    args = parser.parse_args()
    time_budget = args.time_budget
    repeat = args.repeat
    job_count = args.job_count
    tool = args.tool
    null_probability = args.null_probability
    result_home = args.result_home
    benchmark_list_file = args.benchmark_list
    npe_class_only = args.npe_class_only
    criterion = args.criterion
    output_dir = args.output_dir

    if result_home:
        result_dirname = f'{tool}_{null_probability}_{datetime.datetime.now().strftime("%Y-%m-%d_%H-%M")}'
        host_result_base = os.path.join(result_home, result_dirname)
    elif output_dir:
        host_result_base = output_dir
    else:
        print(f'{ERR} Either `--result-home` or `--output-dir` should be passed!! {NC}')
        sys.exit(1)

    os.makedirs(host_result_base, exist_ok=True)

    commands = []

    with open(benchmark_list_file, 'r') as f:
        lines = f.readlines()
        for line in lines:
            if line.startswith('#'):
                continue
            subject = line.strip()

            for i in range(repeat):
                commands.append(Command(tool, subject, host_result_base, null_probability, time_budget, npe_class_only, criterion))


    pool = ThreadPool(job_count)
    random.shuffle(commands)
    for command in commands:
        pool.apply_async(execute_command, (command,))

    pool.close()
    pool.join()


