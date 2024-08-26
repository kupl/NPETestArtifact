package org.evosuite.commons.exceptions;

import java.io.File;
import java.io.IOException;

public class CLIOptionValidationError extends RuntimeException {
  public CLIOptionValidationError(String message, Throwable cause) {
    super(message, cause);
  }

  public CLIOptionValidationError(String message) {
    super(message);
  }

  public static CLIOptionValidationError noSuchFile(File file) {
    return new CLIOptionValidationError(file + " doesn't exist");
  }

  public static CLIOptionValidationError fromIOException(File file, IOException e) {
    return new CLIOptionValidationError("IOException on file `" + file + "`", e);
  }
}
