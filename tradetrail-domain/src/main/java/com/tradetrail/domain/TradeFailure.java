package com.tradetrail.domain;

import java.util.List;

public class TradeFailure extends RuntimeException {
    private final String code;
    private final String currentStatus;
    private final List<String> allowedActions;

    public TradeFailure(String code, String message) {
        this(code, message, "", List.of());
    }

    public TradeFailure(String code, String message, String currentStatus, List<String> allowedActions) {
        super(message);
        this.code = code;
        this.currentStatus = currentStatus;
        this.allowedActions = List.copyOf(allowedActions);
    }

    public String code() {
        return code;
    }

    public String currentStatus() {
        return currentStatus;
    }

    public List<String> allowedActions() {
        return allowedActions;
    }
}
