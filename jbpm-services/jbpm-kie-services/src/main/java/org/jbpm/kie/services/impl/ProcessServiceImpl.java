/*
 * Copyright 2014 JBoss by Red Hat.
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

package org.jbpm.kie.services.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.core.command.runtime.process.SetProcessInstanceVariablesCommand;
import org.drools.core.command.runtime.process.StartProcessCommand;
import org.drools.core.process.instance.WorkItemManager;
import org.jbpm.process.instance.impl.ProcessInstanceImpl;
import org.jbpm.services.api.DeploymentNotFoundException;
import org.jbpm.services.api.DeploymentService;
import org.jbpm.services.api.ProcessInstanceNotFoundException;
import org.jbpm.services.api.ProcessService;
import org.jbpm.services.api.RuntimeDataService;
import org.jbpm.services.api.WorkItemNotFoundException;
import org.jbpm.services.api.model.DeployedUnit;
import org.jbpm.services.api.model.NodeInstanceDesc;
import org.jbpm.services.api.model.ProcessInstanceDesc;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.jbpm.workflow.instance.node.CompositeNodeInstance;
import org.jbpm.workflow.instance.node.EventNodeInstance;
import org.kie.api.command.Command;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.Context;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.manager.RuntimeManager;
import org.kie.api.runtime.process.NodeInstance;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkflowProcessInstance;
import org.kie.internal.process.CorrelationAwareProcessRuntime;
import org.kie.internal.process.CorrelationKey;
import org.kie.internal.runtime.manager.InternalRuntimeManager;
import org.kie.internal.runtime.manager.context.CorrelationKeyContext;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessServiceImpl implements ProcessService, VariablesAware {
	
	private static final Logger logger = LoggerFactory.getLogger(ProcessServiceImpl.class);
	
	private DeploymentService deploymentService;
    private RuntimeDataService dataService;

	public void setDeploymentService(DeploymentService deploymentService) {
		this.deploymentService = deploymentService;
	}

	public void setDataService(RuntimeDataService dataService) {
		this.dataService = dataService;
	}

	@Override
	public Long startProcess(String deploymentId, String processId) {

		return startProcess(deploymentId, processId, new HashMap<String, Object>());
	}

	@Override
	public Long startProcess(String deploymentId, String processId, Map<String, Object> params) {
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(deploymentId);
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + deploymentId);
		}		
		if (!deployedUnit.isActive()) {
			throw new DeploymentNotFoundException("Deployments " + deploymentId + " is not active");
		}
		
		RuntimeManager manager = deployedUnit.getRuntimeManager();
		
		params = process(params, ((InternalRuntimeManager) manager).getEnvironment().getClassLoader());
        RuntimeEngine engine = manager.getRuntimeEngine(ProcessInstanceIdContext.get());
        KieSession ksession = engine.getKieSession();
        ProcessInstance pi = null;
        try {
            pi = ksession.startProcess(processId, params);
            return pi.getId();
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }
	}
	
	@Override
	public Long startProcess(String deploymentId, String processId, CorrelationKey correlationKey) {
		return startProcess(deploymentId, processId, correlationKey, new HashMap<String, Object>());
	}

	@Override
	public Long startProcess(String deploymentId, String processId, CorrelationKey correlationKey, Map<String, Object> params) {
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(deploymentId);
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + deploymentId);
		}		
		if (!deployedUnit.isActive()) {
			throw new DeploymentNotFoundException("Deployments " + deploymentId + " is not active");
		}
		
		RuntimeManager manager = deployedUnit.getRuntimeManager();
		
		params = process(params, ((InternalRuntimeManager) manager).getEnvironment().getClassLoader());
        RuntimeEngine engine = manager.getRuntimeEngine(ProcessInstanceIdContext.get());
        KieSession ksession = engine.getKieSession();
        ProcessInstance pi = null;
        try {
            pi = ((CorrelationAwareProcessRuntime)ksession).startProcess(processId, correlationKey, params);
            return pi.getId();
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }
	}

	@Override
	public void abortProcessInstance(Long processInstanceId) {
		ProcessInstanceDesc piDesc = dataService.getProcessInstanceById(processInstanceId);
		if (piDesc == null) {
			throw new ProcessInstanceNotFoundException("Process instance with id " + processInstanceId + " was not found");
		}
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(piDesc.getDeploymentId());
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + piDesc.getDeploymentId());
		}
		RuntimeManager manager = deployedUnit.getRuntimeManager();
        RuntimeEngine engine = manager.getRuntimeEngine(ProcessInstanceIdContext.get(processInstanceId));
        KieSession ksession = engine.getKieSession();
        try {
            ksession.abortProcessInstance(processInstanceId);
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }
	}

	@Override
	public void abortProcessInstances(List<Long> processInstanceIds) {
		for (long processInstanceId : processInstanceIds) {
			abortProcessInstance(processInstanceId);
		}
	}

	@Override
	public void signalProcessInstance(Long processInstanceId, String signalName, Object event) {
		ProcessInstanceDesc piDesc = dataService.getProcessInstanceById(processInstanceId);
		if (piDesc == null) {
			throw new ProcessInstanceNotFoundException("Process instance with id " + processInstanceId + " was not found");
		}
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(piDesc.getDeploymentId());
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + piDesc.getDeploymentId());
		}
		RuntimeManager manager = deployedUnit.getRuntimeManager();
		event = process(event, ((InternalRuntimeManager) manager).getEnvironment().getClassLoader());
        RuntimeEngine engine = manager.getRuntimeEngine(ProcessInstanceIdContext.get(processInstanceId));
        KieSession ksession = engine.getKieSession();
        try {
            ksession.signalEvent(signalName, event, processInstanceId);
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }
	}

	@Override
	public void signalProcessInstances(List<Long> processInstanceIds, String signalName, Object event) {
		for (Long processInstanceId : processInstanceIds) {
			signalProcessInstance(processInstanceId, signalName, event);
		}

	}

	@Override
	public ProcessInstance getProcessInstance(Long processInstanceId) {
		ProcessInstanceDesc piDesc = dataService.getProcessInstanceById(processInstanceId);
		if (piDesc == null) {
			return null;
		}
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(piDesc.getDeploymentId());
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + piDesc.getDeploymentId());
		}
		RuntimeManager manager = deployedUnit.getRuntimeManager();
        RuntimeEngine engine = manager.getRuntimeEngine(ProcessInstanceIdContext.get(processInstanceId));
        KieSession ksession = engine.getKieSession();
        try {
        	return ksession.getProcessInstance(processInstanceId);
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }
	}
	
	@Override
	public ProcessInstance getProcessInstance(CorrelationKey key) {
		ProcessInstanceDesc piDesc = dataService.getProcessInstanceByCorrelationKey(key);
		if (piDesc == null) {
			return null;
		}
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(piDesc.getDeploymentId());
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + piDesc.getDeploymentId());
		}
		RuntimeManager manager = deployedUnit.getRuntimeManager();
        RuntimeEngine engine = manager.getRuntimeEngine(CorrelationKeyContext.get(key));
        KieSession ksession = engine.getKieSession();
        try {
        	return ((CorrelationAwareProcessRuntime)ksession).getProcessInstance(key);
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }
	}

	@Override
	public void setProcessVariable(Long processInstanceId, String variableId, Object value) {
		ProcessInstanceDesc piDesc = dataService.getProcessInstanceById(processInstanceId);
		if (piDesc == null) {
			throw new ProcessInstanceNotFoundException("Process instance with id " + processInstanceId + " was not found");
		}
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(piDesc.getDeploymentId());
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + piDesc.getDeploymentId());
		}
		RuntimeManager manager = deployedUnit.getRuntimeManager();
		value = process(value, ((InternalRuntimeManager) manager).getEnvironment().getClassLoader());
        RuntimeEngine engine = manager.getRuntimeEngine(ProcessInstanceIdContext.get(processInstanceId));
        KieSession ksession = engine.getKieSession();
        try {
        	ksession.execute(new SetProcessInstanceVariablesCommand(processInstanceId, Collections.singletonMap(variableId, value)));
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }
	}

	@Override
	public void setProcessVariables(Long processInstanceId, Map<String, Object> variables) {
		ProcessInstanceDesc piDesc = dataService.getProcessInstanceById(processInstanceId);
		if (piDesc == null) {
			throw new ProcessInstanceNotFoundException("Process instance with id " + processInstanceId + " was not found");
		}
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(piDesc.getDeploymentId());
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + piDesc.getDeploymentId());
		}
		RuntimeManager manager = deployedUnit.getRuntimeManager();
		variables = process(variables, ((InternalRuntimeManager) manager).getEnvironment().getClassLoader());
        RuntimeEngine engine = manager.getRuntimeEngine(ProcessInstanceIdContext.get(processInstanceId));
        KieSession ksession = engine.getKieSession();
        try {
        	ksession.execute(new SetProcessInstanceVariablesCommand(processInstanceId, variables));
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }
	}

	@Override
	public Object getProcessInstanceVariable(Long processInstanceId, String variableName) {
		ProcessInstanceDesc piDesc = dataService.getProcessInstanceById(processInstanceId);
		if (piDesc == null) {
			throw new ProcessInstanceNotFoundException("Process instance with id " + processInstanceId + " was not found");
		}
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(piDesc.getDeploymentId());
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + piDesc.getDeploymentId());
		}
		RuntimeManager manager = deployedUnit.getRuntimeManager();
        RuntimeEngine engine = manager.getRuntimeEngine(ProcessInstanceIdContext.get(processInstanceId));
        KieSession ksession = engine.getKieSession();
        try {
        	WorkflowProcessInstance pi = (WorkflowProcessInstance) ksession.getProcessInstance(processInstanceId);
        	if (pi == null) {
    			throw new ProcessInstanceNotFoundException("Process instance with id " + processInstanceId + " was not found");
    		}
        	Object variable = pi.getVariable(variableName);
        	return variable;
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }
	}

	@Override
	public Map<String, Object> getProcessInstanceVariables(Long processInstanceId) {
		ProcessInstanceDesc piDesc = dataService.getProcessInstanceById(processInstanceId);
		if (piDesc == null) {
			throw new ProcessInstanceNotFoundException("Process instance with id " + processInstanceId + " was not found");
		}
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(piDesc.getDeploymentId());
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + piDesc.getDeploymentId());
		}
		RuntimeManager manager = deployedUnit.getRuntimeManager();
        RuntimeEngine engine = manager.getRuntimeEngine(ProcessInstanceIdContext.get(processInstanceId));
        KieSession ksession = engine.getKieSession();
        try {
        	WorkflowProcessInstanceImpl pi = (WorkflowProcessInstanceImpl) ksession.getProcessInstance(processInstanceId);
        	
        	return pi.getVariables();
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }
	}

	@Override
	public Collection<String> getAvailableSignals(Long processInstanceId) {
		ProcessInstanceDesc piDesc = dataService.getProcessInstanceById(processInstanceId);
		if (piDesc == null) {
			throw new ProcessInstanceNotFoundException("Process instance with id " + processInstanceId + " was not found");
		}
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(piDesc.getDeploymentId());
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + piDesc.getDeploymentId());
		}
		RuntimeManager manager = deployedUnit.getRuntimeManager();
        RuntimeEngine engine = manager.getRuntimeEngine(ProcessInstanceIdContext.get(processInstanceId));
        KieSession ksession = engine.getKieSession();
        try {
        	ProcessInstance processInstance = ksession.getProcessInstance(processInstanceId);
            Collection<String> activeSignals = new ArrayList<String>();

            if (processInstance != null) {
                ((ProcessInstanceImpl) processInstance)
                        .setProcess(ksession.getKieBase().getProcess(processInstance.getProcessId()));
                Collection<NodeInstance> activeNodes = ((WorkflowProcessInstance) processInstance).getNodeInstances();

                activeSignals.addAll(collectActiveSignals(activeNodes));
            }

            return activeSignals;
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }
	}

	@Override
	public void completeWorkItem(Long id, Map<String, Object> results) {
		NodeInstanceDesc nodeDesc = dataService.getNodeInstanceForWorkItem(id);
		if (nodeDesc == null) {
			throw new WorkItemNotFoundException("Work item with id " + id + " was not found");
		}
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(nodeDesc.getDeploymentId());
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + nodeDesc.getDeploymentId());
		}
		RuntimeManager manager = deployedUnit.getRuntimeManager();
		results = process(results, ((InternalRuntimeManager) manager).getEnvironment().getClassLoader());
        RuntimeEngine engine = manager.getRuntimeEngine(ProcessInstanceIdContext.get(nodeDesc.getProcessInstanceId()));
        KieSession ksession = engine.getKieSession();
        try {
        	ksession.getWorkItemManager().completeWorkItem(id, results);
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }

	}

	@Override
	public void abortWorkItem(Long id) {
		NodeInstanceDesc nodeDesc = dataService.getNodeInstanceForWorkItem(id);
		if (nodeDesc == null) {
			throw new WorkItemNotFoundException("Work item with id " + id + " was not found");
		}
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(nodeDesc.getDeploymentId());
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + nodeDesc.getDeploymentId());
		}
		RuntimeManager manager = deployedUnit.getRuntimeManager();
        RuntimeEngine engine = manager.getRuntimeEngine(ProcessInstanceIdContext.get(nodeDesc.getProcessInstanceId()));
        KieSession ksession = engine.getKieSession();
        try {
        	ksession.getWorkItemManager().abortWorkItem(id);
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }
	}

	@Override
	public WorkItem getWorkItem(Long id) {
		NodeInstanceDesc nodeDesc = dataService.getNodeInstanceForWorkItem(id);
		if (nodeDesc == null) {
			throw new WorkItemNotFoundException("Work item with id " + id + " was not found");
		}
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(nodeDesc.getDeploymentId());
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + nodeDesc.getDeploymentId());
		}
		RuntimeManager manager = deployedUnit.getRuntimeManager();
        RuntimeEngine engine = manager.getRuntimeEngine(ProcessInstanceIdContext.get(nodeDesc.getProcessInstanceId()));
        KieSession ksession = engine.getKieSession();
        try {
        	return ((WorkItemManager)ksession.getWorkItemManager()).getWorkItem(id);
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }
	}

	@Override
	public List<WorkItem> getWorkItemByProcessInstance(Long processInstanceId) {
		ProcessInstanceDesc piDesc = dataService.getProcessInstanceById(processInstanceId);
		if (piDesc == null) {
			throw new ProcessInstanceNotFoundException("Process instance with id " + processInstanceId + " was not found");
		}
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(piDesc.getDeploymentId());
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + piDesc.getDeploymentId());
		}
		RuntimeManager manager = deployedUnit.getRuntimeManager();
        RuntimeEngine engine = manager.getRuntimeEngine(ProcessInstanceIdContext.get(processInstanceId));
        KieSession ksession = engine.getKieSession();
        try {
        	List<WorkItem> workItems = new ArrayList<WorkItem>();
        	
        	Collection<NodeInstanceDesc> nodes = dataService.getProcessInstanceHistoryActive(processInstanceId, null);
        	
        	for (NodeInstanceDesc node : nodes) {
        		if (node.getWorkItemId() != null) {
        			workItems.add(((WorkItemManager)ksession.getWorkItemManager()).getWorkItem(node.getWorkItemId()));
        		}
        	}
        	
        	return workItems;
        	
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }
	}

	@Override
	public <T> T execute(String deploymentId, Command<T> command) {
		Long processInstanceId = CommonUtils.getProcessInstanceId(command);
		logger.debug("Executing command {} with process instance id {} as contextual data", command, processInstanceId);
		
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(deploymentId);
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + deploymentId);
		}
		disallowWhenNotActive(deployedUnit, command);
		
		RuntimeManager manager = deployedUnit.getRuntimeManager();		
        RuntimeEngine engine = manager.getRuntimeEngine(ProcessInstanceIdContext.get(processInstanceId));
        KieSession ksession = engine.getKieSession();
        try {
        	return ksession.execute(command);
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }
	}
	

	@Override
	public <T> T execute(String deploymentId, Context<?> context, Command<T> command) {
		DeployedUnit deployedUnit = deploymentService.getDeployedUnit(deploymentId);
		if (deployedUnit == null) {
			throw new DeploymentNotFoundException("No deployments available for " + deploymentId);
		}
		disallowWhenNotActive(deployedUnit, command);
		
		RuntimeManager manager = deployedUnit.getRuntimeManager();		
        RuntimeEngine engine = manager.getRuntimeEngine(context);
        KieSession ksession = engine.getKieSession();
        try {
        	return ksession.execute(command);
        } finally {
        	disposeRuntimeEngine(manager, engine);
        }
	}
	
	protected void disallowWhenNotActive(DeployedUnit deployedUnit, Command<?> cmd) {
		if (!deployedUnit.isActive() &&
				cmd instanceof StartProcessCommand) {
			throw new DeploymentNotFoundException("Deployments " + deployedUnit.getDeploymentUnit().getIdentifier() + " is not active");
		}
	}
	

	protected Collection<String> collectActiveSignals(
			Collection<NodeInstance> activeNodes) {
		Collection<String> activeNodesComposite = new ArrayList<String>();
		for (NodeInstance nodeInstance : activeNodes) {
			if (nodeInstance instanceof EventNodeInstance) {
				String type = ((EventNodeInstance) nodeInstance).getEventNode().getType();
				if (type != null && !type.startsWith("Message-")) {
					activeNodesComposite.add(type);
				}

			}
			if (nodeInstance instanceof CompositeNodeInstance) {
				Collection<NodeInstance> currentNodeInstances = ((CompositeNodeInstance) nodeInstance).getNodeInstances();

				// recursively check current nodes
				activeNodesComposite
						.addAll(collectActiveSignals(currentNodeInstances));
			}
		}

		return activeNodesComposite;
	}

	@Override
	public <T> T process(T variables, ClassLoader cl) {
		// do nothing here as there is no need to process variables
		return variables;
	}
	
	protected void disposeRuntimeEngine(RuntimeManager manager, RuntimeEngine engine) {
		manager.disposeRuntimeEngine(engine);
	}

}
