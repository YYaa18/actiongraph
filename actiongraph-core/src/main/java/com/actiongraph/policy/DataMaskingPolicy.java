package com.actiongraph.policy;

import java.util.Map;

public interface DataMaskingPolicy {
    String maskText(String text);

    Map<String, String> maskData(Map<String, String> data);
}
