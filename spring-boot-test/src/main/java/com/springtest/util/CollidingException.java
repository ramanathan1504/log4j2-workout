package com.springtest.util;

import java.util.Objects;

public class CollidingException extends RuntimeException {
    public CollidingException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CollidingException)) return false;
        // Logic: different objects are "equal" if messages match
        return Objects.equals(getMessage(), ((CollidingException) obj).getMessage());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getMessage());
    }
}
