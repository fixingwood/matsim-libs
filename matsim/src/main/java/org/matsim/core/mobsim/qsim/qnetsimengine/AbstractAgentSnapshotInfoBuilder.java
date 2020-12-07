/* *********************************************************************** *
 * project: org.matsim.*
 * AbstractAgentSnapshotInfoBuilder
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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
package org.matsim.core.mobsim.qsim.qnetsimengine;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.*;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.framework.MobsimDriverAgent;
import org.matsim.core.mobsim.framework.PassengerAgent;
import org.matsim.core.mobsim.qsim.interfaces.MobsimVehicle;
import org.matsim.core.mobsim.qsim.pt.TransitDriverAgent;
import org.matsim.core.mobsim.qsim.qnetsimengine.QueueWithBuffer.Hole;
import org.matsim.vis.snapshotwriters.AgentSnapshotInfo;
import org.matsim.vis.snapshotwriters.AgentSnapshotInfo.AgentState;
import org.matsim.vis.snapshotwriters.AgentSnapshotInfoFactory;
import org.matsim.vis.snapshotwriters.SnapshotLinkWidthCalculator;
import org.matsim.vis.snapshotwriters.VisVehicle;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;


/**
 * @author dgrether
 * @author knagel
 *
 */
abstract class AbstractAgentSnapshotInfoBuilder {
	private static final Logger log = Logger.getLogger(AbstractAgentSnapshotInfoBuilder.class);

	private final AgentSnapshotInfoFactory snapshotInfoFactory;
	private final Scenario scenario;

	AbstractAgentSnapshotInfoBuilder(Scenario sc, SnapshotLinkWidthCalculator linkWidthCalculator) {
		this.snapshotInfoFactory = new AgentSnapshotInfoFactory(linkWidthCalculator);
		this.scenario = sc;
	}

	private static double computeHolePositionAndReturnDistance(double freespeedTraveltime, Hole hole, double now, double curvedLength) {
		double remainingTravelTime = hole.getEarliestLinkExitTime() - now;
		return remainingTravelTime / freespeedTraveltime * curvedLength;
	}

	/**
	 * Put the vehicles from the waiting list in positions. Their actual position doesn't matter, PositionInfo provides a
	 * constructor for handling this situation.
	 */
	public final int positionVehiclesFromWaitingList(final Collection<AgentSnapshotInfo> positions,
													 final Link link, int cnt2, final Queue<QVehicle> waitingList) {
		for (QVehicle veh : waitingList) {
			var peopleInVehicle = VisUtils.getPeopleInVehicle(veh);
			for (var passenger : peopleInVehicle) {
				cnt2++ ;
				var passengerPosition = snapshotInfoFactory.getAgentSnapshotInfoBuilder()
						.setPersonId(passenger.getId())
						.setVehicleId(veh.getId())
						.setLinkId(link.getId())
						.setFromCoord(link.getFromNode().getCoord())
						.setToCoord(link.getToNode().getCoord())
						.setDistanceOnLink(link.getLength() * 0.9)
						.setLane(cnt2)
						.setLinkLength(link.getLength())
						.setAgentState(getAgentState(passenger))
						.build();
				positions.add(passengerPosition);
			}
		}
		return cnt2;
	}

	private AgentState getAgentState(Identifiable<Person> identifiable) {

		// I don't know whether I have gotten this right, but I think this is tested in every case in every method
		var marker = getMarkerFromAttributes(identifiable.getId());
		if (marker != null) return AgentState.MARKER;

		// these are the regular agent states
		if (identifiable instanceof TransitDriverAgent) return AgentState.TRANSIT_DRIVER;
		if (identifiable instanceof MobsimDriverAgent && ((MobsimDriverAgent) identifiable).getMode().equals(TransportMode.car))
			return AgentState.PERSON_DRIVING_CAR;

		// old tests keep them here, since I'm unsure whether the other ones work
		//if (identifiable.getId().toString().startsWith("pt")) return AgentState.TRANSIT_DRIVER;
		//if (isFirst) return AgentState.PERSON_DRIVING_CAR;

		// we don't know. Set other mode
		return AgentState.PERSON_OTHER_MODE;
	}

