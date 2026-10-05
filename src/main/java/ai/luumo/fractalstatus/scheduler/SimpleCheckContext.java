package ai.luumo.fractalstatus.scheduler;

import ai.luumo.fractalstatus.check.CheckContext;
import ai.luumo.fractalstatus.log.NodeLogger;

import java.util.Map;

/**
 * Immutable {@link CheckContext} built once per check instance.
 */
record SimpleCheckContext(Map<String, Object> config,
                          String nodePath,
                          String checkName,
                          NodeLogger log) implements CheckContext {
}
