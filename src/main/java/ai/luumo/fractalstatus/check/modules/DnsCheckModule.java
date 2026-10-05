package ai.luumo.fractalstatus.check.modules;

import ai.luumo.fractalstatus.check.CheckContext;
import ai.luumo.fractalstatus.check.CheckModule;
import ai.luumo.fractalstatus.check.CheckOutcome;
import ai.luumo.fractalstatus.model.runtime.Metric;
import org.springframework.stereotype.Component;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

/**
 * Resolves a DNS record of a given type for a domain against one or more
 * resolvers, and passes only if every resolver returns a non-empty answer.
 *
 * <p>This does not compare the answer against any expected value - it only
 * verifies resolution succeeds (not SERVFAIL/NXDOMAIN/empty/timeout).
 *
 * <p>Uses the JDK's built-in JNDI DNS provider (no external dependency).
 *
 * <p>Config:
 * <ul>
 *   <li>{@code domain} (required, supports {@code ${this.*}}) - the name to look up.</li>
 *   <li>{@code type} (optional, default {@code A}) - record type: A, AAAA, NS, MX,
 *       CNAME, TXT, SOA, SRV, ...</li>
 *   <li>{@code servers} (optional, default {@code [8.8.8.8, 1.1.1.1]}) - resolver
 *       IP(s)/host(s) to query.</li>
 *   <li>{@code timeout} (optional, default 5) - per-query timeout in seconds.</li>
 * </ul>
 *
 * <p>Output fields: {@code resolved} (boolean), {@code type} (string),
 * {@code serversOk} (int), {@code serversTotal} (int), {@code recordCount} (int).
 */
@Component
public class DnsCheckModule implements CheckModule {

    private static final String DEFAULT_TYPE = "A";
    private static final int DEFAULT_TIMEOUT_SECONDS = 5;
    private static final List<String> DEFAULT_SERVERS = List.of("8.8.8.8", "1.1.1.1");

    @Override
    public String id() {
        return "dns";
    }

    @Override
    public CheckOutcome check(CheckContext ctx) {
        String domain = ctx.getString("domain", ctx.getString("host", "")).trim();
        if (domain.isEmpty()) {
            return CheckOutcome.builder(false)
                    .put("resolved", false)
                    .message("no domain configured")
                    .build();
        }
        String type = ctx.getString("type", DEFAULT_TYPE).trim().toUpperCase(Locale.ROOT);
        int timeoutMs = Math.max(1, ctx.getInt("timeout", DEFAULT_TIMEOUT_SECONDS)) * 1000;
        List<String> servers = servers(ctx);

        int ok = 0;
        int recordCount = 0;
        List<String> failures = new ArrayList<>();
        for (String server : servers) {
            try {
                int count = lookup(server, domain, type, timeoutMs);
                if (count > 0) {
                    ok++;
                    recordCount = Math.max(recordCount, count);
                } else {
                    failures.add(server + ": no " + type + " records");
                }
            } catch (NamingException e) {
                failures.add(server + ": " + rootMessage(e));
            }
        }

        boolean online = ok == servers.size() && !servers.isEmpty();
        String message = online
                ? type + " ok via " + String.join(", ", servers) + " (" + recordCount + " records)"
                : type + " lookup failed - " + String.join("; ", failures);

        ctx.log().debug("dns " + type + " " + domain + " -> " + ok + "/" + servers.size()
                + " resolvers ok");

        return CheckOutcome.builder(online)
                .put("resolved", online)
                .put("type", type)
                .put("serversOk", ok)
                .put("serversTotal", servers.size())
                .put("recordCount", recordCount)
                .message(message)
                .build();
    }

    @Override
    public Metric primaryMetric(java.util.Map<String, Object> output) {
        Object count = output.get("recordCount");
        Object type = output.get("type");
        if (count instanceof Number n && type != null) {
            return new Metric(Integer.toString(n.intValue()), String.valueOf(type));
        }
        return null;
    }

    /** Returns the number of records of {@code type} for {@code domain} from {@code server}. */
    private int lookup(String server, String domain, String type, int timeoutMs)
            throws NamingException {
        Hashtable<String, Object> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", "dns://" + server);
        env.put("com.sun.jndi.dns.timeout.initial", Integer.toString(timeoutMs));
        env.put("com.sun.jndi.dns.timeout.retries", "2");

        DirContext ctx = new InitialDirContext(env);
        try {
            Attributes attrs = ctx.getAttributes(domain, new String[]{type});
            Attribute attr = attrs.get(type);
            return attr == null ? 0 : attr.size();
        } finally {
            try {
                ctx.close();
            } catch (NamingException ignored) {
                // nothing to do
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> servers(CheckContext ctx) {
        Object raw = ctx.config().get("servers");
        if (raw instanceof List<?> list && !list.isEmpty()) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o != null && !o.toString().isBlank()) {
                    out.add(o.toString().trim());
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        if (raw instanceof String s && !s.isBlank()) {
            List<String> out = new ArrayList<>();
            for (String part : s.split(",")) {
                if (!part.isBlank()) {
                    out.add(part.trim());
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        return DEFAULT_SERVERS;
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        String msg = c.getMessage();
        return msg == null ? c.getClass().getSimpleName() : msg;
    }
}
