package com.example.analytics.event;

/** Mensaje que no se puede leer o al que le faltan campos obligatorios: reintentar no lo arregla, va directo al DLT. */
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }

    public MalformedEventException(String message) {
        super(message);
    }
}
