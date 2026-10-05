package ai.luumo.fractalstatus.tree;

import ai.luumo.fractalstatus.model.EntityNode;
import ai.luumo.fractalstatus.model.GroupNode;
import ai.luumo.fractalstatus.model.NodeType;
import ai.luumo.fractalstatus.model.config.Display;
import ai.luumo.fractalstatus.model.config.DisplayColors;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SchemeResolverTest {

    private final SchemeResolver resolver = new SchemeResolver();

    private DisplayColors colors(String on, String warn, String off, String unk) {
        DisplayColors c = new DisplayColors();
        c.setOnline(on);
        c.setWarning(warn);
        c.setOffline(off);
        c.setUnknown(unk);
        return c;
    }

    private Map<String, DisplayColors> schemes() {
        return Map.of(
                "node", colors("#2E7D32", "#F9A825", "#C62828", "#9E9E9E"),
                "group", colors("#ECEFF1", "#F9A825", "#C62828", "#CFD8DC"));
    }

    private final Map<String, String> defaults = Map.of("group", "group", "entity", "node");

    @Test
    void entityGetsDefaultSchemeWhenUnannotated() {
        EntityNode e = new EntityNode();
        e.setType(NodeType.ENTITY);

        resolver.resolve(e, schemes(), defaults);

        assertEquals("#2E7D32", e.getDisplay().getColors().getOnline());
        assertEquals("#C62828", e.getDisplay().getColors().getOffline());
    }

    @Test
    void groupGetsGroupScheme() {
        GroupNode g = new GroupNode();
        g.setType(NodeType.GROUP);

        resolver.resolve(g, schemes(), defaults);

        assertEquals("#ECEFF1", g.getDisplay().getColors().getOnline());
    }

    @Test
    void explicitSchemeNameOverridesDefault() {
        EntityNode e = new EntityNode();
        e.setType(NodeType.ENTITY);
        Display d = new Display();
        d.setScheme("group");
        e.setDisplay(d);

        resolver.resolve(e, schemes(), defaults);

        assertEquals("#ECEFF1", e.getDisplay().getColors().getOnline());
    }

    @Test
    void perStateColorOverridesScheme() {
        EntityNode e = new EntityNode();
        e.setType(NodeType.ENTITY);
        Display d = new Display();
        DisplayColors override = new DisplayColors();
        override.setOffline("#000000");
        d.setColors(override);
        e.setDisplay(d);

        resolver.resolve(e, schemes(), defaults);

        assertEquals("#000000", e.getDisplay().getColors().getOffline()); // override wins
        assertEquals("#2E7D32", e.getDisplay().getColors().getOnline());  // scheme fills the rest
    }

    @Test
    void noSchemeAndNoColorsLeavesColorsNull() {
        EntityNode e = new EntityNode();
        e.setType(NodeType.ENTITY);

        resolver.resolve(e, schemes(), Map.of()); // no defaults

        assertNull(e.getDisplay().getColors());
    }

    @Test
    void resolvesRecursivelyThroughChildren() {
        GroupNode root = new GroupNode();
        root.setType(NodeType.GROUP);
        EntityNode child = new EntityNode();
        child.setType(NodeType.ENTITY);
        root.getChildren().add(child);

        resolver.resolve(root, schemes(), defaults);

        assertEquals("#ECEFF1", root.getDisplay().getColors().getOnline());
        assertEquals("#2E7D32", child.getDisplay().getColors().getOnline());
    }
}
