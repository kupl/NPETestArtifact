import argparse
import os
import random
import shutil, sys

from multiprocessing.pool import ThreadPool

GREEN='\033[0;32m'
RED='\033[0;31m'

INFO=f'{GREEN}[INFO]'
ERR=f'{RED}[ERR ]'
NC='\033[0m'

class DockerBuilder:
    def __init__(self, benchmark_group, benchmark_name):
        self.benchmark_group = benchmark_group
        self.benchmark_name = benchmark_name

    def setup_cmd(self, dockers_dir):
        self.image_name = 'artifact/npetest_benchmark'
        self.tag_name = self.benchmark_name.lower()
        self.docker_file = os.path.join(dockers_dir, self.benchmark_group, self.benchmark_name, 'Dockerfile')
        self.cmd = f'docker build -t {self.image_name}:{self.tag_name} -f {self.docker_file} .'

    def __repr__(self):
        return f'{self.image_name}:{self.tag_name}'


def execute_builder(builder: DockerBuilder):
    cmd = builder.cmd

    print(f'{INFO} Building {builder}... {NC}')
    os.system(cmd)

if __name__ == '__main__':

    parser = argparse.ArgumentParser()
    parser.add_argument('-j', '--job-count', required=False, type=int)
    parser.add_argument('--testing-type', required=False, type=int)

    args = parser.parse_args()

    if (args.testing_type == None or args.testing_type == 0):
        shutil.copy("./benchmark_list_ori.txt", "./benchmark_list.txt")
    elif (args.testing_type > 0):
        shutil.copy("./simple_benchmark.txt", "./benchmark_list.txt")

    benchmark_list_file = "./benchmark_list.txt"

    if (args.job_count == None):
        args.job_count = 1

    job_count = args.job_count

    builders = []
    script_dir = os.path.dirname(os.path.realpath(__file__))
    dockers_dir = os.path.join(script_dir, 'dockers')
    with open(benchmark_list_file, 'r') as f:
        lines = f.readlines()
        for _line in lines:
            if _line.startswith('#'):
                continue
            line = _line.strip()
            benchmark_group = line.split(':')[0]
            benchmark_name = line.split(':')[1]
            builder = DockerBuilder(benchmark_group, benchmark_name)
            builder.setup_cmd(dockers_dir)
            builders.append(builder)


    pool = ThreadPool(job_count)
    random.shuffle(builders)
    for builder in builders:
        pool.apply_async(execute_builder, (builder,))

    pool.close()
    pool.join()

