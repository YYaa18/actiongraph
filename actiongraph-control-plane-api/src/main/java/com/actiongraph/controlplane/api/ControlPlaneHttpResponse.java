package com.actiongraph.controlplane.api;

import org.jspecify.annotations.Nullable;

public final class ControlPlaneHttpResponse {
    private final int statusCode;
    private final String body;

    public ControlPlaneHttpResponse(int statusCode, @Nullable String body) {
        this.statusCode = statusCode;
        this.body = body == null ? "" : body;
    }

    public int statusCode() {
        return statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String body() {
        return body;
    }

    public String getBody() {
        return body;
    }

    public boolean successful() {
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean isSuccessful() {
        return successful();
    }

    public String error() {
        return jsonStringField("error");
    }

    public String getError() {
        return error();
    }

    public boolean hasError(@Nullable String error) {
        return !isBlank(error) && error.equals(error());
    }

    private String jsonStringField(String fieldName) {
        String needle = "\"" + fieldName + "\"";
        int index = body.indexOf(needle);
        while (index >= 0) {
            int cursor = skipWhitespace(body, index + needle.length());
            if (cursor < body.length() && body.charAt(cursor) == ':') {
                cursor = skipWhitespace(body, cursor + 1);
                if (cursor < body.length() && body.charAt(cursor) == '"') {
                    return readJsonString(body, cursor + 1);
                }
            }
            index = body.indexOf(needle, index + needle.length());
        }
        return "";
    }

    private static String readJsonString(String json, int start) {
        @Nullable StringBuilder result = null;
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"') {
                return result == null ? json.substring(start, i) : result.toString();
            }
            if (ch == '\\') {
                if (i + 1 >= json.length()) {
                    return "";
                }
                if (result == null) {
                    result = new StringBuilder();
                    result.append(json, start, i);
                }
                i++;
                char escaped = json.charAt(i);
                switch (escaped) {
                    case '"':
                    case '\\':
                    case '/':
                        result.append(escaped);
                        break;
                    case 'b':
                        result.append('\b');
                        break;
                    case 'f':
                        result.append('\f');
                        break;
                    case 'n':
                        result.append('\n');
                        break;
                    case 'r':
                        result.append('\r');
                        break;
                    case 't':
                        result.append('\t');
                        break;
                    case 'u':
                        if (i + 4 >= json.length()) {
                            return "";
                        }
                        String hex = json.substring(i + 1, i + 5);
                        try {
                            result.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException ex) {
                            return "";
                        }
                        i += 4;
                        break;
                    default:
                        result.append(escaped);
                        break;
                }
            } else if (result != null) {
                result.append(ch);
            }
        }
        return "";
    }

    private static int skipWhitespace(String value, int index) {
        int cursor = index;
        while (cursor < value.length() && Character.isWhitespace(value.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
