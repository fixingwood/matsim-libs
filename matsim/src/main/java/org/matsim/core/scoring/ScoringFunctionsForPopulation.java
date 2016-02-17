/* *********************************************************************** *
 * project: org.matsim.*
 * ScoringFunctionsForPopulation.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.core.scoring;

import com.google.inject.Inject;
import gnu.trove.TDoubleCollection;
import gnu.trove.iterator.TDoubleIterator;
import gnu.trove.list.array.TDoubleArrayList;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.PersonStuckEvent;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.internal.HasPersonId;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.population.PlanImpl;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.rx.ObservableUtils;
import rx.Notification;
import rx.Observable;
import rx.Observer;
import rx.functions.Action1;
import rx.subjects.PublishSubject;

/**
 * This class helps EventsToScore by keeping ScoringFunctions for the entire Population - one per Person -, and dispatching Activities
 * and Legs to the ScoringFunctions. It also gives out the ScoringFunctions, so they can be given other events by EventsToScore.
 * It is not independently useful. Please do not make public.
 * 
 * @author michaz
 *
 */
class ScoringFunctionsForPopulation implements BasicEventHandler, ExperiencedPlansService {

	private final static Logger log = Logger.getLogger(ScoringFunctionsForPopulation.class);
	private final PlansConfigGroup plansConfigGroup;
	private final Population population;
	private final ScoringFunctionFactory scoringFunctionFactory;
	private Network network;

	/*
	 * Replaced TreeMaps with (Linked)HashMaps since they should perform much better. For 'partialScores'
	 * a LinkedHashMap is used to ensure that agents are written in a deterministic order to the output files.
	 * 
	 * Replaced List with TDoubleCollection (TDoubleArrayList) in the partialScores map. This collection allows
	 * storing primitive objects, i.e. its double entries don't have to be wrapped into Double objects which
	 * should be faster and reduce the memory overhead.
	 * 
	 * cdobler, nov'15  
	 */
	private final Map<Id<Person>, ScoringFunction> agentScorers = new HashMap<>();
	private final Map<Id<Person>, Plan> agentRecords = new HashMap<>();
	private final Map<Id<Person>, TDoubleCollection> partialScores = new LinkedHashMap<>();
	private final AtomicReference<Throwable> exception = new AtomicReference<>();

	@Inject
	ScoringFunctionsForPopulation(EventsManager eventsManager, EventsToActivities eventsToActivities, EventsToLegs eventsToLegs,
			PlansConfigGroup plansConfigGroup, Network network, Population population, ScoringFunctionFactory scoringFunctionFactory) {
		this.plansConfigGroup = plansConfigGroup;
		this.network = network;
		this.population = population;
		this.scoringFunctionFactory = scoringFunctionFactory;
		reset();

		// Merge three streams of different type into one and react on it.
		// Otherwise, I would have to synchronize on the three scoreXX methods,
		// because the three upstream producers can run on different threads.
		// And I want to start to stop using low-level concurrency constructs
		// and explore more high-level ones.
		Observable<Event> events = ObservableUtils.fromEventsManager(eventsManager);
		Observable<PersonExperiencedActivity> activities = ObservableUtils.fromEventsToActivities(eventsToActivities);
		Observable<PersonExperiencedLeg> legs = ObservableUtils.fromEventsToLegs(eventsToLegs);
		Observable<Object> allEvents = Observable.merge(events, activities, legs);
		allEvents.subscribe(new Observer<Object>() {
			@Override
			public void onNext(Object o) {
				if (o instanceof HasPersonId) {
					scorePersonEvent((HasPersonId) o);
				} else if (o instanceof PersonExperiencedActivity) {
					scoreExperiencedActivity((PersonExperiencedActivity) o);
				} else if (o instanceof PersonExperiencedLeg) {
					scoreExperiencedLeg((PersonExperiencedLeg) o);
				}
			}

			@Override
			public void onCompleted() {

			}

			@Override
			public void onError(Throwable e) {
				exception.set(e);
			}
		});
		eventsManager.addHandler(this); // only so that I receive the reset() call!
	}

	private void scorePersonEvent(HasPersonId o) {
		// this is for the stuff that is directly based on events.
		// note that this passes on _all_ person events, even those which are aggregated into legs and activities.
		// for the time being, not all PersonEvents may "implement HasPersonId".
		// link enter/leave events are NOT passed on, for performance reasons.
		// kai/dominik, dec'12

		ScoringFunction scoringFunction = getScoringFunctionForAgent(o.getPersonId());
		if (scoringFunction != null) {
			if ( o instanceof PersonStuckEvent) {
				scoringFunction.agentStuck(((Event) o).getTime()) ;
			} else if ( o instanceof PersonMoneyEvent) {
				scoringFunction.addMoney(((PersonMoneyEvent) o).getAmount()) ;
			} else {
				scoringFunction.handleEvent((Event) o) ;
			}
		}
	}