	private Object getMarkerFromAttributes(Id<Person> id) {
		var person = scenario.getPopulation().getPersons().get(id);
		return person != null ? person.getAttributes().getAttribute(AgentSnapshotInfo.marker) : null;
	}

	private AgentState getAgentStateForActivity(Id<Person> id) {

		// I don't know whether I have gotten this right, but I think this is tested in every case in every method
		var marker = getMarkerFromAttributes(id);
		if (marker != null) return AgentState.MARKER;

		return AgentState.PERSON_AT_ACTIVITY;
	}

	public final int positionAgentsInActivities(final Collection<AgentSnapshotInfo> positions, Link link,
												Collection<MobsimAgent> agentsInActivities, int cnt2) {
		for (MobsimAgent agent : agentsInActivities) {

			var position = snapshotInfoFactory.getAgentSnapshotInfoBuilder()
					.setPersonId(agent.getId())
					.setLinkId(link.getId())
					.setFromCoord(link.getFromNode().getCoord())
					.setToCoord(link.getToNode().getCoord())
					.setDistanceOnLink(link.getLength() * 0.9)
					.setLinkLength(link.getLength())
					.setLane(cnt2)
					.setAgentState(getAgentStateForActivity(agent.getId()))
					.build();
			positions.add(position);


			/*AgentSnapshotInfo agInfo = snapshotInfoFactory.createAgentSnapshotInfo(pa.getId(), link, 0.9*link.getLength(), cnt2) ;
			agInfo.setAgentState( AgentState.PERSON_AT_ACTIVITY ) ;
			final Person person = scenario.getPopulation().getPersons().get( pa.getId() );
			if ( person != null ) {
				if ( person.getAttributes().getAttribute( AgentSnapshotInfo.marker ) != null ){
					agInfo.setAgentState( AgentState.MARKER );
				}
			}

			positions.add(agInfo) ;

			 */
			cnt2++ ;
		}
		return cnt2;
	}

	/**
	 * Put the transit vehicles from the transit stop list in positions.
	 */
	public final int positionVehiclesFromTransitStop(final Collection<AgentSnapshotInfo> positions, Link link,
													 Queue<QVehicle> transitVehicleStopQueue, int cnt2) {
		if (transitVehicleStopQueue.size() > 0) {
			for (QVehicle veh : transitVehicleStopQueue) {
				var peopleInVehicle = VisUtils.getPeopleInVehicle(veh);
				for (Identifiable<Person> identifiable : peopleInVehicle) {

					var passengerPosition = snapshotInfoFactory.getAgentSnapshotInfoBuilder()
							.setPersonId(identifiable.getId())
							.setVehicleId(veh.getId())
							.setLinkId(link.getId())
							.setLinkLength(link.getLength())
							.setDistanceOnLink(link.getLength() * 0.9)
							.setLane(cnt2)
							.setAgentState(getAgentState(identifiable))
							.build();
					positions.add(passengerPosition);
				}

				/*
				boolean last = false ;
				cnt2 += peopleInVehicle.size() ;
				for ( var it = peopleInVehicle.listIterator( peopleInVehicle.size() ) ; it.hasPrevious(); ) {
					var passenger = it.previous();
					if ( !it.hasPrevious() ) {
						last = true ;
					}
					AgentSnapshotInfo passengerPosition = snapshotInfoFactory.createAgentSnapshotInfo(passenger.getId(), link, 0.9*link.getLength(), cnt2); // for the time being, same position as facilities
					if ( passenger.getId().toString().startsWith("pt")) {
						passengerPosition.setAgentState(AgentState.TRANSIT_DRIVER);
					} else if (last) {
						passengerPosition.setAgentState(AgentState.PERSON_DRIVING_CAR);
					} else {
						passengerPosition.setAgentState(AgentState.PERSON_OTHER_MODE);
					}
					positions.add(passengerPosition);
					cnt2-- ;
				}
				cnt2 += peopleInVehicle.size() ; // setting it correctly for the next output

				 */
				cnt2 += peopleInVehicle.size();
			}

		}
		return cnt2;
	}

