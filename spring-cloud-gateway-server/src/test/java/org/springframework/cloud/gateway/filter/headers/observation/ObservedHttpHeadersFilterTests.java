/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.filter.headers.observation;

import java.util.Collections;
import java.util.Deque;
import java.util.function.BiConsumer;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.tck.MeterRegistryAssert;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.test.SampleTestRunner;
import io.micrometer.tracing.test.reporter.BuildingBlocks;
import io.micrometer.tracing.test.simple.SpansAssert;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Spencer Gibb
 * @author Biju Kunjummen
 */
public class ObservedHttpHeadersFilterTests extends SampleTestRunner {

	ObservedHttpHeadersFilterTests() {
		super(SampleRunnerConfig.builder().build());
	}

	@Override
	public SampleTestRunnerConsumer yourCode() throws Exception {
		return (bb, meterRegistry) -> {

			// We assume that there has already been a trace
			MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("http://localhost:8080/get")
					.header("X-A", "aValue");
			TraceContext context = bb.getTracer().currentTraceContext().context();
			bb.getPropagator().inject(context, builder, (b, k, v) -> b.header(k, v));
			MockServerHttpRequest request = builder.build();
			MockServerWebExchange exchange = MockServerWebExchange.from(request);
			// Parent observation
			exchange.getAttributes().put(ObservationThreadLocalAccessor.KEY, getObservationRegistry().getCurrentObservation());
			exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(200));

			// when
			HttpHeaders headers = new ObservedRequestHttpHeadersFilter(getObservationRegistry()).filter(request.getHeaders(), exchange);
			headers = new ObservedResponseHttpHeadersFilter().filter(headers, exchange);

			// then
			assertThat(headers)
					.containsEntry("X-A", Collections.singletonList("aValue"))
					.containsEntry("X-B3-Sampled", Collections.singletonList("1"))
					.containsEntry("X-B3-TraceId", Collections.singletonList(context.traceId()))
					.doesNotContainEntry("X-B3-SpanId", Collections.singletonList(context.spanId()))
					.containsKey("X-B3-SpanId");
			SpansAssert.then(bb.getFinishedSpans())
					.hasASpanWithName("HTTP GET", spanAssert -> spanAssert.hasTag("method", "GET").hasTag("status", "200").hasTag("uri", "http://localhost:8080/get"));
			// TODO: URI is high cardinality
			MeterRegistryAssert.then(meterRegistry)
					.hasTimerWithNameAndTags("http.client.requests", Tags.of("error", "none", "method", "GET", "status", "200", "uri", "http://localhost:8080/get"))
					.hasMeterWithNameAndTags("http.client.requests.active", Tags.of("method", "GET", "uri", "http://localhost:8080/get"));
		};
	}

	@Override
	public BiConsumer<BuildingBlocks, Deque<ObservationHandler<? extends Observation.Context>>> customizeObservationHandlers() {
		return (bb, observationHandlers) -> observationHandlers.addFirst(new GatewayPropagatingSenderTracingObservationHandler(bb.getTracer(), bb.getPropagator()));
	}
}
