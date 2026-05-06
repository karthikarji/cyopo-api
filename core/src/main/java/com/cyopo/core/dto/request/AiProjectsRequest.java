package com.cyopo.core.dto.request;

import lombok.Getter;

import java.util.List;

@Getter
public class AiProjectsRequest {

    private String name;
    private String title;
    private String bio;
    private List<String> technologies;
}