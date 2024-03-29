/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.     
 * 
 * The contents of this file are subject to the terms of the Common Development 
 * and Distribution License("CDDL") (the "License").  You may not use this file 
 * except in compliance with the License.
 * 
 * You can obtain a copy of the License at 
 * http://IdentityConnectors.dev.java.net/legal/license.txt
 * See the License for the specific language governing permissions and limitations 
 * under the License. 
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package org.identityconnectors.solaris;

import org.testng.annotations.Test;
import org.testng.AssertJUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.solaris.test.SolarisTestBase;
import org.identityconnectors.solaris.test.SolarisTestCommon;
import org.junit.Ignore;

public class SolarisConnectionTest extends SolarisTestBase {
    
    private static final String TIMEOUT_BETWEEN_MSGS = "0.5";
    private static final String LAST_ECHOED_INFO = "sausage.";
    private final Log log = Log.getLog(SolarisConnectionTest.class);
    
    /** test connection to the configuration given by default credentials (build.groovy) */
    @Test
    public void testGoodConnection() {
        SolarisConnector connector = new SolarisConnector();
        connector.init(getConfiguration());
        
        try {
            connector.checkAlive();
        } finally {
            connector.dispose();
        }
    }
    
    /**
     * test that if an error occurs in the output, an exception is thrown.
     */
    @Test
    public void testErrorReplyScenario() {
        Set<String> rejects = new HashSet<String>();
        final String ERROR = "ERROR";
        rejects.add(ERROR);
        try {
            getConnection().executeCommand(
                    String.format("echo \"%s: ahoj ship\"", ERROR), 
                    rejects);
            AssertJUnit.fail("no exception thrown, when error found.");
        } catch (ConnectorException e) {
            // OK
        }
    }
    
    @Test @Ignore
    public void testErrorReplyScenarioWithTimeout() {
        Set<String> rejects = new HashSet<String>();
        final String ERROR_MARKER = "ERROR";
        rejects.add(ERROR_MARKER);
        try {
            // Tougher test (it demands setting a long timeout on the connection.)
            //conn.executeCommand(String.format("export timeout=\"%s\" && echo  \"%s: ahoj\" && sleep \"$timeout\" && echo  \"ship\" && sleep \"$timeout\" && echo  \"egg\" && sleep \"$timeout\" && echo  \"spam\" && sleep \"$timeout\" && echo  \"%s\"", TIMEOUT_BETWEEN_MSGS, ERROR_MARKER, LAST_ECHOED_INFO), rejects);
            // Weaker test
            getConnection().executeCommand(String.format("export timeout=\"%s\" && echo  \"%s: ahoj\" && sleep \"$timeout\" && echo \"%s\"", TIMEOUT_BETWEEN_MSGS, ERROR_MARKER, LAST_ECHOED_INFO), rejects);
            AssertJUnit.fail("no exception thrown, when error found.");
        } catch (ConnectorException e) {
            final String exMsg = e.getMessage();
            String msg = String.format("Buffer <%s> doesn't containt the last echoed info: '%s'.", exMsg, LAST_ECHOED_INFO);
            //System.out.println("TEST: found: <"  exMsg  ">");
            AssertJUnit.assertTrue(msg, exMsg.contains(LAST_ECHOED_INFO));
        }
    }
    
    @Test
    public void testSpecialAccepts() {
        final String MSG = "AHOJ";
        final String errMarker = "ERROR";
        
        getConnection().executeCommand(String.format("echo \"%s\"", MSG), Collections.<String>emptySet(), CollectionUtil.newSet(MSG));

        try {
            getConnection().executeCommand(String.format("echo \"%s %s\"", errMarker, MSG), CollectionUtil.newSet(errMarker), CollectionUtil.newSet(MSG));
            AssertJUnit.fail("no exception thrown when error should be found in the output.");
        } catch (ConnectorException ex) {
            //OK
        }
    }

