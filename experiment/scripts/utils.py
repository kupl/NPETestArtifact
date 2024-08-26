import os


def extract_test_java_files(result_directory):
    test_java_files = []
    test_directory = None
    for _dir in os.listdir(result_directory):
        if _dir.endswith('-test'):
            test_directory = os.path.join(result_directory, _dir)

    if not test_directory:
        return test_java_files

    for root, dirs, files in os.walk(test_directory):
        for file in files:
            if (file.endswith('.java')
                and 'scaffolding' not in file
                and not file.endswith('RegressionTest.java')
                and not file.endswith('ErrorTest.java')):
                test_java_files.append(file)
    return test_java_files


def extract_testclass_files(result_directory):
    testclass_files = []
    testclass_directory = None
    for _dir in os.listdir(result_directory):
        if _dir.endswith('-testclasses'):
            testclass_directory = os.path.join(result_directory, _dir)

    if not testclass_directory:
        return testclass_files

    for root, dirs, files in os.walk(testclass_directory):
        for file in files:
            if (file.endswith('.class')
                and 'scaffolding' not in file
                and not file.endswith('RegressionTest.class')
                and not file.endswith('ErrorTest.class')):
                testclass_files.append(file)
    return testclass_files
