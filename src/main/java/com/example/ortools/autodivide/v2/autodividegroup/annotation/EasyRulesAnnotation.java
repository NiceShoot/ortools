package com.example.ortools.autodivide.v2.autodividegroup.annotation;


import cn.xdf.api.resource.management.funmoudle.autodividegroup.config.EasyRuleEnum;

import java.lang.annotation.*;

/**
 *
 * 区分规则
 *
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface EasyRulesAnnotation {

    String name() default "rule";

    String description() default "description";

    EasyRuleEnum ruleType() default EasyRuleEnum.DEFAULT_TYPE;

}

