/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Arbalo AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.opentdc.wtt.opencrx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.naming.NamingException;
import javax.servlet.ServletContext;

import org.opencrx.kernel.account1.jmi1.Account;
import org.opencrx.kernel.activity1.cci2.ActivityQuery;
import org.opencrx.kernel.activity1.cci2.ActivityTrackerQuery;
import org.opencrx.kernel.activity1.cci2.ActivityTypeQuery;
import org.opencrx.kernel.activity1.jmi1.AccountAssignmentActivityGroup;
import org.opencrx.kernel.activity1.jmi1.Activity;
import org.opencrx.kernel.activity1.jmi1.ActivityCreator;
import org.opencrx.kernel.activity1.jmi1.ActivityTracker;
import org.opencrx.kernel.activity1.jmi1.ActivityType;
import org.opencrx.kernel.activity1.jmi1.NewActivityParams;
import org.opencrx.kernel.activity1.jmi1.NewActivityResult;
import org.opencrx.kernel.utils.Utils;
import org.openmdx.base.exception.ServiceException;
import org.opentdc.opencrx.AbstractOpencrxServiceProvider;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.service.exception.NotImplementedException;
import org.opentdc.service.exception.ValidationException;
import org.opentdc.wtt.CompanyModel;
import org.opentdc.wtt.ProjectModel;
import org.opentdc.wtt.ResourceModel;
import org.opentdc.wtt.ServiceProvider;
import org.w3c.spi2.Datatypes;
import org.w3c.spi2.Structures;

public class OpencrxServiceProvider extends AbstractOpencrxServiceProvider implements ServiceProvider {
	
	public static final short ACTIVITY_GROUP_TYPE_PROJECT = 40;
	public static final short ACCOUNT_ROLE_CUSTOMER = 100;
	public static final short ACTIVITY_CLASS_INCIDENT = 2;
	public static final short ICAL_TYPE_NA = 0;
	public static final short ICAL_CLASS_NA = 0;
	public static final short ICAL_TYPE_VEVENT = 1;

	// instance variables
	private static final Logger logger = Logger.getLogger(OpencrxServiceProvider.class.getName());
	
	public OpencrxServiceProvider(
		ServletContext context,
		String prefix
	) throws ServiceException, NamingException {
		super(context, prefix);
	}

	/******************************** company *****************************************/
	/**
	 * List all companies.
	 * 
	 * @return a list of all companies.
	 */
	@Override
	public ArrayList<CompanyModel> listCompanies(
		boolean asTree,
		String query, 
		String queryType, 
		long position, 
		long size
	) {
		logger.info("listCompanies() -> " + countCompanies() + " companies");
		List<ActivityTracker> _trackers = getCustomerProjectGroups(null);
		ArrayList<CompanyModel> companies = new ArrayList<CompanyModel>();
		for (ActivityTracker _tracker : _trackers) {
			companies.add(new CompanyModel(_tracker.refGetPath().toXRI(), _tracker
					.getName(), _tracker.getDescription()));
		}
		Collections.sort(companies, CompanyModel.CompanyComparator);
		return companies;
	}

	/**
	 * Create a new company.
	 * 
	 * @param company
	 *            the raw company information
	 * @return the newly created company (can be different than param company)
	 * @throws DuplicateException
	 *             if a company with the same ID already exists.
	 */
	@Override
	public CompanyModel createCompany(
			CompanyModel company) 
					throws DuplicateException {
		if (readCompany(company.getId()) != null) {
			// object with same ID exists already
			throw new DuplicateException("Company with ID " + company.getId() +
					" exists already.");
		}
		ActivityTracker _activityTracker = createCustomerProjectGroup(
				company.getTitle(), company.getDescription(),
				null);
		CompanyModel _newCompany = new CompanyModel(
				_activityTracker.refGetPath().toXRI(),
				_activityTracker.getName(),
				_activityTracker.getDescription());
		logger.info("createCompany() -> " + _newCompany);
		return _newCompany;
	}

