package com.pgu.palais_divin_back.business.exception;

public class GeoCodingException extends RuntimeException{

    public GeoCodingException(String message, Throwable cause) {
        super(message, cause);
    }

    public GeoCodingException(String message) {
        super(message);
    }
}
