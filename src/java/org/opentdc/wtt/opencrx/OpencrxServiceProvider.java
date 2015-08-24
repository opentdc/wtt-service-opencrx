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
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.naming.NamingException;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.opencrx.kernel.account1.jmi1.LegalEntity;
import org.opencrx.kernel.activity1.cci2.AccountAssignmentActivityGroupQuery;
import org.opencrx.kernel.activity1.cci2.ActivityQuery;
import org.opencrx.kernel.activity1.jmi1.AccountAssignmentActivityGroup;
import org.opencrx.kernel.activity1.jmi1.Activity;
import org.opencrx.kernel.activity1.jmi1.ActivityTracker;
import org.opencrx.kernel.activity1.jmi1.Resource;
import org.opencrx.kernel.activity1.jmi1.ResourceAssignment;
import org.opencrx.kernel.utils.Utils;
import org.openmdx.base.exception.ServiceException;
import org.opentdc.opencrx.AbstractOpencrxServiceProvider;
import org.opentdc.opencrx.ActivitiesHelper;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.service.exception.ValidationException;
import org.opentdc.wtt.CompanyModel;
import org.opentdc.wtt.ProjectModel;
import org.opentdc.wtt.ProjectTreeNodeModel;
import org.opentdc.wtt.ResourceRefModel;
import org.opentdc.wtt.ServiceProvider;

/**
 * Wtt service for openCRX.
 *
 */
public class OpencrxServiceProvider extends AbstractOpencrxServiceProvider implements ServiceProvider {
	
	private static final Logger logger = Logger.getLogger(OpencrxServiceProvider.class.getName());
	
	/**
	 * Constructor.
	 * 
	 * @param context
	 * @param prefix
	 * @throws ServiceException
	 * @throws NamingException
	 */
	public OpencrxServiceProvider(
		ServletContext context,
		String prefix
	) throws ServiceException, NamingException {
		super(context, prefix);
	}

	/**
	 * Map to project model.
	 * 
	 * @param project
	 * @return
	 */
	protected ProjectModel mapToProject(
		Activity project
	) {
		ProjectModel projectModel = new ProjectModel();
		projectModel.setCreatedAt(project.getCreatedAt());
		projectModel.setCreatedBy(project.getCreatedBy().get(0));
		projectModel.setModifiedAt(project.getModifiedAt());
		projectModel.setModifiedBy(project.getModifiedBy().get(0));
		projectModel.setId(project.refGetPath().getLastSegment().toClassicRepresentation());
		projectModel.setTitle(project.getName());
		projectModel.setDescription(project.getDescription());
		return projectModel;
	}

	/**
	 * Map to company model.
	 * 
	 * @param project
	 * @return
	 */
	protected CompanyModel mapToCompany(
		ActivityTracker customerProjectGroup
	) {
		PersistenceManager pm = JDOHelper.getPersistenceManager(customerProjectGroup);
		CompanyModel companyModel = new CompanyModel();
		companyModel.setCreatedAt(customerProjectGroup.getCreatedAt());
		companyModel.setCreatedBy(customerProjectGroup.getCreatedBy().get(0));
		companyModel.setModifiedAt(customerProjectGroup.getModifiedAt());
		companyModel.setModifiedBy(customerProjectGroup.getModifiedBy().get(0));
		companyModel.setId(customerProjectGroup.refGetPath().getLastSegment().toClassicRepresentation());
		companyModel.setTitle(customerProjectGroup.getName());
		companyModel.setDescription(customerProjectGroup.getDescription());
		AccountAssignmentActivityGroupQuery accountAssignmenQuery = (AccountAssignmentActivityGroupQuery)pm.newQuery(AccountAssignmentActivityGroup.class);
		accountAssignmenQuery.accountRole().equalTo(ActivitiesHelper.ACCOUNT_ROLE_CUSTOMER);
		List<AccountAssignmentActivityGroup> assignedAccounts = customerProjectGroup.getAssignedAccount(accountAssignmenQuery);
		if(!assignedAccounts.isEmpty()) {
			try {
				LegalEntity organisation = (LegalEntity)assignedAccounts.iterator().next().getAccount();
				companyModel.setOrgId(organisation.refGetPath().getLastSegment().toClassicRepresentation());
			} catch(Exception e) {
				new ServiceException(e).log();
			}
		}
		return companyModel;
	}

