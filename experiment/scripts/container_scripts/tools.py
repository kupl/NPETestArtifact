import os
import json
import uuid

from commons import *

class Tool:
    def __init__(self, experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only):
        self.experiment_id = experiment_id
        self.benchmark_group = benchmark_group
        self.benchmark_name = benchmark_name
        self.null_probability = null_probability
        self.time_budget = time_budget
        self.npe_class_only = npe_class_only

        # shared path located in container
        self.benchmark_directory_in_docker = os.path.join(SUBJECT_DIR, benchmark_group, benchmark_name)
        self.benchmark_metadata = os.path.join(METADATA_DIR, benchmark_group, benchmark_name, 'npetest.json')

        # command and log
        self.test_generator_commands = []
        self.test_generation_log_files = []

        # flag
        self.disabled = False

        with open(self.benchmark_metadata, 'r') as f:
            metadata = json.load(f)
            self.npe_classes = set(map(lambda x: (x["module"], x["npe_class"]), metadata['npe_info']))
            self.input_classes = set(map(lambda x: (x["module"], x["class_name"]), metadata['experiment_config']['input_classes']))
            self.java_version = metadata['build_info']['java_version']
            self.benchmark_jdk_home = get_jdk(self.java_version)


    def setup_commands(self):
        pass

    def get_classpath(self, module_path):
        try:
            target_classpath = os.path.join(self.benchmark_directory_in_docker, module_path, 'target/classes')
            dependency_classpath = os.path.join(self.benchmark_directory_in_docker, module_path, 'target/dependency')
            if os.path.exists(dependency_classpath):
                for jar in os.listdir(dependency_classpath):
                    if not jar.endswith('.jar'):
                        continue
                    jar_path = os.path.join(self.benchmark_directory_in_docker, module_path, 'target/dependency', jar)
                    target_classpath = f'{target_classpath}:{jar_path}'
            return target_classpath
        except:
            self.disabled = True


class Randoop(Tool):

    def __init__(self, experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only):
        super().__init__(experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only)

    def __repr__(self):
        return f'Randoop_{self.experiment_id[0:4]}[subject={self.benchmark_group}:{self.benchmark_name}, #CUT={len(self.input_classes)}]'

    def setup_commands(self):
        if self.disabled:
            return 
        
        for module, class_name in self.input_classes:
            if ((module, class_name) not in self.npe_classes) and self.npe_class_only:
                continue
            per_class_result_dir = os.path.join(RESULTS_HOME, class_name)
            os.makedirs(per_class_result_dir)
            classpath = self.get_classpath(module)

            package_name = '.'.join(class_name.split('.')[:-1])
            simple_classname = class_name.split('.')[-1]
            regression_test_basename = simple_classname + '_RegressionTest'
            error_test_basename = simple_classname + '_ErrorTest'
            test_dir = os.path.join(per_class_result_dir, 'randoop-test') 
            log_file = os.path.join(per_class_result_dir, 'randoop_log.txt')
    
            cmd = f'JAVA_HOME={self.benchmark_jdk_home} {self.benchmark_jdk_home}/bin/java -Xmx16384m -classpath {classpath}:{RANDOOP_JAR}'
            cmd = f'{cmd} randoop.main.Main gentests'
            cmd = f'{cmd} --testclass={class_name}'
            cmd = f'{cmd} --time-limit={self.time_budget} --jvm-max-memory=16384m'
            cmd = f'{cmd} --regression-test-basename={regression_test_basename}'
            cmd = f'{cmd} --error-test-basename={error_test_basename}'
            cmd = f'{cmd} --no-regression-assertions=true'
            cmd = f'{cmd} --checked-exception=EXPECTED --unchecked-exception=ERROR --npe-on-null-input=ERROR --npe-on-non-null-input=ERROR'
            cmd = f'{cmd} --null-ratio={self.null_probability}'
            cmd = f'{cmd} --check-compilable=true'
            cmd = f'{cmd} --junit-output-dir={test_dir} --junit-package-name={package_name}'
            self.test_generator_commands.append(cmd)
            self.test_generation_log_files.append(log_file)


