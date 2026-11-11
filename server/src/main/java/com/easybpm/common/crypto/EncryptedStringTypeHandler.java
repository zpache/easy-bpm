package com.easybpm.common.crypto;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 敏感字段类型处理器：写库前加密、读库后解密，业务代码无感知。
 *
 * <p>MyBatis 通过无参构造实例化 TypeHandler，拿不到 Spring 容器，因此用
 * {@link #install(DataCipher)} 注入单例（见 {@code SensitiveDataConfig}）。
 * 实体需声明 {@code @TableName(autoResultMap = true)} 才会在查询结果映射时使用本处理器。
 */
@MappedTypes(String.class)
@MappedJdbcTypes({JdbcType.VARCHAR, JdbcType.CHAR, JdbcType.LONGVARCHAR})
public class EncryptedStringTypeHandler extends BaseTypeHandler<String> {

    private static volatile DataCipher cipher;

    public static void install(DataCipher dataCipher) {
        cipher = dataCipher;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        DataCipher current = cipher;
        ps.setString(i, current == null ? parameter : current.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decrypt(cs.getString(columnIndex));
    }

    private String decrypt(String stored) {
        DataCipher current = cipher;
        return stored == null || current == null ? stored : current.decrypt(stored);
    }
}
