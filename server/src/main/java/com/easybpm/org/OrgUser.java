package com.easybpm.org;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bpm_org_user")
public class OrgUser {

    @TableId(type = IdType.INPUT)
    private String id;
    private String userCode;
    private String name;
    private String deptCode;
    private String deptName;
    private String leaderCode;
    private String title;
    private String mobile;
    private String email;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
