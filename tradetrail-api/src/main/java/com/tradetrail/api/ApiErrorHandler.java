package com.tradetrail.api;

import com.tradetrail.domain.TradeFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(TradeFailure.class)
    public ResponseEntity<Map<String, Object>> trade(TradeFailure error, HttpServletRequest request) {
        HttpStatus status = switch (error.code()) {
            case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "PRODUCT_NOT_FOUND", "ORDER_NOT_FOUND", "AFTER_SALE_NOT_FOUND", "TRACKING_NOT_FOUND", "CART_ITEM_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_PRODUCT", "INVALID_ORDER", "INVALID_REASON", "INVALID_TRACKING_EVENT", "INVALID_SIGNATURE", "INVALID_CART", "INVALID_ADJUSTMENT" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(body(error.code(), error.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(body("VALIDATION_ERROR", "请求参数不合法", request));
    }

    private Map<String, Object> body(String code, String message, HttpServletRequest request) {
        return Map.of("code", code, "message", message, "traceId", UUID.randomUUID().toString(),
                "currentStatus", "", "allowedActions", List.of(), "path", request.getRequestURI());
    }
}
