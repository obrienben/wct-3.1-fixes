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

import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.webcurator.domain.model.core.BusinessObjectFactory;
import org.webcurator.domain.model.core.HarvestResourceDTO;
import org.webcurator.domain.model.core.HarvestResult;
import org.webcurator.domain.model.core.Seed;
import org.webcurator.domain.model.core.SeedHistory;
import org.webcurator.domain.model.core.Target;
import org.webcurator.domain.model.core.TargetInstance;
import org.webcurator.ui.tools.command.QualityReviewToolCommand;

/**
 * The QualityReviewToolController is responsible for displaying the "menu"
 * page where the user can access the other quality review tools.
 * @author bbeaumont
 */
@Controller
@Scope(BeanDefinition.SCOPE_SINGLETON)
@RequestMapping(path = "/curator/target/quality-review-toc.html")
public class QualityReviewToolController {
	static private Log log = LogFactory.getLog(QualityReviewToolController.class);

	@Autowired
	private QualityReviewToolControllerAttribute attr;

	private BusinessObjectFactory businessObjectFactory = null;

	public QualityReviewToolController() {
		businessObjectFactory = new BusinessObjectFactory();
	}

	@GetMapping
	public ModelAndView getHandle(@RequestParam("targetInstanceOid") String sTargetInstanceOid, @RequestParam("harvestResultId") String sHarvestResultId) throws Exception{
		long targetInstanceOid = -1;
		long harvestResultId = -1;

		try {
			targetInstanceOid =Long.parseLong(sTargetInstanceOid);
			harvestResultId = Long.parseLong(sHarvestResultId);
		}catch (NumberFormatException e){
			log.error("Invalid parameter", e);
			throw e;
		}

		QualityReviewToolCommand cmd = new QualityReviewToolCommand();
		cmd.setTargetInstanceOid(targetInstanceOid);
		cmd.setHarvestResultId(harvestResultId);
		return handle(targetInstanceOid, harvestResultId, cmd);
	}

	@PostMapping
	protected ModelAndView postHandle(QualityReviewToolCommand cmd) throws Exception {
		long targetInstanceOid = cmd.getTargetInstanceOid();
		long harvestResultId = cmd.getHarvestResultId();
		return handle(targetInstanceOid, harvestResultId, cmd);
	}

	private ModelAndView handle(long targetInstanceOid, long harvestResultId, QualityReviewToolCommand cmd) throws Exception {
		TargetInstance ti = attr.targetInstanceManager.getTargetInstance(targetInstanceOid);

		//Do not load fully as this loads ALL resources, regardless of whether they're seeds. Causes OutOfMemory for large harvests.
		HarvestResult result = attr.targetInstanceDao.getHarvestResult(harvestResultId, false);

		// v1.2 - The seeds are now against the Target Instance. We should prefer the seeds
		// in the instances over those on the target.
		Set<Seed> seeds = new LinkedHashSet<Seed>();

		// build the original seeds from the ti
		Iterator<String> originalSeedsIt = ti.getOriginalSeeds().iterator();

		// fetch the seed with the same url as the original seed
		while (originalSeedsIt.hasNext()) {
			String seedUrl = originalSeedsIt.next();
			Iterator<Seed> currentSeedsIt = attr.targetManager.getSeeds(ti).iterator();
			while (currentSeedsIt.hasNext()) {
				Seed seed = currentSeedsIt.next();
				if (seed.getSeed().equals(seedUrl)) {
					seeds.add(seed);
				}
			}

		}

		// If the seed url has been changed on the Target, then look for a match in the seed history.
		// Construct a new seed object for the purposes of the QR tool.
		if(seeds.isEmpty()){
			originalSeedsIt = ti.getOriginalSeeds().iterator();
			while (originalSeedsIt.hasNext()) {
				String seedUrl = originalSeedsIt.next();
				Iterator<SeedHistory> currentSeedHistory = ti.getSeedHistory().iterator();
				while (currentSeedHistory.hasNext()) {
					SeedHistory seedHistoryObj = currentSeedHistory.next();
					if (seedHistoryObj.getSeed().equals(seedUrl)) {
						// Retrieve target for building new seed
						Target existingTarget = null;
						Iterator<Seed> currentSeedsIt = attr.targetManager.getSeeds(ti).iterator();
						while (currentSeedsIt.hasNext()) {
							Seed seed = currentSeedsIt.next();
							existingTarget = seed.getTarget();
							if(existingTarget != null) break;
						}
						// Build new seed
						if(existingTarget != null){
							Seed newSeed = businessObjectFactory.newSeed(existingTarget);
							newSeed.setPrimary(seedHistoryObj.isPrimary());
							newSeed.setSeed(seedHistoryObj.getSeed());
							seeds.add(newSeed);
						}
					}
				}
			}
		}

		// load seedMap list with primary seeds, followed by non-primary seeds.
		List<SeedMapElement> seedMap = new ArrayList<SeedMapElement>();
		for(Seed seed: seeds)
		{
			load(seedMap, seed, true, result);
		}
		for(Seed seed: seeds)
		{
			load(seedMap, seed, false, result);
		}

		ModelAndView mav = new ModelAndView("quality-review-toc", "command", cmd);
		mav.addObject(QualityReviewToolCommand.MDL_SEEDS, seedMap);
		mav.addObject("targetInstanceOid", ti.getOid());
		mav.addObject("archiveUrl", attr.archiveUrl);
		mav.addObject("archiveName", attr.archiveName);
		mav.addObject("archiveAlternative", attr.archiveUrlAlternative);
		mav.addObject("archiveAlternativeName", attr.archiveUrlAlternativeName);
		mav.addObject("webArchiveTarget", attr.webArchiveTarget);
		mav.addObject("targetOid", ti.getTarget().getOid());

		return mav;
	}

	private void load(List<SeedMapElement> seedMap, Seed seed, boolean loadPrimary, HarvestResult result) {
		if (seed.isPrimary() == loadPrimary) {
			SeedMapElement element = new SeedMapElement(seed.getSeed());
			element.setPrimary(loadPrimary);
			if(attr.enableBrowseTool)
			{
				//Encode BrowserURL to base64 chars
				element.setBrowseUrl("curator/tools/browse/" + String.valueOf(result.getOid()) + "/" +Base64.getEncoder().encodeToString(seed.getSeed().getBytes()));
			}

			if(attr.enableAccessTool && attr.harvestResourceUrlMapper != null)
			{
				HarvestResourceDTO hRsr = attr.targetInstanceDao.getHarvestResourceDTO(result.getOid(), seed.getSeed());
				if(hRsr != null)
				{
					element.setAccessUrl(attr.harvestResourceUrlMapper.generateUrl(result, hRsr));
				}
				else
				{
					log.warn("Cannot find seed '" + seed.getSeed() + "' in harvest result ("+result.getOid()+").");
				}
			}
			seedMap.add(element);
		}

	}
}
