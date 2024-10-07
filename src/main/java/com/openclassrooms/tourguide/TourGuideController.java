package com.openclassrooms.tourguide;

import java.util.ArrayList;
import java.util.List;

import com.openclassrooms.tourguide.service.RewardsService;
import gpsUtil.location.Location;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import tripPricer.Provider;

@RestController
public class TourGuideController
{

	@Autowired
	TourGuideService tourGuideService;
	@Autowired
	private RewardsService rewardsService;

	@RequestMapping("/")
	public String index()
	{
		return "Greetings from TourGuide!";
	}

	@RequestMapping("/getLocation")
	public VisitedLocation getLocation(@RequestParam String userName)
	{
		return tourGuideService.getUserLocation(getUser(userName));
	}

	@RequestMapping("/getNearbyAttractions")
	public JSONObject getNearbyAttractions(@RequestParam String userName)
	{
		User user = tourGuideService.getUser(userName);
		List<Attraction> attractions = tourGuideService.getNearByAttractions(tourGuideService.getUserLocation(user));
		JSONObject response = new JSONObject();
		response.put("userLatitude", user.getLastVisitedLocation().location.latitude);
		response.put("userLongitude", user.getLastVisitedLocation().location.longitude);
		JSONArray jsonAttractions = new JSONArray();

		for(Attraction attraction : attractions)
		{
			JSONObject jsonAttraction = new JSONObject();
			jsonAttraction.put("name", attraction.attractionName);
			jsonAttraction.put("attractionLatitude", attraction.latitude);
			jsonAttraction.put("attractionLongitude", attraction.longitude);
			jsonAttraction.put("distance", rewardsService.getDistance(user.getLastVisitedLocation().location, new Location(attraction.latitude, attraction.longitude)));
			jsonAttraction.put("rewardPoints", rewardsService.getRewardPoints(attraction, user));
			jsonAttractions.add(jsonAttraction);
		}
		response.put("attractions", jsonAttractions);

		return response;
	}

	@RequestMapping("/getRewards")
	public List<UserReward> getRewards(@RequestParam String userName)
	{
		return getUser(userName).getUserRewards();
	}

	@RequestMapping("/getTripDeals")
	public List<Provider> getTripDeals(@RequestParam String userName)
	{
		return tourGuideService.getTripDeals(getUser(userName));
	}

	private User getUser(String userName)
	{
		return tourGuideService.getUser(userName);
	}


}