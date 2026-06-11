package com.actiongraph.controlplane.api;

import java.util.Properties;

public final class ActionGraphControlPlaneHttpClientProperties {
    public static final String DEFAULT_PREFIX = "actiongraph.control-plane.";

    private final Properties properties;
    private final String prefix;

    private ActionGraphControlPlaneHttpClientProperties(Properties properties, String prefix) {
        this.properties = new Properties();
        if (properties != null) {
            this.properties.putAll(properties);
        }
        this.prefix = normalizePrefix(prefix);
    }

    public static ActionGraphControlPlaneHttpClient build(Properties properties) {
        return from(properties).toClient();
    }

    public static ActionGraphControlPlaneHttpClient build(Properties properties, String prefix) {
        return from(properties, prefix).toClient();
    }

    public static ActionGraphControlPlaneHttpClientProperties from(Properties properties) {
        return new ActionGraphControlPlaneHttpClientProperties(properties, DEFAULT_PREFIX);
    }

    public static ActionGraphControlPlaneHttpClientProperties from(Properties properties, String prefix) {
        return new ActionGraphControlPlaneHttpClientProperties(properties, prefix);
    }

    public ActionGraphControlPlaneHttpClient toClient() {
        ActionGraphControlPlaneHttpClient.Builder builder = ActionGraphControlPlaneHttpClient.builder();
        applyText("base-url", new TextSetter() {
            @Override
            public void set(String value) {
                builder.actionGraphBaseUrl(value);
            }
        });
        applyText("runtime.base-url", new TextSetter() {
            @Override
            public void set(String value) {
                builder.runtimeApiBaseUrl(value);
            }
        });
        applyText("catalog.base-url", new TextSetter() {
            @Override
            public void set(String value) {
                builder.catalogApiBaseUrl(value);
            }
        });
        applyText("review.tasks-base-url", new TextSetter() {
            @Override
            public void set(String value) {
                builder.reviewTaskApiBaseUrl(value);
            }
        });
        applyText("review.callback-base-url", new TextSetter() {
            @Override
            public void set(String value) {
                builder.reviewCallbackApiBaseUrl(value);
            }
        });
        applyText("console.base-url", new TextSetter() {
            @Override
            public void set(String value) {
                builder.consoleApiBaseUrl(value);
            }
        });

        applyText("token-header", new TextSetter() {
            @Override
            public void set(String value) {
                builder.tokenHeader(value);
            }
        });
        applyText("runtime.token-header", new TextSetter() {
            @Override
            public void set(String value) {
                builder.runtimeTokenHeader(value);
            }
        });
        applyText("catalog.token-header", new TextSetter() {
            @Override
            public void set(String value) {
                builder.catalogTokenHeader(value);
            }
        });
        applyText("review.token-header", new TextSetter() {
            @Override
            public void set(String value) {
                builder.reviewTokenHeader(value);
            }
        });
        applyText("console.token-header", new TextSetter() {
            @Override
            public void set(String value) {
                builder.consoleTokenHeader(value);
            }
        });

        applyText("shared-secret", new TextSetter() {
            @Override
            public void set(String value) {
                builder.sharedSecret(value);
            }
        });
        applyText("runtime.shared-secret", new TextSetter() {
            @Override
            public void set(String value) {
                builder.runtimeSharedSecret(value);
            }
        });
        applyText("catalog.shared-secret", new TextSetter() {
            @Override
            public void set(String value) {
                builder.catalogSharedSecret(value);
            }
        });
        applyText("review.shared-secret", new TextSetter() {
            @Override
            public void set(String value) {
                builder.reviewSharedSecret(value);
            }
        });
        applyText("console.shared-secret", new TextSetter() {
            @Override
            public void set(String value) {
                builder.consoleSharedSecret(value);
            }
        });

        applyInt("connect-timeout-millis", new IntSetter() {
            @Override
            public void set(int value) {
                builder.connectTimeoutMillis(value);
            }
        });
        applyInt("read-timeout-millis", new IntSetter() {
            @Override
            public void set(int value) {
                builder.readTimeoutMillis(value);
            }
        });
        applyInt("max-get-retries", new IntSetter() {
            @Override
            public void set(int value) {
                builder.maxGetRetries(value);
            }
        });
        applyInt("get-retry-backoff-millis", new IntSetter() {
            @Override
            public void set(int value) {
                builder.getRetryBackoffMillis(value);
            }
        });
        applyDefaultHeaders(builder);
        return builder.build();
    }

    private void applyDefaultHeaders(ActionGraphControlPlaneHttpClient.Builder builder) {
        String headerPrefix = propertyName("default-header.");
        for (String name : properties.stringPropertyNames()) {
            if (!name.startsWith(headerPrefix)) {
                continue;
            }
            String headerName = name.substring(headerPrefix.length());
            String headerValue = properties.getProperty(name);
            if (!isBlank(headerName)) {
                builder.defaultHeader(headerName, headerValue);
            }
        }
    }

    private void applyText(String name, TextSetter setter) {
        String value = properties.getProperty(propertyName(name));
        if (!isBlank(value)) {
            setter.set(value.trim());
        }
    }

    private void applyInt(String name, IntSetter setter) {
        String propertyName = propertyName(name);
        String value = properties.getProperty(propertyName);
        if (isBlank(value)) {
            return;
        }
        try {
            setter.set(Integer.parseInt(value.trim()));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(propertyName + " must be an integer", ex);
        }
    }

    private String propertyName(String name) {
        return prefix + name;
    }

    private static String normalizePrefix(String prefix) {
        if (isBlank(prefix)) {
            return "";
        }
        String trimmed = prefix.trim();
        if (trimmed.endsWith(".")) {
            return trimmed;
        }
        return trimmed + ".";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private interface TextSetter {
        void set(String value);
    }

    private interface IntSetter {
        void set(int value);
    }
}
