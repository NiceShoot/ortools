package com.example.ortools.自动分组.v2.autodividegroup.rule;

import cn.xdf.api.resource.management.funmoudle.autodividegroup.model.AutoDivideOrToolsDto;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.model.BaseOrToolsModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseRule extends BaseOrToolsModel {

    public static Logger logger = LoggerFactory.getLogger(BaseRule.class);

    public void init(AutoDivideOrToolsDto autoDivideOrToolsDto) {

        // ----------------------------初始化数组
        this.groupArr = autoDivideOrToolsDto.getGroupArr();
        this.priorityArr = autoDivideOrToolsDto.getPriorityArr();
        this.indexArr = autoDivideOrToolsDto.getIndexArr();
        this.cardGroupPoints = autoDivideOrToolsDto.getCardGroupPoints();
        this.groupIndexCurrentStuMap = autoDivideOrToolsDto.getGroupIndexCurrentStuMap();
        this.lineIndexMap = autoDivideOrToolsDto.getLineIndexMap();
        this.priorityCoeffMap = autoDivideOrToolsDto.getPriorityCoeffMap();
        this.productKeyAndCardCountMap = autoDivideOrToolsDto.getProductKeyAndCardCountMap();
        this.productKeyAndGroupIndexMap = autoDivideOrToolsDto.getProductKeyAndGroupIndexMap();
        this.priorityIndexAndGroupIndexMap = autoDivideOrToolsDto.getPriorityIndexAndGroupIndexMap();
        this.groupIndexStockMap = autoDivideOrToolsDto.getGroupIndexStockMap();
        this.groupIndexAndIndexAndPriorityIndexMap = autoDivideOrToolsDto.getGroupIndexAndIndexAndPriorityIndexMap();
    }


}
