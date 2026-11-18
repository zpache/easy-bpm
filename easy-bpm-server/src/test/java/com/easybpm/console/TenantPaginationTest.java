package com.easybpm.console;

import com.easybpm.auth.AuthPrincipal;
import com.easybpm.auth.ConsoleUser;
import com.easybpm.auth.ConsoleUserMapper;
import com.easybpm.auth.CurrentUser;
import com.easybpm.common.GlobalExceptionHandler;
import com.easybpm.common.Ids;
import com.easybpm.tenant.Tenant;
import com.easybpm.tenant.TenantContext;
import com.easybpm.tenant.TenantMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TenantPaginationTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Autowired
    private ConsolePlatformController controller;
    @Autowired
    private TenantMapper tenantMapper;
    @Autowired
    private ConsoleUserMapper consoleUserMapper;

    private MockMvc mockMvc;
    private String prefix;
    private String callerTenantId;
    private Tenant tenantA;
    private Tenant tenantB;

    @BeforeEach
    void setUp() {
        prefix = "p" + Ids.id().substring(0, 10);
        callerTenantId = Ids.id();
        TenantContext.set(callerTenantId);
        CurrentUser.set(new AuthPrincipal("platform-admin", callerTenantId, "平台管理员", "PLATFORM_ADMIN", null));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        tenantB = insertTenant("b", "Procurement", prefix + " rateX100abc", CREATED_AT);
        tenantA = insertTenant("a", "Finance", prefix + " rate_100%!", CREATED_AT);
        insertTenant("c", "HR Team", null, CREATED_AT.plusSeconds(1));
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
        TenantContext.clear();
    }

    @Test
    void paginatesWithStableOrdering() throws Exception {
        mockMvc.perform(get("/api/platform/tenants").param("keyword", prefix)
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.list[0].code").value(prefix + "-c"))
                .andExpect(jsonPath("$.data.list[1].code").value(prefix + "-a"));

        mockMvc.perform(get("/api/platform/tenants").param("keyword", prefix)
                        .param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.list[0].code").value(prefix + "-b"));
    }

    @Test
    void defaultsToFirstPageAndTrimsKeyword() throws Exception {
        mockMvc.perform(get("/api/platform/tenants")
                        .param("keyword", "  " + prefix.toUpperCase(Locale.ROOT) + "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.list.length()").value(3));
    }

    @Test
    void searchesCodeNameAndRemarkCaseInsensitively() throws Exception {
        mockMvc.perform(get("/api/platform/tenants").param("keyword", prefix + "-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].code").value(prefix + "-b"));

        mockMvc.perform(get("/api/platform/tenants").param("keyword", prefix + " HR TEAM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].code").value(prefix + "-c"));

        mockMvc.perform(get("/api/platform/tenants").param("keyword", prefix + " RATEX100ABC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].code").value(prefix + "-b"));
    }

    @Test
    void treatsWildcardCharactersAsLiteralText() throws Exception {
        for (String keyword : List.of(prefix + " rate_100%", prefix + " rate_100%!")) {
            mockMvc.perform(get("/api/platform/tenants").param("keyword", keyword))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.list[0].code").value(prefix + "-a"));
        }
    }

    @Test
    void clampsPageAndSize() throws Exception {
        mockMvc.perform(get("/api/platform/tenants").param("keyword", prefix)
                        .param("page", "-1").param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.list.length()").value(1));

        mockMvc.perform(get("/api/platform/tenants").param("keyword", prefix)
                        .param("page", "1").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.list.length()").value(3));
    }

    @Test
    void handlesEmptySearchAndOutOfRangePages() throws Exception {
        mockMvc.perform(get("/api/platform/tenants").param("keyword", prefix)
                        .param("page", "3").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.list.length()").value(0));

        mockMvc.perform(get("/api/platform/tenants").param("keyword", prefix + "-missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.list.length()").value(0));
    }

    @Test
    void requiresPlatformAdmin() throws Exception {
        for (String role : List.of("TENANT_ADMIN", "MEMBER")) {
            CurrentUser.set(new AuthPrincipal("other-user", callerTenantId, "其他用户", role, null));
            mockMvc.perform(get("/api/platform/tenants"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));
        }

        CurrentUser.clear();
        mockMvc.perform(get("/api/platform/tenants"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void keepsAccountCountsTenantScopedAndRestoresContext() throws Exception {
        insertUser(tenantA.getId(), "admin-a");
        insertUser(tenantA.getId(), "member-a");
        insertUser(tenantB.getId(), "admin-b");

        mockMvc.perform(get("/api/platform/tenants").param("keyword", prefix)
                        .param("page", "2").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].code").value(prefix + "-a"))
                .andExpect(jsonPath("$.data.list[0].consoleUserCount").value(2));
        assertEquals(callerTenantId, TenantContext.require());

        mockMvc.perform(get("/api/platform/tenants").param("keyword", prefix)
                        .param("page", "3").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].code").value(prefix + "-b"))
                .andExpect(jsonPath("$.data.list[0].consoleUserCount").value(1));
        assertEquals(callerTenantId, TenantContext.require());
    }

    private Tenant insertTenant(String suffix, String name, String remark, LocalDateTime createdAt) {
        Tenant tenant = new Tenant();
        tenant.setId(Ids.id());
        tenant.setCode(prefix + "-" + suffix);
        tenant.setName(prefix + " " + name);
        tenant.setRemark(remark);
        tenant.setStatus("ACTIVE");
        tenant.setCreatedAt(createdAt);
        tenant.setUpdatedAt(createdAt);
        tenantMapper.insert(tenant);
        return tenant;
    }

    private void insertUser(String tenantId, String username) {
        ConsoleUser user = new ConsoleUser();
        user.setId(Ids.id());
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setPasswordHash("test-only-unused-hash");
        user.setDisplayName(username);
        user.setRole("MEMBER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(CREATED_AT);
        consoleUserMapper.insert(user);
    }
}
