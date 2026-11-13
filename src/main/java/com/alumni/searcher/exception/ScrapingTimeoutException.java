package com.alumni.searcher.exception;

// Raised when the PhantomBuster job is still running after the  maximum time we are willing to wait.
public class ScrapingTimeoutException extends RuntimeException {

    public ScrapingTimeoutException(String message) {
        super(message);
    }
}
