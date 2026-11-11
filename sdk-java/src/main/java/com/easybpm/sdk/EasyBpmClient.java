package com.easybpm.sdk;

import com.easybpm.sdk.model.ActionResult;
import com.easybpm.sdk.model.CcView;
import com.easybpm.sdk.model.ChangeFeed;
import com.easybpm.sdk.model.FlowSummary;
import com.easybpm.sdk.model.HandleRequest;
import com.easybpm.sdk.model.InstanceDetail;
import com.easybpm.sdk.model.InstanceView;
import com.easybpm.sdk.model.OrgSyncRequest;
import com.easybpm.sdk.model.OrgSyncResult;
import com.easybpm.sdk.model.PageResult;
import com.easybpm.sdk.model.ResubmitRequest;
import com.easybpm.sdk.model.StartRequest;
import com.easybpm.sdk.model.StartResult;
import com.easybpm.sdk.model.TaskView;
import com.easybpm.sdk.model.TransferRequest;
import com.easybpm.sdk.model.WithdrawRequest;
import com.easybpm.sdk.model.WorkbenchCounts;
import com.easybpm.sdk.model.WorkbenchSnapshot;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * easy-bpm OpenAPI 客户端（线程安全，建议整个应用复用一个实例）。
 *
 * <pre>{@code
 * EasyBpmClient client = EasyBpmClient.builder()
 *         .baseUrl("http://localhost:8090")
 *         .appKey("ak_demo")
 *         .appSecret("sk_demo_2026")
 *         .build();
 *
 * StartResult started = client.start(StartRequest.builder()
 *         .flowCode("leave")
 *         .businessKey("LEAVE-2026-0001")
 *         .initiator("u1001", "张三")
 *         .formData(Map.of("days", 3))
 *         .build());
 * }</pre>
 *
 * <p>鉴权默认使用签名模式：{@code X-App-Key} + {@code X-Timestamp} +
 * HMAC-SHA256(method + "\n" + 路径与查询串 + "\n" + 时间戳 + "\n" + body)，密钥为 AppSecret。
 * 业务错误统一抛 {@link EasyBpmException}（含 code / traceId / httpStatus）。
 */
public class EasyBpmClient {

    private static final String USER_AGENT = "easy-bpm-sdk-java/1.0.0";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final String baseUrl;
    private final String appKey;
    private final String appSecret;
    private final Duration requestTimeout;
    private final int maxRetries;
    private final HttpClient http;

    private EasyBpmClient(Builder builder) {
        this.baseUrl = stripTrailingSlash(builder.baseUrl);
        this.appKey = builder.appKey;
        this.appSecret = builder.appSecret;
        this.requestTimeout = builder.requestTimeout;
        this.maxRetries = Math.max(builder.maxRetries, 0);
        this.http = HttpClient.newBuilder()
                .connectTimeout(builder.connectTimeout)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EasyBpmClient create(String baseUrl, String appKey, String appSecret) {
        return builder().baseUrl(baseUrl).appKey(appKey).appSecret(appSecret).build();
    }

    // ---------------- 流程 ----------------

    /** 当前租户已发布的流程（含表单 schema）。 */
    public List<FlowSummary> flows() {
        return read("GET", "/open/v1/flows", Map.of(), null, new TypeReference<>() {
        });
    }

    // ---------------- 发起 / 撤回 / 重提 ----------------

    /** 创建并提交审批。建议带 idempotencyKey，超时重试不会重复建单。 */
    public StartResult start(StartRequest request) {
        return read("POST", "/open/v1/instances", Map.of(), request, new TypeReference<>() {
        });
    }

    /** TO_START 驳回后，发起人修改表单重新提交。 */
    public StartResult resubmit(String instanceId, ResubmitRequest request) {
        return read("POST", path("/open/v1/instances/", instanceId, "/resubmit"), Map.of(), request,
                new TypeReference<>() {
                });
    }

    /** 发起人撤回运行中的实例（状态变为 CANCELED）。 */
    public ActionResult withdraw(String instanceId, WithdrawRequest request) {
        return read("POST", path("/open/v1/instances/", instanceId, "/withdraw"), Map.of(), request,
                new TypeReference<>() {
                });
    }

    // ---------------- 查询 ----------------

    /** 实例详情 + 时间线 + 当前待办。 */
    public InstanceDetail detail(String instanceId) {
        return read("GET", path("/open/v1/instances/", instanceId), Map.of(), null,
                new TypeReference<>() {
                });
    }

    /** 按接入方业务单号反查实例（跨流程重复时取最近一条）。 */
    public InstanceView byBusinessKey(String businessKey) {
        return byBusinessKey(businessKey, null);
    }

    public InstanceView byBusinessKey(String businessKey, String flowCode) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("businessKey", businessKey);
        putIfPresent(query, "flowCode", flowCode);
        return read("GET", "/open/v1/instances/by-business-key", query, null, new TypeReference<>() {
        });
    }

