package by.gusto.common.exception;

import lombok.Getter;

@Getter
public class GustoException extends RuntimeException {

    private final ErrorCode errorCode;

    public GustoException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public GustoException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