	private static int wrnCnt = 0;

	public final void positionAgentGivenDistanceFromFNode(final Collection<AgentSnapshotInfo> positions, Coord startCoord, Coord endCoord,
														  double lengthOfCurve, QVehicle veh, double distanceFromFromNode,
														  int lane, double speedValueBetweenZeroAndOne) {
		// I think that the main reason why this exists as public method is that AssignmentEmulatingQLane wants to use it directly.
		// The reason for this, in return, is that positionVehiclesAlongLine(...) is a service method for queue models only.  kai, apr'16

		MobsimDriverAgent driverAgent = veh.getDriver();

		var position = snapshotInfoFactory.getAgentSnapshotInfoBuilder()
				.setPersonId(driverAgent.getId())
				.setVehicleId(veh.getId())
				.setLinkId(veh.getCurrentLink().getId())
				.setFromCoord(startCoord)
				.setToCoord(endCoord)
				.setDistanceOnLink(distanceFromFromNode)
				.setLane(lane)
				.setLinkLength(lengthOfCurve)
				.setAgentState(getAgentState(driverAgent))
				.setColorValue(speedValueBetweenZeroAndOne)
				.build();

		if (position.getAgentState().equals(AgentState.PERSON_OTHER_MODE) && veh.getVehicle().getType().getNetworkMode().equals(TransportMode.car)) {
			throw new RuntimeException("missed a car driver");
		}
		if (position.getAgentState().equals(AgentState.PERSON_DRIVING_CAR)) {
			var stop = "here";
		}
		if (position.getAgentState().equals(AgentState.TRANSIT_DRIVER)) {
			var stop = "here also";
		}


		positions.add(position);





	/*	AgentSnapshotInfo pos = snapshotInfoFactory.createAgentSnapshotInfo(driverAgent.getId(), veh.getId(), veh.getCurrentLink().getId(), startCoord, endCoord,
				distanceFromFromNode, lane, lengthOfCurve);

		if (pos.getLinkId() == null || pos.getVehicleId() == null) {
			throw new RuntimeException("break here");
		}

		pos.setColorValueBetweenZeroAndOne(speedValueBetweenZeroAndOne);
		if (driverAgent instanceof TransitDriverAgent){
			pos.setAgentState(AgentState.TRANSIT_DRIVER);
		} else if ( driverAgent.getMode().equals(TransportMode.car)) {
			pos.setAgentState(AgentState.PERSON_DRIVING_CAR);
		} else {
			pos.setAgentState(AgentState.PERSON_OTHER_MODE );
		}
		if ( driverAgent instanceof HasPerson ){
			if( PopulationUtils.getPersonAttribute( ((HasPerson) driverAgent).getPerson(), AgentSnapshotInfo.marker ) != null ){
				pos.setAgentState( AgentState.MARKER );
			}
		}
		final Person person = scenario.getPopulation().getPersons().get( driverAgent.getId() );
		if ( person != null && person.getAttributes().getAttribute( AgentSnapshotInfo.marker ) != null ) {
			pos.setAgentState( AgentState.MARKER );
		}

	 */

		this.positionPassengers(positions, veh.getPassengers(), distanceFromFromNode, startCoord,
				endCoord, lengthOfCurve, lane + 5, speedValueBetweenZeroAndOne);
		// (this is deliberately first memorizing "pos" but then filling in the passengers first)

		//positions.add(pos);
	}

