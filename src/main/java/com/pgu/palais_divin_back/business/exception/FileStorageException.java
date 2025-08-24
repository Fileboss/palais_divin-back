package com.pgu.palais_divin_back.business.exception;

public class FileStorageException extends RuntimeException{

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileStorageException(String message) {
        super(message);
    }
}