class EvoSuite(Tool):

    def __init__(self, experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only, criterion):
        super().__init__(experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only)
        self.criterion = criterion

    def __repr__(self):
        return f'EvoSuite_{self.experiment_id[0:4]}[subject={self.benchmark_group}:{self.benchmark_name}, #CUT={len(self.input_classes)}]'

    def setup_commands(self):
        if self.disabled:
            return

        for module, class_name in self.input_classes:
            if ((module, class_name) not in self.npe_classes) and self.npe_class_only:
                continue

            per_class_result_dir = os.path.join(RESULTS_HOME, class_name)
            os.makedirs(per_class_result_dir)
            report_dir = os.path.join(per_class_result_dir, 'evosuite-report') 
            test_dir = os.path.join(per_class_result_dir, 'evosuite-test') 
            log_file = os.path.join(per_class_result_dir, 'evosuite_log.txt')
            classpath = self.get_classpath(module)
    
            cmd = f'JAVA_HOME={self.benchmark_jdk_home} {self.benchmark_jdk_home}/bin/java -Xmx16384m -jar {EVOSUITE_JAR}'
            cmd = f'{cmd} -Dsearch_budget {self.time_budget} -class {class_name} -projectCP {classpath}'
            cmd = f'{cmd} -Dassertions false -Dcatch_undeclared_exceptions false'
            cmd = f'{cmd} -Dnull_probability={self.null_probability}'
            cmd = f'{cmd} -Dreport_dir {report_dir} -Dtest_dir {test_dir}' 
            cmd = f'{cmd} -Dmock_if_no_generator false'
            
            cmd = f'{cmd} -generateMOSuite'
            cmd = f'{cmd} -Dalgorithm=DynaMOSA'
            cmd = f'{cmd} -Dstatistics_backend=NONE'
            cmd = f'{cmd} -Dshow_progress=false'
            cmd = f'{cmd} -Dnew_statistics=false'
            cmd = f'{cmd} -Dcoverage=false'
            cmd = f'{cmd} -Dinline=true'
            cmd = f'{cmd} -Dp_functional_mocking=0.8'
            cmd = f'{cmd} -Dp_reflection_on_private=0.5'
            cmd = f'{cmd} -Dreflection_start_percent=0.8'


            if self.criterion:
                cmd = f'{cmd} -Dcriterion={self.criterion}'

            self.test_generator_commands.append(cmd)
            self.test_generation_log_files.append(log_file)


class EvoSuiteRandom(Tool):

    def __init__(self, experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only, criterion):
        super().__init__(experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only)
        self.criterion = criterion

    def __repr__(self):
        return f'EvoSuiteRandom_{self.experiment_id[0:4]}[subject={self.benchmark_group}:{self.benchmark_name}, #CUT={len(self.input_classes)}]'

    def setup_commands(self):
        if self.disabled:
            return

        for module, class_name in self.input_classes:
            if ((module, class_name) not in self.npe_classes) and self.npe_class_only:
                continue

            per_class_result_dir = os.path.join(RESULTS_HOME, class_name)
            os.makedirs(per_class_result_dir)
            report_dir = os.path.join(per_class_result_dir, 'evosuiteRandom-report') 
            test_dir = os.path.join(per_class_result_dir, 'evosuiteRandom-test') 
            log_file = os.path.join(per_class_result_dir, 'evosuiteRandom_log.txt')
            classpath = self.get_classpath(module)
    
            cmd = f'JAVA_HOME={self.benchmark_jdk_home} {self.benchmark_jdk_home}/bin/java -Xmx16384m -jar {EVOSUITE_JAR}'
            cmd = f'{cmd} -generateRandom'
            cmd = f'{cmd} -Dsearch_budget {self.time_budget} -class {class_name} -projectCP {classpath}'
            cmd = f'{cmd} -Dassertions false -Dcatch_undeclared_exceptions false'
            cmd = f'{cmd} -Dnull_probability={self.null_probability}'
            cmd = f'{cmd} -Dreport_dir {report_dir} -Dtest_dir {test_dir}' 
            cmd = f'{cmd} -Dmock_if_no_generator false'
            if self.criterion:
                cmd = f'{cmd} -Dcriterion={self.criterion}'
            cmd = f'{cmd} -Dstatistics_backend=NONE'
            cmd = f'{cmd} -Dshow_progress=false'
            cmd = f'{cmd} -Dnew_statistics=false'
            cmd = f'{cmd} -Dcoverage=false'
            cmd = f'{cmd} -Dinline=true'
            cmd = f'{cmd} -Dp_functional_mocking=0.8'
            cmd = f'{cmd} -Dp_reflection_on_private=0.5'
            cmd = f'{cmd} -Dreflection_start_percent=0.8'

            self.test_generator_commands.append(cmd)
            self.test_generation_log_files.append(log_file)