	public final Collection<AgentSnapshotInfo> positionVehiclesAlongLine(Collection<AgentSnapshotInfo> positions,
			double now, Collection<? extends MobsimVehicle> vehs, double curvedLength, double storageCapacity,
			Coord upstreamCoord, Coord downstreamCoord, double inverseFlowCapPerTS, double freeSpeed,
			int numberOfLanesAsInt, Queue<Hole> holes)
	{
		double spacingOfOnePCE = this.calculateVehicleSpacing( curvedLength, storageCapacity, vehs );

		double ttimeOfHoles = curvedLength / (QueueWithBuffer.HOLE_SPEED_KM_H*1000./3600.);

		TreeMap<Double,Hole> consumableHoles = new TreeMap<>() ;

		// holes or kinematicWaves, if applicable:

		switch (scenario.getConfig().qsim().getSnapshotStyle()) {
			case equiDist:
			case queue:
				break;
			case withHoles:
			case withHolesAndShowHoles:
			case kinematicWaves:
				if ( !holes.isEmpty() ) {
					double firstHolePosition = Double.NaN ;
					double distanceOfHoleFromFromNode = Double.NaN ;
					double sum = 0 ;
					for (Hole hole : holes) {
						sum += hole.getSizeInEquivalents();
						distanceOfHoleFromFromNode = computeHolePositionAndReturnDistance(ttimeOfHoles, hole, now, curvedLength);
						if (Double.isNaN(firstHolePosition)) {
							firstHolePosition = distanceOfHoleFromFromNode;
							sum = 0; // don't include first vehicle
						}

						if (Math.round(distanceOfHoleFromFromNode) != Math.round(curvedLength)) {
							consumableHoles.put(distanceOfHoleFromFromNode, hole);
						} // else {
						// since hole is already created even if vehicle is in buffer, thus excluding such holes in vehicle position updating
						// probably, don't create hole in visualizer also. amit May 2016
						//}

						if (QSimConfigGroup.SnapshotStyle.withHolesAndShowHoles == scenario.getConfig().qsim().getSnapshotStyle()) {
							addHolePosition(positions, distanceOfHoleFromFromNode, hole, curvedLength, upstreamCoord, downstreamCoord);
						}
					}
					final double spaceConsumptionOfHoles = sum*spacingOfOnePCE;
					final double spaceAvailableForHoles = distanceOfHoleFromFromNode - firstHolePosition;
					if ( wrnCnt < 10 ) {
						wrnCnt++ ;
						if ( spaceConsumptionOfHoles >= spaceAvailableForHoles ) {
							log.warn("we have a problem: holes consume too much space:" ) ;
							log.warn( "summed up space consumption of holes: " + spaceConsumptionOfHoles );
							log.warn("distance bw first and last hole: " + spaceAvailableForHoles ) ;
						}
						if (wrnCnt == 10) {
							log.warn(Gbl.FUTURE_SUPPRESSED ) ;
						}
					}
				}
				break;
			default: throw new RuntimeException("The traffic dynmics "+scenario.getConfig().qsim().getSnapshotStyle()+" is not implemented yet.");
		}

		// yyyyyy might be faster by sorting holes into a regular array list ...

		double freespeedTraveltime = curvedLength / freeSpeed ;

		double distanceFromFromNode = Double.NaN;

		for ( MobsimVehicle mveh : vehs) {
			final QVehicle veh = (QVehicle) mveh;

			final double remainingTravelTime = veh.getEarliestLinkExitTime() - now;
			// (starts off relatively small (rightmost vehicle))

			final double vehicleSpacing = mveh.getSizeInEquivalents() * spacingOfOnePCE;
			distanceFromFromNode = this.calculateOdometerDistanceFromFromNode(curvedLength, vehicleSpacing, distanceFromFromNode,
					now, freespeedTraveltime, remainingTravelTime);
			// (starts off relatively large (rightmost vehicle))

			int lane = VisUtils.guessLane(veh, numberOfLanesAsInt);
			double speedValue = VisUtils.calcSpeedValueBetweenZeroAndOne(veh, inverseFlowCapPerTS, now, freeSpeed);
			Gbl.assertNotNull(upstreamCoord);
			Gbl.assertNotNull(downstreamCoord);
			this.positionAgentGivenDistanceFromFNode(positions, upstreamCoord, downstreamCoord, curvedLength, veh, distanceFromFromNode, lane, speedValue);

			switch (this.scenario.getConfig().qsim().getTrafficDynamics()) {
				case queue:
					break;
				case withHoles:
				case kinematicWaves:
					while ( !consumableHoles.isEmpty() && distanceFromFromNode < consumableHoles.lastKey() ) {
						Map.Entry<Double, Hole> entry = consumableHoles.pollLastEntry() ;
						distanceFromFromNode -= spacingOfOnePCE * entry.getValue().getSizeInEquivalents() ;
					}
					break;
				default: throw new RuntimeException("The traffic dynmics "+this.scenario.getConfig().qsim().getTrafficDynamics()+" is not implemented yet.");
			}
		}

		/* Can't explain the above in easy words.  Essentially, when vehicles leave the link at max rate, there still must be some space between
		 * the holes that this generates.  That space is added up until a full vehicle fits into it.  There must be some better way of
		 * explaining this, but I don't know it right now.  kai, apr'16
		 */
		return positions;
	}
		