	/**
	 * Find a company by ID.
	 * 
	 * @param id
	 *            the company ID
	 * @return the company
	 * @throws NotFoundException
	 *             if there exists no company with this ID
	 */
	@Override
	public CompanyModel readCompany(
		String xri
	)  throws NotFoundException {
		CompanyModel _company = null;
		PersistenceManager pm = this.getPersistenceManager();
		ActivityTracker _tracker = (ActivityTracker)pm.getObjectById(new org.openmdx.base.naming.Path(xri));
		if (_tracker == null) {
			throw new NotFoundException("no company with ID <" + xri + "> found.");
		}
		_company = new CompanyModel(
			_tracker.refGetPath().toXRI(),
			_tracker.getName(),
			_tracker.getDescription());
		logger.info("readCompany(" + xri + ") -> " + _company);
		return _company;
	}

	@Override
	public CompanyModel updateCompany(
		String compId,
		CompanyModel company
	) throws NotFoundException {
		throw new NotImplementedException(
				"method updateCompany() is not yet implemented for opencrx storage.");
		// TODO implement updateCompany()
	}

	@Override
	public void deleteCompany(
			String id
	) throws NotFoundException {
		throw new NotImplementedException(
				"method deleteCompany() is not yet implemented for opencrx storage.");
		// TODO implement deleteCompany()
	}

	@Override
	public int countCompanies(
	) {
		int _count = -1;
		List<ActivityTracker> _trackers = getCustomerProjectGroups(null);
		_count = _trackers.size();
		logger.info("countCompanies() = " + _count);
		return _count;
	}

	/******************************** project *****************************************/
	/**
	 * Return the top-level projects of a company without subprojects.
	 * 
	 * @param compId
	 * @return all top-level projects of a company
	 */
	@Override
	public ArrayList<ProjectModel> listProjects(
			String compId,
			String query, 
			String queryType, 
			long position, 
			long size) {
		List<Activity> _activities = getCustomerProjects(getActivityTracker(compId));
		logger.info("listProjects(" + compId + ") -> " + _activities.size()
				+ " values");
		ArrayList<ProjectModel> _projects = new ArrayList<ProjectModel>();
		for (Activity _activity : _activities) {
			_projects.add(new ProjectModel(
					_activity.refGetPath().toXRI(), 
					_activity.getName(), 
					_activity.getDescription()));
		}

		return _projects;
	}
	
	/**
	 * Return all projects of a company
	 * 
	 * @param compId
	 * @param asTree return the projects either as a hierarchial tree or as a flat list
	 * @return all projects of a company
	 */
	@Override
	public ArrayList<ProjectModel> listAllProjects(
			String compId, 
			boolean asTree,
			String query, 
			String queryType, 
			long position, 
			long size) {
		ArrayList<ProjectModel> _projects = readCompany(compId).getProjects();
		if (asTree == false) {
			_projects = new ArrayList<ProjectModel>();
			// TODO: implement listAllProjects for opencrx storage
			// _projects = flatten(_projects, readCompany(compId).getProjects());
		}
		logger.info("listProjects(" + compId + ") -> " + _projects.size()
				+ " values");
		return _projects;
	}


	/**
	 * Get customer projects for the given customer project group.
	 * 
	 * @param customerProjectGroup
	 * @return
	 */
	private static List<Activity> getCustomerProjects(
			ActivityTracker customerProjectGroup) {
		PersistenceManager _pm = JDOHelper
				.getPersistenceManager(customerProjectGroup);
		try {
			ActivityQuery activityQuery = (ActivityQuery) _pm
					.newQuery(Activity.class);
			activityQuery.forAllDisabled().isFalse();
			activityQuery.orderByName().ascending();
			return customerProjectGroup.getFilteredActivity(activityQuery);
		} finally {
			_pm.close();
		}
	}