class NPETest(Tool):

    def __init__(self, experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only):
        super().__init__(experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only)
        self.criterion = ""

    def __repr__(self):
        return f'NPETest_{self.experiment_id[0:4]}[subject={self.benchmark_group}:{self.benchmark_name}, #CUT={len(self.input_classes)}]'

    # def get_classpath(self, module_path):
    #     try:
    #         target_classpath = ''
    #         dependency_classpath = os.path.join(self.benchmark_directory_in_docker, module_path, 'target/dependency')
    #         if os.path.exists(dependency_classpath):
    #             for jar in os.listdir(dependency_classpath):
    #                 if not jar.endswith('.jar'):
    #                     continue
    #                 jar_path = os.path.join(self.benchmark_directory_in_docker, module_path, 'target/dependency', jar)
    #                 target_classpath = f'{target_classpath}:{jar_path}'
    #             return target_classpath[1:]
    #         else:
    #             target_classpath
    #     except:
    #         self.disabled = True

    def setup_commands(self):
        if self.disabled:
            return

            
        for module, class_name in self.input_classes:
            if ((module, class_name) not in self.npe_classes) and self.npe_class_only:
                continue

            per_class_result_dir = os.path.join(RESULTS_HOME, class_name)
            os.makedirs(per_class_result_dir)
            report_dir = os.path.join(per_class_result_dir, 'npetest-report') 
            test_dir = os.path.join(per_class_result_dir, 'npetest-test') 
            log_file = os.path.join(per_class_result_dir, 'npetest_log.txt')
            classpath = self.get_classpath(module)
    
            cmd = f'JAVA_HOME={self.benchmark_jdk_home} {self.benchmark_jdk_home}/bin/java -Xmx16384m -jar {EVOSUITE_JAR}'
            cmd = f'{cmd} -npetest'
            cmd = f'{cmd} -Dsearch_budget {self.time_budget} -class {class_name} -projectCP {classpath}'
            cmd = f'{cmd} -Dassertions false -Dcatch_undeclared_exceptions false'
            cmd = f'{cmd} -Dnull_probability={self.null_probability}'
            cmd = f'{cmd} -Dreport_dir {report_dir} -Dtest_dir {test_dir}' 
            cmd = f'{cmd} -Dmock_if_no_generator false'
            if self.criterion:
                cmd = f'{cmd} -Dcriterion={self.criterion}'
            
            tmp_str = classpath.replace("/target/","/:/")

            target_dir = tmp_str.split(':')[0]

            cmd = f'{cmd} -npetest'
            cmd = f'{cmd} -target_dir {target_dir}'
            
            cmd = f'{cmd} -Dstatistics_backend=NONE'
            cmd = f'{cmd} -Dshow_progress=false'
            cmd = f'{cmd} -Dnew_statistics=false'
            cmd = f'{cmd} -Dcoverage=false'
            cmd = f'{cmd} -Dinline=true'
            cmd = f'{cmd} -Dp_functional_mocking=0.8'
            cmd = f'{cmd} -Dp_reflection_on_private=0.5'
            cmd = f'{cmd} -Dreflection_start_percent=0.8'

            self.test_generator_commands.append(cmd)
            self.test_generation_log_files.append(log_file)


        # for module, class_name in self.input_classes:
        #     if ((module, class_name) not in self.npe_classes) and self.npe_class_only:
        #         continue

        #     per_class_result_dir = os.path.join(RESULTS_HOME, class_name)
        #     os.makedirs(per_class_result_dir)
        #     report_dir = 'npetest-report'
        #     test_dir = 'npetest-test'
        #     log_file = os.path.join(per_class_result_dir, 'npetest_log.txt')
        #     classpath = self.get_classpath(module)
        #     # mvn_directory = os.path.join(self.benchmark_directory_in_docker, module)

        #     # app_jdk_home = get_jdk(15)

            
        #     cmd = f'JAVA_HOME={self.benchmark_jdk_home} {self.benchmark_jdk_home}/bin/java -Xmx16384m -jar {EVOSUITE_JAR}'
        #     cmd = f'{cmd} -Dsearch_budget {self.time_budget} -class {class_name} -projectCP {classpath}'
        #     cmd = f'{cmd} -Dassertions false -Dcatch_undeclared_exceptions false'
        #     cmd = f'{cmd} -Dnull_probability={self.null_probability}'
        #     cmd = f'{cmd} -Dreport_dir {report_dir} -Dtest_dir {test_dir}' 
        #     cmd = f'{cmd} -Dmock_if_no_generator false'

            
        #     tmp_str = classpath.replace("/target/","/:/")

        #     target_dir = tmp_str.split(':')[0]


        #     cmd = f'{cmd} -npetest'
        #     cmd = f'{cmd} -target_dir {target_dir}'
        #     if self.criterion:
        #         cmd = f'{cmd} -Dcriterion={self.criterion}'
    
        #     # cmd = f'JAVA_HOME={self.benchmark_jdk_home} {app_jdk_home}/bin/java -Xmx16384m -ea -XX:MaxJavaStackTraceDepth=1000000'
        #     # cmd = f'{cmd} -classpath {NPETEST_JAR} npetest.Main'
        #     # cmd = f'{cmd} --mvn {mvn_directory} --cuts {class_name}'
        #     # if classpath:
        #     #     cmd = f'{cmd} --auxiliary-classpath {classpath}'
        #     # cmd = f'{cmd} --time-budget {self.time_budget}'
        #     # # cmd = f'{cmd} --log-level DEBUG'
            
        #     # ### npe strategy
        #     # cmd = f'{cmd} --filter-mut'
        #     # cmd = f'{cmd} --enable-analysis'
        #     # cmd = f'{cmd} --seed-selection-strategy feedback'
        #     # cmd = f'{cmd} --mutation-strategy feedback'
        #     # cmd = f'{cmd} --seed-gen-stopping-condition FIXED_PARAMETER_SPACE'
        #     # cmd = f'{cmd} --debug'

        #     # cmd = f'{cmd} --null-probability {self.null_probability}'
        #     # cmd = f'{cmd} --output-dir {per_class_result_dir} --test-dir {test_dir} --report-dir {report_dir}'
        #     # cmd = f'{cmd} --java-version {self.java_version}'


        #     self.test_generator_commands.append(cmd)
        #     self.test_generation_log_files.append(log_file)


