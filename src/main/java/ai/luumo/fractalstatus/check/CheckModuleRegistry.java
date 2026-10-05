package ai.luumo.fractalstatus.check;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of available {@link CheckModule}s, indexed by {@link CheckModule#id()}.
 * All Spring-managed modules are discovered automatically via constructor
 * injection.
 */
@Component
public class CheckModuleRegistry {

    private final Map<String, CheckModule> modules = new HashMap<>();

    public CheckModuleRegistry(List<CheckModule> discovered) {
        for (CheckModule module : discovered) {
            CheckModule previous = modules.put(module.id(), module);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate check module id: " + module.id());
            }
        }
    }

    public Optional<CheckModule> find(String id) {
        return Optional.ofNullable(modules.get(id));
    }

    public CheckModule require(String id) {
        return find(id).orElseThrow(() ->
                new IllegalArgumentException("Unknown check module: " + id));
    }
}
