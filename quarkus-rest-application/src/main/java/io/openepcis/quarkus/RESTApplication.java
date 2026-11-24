/*
 * Copyright 2022-2026 benelog GmbH & Co. KG
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */
package io.openepcis.quarkus;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;

@OpenAPIDefinition(
    info =
        @Info(
            title = "EPCIS 2.0 REST Bindings",
            // The version of the EPCIS standard these bindings implement. The
            // runtime already answers 2.0.1 (RestResourceUtil defaults the
            // GS1-EPCIS-Version header to it, and EPCISVersion knows the value);
            // only this annotation still said 2.0.0, so the published reference
            // advertised an older standard than the service actually speaks.
            // NOT to be confused with gs1.cbv.version in application.yml — the
            // Core Business Vocabulary is a separate vocabulary at 2.0.
            version = "2.0.1",
            license = @License(name = "Apache 2.0")))
// No security schemes: the Community Edition authenticates nobody. The Business edition declares
// its schemes through KeycloakOASFilter.
@ApplicationPath("/")
public class RESTApplication extends Application {}
