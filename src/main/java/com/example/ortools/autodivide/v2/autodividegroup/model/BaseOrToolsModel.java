package com.example.ortools.autodivide.v2.autodividegroup.model;

import cn.xdf.api.resource.management.bean.dto.learningmachine.LmMemberCardBaseInfoDTO;
import cn.xdf.api.resource.management.bean.po.learningmachine.LmLearnGroupPO;
import com.google.ortools.sat.IntVar;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Data
public class BaseOrToolsModel {

    // 学习组中的 产品，科目，年级，版本，层级
    public static Function<LmLearnGroupPO, String> FUN_LEARN_GROUP_SELECT_PO = t -> t.getInnerCategoryCode() + "," + t.getSubjectCode() + "," + t.getGradeCode() + "," + t.getBookCode() + "," + t.getDifficultyLevelCode();
    // 会员卡中的 产品，科目，年级，版本，层级
    public static Function<LmMemberCardBaseInfoDTO, String> FUN_LEARN_GROUP_SELECT_PARAM = t -> t.getProductId() + "," + t.getSubjectCode() + "," + t.getStudyGradeCode() + "," + t.getBookCode() + "," + t.getDifficultyLevelCode();


    // 所有点位
    protected IntVar[][][] cardGroupPoints;

    // 每个档位对应的上限下限
    protected Map<Integer, Pair<Integer, Integer>> lineIndexMap;
    // key=产品,科目,年级,教材,层级 拼接成的key, value=会员卡数量
    protected Map<String,Integer> productKeyAndCardCountMap = new HashMap<>();
    // 初始化
    protected int[] groupArr;
    protected int[] priorityArr;
    protected int[] indexArr;
    // 数组角标与实际值对应关系
    protected Map<Integer,Integer> groupIndexStockMap = new HashMap<>();
    protected Map<Integer,Integer> groupIndexCurrentStuMap = new HashMap<>();
    protected Map<String,List<Integer>> groupIndexAndIndexAndPriorityIndexMap = new HashMap<>();
    protected Map<Integer,List<Integer>> groupIndexAndIndexIndexMap = new HashMap<>();
    protected Map<Integer,List<Integer>> priorityIndexAndGroupIndexMap = new HashMap<>();
    // key=产品,科目,年级,教材,层级 拼接成的key, value=组的index
    protected Map<String,List<Integer>> productKeyAndGroupIndexMap = new HashMap<>();
    // 系数限制
    protected Map<Integer,Integer> priorityCoeffMap = new HashMap<>();
}
