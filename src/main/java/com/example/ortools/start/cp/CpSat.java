package com.example.ortools.start.cp;

import com.google.ortools.Loader;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverSolutionCallback;
import com.google.ortools.sat.IntVar;

public class CpSat {


    public static void main(String[] args) {

        Loader.loadNativeLibraries();
        /**
         * 三个变量 x、y 和 z，每个变量都可以取值：0、1 或 2。
         * 一个约束：x ≠ y
         */
        // 创建模型
        CpModel model  = new CpModel();

        // 创建变量
        IntVar x = model.newIntVar(0, 2, "x");
        IntVar y = model.newIntVar(0, 2, "y");
        IntVar z = model.newIntVar(0, 2, "z");

        // 创建约束
        model.addDifferent(x,y);

        // 求解器
        CpSolver solver = new CpSolver();


        /**
         * OPTIMAL	找到了一个最优可行的解决方案。
         * FEASIBLE	找到了一个可行的解决方案，但我们不知道它是否是最优的。
         * INFEASIBLE	这个问题被证明是不可行的。
         * MODEL_INVALID	给定的 CpModelProto 没有通过验证步骤。您可以通过调用获取详细的错误信息ValidateCpModel(model_proto)。
         * UNKNOWN	模型的状态是未知的，因为在某些事情导致求解器停止之前没有找到解决方案（或者问题未被证明是不可行的），例如时间限制、内存限制或用户设置的自定义限制。
         */
//        CpSolverStatus status = solver.solve(model);
//        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
//            System.out.println("x = " + solver.value(x));
//            System.out.println("y = " + solver.value(y));
//            System.out.println("z = " + solver.value(z));
//        } else {
//            System.out.println("No solution found.");
//        }

        VarArraySolutionPrinter cb = new VarArraySolutionPrinter(new IntVar[]{x, y, z});
        // 设置获得所有可行解
        solver.getParameters().setEnumerateAllSolutions(true);
        // 求解器执行
        solver.solve(model, cb);
    }



    // 回调
    public static class VarArraySolutionPrinter extends CpSolverSolutionCallback {

        private int solutionCount;
        private final IntVar[] variableArray;

        public VarArraySolutionPrinter(IntVar[] variables) {
            variableArray = variables;
        }

        @Override
        public void onSolutionCallback() {
            System.out.printf("Solution #%d: time = %.02f s%n", solutionCount, wallTime());
            for (IntVar v : variableArray) {
                System.out.printf("  %s = %d%n", v.getName(), value(v));
            }
            solutionCount++;
        }
    }
}
