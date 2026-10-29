package com.tradetrail.api;

import com.tradetrail.application.integration.TraceContext;
import com.tradetrail.domain.TradeFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(TradeFailure.class)
    public ResponseEntity<Map<String, Object>> trade(TradeFailure error, HttpServletRequest request) {
        int status = error.code().equals("FORBIDDEN") ? 403
                : error.code().endsWith("NOT_FOUND") ? 404 : error.code().startsWith("INVALID_") ? 400 : 409;
        return ResponseEntity.status(status)
                .body(body(error.code(), error.getMessage(), error.currentStatus(), error.allowedActions()));
    }

    @ExceptionHandler({ MethodArgumentNotValidException.class, MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class })
    public ResponseEntity<Map<String, Object>> validation(Exception error) {
        return ResponseEntity.badRequest().body(body("VALIDATION_ERROR", "请求参数不合法", "", List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception error) {
        LoggerFactory.getLogger(ApiErrorHandler.class).error("请求处理失败 traceId={}", TraceContext.current(), error);
        return ResponseEntity.internalServerError().body(body("INTERNAL_ERROR", "服务暂时不可用，请使用原请求号重试", "", List.of()));
    }

    public static Map<String, Object> body(String code, String message, String state, List<String> actions) {
        return Map.of("code", code, "message", message, "traceId", TraceContext.current(), "currentStatus", state,
                "allowedActions", actions);
    }
}