    @Test 
    public void testSSHPubKeyConnection() {
        if (!SolarisTestCommon.getProperty("unitTests.SolarisConnection.testSSHPubkeyMode", Boolean.class)) {
            log.info("skipping testSSHPubKeyConnection test, because the resource doesn't support it.");
            return;
        }
        
        // connection is recreated after every test method call so we are free to modify it.
        SolarisConfiguration config = getConnection().getConfiguration();
        config.setPassphrase(SolarisTestCommon.getProperty("rootPassphrase", GuardedString.class));
        config.setPrivateKey(SolarisTestCommon.getProperty("rootPrivateKey", GuardedString.class));
        config.setConnectionType(ConnectionType.SSHPUBKEY.toString());
        SolarisConnection conn = new SolarisConnection(config);
        
        String out = conn.executeCommand("echo 'ahoj ship'");
        AssertJUnit.assertTrue(out.contains("ahoj ship"));
        conn.dispose();
    }
    
    @Test
    public void testSudoAuthorization() {
        if (!SolarisTestCommon.getProperty("unitTests.SolarisConnection.testsudoAuthorization", Boolean.class)) {
            log.info("skipping testSSHPubKeyConnection test, because the resource doesn't support it.");
            return;
        }
        
        // connection is recreated after every test method call so we are free to modify it.
        SolarisConfiguration config = getConnection().getConfiguration();
        config.setSudoAuthorization(true);
        
        SolarisConnection conn = new SolarisConnection(config);
        
        String out = conn.executeCommand("echo 'ahoj ship'");
        AssertJUnit.assertTrue(out.contains("ahoj ship"));
        conn.dispose();        
    }
    
    @Test
    public void checkAliveTest() {
        try {
            getConnection().checkAlive();
        } catch (Exception ex) {
            AssertJUnit.fail("no exception is expected.");
        }
        getConnection().dispose();
        try {
            getConnection().checkAlive();
            AssertJUnit.fail("exception should be thrown");
        } catch (Exception ex) {
            // OK
        }
    }
    
    /**
     * verify if the connector is resistant to password exploits. Password is
     * invalid if it contains control characters (such as carriage return and
     * newline).
     */
    @Test
    public void testPasswordExploit() {
        // we shouldn't be able to send some special characters with the password!
        String exploit = "nonSensePassWord \n echo 'Popeye'";
        try {
            getConnection().sendPassword(new GuardedString(exploit.toCharArray()));
            AssertJUnit.fail("no exception thrown  upon sending a password exploit.");
        } catch (IllegalArgumentException ex) {
            // OK
        }
    }
    
    @Test
    public void testConnectorConstruction() {
        //
        boolean isSudoAuthorization = false;
        try {
            isSudoAuthorization = SolarisTestCommon.getProperty("unitTests.SolarisConnection.testsudoAuthorization", Boolean.class);
        } catch (Exception ex) {
            // OK
        }
        
        implTestConnectorConstruction(isSudoAuthorization);
    }

    private void implTestConnectorConstruction(boolean isSudoAuthorization) {
        SolarisConfiguration config = reloadConfig(isSudoAuthorization);
        
        // negative test: bad host
        config.setHost("111.111.111.111");
        try {
            new SolarisConnection(config).checkAlive();
            AssertJUnit.fail("Expected bad host to fail.");
        } catch (Exception ex) {
            // OK
        }
        
        // negative test: bad port
        config = reloadConfig(isSudoAuthorization);
        config.setPort(1);
        try {
            new SolarisConnection(config).checkAlive();
            AssertJUnit.fail("Expected bad port to fail.");
        } catch (Exception ex) {
            // OK
        }
        
        // negative test: bad admin
        config = reloadConfig(isSudoAuthorization);
        if (!isSudoAuthorization) {
            config.setRootUser("badAdminUser");
        } else {
            config.setLoginUser("badAdminUser");
        }
        try {
            new SolarisConnection(config).checkAlive();
            AssertJUnit.fail("Expected bad admin to fail.");
        } catch (Exception ex) {
            // OK
        }
        
        // negative test: bad admin password
        //    avoiding bad admin password test for SSHPubKey connection
        //    as it does not use the password at all.
        if (!ConnectionType.toConnectionType(config.getConnectionType()).equals(ConnectionType.SSHPUBKEY)) {
            config = reloadConfig(isSudoAuthorization);
            if (!isSudoAuthorization) {
                config.setCredentials(new GuardedString("badPassword".toCharArray()));
            } else {
                config.setPassword(new GuardedString("badPassword".toCharArray()));
            }
            try {
                new SolarisConnection(config).checkAlive();
                AssertJUnit.fail("Expected bad admin password to fail.");
            } catch (Exception ex) {
                // OK
            }
        }
        
        // negative test: bad shell prompt
        config = reloadConfig(isSudoAuthorization);
        if (!isSudoAuthorization) {
            config.setRootShellPrompt("badRootShellPrompt");
        } else {
            config.setLoginShellPrompt("badLoginShellPrompt");
        }
        try {
            new SolarisConnection(config).checkAlive();
            AssertJUnit.fail("Expected bad shell prompt to fail.");
        } catch (Exception ex) {
            // OK
        }
        
        // negative test: bad conn type
        config = reloadConfig(isSudoAuthorization);
        try {
            config.setConnectionType("nonExistingConnetionType");
            new SolarisConnection(config).checkAlive();
            AssertJUnit.fail("Expected bad connection type to fail.");
        } catch (Exception ex) {
            // OK
        }
    }

