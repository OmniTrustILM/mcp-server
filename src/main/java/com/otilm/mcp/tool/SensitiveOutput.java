package com.otilm.mcp.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Restricts deliberately returned credential or private material to full access. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SensitiveOutput {
}
