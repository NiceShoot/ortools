package com.example.ortools.自动分组.v2.autodividegroup.listener;

import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rule;
import org.jeasy.rules.api.RuleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AutoDivideGroupRuleListener implements RuleListener {

    private static final Logger logger = LoggerFactory.getLogger(AutoDivideGroupRuleListener.class);


    @Override
    public boolean beforeEvaluate(Rule rule, Facts facts) {
        return true;
    }


    @Override
    public void afterEvaluate(Rule rule, Facts facts, boolean b) {
    }

    @Override
    public void beforeExecute(Rule rule, Facts facts) {
    }

    @Override
    public void onSuccess(Rule rule, Facts facts) {
    }

    @Override
    public void onFailure(Rule rule, Facts facts, Exception e) {
        logger.info("【{}】规则执行失败,失败原因:",rule.getName(),e);
    }
}
