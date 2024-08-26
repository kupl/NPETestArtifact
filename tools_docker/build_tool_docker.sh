#!/usr/bin/env bash

cp -r ../tool ./evosuite/
cp -r ../tool ./npetest/

cd ./java_base
docker build -t artifact/java_base .

cd ../evosuite
docker build -t artifact/evosuite .
rm -rf tool

cd ../npetest
docker build -t artifact/npetest .
rm -rf tool

cd ../randoop 
docker build -t artifact/randoop .

cd ../testing_tools
docker build -t artifact/testing_tools . 


