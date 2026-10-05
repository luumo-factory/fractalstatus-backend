package ai.luumo.fractalstatus.tree;

import ai.luumo.fractalstatus.model.GroupNode;
import ai.luumo.fractalstatus.model.Node;
import ai.luumo.fractalstatus.model.NodeType;
import ai.luumo.fractalstatus.model.config.Display;
import ai.luumo.fractalstatus.model.config.DisplayColors;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Materialises each node's effective colour palette from the named colour schemes
 * declared at the top of the config.
 *
 * <p>For every node the effective palette is: the referenced scheme
 * ({@code display.scheme}, falling back to the per-type default scheme), with any
 * explicit per-state {@code display.colors} entries overlaid on top. The result
 * is written back into {@code display.colors} so the resolved API views carry the
 * final colours while the raw config keeps the authored scheme name.
 */
@Component
public class SchemeResolver {

    public void resolve(Node root,
                        Map<String, DisplayColors> schemes,
                        Map<String, String> defaultSchemes) {
        if (root == null) {
            return;
        }
        apply(root, schemes, defaultSchemes);
        if (root instanceof GroupNode g) {
            for (Node child : g.getChildren()) {
                resolve(child, schemes, defaultSchemes);
            }
        }
    }

    private void apply(Node node,
                       Map<String, DisplayColors> schemes,
                       Map<String, String> defaultSchemes) {
        Display display = node.getDisplay();
        if (display == null) {
            display = new Display();
            node.setDisplay(display);
        }

        String typeKey = node.getType() == NodeType.GROUP ? "group" : "entity";
        String schemeName = display.getScheme() != null
                ? display.getScheme()
                : (defaultSchemes != null ? defaultSchemes.get(typeKey) : null);

        DisplayColors base = (schemeName != null && schemes != null)
                ? schemes.get(schemeName)
                : null;

        DisplayColors resolved = overlay(base, display.getColors());
        display.setColors(resolved);
    }

    /** Returns a palette where each state uses the override if set, else the base. */
    private DisplayColors overlay(DisplayColors base, DisplayColors override) {
        if (base == null && override == null) {
            return null;
        }
        DisplayColors out = new DisplayColors();
        out.setOnline(pick(override == null ? null : override.getOnline(),
                base == null ? null : base.getOnline()));
        out.setWarning(pick(override == null ? null : override.getWarning(),
                base == null ? null : base.getWarning()));
        out.setOffline(pick(override == null ? null : override.getOffline(),
                base == null ? null : base.getOffline()));
        out.setUnknown(pick(override == null ? null : override.getUnknown(),
                base == null ? null : base.getUnknown()));
        return out;
    }

    private String pick(String override, String base) {
        return override != null ? override : base;
    }
}
