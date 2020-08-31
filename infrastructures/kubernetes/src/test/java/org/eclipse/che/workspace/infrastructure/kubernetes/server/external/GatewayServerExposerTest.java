/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.workspace.infrastructure.kubernetes.server.external;

import static org.eclipse.che.workspace.infrastructure.kubernetes.provision.GatewayRouterProvisioner.GATEWAY_CONFIGMAP_LABELS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServicePort;
import java.util.Collections;
import java.util.Map;
import org.eclipse.che.api.core.model.workspace.config.ServerConfig;
import org.eclipse.che.api.workspace.server.model.impl.ServerConfigImpl;
import org.eclipse.che.workspace.infrastructure.kubernetes.Annotations;
import org.eclipse.che.workspace.infrastructure.kubernetes.environment.KubernetesEnvironment;
import org.testng.annotations.Test;

public class GatewayServerExposerTest {

  private final String machineName = "machine";
  private final String serviceName = "service";
  private final String serverId = "server";
  private final ServicePort servicePort =
      new ServicePort("portName", 1, 1, "http", new IntOrString(1234));

  private final Map<String, String> s1attrs = Collections.singletonMap("s1attr", "s1val");

  private final Map<String, ServerConfig> servers =
      Collections.singletonMap("serverOne", new ServerConfigImpl("1111", "ws", null, s1attrs));

  private final ExternalServerExposer<KubernetesEnvironment> serverExposer =
      new GatewayServerExposer<>(new SingleHostExternalServiceExposureStrategy("che-host"));

  @Test
  public void testExposeServiceWithGatewayConfigmap() {
    // given
    KubernetesEnvironment k8sEnv = KubernetesEnvironment.builder().build();

    // when
    serverExposer.expose(k8sEnv, machineName, serviceName, serverId, servicePort, servers);

    // then
    Map<String, ConfigMap> configMaps = k8sEnv.getConfigMaps();
    assertTrue(configMaps.containsKey(serviceName + "-" + serverId));
    ConfigMap serverConfigMap = configMaps.get("service-server");

    // data should be empty at this point
    assertTrue(serverConfigMap.getData() == null || serverConfigMap.getData().isEmpty());

    assertEquals(serverConfigMap.getMetadata().getLabels(), GATEWAY_CONFIGMAP_LABELS);

    Map<String, String> annotations = serverConfigMap.getMetadata().getAnnotations();
    Annotations.Deserializer deserializer = Annotations.newDeserializer(annotations);
    assertEquals(deserializer.machineName(), machineName);

    Map<String, ServerConfigImpl> exposedServers = deserializer.servers();
    assertTrue(exposedServers.containsKey("serverOne"));

    ServerConfig s1 = exposedServers.get("serverOne");
    assertEquals(
        s1.getAttributes().get(s1attrs.keySet().iterator().next()),
        s1attrs.values().iterator().next());
    assertEquals(s1.getAttributes().get(ServerConfigImpl.SERVICE_NAME_ATTRIBUTE), "service");
    assertEquals(s1.getAttributes().get(ServerConfigImpl.SERVICE_PORT_ATTRIBUTE), "1234");
    assertEquals(s1.getPort(), "1111");
    assertEquals(s1.getProtocol(), "ws");
    assertEquals(s1.getPath(), "/service/server/");
  }
}
