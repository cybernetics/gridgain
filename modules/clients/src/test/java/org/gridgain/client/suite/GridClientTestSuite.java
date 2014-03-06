/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.client.suite;

import junit.framework.*;
import org.gridgain.client.*;
import org.gridgain.client.impl.*;
import org.gridgain.client.integration.*;
import org.gridgain.client.marshaller.protobuf.*;
import org.gridgain.client.util.*;
import org.gridgain.grid.kernal.processors.rest.*;
import org.gridgain.grid.kernal.processors.rest.protocols.tcp.*;
import org.gridgain.testframework.*;

import java.io.*;

/**
 * Test suite includes all test that concern REST processors.
 */
public class GridClientTestSuite extends TestSuite {
    /**
     * @return Suite that contains all tests for REST.
     * @throws IOException If failed.
     */
    public static TestSuite suite() throws IOException {
        TestSuite suite = GridTestUtils.createLocalTestSuite("Gridgain Clients Test Suite");

        // Parser standalone test.
        suite.addTest(new TestSuite(GridTcpRestParserSelfTest.class));

        // Test memcache protocol with custom test client.
        suite.addTest(new TestSuite(GridRestMemcacheProtocolSelfTest.class));

        // Test custom binary protocol with test client.
        suite.addTest(new TestSuite(GridRestBinaryProtocolSelfTest.class));

        // Test jetty rest processor
        suite.addTest(new TestSuite(GridJettyRestProcessorSignedSelfTest.class));
        suite.addTest(new TestSuite(GridJettyRestProcessorUnsignedSelfTest.class));
        suite.addTest(new TestSuite(GridClientHttpRestSecuritySelfTest.class));

        // Test TCP rest processor with original memcache client.
        suite.addTest(new TestSuite(GridClientMemcachedProtocolSelfTest.class));

        suite.addTest(new TestSuite(GridRestProcessorStartSelfTest.class));

        // Test cache flag conversion.
        suite.addTest(new TestSuite(GridClientCacheFlagsCodecTest.class));

        // Test multi-start.
        suite.addTest(new TestSuite(GridRestProcessorMultiStartSelfTest.class));

        // Test clients.
        suite.addTest(new TestSuite(GridClientDataImplSelfTest.class));
        suite.addTest(new TestSuite(GridClientComputeImplSelfTest.class));
        suite.addTest(new TestSuite(GridClientHttpSelfTest.class));
        suite.addTest(new TestSuite(GridClientHttpDirectSelfTest.class));
        suite.addTest(new TestSuite(GridClientTcpSelfTest.class));
        suite.addTest(new TestSuite(GridClientTcpDirectSelfTest.class));
        suite.addTest(new TestSuite(GridClientTcpSslSelfTest.class));
        suite.addTest(new TestSuite(GridClientTcpSslDirectSelfTest.class));
        suite.addTest(new TestSuite(GridClientHttpsSelfTest.class));
        suite.addTest(new TestSuite(GridClientHttpsDirectSelfTest.class));

        // Test client with many nodes.
        suite.addTest(new TestSuite(GridClientHttpMultiNodeSelfTest.class));
        suite.addTest(new TestSuite(GridClientHttpDirectMultiNodeSelfTest.class));
        suite.addTest(new TestSuite(GridClientTcpMultiNodeSelfTest.class));
        suite.addTest(new TestSuite(GridClientTcpDirectMultiNodeSelfTest.class));
        suite.addTest(new TestSuite(GridClientTcpSslMultiNodeSelfTest.class));
        suite.addTest(new TestSuite(GridClientTcpSslDirectMultiNodeSelfTest.class));
        suite.addTest(new TestSuite(GridClientHttpsMultiNodeSelfTest.class));
        suite.addTest(new TestSuite(GridClientHttpsDirectMultiNodeSelfTest.class));
        suite.addTest(new TestSuite(GridClientTcpUnreachableMultiNodeSelfTest.class));
        suite.addTest(new TestSuite(GridClientPreferDirectSelfTest.class));

        // Test client with many nodes and in multithreaded scenarios
        suite.addTest(new TestSuite(GridClientHttpMultiThreadedSelfTest.class));
        suite.addTest(new TestSuite(GridClientTcpMultiThreadedSelfTest.class));
        suite.addTest(new TestSuite(GridClientTcpSslMultiThreadedSelfTest.class));
        suite.addTest(new TestSuite(GridClientHttpsMultiThreadedSelfTest.class));

        // Test client authentication.
        suite.addTest(new TestSuite(GridClientTcpSecuritySelfTest.class));
        suite.addTest(new TestSuite(GridClientHttpSecuritySelfTest.class));
        suite.addTest(new TestSuite(GridClientTcpSslAuthenticationSelfTest.class));

        suite.addTest(new TestSuite(GridClientTcpConnectivitySelfTest.class));
        suite.addTest(new TestSuite(GridClientReconnectionSelfTest.class));

        // Rest task command handler test.
        suite.addTest(new TestSuite(GridTaskCommandHandlerSelfTest.class));

        // Default cache only test.
        suite.addTest(new TestSuite(GridClientDefaultCacheSelfTest.class));

        suite.addTestSuite(GridClientFutureAdapterSelfTest.class);
        suite.addTestSuite(GridClientProtobufMarshallerSelfTest.class);
        suite.addTestSuite(GridClientPartitionAffinitySelfTest.class);
        suite.addTestSuite(GridClientPropertiesConfigurationSelfTest.class);
        suite.addTestSuite(GridClientConsistentHashSelfTest.class);
        suite.addTestSuite(GridClientJavaHasherSelfTest.class);

        suite.addTestSuite(GridClientByteUtilsTest.class);

        suite.addTest(new TestSuite(GridClientTopologyCacheSelfTest.class));

        return suite;
    }
}
