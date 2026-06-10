package com.actiongraph.console.spring;

import com.actiongraph.console.ConsoleOptions;
import com.actiongraph.console.ConsolePageRenderer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@RestController
@RequestMapping("${actiongraph.console.path:/actiongraph/console}")
public final class ActionGraphConsolePageController {
    private static final String CONSOLE_PAGE_RESOURCE = "/actiongraph/console/index.html";

    private final String consolePage;

    public ActionGraphConsolePageController(ActionGraphConsoleProperties properties) {
        Objects.requireNonNull(properties, "properties");
        this.consolePage = ConsolePageRenderer.render(
                loadConsolePage(),
                new ConsoleOptions(
                        properties.getTokenHeader(),
                        properties.getDefaultLimit(),
                        properties.getMaxLimit()
                )
        );
    }

    @GetMapping(value = {"", "/"}, produces = MediaType.TEXT_HTML_VALUE)
    public String page() {
        return consolePage;
    }

    private String loadConsolePage() {
        try (InputStream input = ActionGraphConsolePageController.class.getResourceAsStream(CONSOLE_PAGE_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Console page resource not found: " + CONSOLE_PAGE_RESOURCE);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read console page resource", ex);
        }
    }
}
