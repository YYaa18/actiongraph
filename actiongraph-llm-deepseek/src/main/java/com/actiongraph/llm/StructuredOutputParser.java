package com.actiongraph.llm;

public interface StructuredOutputParser<T> {
    T parse(String text);
}
