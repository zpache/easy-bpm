package cn.easykits.bpm.org;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 角色定义：关联处（授权关系、审批节点、OpenAPI 同步）仍以 roleCode 为键，roleName 仅作展示。 */
@Data
@TableName("bpm_org_role")
public class OrgRole {

    @TableId(type = IdType.INPUT)
    private String id;
    private String roleCode;
    private String roleName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
