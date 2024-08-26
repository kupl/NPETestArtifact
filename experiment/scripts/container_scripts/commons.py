
GREEN='\033[0;32m'
RED='\033[0;31m'

INFO=f'{GREEN}[INFO]'
ERR=f'{RED}[ERR ]'
NC='\033[0m'

# Benchmarks
SUBJECT_DIR = '/subject'

# Metadata
METADATA_DIR = '/metadata'

# TOOLS
EVOSUITE_JAR = '/tools/evosuite/evosuite.jar'
EVOSUITE_RT_JAR= '/tools/evosuite/evosuite-rt.jar'
RANDOOP_JAR = '/tools/randoop/randoop.jar'
NPETEST_JAR = '/tools/npetest/npetest.jar'

# JDK
JAVA8_HOME = '/usr/java/openjdk-8'
JAVA11_HOME = '/usr/java/openjdk-11'
JAVA15_HOME = '/usr/java/openjdk-15'

def get_jdk(version):
    if version == '8' or version == 8:
        return JAVA8_HOME
    elif version == '11' or version == 11:
        return JAVA11_HOME
    elif version == '15' or version == 15:
        return JAVA15_HOME

# Test driver
JUNIT_JAR = '/tools/junit/junit.jar'
HAMCREST_CORE_JAR = '/tools/junit/hamcrest-core.jar'

# Shared directory
RESULTS_HOME = '/experiment_results'