	@Override
	public ProjectModel createProject(
			String compId, 
			ProjectModel newProject)
					throws DuplicateException {
		logger.info("> createProject(" + compId + ", " + newProject + ")");
		if (getProject(newProject.getId()) != null) {
			throw new DuplicateException("Project with ID " + newProject.getId() +
					" exists already.");
		}
		ActivityTracker _activityTracker = getActivityTracker(compId);
		// TODO: set scheduledStart, scheduledEnd, priority correctly
		Activity _activity = createCustomerProject(
				_activityTracker,
				newProject.getTitle(), 
				newProject.getDescription(), 
				null,
				new Date(), 
				new Date(), 
				(short) 0);
		newProject.setXri(_activity.refGetPath().toXRI());
		return newProject;
	}
	
	@Override
	public ProjectModel createProjectAsSubproject(
			String compId, 
			String projId, 
			ProjectModel newProject) 
					throws DuplicateException {
		logger.info("> createProjectAsSubproject(" + compId +
				", " + projId + ", " + newProject + ")");
		// TODO: temporary solution: 
		// subprojects are currently not supported in opencrx
		// we need to define the [000.000.000] syntax accordingly
		return createProject(compId, newProject);
	}

	/**
	 * Create a customer project.
	 * 
	 * @param customerProjectGroup
	 * @return
	 */
	private Activity createCustomerProject(
		ActivityTracker customerProjectGroup, 
		String name,
		String description, 
		String detailedDescription,
		Date scheduledStart, 
		Date scheduledEnd, 
		short priority
	) {
		PersistenceManager pm = JDOHelper.getPersistenceManager(customerProjectGroup);
		ActivityCreator customerProjectCreator = null;
		for (ActivityCreator activityCreator : customerProjectGroup
				.<ActivityCreator> getActivityCreator()) {
			if (activityCreator.getActivityType().getActivityClass() == ACTIVITY_CLASS_INCIDENT) {
				customerProjectCreator = activityCreator;
				break;
			}
		}
		if (customerProjectCreator != null) {
			try {
				pm.currentTransaction().begin();
				NewActivityParams newActivityParams = Structures.create(
						NewActivityParams.class, 
						Datatypes.member(
								NewActivityParams.Member.name, 
								name), 
						Datatypes.member(
								NewActivityParams.Member.description,
								description), 
						Datatypes.member(
								NewActivityParams.Member.detailedDescription,
								detailedDescription), 
						Datatypes.member(
								NewActivityParams.Member.scheduledStart,
								scheduledStart), 
						Datatypes.member(
								NewActivityParams.Member.scheduledEnd,
								scheduledEnd), 
						Datatypes.member(
								NewActivityParams.Member.priority, 
								priority),
						Datatypes.member(NewActivityParams.Member.icalType,
								ICAL_TYPE_NA));
				NewActivityResult newActivityResult = customerProjectCreator
						.newActivity(newActivityParams);
				pm.currentTransaction().commit();
				return newActivityResult.getActivity();
			} catch (Exception e) {
				new ServiceException(e).log();
				try {
					pm.currentTransaction().rollback();
				} catch (Exception ignore) {
				}
			}
		}
		return null;
	}

	@Override
	public ProjectModel readProject(
			String projId)
					throws NotFoundException {
		ProjectModel _project = getProject(projId);
		if (_project == null) {
			throw new NotFoundException("no project with ID <" + projId
					+ "> found.");
		}
		logger.info("readProject(" + projId + "): " + _project);
		return _project;
	}

	private ProjectModel getProject(
		String xri
	) {
		PersistenceManager pm = this.getPersistenceManager();
		ActivityTracker _tracker = (ActivityTracker)pm.getObjectById(new org.openmdx.base.naming.Path(xri));
		return new ProjectModel(
			_tracker.refGetPath().toXRI(),
			_tracker.getName(), 
			_tracker.getDescription()
		);
	}

	@Override
	public ProjectModel updateProject(
		String compId,
		String projId,
		ProjectModel project
	) throws NotFoundException {
		// TODO implement updateProject for opencrx
		throw new DuplicateException(
				"method updateProject is not yet implemented for opencrx");
	}

	@Override
	public void deleteProject(
			String compId, 
			String projId)
					throws NotFoundException {
		// TODO implement deleteProject for opencrx
		throw new DuplicateException(
				"method deleteProject is not yet implemented for opencrx");
	}

