package io.axoniq.axonserver.localstorage.query.expressions.timeconstraints;

import io.axoniq.axonserver.localstorage.query.ExpressionContext;
import io.axoniq.axonserver.localstorage.query.PipeExpression;
import io.axoniq.axonserver.localstorage.query.Pipeline;
import io.axoniq.axonserver.localstorage.query.QueryResult;

/**
 * Author: marc
 * At this moment this is a NoOp. Time constraint is already applied at the source.
 */
public class NoOpExpression implements PipeExpression {

    private static final NoOpExpression INSTANCE = new NoOpExpression();

    public static NoOpExpression instance() {
        return INSTANCE;
    }

    private NoOpExpression() {
    }

    @Override
    public boolean process(ExpressionContext context, QueryResult result, Pipeline next) {
        return next.process(result);
    }

}