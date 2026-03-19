package com.example.optimizepatientimport.emr;

/**
 * Base exception for EMR lookup failures.
 */
public class EmrLookupException extends RuntimeException {

    public EmrLookupException(String message) {
        super(message);
    }

    public EmrLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
