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
package org.webcurator.ui.target.controller;

import java.text.NumberFormat;
import java.util.Locale;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.propertyeditors.CustomNumberEditor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.orm.hibernate5.HibernateOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.ServletRequestDataBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.webcurator.auth.AuthorityManager;
import org.webcurator.core.exceptions.WCTRuntimeException;
import org.webcurator.core.coordinator.WctCoordinator;
import org.webcurator.core.scheduler.TargetInstanceManager;
import org.webcurator.domain.model.auth.Privilege;
import org.webcurator.domain.model.core.TargetInstance;
import org.webcurator.common.ui.Constants;
import org.webcurator.ui.target.command.TargetInstanceCommand;
import org.webcurator.ui.util.Tab;
import org.webcurator.ui.util.TabConfig;
import org.webcurator.ui.util.TabbedController;

/**
 * The controller for all target instance tabs.
 * @author nwaight
 */
@Controller
@Scope(BeanDefinition.SCOPE_SINGLETON)
@Lazy(false)
@RequestMapping("/curator/target/target-instance.html")
public class TabbedTargetInstanceController extends TabbedController {
    /** The manager for target instance data access. */
    @Autowired
    TargetInstanceManager targetInstanceManager;
    /** The harvest coordinator to use to update the profile overrides. */
    @Autowired
    WctCoordinator wctCoordinator;
    /** The authority manager used to perform security checks */
    @Autowired
    AuthorityManager authorityManager;
    /** The queue controller */
    @Autowired
    private QueueController queueController;
    /** The message source */
    @Autowired
    private MessageSource messageSource;

    private static Log log = LogFactory.getLog(TabbedTargetInstanceController.class);

    @Autowired
    private ApplicationContext context;

    @PostConstruct
    protected void init() {
        setDefaultCommandClass(TargetInstanceCommand.class);
        setTabConfig((TabConfig) context.getBean("targetInstanceTabConfig"));
    }


    @Override
    public void initBinder(HttpServletRequest request, ServletRequestDataBinder binder) throws Exception {
    	super.initBinder(request,binder);
    	// enable null values for long and float fields
        NumberFormat nf = NumberFormat.getInstance(request.getLocale());
        binder.registerCustomEditor(java.lang.Long.class, new CustomNumberEditor(java.lang.Long.class, nf, true));
        binder.registerCustomEditor(java.lang.Float.class, new CustomNumberEditor(java.lang.Float.class, nf, true));
    }


	@Override
	@RequestMapping(method = {RequestMethod.POST, RequestMethod.GET})
	public ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {
		return super.handleRequestInternal(request, response);
	}

	@Override
	protected void switchToEditMode(HttpServletRequest req) {
		req.getSession().setAttribute(TargetInstanceCommand.SESSION_MODE, true);
        req.getSession().setAttribute(Constants.GBL_SESS_EDIT_MODE, true);
	};

    @Override
    protected ModelAndView processSave(Tab currentTab, HttpServletRequest req, HttpServletResponse res, Object comm,
                                       BindingResult bindingResult) {
        TargetInstance ti = (TargetInstance) req.getSession().getAttribute(TargetInstanceCommand.SESSION_TI);

        try {
			targetInstanceManager.save(ti);
			if (ti.getState().equals(TargetInstance.STATE_PAUSED)) {
				// Send the updated profile to the harvester.
				wctCoordinator.updateProfileOverrides(ti);
			}
		}
        catch (HibernateOptimisticLockingFailureException e) {
        	if (log.isErrorEnabled()) {
        		log.error("Failed to save target instance. " + e.getMessage(), e);
        	}
        	throw new WCTRuntimeException("The target instance " + ti.getJobName() + " could not be saved as it has been modified by another user or the system.");
		}
        finally {
	    	unbindContext(req);
        }

		try {
			ModelAndView mav = queueController.showForm(req ,res, bindingResult);
			mav.addObject(Constants.GBL_MESSAGES, messageSource.getMessage("targetInstance.saved", new Object[] { ti.getTarget().getName() + " ("+ ti.getOid().toString() + ")" }, Locale.getDefault()));
	        return mav;
		}
		catch(Exception ex) {
			throw new WCTRuntimeException("Failed to load queue controller", ex);
		}
    }

    @Override
    protected ModelAndView processCancel(Tab currentTab, HttpServletRequest req, HttpServletResponse res, Object comm,
                                         BindingResult bindingResult) {

    	unbindContext(req);

        return new ModelAndView("redirect:/curator/target/queue.html");
    }

    @Override
    protected ModelAndView showForm(HttpServletRequest req, HttpServletResponse res, Object comm,
                                    BindingResult bindingResult) throws Exception {
        // return std model and view to show form on a get.
    	return processInitial(req, res, comm, bindingResult);
    }

