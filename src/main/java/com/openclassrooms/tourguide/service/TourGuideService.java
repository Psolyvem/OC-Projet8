package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService
{
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	public ExecutorService executor;
	boolean testMode = true;

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService)
	{
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		this.executor = Executors.newFixedThreadPool(600);

		Locale.setDefault(Locale.US);

		if (testMode)
		{
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		tracker.start();
		addShutDownHook(this);
	}

	public VisitedLocation getUserLocation(User user)
	{
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation() : trackUserLocation(user);
		return visitedLocation;
	}

	public User getUser(String userName)
	{
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers()
	{
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	public void addUser(User user)
	{
		if (!internalUserMap.containsKey(user.getUserName()))
		{
			internalUserMap.put(user.getUserName(), user);
		}
	}
	
	public List<Provider> getTripDeals(User user)
	{
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(), user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(), user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	public VisitedLocation trackUserLocation(User user)
	{
		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		executor.submit(() ->
		{
			user.addToVisitedLocations(visitedLocation);
			rewardsService.calculateRewards(user);
		});
		return visitedLocation;
	}

	public void batchTrackUsersLocation(List<User> users)
	{
		List<Callable<Void>> tasks = new ArrayList<>();
		try
		{
			for (User user : users)
			{
				tasks.add(() ->
				{
					trackUserLocation(user);
					return null;
				});
			}

			List<Future<Void>> results = executor.invokeAll(tasks);
			for (Future<Void> result : results)
			{
				result.get();
			}
		}
		catch (InterruptedException | ExecutionException e)
		{
			org.tinylog.Logger.info("Thread interrupted while tracking users in batch : " + e + " from " + Thread.currentThread().getName());
		}
	}

	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation)
	{

		List<Attraction> nearbyAttractions = new ArrayList<>();
		List<Attraction> attractions = gpsUtil.getAttractions();
		attractions.sort((attraction1, attraction2) ->
		{
			Location location1 = new Location(attraction1.latitude, attraction1.longitude);
			Location location2 = new Location(attraction2.latitude, attraction2.longitude);
			double distanceTo1 = rewardsService.getDistance(visitedLocation.location, location1);
			double distanceTo2 = rewardsService.getDistance(visitedLocation.location, location2);
			if (distanceTo1 < distanceTo2)
			{
				return -1;
			}
			else if (distanceTo1 == distanceTo2)
			{
				return 0;
			}
			else if (distanceTo1 > distanceTo2)
			{
				return 1;
			}
			else
			{
				return 0;
			}
		});
		for (int i = 0; i < 5; i++)
		{
			nearbyAttractions.add(attractions.get(i));
		}

		return nearbyAttractions;
	}

	private void addShutDownHook(TourGuideService tourGuideService)
	{
		Runtime.getRuntime().addShutdownHook(new Thread(() ->
		{
			tracker.stopTracking();
			tourGuideService.executor.shutdown();
		}));
	}

	/**********************************************************************************
	 *
	 * Methods Below: For Internal Testing
	 *
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers()
	{
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i ->
		{
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user)
	{
		IntStream.range(0, 3).forEach(i ->
		{
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(), new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude()
	{
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude()
	{
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime()
	{
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
