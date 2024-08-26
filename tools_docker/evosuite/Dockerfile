FROM artifact/java_base as build

ADD ./tool /tmp/evosuite

WORKDIR /tmp
RUN  cd evosuite && \
    JAVA_HOME=/usr/java/openjdk-11 mvn package -DskipTests -DskipITs -B

WORKDIR /tools/evosuite
RUN mv /tmp/evosuite/master/target/evosuite-master-*-tests.jar evosuite-tests.jar && \
    mv /tmp/evosuite/master/target/evosuite-master-*.jar evosuite.jar && \
    mv /tmp/evosuite/standalone_runtime/target/evosuite-standalone-runtime-*-tests.jar evosuite-rt-tests.jar && \
    mv /tmp/evosuite/standalone_runtime/target/evosuite-standalone-runtime-*.jar evosuite-rt.jar && \
    rm -rf /tmp/evosuite

WORKDIR /tools/evosuite

WORKDIR /

ENTRYPOINT [ "/bin/bash", "-l", "-c" ]
CMD [ "/bin/bash" ]
