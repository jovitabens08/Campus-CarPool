package com.campuscarpool.model;

/** Thrown when an operation violates a business rule (not a database failure). */
public class BusinessException extends Exception {
    private static final long serialVersionUID = 1L;

    public BusinessException(String message) { super(message); }
}
