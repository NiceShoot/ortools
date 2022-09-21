package com.example.ortools.自动分组.v2.autodividegroup.utils;

import cn.xdf.api.resource.management.funmoudle.autodividegroup.annotation.EasyRulesAnnotation;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.config.EasyRuleEnum;
import com.alibaba.fastjson.JSON;
import org.jeasy.rules.api.Rules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AutoDivideGroupRuleUtil {

    private static final Logger logger = LoggerFactory.getLogger(AutoDivideGroupRuleUtil.class);


    /**
     * 注册规则
     * @param easyRuleEnum rule的类型
     */
    public static Rules registerRules(EasyRuleEnum easyRuleEnum, ApplicationContext applicationContext){
        Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(EasyRulesAnnotation.class);
        List<Object> ruleList = new ArrayList<>();
        for (String key : beansWithAnnotation.keySet()) {
            Object o = beansWithAnnotation.get(key);
            if (o != null) {
                EasyRulesAnnotation annotation = o.getClass().getAnnotation(EasyRulesAnnotation.class);
                if (annotation != null && annotation.ruleType().getRuleType().equals(easyRuleEnum.getRuleType())) {
                    ruleList.add(o);
                }
            }
        }
        logger.info("载入的自动分组的规则:{}", JSON.toJSONString(ruleList));
        // 注册规则
        Rules rules = new Rules();
        ruleList.forEach(rules::register);
        return rules;
    }
}
