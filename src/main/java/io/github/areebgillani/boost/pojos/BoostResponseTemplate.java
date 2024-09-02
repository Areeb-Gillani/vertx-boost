package io.github.areebgillani.boost.pojos;

public class BoostResponseTemplate {
    int statusCode;
    String message;
    Object data;
    String errorMessage;
    String trace;

    public BoostResponseTemplate(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
    public BoostResponseTemplate(int statusCode, String message, Object data) {
        this.statusCode = statusCode;
        this.message = message;
        this.data = data;
    }

    public BoostResponseTemplate(int statusCode, String errorMessage, Throwable trace) {
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
        this.trace = trace.toString();
    }

    public BoostResponseTemplate(int statusCode, String message, Object data, String errorMessage, String trace) {
        this.statusCode = statusCode;
        this.message = message;
        this.data = data;
        this.errorMessage = errorMessage;
        this.trace = trace;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getTrace() {
        return trace;
    }

    public void setTrace(String trace) {
        this.trace = trace;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }
}

