import argparse
import os
import random
import subprocess
import sys

from multiprocessing.pool import ThreadPool

from commons import *

SCRIPT_DIR = os.path.dirname(os.path.realpath(__file__))

class Command:
    def __init__(self, tool, result_base, benchmark_group, benchmark_name, experiment_id):
        self.result_base_name = os.path.basename(result_base)
        self.tool = tool
        self.benchmark_group = benchmark_group
        self.benchmark_name = benchmark_name
        self.experiment_id = experiment_id

        self.docker_img = f'artifact/npetest_evaluation:{self.benchmark_name.lower()}' 

        host_result_directory = os.path.join(result_base, benchmark_group, benchmark_name, experiment_id)


        compile_cmd = 'python3 /container_scripts/replay.py'
        compile_cmd = f'{compile_cmd} --tool {tool}'
        compile_cmd = f'{compile_cmd} --benchmark-group {benchmark_group}'
        compile_cmd = f'{compile_cmd} --benchmark-name {benchmark_name}'

        cmd = 'docker run'
        cmd = f'{cmd} --ulimit nofile=65536:65536'
        cmd = f'{cmd} -v {os.path.join(SCRIPT_DIR, "container_scripts")}:/container_scripts'
        cmd = f'{cmd} -v {host_result_directory}:{RESULTS_HOME}'
        cmd = f'{cmd} --rm {self.docker_img}'
        cmd = f'{cmd} "{compile_cmd}"'
        
        self.cmd = cmd

    def __repr__(self):
        return f'ReplayCMD_{self.result_base_name}_{self.experiment_id[0:4]}[subject={self.benchmark_group}:{self.benchmark_name}]'


def execute_command(command: Command):
    print(f'{INFO} Running {command} ({get_time()})... {NC}')
    os.system(command.cmd)

if __name__ == '__main__':

    parser = argparse.ArgumentParser()
    parser.add_argument('-j', '--job-count', required=True, type=int)
    parser.add_argument('--result-base', required=True, type=os.path.abspath)

    args = parser.parse_args()
    job_count = args.job_count
    result_base = args.result_base
    tool = os.path.basename(result_base).split('_')[0]

    commands = []
    for benchmark_group in os.listdir(result_base):
        for benchmark_name in os.listdir(os.path.join(result_base, benchmark_group)):
            for experiment_id in os.listdir(os.path.join(result_base, benchmark_group, benchmark_name)):
                command = Command(tool, result_base, benchmark_group, benchmark_name, experiment_id)
                commands.append(command)

    pool = ThreadPool(job_count)
    random.shuffle(commands)
    for command in commands:
        pool.apply_async(execute_command, (command,))

    pool.close()
    pool.join()

