package com.actiongraph.policy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegexMaskingPolicy implements DataMaskingPolicy {
    private static final Pattern ID_CARD = Pattern.compile("\\b\\d{17}[\\dXx]\\b");
    private static final Pattern BANK_CARD = Pattern.compile("\\b\\d{13,19}\\b");
    private static final Pattern MOBILE = Pattern.compile("\\b1[3-9]\\d{9}\\b");
    private static final Pattern EMAIL = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}\\b");

    private final List<Rule> rules;
    private final Set<String> blockedKeys;

    private RegexMaskingPolicy(List<Rule> rules, Set<String> blockedKeys) {
        this.rules = List.copyOf(rules);
        this.blockedKeys = Set.copyOf(blockedKeys);
    }

    public static RegexMaskingPolicy financialDefaults() {
        return builder().addFinancialDefaults().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String maskText(String text) {
        String masked = text == null ? "" : text;
        for (Rule rule : rules) {
            masked = applyRule(masked, rule);
        }
        return masked;
    }

    @Override
    public Map<String, String> maskData(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        Map<String, String> masked = new LinkedHashMap<>();
        data.forEach((key, value) -> masked.put(
                key,
                isBlockedKey(key) ? "***" : maskText(value)
        ));
        return masked;
    }

    private boolean isBlockedKey(String key) {
        return key != null && blockedKeys.contains(key.toLowerCase(Locale.ROOT));
    }

    private static String applyRule(String input, Rule rule) {
        Matcher matcher = rule.pattern().matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(rule.replacer().apply(matcher)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String maskMiddle(String value, int prefix, int suffix, int minStars) {
        if (value.length() <= prefix + suffix) {
            return "*".repeat(Math.max(minStars, value.length()));
        }
        int stars = Math.max(minStars, value.length() - prefix - suffix);
        return value.substring(0, prefix) + "*".repeat(stars) + value.substring(value.length() - suffix);
    }

    private record Rule(Pattern pattern, Function<Matcher, String> replacer) {
        private Rule {
            Objects.requireNonNull(pattern, "pattern");
            Objects.requireNonNull(replacer, "replacer");
        }
    }

    public static final class Builder {
        private final List<Rule> rules = new ArrayList<>();
        private final Set<String> blockedKeys = new LinkedHashSet<>();

        public Builder addFinancialDefaults() {
            return addRule(ID_CARD, matcher -> maskMiddle(matcher.group(), 6, 4, 8))
                    .addRule(BANK_CARD, matcher -> maskMiddle(matcher.group(), 6, 4, 3))
                    .addRule(MOBILE, matcher -> maskMiddle(matcher.group(), 3, 4, 4))
                    .addRule(EMAIL, matcher -> {
                        String value = matcher.group();
                        int at = value.indexOf('@');
                        String local = value.substring(0, at);
                        return local.charAt(0) + "***" + value.substring(at);
                    });
        }

        public Builder addRule(String regex, Function<Matcher, String> replacer) {
            return addRule(Pattern.compile(regex), replacer);
        }

        public Builder addRule(Pattern pattern, Function<Matcher, String> replacer) {
            rules.add(new Rule(pattern, replacer));
            return this;
        }

        public Builder addBlockedKey(String key) {
            if (key != null && !key.isBlank()) {
                blockedKeys.add(key.toLowerCase(Locale.ROOT));
            }
            return this;
        }

        public Builder addBlockedKeys(Collection<String> keys) {
            if (keys != null) {
                keys.forEach(this::addBlockedKey);
            }
            return this;
        }

        public RegexMaskingPolicy build() {
            return new RegexMaskingPolicy(rules, blockedKeys);
        }
    }
}
