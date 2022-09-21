package com.example.ortools.自动分组.v2.autodividegroup.model;

import lombok.Data;

import java.util.concurrent.ConcurrentHashMap;

@Data
public class AutoDivideResultDto {

    /**
     * 结果
     */
    private ConcurrentHashMap<String, String> cardAndGroupResultMap;
}
