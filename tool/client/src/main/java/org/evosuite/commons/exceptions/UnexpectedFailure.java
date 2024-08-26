package org.evosuite.commons.exceptions;

public class UnexpectedFailure extends RuntimeException {
  public UnexpectedFailure(String message) {
    super(message);
  }

  public UnexpectedFailure(Throwable cause) {
    super(cause);
  }
}
