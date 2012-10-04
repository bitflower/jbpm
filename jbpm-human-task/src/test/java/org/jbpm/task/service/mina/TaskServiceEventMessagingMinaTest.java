/**
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.task.service.mina;

import org.drools.SystemEventListenerFactory;
import org.jbpm.task.MockUserInfo;
import org.jbpm.task.service.TaskClient;
import org.jbpm.task.service.TaskServiceEventMessagingBaseTest;

public class TaskServiceEventMessagingMinaTest extends TaskServiceEventMessagingBaseTest {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        server = new MinaTaskServer(taskService);
        Thread thread = new Thread(server);
        thread.start();
        logger.debug("Waiting for the MinaTask Server to come up");
        while (!server.isRunning()) {

        	Thread.sleep( 50 );
        }
        
        client = new TaskClient(new MinaTaskClientConnector("client 1",
                                new MinaTaskClientHandler(SystemEventListenerFactory.getSystemEventListener())));
        client.connect("127.0.0.1", 9123);
        
        MockUserInfo userInfo = new MockUserInfo();
        userInfo.getEmails().put(users.get("tony"), "tony@domain.com");
        userInfo.getEmails().put(users.get("steve"), "steve@domain.com");

        userInfo.getLanguages().put(users.get("tony"), "en-UK");
        userInfo.getLanguages().put(users.get("steve"), "en-UK");
        taskService.setUserinfo(userInfo);
    }
 
    
}