    @Override
    protected ModelAndView processInitial(HttpServletRequest req, HttpServletResponse res, Object comm,
                                          BindingResult bindingResult) {
        // Load the handler the first time

    	// Clear out the session attributes.
    	unbindContext(req);

    	// If the command object does not exist, or does not have the ID, go
    	// to the queue view.
        TargetInstanceCommand cmd = (TargetInstanceCommand) comm;
    	if (cmd == null || cmd.getTargetInstanceId() == null) {
    		return new ModelAndView("redirect:/curator/target/queue.html");
    	}

    	// Load the target instance from the database.
        TargetInstance ti = targetInstanceManager.getTargetInstance(cmd.getTargetInstanceId(), true);

        // Populate the command object from the Target Instance.
        TargetInstanceCommand populatedCommand = new TargetInstanceCommand(ti);
        populatedCommand.setCmd(cmd.getCmd());

        Boolean editMode = false;
    	if (TargetInstanceCommand.ACTION_EDIT.equals(cmd.getCmd())) {
    		editMode = true;
    	}
		if (!authorityManager.hasAtLeastOnePrivilege(ti, new String[] {Privilege.MANAGE_TARGET_INSTANCES, Privilege.MANAGE_WEB_HARVESTER})) {
			editMode = false;
		}

		HttpSession session = req.getSession();
		// If we've come from the Harvest History page of some
		// other Target Instance then save the keys required to
		// construct a return link in session data..
		String tiOid = req.getParameter("ti_oid");
		if (tiOid != null) {
			session.setAttribute(TargetInstanceCommand.SESSION_HH_TI_OID, tiOid);
		} else {
			session.setAttribute(TargetInstanceCommand.SESSION_HH_TI_OID, "");
		}
		String harvestResultId = req.getParameter("harvestResultId");
		if (harvestResultId != null) {
			session.setAttribute(TargetInstanceCommand.SESSION_HH_HR_OID, harvestResultId);
		} else {
			session.setAttribute(TargetInstanceCommand.SESSION_HH_HR_OID, "");
		}

		session.setAttribute(TargetInstanceCommand.SESSION_TI, ti);
        session.setAttribute(TargetInstanceCommand.SESSION_MODE, editMode);
        session.setAttribute(Constants.GBL_SESS_EDIT_MODE, editMode);


		//Code to display edit button (in layouts\tabbed-new.jsp) driven by session vars
        session.setAttribute(Constants.GBL_SESS_CAN_EDIT, false);
		if (!editMode)
		{
			String[] privs = {Privilege.MANAGE_TARGET_INSTANCES,Privilege.MANAGE_WEB_HARVESTER};
			if (authorityManager.hasAtLeastOnePrivilege(ti,privs))
			{
				session.setAttribute(Constants.GBL_SESS_CAN_EDIT, true);
			}
		}



        // Determine which tab to show.
        String destTab = (String) req.getParameter("init_tab");
        if(destTab == null) { destTab = "GENERAL"; }

        // Go to the first tab.
        Tab generalTab = getTabConfig().getTabByID(destTab);
        TabbedModelAndView mav = generalTab.getTabHandler().preProcessNextTab(this, generalTab, req, res, populatedCommand, bindingResult);
        mav.getTabStatus().setCurrentTab(generalTab);
        return mav;
    }

	private void unbindContext(HttpServletRequest req) {
		req.getSession().removeAttribute(TargetInstanceCommand.SESSION_TI);
    	req.getSession().removeAttribute(TargetInstanceCommand.SESSION_MODE);
    	req.getSession().removeAttribute(Constants.GBL_SESS_EDIT_MODE);
    	req.getSession().removeAttribute(Constants.GBL_SESS_CAN_EDIT);
	}

    /**
     * @param targetInstanceManager The targetInstanceManager to set.
     */
    public void setTargetInstanceManager(TargetInstanceManager targetInstanceManager) {
        this.targetInstanceManager = targetInstanceManager;
    }

	/**
	 * @param wctCoordinator the wctCoordinator to set
	 */
	public void setHarvestCoordinator(WctCoordinator wctCoordinator) {
		this.wctCoordinator = wctCoordinator;
	}

	/**
	 * @param authorityManager the authority manager to set.
	 */
	public void setAuthorityManager(AuthorityManager authorityManager) {
		this.authorityManager = authorityManager;
	}

	/**
	 * @param queueController The queueController to set.
	 */
	public void setQueueController(QueueController queueController) {
		this.queueController = queueController;
	}

	/**
	 * @param messageSource The messageSource to set.
	 */
	public void setMessageSource(MessageSource messageSource) {
		this.messageSource = messageSource;
	}
}
