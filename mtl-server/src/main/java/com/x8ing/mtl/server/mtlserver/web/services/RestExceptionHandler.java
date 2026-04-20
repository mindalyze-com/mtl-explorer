package com.x8ing.mtl.server.mtlserver.web.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String errorMessage = "Error occurred while deserializing request: " + ex.getMessage();
        log.warn(ex.toString(), ex);
        return buildResponseEntity(HttpStatus.BAD_REQUEST, errorMessage);
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<Object> handleHttpMessageNotWritable(HttpMessageNotWritableException ex) {
        String errorMessage = "Error occurred while serializing response: " + ex.getMessage();
        log.warn(errorMessage, ex);
        return buildResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR, errorMessage);
    }

    private ResponseEntity<Object> buildResponseEntity(HttpStatus httpStatus, String errorMessage) {
        // Customize your error response here
        log.warn(errorMessage);
        return new ResponseEntity<>(errorMessage, httpStatus);
    }
}
