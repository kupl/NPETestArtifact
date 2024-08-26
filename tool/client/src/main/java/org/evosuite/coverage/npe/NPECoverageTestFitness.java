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
package org.evosuite.coverage.npe;

import org.evosuite.Properties;
import org.evosuite.ga.archive.Archive;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.utils.LoggingUtils;
import org.evosuite.utils.generic.GenericClass;
import org.evosuite.utils.generic.GenericClassFactory;

import java.util.Objects;


/**
 * Fitness function for a single test on a single exception
 *
 * @author Gordon Fraser, Jose Miguel Rojas
 */
public class NPECoverageTestFitness extends TestFitnessFunction {


    private static final long serialVersionUID = 1221020001417476348L;

    protected final String line;

    protected final String className;

    protected final boolean triggerNPE;

    /**
     * name+descriptor
     */
    protected final String methodIdentifier;


    /**
     * Constructor - fitness is specific to a method
     *
     * @param methodIdentifier the method name
     * @param exceptionClass   the exception class
     */
    public NPECoverageTestFitness(String className, String methodIdentifier, String line, boolean triggerNPE) {

        this.className = Objects.requireNonNull(className, "class cannot be null");
        this.methodIdentifier = Objects.requireNonNull(methodIdentifier, "method identifier cannot be null");
        this.line = Objects.requireNonNull(line, "line cannot be null");

        this.triggerNPE = Objects.requireNonNull(triggerNPE, "tirggerNPE cannot be null");
    }

    public String getKey() {
        return methodIdentifier + ":" + line;
    }

    /**
     * <p>
     * getMethod
     * </p>
     *
     * @return a {@link String} object.
     */
    public String getMethod() {
        return methodIdentifier;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Calculate fitness
     *
     * @param individual a {@link org.evosuite.testcase.ExecutableChromosome} object.
     * @param result     a {@link org.evosuite.testcase.execution.ExecutionResult} object.
     * @return a double.
     */
    @Override
    public double getFitness(TestChromosome individual, ExecutionResult result) {
        double fitness = 1.0;

        // Using private reflection can lead to false positives
        // that represent unrealistic behaviour. Thus, we only
        // use reflection for basic criteria, not for exception
        if (result.calledReflection())
            return fitness;

        //iterate on the indexes of the statements that resulted in an exception
        for (Integer i : result.getPositionsWhereExceptionsWereThrown()) {
            if (NPECoverageHelper.shouldSkip(result, i)) {
                continue;
            }

            String methodIdentifier = NPECoverageHelper.getMethodIdentifier(result, i); //eg name+descriptor
            boolean sutException = NPECoverageHelper.isSutException(result, i); // was the exception originated by a direct call on the SUT?

            /*
             * We only consider exceptions that were thrown directly in the SUT (not called libraries)
             */
            if (sutException) {

                String line = NPECoverageHelper.getLine(result, i);

                if (this.methodIdentifier.equals(methodIdentifier) && this.line.equals(line)) {
                    fitness = 0.0;
                    break;
                }
            }
        }
        LoggingUtils.getEvoLogger().info("UPDATE INDIVIDUAL ADDCOVERED GOAL");

        if (fitness == 0.0) {
            individual.getTestCase().addCoveredGoal(this);
        }
        LoggingUtils.getEvoLogger().info("UPDATE INDIVIDUAL - done");


        if (Properties.TEST_ARCHIVE) {
            Archive.getArchiveInstance().addTarget(this);
            Archive.getArchiveInstance().updateArchive(this, individual, fitness);
        }

        return fitness;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return getKey();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int iConst = 17;
        return 53 * iConst + methodIdentifier.hashCode() * iConst + line.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        NPECoverageTestFitness other = (NPECoverageTestFitness) obj;
        if (!methodIdentifier.equals(other.methodIdentifier)) {
            return false;
        } else {
            return this.line.equals(other.line);
        }
    }

    /* (non-Javadoc)
     * @see org.evosuite.testcase.TestFitnessFunction#compareTo(org.evosuite.testcase.TestFitnessFunction)
     */
    @Override
    public int compareTo(TestFitnessFunction other) {
        if (other instanceof NPECoverageTestFitness) {
            NPECoverageTestFitness otherMethodFitness = (NPECoverageTestFitness) other;
            if (methodIdentifier.equals(otherMethodFitness.getMethod())) {
                    return this.line.compareTo(((NPECoverageTestFitness) other).line);
            } else
                return methodIdentifier.compareTo(otherMethodFitness.getMethod());
        }
        return compareClassName(other);
    }


    /* (non-Javadoc)
     * @see org.evosuite.testcase.TestFitnessFunction#getTargetClass()
     */
    @Override
    public String getTargetClass() {
        return className;
    }

    /* (non-Javadoc)
     * @see org.evosuite.testcase.TestFitnessFunction#getTargetMethod()
     */
    @Override
    public String getTargetMethod() {
        return methodIdentifier;
    }

    /* (non-Javadoc)
     * @see org.evosuite.testcase.TestFitnessFunction#triggeredNPE()
     */
    
    public boolean triggeredNPE() {
        return triggerNPE;
    }
    

}
