package com.otilm.mcp.tool;

import java.lang.reflect.Type;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

/**
 * Hands a tool's text to the MCP client as it is; Spring AI's default converter would encode it as a JSON string.
 */
public class PlainTextResult implements ToolCallResultConverter {

    @Override
    public String convert(Object result, Type returnType) {
        return String.valueOf(result);
    }
}
