package io.axoniq.axonserver.localstorage.query;


import org.xml.sax.InputSource;

import java.io.StringReader;
import java.math.BigDecimal;

/**
 * Author: marc
 */
public interface ExpressionResult extends Comparable<ExpressionResult> {

    default boolean isTrue() {
        return Boolean.TRUE.equals(getValue());
    }

    Object getValue();

    default ExpressionResult min(ExpressionResult other) {
        if( other == null) return this;
        int compare = this.compareTo(other);
        if( compare > 0) return other;
        return this;
    }

    default ExpressionResult max(ExpressionResult other) {
        if( other == null) return this;
        int compare = this.compareTo(other);
        if( compare < 0) return other;
        return this;
    }

    default ExpressionResult getByIdentifier(String identifier) {
        return null;
    }

    default boolean isNonNull() {
        return getValue() != null;
    }

    default ExpressionResult divide(ExpressionResult other) {
        throw new UnsupportedOperationException("Divide not supported for type: " + getClass().getSimpleName());
    }

    default ExpressionResult add(ExpressionResult other) {
        throw new UnsupportedOperationException("Add not supported for type: " + getClass().getSimpleName());
    }

    default ExpressionResult subtract(ExpressionResult other) {
        throw new UnsupportedOperationException("Subtract not supported for type: " + getClass().getSimpleName());
    }

    default ExpressionResult multiply(ExpressionResult other) {
        throw new UnsupportedOperationException("Multiply not supported for type: " + getClass().getSimpleName());
    }

    default Object asJson() {
        return "{}";
    }

    default boolean isNumeric() {
        return getValue() instanceof BigDecimal;
    }

    default BigDecimal getNumericValue() {
        return (BigDecimal) getValue();
    }

    default long count() {
        return isNonNull() ? 1 : 0;
    }

    default Object asXml() {
        return new InputSource(new StringReader(toString()));
    }

    default ExpressionResult modulo(ExpressionResult other) {
        throw new UnsupportedOperationException("Modulo not supported for type: " + getClass().getSimpleName());
    }
}