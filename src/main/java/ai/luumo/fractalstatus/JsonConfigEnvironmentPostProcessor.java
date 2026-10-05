package ai.luumo.fractalstatus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the whole-application configuration from a JSON file (e.g. the listen
 * port) and contributes it to the Spring {@link org.springframework.core.env.Environment}.
 *
 * <p>Resolution order for the file:
 * <ol>
 *   <li>the path in the {@code fractalstatus.config-file} property / env var
 *       {@code FRACTALSTATUS_CONFIG_FILE} (supports {@code file:}/{@code classpath:}),</li>
 *   <li>otherwise {@code ./fractalstatus.json} next to the running app,</li>
 *   <li>otherwise the bundled {@code classpath:fractalstatus.json} defaults.</li>
 * </ol>
 *
 * <p>The JSON is flattened to dotted property keys using the same namespace as
 * Spring properties (e.g. {@code server.port}, {@code fractalstatus.config-path})
 * and inserted just below system properties / environment variables, so those
 * and command-line arguments can still override the file.
 */
public class JsonConfigEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "fractalstatusJsonConfig";
    private static final String FILE_PROPERTY = "fractalstatus.config-file";
    private static final String DEFAULT_FILE = "fractalstatus.json";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Resource resource = locate(environment);
        if (resource == null || !resource.exists()) {
            return;
        }

        Map<String, Object> flattened = new LinkedHashMap<>();
        try (InputStream in = resource.getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> parsed = JsonParserFactory.getJsonParser().parseMap(json);
            flatten(null, parsed, flattened);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read application config file: " + resource, e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to parse application config file: " + resource, e);
        }

        MapPropertySource source = new MapPropertySource(SOURCE_NAME, flattened);
        // Below system env/properties and command line (so those can override),
        // but above application.properties and the code defaults.
        if (environment.getPropertySources()
                .contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addAfter(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
        } else {
            environment.getPropertySources().addFirst(source);
        }
    }

    private Resource locate(ConfigurableEnvironment environment) {
        String configured = environment.getProperty(FILE_PROPERTY);
        if (StringUtils.hasText(configured)) {
            // A plain path (e.g. /etc/app/fractalstatus.json) must be treated as a
            // filesystem resource; DefaultResourceLoader would otherwise resolve it
            // as a classpath resource. Only defer to the loader for explicit schemes.
            if (configured.startsWith("classpath:")
                    || configured.startsWith("file:")
                    || configured.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
                return new DefaultResourceLoader().getResource(configured);
            }
            return new FileSystemResource(configured);
        }
        File external = new File(DEFAULT_FILE);
        if (external.exists()) {
            return new FileSystemResource(external);
        }
        return new ClassPathResource(DEFAULT_FILE);
    }

    private void flatten(String prefix, Object value, Map<String, Object> out) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> flatten(join(prefix, String.valueOf(k)), v, out));
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                flatten(prefix + "[" + i + "]", list.get(i), out);
            }
        } else {
            out.put(prefix, value);
        }
    }

    private String join(String prefix, String key) {
        return prefix == null ? key : prefix + "." + key;
    }
}
