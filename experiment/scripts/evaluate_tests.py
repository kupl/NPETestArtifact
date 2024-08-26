import argparse
import json
import os
import re
import subprocess
import sys

import pandas as pd
import numpy as np

from utils import *

SCRIPT_DIR = os.path.dirname(os.path.realpath(__file__))
METADATA_DIR = os.path.join(SCRIPT_DIR, '../../benchmarks/metadata')
BENCHMARK_LIST = os.path.join(SCRIPT_DIR, '../../benchmarks/benchmark_list.txt')


def count_commented_tests(testsuite_java):
    try:
        cmd = f'grep -w "^[/]\+\s\+public void test[0-9]\+()" {testsuite_java} | wc -l'
        output = subprocess.check_output(cmd, shell=True)
        decoded_output = output.decode("utf-8")
        return int(re.findall(r"\d+", decoded_output)[0])
    except:
        return -1


def count_compilable_tests(testsuite_java):
    try:
        cmd = f'grep -w "^\s\+public void test[0-9]\+()" {testsuite_java} | wc -l'
        output = subprocess.check_output(cmd, shell=True)
        decoded_output = output.decode("utf-8")
        return int(re.findall(r"\d+", decoded_output)[0])
    except:
        return -1


def count_unique_npes(replay_log):
    try:
        unique_npes = set()
        cmd = f'grep -awr "^[0-9]\+) test[0-9]\+(.*)" {replay_log} -A2 | grep "java.lang.NullPointerException" -A1 | grep "(.\+\.java:[0-9]\+)"'
        output = subprocess.check_output(cmd, shell=True)
        decoded_output = output.decode("utf-8")

        for _output_line in filter(None, decoded_output.split('\n')):
            output_line = _output_line.split(' ')[-1]
            method_path = output_line.split('(')[0]
            full_class_name = '.'.join(method_path.split('.')[:-1]) if '$' not in method_path else method_path.split('$')[0]

            try:
                file_info = output_line.split('(')[1]
            except:
                continue
            file_line_number = int(re.findall(r":\d+", file_info)[0][1:])
            file_name = re.findall(r".+\.java", file_info)[0][:-5]

            unique_npes.add((full_class_name, file_name, file_line_number))
        return unique_npes
    except subprocess.CalledProcessError as e:
        return unique_npes


parser = argparse.ArgumentParser()
parser.add_argument('--result-base', required=True, type=os.path.abspath)
parser.add_argument('--output-console', required=False, action='store_true')
parser.add_argument('--npe-class-only', required=False, action='store_true')
parser.add_argument('--experiment-count', required=True, type=int)

args = parser.parse_args()
result_base = args.result_base
npe_class_only = args.npe_class_only
output_console = args.output_console
experiment_count = args.experiment_count

tool = os.path.basename(result_base).split('_')[0]

benchmark_groups = []
benchmark_names = []
npe_labels = []
experiment_ids = []
input_classes = []
replayed_testsuites = []
compiled_tests_counts = []
commented_tests_counts = []
npe_reproduced = []


for benchmark_group in os.listdir(result_base):
    for benchmark_name in os.listdir(os.path.join(result_base, benchmark_group)):
        metadata_file = os.path.join(METADATA_DIR, benchmark_group, benchmark_name, 'npetest.json')
        with open(metadata_file, 'r') as f:
            metadata = json.load(f)
            npe_infos = metadata['npe_info']
            npe_classes = list(map(lambda npe_info: npe_info['npe_class'], npe_infos))
            for npe_label, npe_info in enumerate(npe_infos):
                npe_class = npe_info['npe_class']
                npe_line = npe_info['line']
                labelled_npe = (npe_class, npe_class.split('.')[-1], npe_line)

                for experiment_id in os.listdir(os.path.join(result_base, benchmark_group, benchmark_name)):
                    for input_class in os.listdir(os.path.join(result_base, benchmark_group, benchmark_name, experiment_id)):
                        if (input_class not in npe_classes) and npe_class_only:
                            continue
                        result_directory = os.path.join(result_base, benchmark_group, benchmark_name, experiment_id, input_class)
                        replay_logs_dir = os.path.join(result_directory, 'replay_logs')
                        if not os.path.exists(replay_logs_dir):
                            continue

                        for testsuite_name in os.listdir(replay_logs_dir):
                            testsuite_path = os.path.join(result_directory, f'{tool}-test', f'{testsuite_name.replace(".", "/")}.java')
                            replay_log = os.path.join(replay_logs_dir, testsuite_name, 'replay_log.txt')

                            if os.path.exists(os.path.join(replay_logs_dir, testsuite_name, '.test_failure')):
                                unique_npes = count_unique_npes(replay_log)
                            else:
                                unique_npes = set()

                            benchmark_groups.append(benchmark_group)
                            benchmark_names.append(benchmark_name)
                            npe_labels.append(npe_label)
                            experiment_ids.append(experiment_id)
                            input_classes.append(input_class)
                
                            replayed_testsuites.append(testsuite_name)
                            compiled_tests_count = count_compilable_tests(testsuite_path)
                            compiled_tests_counts.append(compiled_tests_count)
                            commented_tests_count = count_commented_tests(testsuite_path)
                            commented_tests_counts.append(commented_tests_count)

                            npe_reproduced.append(labelled_npe in unique_npes)

    

