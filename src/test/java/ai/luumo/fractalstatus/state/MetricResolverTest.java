package ai.luumo.fractalstatus.state;

import ai.luumo.fractalstatus.check.CheckModuleRegistry;
import ai.luumo.fractalstatus.check.modules.HttpCheckModule;
import ai.luumo.fractalstatus.check.modules.PingCheckModule;
import ai.luumo.fractalstatus.model.EntityNode;
import ai.luumo.fractalstatus.model.NodeType;
import ai.luumo.fractalstatus.model.config.CheckConfig;
import ai.luumo.fractalstatus.model.config.Display;
import ai.luumo.fractalstatus.model.config.MetricSpec;
import ai.luumo.fractalstatus.model.runtime.CheckResult;
import ai.luumo.fractalstatus.model.runtime.Metric;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MetricResolverTest {

    private final MetricResolver resolver = new MetricResolver(
            new CheckModuleRegistry(List.of(new PingCheckModule(), new HttpCheckModule())));

    private EntityNode entity(String checkName, String module, Map<String, Object> output) {
        EntityNode e = new EntityNode();
        e.setType(NodeType.ENTITY);
        CheckConfig cc = new CheckConfig();
        cc.setName(checkName);
        cc.setModule(module);
        e.getConfig().getChecks().add(cc);
        if (output != null) {
            CheckResult r = new CheckResult(checkName);
            r.setOutput(new LinkedHashMap<>(output));
            e.getRuntime().putResult(r);
        }
        return e;
    }

    @Test
    void pingDefaultMetricIsRttTwoDpMs() {
        EntityNode e = entity("ping", "ping", Map.of("reachable", true, "rttMs", 0.214));
        Metric m = resolver.resolve(e);
        assertEquals("0.21", m.value());
        assertEquals("ms", m.unit());
    }

    @Test
    void httpDefaultMetricIsStatusCodeHttp() {
        EntityNode e = entity("http", "http", Map.of("statusCode", 200, "sslValid", true));
        Metric m = resolver.resolve(e);
        assertEquals("200", m.value());
        assertEquals("HTTP", m.unit());
    }

    @Test
    void explicitFieldOverrideWithDecimalsAndUnit() {
        EntityNode e = entity("ping", "ping", Map.of("reachable", true, "rttMs", 12.56));
        MetricSpec spec = new MetricSpec();
        spec.setField("rttMs");
        spec.setDecimals(1);
        spec.setUnit("millis");
        Display d = new Display();
        d.setMetric(spec);
        e.setDisplay(d);

        Metric m = resolver.resolve(e);
        assertEquals("12.6", m.value());
        assertEquals("millis", m.unit());
    }

    @Test
    void unitOverrideKeepsModuleDefaultValue() {
        EntityNode e = entity("http", "http", Map.of("statusCode", 204));
        MetricSpec spec = new MetricSpec();
        spec.setUnit("code");
        Display d = new Display();
        d.setMetric(spec);
        e.setDisplay(d);

        Metric m = resolver.resolve(e);
        assertEquals("204", m.value());
        assertEquals("code", m.unit());
    }

    @Test
    void noResultYieldsNoMetric() {
        EntityNode e = entity("ping", "ping", null);
        assertNull(resolver.resolve(e));
    }

    @Test
    void unreachablePingYieldsNoMetric() {
        EntityNode e = entity("ping", "ping", Map.of("reachable", false));
        assertNull(resolver.resolve(e));
    }
}