	private void scoreExperiencedLeg(PersonExperiencedLeg o) {
		Id<Person> agentId = o.getAgentId();
		Leg leg = o.getLeg();
		ScoringFunction scoringFunction = ScoringFunctionsForPopulation.this.getScoringFunctionForAgent(agentId);
		if (scoringFunction != null) {
			scoringFunction.handleLeg(leg);
			agentRecords.get(agentId).addLeg(leg);
			TDoubleCollection partialScoresForAgent = ScoringFunctionsForPopulation.this.partialScores.get(agentId);
			partialScoresForAgent.add(scoringFunction.getScore());
		}
	}

	private void scoreExperiencedActivity(PersonExperiencedActivity o) {
		Id<Person> agentId = o.getAgentId();
		Activity activity = o.getActivity();
		ScoringFunction scoringFunction = ScoringFunctionsForPopulation.this.getScoringFunctionForAgent(agentId);
		if (scoringFunction != null) {
			scoringFunction.handleActivity(activity);
			ScoringFunctionsForPopulation.this.agentRecords.get(agentId).addActivity(activity);
			TDoubleCollection partialScoresForAgent = ScoringFunctionsForPopulation.this.partialScores.get(agentId);
			partialScoresForAgent.add(scoringFunction.getScore());
		}
	}

	private void reset() {
		for (Person person : population.getPersons().values()) {
			ScoringFunction data = scoringFunctionFactory.createNewScoringFunction(person);
			this.agentScorers.put(person.getId(), data);
			this.agentRecords.put(person.getId(), new PlanImpl());
			this.partialScores.put(person.getId(), new TDoubleArrayList());
		}
	}

	/**
	 * Returns the scoring function for the specified agent. If the agent
	 * already has a scoring function, that one is returned. If the agent does
	 * not yet have a scoring function, a new one is created and assigned to the
	 * agent and returned.
	 *
	 * @param agentId
	 *            The id of the agent the scoring function is requested for.
	 * @return The scoring function for the specified agent.
	 */
	public ScoringFunction getScoringFunctionForAgent(final Id<Person> agentId) {
		return this.agentScorers.get(agentId);
	}

	@Override
	public Map<Id<Person>, Plan> getAgentRecords() {
		return this.agentRecords;
	}

	public void finishScoringFunctions() {
		// Rethrow an exception in a scoring function (user code) if there was one.
		if (exception.get() != null) {
			Throwable throwable = exception.get();
			if (throwable instanceof RuntimeException) {
				throw ((RuntimeException) throwable);
			} else {
				throw new RuntimeException(throwable);
			}
		}
		for (ScoringFunction sf : this.agentScorers.values()) {
			sf.finish();
		}
		for (Entry<Id<Person>, TDoubleCollection> entry : this.partialScores.entrySet()) {
			entry.getValue().add(this.getScoringFunctionForAgent(entry.getKey()).getScore());
		}
	}

	public void writeExperiencedPlans(String iterationFilename) {
		Population tmpPop = PopulationUtils.createPopulation(plansConfigGroup, network);
		for (Entry<Id<Person>, Plan> entry : this.agentRecords.entrySet()) {
			Person person = PopulationUtils.createPerson(entry.getKey());
			Plan plan = entry.getValue();
			plan.setScore(getScoringFunctionForAgent(person.getId()).getScore());
			person.addPlan(plan);
			tmpPop.addPerson(person);
			if (plan.getScore().isNaN()) {
				log.warn("score is NaN; plan:" + plan.toString());
			}
		}
		new PopulationWriter(tmpPop, network).write(iterationFilename + ".xml.gz");
		// I removed the "V5" here in the assumption that it is better to move along with future format changes.  If this is 
		// undesired, please change back but could you then please also add a comment why you prefer this.  Thanks.
		// kai, jan'16

		try ( BufferedWriter out = IOUtils.getBufferedWriter(iterationFilename + "_scores.xml.gz") ) {
			for (Entry<Id<Person>, TDoubleCollection> entry : this.partialScores.entrySet()) {
				out.write(entry.getKey().toString());
				TDoubleIterator iterator = entry.getValue().iterator();
				while (iterator.hasNext()) {
					out.write('\t' + String.valueOf(iterator.next()));					
				}
				out.newLine();
			}
			out.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void handleEvent(Event event) {
	}

	@Override
	public void reset(int iteration) {
		reset();
	}
}