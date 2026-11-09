package com.easybpm.engine;

import com.easybpm.common.BizException;
import com.easybpm.common.ErrorCode;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 条件表达式求值。
 *
 * <p>使用 SimpleEvaluationContext：只允许读取变量与做比较/布尔运算，
 * 不能调用静态方法、构造对象或访问容器，避免流程配置成为注入入口。
 */
@Component
public class ConditionEvaluator {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    public boolean evaluate(String expression, Map<String, Object> variables, String nodeName) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        try {
            EvaluationContext context = SimpleEvaluationContext
                    .forPropertyAccessors(new MapAccessor())
                    .build();
            Boolean result = parser.parseExpression(expression).getValue(context, variables, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            throw new BizException(ErrorCode.FLOW_SCHEMA_INVALID,
                    "节点「" + nodeName + "」条件表达式求值失败：" + e.getMessage());
        }
    }
}
