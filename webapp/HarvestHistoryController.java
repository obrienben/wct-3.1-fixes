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
package org.webcurator.ui.tools.controller;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.webcurator.core.scheduler.TargetInstanceManager;
import org.webcurator.domain.TargetInstanceDAO;
import org.webcurator.domain.model.core.HarvestResult;
import org.webcurator.domain.model.core.TargetInstance;
import org.webcurator.domain.model.dto.HarvestHistoryDTO;
import org.webcurator.ui.target.command.TargetInstanceCommand;
import org.webcurator.ui.tools.command.HarvestHistoryCommand;

/**
 * Controller for the HarvestHistory QR tool.
 *
 * @author beaumontb
 */
//@Scope(BeanDefinition.SCOPE_SINGLETON)
//@Lazy(false)
@Controller
public class HarvestHistoryController {
    @Autowired
    private TargetInstanceManager targetInstanceManager;
    @Autowired
    private TargetInstanceDAO targetInstanceDAO;

    public HarvestHistoryController() {
    }

    @RequestMapping(path = "/curator/tools/harvest-history.html", method = {RequestMethod.POST, RequestMethod.GET})
    protected ModelAndView handle(HttpServletRequest request, HarvestHistoryCommand cmd) throws Exception {
        TargetInstance ti = targetInstanceManager.getTargetInstance(cmd.getTargetInstanceOid());
        HarvestResult hr = targetInstanceDAO.getHarvestResult(cmd.getHarvestResultId());

        List<HarvestHistoryDTO> history = targetInstanceManager.getHarvestHistory(ti.getTarget().getOid());

        //Set the session target instance because it is overwritten by the TargetInstanceController
        //upon viewing any history item.  The URL query string parameters are ignored.
        request.getSession().setAttribute(TargetInstanceCommand.SESSION_TI, ti);
        ModelAndView mav = new ModelAndView("harvest-history");
        mav.addObject("history", history);
        mav.addObject("ti_oid", cmd.getTargetInstanceOid());
        mav.addObject("harvestResultId", cmd.getHarvestResultId());
        mav.addObject("harvestNumber", hr.getHarvestNumber());
        return mav;
    }

    public void setTargetInstanceManager(TargetInstanceManager targetInstanceManager) {
        this.targetInstanceManager = targetInstanceManager;
    }

}
