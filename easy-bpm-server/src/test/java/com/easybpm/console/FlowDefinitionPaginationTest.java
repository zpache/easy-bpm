package com.easybpm.console;

import com.easybpm.common.GlobalExceptionHandler;
import com.easybpm.common.Ids;
import com.easybpm.flow.FlowDefinition;
import com.easybpm.flow.FlowDefinitionMapper;
import com.easybpm.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class FlowDefinitionPaginationTest {

    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Autowired
    private ConsoleFlowController controller;
    @Autowired
    private FlowDefinitionMapper definitionMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContext.set(Ids.id());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        insertFlow("flow_b", UPDATED_AT);
        insertFlow("flow_a", UPDATED_AT);
        insertFlow("flow_c", UPDATED_AT.plusSeconds(1));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void paginatesWithStableOrdering() throws Exception {
        mockMvc.perform(get("/api/flows").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.list.length()").value(2))
                .andExpect(jsonPath("$.data.list[0].code").value("flow_c"))
                .andExpect(jsonPath("$.data.list[1].code").value("flow_a"));

        mockMvc.perform(get("/api/flows").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.list[0].code").value("flow_b"));
    }

    @Test
    void preservesFullListForFlowSelectors() throws Exception {
        mockMvc.perform(get("/api/flows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].code").value("flow_c"));
    }

    @Test
    void defaultsToTwentyRecordsPerPage() throws Exception {
        mockMvc.perform(get("/api/flows").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.list.length()").value(3));
    }

    @Test
    void clampsPageAndSize() throws Exception {
        mockMvc.perform(get("/api/flows").param("page", "0").param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.list.length()").value(1));

        mockMvc.perform(get("/api/flows").param("page", "-1").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(100))
                .andExpect(jsonPath("$.data.list.length()").value(3));
    }

    @Test
    void returnsEmptyListBeyondLastPage() throws Exception {
        mockMvc.perform(get("/api/flows").param("page", "3").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(3))
                .andExpect(jsonPath("$.data.list.length()").value(0));
    }

    @Test
    void isolatesRecordsAndTotalsByTenant() throws Exception {
        TenantContext.set(Ids.id());
        insertFlow("other_tenant_flow", UPDATED_AT);

        mockMvc.perform(get("/api/flows").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.list[0].code").value("other_tenant_flow"));
    }

    private void insertFlow(String code, LocalDateTime updatedAt) {
        FlowDefinition definition = new FlowDefinition();
        definition.setId(Ids.id());
        definition.setCode(code);
        definition.setName(code);
        definition.setFlowSchema("{}");
        definition.setVersion(0);
        definition.setStatus("DRAFT");
        definition.setCreatedAt(UPDATED_AT);
        definition.setUpdatedAt(updatedAt);
        definitionMapper.insert(definition);
    }
}
