package com.tradetrail.domain;

public class TradeFailure extends RuntimeException {
    private final String code;

    public TradeFailure(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
