package com.example.ortools.自动分组.v2.autodividegroup.config;

import cn.xdf.api.resource.management.funmoudle.autodividegroup.listener.AutoDivideGroupRuleEngineListener;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.listener.AutoDivideGroupRuleListener;
import org.jeasy.rules.api.RulesEngine;
import org.jeasy.rules.api.RulesEngineParameters;
import org.jeasy.rules.core.DefaultRulesEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import javax.annotation.Resource;

@Configuration
public class AutoDivideGroupConfig {

    private static final Integer MAX_PRIORITY = Integer.MAX_VALUE;

    // -----------------------------------------------------------------------------------------------------------------------------------

    @Resource
    private AutoDivideGroupRuleEngineListener engineListener;

    @Resource
    private AutoDivideGroupRuleListener ruleListener;

    @Bean(name = "autoDivideGroupRuleEngine")
    @Lazy
    public RulesEngine autoDivideGroupRuleEngine(){
        RulesEngineParameters rulesEngineParameters = new RulesEngineParameters();
        rulesEngineParameters.setSkipOnFirstAppliedRule(false);
        rulesEngineParameters.setPriorityThreshold(MAX_PRIORITY);
        DefaultRulesEngine defaultRulesEngine = new DefaultRulesEngine(rulesEngineParameters);
        defaultRulesEngine.registerRuleListener(ruleListener);
        defaultRulesEngine.registerRulesEngineListener(engineListener);
        return defaultRulesEngine;
    }

}
