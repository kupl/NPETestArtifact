FROM artifact/evosuite as evosuite
FROM artifact/randoop as randoop
FROM artifact/npetest as npetest

FROM artifact/java_base

# Copy Tools
COPY --from=evosuite /tools/evosuite /tools/evosuite
COPY --from=randoop /tools/randoop /tools/randoop
COPY --from=npetest /tools/npetest /tools/npetest

WORKDIR /

ENTRYPOINT [ "/bin/bash", "-l", "-c" ]
CMD [ "/bin/bash" ]
