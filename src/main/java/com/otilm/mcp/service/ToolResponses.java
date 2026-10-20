package com.otilm.mcp.service;

import com.otilm.api.model.common.ErrorMessageDto;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

/**
 * Turns a tool's work into the text an AI client receives, including failures: invalid input as given, ILM errors as
 * status and message, anything else as a short error line.
 */
public final class ToolResponses {

    private static final Logger logger = LoggerFactory.getLogger(ToolResponses.class);

    private ToolResponses() {
    }

    public static String run(String action, Supplier<String> body) {
        try {
            return body.get();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (RestClientResponseException e) {
            logger.warn("ILM answered {} while {}", e.getStatusCode().value(), action);
            return "Error " + action + ": ILM answered " + status(e) + ilmMessage(e);
        } catch (RuntimeException e) {
            logger.error("Error {}", action, e);
            return "Error " + action + ": " + e.getMessage();
        }
    }

    private static String status(RestClientResponseException e) {
        int code = e.getStatusCode().value();
        HttpStatus status = HttpStatus.resolve(code);
        return code + " " + (status != null ? status.getReasonPhrase() : e.getStatusText());
    }

    private static String ilmMessage(RestClientResponseException e) {
        try {
            ErrorMessageDto error = e.getResponseBodyAs(ErrorMessageDto.class);
            if (error != null && error.getMessage() != null && !error.getMessage().isBlank()) {
                return ": " + error.getMessage();
            }
        } catch (RuntimeException notAnErrorMessage) {
            logger.trace("ILM error body is not a single message", notAnErrorMessage);
        }
        try {
            String[] messages = e.getResponseBodyAs(String[].class);
            if (messages != null && messages.length > 0) {
                return ": " + String.join("; ", messages);
            }
        } catch (RuntimeException notAMessageList) {
            logger.trace("ILM error body is not a message list", notAMessageList);
        }
        return "";
    }
}
