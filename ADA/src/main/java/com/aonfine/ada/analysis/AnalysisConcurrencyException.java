package com.aonfine.ada.analysis;

/** Unchecked so it rolls back the enclosing @Transactional method when a CAS step affects 0 rows. */
public class AnalysisConcurrencyException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public AnalysisConcurrencyException(String message) { super(message); }
}
