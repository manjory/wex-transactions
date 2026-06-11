package com.wex.transactions.exception;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(String id) {
        super("Purchase transaction not found with id: " + id);
    }
}
