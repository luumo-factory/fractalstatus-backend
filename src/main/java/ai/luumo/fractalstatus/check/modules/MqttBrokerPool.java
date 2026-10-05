package ai.luumo.fractalstatus.check.modules;

import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages a pool of MQTT broker connections - one {@link MqttClient} per distinct
 * broker URL and credential pair. All check instances that share a broker reuse
 * the same connection, so N entities monitoring the same broker produce exactly
 * one TCP connection.
 *
 * <p>Subscriptions are tracked per broker so they can be transparently
 * re-established if the connection drops and Paho's automatic reconnect fires.
 *
 * <p>Thread safety: {@link #ensureSubscribed} is synchronized per broker key.
 * The fast-path check against the {@code subscribed} set is lock-free so
 * established check instances pay no contention cost after the first call.
 */
@Component
public class MqttBrokerPool {

    private static final Logger LOG = LoggerFactory.getLogger(MqttBrokerPool.class);

    /**
     * A subscription registered against a broker client.
     * Stored so it can be re-applied after a reconnect.
     */
    private record Subscription(String topic, int qos, IMqttMessageListener listener) {}

    /** One connected client per broker+credentials key. */
    private final ConcurrentHashMap<String, MqttClient> clients = new ConcurrentHashMap<>();

    /** Per-broker list of subscriptions for resubscription on reconnect. */
    private final ConcurrentHashMap<String, List<Subscription>> brokerSubs =
            new ConcurrentHashMap<>();

    /**
     * Set of subscription keys already registered.
     * Key format: {@code brokerKey + '\0' + topic + '\0' + entityPath + '\0' + checkName}.
     * Acts as the idempotency guard so calling this method on every scheduler tick
     * is free once the subscription is established.
     */
    private final Set<String> registered = ConcurrentHashMap.newKeySet();

    /**
     * Ensures the given check instance is subscribed to {@code topic} on
     * {@code brokerUrl}. Safe to call on every scheduler tick - the first call
     * connects (if needed) and subscribes; all subsequent calls return immediately.
     *
     * <p>If the connection or subscribe fails, the key is removed from the
     * registered set so the next scheduler tick retries automatically.
     *
     * @param brokerUrl  e.g. {@code tcp://192.168.88.1:1883} or {@code ssl://host:8883}
     * @param topic      MQTT topic filter (wildcards + and # supported)
     * @param qos        0, 1, or 2
     * @param username   null or blank for unauthenticated
     * @param password   null or blank for unauthenticated
     * @param entityPath dotted node path (e.g. {@code root.homeAutomation.tempSensor})
     * @param checkName  check instance name (e.g. {@code heartbeat})
     * @param store      the store to write incoming messages into
     */
    public void ensureSubscribed(String brokerUrl, String topic, int qos,
                                  String username, String password,
                                  String entityPath, String checkName,
                                  MqttStore store) {
        String brokerKey = brokerKey(brokerUrl, username);
        String subKey = brokerKey + '\0' + topic + '\0' + entityPath + '\0' + checkName;

        // Fast path - already subscribed.
        if (!registered.add(subKey)) {
            return;
        }

        // Slow path - synchronize per broker so only one thread creates the client.
        Object brokerLock = getLockFor(brokerKey);
        synchronized (brokerLock) {
            try {
                MqttClient client = getOrCreateClient(brokerKey, brokerUrl, username, password);

                IMqttMessageListener listener = (receivedTopic, message) -> {
                    String payload = message.getPayload() != null
                            ? new String(message.getPayload(), StandardCharsets.UTF_8)
                            : "";
                    store.recordMessage(entityPath, checkName, payload);
                    LOG.debug("MQTT {}/{}: message on '{}'", entityPath, checkName, receivedTopic);
                };

                client.subscribe(topic, qos, listener);

                // Track for resubscription on reconnect.
                brokerSubs.computeIfAbsent(brokerKey, k -> new CopyOnWriteArrayList<>())
                          .add(new Subscription(topic, qos, listener));

                LOG.info("MQTT {}/{}: subscribed to '{}' on {}", entityPath, checkName, topic, brokerUrl);

            } catch (Exception e) {
                // Allow retry on the next scheduler tick.
                registered.remove(subKey);
                LOG.warn("MQTT {}/{}: subscribe failed for '{}' on {}: {}",
                        entityPath, checkName, topic, brokerUrl, e.getMessage());
            }
        }
    }

    // --- internal ---

    private final ConcurrentHashMap<String, Object> brokerLocks = new ConcurrentHashMap<>();

    private Object getLockFor(String brokerKey) {
        return brokerLocks.computeIfAbsent(brokerKey, k -> new Object());
    }

    private MqttClient getOrCreateClient(String brokerKey, String brokerUrl,
                                          String username, String password) throws MqttException {
        MqttClient existing = clients.get(brokerKey);
        if (existing != null) {
            // Client object exists - Paho's auto-reconnect manages the connection.
            return existing;
        }
        MqttClient client = buildClient(brokerKey, brokerUrl, username, password);
        clients.put(brokerKey, client);
        return client;
    }

    private MqttClient buildClient(String brokerKey, String brokerUrl,
                                    String username, String password) throws MqttException {
        String clientId = "fractalstatus-" + UUID.randomUUID().toString().substring(0, 8);
        MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

        // Callback handles logging and resubscription after reconnect.
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                if (!reconnect) {
                    return;
                }
                LOG.info("MQTT: reconnected to {} - resubscribing {} topic(s)", serverURI,
                        brokerSubs.getOrDefault(brokerKey, List.of()).size());
                for (Subscription sub : brokerSubs.getOrDefault(brokerKey, List.of())) {
                    try {
                        client.subscribe(sub.topic(), sub.qos(), sub.listener());
                        LOG.debug("MQTT: resubscribed to '{}'", sub.topic());
                    } catch (MqttException e) {
                        LOG.warn("MQTT: resubscribe to '{}' failed: {}", sub.topic(), e.getMessage());
                    }
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                LOG.warn("MQTT: connection lost to {}: {}", brokerUrl,
                        cause != null ? cause.getMessage() : "unknown");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                // Individual subscription listeners handle messages; this is unused.
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Publish not used.
            }
        });

        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setAutomaticReconnect(true);
        opts.setConnectionTimeout(10);
        opts.setKeepAliveInterval(30);
        opts.setMaxReconnectDelay(30_000); // cap backoff at 30s
        if (username != null && !username.isBlank()) {
            opts.setUserName(username);
        }
        if (password != null && !password.isBlank()) {
            opts.setPassword(password.toCharArray());
        }

        client.connect(opts);
        LOG.info("MQTT: connected to broker {}", brokerUrl);
        return client;
    }

    private static String brokerKey(String brokerUrl, String username) {
        return brokerUrl + '\0' + (username != null ? username : "");
    }

    @PreDestroy
    public void shutdown() {
        LOG.info("MQTT: shutting down {} broker connection(s)", clients.size());
        for (MqttClient client : clients.values()) {
            try { client.disconnect(2000); } catch (Exception ignored) {}
            try { client.close(); } catch (Exception ignored) {}
        }
        clients.clear();
        brokerSubs.clear();
        registered.clear();
        brokerLocks.clear();
    }
}
