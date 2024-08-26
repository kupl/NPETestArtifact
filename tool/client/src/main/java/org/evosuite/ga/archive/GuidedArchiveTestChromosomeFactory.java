/*
 * Copyright (C) 2010-2018 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 *
 * This file is part of EvoSuite.
 *
 * EvoSuite is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3.0 of the License, or
 * (at your option) any later version.
 *
 * EvoSuite is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 */
package org.evosuite.ga.archive;

import org.evosuite.Properties;
import org.evosuite.analysis.ClassInfo;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testcase.execution.TestCaseExecutor;
import org.evosuite.testcase.factories.GuidedRandomLengthTestFactory;
import org.evosuite.testsuite.TestSuiteSerialization;
import org.evosuite.utils.LoggingUtils;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;

public class GuidedArchiveTestChromosomeFactory implements ChromosomeFactory<TestChromosome> {

    private static final long serialVersionUID = -8499807341782893732L;

    private final static Logger logger = LoggerFactory.getLogger(GuidedArchiveTestChromosomeFactory.class);

    private final ChromosomeFactory<TestChromosome> defaultFactory = new GuidedRandomLengthTestFactory();

    /**
     * Serialized tests read from disk, eg from previous runs in CTG
     */
    // private List<TestChromosome> seededTests;

    public GuidedArchiveTestChromosomeFactory() {
        
        // if (Archive.getArchiveInstance().isArchiveEmpty()) {
        //     for (int i = 0; i < ClassInfo.getInstance().getTargetMethods().size() * (Randomness.nextInt(5)+3); i++) {
        //         Archive.numGen++;
        //         Properties.NUM_GENERATION++;
        //         buildInitialPopulation();
        //     }
        // }
    }


    @Override
    public TestChromosome getChromosome() {
        TestChromosome test = null;

        if (!Archive.getArchiveInstance().isArchiveEmpty()
                && Randomness.nextDouble() < 0.7) {

            Archive.numMutant++;
            Properties.NUM_MUTATION++;

            test = new TestChromosome();
            
            if (Randomness.nextDouble() < 0.3) { 
                test.setTestCase(Archive.getArchiveInstance().getRandomSolution().getTestCase());
            } else {
                test.setTestCase(Archive.getArchiveInstance().getGuidedSolution().getTestCase());                
            }
            
            int mutations = Randomness.nextInt(Properties.SEED_MUTATIONS * 2);
            
            for (int i = 0; i < mutations; i++) {
                test.guidedMutate();
            }

        } else {
            Archive.numGen++;
            Properties.NUM_GENERATION++;
            logger.info("Creating random test");
            test = defaultFactory.getChromosome();

            logger.debug("Created Random Test");
            logger.debug(test.toString());
        }

        return test;
    }

    public int getNumMutant() {
        return Archive.numMutant;
    }

    public int getNumGen() {
        return Archive.numGen;
    }
}
