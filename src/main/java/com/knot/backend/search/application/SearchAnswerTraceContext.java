package com.knot.backend.search.application;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.List;

public final class SearchAnswerTraceContext implements AutoCloseable {
    private static final String TRACEPARENT = "traceparent";
    private static final Tracer TRACER = GlobalOpenTelemetry.getTracer("com.knot.backend.search");
    private static final TextMapGetter<String> TRACEPARENT_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(String carrier) {
            return List.of(TRACEPARENT);
        }

        @Override
        public String get(
                String carrier,
                String key
        ) {
            if (!TRACEPARENT.equals(key)) {
                return null;
            }
            return carrier;
        }
    };

    private final Span span;
    private final Scope scope;

    private SearchAnswerTraceContext(
            Span span,
            Scope scope
    ) {
        this.span = span;
        this.scope = scope;
    }

    public static String captureTraceparent() {
        SpanContext context = Span.current()
                .getSpanContext();
        if (!context.isValid()) {
            return null;
        }
        return "00-" + context.getTraceId() + "-" + context.getSpanId() + "-" + context.getTraceFlags()
                .asHex();
    }

    public static SearchAnswerTraceContext openWorker(String traceparent) {
        SpanBuilder builder = TRACER.spanBuilder("search.answer.process")
                .setNoParent();
        SpanContext parent = linkedContext(traceparent);
        if (parent.isValid()) {
            builder.addLink(parent);
        }
        Span span = builder.startSpan();
        return new SearchAnswerTraceContext(
                span,
                span.makeCurrent()
        );
    }

    public void recordFailure() {
        span.setStatus(StatusCode.ERROR);
    }

    @Override
    public void close() {
        scope.close();
        span.end();
    }

    private static SpanContext linkedContext(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return SpanContext.getInvalid();
        }
        Context extracted = W3CTraceContextPropagator.getInstance()
                .extract(
                        Context.root(),
                        traceparent,
                        TRACEPARENT_GETTER
                );
        return Span.fromContext(extracted)
                .getSpanContext();
    }
}
