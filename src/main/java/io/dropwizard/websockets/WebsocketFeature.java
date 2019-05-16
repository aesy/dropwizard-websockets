/**
 * The MIT License
 * Copyright (c) 2017 LivePerson, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.dropwizard.websockets;

import io.dropwizard.metrics.jetty9.websockets.InstWebSocketServerContainerInitializer;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import java.util.List;

public class WebsocketFeature implements Feature {
    @Override
    public boolean configure(FeatureContext context) {
        context.register(WebsocketConfigurer.class);

        return true;
    }

    private static final class WebsocketConfigurer implements ApplicationEventListener {
        private final Environment environment;
        private final ServiceLocator serviceLocator;

        @Inject
        private WebsocketConfigurer(
            Environment environment,
            ServiceLocator serviceLocator
        ) {
            this.environment = environment;
            this.serviceLocator = serviceLocator;
        }

        // TODO better error handling and logging

        @Override
        public void onEvent(ApplicationEvent event) {
            if (event.getType() != ApplicationEvent.Type.INITIALIZATION_START) {
                return;
            }

            ServerContainer wsContainer;

            try {
                wsContainer = InstWebSocketServerContainerInitializer.configureContext(
                    environment.getApplicationContext(), environment.metrics());
            } catch (ServletException exception) {
                return;
            }

            List<ServiceHandle<ServerEndpoint>> serviceHandles = serviceLocator.getAllServiceHandles(ServerEndpoint.class);

            for (ServiceHandle<ServerEndpoint> serviceHandle : serviceHandles) {
                Class<?> clazz = serviceHandle.getActiveDescriptor().getImplementationClass();
                ServerEndpoint anno = clazz.getAnnotation(ServerEndpoint.class);
                ServerEndpointConfig config = ServerEndpointConfig.Builder
                    .create(clazz, anno.value())
                    .configurator(new ServerEndpointConfig.Configurator() {
                        // TODO override handshake to allow filters ?

                        @Override
                        public <T> T getEndpointInstance(Class<T> endpointClass) {
                            return serviceLocator.getService(endpointClass);
                        }
                    })
                    .build();

                try {
                    wsContainer.addEndpoint(config);
                } catch (DeploymentException exception) {
                    return;
                }
            }
        }

        @Override
        public RequestEventListener onRequest(RequestEvent requestEvent) {
            return null;
        }
    }
}
