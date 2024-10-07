package com.openclassrooms.tourguide.tracker;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

public class Tracker extends Thread
{
	private Logger logger = LoggerFactory.getLogger(Tracker.class);
	private static final long trackingPollingInterval = TimeUnit.MINUTES.toSeconds(5);
	private final TourGuideService tourGuideService;
	private boolean stop = false;

	public Tracker(TourGuideService tourGuideService)
	{
		this.tourGuideService = tourGuideService;
		this.setName("Tracker");
	}

	/**
	 * Assures to shut down the Tracker thread
	 */
	public void stopTracking()
	{
		this.interrupt();
		stop = true;
	}

	@Override
	public void run()
	{
		StopWatch stopWatch = new StopWatch();
		while (true)
		{
			if (Thread.currentThread().isInterrupted() || stop)
			{
				logger.debug("Tracker stopping");
				break;
			}

			List<User> users = tourGuideService.getAllUsers();
			logger.debug("Begin Tracker. Tracking " + users.size() + " users.");
			stopWatch.start();
			tourGuideService.batchTrackUsersLocation(users);
			stopWatch.stop();
			logger.debug("Tracker Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
			stopWatch.reset();

			try // Why make the thread sleep ?
			{
				logger.debug("Tracker sleeping");
				TimeUnit.SECONDS.sleep(trackingPollingInterval);
			}
			catch (InterruptedException e)
			{
				break;
			}
		}

	}
}