    /** 我提交的审批。status 可选 RUNNING / APPROVED / REJECTED / CANCELED。 */
    public PageResult<InstanceView> submitted(String userId, int page, int size,
                                              String flowCode, String status, String keyword) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("userId", userId);
        query.put("page", page);
        query.put("size", size);
        putIfPresent(query, "flowCode", flowCode);
        putIfPresent(query, "status", status);
        putIfPresent(query, "keyword", keyword);
        return read("GET", "/open/v1/instances/submitted", query, null, new TypeReference<>() {
        });
    }

    /** 待办任务。 */
    public PageResult<TaskView> todo(String userId, int page, int size, String flowCode, String keyword) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("userId", userId);
        query.put("page", page);
        query.put("size", size);
        putIfPresent(query, "flowCode", flowCode);
        putIfPresent(query, "keyword", keyword);
        return read("GET", "/open/v1/tasks/todo", query, null, new TypeReference<>() {
        });
    }

    /** 已办任务。 */
    public PageResult<TaskView> done(String userId, int page, int size, String flowCode, String keyword) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("userId", userId);
        query.put("page", page);
        query.put("size", size);
        putIfPresent(query, "flowCode", flowCode);
        putIfPresent(query, "keyword", keyword);
        return read("GET", "/open/v1/tasks/done", query, null, new TypeReference<>() {
        });
    }

    /** 待办 / 已办 / 已提交 / 未读抄送数量。 */
    public WorkbenchCounts counts(String userId) {
        return read("GET", "/open/v1/tasks/count", Map.of("userId", userId), null, new TypeReference<>() {
        });
    }

    /** 工作台快照：角标 + 待办首页 + 未读抄送 + 增量游标。 */
    public WorkbenchSnapshot workbench(String userId, int todoSize, int ccSize) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("userId", userId);
        query.put("todoSize", todoSize);
        query.put("ccSize", ccSize);
        return read("GET", "/open/v1/workbench", query, null, new TypeReference<>() {
        });
    }

    /**
     * 增量变更流。cursor 传 {@link WorkbenchSnapshot#cursor()} 或上一轮返回的 cursor；
     * 首次接入可传 null，只拿初始游标（不回放历史）。
     */
    public ChangeFeed changes(String userId, Long cursor, Integer limit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("userId", userId);
        putIfPresent(query, "cursor", cursor);
        putIfPresent(query, "limit", limit);
        return read("GET", "/open/v1/changes", query, null, new TypeReference<>() {
        });
    }

    /** 抄送我的。unreadOnly=true 只看未读。 */
    public PageResult<CcView> cc(String userId, int page, int size, boolean unreadOnly, String keyword) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("userId", userId);
        query.put("page", page);
        query.put("size", size);
        query.put("unreadOnly", unreadOnly);
        putIfPresent(query, "keyword", keyword);
        return read("GET", "/open/v1/cc", query, null, new TypeReference<>() {
        });
    }

    /** 标记抄送已读（仅本人，重复调用幂等）。 */
    public void markCcRead(String ccId, String userId) {
        read("POST", path("/open/v1/cc/", ccId, "/read"), Map.of("userId", userId), null, new TypeReference<Void>() {
        });
    }

    // ---------------- 审批动作 ----------------

    /** 同意。formData 非空时会合并进实例表单。 */
    public ActionResult approve(String taskId, String userId, String comment, Map<String, Object> formData) {
        return read("POST", path("/open/v1/tasks/", taskId, "/approve"), Map.of(),
                new HandleRequest(userId, comment, formData), new TypeReference<>() {
                });
    }

    /** 驳回，按节点策略 TO_END 结束或 TO_START 退回发起人。 */
    public ActionResult reject(String taskId, String userId, String comment) {
        return read("POST", path("/open/v1/tasks/", taskId, "/reject"), Map.of(),
                HandleRequest.of(userId, comment), new TypeReference<>() {
                });
    }

    /** 转交待办给同租户在职人员。 */
    public ActionResult transfer(String taskId, TransferRequest request) {
        return read("POST", path("/open/v1/tasks/", taskId, "/transfer"), Map.of(), request,
                new TypeReference<>() {
                });
    }

    // ---------------- 组织 ----------------

    /** 组织同步：人员 / 部门 / 角色按编码幂等 upsert。 */
    public OrgSyncResult orgSync(OrgSyncRequest request) {
        return read("POST", "/open/v1/org/sync", Map.of(), request, new TypeReference<>() {
        });
    }

    // ---------------- 轮询 ----------------

    /**
     * 创建增量轮询器（未启动）。适合「不接 Webhook、由接入方前端/后台拉待办」的场景。
     *
     * <pre>{@code
     * ChangePoller poller = client.poller("u1002", snapshot.cursor())
     *         .interval(Duration.ofSeconds(15))
     *         .onEvent(event -> System.out.println(event.type() + " " + event.instanceTitle()))
     *         .start();
     * }</pre>
     */
    public ChangePoller.Builder poller(String userId, Long cursor) {
        return ChangePoller.builder(this, userId, cursor);
    }

    // ---------------- 内部实现 ----------------

    private <T> T read(String method, String path, Map<String, Object> query, Object body,
                       TypeReference<T> type) {
        String target = path + queryString(query);
        JsonNode data = execute(method, target, body);
        if (data == null || data.isNull() || data.isMissingNode()) {
            return null;
        }
        try {
            return MAPPER.convertValue(data, type);
        } catch (IllegalArgumentException e) {
            throw new EasyBpmException(-1, "响应解析失败：" + e.getMessage(), null, 0);
        }
    }

    private JsonNode execute(String method, String target, Object body) {
        String bodyText = serialize(body);
        boolean retryable = "GET".equals(method);
        int attempts = retryable ? maxRetries + 1 : 1;
        EasyBpmException lastError = null;
        IOException lastIo = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (attempt > 1) {
                sleep(200L << (attempt - 2));
            }
            String timestamp = Long.toString(System.currentTimeMillis());
            String signature = sign(method, target, timestamp, bodyText);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + target))
                    .timeout(requestTimeout)
                    .header("X-App-Key", appKey)
                    .header("X-Timestamp", timestamp)
                    .header("X-Signature", signature)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT);
            if ("GET".equals(method)) {
                builder.GET();
            } else {
                builder.header("Content-Type", "application/json; charset=utf-8")
                        .method(method, bodyText.isEmpty()
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(bodyText, StandardCharsets.UTF_8));
            }

            try {
                HttpResponse<String> response = http.send(builder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status == 429 || status >= 500) {
                    lastError = toError(response, status);
                    if (status == 429 && attempt < attempts) {
                        sleep(retryAfterMillis(response));
                    }
                    continue;
                }
                return unpack(response, status);
            } catch (IOException e) {
                lastIo = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EasyBpmException(-1, "请求被中断", null, 0);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new EasyBpmException(-1, "网络异常：" + (lastIo == null ? "未知原因" : lastIo.getMessage()), null, 0);
    }

    private JsonNode unpack(HttpResponse<String> response, int status) {
        String body = response.body();
        if (status < 200 || status >= 300) {
            throw toError(response, status);
        }
        JsonNode root = parse(body);
        if (root == null) {
            throw new EasyBpmException(-1, "响应不是合法 JSON（HTTP " + status + "）", null, status);
        }
        int code = root.path("code").asInt(0);
        if (code != 0) {
            throw new EasyBpmException(code, root.path("message").asText(null),
                    root.path("traceId").asText(null), status);
        }
        return root.path("data");
    }

    private EasyBpmException toError(HttpResponse<String> response, int status) {
        JsonNode root = parse(response.body());
        if (root != null && root.hasNonNull("code")) {
            return new EasyBpmException(root.path("code").asInt(-1), root.path("message").asText(null),
                    root.path("traceId").asText(null), status);
        }
        String body = response.body() == null ? "" : response.body();
        if (body.length() > 200) {
            body = body.substring(0, 200);
        }
        return new EasyBpmException(-1, "HTTP " + status + "：" + body, null, status);
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String serialize(Object body) {
        if (body == null) {
            return "";
        }
        try {
            return MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new EasyBpmException(-1, "请求序列化失败：" + e.getMessage(), null, 0);
        }
    }

    private String sign(String method, String target, String timestamp, String body) {
        String payload = method + "\n" + target + "\n" + timestamp + "\n" + body;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("签名计算失败", e);
        }
    }

    private String queryString(Map<String, Object> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("?");
        boolean first = true;
        for (Map.Entry<String, Object> entry : query.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (!first) {
                sb.append('&');
            }
            sb.append(encode(entry.getKey())).append('=').append(encode(String.valueOf(value)));
            first = false;
        }
        return sb.length() == 1 ? "" : sb.toString();
    }

    private String path(String prefix, String segment, String suffix) {
        return prefix + encode(segment) + suffix;
    }

    private String path(String prefix, String segment) {
        return prefix + encode(segment);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void putIfPresent(Map<String, Object> query, String key, Object value) {
        if (value != null && !(value instanceof String s && s.isBlank())) {
            query.put(key, value);
        }
    }

    private long retryAfterMillis(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After")
                .map(v -> safeSeconds(v) * 1000L)
                .orElse(1000L);
    }

    private long safeSeconds(String value) {
        try {
            return Math.max(1, Math.min(Long.parseLong(value.trim()), 60));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(Math.min(millis, 5000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("baseUrl 不能为空");
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /** 客户端构建器。 */
    public static final class Builder {
        private String baseUrl;
        private String appKey;
        private String appSecret;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(10);
        private int maxRetries = 2;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder appKey(String appKey) {
            this.appKey = appKey;
            return this;
        }

        public Builder appSecret(String appSecret) {
            this.appSecret = appSecret;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /** GET 请求的重试次数（默认 2）。POST 不自动重试，避免重复处理任务。 */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public EasyBpmClient build() {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("baseUrl 不能为空");
            }
            if (appKey == null || appKey.isBlank()) {
                throw new IllegalArgumentException("appKey 不能为空");
            }
            if (appSecret == null || appSecret.isBlank()) {
                throw new IllegalArgumentException("appSecret 不能为空");
            }
            return new EasyBpmClient(this);
        }
    }
}