	@Override
	public int countProjects(
			String compId) {
		// TODO: replace this with size of index (= count recursively)
		int _count = readCompany(compId).getProjects().size();
		logger.info("countProjects(" + compId + ") -> " + _count);
		return _count;
	}
	
	/******************************** resource *****************************************/
	@Override
	public ArrayList<ResourceModel> listResources(
			String projId,
			String query, 
			String queryType, 
			long position, 
			long size) 
		throws NotFoundException {
		// TODO implement listResources for opencrx
		throw new DuplicateException(
				"method listResources is not yet implemented for opencrx");		
	}

	@Override
	public String addResource(
			String projId, 
			String resourceId)
					throws NotFoundException {
		// TODO implement addResource for opencrx
		throw new DuplicateException(
				"method addResource is not yet implemented for opencrx");		
	}

	@Override
	public void removeResource(
			String projId, 
			String resourceId)
					throws NotFoundException {
		// TODO implement removeResource for opencrx
		throw new DuplicateException(
				"method removeResource is not yet implemented for opencrx");				
	}

	@Override
	public int countResources(
			String projId) 
					throws NotFoundException {
		// TODO implement countResources for opencrx
		throw new DuplicateException(
				"method countResources is not yet implemented for opencrx");		
	}
	
	/******************************** utility methods *****************************************/
	/**
	 * Get customer project groups and restrict to account if specified.
	 * 
	 * @param activitySegment
	 * @param account
	 * @return
	 */
	private List<ActivityTracker> getCustomerProjectGroups(
		Account account
	) {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		ActivityTrackerQuery activityTrackerQuery = (ActivityTrackerQuery)pm.newQuery(ActivityTracker.class);
		activityTrackerQuery.forAllDisabled().isFalse();
		activityTrackerQuery.activityGroupType().equalTo(
				ACTIVITY_GROUP_TYPE_PROJECT);
		activityTrackerQuery.thereExistsAssignedAccount().accountRole()
				.equalTo(ACCOUNT_ROLE_CUSTOMER);
		if (account != null) {
			activityTrackerQuery.thereExistsAssignedAccount()
					.thereExistsAccount().equalTo(account);
		}
		activityTrackerQuery.orderByName().ascending();
		return activitySegment.getActivityTracker(activityTrackerQuery);
	}

	/**
	 * Create a customer project group.
	 * 
	 * @param name
	 * @param description
	 * @param customer
	 */
	private ActivityTracker createCustomerProjectGroup(
		String name,
		String description, 
		Account customer
	) {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		ActivityTracker _activityTracker = null;
		try {
			pm.currentTransaction().begin();
			// Activity tracker
			_activityTracker = pm.newInstance(ActivityTracker.class);
			_activityTracker.setName(name);
			_activityTracker.setDescription(description);
			_activityTracker.setActivityGroupType(ACTIVITY_GROUP_TYPE_PROJECT);
			activitySegment.addActivityTracker(
					org.opencrx.kernel.utils.Utils.getUidAsString(),
					_activityTracker);
			// Activity creator
			ActivityCreator _activityCreator = null;
			{
				ActivityTypeQuery _activityTypeQuery = (ActivityTypeQuery)pm.newQuery(ActivityType.class);
				_activityTypeQuery.name().equalTo("Incidents");
				List<ActivityType> _activityTypes = activitySegment
						.getActivityType(_activityTypeQuery);
				ActivityType _incidentType = _activityTypes.isEmpty() ? null
						: _activityTypes.iterator().next();

				_activityCreator = pm.newInstance(ActivityCreator.class);
				_activityCreator.setName(name);
				_activityCreator.setDescription(description);
				_activityCreator.getActivityGroup().add(_activityTracker);
				_activityCreator.setActivityType(_incidentType);
				_activityCreator.setIcalClass(ICAL_CLASS_NA);
				_activityCreator.setIcalType(ICAL_TYPE_VEVENT);
				_activityCreator.setPriority((short) 0);
				activitySegment.addActivityCreator(Utils.getUidAsString(),
						_activityCreator);
			}
			// Account assignment
			{
				AccountAssignmentActivityGroup accountAssignment = pm.newInstance(AccountAssignmentActivityGroup.class);
				accountAssignment.setName(customer.getFullName());
				accountAssignment.setAccount(customer);
				accountAssignment.setAccountRole(ACCOUNT_ROLE_CUSTOMER);
				_activityTracker.addAssignedAccount(Utils.getUidAsString(),
						accountAssignment);
			}
			pm.currentTransaction().commit();
		} catch (Exception e) {
			new ServiceException(e).log();
			try {
				pm.currentTransaction().rollback();
			} catch (Exception ignore) {
			}
		}
		return _activityTracker;
	}

