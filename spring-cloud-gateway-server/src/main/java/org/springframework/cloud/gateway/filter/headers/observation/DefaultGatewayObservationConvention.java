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

import io.micrometer.common.KeyValues;
import io.micrometer.common.lang.NonNull;
import io.micrometer.common.lang.Nullable;

import static org.springframework.cloud.gateway.filter.headers.observation.GatewayDocumentedObservation.LowCardinalityKeys.METHOD;
import static org.springframework.cloud.gateway.filter.headers.observation.GatewayDocumentedObservation.LowCardinalityKeys.STATUS;
import static org.springframework.cloud.gateway.filter.headers.observation.GatewayDocumentedObservation.LowCardinalityKeys.URI;

public class DefaultGatewayObservationConvention implements GatewayObservationConvention {

	public static final GatewayObservationConvention INSTANCE = new DefaultGatewayObservationConvention();

	@Override
	public KeyValues getLowCardinalityKeyValues(GatewayContext context) {
		KeyValues keyValues = KeyValues.empty();
		if (context.getCarrier() == null) {
			return keyValues;
		}
		// TODO: URI is high cardinality
		keyValues = keyValues.and(METHOD.withValue(context.getRequest().getMethod().name()), URI.withValue(context.getRequest().getURI().toString()));
		if (context.getResponse() != null && context.getResponse().getStatusCode() != null) {
			keyValues = keyValues.and(STATUS.withValue(String.valueOf(context.getResponse().getStatusCode().value())));
		}
		return keyValues;
	}

	@Override
	@NonNull
	public String getName() {
		return "http.client.requests";
	}

	@Nullable
	@Override
	public String getContextualName(GatewayContext context) {
		if (context.getRequest() == null) {
			return null;
		}
		return "HTTP " + context.getRequest().getMethod();
	}
}
