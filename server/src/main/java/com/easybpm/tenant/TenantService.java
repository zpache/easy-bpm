package com.easybpm.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TenantService {

    private final TenantMapper tenantMapper;
    private final TenantCredentialMapper credentialMapper;

    public TenantService(TenantMapper tenantMapper, TenantCredentialMapper credentialMapper) {
        this.tenantMapper = tenantMapper;
        this.credentialMapper = credentialMapper;
    }

    public Tenant requireByCode(String code) {
        Tenant tenant = tenantMapper.selectOne(new LambdaQueryWrapper<Tenant>().eq(Tenant::getCode, code));
        if (tenant == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "租户不存在：" + code);
        }
        requireActive(tenant);
        return tenant;
    }

    public Tenant requireById(String id) {
        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "租户不存在");
        }
        requireActive(tenant);
        return tenant;
    }

    public void requireActive(Tenant tenant) {
        if (!"ACTIVE".equals(tenant.getStatus())) {
            throw new BizException(ErrorCode.TENANT_DISABLED, "租户已停用：" + tenant.getCode());
        }
    }

    public TenantCredential findCredential(String appKey) {
        return credentialMapper.selectOne(new LambdaQueryWrapper<TenantCredential>()
                .eq(TenantCredential::getAppKey, appKey));
    }

    public void touchCredential(String credentialId) {
        TenantCredential update = new TenantCredential();
        update.setId(credentialId);
        update.setLastUsedAt(LocalDateTime.now());
        credentialMapper.updateById(update);
    }
}
