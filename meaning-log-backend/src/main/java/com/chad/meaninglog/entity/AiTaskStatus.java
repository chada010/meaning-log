package com.chad.meaninglog.entity;

public enum AiTaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }
}
