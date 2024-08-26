import pandas as pd
import sys
import os
from matplotlib import pyplot as plt
from matplotlib_venn import venn3

def main():
    csv_path = sys.argv[1]

    df = pd.read_csv(csv_path)

    df['Percentage'] = (df['NPE'] / df['Execution']) * 100

    npex_pivot = df[df['Benchmark'] == 'NPEX'].pivot_table(index='Project', columns='Tool', values='Percentage', fill_value=0)
    bugswarm_pivot = df[df['Benchmark'] == 'BugSwarm'].pivot_table(index='Project', columns='Tool', values='Percentage', fill_value=0)
    defects_pivot = df[df['Benchmark'] == 'Defects4J'].pivot_table(index='Project', columns='Tool', values='Percentage', fill_value=0)
    genesis_pivot = df[df['Benchmark'] == 'Genesis'].pivot_table(index='Project', columns='Tool', values='Percentage', fill_value=0)
    bears_pivot = df[df['Benchmark'] == 'Bears'].pivot_table(index='Project', columns='Tool', values='Percentage', fill_value=0)
    
    
    npetest_npe = set(df[(df['Tool'] == 'npetest') & (df['Percentage'] > 0)]['Project'])
    evosuite_npe = set(df[(df['Tool'] == 'evosuite') & (df['Percentage'] > 0)]['Project'])
    randoop_npe = set(df[(df['Tool'] == 'randoop') & (df['Percentage'] > 0)]['Project'])
    
    venn = venn3([npetest_npe, evosuite_npe, randoop_npe], ('NPETest', 'EvoSuite', 'Randoop'))
    
    plt.title("Venn Diagram of unique NPEs detected by each tool")
    
    plt.savefig("venn_diagram.png")


    with open(sys.argv[2], 'w') as f:
        sys.stdout = f
        print("NPEX:")
        print(npex_pivot)
        print("\n")
    
        print("BugSwarm:")
        print(bugswarm_pivot)
        
        print("\n")
    
        print("Defects4J:")
        print(defects_pivot)
    
        print("\n")
    
        print("Genesis:")
        print(genesis_pivot)
    
        print("\n")
    
        print("Bears:")
        print(bears_pivot)


    sys.stdout=sys.__stdout__

    # print("NPEX:")
    # print(npex_pivot)
    # print("\n")

    # print("BugSwarm:")
    # print(bugswarm_pivot)
    
    # print("\n")

    # print("Defects4J:")
    # print(defects_pivot)

    # print("\n")

    # print("Genesis:")
    # print(genesis_pivot)

    # print("\n")

    # print("Bears:")
    # print(bears_pivot)





if __name__ == "__main__":
    main()
