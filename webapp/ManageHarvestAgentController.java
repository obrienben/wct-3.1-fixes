/*
 *  Copyright 2006 The National Library of New Zealand
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.webcurator.ui.agent.controller;

import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.webcurator.core.exceptions.WCTRuntimeException;
import org.webcurator.core.coordinator.WctCoordinator;
import org.webcurator.domain.model.core.harvester.agent.HarvestAgentStatusDTO;
import org.webcurator.ui.agent.command.ManageHarvestAgentCommand;
import org.webcurator.common.ui.Constants;

/**
 * The controller for displaying harvest agent data.
 * @author nwaight
 */
@Controller
@Scope(BeanDefinition.SCOPE_SINGLETON)
@Lazy(false)
public class ManageHarvestAgentController {
    /** The class the coordinates the harvest agents and holds their states. */
    @Autowired
    private WctCoordinator wctCoordinator;
    /** the logger. */
    private Log log;

    /** Default constructor. */
    public ManageHarvestAgentController() {
        super();
        log = LogFactory.getLog(getClass());
    }

    @RequestMapping(method = RequestMethod.GET, path = "/curator/agent/harvest-agent.html")
    protected ModelAndView showForm() throws Exception {
        // Show the initial manage harvests page.
        ModelAndView mav = processAgentSummary();

        ManageHarvestAgentCommand command = new ManageHarvestAgentCommand();

        populateCommand(command);
        mav.addObject(Constants.GBL_CMD_DATA, command);

        return mav;
    }

    @RequestMapping(method = RequestMethod.POST, path = "/curator/agent/harvest-agent.html")
    protected ModelAndView processFormSubmission(@ModelAttribute ManageHarvestAgentCommand command) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("process command " + command.getActionCmd());
        }

        ModelAndView mav = new ModelAndView();
        if (command != null && command.getActionCmd() != null) {
            if (command.getActionCmd().equals(ManageHarvestAgentCommand.ACTION_AGENT)) {
                mav = processAgentDetails(command);
            }
            else if (command.getActionCmd().equals(ManageHarvestAgentCommand.ACTION_SUMMARY)) {
                mav = processAgentSummary();
            }
            else if (command.getActionCmd().equals(ManageHarvestAgentCommand.ACTION_HOME)) {
                mav =  new ModelAndView("redirect:/" + Constants.CNTRL_HOME);
            }
            else if (command.getActionCmd().equals(ManageHarvestAgentCommand.ACTION_PAUSE)) {
                mav =  processPauseAll();
            }
            else if (command.getActionCmd().equals(ManageHarvestAgentCommand.ACTION_RESUME)) {
                mav =  processResumeAll();
            }
            else if (command.getActionCmd().equals(ManageHarvestAgentCommand.ACTION_PAUSEQ)) {
                mav =  processPauseQueue();
            }
            else if (command.getActionCmd().equals(ManageHarvestAgentCommand.ACTION_RESUMEQ)) {
                mav =  processResumeQueue();
            }
            else if (command.getActionCmd().equals(ManageHarvestAgentCommand.ACTION_PAUSE_AGENT)) {
                mav =  processPauseAgent(command);
            }
            else if (command.getActionCmd().equals(ManageHarvestAgentCommand.ACTION_RESUME_AGENT)) {
                mav =  processResumeAgent(command);
            }
            else if (command.getActionCmd().equals(ManageHarvestAgentCommand.ACTION_OPTIMIZE_DISABLE)) {
                mav =  processChangeOptimization(false);
            }
            else if (command.getActionCmd().equals(ManageHarvestAgentCommand.ACTION_OPTIMIZE_ENABLE)) {
                mav =  processChangeOptimization(true);
            }
            else {
                throw new WCTRuntimeException("Unknown command " + command.getActionCmd() + " recieved.");
            }

            populateCommand(command);
            mav.addObject(Constants.GBL_CMD_DATA, command);

            return mav;
        }

        throw new WCTRuntimeException("Unknown command recieved.");
    }

    /**
     * @param aHarvestCoordinator The wctCoordinator to set.
     */
    public void setHarvestCoordinator(WctCoordinator aHarvestCoordinator) {
        this.wctCoordinator = aHarvestCoordinator;
    }

    /**
     * process the Show Agent Details action.
     */
    private ModelAndView processAgentDetails(ManageHarvestAgentCommand aCmd) {
        ModelAndView mav = new ModelAndView();

        HashMap agents = wctCoordinator.getHarvestAgents();
        HarvestAgentStatusDTO status = (HarvestAgentStatusDTO) agents.get(aCmd.getAgentName());

        mav.addObject(ManageHarvestAgentCommand.MDL_HARVEST_AGENT, status);
        mav.setViewName(Constants.VIEW_AGENT);

        return mav;
    }

    /**
     * process the Show Agent Summary action.
     */
    private ModelAndView processAgentSummary() {
        ModelAndView mav = getDefaultModelAndView();
        return mav;
    }

    /**
     * process the pause all running harvests action.
     */
    private ModelAndView processPauseAll() {
        wctCoordinator.pauseAll();
        ModelAndView mav = getDefaultModelAndView();
        return mav;
    }

    /**
     * process the resume all paused harvests action.
     */
    private ModelAndView processResumeAll() {
        wctCoordinator.resumeAll();
        ModelAndView mav = getDefaultModelAndView();
        return mav;
    }

    /**
     * process the halt Scheduled and Queued harvests action.
     */
    private ModelAndView processPauseQueue() {
        wctCoordinator.pauseQueue();
        ModelAndView mav = getDefaultModelAndView();
        return mav;
    }

    /**
     * process the resume Scheduled and Queued harvests action.
     */
    private ModelAndView processResumeQueue() {
        wctCoordinator.resumeQueue();
        ModelAndView mav = getDefaultModelAndView();
        return mav;
    }

    /**
     * process the halt Scheduled and Queued harvests action.
     */
    private ModelAndView processPauseAgent(ManageHarvestAgentCommand aCmd) {
        wctCoordinator.pauseAgent(aCmd.getAgentName());
        ModelAndView mav = getDefaultModelAndView();
        return mav;
    }

    /**
     * process the resume Scheduled and Queued harvests action.
     */
    private ModelAndView processResumeAgent(ManageHarvestAgentCommand aCmd) {
        wctCoordinator.resumeAgent(aCmd.getAgentName());
        ModelAndView mav = getDefaultModelAndView();
        return mav;
    }


    private ModelAndView processChangeOptimization(boolean optimizationEnabled) {
        wctCoordinator.setHarvestOptimizationEnabled(optimizationEnabled);
        ModelAndView mav = getDefaultModelAndView();
        return mav;
    }

    private ModelAndView getDefaultModelAndView() {
        ModelAndView mav = new ModelAndView();
        mav.addObject(ManageHarvestAgentCommand.MDL_HARVEST_AGENTS, wctCoordinator.getHarvestAgents());
        mav.setViewName(Constants.VIEW_MNG_AGENTS);
        return mav;
    }

    private void populateCommand(ManageHarvestAgentCommand command) {
        command.setQueuePaused(wctCoordinator.isQueuePaused());
        command.setOptimizationEnabled(wctCoordinator.isHarvestOptimizationEnabled());
        command.setOptimizationLookaheadHours(wctCoordinator.getHarvestOptimizationLookAheadHours());
    }
}
