package io.axoniq.axonserver.localstorage.query;

import java.util.List;

public interface Pipeline {

    boolean process(QueryResult value);

    default List<String> columnNames(List<String> inputColumnNames) {
        return inputColumnNames;
    }
}