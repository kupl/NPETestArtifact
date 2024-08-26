import argparse
import os
import random

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
        self.image_name = 'artifact/npetest_evaluation'
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
    
    args = parser.parse_args()
    
    if (args.job_count == None):
        args.job_count = 1

    job_count = args.job_count

    builders = []
    script_dir = os.path.dirname(os.path.realpath(__file__))


    dockers_dir = os.path.join(script_dir, 'dockers')

    for benchmark_group in os.listdir(dockers_dir):
        for benchmark_name in os.listdir(os.path.join(dockers_dir, benchmark_group)):
            builder = DockerBuilder(benchmark_group, benchmark_name)
            builder.setup_cmd(dockers_dir)
            builders.append(builder)

    pool = ThreadPool(job_count)
    random.shuffle(builders)
    for builder in builders:
        pool.apply_async(execute_builder, (builder,))

    pool.close()
    pool.join()

