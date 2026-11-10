package com.easybpm.webhook;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easybpm.common.Ids;
import com.easybpm.common.JsonCodec;
import com.easybpm.tenant.Tenant;
import com.easybpm.tenant.TenantMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * Webhook 回调（outbox 模式）。
 *
 * <p>业务事务内只写 PENDING 日志行（enqueue），实际 HTTP 投递由 {@link #processPending()}
 * 定时轮询完成，指数退避重试（1m / 5m / 15m / 60m），超过最大次数标记 FAILED。
 * 请求带 X-EasyBpm-Event、X-EasyBpm-Signature（HMAC-SHA256，密钥为租户 webhook_secret）与 X-Trace-Id。
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    /** 状态常量。 */
    public static final String PENDING = "PENDING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    /** 指数退避间隔（分钟），下标 = 已尝试次数 - 1。 */
    private static final long[] BACKOFF_MINUTES = {1, 5, 15, 60};

    private final BpmWebhookLogMapper logMapper;
    private final TenantMapper tenantMapper;
    private final JsonCodec json;

    private final boolean enabled;
    private final int maxAttempts;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    private final HttpClient httpClient;

    public WebhookService(BpmWebhookLogMapper logMapper, TenantMapper tenantMapper, JsonCodec json,
                          @Value("${easybpm.webhook.enabled:true}") boolean enabled,
                          @Value("${easybpm.webhook.max-attempts:5}") int maxAttempts,
                          @Value("${easybpm.webhook.connect-timeout-ms:5000}") int connectTimeoutMs,
                          @Value("${easybpm.webhook.read-timeout-ms:10000}") int readTimeoutMs) {
        this.logMapper = logMapper;
        this.tenantMapper = tenantMapper;
        this.json = json;
        this.enabled = enabled;
        this.maxAttempts = Math.max(maxAttempts, 1);
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    /**
     * 业务事务内调用：落一条 PENDING 投递记录。url 为空或功能关闭时静默跳过。
     */
    public void enqueue(String tenantId, String instanceId, String eventType, String url, Object payload) {
        if (!enabled || url == null || url.isBlank()) {
            return;
        }
        BpmWebhookLog row = new BpmWebhookLog();
        row.setId(Ids.id());
        row.setTenantId(tenantId);
        row.setInstanceId(instanceId);
        row.setEventType(eventType);
        row.setUrl(url);
        row.setPayload(json.write(payload));
        row.setStatus(PENDING);
        row.setAttempts(0);
        row.setNextRetryAt(LocalDateTime.now());
        row.setCreatedAt(LocalDateTime.now());
        logMapper.insert(row);
    }

    /** 按 next_retry_at 轮询投递，单批最多 20 条，避免长时间占用调度线程。 */
    @Scheduled(fixedDelayString = "${easybpm.webhook.poll-interval-ms:30000}")
    public void processPending() {
        if (!enabled) {
            return;
        }
        List<BpmWebhookLog> rows = logMapper.selectList(new LambdaQueryWrapper<BpmWebhookLog>()
                .eq(BpmWebhookLog::getStatus, PENDING)
                .le(BpmWebhookLog::getNextRetryAt, LocalDateTime.now())
                .orderByAsc(BpmWebhookLog::getCreatedAt)
                .last("limit 20"));
        for (BpmWebhookLog row : rows) {
            deliver(row);
        }
    }

    private void deliver(BpmWebhookLog row) {
        int attempts = (row.getAttempts() == null ? 0 : row.getAttempts()) + 1;
        row.setAttempts(attempts);
        try {
            Tenant tenant = tenantMapper.selectById(row.getTenantId());
            String secret = tenant == null ? null : tenant.getWebhookSecret();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(row.getUrl()))
                    .timeout(Duration.ofMillis(readTimeoutMs))
                    .header("Content-Type", "application/json")
                    .header("X-EasyBpm-Event", row.getEventType())
                    .header("X-EasyBpm-Signature", hmacHex(secret, row.getPayload()))
                    .header("X-EasyBpm-Delivery-Id", row.getId())
                    .POST(HttpRequest.BodyPublishers.ofString(row.getPayload(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            row.setResponseCode(response.statusCode());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                row.setStatus(SUCCESS);
                row.setError(null);
            } else {
                row.setStatus(attempts >= maxAttempts ? FAILED : PENDING);
                row.setError("HTTP " + response.statusCode() + ": " + truncate(response.body()));
            }
        } catch (Exception e) {
            row.setStatus(attempts >= maxAttempts ? FAILED : PENDING);
            row.setError(truncate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        if (PENDING.equals(row.getStatus())) {
            long backoff = BACKOFF_MINUTES[Math.min(attempts - 1, BACKOFF_MINUTES.length - 1)];
            row.setNextRetryAt(LocalDateTime.now().plusMinutes(backoff));
        } else {
            row.setNextRetryAt(null);
        }
        logMapper.updateById(row);
        if (!SUCCESS.equals(row.getStatus())) {
            log.warn("webhook 投递未成功 instance={} event={} attempts={} status={} error={}",
                    row.getInstanceId(), row.getEventType(), attempts, row.getStatus(), row.getError());
        }
    }

    private String hmacHex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    (secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("webhook 签名计算失败", e);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