    private SolarisConfiguration reloadConfig(boolean isSudoAuthorization) {
        SolarisConfiguration config = getConnection().getConfiguration();
        if (isSudoAuthorization) {
            config.setSudoAuthorization(true);
        }
        
        return config;
    }
    
    /**
     * test that repetitive open of sessions doesn't cause exception.
     * This test shows if some resources are not cleaned up properly.
     * 
     * These tests verify Issue #614. Turned off, just in cases of changes to SolarisConnection are these tests needed.
     */
    @Test @Ignore 
    public void testSessionSSHClose() {
        SolarisConfiguration config = SolarisTestCommon.createConfiguration();
        testSessionRepetitiveCreation(config);
    }

    @Test(enabled = false)
	private void testSessionRepetitiveCreation(SolarisConfiguration config) {
        try {
            for (int i = 0; i < 100; i++) {
                SolarisConnection connection = new SolarisConnection(config);
                try {
                    // empty on purpose
                } finally {
                    if (connection != null)
                        connection.dispose();
                }
            }
        } catch (Exception ex) {
            AssertJUnit.fail("no exception should be thrown when cyclically calling creating and closing a connection.");
        }
    }
    
    /** 
     * analogical to {@link SolarisConnectionTest#testSessionSSHClose()} 
     * These tests verify Issue #614. Turned off, just in cases of changes to SolarisConnection are these tests needed.
     */
    @Test @Ignore
    public void testSSHPubKeySessionClose() {
        if (!SolarisTestCommon.getProperty("unitTests.SolarisConnection.testSSHPubkeyMode", Boolean.class)) {
            log.info("skipping testSSHPubKeyConnection test, because the resource doesn't support it.");
            return;
        }
        
        SolarisConfiguration config = getConnection().getConfiguration();
        config.setPassphrase(SolarisTestCommon.getProperty("rootPassphrase", GuardedString.class));
        config.setPrivateKey(SolarisTestCommon.getProperty("rootPrivateKey", GuardedString.class));
        config.setConnectionType(ConnectionType.SSHPUBKEY.toString());
        
        testSessionRepetitiveCreation(config);
    }
    
    /**
     * this test needs a 'isVersionLT10' test property defined.
     */
    @Test
    public void testIsVersionLT10() {
        final String testPropName = "unitTests.isVersionLT10";
        boolean isVersionLT10expected = false;
        try {
            isVersionLT10expected = SolarisTestCommon.getProperty(testPropName, Boolean.class);
        } catch (IllegalArgumentException ex) {
            log.info("skipping testIsVersionLT10() because test property '" + testPropName + "' is not defined.");
        }
        
        AssertJUnit.assertEquals(isVersionLT10expected, getConnection().isVersionLT10());
    }

    @Override
    public boolean createGroup() {
        return false;
    }

    @Override
    public int getCreateUsersNumber() {
        return 0;
    }
}
