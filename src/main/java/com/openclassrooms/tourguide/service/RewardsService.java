package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
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

	public void calculateRewards(User user)
	{
		List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());
		List<Attraction> attractions = gpsUtil.getAttractions();
		List<Future<?>> results = new ArrayList<>();
		ExecutorService executor = Executors.newCachedThreadPool();
		try
		{
			for (VisitedLocation visitedLocation : userLocations)
			{
				for (Attraction attraction : attractions)
				{
					if (user.getUserRewards().stream().noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName)))
					{
						synchronized (user)
						{
							if (nearAttraction(visitedLocation, attraction))
							{
								results.add(executor.submit(() -> user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)))));
							}
						}
					}
				}
			}
			for (Future<?> result : results)
			{
				result.get();
			}
		}
		catch (InterruptedException | ExecutionException e)
		{
			Logger.info("Thread interrupted while calculating rewards : " + e + " from " + Thread.currentThread().getName());
		}
		finally
		{
			executor.shutdown();
		}
	}

	public void batchCalculateRewards(List<User> users)
	{
		ExecutorService executor = Executors.newFixedThreadPool(600);
		List<Callable<Void>> tasks = new ArrayList<>();

		try
		{
			for (User user : users)
			{
				tasks.add(() -> {calculateRewards(user); return null;});
			}

			List<Future<Void>> results = executor.invokeAll(tasks);
			for(Future<Void> result : results)
			{
				result.get();
			}
		}
		catch (InterruptedException | ExecutionException e)
		{
			Logger.info("Thread interrupted while calculating rewards in batch : " + e + " from " + Thread.currentThread().getName());
		}
		finally
		{
			executor.shutdown();
		}

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
