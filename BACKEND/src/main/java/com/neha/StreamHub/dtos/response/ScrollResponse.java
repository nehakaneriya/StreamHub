package com.neha.StreamHub.dtos.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScrollResponse<T> {

    private List<T> content;

    // The scrollId is used to fetch the next set of results in a paginated response.
    private String scrollId;

    // The hasNext field indicates whether there are more results to fetch.
    private boolean hasNext;

    // The pageSize field indicates the number of items returned in the current response.
    private int pageSize;

}
