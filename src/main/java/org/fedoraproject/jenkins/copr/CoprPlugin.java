/*
 * The MIT License
 * 
 * Copyright (c) 2014 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.fedoraproject.jenkins.copr;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.fedoraproject.copr.Copr;
import org.fedoraproject.copr.CoprRepo;
import org.fedoraproject.copr.CoprUser;
import org.fedoraproject.copr.exception.CoprException;
import org.kohsuke.stapler.DataBoundConstructor;

public class CoprPlugin extends Notifier {

	protected static final Logger LOGGER = Logger.getLogger(CoprPlugin.class
			.getName());

	private final String coprname;
	private final String username;
	private final String srpm;
	private final String apilogin;
	private final String apitoken;
	private final String apiurl;
	private final String srpmscript;
	private final boolean prepareSrpm;

	@DataBoundConstructor
	public CoprPlugin(String coprname, String username, String srpm,
			String apilogin, String apitoken, String apiurl, String srpmscript,
			boolean prepareSrpm) {
		this.coprname = coprname;
		this.username = username;
		this.srpm = srpm;
		this.apilogin = apilogin;
		this.apitoken = apitoken;
		this.apiurl = apiurl;
		this.srpmscript = srpmscript;
		this.prepareSrpm = prepareSrpm;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException {

		// TODO:
		// create repository in Copr if it doesn't exist yet
		// add button to check if provided information are correct (API URL,
		// credentials?)

		listener.getLogger().println("Running Copr plugin");

		if (build.getResult() != Result.SUCCESS) {
			listener.getLogger().println(
					"Build was unsuccessful. Nothing to build in Copr.");
			return true;
		}

		if (prepareSrpm) {
			Result srpmres = prepareSrpm(build, launcher, listener);

			listener.getLogger().println("Copr plugin: " + srpmres.toString());

			if (srpmres != Result.SUCCESS) {
				return false;
			}
		}

		EnvVars env = build.getEnvironment(listener);
		String srpmstr = env.expand(srpm);
		URL srpmurl = getSrpmUrl(srpmstr, build, listener);

		Copr copr = new Copr(apiurl);

		try {
			CoprUser user = copr.getUser(username, apilogin, apitoken);
			CoprRepo repo = user.getRepo(coprname);

			List<String> srpms = new ArrayList<String>();
			srpms.add(srpmurl.toString());
			repo.addNewBuild(srpms);

			listener.getLogger().println("New Copr job has been scheduled");
		} catch (CoprException e) {
			listener.getLogger().println(e);
			return false;
		}

		return true;
	}

	private Result prepareSrpm(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws InterruptedException {
		CommandInterpreter shell;
		if (launcher.isUnix()) {
			shell = new Shell(srpmscript);
		} else {
			shell = new BatchFile(srpmscript);
		}

		return shell.perform(build, launcher, listener) ? Result.SUCCESS
				: Result.FAILURE;
	}

	private URL getSrpmUrl(String srpmurl, AbstractBuild<?, ?> build,
			BuildListener listener) throws IOException, InterruptedException {

		URL url;
		try {
			url = new URL(srpmurl);
		} catch (MalformedURLException e) {
			// TODO: what's wrong with JOB_URL?
			String jenkinsUrl = build.getEnvironment(listener).get(
					"JENKINS_URL");
			String jobName = build.getEnvironment(listener).get("JOB_NAME");
			if (jenkinsUrl == null || jobName == null) {
				// something's really wrong
				throw new AssertionError(
						String.format(
								"JENKINS_URL or JOB_NAME env. variable is not set (%s, %s)",
								String.valueOf(jenkinsUrl),
								String.valueOf(jobName)));
			}
			url = new URL(jenkinsUrl + "/job/" + jobName + "/ws/");
			url = new URL(url, srpmurl);
		}

		return url;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	public String getCoprname() {
		return coprname;
	}

	public String getUsername() {
		return username;
	}

	public String getSrpm() {
		return srpm;
	}

	public String getApilogin() {
		return apilogin;
	}

	public String getApitoken() {
		return apitoken;
	}

	public String getApiurl() {
		return apiurl;
	}

	public String getSrpmscript() {
		return srpmscript;
	}

	public boolean getPrepareSrpm() {
		return prepareSrpm;
	}

	@Extension
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Publisher> {

		public DescriptorImpl() {
			load();
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Build RPM in Copr";
		}
	}
}
