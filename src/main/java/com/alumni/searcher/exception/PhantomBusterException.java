package com.alumni.searcher.exception;

// Raised when PhantomBuster cannot be reached, rejects our request, or the scraping job itself fails.
public class PhantomBusterException extends RuntimeException {

    public PhantomBusterException(String message) {
        super(message);
    }

    public PhantomBusterException(String message, Throwable cause) {
        super(message, cause);
    }
}
