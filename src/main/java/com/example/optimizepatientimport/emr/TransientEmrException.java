package com.example.optimizepatientimport.emr;

/**
 * Exception indicating a transient EMR error which is safe to retry.
 */
public class TransientEmrException extends EmrLookupException {

    public TransientEmrException(String message) {
        super(message);
    }

    public TransientEmrException(String message, Throwable cause) {
        super(message, cause);
    }
}
