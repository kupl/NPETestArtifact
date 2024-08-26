package org.evosuite.commons.filters;

import org.evosuite.commons.spoon.ASTUtils;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtThrow;
import spoon.reflect.reference.CtTypeReference;

public class LiteralFilter extends BaseTypeFilter<CtLiteral<?>> {
  private CtTypeReference<?> type;

  public static final LiteralFilter INSTANCE = new LiteralFilter();

  private LiteralFilter() {
    super(CtLiteral.class);
  }

  public LiteralFilter setType(CtTypeReference<?> type) {
    this.type = type;
    return this;
  }

  @Override
  public boolean matches(CtLiteral<?> literal) {
    if (!super.matches(literal)) {
      return false;
    }

    if (!literal.getType().unbox().getQualifiedName().equals(type.getQualifiedName())) {
      return false;
    }

    // Filter out string which is a message of logger or exception
    if (type.getQualifiedName().equals("java.lang.String")) {
      CtStatement enclosingStatement = ASTUtils.getEnclosingStatement(literal);
      if (enclosingStatement != null) {
        if (enclosingStatement instanceof CtThrow) {
          return false;
        } else {
          String code = enclosingStatement.toString().toLowerCase();
          if (code.contains("log.") || code.contains("logger.") || code.contains("print")) {
            return false;
          }
        }
      }
    }

    return true;
  }
}