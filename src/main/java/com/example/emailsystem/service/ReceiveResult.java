package com.example.emailsystem.service;

public record ReceiveResult(int scanned, int imported, int duplicates, int skipped) {

    public String message() {
        if (scanned == 0) {
            return "Connected to POP3, but Gmail returned 0 messages.";
        }
        return "Scanned " + scanned
                + " message(s), imported " + imported
                + ", duplicates " + duplicates
                + ", skipped " + skipped + ".";
    }
}
