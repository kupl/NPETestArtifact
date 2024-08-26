FROM artifact/java_base as build

# Build Randoop
WORKDIR /tmp
RUN git clone http://github.com/randoop/randoop -b v4.3.0 --depth 1 --quiet && \
    cd randoop && \
    JAVA_HOME=/usr/java/openjdk-8 ./gradlew assemble -x test

WORKDIR /tools/randoop
RUN mv /tmp/randoop/build/libs/randoop-all*.jar randoop.jar && \
    rm -rf /tmp/randoop

WORKDIR /

ENTRYPOINT [ "/bin/bash", "-l", "-c" ]
CMD [ "/bin/bash" ]
