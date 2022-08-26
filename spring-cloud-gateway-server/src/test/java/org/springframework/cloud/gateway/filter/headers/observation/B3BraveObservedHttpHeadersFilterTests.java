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

import java.util.List;
import java.util.stream.Collectors;

import brave.Tracing;
import brave.propagation.B3Propagation;
import brave.sampler.Sampler;
import brave.test.TestSpanHandler;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveFinishedSpan;
import io.micrometer.tracing.brave.bridge.BravePropagator;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.test.simple.SpansAssert;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class B3BraveObservedHttpHeadersFilterTests {

	TestSpanHandler testSpanHandler = new TestSpanHandler();

	Tracing tracing = Tracing.newBuilder()
			.propagationFactory(B3Propagation.newFactoryBuilder().injectFormat(B3Propagation.Format.SINGLE).build())
			.addSpanHandler(testSpanHandler)
			.sampler(Sampler.ALWAYS_SAMPLE)
			.build();

	Tracer tracer = new BraveTracer(tracing.tracer(), new BraveCurrentTraceContext(tracing.currentTraceContext()),
			new BraveBaggageManager());

	Propagator propagator = new BravePropagator(tracing);

	@Test
	void shouldWorkWithB3SingleHeader() {
		TestObservationRegistry observationRegistry = TestObservationRegistry.create();
		observationRegistry.observationConfig().observationHandler(new ObservationHandler.FirstMatchingCompositeObservationHandler(new GatewayPropagatingSenderTracingObservationHandler(tracer, propagator), new DefaultTracingObservationHandler(tracer)));

		Observation.createNotStarted("parent", observationRegistry).observe(() -> {
			// given
			MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("http://localhost:8080/get")
					.header("X-A", "aValue");
			TraceContext context = tracer.currentTraceContext().context();
			propagator.inject(context, builder, (b, k, v) -> b.header(k, v));

			// and
			MockServerHttpRequest request = builder.build();
			MockServerWebExchange exchange = MockServerWebExchange.from(request);
			exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(200));
			// Parent observation
			exchange.getAttributes().put(ObservationThreadLocalAccessor.KEY, observationRegistry.getCurrentObservation());

			// and
			ObservedRequestHttpHeadersFilter requestHttpHeadersFilter = new ObservedRequestHttpHeadersFilter(observationRegistry);
			ObservedResponseHttpHeadersFilter responseHttpHeadersFilter = new ObservedResponseHttpHeadersFilter();

			// when
			HttpHeaders headers = requestHttpHeadersFilter.filter(request.getHeaders(), exchange);
			headers = responseHttpHeadersFilter.filter(headers, exchange);

			// then
			assertThat(headers).containsOnlyKeys("X-A", "b3")
					.doesNotContainEntry("b3", request.getHeaders().get("b3"));
			assertThat(headers.get("b3").get(0)).matches("^" + context.traceId()+"-(.*)-1-" + context.spanId() + "$");
			List<FinishedSpan> finishedSpans = testSpanHandler.spans().stream().map(BraveFinishedSpan::new).collect(Collectors.toList());
			SpansAssert.then(finishedSpans)
					.hasASpanWithName("HTTP GET", spanAssert -> spanAssert.hasTag("method", "GET").hasTag("status", "200").hasTag("uri", "http://localhost:8080/get"));
		});
	}
}
