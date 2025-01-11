package com.mohistmc.banner.stackdeobf.mappings;

// Created by booky10 in StackDeobfuscator (18:03 20.03.23)

import lombok.Getter;
import org.jetbrains.annotations.ApiStatus;

@Getter
@ApiStatus.Internal
public class RemappedThrowable extends Throwable {

    private final Throwable original;
    private final String className;

    public RemappedThrowable(String message, Throwable cause,
                             Throwable original, String className) {
        super(message, cause);
        this.original = original;
        this.className = className;
    }

    @Override
    public String toString() {
        String message = this.getLocalizedMessage();
        if (message == null) {
            return this.className;
        }
        return this.className + ": " + message;
    }
}