class NPETestbase(Tool):

    def __init__(self, experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only):
        super().__init__(experiment_id, benchmark_group, benchmark_name, null_probability, time_budget, npe_class_only)

    def __repr__(self):
        return f'NPETest_{self.experiment_id[0:4]}[subject={self.benchmark_group}:{self.benchmark_name}, #CUT={len(self.input_classes)}]'

    def get_classpath(self, module_path):
        try:
            target_classpath = ''
            dependency_classpath = os.path.join(self.benchmark_directory_in_docker, module_path, 'target/dependency')
            if os.path.exists(dependency_classpath):
                for jar in os.listdir(dependency_classpath):
                    if not jar.endswith('.jar'):
                        continue
                    jar_path = os.path.join(self.benchmark_directory_in_docker, module_path, 'target/dependency', jar)
                    target_classpath = f'{target_classpath}:{jar_path}'
                return target_classpath[1:]
            else:
                target_classpath
        except:
            self.disabled = True

    def setup_commands(self):
        if self.disabled:
            return

        for module, class_name in self.input_classes:
            if ((module, class_name) not in self.npe_classes) and self.npe_class_only:
                continue

            per_class_result_dir = os.path.join(RESULTS_HOME, class_name)
            os.makedirs(per_class_result_dir)
            report_dir = 'npetest-report'
            test_dir = 'npetest-test'
            log_file = os.path.join(per_class_result_dir, 'npetest_log.txt')
            classpath = self.get_classpath(module)
            mvn_directory = os.path.join(self.benchmark_directory_in_docker, module)

            app_jdk_home = get_jdk(15)
    
            cmd = f'JAVA_HOME={self.benchmark_jdk_home} {app_jdk_home}/bin/java -Xmx16384m -ea -XX:MaxJavaStackTraceDepth=1000000'
            cmd = f'{cmd} -classpath {NPETEST_JAR} npetest.Main'
            cmd = f'{cmd} --mvn {mvn_directory} --cuts {class_name}'
            if classpath:
                cmd = f'{cmd} --auxiliary-classpath {classpath}'
            cmd = f'{cmd} --time-budget {self.time_budget}'

            ### baseline
            cmd = f'{cmd} --seed-selection-strategy default'
            cmd = f'{cmd} --mutation-strategy default'
            cmd = f'{cmd} --seed-gen-stopping-condition FIXED_PARAMETER_SPACE'
            cmd = f'{cmd} --log-level DEBUG'

            cmd = f'{cmd} --null-probability {self.null_probability}'
            cmd = f'{cmd} --output-dir {per_class_result_dir} --test-dir {test_dir} --report-dir {report_dir}'
            cmd = f'{cmd} --java-version {self.java_version}'

            self.test_generator_commands.append(cmd)
            self.test_generation_log_files.append(log_file)

