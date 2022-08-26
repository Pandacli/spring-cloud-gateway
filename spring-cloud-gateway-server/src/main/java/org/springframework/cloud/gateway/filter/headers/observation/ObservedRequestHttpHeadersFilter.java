/*
 * Copyright 2013-2021 the original author or authors.
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

import io.micrometer.common.lang.Nullable;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/**
 * Trace representation of {@link HttpHeadersFilter} for a request.
 *
 * @author Marcin Grzejszczak
 * @since 3.0.0
 */
public class ObservedRequestHttpHeadersFilter implements HttpHeadersFilter {

	private static final Log log = LogFactory.getLog(ObservedRequestHttpHeadersFilter.class);

	static final String CHILD_OBSERVATION = "gateway.observation";

	static final String CHILD_OBSERVATION_CONTEXT = "gateway.observation.context";

	private final ObservationRegistry observationRegistry;

	@Nullable
	private final GatewayObservationConvention customGatewayObservationConvention;

	public ObservedRequestHttpHeadersFilter(ObservationRegistry observationRegistry) {
		this(observationRegistry, null);
	}

	public ObservedRequestHttpHeadersFilter(ObservationRegistry observationRegistry, @Nullable GatewayObservationConvention customGatewayObservationConvention) {
		this.observationRegistry = observationRegistry;
		this.customGatewayObservationConvention = customGatewayObservationConvention;
	}

	@Override
	public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
		HttpHeaders newHeaders = new HttpHeaders();
		newHeaders.putAll(input);
		if (log.isDebugEnabled()) {
			log.debug("Will instrument the HTTP request headers " + newHeaders);
		}
		Observation parentObservation = exchange.getAttribute("micrometer.observation");
		GatewayContext gatewayContext = new GatewayContext(newHeaders, exchange.getRequest());
		Observation childObservation = GatewayDocumentedObservation.GATEWAY_HTTP_CLIENT_OBSERVATION
				.observation(this.customGatewayObservationConvention, DefaultGatewayObservationConvention.INSTANCE, gatewayContext, this.observationRegistry);
		if (parentObservation != null) {
			childObservation = childObservation.parentObservation(parentObservation);
		}
		childObservation = childObservation.start();
		if (log.isDebugEnabled()) {
			log.debug("Client observation  " + childObservation + " created for the request. New headers are " + newHeaders);
		}
		exchange.getAttributes().put(CHILD_OBSERVATION, childObservation);
		exchange.getAttributes().put(CHILD_OBSERVATION_CONTEXT, gatewayContext);
		return newHeaders;
	}

	@Override
	public boolean supports(Type type) {
		return type.equals(Type.REQUEST);
	}

}
