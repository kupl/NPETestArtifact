#!/usr/bin/env bash

# console output

# ./merge_csv_files.sh [merged_csv_file]


#echo "Benchmark, Project, Class, NPE, Execution" >> ${2}
current=$(pwd)

rm ${1}

files=$(ls ${current}/result/*.csv)

echo "Tool,Benchmark,Project,Class,NPE,Execution" >> ${1} 


for file in ${files}; do
	tool=$(echo ${file} | rev | cut -d "/" -f1 | rev | cut -d "_" -f1)
	echo ${tool}
	echo ${file}

	while read line
	do
		if [[ "$line" == *"Project"* ]]; then
			continue
		fi

		if [[ "$line" == *"Unique"* ]]; then
			continue
		fi

		result="${tool},${line}"

		echo ${result} >> ${1}

	done < ${file}


done