	private void addHolePosition(final Collection<AgentSnapshotInfo> positions, double distanceFromFromNode, Hole veh, 
			double curvedLength, Coord upstreamCoord, Coord downstreamCoord)
	{
		int lane = 20 ;
		double speedValue = 1. ;
		AgentSnapshotInfo pos = this.snapshotInfoFactory.createAgentSnapshotInfo(Id.create("hole", Person.class),
				veh.getId(), null, upstreamCoord, downstreamCoord,
				distanceFromFromNode, lane, curvedLength);
		pos.setColorValueBetweenZeroAndOne(speedValue);
		pos.setAgentState(AgentState.PERSON_OTHER_MODE );
		positions.add(pos);
	}

	final void positionPassengers(Collection<AgentSnapshotInfo> positions,
								  Collection<? extends PassengerAgent> passengers, double distanceOnLink, Coord startCoord, Coord endCoord,
								  double lengthOfCurve, int lane, double speedValueBetweenZeroAndOne) {

		int cnt = passengers.size();
		int laneInt = 2 * (cnt + 1) + lane;

		for (PassengerAgent passenger : passengers) {
			int lanePos = laneInt - 2 * cnt;
			var passengerPosition = snapshotInfoFactory.getAgentSnapshotInfoBuilder()
					.setPersonId(passenger.getId())
					.setVehicleId(passenger.getVehicle().getId())
					.setLinkId(passenger.getCurrentLinkId())
					.setFromCoord(startCoord)
					.setToCoord(endCoord)
					.setDistanceOnLink(distanceOnLink)
					.setLane(lanePos)
					.setLinkLength(lengthOfCurve)
					.setColorValue(speedValueBetweenZeroAndOne)
					.setAgentState(getAgentState(passenger))
					.build();



			/*AgentSnapshotInfo passengerPosition = snapshotInfoFactory.createAgentSnapshotInfo(passenger.getId(),
					passenger.getVehicle().getId(), passenger.getCurrentLinkId(), startCoord, endCoord,
					distanceOnLink, lanePos, lengthOfCurve);
			passengerPosition.setColorValueBetweenZeroAndOne(speedValueBetweenZeroAndOne);
			passengerPosition.setAgentState(AgentState.PERSON_OTHER_MODE); // in 2010, probably a passenger
			final Person person = scenario.getPopulation().getPersons().get( passenger.getId() );
			if ( person != null && person.getAttributes().getAttribute( AgentSnapshotInfo.marker ) != null ) {
				passengerPosition.setAgentState( AgentState.MARKER );
			}

			 */

			positions.add(passengerPosition);
			cnt-- ;
		}
	}

	public abstract double calculateVehicleSpacing(double linkLength, double overallStorageCapacity, Collection<? extends VisVehicle> vehs);

	public abstract double calculateOdometerDistanceFromFromNode(double length, double spacing, double lastDistanceFromFromNode, 
			double now, double freespeedTraveltime, double remainingTravelTime);
}