benchmark_groups_set = []
benchmark_names_set = []
npe_labels_set = []

for benchmark_group in os.listdir(METADATA_DIR):
    for benchmark_name in os.listdir(os.path.join(METADATA_DIR, benchmark_group)):
        npe_json = os.path.join(METADATA_DIR, benchmark_group, benchmark_name, 'npetest.json')
        json_file = open(npe_json, "r")
        data = json.load(json_file)
        json_file.close()

        npe_infos = data["npe_info"]

        for npe_label in range(len(npe_infos)):
            benchmark_groups_set.append(benchmark_group)
            benchmark_names_set.append(benchmark_name)
            npe_labels_set.append(npe_label)


pd.set_option('display.max_columns', 20)
pd.set_option('display.max_colwidth', None)
pd.set_option('display.max_rows', 10000)
pd.set_option('display.expand_frame_repr', False)
pd.set_option('display.multi_sparse', False)


effective_benchmark_set = []
with open(BENCHMARK_LIST, 'r') as f:
    lines = f.read().splitlines()
    for benchmark in lines:
        if '#' in benchmark:
            continue
        benchmark_name = benchmark.split(':')[1]
        effective_benchmark_set.append(benchmark_name)

data = {
    'benchmark_group': benchmark_groups,
    'benchmark_name': benchmark_names,
    'npe_label': npe_labels,
    'experiment_id': experiment_ids,
    'input_class': input_classes,
    'replayed_testsuite': replayed_testsuites,
    'compiled_tests': compiled_tests_counts,
    'commented_tests': commented_tests_counts,
    'npe_reproduced': npe_reproduced
    }

raw_data = pd.DataFrame.from_dict(data)

output_dir = os.path.join(SCRIPT_DIR, 'output')
os.makedirs(output_dir, exist_ok=True)

# save raw data
reproduction_rate_csv_file = os.path.join(output_dir, f'{os.path.basename(result_base)}_reproduction_raw_data.csv')
raw_data.to_csv(reproduction_rate_csv_file)

####### generate tables
benchmark_index = ['benchmark_group', 'benchmark_name']
experiment_index = ['benchmark_group', 'benchmark_name', 'experiment_id']
npe_label_index = ['benchmark_group', 'benchmark_name', 'npe_label']
npe_label_index_with_experiment_id = ['benchmark_group', 'benchmark_name', 'npe_label', 'experiment_id']
labelled_npe_reproduced_in_experiment = raw_data.groupby(npe_label_index_with_experiment_id)['npe_reproduced'].apply(lambda x : 1 if x.any() else 0)

# reproduction
labelled_npe_reproduction_count = labelled_npe_reproduced_in_experiment.groupby(npe_label_index).sum()
labelled_npe_reproduction_rate = (labelled_npe_reproduction_count.div(experiment_count, axis=0) * 100).astype('int64').rename('reproduction_rate')

# recall
labelled_npe_reproduction_overall = labelled_npe_reproduced_in_experiment.groupby(npe_label_index).apply(lambda x : 1 if x.any() else 0).rename('reproduction')

# create tables
per_bug_template = pd.Series(index=pd.MultiIndex.from_arrays([benchmark_groups_set, benchmark_names_set, npe_labels_set], names=npe_label_index), dtype='float64')
aggregates = [per_bug_template, labelled_npe_reproduction_overall, labelled_npe_reproduction_rate]

result = pd.concat(aggregates, axis=1).fillna(-1).astype('int64').reset_index()
result = result[result.benchmark_name.isin(effective_benchmark_set)]
result.loc[:,'benchmark_name'] = result['benchmark_name'].str.lower()
result.set_index(['benchmark_name', 'npe_label'], inplace=True)
result.sort_index(inplace=True)
result_reproduction_rate = result[['reproduction', 'reproduction_rate']]

# save raw data
reproduction_rate_csv_file = os.path.join(output_dir, f'{os.path.basename(result_base)}_reproduction_rate.csv')
result_reproduction_rate.to_csv(reproduction_rate_csv_file)

