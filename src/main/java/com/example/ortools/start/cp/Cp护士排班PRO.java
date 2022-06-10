package com.example.ortools.start.cp;

import com.google.ortools.Loader;
import com.google.ortools.sat.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class Cp护士排班PRO {

    public static void main(String[] args) {

        /**
         * 在下一个示例中，医院主管需要在三天内为四名护士创建一个时间表，但需满足以下条件：
         *
         * 每天分为三个 8 小时轮班。
         * 每天，每个班次都分配给一名护士，没有护士工作超过一个班次。
         * 在三天的时间里，每位护士至少被分配到两个班次。
         */


        Loader.loadNativeLibraries();

        // 基础数据
        int nurseNum = 5;
        int daysNum = 7;
        int shiftsNum = 3;
        int[] nurses = IntStream.range(0, nurseNum).toArray();
        int[] days = IntStream.range(0, daysNum).toArray();
        int[] shifts = IntStream.range(0, shiftsNum).toArray();
        // 三元组，对应于每天的三个班次。
        // 三元组的每个元素都是 0 或 1，表示是否请求了班次。
        // 例如，第 1 行第 5 个位置的三元组 [0, 0, 1] 表示护士 1 在第 5 天请求换班 3。
        final int[][][] shiftRequests = new int[][][] {
                {
                        {0, 0, 1},
                        {0, 0, 0},
                        {0, 0, 0},
                        {0, 0, 0},
                        {0, 0, 1},
                        {0, 1, 0},
                        {0, 0, 1},
                },
                {
                        {0, 0, 0},
                        {0, 0, 0},
                        {0, 1, 0},
                        {0, 1, 0},
                        {1, 0, 0},
                        {0, 0, 0},
                        {0, 0, 1},
                },
                {
                        {0, 1, 0},
                        {0, 1, 0},
                        {0, 0, 0},
                        {1, 0, 0},
                        {0, 0, 0},
                        {0, 1, 0},
                        {0, 0, 0},
                },
                {
                        {0, 0, 1},
                        {0, 0, 0},
                        {1, 0, 0},
                        {0, 1, 0},
                        {0, 0, 0},
                        {1, 0, 0},
                        {0, 0, 0},
                },
                {
                        {0, 0, 0},
                        {0, 0, 1},
                        {0, 1, 0},
                        {0, 0, 0},
                        {1, 0, 0},
                        {0, 1, 0},
                        {0, 0, 0},
                },
        };
        // 组建数据
        CpModel model = new CpModel();
        Literal[][][] linearArguments = new Literal[nurseNum][daysNum][shiftsNum];
        for (int nurs : nurses) {
            for (int day : days) {
                for (int shift : shifts) {
                    linearArguments[nurs][day][shift] = model.newBoolVar("shifts_护士：" + nurs + "，第几天：" + day + "，第几班：" + shift);
                }
            }
        }
        // 创建约束--每天 每个班次分给一名护士
        for (int day : days) {
            for (int shift : shifts) {
                List<Literal> allNurses = new ArrayList<>();
                for (int nurs : nurses) {
                    allNurses.add(linearArguments[nurs][day][shift]);
                }
                model.addExactlyOne(allNurses);
            }
        }

        // 创建约束--每天 没有护士工作超过一个班次
        for (int nurs : nurses) {
            for (int day : days) {
                List<Literal> allNurses = new ArrayList<>();
                for (int shift : shifts) {
                    allNurses.add(linearArguments[nurs][day][shift]);
                }
                model.addAtMostOne(allNurses);
            }
        }

        // 创建约束--在5天的时间里，每位护士至少被分配到两个班次
        int allShifts = daysNum * shiftsNum ;
        int minShiftsPerNurse = allShifts / nurseNum;
        int maxShiftsPerNurse = allShifts % nurseNum == 0?minShiftsPerNurse:minShiftsPerNurse+1;
        for (int nurs : nurses) {
            LinearExprBuilder linearExprBuilder = LinearExpr.newBuilder();
            for (int day : days) {
                for (int shift : shifts) {
                    linearExprBuilder.add(linearArguments[nurs][day][shift]);
                }
            }
            model.addLinearConstraint(linearExprBuilder,minShiftsPerNurse,maxShiftsPerNurse);
        }

        // 约束，护士自己请求的班次
        for (int nurs : nurses) {
            LinearExprBuilder linearExprBuilder = LinearExpr.newBuilder();
            for (int day : days) {
                for (int shift : shifts) {
                    linearExprBuilder.addTerm(linearArguments[nurs][day][shift],shiftRequests[nurs][day][shift]);
                }
            }
            model.maximize(linearExprBuilder);
        }

        // 求解
        CpSolver solver = new CpSolver();
        solver.getParameters().setLinearizationLevel(0);
        solver.getParameters().setEnumerateAllSolutions(true);
        // Display the first five solutions.
        final int solutionLimit = 5;

        // Creates a solver and solves the model.
        CpSolverStatus status = solver.solve(model);
        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            System.out.printf("Solution:%n");
            for (int d : days) {
                System.out.printf("Day %d%n", d);
                for (int n : nurses) {
                    for (int s : shifts) {
                        if (solver.booleanValue(linearArguments[n][d][s])) {
                            if (shiftRequests[n][d][s] == 1) {
                                System.out.printf("  Nurse %d works shift %d (requested).%n", n, s);
                            } else {
                                System.out.printf("  Nurse %d works shift %d (not requested).%n", n, s);
                            }
                        }
                    }
                }
            }
            System.out.printf("Number of shift requests met = %f (out of %d)%n", solver.objectiveValue(),
                    nurseNum * minShiftsPerNurse);
        } else {
            System.out.printf("No optimal solution found !");
        }
        // Statistics.
        System.out.println("Statistics");
        System.out.printf("  conflicts: %d%n", solver.numConflicts());
        System.out.printf("  branches : %d%n", solver.numBranches());
        System.out.printf("  wall time: %f s%n", solver.wallTime());
    }

}