	private ActivityTracker getActivityTracker(
		String xri
	) {
		PersistenceManager pm = this.getPersistenceManager();
		ActivityTracker _tracker = (ActivityTracker)pm.getObjectById(new org.openmdx.base.naming.Path(xri));
		return _tracker;
	}
	
	/*
	 * private treeJSON convert(flatJSON) assumption: data is sorted ascending
	 */
	private ArrayList<ProjectModel> convertProjects(
			ArrayList<ProjectModel> srcProjects,
			boolean isFlat) 
					throws ValidationException {
		logger.info("converting data");

		int oldIdx1 = -1;
		int oldIdx2 = -1;
		int newIdx1 = -1;
		int newIdx2 = -1;
		int newIdx3 = -1;
		ProjectModel _srcProject = null;
		ProjectModel _destProject = null;
		ArrayList<ProjectModel> _destProjects = new ArrayList<ProjectModel>();
		ProjectModel _lastL1Project = null;
		ProjectModel _lastL2Project = null;
		String _title = null;
		for (int i = 0; i < srcProjects.size(); i++) {
			_srcProject = srcProjects.get(i);
			_title = _srcProject.getTitle();
			if (_title.length() <= 14) {
				throw new ValidationException(
						"title is too short: <"
								+ _title
								+ ">. Should be at least 14 chars long with Format <[000.000.000] text>.");
			}
			if (_title.charAt(0) != '[' || _title.charAt(4) != '.'
					|| _title.charAt(8) != '.' || _title.charAt(12) != ']') {
				throw new ValidationException(
						"title <"
								+ _title
								+ "> has invalid format. Should be <[000.000.000] text>.");
			}
			newIdx1 = new Integer(_title.substring(1, 4)).intValue();
			newIdx2 = new Integer(_title.substring(5, 8)).intValue();
			newIdx3 = new Integer(_title.substring(9, 12)).intValue();

			_destProject = new ProjectModel(_title.substring(14), "");

			if (isFlat == true) {
				_destProjects.add(_destProject);
			} else {
				if (newIdx1 > oldIdx1) { // level 1 has changed
					if (newIdx2 == 0 && newIdx3 == 0) {
						_lastL1Project = _destProject;
						_destProjects.add(_lastL1Project);
						_lastL2Project = null;
					} else {
						_lastL1Project = new ProjectModel();
						_destProjects.add(_lastL1Project);

						if (newIdx3 == 0) {
							_lastL2Project = _destProject;
							_lastL1Project.addProject(_lastL2Project);
						} else {
							_lastL2Project = new ProjectModel();
							_lastL1Project.addProject(_lastL2Project);
							_lastL2Project.addProject(_destProject);
						}
					}
				} else { // level1 unchanged = _lastL1Project
					if (newIdx2 > oldIdx2) { // level 2 has changed
						if (newIdx3 == 0) {
							_lastL2Project = _destProject;
							_lastL1Project.addProject(_lastL2Project);
						} else {
							_lastL2Project = new ProjectModel();
							_lastL1Project.addProject(_lastL2Project);
							_lastL2Project.addProject(_destProject);
						}
					} else { // level 2 unchanged = _lastL2Project
						if (_lastL2Project == null) {
							_lastL2Project = new ProjectModel();
							_lastL1Project.addProject(_lastL2Project);
						}
						_lastL2Project.addProject(_destProject);
					}
				}
			}
			oldIdx1 = newIdx1;
			oldIdx2 = newIdx2;
		}
		return _destProjects;
	}
}
