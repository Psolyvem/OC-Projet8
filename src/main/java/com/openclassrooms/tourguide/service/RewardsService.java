package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.apache.commons.lang3.time.StopWatch;
import org.springframework.stereotype.Service;
import org.tinylog.Logger;
import rewardCentral.RewardCentral;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Service
public class RewardsService
{
	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
	private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral)
	{
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}

	public void setProximityBuffer(int proximityBuffer)
	{
		this.proximityBuffer = proximityBuffer;
	}

	public void setDefaultProximityBuffer()
	{
		proximityBuffer = defaultProximityBuffer;
	}

	public synchronized Future<UserReward> calculateRewards(User user)
	{
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());
		List<Attraction> attractions = gpsUtil.getAttractions();
		Future<UserReward> result = null;
		ExecutorService executor = Executors.newCachedThreadPool();

		try
		{
			for (VisitedLocation visitedLocation : userLocations)
			{
				for (Attraction attraction : attractions)
				{
					if (user.getUserRewards().stream().noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName)))
					{
						if (nearAttraction(visitedLocation, attraction))
						{
							result = executor.submit(() ->
							{
								UserReward reward = new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user));
								user.addUserReward(reward);
								return reward;
							});
							break;
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			executor.shutdown();
		}
		stopWatch.stop();
		Logger.info("Calculated reward in : " + stopWatch.getTime());
		return result;
	}

	public boolean isWithinAttractionProximity(Attraction attraction, Location location)
	{
		return getDistance(attraction, location) <= attractionProximityRange;
	}

	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction)
	{
		return getDistance(attraction, visitedLocation.location) <= proximityBuffer;
	}

	public int getRewardPoints(Attraction attraction, User user)
	{
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	public double getDistance(Location loc1, Location loc2)
	{
		double lat1 = Math.toRadians(loc1.latitude);
		double lon1 = Math.toRadians(loc1.longitude);
		double lat2 = Math.toRadians(loc2.latitude);
		double lon2 = Math.toRadians(loc2.longitude);

		double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2) + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

		double nauticalMiles = 60 * Math.toDegrees(angle);
		double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
		return statuteMiles;
	}

}
