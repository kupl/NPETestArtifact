# Tools

All tools (NPETest, EvoSuite, and Randoop) used in the experiment will be installed in docker images. Those images built here will be used in [experiment](../experiment).

* [java_base](./java_base) : contains Dockerfile to generate a base docker image for tools.
* [NPETest](./npetest) : contains Dockerfile to generate docker image of NPETest
* [EvoSuite](./evosuite) : contains Dockerfile to generate docker image of EvoSuite
* [Randoop](./randoop) : contains Dockerfile to generate docker image of Randoop
* [testing_tools](./testing_tools) : contains Dockerfile which contains exectuable files of all tools.

## Build Instruction

You can build all tools by running the following command:
```
./build_tool_docker.sh
```

