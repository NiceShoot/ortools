package com.example.ortools.autodivide.v2.autodividegroup.utils;

import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpObjectiveProto;
import com.google.ortools.sat.LinearArgument;
import com.google.ortools.sat.LinearExpr;

public class OrToolsUtils {

    /**
     * or-tools 最大目标
     */
    public static void maximize(CpModel model, LinearArgument builder) {
        CpObjectiveProto.Builder obj = model.getBuilder().getObjectiveBuilder();
        LinearExpr e = builder.build();
        for(int i = 0; i < e.numElements(); ++i) {
            obj.addVars(e.getVariableIndex(i)).addCoeffs(-e.getCoefficient(i));
        }
        obj.setOffset((double)(-e.getOffset()));
        obj.setScalingFactor(-1.0D);
    }
    public static void minimize(CpModel model, LinearArgument expr) {
        CpObjectiveProto.Builder obj = model.getBuilder().getObjectiveBuilder();
        LinearExpr e = expr.build();

        for(int i = 0; i < e.numElements(); ++i) {
            obj.addVars(e.getVariableIndex(i)).addCoeffs(e.getCoefficient(i));
        }

        obj.setOffset((double)e.getOffset());
    }
}
