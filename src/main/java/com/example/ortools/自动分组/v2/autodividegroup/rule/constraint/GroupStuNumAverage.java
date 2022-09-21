package com.example.ortools.自动分组.v2.autodividegroup.rule.constraint;

import cn.xdf.api.resource.management.funmoudle.autodividegroup.annotation.EasyRulesAnnotation;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.config.EasyRuleEnum;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.model.AutoDivideOrToolsDto;
import cn.xdf.api.resource.management.funmoudle.autodividegroup.rule.BaseRule;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.LinearExpr;
import com.google.ortools.sat.LinearExprBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.jeasy.rules.annotation.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Rule(priority = 1)
@EasyRulesAnnotation(name = "groupStuNumAverage",description = "均匀",ruleType = EasyRuleEnum.AUTO_DIVIDE_GROUP)
@Service
public class GroupStuNumAverage extends BaseRule {


    @Condition
    public boolean condition(@Fact("autoDivideOrToolsDto") AutoDivideOrToolsDto autoDivideOrToolsDto){
        super.init(autoDivideOrToolsDto);
        return Boolean.TRUE;
    }

    @Action
    public void action(@Fact("cpModel")CpModel cpModel){

        // 均匀散列 同产品科目年级教材层级档位 优先级 的 每个组的人数 均匀
        productKeyAndCardCountMap.forEach((productKey,cardMaxCount)->{
            List<Integer> groupIndexListByProductKey = productKeyAndGroupIndexMap.getOrDefault(productKey,new ArrayList<>(1));
            for (Integer index : indexArr) {
                for (Integer priority : priorityArr) {
                    List<Integer> groupIndexListByPriority = priorityIndexAndGroupIndexMap.getOrDefault(priority, new ArrayList<>(1));
                    groupIndexListByPriority.retainAll(groupIndexListByProductKey);
                    if (groupIndexListByPriority.size()>=2){
                        for (int x=0;x<groupIndexListByPriority.size()-1;x++){
                            Integer group1 = groupIndexListByPriority.get(x);
                            LinearExprBuilder builder1 = LinearExpr.newBuilder();
                            builder1.add(cardGroupPoints[group1][priority][index]);

                            Integer group2 = groupIndexListByPriority.get(x+1);
                            LinearExprBuilder builder2 = LinearExpr.newBuilder();
                            builder2.add(cardGroupPoints[group2][priority][index]);

                            Pair<Integer, Integer> pair = lineIndexMap.get(index);
                            Integer right = pair.getRight();
                            Integer left = pair.getLeft();

                            // group 1
                            Integer currentStuNum1 = groupIndexCurrentStuMap.get(group1);
                            Integer stock1 = groupIndexStockMap.get(group1);
                            Integer right1 = Math.min(right,stock1);
                            Integer left1 = Math.max(left,currentStuNum1);
                            Integer temp1 = currentStuNum1 > right1 ? 0 : right1-left1;

                            // group 2
                            Integer currentStuNum2 = groupIndexCurrentStuMap.get(group2);
                            Integer stock2 = groupIndexStockMap.get(group2);
                            Integer right2 = Math.min(right,stock2);
                            Integer left2 = Math.max(left,currentStuNum2);
                            Integer temp2 = currentStuNum1 > right2 ? 0 : right2-left2;

                            // 差值
                            if (temp2>temp1){
                                builder2.addTerm(builder1,-1);
                                cpModel.addLinearConstraint(builder2,0,Math.abs(temp2-temp1));
                            }
                            else if (temp2==temp1){
                                builder2.addTerm(builder1,-1);
                                cpModel.addLinearConstraint(builder2,0,1);
                            }
                            else {
                                builder1.addTerm(builder2,-1);
                                cpModel.addLinearConstraint(builder1,0,Math.abs(temp2-temp1));
                            }
                        }
                    }
                }
            }
        });

    }


}
