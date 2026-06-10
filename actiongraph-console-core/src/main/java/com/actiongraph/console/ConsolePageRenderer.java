package com.actiongraph.console;

import java.util.Objects;

public final class ConsolePageRenderer {
    private ConsolePageRenderer() {
    }

    public static String render(String template, ConsoleOptions options) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(options, "options");
        return template
                .replace("__ACTIONGRAPH_CONSOLE_TOKEN_HEADER__", jsString(options.tokenHeader()))
                .replace("__ACTIONGRAPH_CONSOLE_DEFAULT_LIMIT__", Integer.toString(options.defaultLimit()))
                .replace("__ACTIONGRAPH_CONSOLE_MAX_LIMIT__", Integer.toString(options.maxLimit()));
    }

    private static String jsString(String value) {
        StringBuilder escaped = new StringBuilder("'");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '\'' -> escaped.append("\\'");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '<' -> escaped.append("\\u003c");
                case '>' -> escaped.append("\\u003e");
                case '&' -> escaped.append("\\u0026");
                default -> escaped.append(ch);
            }
        }
        return escaped.append("'").toString();
    }
}
