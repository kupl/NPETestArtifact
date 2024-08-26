import os

from commons import *


def setup_base_classpath(benchmark_group, benchmark_name, input_class):
    benchmark_directory_in_docker = os.path.join(SUBJECT_DIR, benchmark_group, benchmark_name)
    module_path = input_class['module']
    class_name = input_class['class_name']

    benchmark_module_directory_in_docker = os.path.join(benchmark_directory_in_docker, module_path)
    base_classpath = os.path.join(benchmark_module_directory_in_docker, 'target/classes')
    dependencies_path = os.path.join(benchmark_module_directory_in_docker, 'target/dependency')
    if os.path.exists(dependencies_path):
        for jar in os.listdir(dependencies_path):
            if not jar.endswith('.jar'):
                continue
            if ('junit' in jar) or ('hamcrest-core' in jar):
                continue
            jar_path = os.path.join(dependencies_path, jar)
            base_classpath = f'{base_classpath}:{jar_path}'
    return base_classpath


def setup_compile_classpath(tool, benchmark_group, benchmark_name, input_class):
    base_classpath = setup_base_classpath(benchmark_group, benchmark_name, input_class)
    test_related_classpath = f'{JUNIT_JAR}:{HAMCREST_CORE_JAR}'
    if 'evosuite' in tool:
        test_related_classpath = f'{test_related_classpath}:{EVOSUITE_RT_JAR}'
    elif 'npetest' in tool:
        test_related_classpath = f'{test_related_classpath}:{EVOSUITE_RT_JAR}'

    return f'{test_related_classpath}:{base_classpath}'

