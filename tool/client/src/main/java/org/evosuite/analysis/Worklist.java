package org.evosuite.analysis;

import org.evosuite.Properties;
import org.evosuite.classpath.ClassPathHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;

import org.evosuite.utils.LoggingUtils;

import spoon.Launcher;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCodeElement;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtSuperAccess;
import spoon.reflect.code.CtTargetedExpression;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtBlockImpl;
import spoon.support.reflect.code.CtCaseImpl;
import spoon.support.reflect.code.CtConditionalImpl;
import spoon.support.reflect.code.CtForEachImpl;
import spoon.support.reflect.code.CtForImpl;
import spoon.support.reflect.code.CtIfImpl;
import spoon.support.reflect.code.CtLocalVariableImpl;
import spoon.support.reflect.code.CtSwitchImpl;
import spoon.support.reflect.code.CtWhileImpl;
import spoon.support.reflect.declaration.CtParameterImpl;
// import com.thoughtworks.xstream.XStream;

public class Worklist implements Serializable {

    private static Launcher l;

    private static Collection<CtFieldReference<?>> fieldInfo = new HashSet<>();

    private static HashMap<CtFieldReference<?>, String> fieldsTyp = new HashMap<>();

    public Worklist() {

    }

    private HashSet<VarInfo> worklist = new HashSet<>();

    public void init(HashSet<CtElement> varEle) {
        // worklist        
    }

    public Worklist deepClone() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(this);;

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            return (Worklist) ois.readObject();
        } catch (IOException e) {
            return null;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
    
    public boolean checkFixedPoint(Worklist prev) {
        if (prev.equals(this)) return true;

        return false;
    }
}