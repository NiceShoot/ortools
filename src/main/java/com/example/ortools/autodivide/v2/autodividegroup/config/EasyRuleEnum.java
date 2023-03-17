package com.example.ortools.autodivide.v2.autodividegroup.config;

/**
 * 高端排课 规则引擎枚举
 */
public enum EasyRuleEnum {

    DEFAULT_TYPE(0,"默认类型"),
    AUTO_DIVIDE_GROUP(1,"自动分组"),
    ;
    /**
     * 规则类型
     */
    private Integer ruleType;
    /**
     * 描述
     */
    private String desc;

    EasyRuleEnum(Integer ruleType, String desc) {
        this.ruleType = ruleType;
        this.desc = desc;
    }

    public Integer getRuleType() {
        return ruleType;
    }

    public String getDesc() {
        return desc;
    }
}