	/**
	 * Map to resource ref.
	 * 
	 * @param resourceAssignment
	 * @return
	 */
	protected ResourceRefModel mapToResourceRef(
		ResourceAssignment resourceAssignment
	) {
		ResourceRefModel resourceRef = new ResourceRefModel();
		resourceRef.setCreatedAt(resourceAssignment.getCreatedAt());
		resourceRef.setCreatedBy(resourceAssignment.getCreatedBy().get(0));
		resourceRef.setModifiedAt(resourceAssignment.getModifiedAt());
		resourceRef.setModifiedBy(resourceAssignment.getModifiedBy().get(0));
		resourceRef.setId(resourceAssignment.refGetPath().getLastSegment().toClassicRepresentation());
		resourceRef.setResourceName(
			resourceAssignment.getResource() == null 
				? "" 
				: resourceAssignment.getResource().getName()
		);
		resourceRef.setResourceId(
			resourceAssignment.getResource() == null 
				? "UNDEF"
				: resourceAssignment.getResource().refGetPath().getLastSegment().toClassicRepresentation()
		);
		return resourceRef;
	}

	/******************************** company *****************************************/
	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#listCompanies(boolean, java.lang.String, java.lang.String, long, long)
	 */
	@Override
	public List<CompanyModel> listCompanies(
		String query, 
		String queryType, 
		int position, 
		int size
	) {
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		List<ActivityTracker> trackers = ActivitiesHelper.getCustomerProjectGroups(activitySegment, null);
		List<CompanyModel> companies = new ArrayList<CompanyModel>();
		int count = 0;
		for(Iterator<ActivityTracker> i = trackers.listIterator(position); i.hasNext(); ) {
			ActivityTracker tracker = i.next();
			companies.add(this.mapToCompany(tracker));
			count++;
			if(count >= size) break;
		}
		logger.info("listCompanies() -> " + companies.size() + " companies");
		Collections.sort(companies, CompanyModel.CompanyComparator);
		return companies;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#createCompany(org.opentdc.wtt.CompanyModel)
	 */
	@Override
	public CompanyModel createCompany(
		HttpServletRequest request,
		CompanyModel company
	)  throws DuplicateException, ValidationException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		org.opencrx.kernel.account1.jmi1.Segment accountSegment = this.getAccountSegment();
		if(company.getId() != null) {
			try {
				readCompany(company.getId());
				throw new DuplicateException("Company with ID " + company.getId() + " exists already.");
			} catch(NotFoundException ignore) {
				throw new ValidationException("company <" + company.getId() + "> contains an ID generated on the client. This is not allowed.");
			}
		}
		if(company.getTitle() == null || company.getTitle().length() == 0) {
			throw new ValidationException("company must contain a valid title.");
		}
		if(company.getOrgId() == null) {
			throw new ValidationException("company must contain a contactId.");
		}
		LegalEntity customer = (LegalEntity)accountSegment.getAccount(company.getOrgId());
		if(customer == null) {
			throw new ValidationException("company must contain a contactId.");
		}
		ActivityTracker customerProjectGroup = ActivitiesHelper.createCustomerProjectGroup(
			pm,
			activitySegment,
			company.getTitle(), 
			company.getDescription(),
			customer
		);
		if(customerProjectGroup == null) {
			throw new InternalServerErrorException();
		} else {
			CompanyModel _newCompany = this.mapToCompany(customerProjectGroup);
			logger.info("createCompany() -> " + _newCompany);
			return _newCompany;
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#readCompany(java.lang.String)
	 */
	@Override
	public CompanyModel readCompany(
		String id
	)  throws NotFoundException {
		CompanyModel _company = null;
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		ActivityTracker customerProjectGroup = null;
		try {
			customerProjectGroup = activitySegment.getActivityTracker(id);
		} catch(Exception ignore) {}
		if(customerProjectGroup == null || Boolean.TRUE.equals(customerProjectGroup.isDisabled())) {
			throw new NotFoundException("no company with ID <" + id + "> found.");
		}
		_company = this.mapToCompany(customerProjectGroup);
		logger.info("readCompany(" + id + ") -> " + _company);
		return _company;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#updateCompany(java.lang.String, org.opentdc.wtt.CompanyModel)
	 */
	@Override
	public CompanyModel updateCompany(
		HttpServletRequest request,
		String id,
		CompanyModel company
	) throws NotFoundException, ValidationException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		ActivityTracker customerProjectGroup = null;
		try {
			customerProjectGroup = activitySegment.getActivityTracker(id);
		} catch(Exception ignore) {}
		if(customerProjectGroup == null || Boolean.TRUE.equals(customerProjectGroup.isDisabled())) {
			throw new NotFoundException("no company with ID <" + id + "> found.");
		}
		try {
			pm.currentTransaction().begin();
			customerProjectGroup.setName(company.getTitle());
			customerProjectGroup.setDescription(company.getDescription());
			pm.currentTransaction().commit();
		} catch(Exception e) {
			new ServiceException(e).log();
			try {
				pm.currentTransaction().rollback();
			} catch(Exception ignore) {}
			throw new InternalServerErrorException(e.getMessage());
		}
		return this.readCompany(id);
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#deleteCompany(java.lang.String)
	 */
	@Override
	public void deleteCompany(
		String id
	) throws NotFoundException, InternalServerErrorException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		ActivityTracker customerProjectGroup = null;
		try {
			customerProjectGroup = activitySegment.getActivityTracker(id);
		} catch(Exception ignore) {}
		if(customerProjectGroup == null || Boolean.TRUE.equals(customerProjectGroup.isDisabled())) {
			throw new NotFoundException("no company with ID <" + id + "> found.");
		}
		// Disable tracker and assigned activities
		try {
			pm.currentTransaction().begin();
			customerProjectGroup.setDisabled(true);
			List<Activity> customerProjects = ActivitiesHelper.getCustomerProjects(customerProjectGroup, false);
			for(Activity project: customerProjects) {
				project.setDisabled(true);
			}
			pm.currentTransaction().commit();
		} catch(Exception e) {
			new ServiceException(e).log();
			try {
				pm.currentTransaction().rollback();
			} catch(Exception ignore) {}
			throw new InternalServerErrorException(e.getMessage());
		}
		logger.info("deleteCompany(" + id + ")");
	}

	/**
	 * Get project tree.
	 * 
	 * @param compId
	 * @param project
	 * @return
	 */
	protected ProjectTreeNodeModel getProjectTree(
		String compId,
		ProjectModel project
	) {
		ProjectTreeNodeModel projectTree = new ProjectTreeNodeModel();
		projectTree.setId(project.getId());
		projectTree.setProjects(new ArrayList<ProjectTreeNodeModel>());
		projectTree.setResources(new ArrayList<String>());
		List<ResourceRefModel> resourceRefs = this.listResourceRefs(compId, project.getId(), null, null, 0, Integer.MAX_VALUE);
		for(ResourceRefModel resourceRef: resourceRefs) {
			projectTree.getResources().add(resourceRef.getId());
		}
		List<ProjectModel> subprojects = this.listSubprojects(compId, project.getId(), null, null, 0, Integer.MAX_VALUE);
		for(ProjectModel subproject: subprojects) {
			projectTree.getProjects().add(this.getProjectTree(compId, subproject));
		}
		return projectTree;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#readAsTree(java.lang.String)
	 */
	@Override
	public ProjectTreeNodeModel readAsTree(
		String compId
	) throws NotFoundException {
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		ActivityTracker customerProjectGroup = null;
		try {
			customerProjectGroup = activitySegment.getActivityTracker(compId);
		} catch(Exception ignore) {}
		if(customerProjectGroup == null || Boolean.TRUE.equals(customerProjectGroup.isDisabled())) {
			throw new NotFoundException("no company with ID <" + compId + "> found.");
		}
		List<Activity> customerProjects = ActivitiesHelper.getCustomerProjects(customerProjectGroup, true);
		ProjectTreeNodeModel _result = new ProjectTreeNodeModel();
		_result.setId(compId);
		_result.setProjects(new ArrayList<ProjectTreeNodeModel>());
		_result.setResources(new ArrayList<String>());
		for(Activity customerProject: customerProjects) {
			ProjectModel project = this.readProject(compId, customerProject.refGetPath().getLastSegment().toClassicRepresentation());
			_result.getProjects().add(this.getProjectTree(compId, project));
		}
		return _result;
	}

	/******************************** projects *****************************************/
	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#listProjects(java.lang.String, java.lang.String, java.lang.String, int, int)
	 */
	@Override
	public List<ProjectModel> listProjects(
		String compId,
		String query, 
		String queryType, 
		int position, 
		int size
	) {
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		ActivityTracker customerProjectGroup = null;
		try {
			customerProjectGroup = activitySegment.getActivityTracker(compId);
		} catch(Exception ignore) {}
		if(customerProjectGroup == null || Boolean.TRUE.equals(customerProjectGroup.isDisabled())) {
			throw new NotFoundException("no company with ID <" + compId + "> found.");
		}
		List<Activity> customerProjects = ActivitiesHelper.getCustomerProjects(customerProjectGroup, true);
		ArrayList<ProjectModel> _result = new ArrayList<ProjectModel>();
		int count = 0;
		for(Iterator<Activity> i = customerProjects.listIterator(position); i.hasNext(); ) {
			Activity customerProject = i.next();
			_result.add(
				this.mapToProject(customerProject)
			);
			count++;
			if(count >= size) break;
		}
		return _result;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#createProject(java.lang.String, org.opentdc.wtt.ProjectModel)
	 */
	@Override
	public ProjectModel createProject(
		HttpServletRequest request,
		String compId, 
		ProjectModel project
	) throws DuplicateException, ValidationException {
		logger.info("> createProject(" + compId + ", " + project + ")");
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		ActivityTracker customerProjectGroup = null;
		try {
			customerProjectGroup = activitySegment.getActivityTracker(compId);
		} catch(Exception ignore) {}
		if(customerProjectGroup == null || Boolean.TRUE.equals(customerProjectGroup.isDisabled())) {
			throw new NotFoundException("no company with ID <" + compId + "> found.");
		}
		if(project.getId() != null) {
			Activity _project = null;
			try {
				_project = activitySegment.getActivity(project.getId());
			} catch(Exception ignore) {}
			if(_project != null) {
				throw new DuplicateException("Project with ID " + project.getId() + " exists already.");				
			} else {
				throw new ValidationException("project <" + project.getId() + "> contains an ID generated on the client. This is not allowed.");
			}
		}
		if(project.getTitle() == null || project.getTitle().isEmpty()) {
			throw new ValidationException("project must have a valid title.");
		}
		Activity _project = ActivitiesHelper.createCustomerProject(
			pm,
			customerProjectGroup,
			project.getTitle(), 
			project.getDescription(), 
			null,
			new Date(), 
			new Date(),
			ActivitiesHelper.ACTIVITY_PRIORITY_NA,
			null
		);
		if(_project == null) {
			throw new InternalServerErrorException();
		} else {
			ProjectModel _p = this.mapToProject(_project);
			return(_p);
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#readProject(java.lang.String)
	 */
	@Override
	public ProjectModel readProject(
		String compId,
		String projId
	) throws NotFoundException {
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();		
		Activity project = null;
		try {
			project = activitySegment.getActivity(projId);
		} catch(Exception ignore) {}
		if(project == null || Boolean.TRUE.equals(project.isDisabled())) {
			throw new NotFoundException("no project with ID <" + projId + "> found.");
		}
		ProjectModel _p = this.mapToProject(project);
		logger.info("readProject(" + projId + "): " + _p);
		return _p;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#updateProject(java.lang.String, java.lang.String, org.opentdc.wtt.ProjectModel)
	 */
	@Override
	public ProjectModel updateProject(
		HttpServletRequest request,
		String compId,
		String projId,
		ProjectModel p
	) throws NotFoundException, ValidationException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();		
		Activity project = null;
		try {
			project = activitySegment.getActivity(projId);
		} catch(Exception ignore) {}
		if(project == null) {
			throw new NotFoundException("no project with ID <" + projId + "> found.");
		}
		try {
			pm.currentTransaction().begin();
			project.setName(p.getTitle());
			project.setDescription(p.getDescription());
			pm.currentTransaction().commit();
		} catch(Exception e) {
			new ServiceException(e).log();
			try {
				pm.currentTransaction().rollback();
			} catch(Exception ignore) {}
		}
		return this.readProject(compId, projId);
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#deleteProject(java.lang.String, java.lang.String)
	 */
	@Override
	public void deleteProject(
		String compId, 
		String projId
	) throws NotFoundException, InternalServerErrorException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();		
		Activity project = null;
		try {
			project = activitySegment.getActivity(projId);
		} catch(Exception ignore) {}
		if(project == null || Boolean.TRUE.equals(project.isDisabled())) {
			throw new NotFoundException("no project with ID <" + projId + "> found.");
		}
		try {
			// Delete project
			pm.currentTransaction().begin();
			project.setDisabled(true);
			pm.currentTransaction().commit();
			// ... and sub-projects
			List<ProjectModel> subprojects = this.listSubprojects(compId, projId, null, null, 0, 0);
			for(ProjectModel subproject: subprojects) {
				this.deleteSubproject(compId, projId, subproject.getId());
			}
		} catch(Exception e) {
			new ServiceException(e).log();
			try {
				pm.currentTransaction().rollback();
			} catch(Exception ignore) {}
		}
	}

	/******************************** subprojects *****************************************/
	
	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#listSubprojects(java.lang.String, java.lang.String, java.lang.String, java.lang.String, int, int)
	 */
	@Override
	public List<ProjectModel> listSubprojects(
		String compId, 
		String projId,
		String query, 
		String queryType, 
		int position, 
		int size
	) {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		Activity project = activitySegment.getActivity(projId);
		ActivityQuery subprojectsQuery = (ActivityQuery)pm.newQuery(Activity.class);
		subprojectsQuery.thereExistsActivityLinkTo().activityLinkType().equalTo(ActivitiesHelper.ACTIVITY_LINK_TYPE_IS_CHILD_OF);
		subprojectsQuery.thereExistsActivityLinkTo().thereExistsLinkTo().equalTo(project);
		List<Activity> subprojects = activitySegment.getActivity(subprojectsQuery);
		List<ProjectModel> result = new ArrayList<ProjectModel>();
		int count = 0;
		for(Iterator<Activity> i = subprojects.listIterator(position); i.hasNext(); ) {
			Activity subproject = i.next();
			result.add(this.mapToProject(subproject));
			count++;
			if(count >= size) break;
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#createSubproject(java.lang.String, java.lang.String, org.opentdc.wtt.ProjectModel)
	 */
	@Override
	public ProjectModel createSubproject(
		HttpServletRequest request,
		String compId, 
		String projId,
		ProjectModel project
	) throws DuplicateException, ValidationException {
		logger.info("> createSubproject(" + compId + ", " + project + ")");
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		ActivityTracker customerProjectGroup = null;
		try {
			customerProjectGroup = activitySegment.getActivityTracker(compId);
		} catch(Exception ignore) {}
		if(customerProjectGroup == null || Boolean.TRUE.equals(customerProjectGroup.isDisabled())) {
			throw new NotFoundException("no company with ID <" + compId + "> found.");
		}
		Activity parentProject = null;
		try {
			parentProject = activitySegment.getActivity(projId);
		} catch(Exception ignore) {}
		if(parentProject == null) {
			throw new NotFoundException("Project with ID " + projId + " not found.");				
		}
		if(project.getId() != null) {
			Activity _project = null;
			try {
				_project = activitySegment.getActivity(project.getId());
			} catch(Exception ignore) {}
			if(_project != null) {
				throw new DuplicateException("Project with ID " + project.getId() + " exists already.");				
			} else {
				throw new ValidationException("project <" + project.getId() + "> contains an ID generated on the client. This is not allowed.");
			}
		}
		if(project.getTitle() == null || project.getTitle().length() == 0) {
			throw new ValidationException("project must have a valid title.");
		}
		Activity _project = ActivitiesHelper.createCustomerProject(
			pm,
			customerProjectGroup,
			project.getTitle(), 
			project.getDescription(), 
			null,
			new Date(), 
			new Date(), 
			ActivitiesHelper.ACTIVITY_PRIORITY_NA,
			parentProject
		);
		if(_project == null) {
			throw new InternalServerErrorException();
		} else {
			ProjectModel _p = this.mapToProject(_project);
			return _p;
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#readSubproject(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public ProjectModel readSubproject(
		String compId, 
		String projId,
		String subprojId
	)  throws NotFoundException {
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();		
		Activity _project = null;
		try {
			_project = activitySegment.getActivity(subprojId);
		} catch(Exception ignore) {}
		if(_project == null || Boolean.TRUE.equals(_project.isDisabled())) {
			throw new NotFoundException("no sub-project with ID <" + projId + "> found.");
		}
		ProjectModel _p = this.mapToProject(_project);
		logger.info("readSubproject(" + projId + "): " + _p);
		return _p;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#updateSubproject(java.lang.String, java.lang.String, java.lang.String, org.opentdc.wtt.ProjectModel)
	 */
	@Override
	public ProjectModel updateSubproject(
		HttpServletRequest request,
		String compId, 
		String projId,
		String subprojId, 
		ProjectModel project
	) throws NotFoundException, ValidationException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();		
		Activity _project = null;
		try {
			_project = activitySegment.getActivity(subprojId);
		} catch(Exception ignore) {}
		if(_project == null || Boolean.TRUE.equals(_project.isDisabled())) {
			throw new NotFoundException("no sub-project with ID <" + projId + "> found.");
		}
		try {
			pm.currentTransaction().begin();
			_project.setName(project.getTitle());
			_project.setDescription(project.getDescription());
			pm.currentTransaction().commit();
		} catch(Exception e) {
			new ServiceException(e).log();
			try {
				pm.currentTransaction().rollback();
			} catch(Exception ignore) {}
		}
		return this.readSubproject(compId, projId, subprojId);
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#deleteSubproject(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void deleteSubproject(
		String compId, 
		String projId, 
		String subprojId
	) throws NotFoundException, InternalServerErrorException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();		
		Activity _subproject = null;
		try {
			_subproject = activitySegment.getActivity(subprojId);
		} catch(Exception ignore) {}
		if(_subproject == null || Boolean.TRUE.equals(_subproject.isDisabled())) {
			throw new NotFoundException("no sub-project with ID <" + subprojId + "> found.");
		}
		try {
			// Delete sub-project
			pm.currentTransaction().begin();
			_subproject.setDisabled(true);
			pm.currentTransaction().commit();
			// ... and its sub-projects
			List<ProjectModel> subprojects = this.listSubprojects(compId, subprojId, null, null, 0, 0);
			for(ProjectModel subproject: subprojects) {
				this.deleteSubproject(compId, projId, subproject.getId());
			}
		} catch(Exception e) {
			new ServiceException(e).log();
			try {
				pm.currentTransaction().rollback();
			} catch(Exception ignore) {}
		}
	}

	/******************************** resource *****************************************/
	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#listResources(java.lang.String, java.lang.String, java.lang.String, long, long)
	 */
	@Override
	public List<ResourceRefModel> listResourceRefs(
		String compId,
		String projId,
		String query, 
		String queryType, 
		int position,
		int size
	)  throws NotFoundException {
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();		
		Activity project = null;
		try {
			project = activitySegment.getActivity(projId);
		} catch(Exception ignore) {}
		if(project == null || Boolean.TRUE.equals(project.isDisabled())) {
			throw new NotFoundException("no project with ID <" + projId + "> found.");
		}
		List<ResourceAssignment> resourceAssignments = ActivitiesHelper.getProjectResources(project);
		List<ResourceRefModel> _result = new ArrayList<ResourceRefModel>();
		int count = 0;
		for(Iterator<ResourceAssignment> i = resourceAssignments.listIterator(position); i.hasNext(); ) {
			ResourceAssignment resourceAssignment = i.next();
			_result.add(this.mapToResourceRef(resourceAssignment));
			count++;
			if(count >= size) break;
		}
		return _result;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#addResource(java.lang.String, java.lang.String)
	 */
	@Override
	public ResourceRefModel addResourceRef(
		HttpServletRequest request,
		String compId,
		String projId, 
		ResourceRefModel resourceRef
	) throws NotFoundException, DuplicateException, ValidationException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();		
		Activity project = null;
		try {
			project = activitySegment.getActivity(projId);
		} catch(Exception ignore) {}
		if(project == null || Boolean.TRUE.equals(project.isDisabled())) {
			throw new NotFoundException("no project with ID <" + projId + "> found.");
		}
		if(resourceRef.getId() != null) {
			ResourceAssignment resourceAssignment = project.getAssignedResource(resourceRef.getId());
			if(resourceAssignment != null) {
				throw new DuplicateException("resource ref with ID " + resourceRef.getId() + " exists already.");
			} else {
				throw new ValidationException("resource ref <" + resourceRef.getId() + "> contains an ID generated on the client. This is not allowed.");
			}
		}
		Resource resource = null;
		try {
			resource = activitySegment.getResource(resourceRef.getResourceId());
		} catch(Exception ignore) {}
		// @TODO test for existing resource
//		if(resource == null || Boolean.TRUE.equals(resource.isDisabled())) {
//			throw new NotFoundException("no resource with ID <" + resourceRef.getId() + "> found.");
//		}
		try {
			ResourceAssignment resourceAssignment = pm.newInstance(ResourceAssignment.class);
			pm.currentTransaction().begin();
			resourceAssignment.setName(
				(resourceRef.getResourceName() == null ? "" : resourceRef.getResourceName())
			);
			resourceAssignment.setResource(resource);
			resourceAssignment.setResourceRole(ActivitiesHelper.RESOURCE_ROLE_MEMBER);
			project.addAssignedResource(
				Utils.getUidAsString(),
				resourceAssignment
			);
			pm.currentTransaction().commit();
			resourceRef = this.mapToResourceRef(resourceAssignment);
		} catch(Exception e) {
			new ServiceException(e).log();
			try {
				pm.currentTransaction().rollback();
			} catch(Exception ignore) {}
			throw new InternalServerErrorException();
		}
		return resourceRef;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.wtt.ServiceProvider#removeResource(java.lang.String, java.lang.String)
	 */
	@Override
	public void removeResourceRef(
		String compId,
		String projId, 
		String resourceId
	) throws NotFoundException, InternalServerErrorException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();		
		Activity project = null;
		try {
			project = activitySegment.getActivity(projId);
		} catch(Exception ignore) {}
		if(project == null || Boolean.TRUE.equals(project.isDisabled())) {
			throw new NotFoundException("no project with ID <" + projId + "> found.");
		}
		ResourceAssignment resourceAssignment = null;
		try {
			resourceAssignment = project.getAssignedResource(resourceId);
		} catch(Exception ignore) {}
		if(resourceAssignment == null || Boolean.TRUE.equals(resourceAssignment.isDisabled())) {
			throw new NotFoundException("no resource with ID <" + resourceId + "> found.");
		}
		try {
			pm.currentTransaction().begin();
			resourceAssignment.setDisabled(true);
			pm.currentTransaction().commit();
		} catch(Exception e) {
			new ServiceException(e).log();
			try {
				pm.currentTransaction().rollback();
			} catch(Exception ignore) {}
			throw new InternalServerErrorException();
		}
	}
}
