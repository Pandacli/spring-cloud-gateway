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

import io.micrometer.common.docs.KeyName;
import io.micrometer.common.lang.NonNullApi;
import io.micrometer.observation.Observation;
import io.micrometer.observation.docs.DocumentedObservation;

enum GatewayDocumentedObservation implements DocumentedObservation {

	/**
	 * Observation created when sending a request through the gateway.
	 */
	GATEWAY_HTTP_CLIENT_OBSERVATION {
		@Override
		public Class<? extends Observation.ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultGatewayObservationConvention.class;
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return LowCardinalityKeys.values();
		}

	};

	@NonNullApi
	enum LowCardinalityKeys implements KeyName {

		/**
		 * HTTP Method.
		 */
		METHOD {
			@Override
			public String asString() {
				return "method";
			}
		},

		/**
		 * HTTP Status.
		 */
		STATUS {
			@Override
			public String asString() {
				return "status";
			}
		},

		// TODO: What about the high cardinality of URI?
		/**
		 * HTTP URI.
		 */
		URI {
			@Override
			public String asString() {
				return "uri";
			}
		}

	}

}
